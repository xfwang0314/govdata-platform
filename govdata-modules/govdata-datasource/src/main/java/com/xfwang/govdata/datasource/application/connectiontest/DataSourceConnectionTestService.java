package com.xfwang.govdata.datasource.application.connectiontest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.datasource.application.connectiontest.command.ConnectionTestRecordCommand;
import com.xfwang.govdata.datasource.application.connectiontest.view.ConnectionTestView;
import com.xfwang.govdata.datasource.domain.entity.DataSourceConnectionTest;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestRequest;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ConnectionTestResult;
import com.xfwang.govdata.datasource.infrastructure.connector.registry.ConnectionTesterRegistry;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceConnectionTestMapper;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据源连接测试应用服务。
 *
 * <p>提供触发指定数据源连通测试以及分页查询测试历史记录的功能。</p>
 */
@Service
@RequiredArgsConstructor
public class DataSourceConnectionTestService {

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceConnectionTestMapper testMapper;
    private final ConnectionTesterRegistry testerRegistry;
    private final ConnectorCatalog connectorCatalog;
    private final DataSourceConnectionTestRecorder recorder;
    @Qualifier("connectionTestExecutor")
    private final Executor connectionTestExecutor;

    /**
     * 对指定数据源触发连通测试并保存测试结果。
     *
     * @param datasourceId 数据源 ID
     * @return 连通测试视图
     * @throws ServiceException 如果数据源不存在或端点校验失败
     */
    public ConnectionTestView test(Long datasourceId) {
        DataSourceInstance instance = instanceMapper.selectById(datasourceId);
        if (instance == null) {
            throw new ServiceException(DataSourceErrorCode.NOT_FOUND);
        }
        Map<String, Object> storedEndpoint = JsonUtils.parseObject(instance.getEndpointConfigJson(),
            new TypeReference<Map<String, Object>>() { });
        Map<String, Object> endpoint = new HashMap<>(storedEndpoint == null ? Map.of() : storedEndpoint);
        // 连接目标属于 Connector 派生值；测试时按当前模板重建，使 Adapter 升级无需用户重新保存历史数据源。
        endpoint.remove("connectionTarget");
        endpoint.remove("jdbcUrl");
        connectorCatalog.validate(instance.getConnectorCode(), instance.getAuthProfileCode(),
            instance.getEnvironment(), endpoint);
        ConnectionTestRequest request = ConnectionTestRequest.builder()
            .datasourceId(datasourceId)
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .authProfileCode(instance.getAuthProfileCode())
            .environment(instance.getEnvironment())
            .endpointConfig(endpoint)
            .secretRef(instance.getSecretRef())
            .tenantId(TenantHelper.getTenantId())
            .actorType("USER")
            .actorId(LoginHelper.getUserIdStr())
            .purpose(DataSourceSecretUsage.CONNECTION_TEST.code())
            .traceId(MDC.get("traceId"))
            .build();

        ConnectionTestResult result;
        try {
            result = CompletableFuture.supplyAsync(() -> testerRegistry
                    .require(instance.getConnectorCode(), instance.getConnectorVersion())
                    .test(request), connectionTestExecutor)
                .orTimeout(30, TimeUnit.SECONDS)
                .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                result = ConnectionTestResult.builder()
                    .success(false)
                    .reasonCode("CONNECTION_TIMEOUT")
                    .message("连接测试超时，请检查网络和数据源状态")
                    .latencyMs(30000L)
                    .build();
            } else if (e.getCause() instanceof ServiceException se) {
                throw se;
            } else {
                result = ConnectionTestResult.builder()
                    .success(false)
                    .reasonCode("TEST_FAILED")
                    .message("连接测试失败：" + e.getCause().getMessage())
                    .latencyMs(0)
                    .build();
            }
        } catch (java.util.concurrent.RejectedExecutionException e) {
            throw new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "连接测试服务繁忙，请稍后重试");
        }
        return recorder.record(ConnectionTestRecordCommand.builder()
            .datasourceId(datasourceId)
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .result(result.isSuccess() ? "SUCCESS" : "FAILED")
            .reasonCode(result.getReasonCode())
            .message(result.getMessage())
            .latencyMs(Math.max(0L, result.getLatencyMs()))
            .serverInfo(result.getServerInfo())
            .actorType("USER")
            .actorId(LoginHelper.getUserIdStr())
            .traceId(MDC.get("traceId"))
            .occurredTime(LocalDateTime.now())
            .build());
    }

    /**
     * 分页查询指定数据源的连通测试历史记录。
     *
     * @param datasourceId 数据源 ID
     * @param pageQuery    分页查询参数
     * @return 连通测试历史视图分页结果
     * @throws ServiceException 如果数据源不存在
     */
    public PageResult<ConnectionTestView> history(Long datasourceId, PageQuery pageQuery) {
        DataSourceInstance instance = instanceMapper.selectById(datasourceId);
        if (instance == null) {
            throw new ServiceException(DataSourceErrorCode.NOT_FOUND);
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
        return ConnectionTestView.builder()
            .testId(record.getTestId())
            .datasourceId(record.getDatasourceId())
            .result(record.getResult())
            .reasonCode(record.getReasonCode())
            .latencyMs(record.getLatencyMs())
            .serverInfo(serverInfo == null ? Map.of() : serverInfo)
            .occurredTime(record.getOccurredTime())
            .build();
    }
}
