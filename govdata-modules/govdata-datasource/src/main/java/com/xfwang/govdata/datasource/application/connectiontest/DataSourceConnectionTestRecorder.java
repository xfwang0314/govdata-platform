package com.xfwang.govdata.datasource.application.connectiontest;

import com.xfwang.govdata.datasource.application.connectiontest.command.ConnectionTestRecordCommand;
import com.xfwang.govdata.datasource.application.connectiontest.view.ConnectionTestView;
import com.xfwang.govdata.datasource.domain.entity.DataSourceConnectionTest;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceConnectionTestMapper;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据源连通测试日志记录应用服务。
 *
 * <p>负责持久化测试历史记录并同步更新数据源主表 {@link DataSourceInstance} 的最近测试状态与时间。</p>
 */
@Service
@RequiredArgsConstructor
public class DataSourceConnectionTestRecorder {

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceConnectionTestMapper testMapper;

    /**
     * 记录连通测试结果并更新数据源测试状态。
     *
     * @param command 连通测试记录命令
     * @return 连通测试结果视图
     * @throws ServiceException 如果数据源不存在
     */
    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestView record(ConnectionTestRecordCommand command) {
        DataSourceInstance instance = instanceMapper.selectById(command.datasourceId());
        if (instance == null) {
            throw new ServiceException(DataSourceErrorCode.NOT_FOUND);
        }

        DataSourceConnectionTest record = new DataSourceConnectionTest();
        record.setTenantId(TenantHelper.getTenantId());
        record.setDatasourceId(command.datasourceId());
        record.setConnectorCode(command.connectorCode());
        record.setConnectorVersion(command.connectorVersion());
        record.setResult(command.result());
        record.setReasonCode(command.reasonCode());
        record.setLatencyMs(Math.max(0L, command.latencyMs()));
        record.setServerInfoJson(JsonUtils.toJsonString(command.serverInfo()));
        record.setActorType(command.actorType());
        record.setActorId(command.actorId());
        record.setTraceId(command.traceId());
        record.setOccurredTime(command.occurredTime());
        testMapper.insert(record);

        instance.setLastTestStatus(command.result());
        instance.setLastTestTime(command.occurredTime());
        instanceMapper.updateById(instance);

        return ConnectionTestView.builder()
            .testId(record.getTestId())
            .datasourceId(command.datasourceId())
            .result(command.result())
            .reasonCode(command.reasonCode())
            .message(command.message())
            .latencyMs(command.latencyMs())
            .serverInfo(command.serverInfo())
            .occurredTime(command.occurredTime())
            .build();
    }
}
