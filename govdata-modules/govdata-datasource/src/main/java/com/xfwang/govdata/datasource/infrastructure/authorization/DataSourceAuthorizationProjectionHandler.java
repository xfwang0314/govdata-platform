package com.xfwang.govdata.datasource.infrastructure.authorization;

import com.xfwang.govdata.datasource.application.authorization.event.DataSourceAuthorizationProjectionEvent;
import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将数据源领域状态投影到统一授权资源表。
 *
 * <p>投影在业务事务提交后执行，授权表故障不会回滚数据源业务事务。未投影成功时
 * {@code authz_resource_id} 保持为空，对象级鉴权按 fail-closed 拒绝访问，后续可安全重放。</p>
 */
@Component
@RequiredArgsConstructor
public class DataSourceAuthorizationProjectionHandler {

    private final ResourceRegistry resourceRegistry;
    private final DataSourceInstanceMapper instanceMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void project(DataSourceAuthorizationProjectionEvent event) {
        if (event.operation() == DataSourceAuthorizationProjectionEvent.Operation.ARCHIVE) {
            archive(event);
            return;
        }
        Map<String, Object> datasourceAttributes = new LinkedHashMap<>();
        putIfPresent(datasourceAttributes, "environment", event.environment());
        putIfPresent(datasourceAttributes, "accessMode", event.accessMode());
        putIfPresent(datasourceAttributes, "status", event.status());
        putIfPresent(datasourceAttributes, "connectorCode", event.connectorCode());
        ResourceDescriptor resource = resourceRegistry.register(ResourceDescriptor.builder()
            .resourceName(event.datasourceName())
            .resourceType(DataSourceAuthorizationCodes.RESOURCE_TYPE)
            .businessId(String.valueOf(event.datasourceId()))
            .sourceModule(DataSourceAuthorizationCodes.MODULE)
            .ownerDeptId(event.ownerDeptId())
            .ownerUserId(event.ownerUserId())
            .classification(event.classificationCode())
            .securityLevel(event.securityLevel())
            .attributes(Map.of("datasource", Map.copyOf(datasourceAttributes)))
            .build());
        DataSourceInstance projection = new DataSourceInstance();
        projection.setDatasourceId(event.datasourceId());
        projection.setAuthzResourceId(resource.getResourceId());
        instanceMapper.updateById(projection);
    }

    private void archive(DataSourceAuthorizationProjectionEvent event) {
        ResourceDescriptor resource = event.authzResourceId() == null
            ? resourceRegistry.findByBusinessKey(DataSourceAuthorizationCodes.MODULE,
                DataSourceAuthorizationCodes.RESOURCE_TYPE, String.valueOf(event.datasourceId()))
                .orElse(null)
            : resourceRegistry.findById(event.authzResourceId()).orElse(null);
        if (resource != null) {
            resourceRegistry.changeStatus(resource.getResourceId(), "ARCHIVED");
        }
    }

    private void putIfPresent(Map<String, Object> attributes, String key, Object value) {
        if (value != null) {
            attributes.put(key, value);
        }
    }
}
