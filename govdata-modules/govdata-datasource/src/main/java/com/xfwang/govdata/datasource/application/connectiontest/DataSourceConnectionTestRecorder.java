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

@Service
@RequiredArgsConstructor
public class DataSourceConnectionTestRecorder {

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceConnectionTestMapper testMapper;

    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestView record(ConnectionTestRecordCommand command) {
        DataSourceInstance instance = instanceMapper.selectById(command.getDatasourceId());
        if (instance == null) {
            throw new ServiceException(DataSourceErrorCode.NOT_FOUND);
        }

        DataSourceConnectionTest record = new DataSourceConnectionTest();
        record.setTenantId(TenantHelper.getTenantId());
        record.setDatasourceId(command.getDatasourceId());
        record.setConnectorCode(command.getConnectorCode());
        record.setConnectorVersion(command.getConnectorVersion());
        record.setResult(command.getResult());
        record.setReasonCode(command.getReasonCode());
        record.setLatencyMs(command.getLatencyMs());
        record.setServerInfoJson(JsonUtils.toJsonString(command.getServerInfo()));
        record.setActorType(command.getActorType());
        record.setActorId(command.getActorId());
        record.setTraceId(command.getTraceId());
        record.setOccurredTime(command.getOccurredTime());
        testMapper.insert(record);

        instance.setLastTestStatus(command.getResult());
        instance.setLastTestTime(command.getOccurredTime());
        instanceMapper.updateById(instance);

        return ConnectionTestView.builder()
            .testId(record.getTestId())
            .datasourceId(command.getDatasourceId())
            .result(command.getResult())
            .reasonCode(command.getReasonCode())
            .message(command.getMessage())
            .latencyMs(command.getLatencyMs())
            .serverInfo(command.getServerInfo())
            .occurredTime(command.getOccurredTime())
            .build();
    }
}
