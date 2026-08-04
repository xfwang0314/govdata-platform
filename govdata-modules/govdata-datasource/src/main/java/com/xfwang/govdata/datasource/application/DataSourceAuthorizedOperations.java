package com.xfwang.govdata.datasource.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.datasource.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.connector.ConnectionTestResult;
import com.xfwang.govdata.datasource.connector.ConnectionTesterRegistry;
import com.xfwang.govdata.datasource.domain.DataSourceConnectionTest;
import com.xfwang.govdata.datasource.domain.DataSourceInstance;
import com.xfwang.govdata.datasource.mapper.DataSourceConnectionTestMapper;
import com.xfwang.govdata.datasource.mapper.DataSourceInstanceMapper;
import com.xfwang.govdata.datasource.model.ConnectionTestView;
import com.xfwang.platform.auth.api.annotation.ResourceAuthorize;
import lombok.RequiredArgsConstructor;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 必须经过 Resource + ABAC 的数据源操作入口。 */
@Service
@RequiredArgsConstructor
public class DataSourceAuthorizedOperations {

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceConnectionTestMapper testMapper;
    private final ConnectionTesterRegistry testerRegistry;

    @ResourceAuthorize(resourceId = "#authzResourceId", resourceType = "govdata:data-source",
        action = "TEST_CONNECTION", purpose = "#purpose")
    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestView test(Long authzResourceId, Long datasourceId, String purpose) {
        DataSourceInstance instance = instanceMapper.selectById(datasourceId);
        if (instance == null || !authzResourceId.equals(instance.getAuthzResourceId())) {
            throw new org.dromara.common.core.exception.ServiceException(
                com.xfwang.govdata.datasource.DataSourceErrorCode.NOT_FOUND);
        }
        if (!"ACTIVE".equals(instance.getStatus())) {
            throw new org.dromara.common.core.exception.ServiceException(
                org.dromara.common.core.exception.GlobalErrorCode.VALIDATION_ERROR, "已停用的数据源不能执行连接测试");
        }
        Map<String, Object> endpoint = JsonUtils.parseObject(instance.getEndpointConfigJson(),
            new TypeReference<Map<String, Object>>() { });
        ConnectionTestResult result = testerRegistry
            .require(instance.getConnectorCode(), instance.getConnectorVersion())
            .test(ConnectionTestRequest.builder()
                .datasourceId(datasourceId)
                .connectorCode(instance.getConnectorCode())
                .connectorVersion(instance.getConnectorVersion())
                .environment(instance.getEnvironment())
                .endpointConfig(endpoint == null ? Map.of() : endpoint)
                .secretRef(instance.getSecretRef())
                .build());
        LocalDateTime occurredTime = LocalDateTime.now();
        DataSourceConnectionTest record = new DataSourceConnectionTest();
        record.setDatasourceId(datasourceId);
        record.setConnectorCode(instance.getConnectorCode());
        record.setConnectorVersion(instance.getConnectorVersion());
        record.setResult(result.isSuccess() ? "SUCCESS" : "FAILED");
        record.setReasonCode(result.getReasonCode());
        record.setLatencyMs(result.getLatencyMs());
        record.setServerInfoJson(JsonUtils.toJsonString(result.getServerInfo()));
        record.setActorType("USER");
        record.setActorId(LoginHelper.getUserIdStr());
        record.setTraceId(MDC.get("traceId"));
        record.setOccurredTime(occurredTime);
        testMapper.insert(record);

        instance.setLastTestStatus(record.getResult());
        instance.setLastTestTime(occurredTime);
        instanceMapper.updateById(instance);
        return ConnectionTestView.builder()
            .testId(record.getTestId())
            .datasourceId(datasourceId)
            .result(record.getResult())
            .reasonCode(result.getReasonCode())
            .message(result.getMessage())
            .latencyMs(result.getLatencyMs())
            .serverInfo(result.getServerInfo())
            .occurredTime(occurredTime)
            .build();
    }
}
