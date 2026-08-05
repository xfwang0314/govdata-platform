package com.xfwang.govdata.datasource.application.connectiontest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xfwang.govdata.datasource.application.connectiontest.command.ConnectionTestRecordCommand;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectionTesterRegistry;
import com.xfwang.govdata.datasource.domain.entity.DataSourceConnectionTest;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceConnectionTestMapper;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.govdata.datasource.application.connectiontest.view.ConnectionTestView;
import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import lombok.RequiredArgsConstructor;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.dromara.common.tenant.helper.TenantHelper;

import java.time.LocalDateTime;
import java.util.Map;

/** 必须经过 Resource + ABAC 的数据源操作入口。 */
@Service
@RequiredArgsConstructor
public class DataSourceConnectionTestService {

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceConnectionTestMapper testMapper;
    private final ConnectionTesterRegistry testerRegistry;
    private final DataSourceConnectionTestRecorder recorder;

    @ResourceAuthorize(resourceId = "#authzResourceId", resourceType = "govdata:data-source",
        action = "TEST_CONNECTION", purpose = "#purpose")
    public ConnectionTestView test(Long authzResourceId, Long datasourceId, String purpose) {
        DataSourceInstance instance = instanceMapper.selectById(datasourceId);
        if (instance == null || !authzResourceId.equals(instance.getAuthzResourceId())) {
            throw new org.dromara.common.core.exception.ServiceException(
                com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode.NOT_FOUND);
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
                .tenantId(TenantHelper.getTenantId())
                .actorType("USER")
                .actorId(LoginHelper.getUserIdStr())
                .purpose("DATASOURCE_CONNECTION_TEST")
                .traceId(MDC.get("traceId"))
                .build());
        return recorder.record(ConnectionTestRecordCommand.builder()
            .datasourceId(datasourceId)
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .result(result.isSuccess() ? "SUCCESS" : "FAILED")
            .reasonCode(result.getReasonCode())
            .message(result.getMessage())
            .latencyMs(result.getLatencyMs())
            .serverInfo(result.getServerInfo())
            .actorType("USER")
            .actorId(LoginHelper.getUserIdStr())
            .traceId(MDC.get("traceId"))
            .occurredTime(LocalDateTime.now())
            .build());
    }

    public PageResult<ConnectionTestView> history(Long datasourceId, PageQuery pageQuery) {
        DataSourceInstance instance = instanceMapper.selectById(datasourceId);
        if (instance == null) {
            throw new org.dromara.common.core.exception.ServiceException(
                com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode.NOT_FOUND);
        }
        Page<DataSourceConnectionTest> page = recorderHistory(pageQuery, datasourceId);
        return PageResult.build(page.getRecords().stream().map(this::toView).toList(), page.getTotal());
    }

    private Page<DataSourceConnectionTest> recorderHistory(PageQuery pageQuery, Long datasourceId) {
        return testMapper.selectPage(pageQuery.build(), new LambdaQueryWrapper<DataSourceConnectionTest>()
            .eq(DataSourceConnectionTest::getDatasourceId, datasourceId)
            .orderByDesc(DataSourceConnectionTest::getOccurredTime)
            .orderByDesc(DataSourceConnectionTest::getTestId));
    }

    private ConnectionTestView toView(DataSourceConnectionTest record) {
        Map<String, Object> serverInfo = JsonUtils.parseObject(record.getServerInfoJson(),
            new TypeReference<Map<String, Object>>() { });
        return ConnectionTestView.builder().testId(record.getTestId()).datasourceId(record.getDatasourceId())
            .result(record.getResult()).reasonCode(record.getReasonCode()).latencyMs(record.getLatencyMs())
            .serverInfo(serverInfo == null ? Map.of() : serverInfo).occurredTime(record.getOccurredTime()).build();
    }
}
