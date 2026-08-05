package com.xfwang.govdata.datasource.application.management;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.enums.DataSourceEnums;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.govdata.datasource.application.management.command.DataSourceCommand;
import com.xfwang.govdata.datasource.application.management.query.DataSourceQuery;
import com.xfwang.govdata.datasource.application.management.view.DataSourceView;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** 数据源实例用例编排。 */
@Service
@RequiredArgsConstructor
public class DataSourceManagementService {

    private static final Set<String> FORBIDDEN_CONFIG_KEYS = Set.of(
        "password", "passwd", "pwd", "token", "secret", "privatekey", "accesskey");

    private final DataSourceInstanceMapper instanceMapper;
    private final ResourceRegistry resourceRegistry;
    private final ConnectorCatalog connectorCatalog;

    public PageResult<DataSourceView> page(DataSourceQuery query, PageQuery pageQuery) {
        validateQuery(query);
        LambdaQueryWrapper<DataSourceInstance> wrapper = new LambdaQueryWrapper<DataSourceInstance>()
            .like(StringUtils.isNotBlank(query.getDatasourceCode()), DataSourceInstance::getDatasourceCode,
                query.getDatasourceCode())
            .like(StringUtils.isNotBlank(query.getDatasourceName()), DataSourceInstance::getDatasourceName,
                query.getDatasourceName())
            .eq(StringUtils.isNotBlank(query.getConnectorCode()), DataSourceInstance::getConnectorCode,
                query.getConnectorCode())
            .eq(StringUtils.isNotBlank(query.getEnvironment()), DataSourceInstance::getEnvironment,
                query.getEnvironment())
            .eq(StringUtils.isNotBlank(query.getAccessMode()), DataSourceInstance::getAccessMode,
                query.getAccessMode())
            .eq(StringUtils.isNotBlank(query.getClassificationCode()), DataSourceInstance::getClassificationCode,
                query.getClassificationCode())
            .eq(query.getSecurityLevel() != null, DataSourceInstance::getSecurityLevel, query.getSecurityLevel())
            .eq(StringUtils.isNotBlank(query.getStatus()), DataSourceInstance::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getLastTestStatus()), DataSourceInstance::getLastTestStatus,
                query.getLastTestStatus())
            .orderByDesc(DataSourceInstance::getCreateTime)
            .orderByDesc(DataSourceInstance::getDatasourceId);
        Page<DataSourceInstance> page = instanceMapper.selectPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords().stream().map(this::toView).toList(), page.getTotal());
    }

    public DataSourceView get(Long datasourceId) {
        return toView(require(datasourceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceView create(DataSourceCommand command) {
        ConnectorDescriptor descriptor = prepare(command);
        DataSourceInstance instance = new DataSourceInstance();
        long datasourceId = IdWorker.getId();
        instance.setDatasourceId(datasourceId);
        instance.setDatasourceCode("ds_" + Long.toUnsignedString(datasourceId, 36));
        copy(command, descriptor, instance);
        instance.setStatus("DISABLED");
        instance.setLastTestStatus("NOT_TESTED");
        instance.setLockVersion(0);
        instance.setDelFlag("0");
        instanceMapper.insert(instance);

        ResourceDescriptor resource = resourceRegistry.register(toResourceDescriptor(instance));
        instance.setAuthzResourceId(resource.getResourceId());
        instanceMapper.updateById(instance);
        return toView(instance);
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceView update(Long datasourceId, DataSourceCommand command) {
        ConnectorDescriptor descriptor = prepare(command);
        DataSourceInstance instance = require(datasourceId);
        if (command.getLockVersion() == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "修改数据源必须携带 lockVersion");
        }
        copy(command, descriptor, instance);
        instance.setStatus("DISABLED");
        instance.setLastTestStatus("NOT_TESTED");
        instance.setLastTestTime(null);
        instance.setLockVersion(command.getLockVersion());
        if (instanceMapper.updateById(instance) != 1) {
            throw new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "数据源已被其他请求修改，请刷新后重试");
        }
        resourceRegistry.register(toResourceDescriptor(instance));
        return toView(instance);
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceView changeStatus(Long datasourceId, String status) {
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "数据源状态无效");
        }
        DataSourceInstance instance = require(datasourceId);
        if ("ACTIVE".equals(status) && !"SUCCESS".equals(instance.getLastTestStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "连接测试成功后才能启用数据源");
        }
        instance.setStatus(status);
        instanceMapper.updateById(instance);
        resourceRegistry.register(toResourceDescriptor(instance));
        return toView(instance);
    }

    ResourceDescriptor toResourceDescriptor(DataSourceInstance instance) {
        return ResourceDescriptor.builder().resourceId(instance.getAuthzResourceId())
            .resourceName(instance.getDatasourceName())
            .resourceType("govdata:data-source")
            .businessId(instance.getDatasourceId().toString())
            .sourceModule("govdata-datasource")
            .ownerDeptId(instance.getOwnerDeptId())
            .classification(instance.getClassificationCode())
            .securityLevel(instance.getSecurityLevel())
            .status("ACTIVE")
            .attributes(Map.of("connectorCode", instance.getConnectorCode(),
                "environment", instance.getEnvironment(), "accessMode", instance.getAccessMode(),
                "datasourceStatus", instance.getStatus()))
            .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long datasourceId) {
        DataSourceInstance instance = require(datasourceId);
        if (!"DISABLED".equals(instance.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "请先停用数据源再删除");
        }
        resourceRegistry.changeStatus(instance.getAuthzResourceId(), "ARCHIVED");
        instanceMapper.deleteById(instance);
    }

    DataSourceInstance require(Long datasourceId) {
        DataSourceInstance instance = datasourceId == null ? null : instanceMapper.selectById(datasourceId);
        if (instance == null) {
            throw new ServiceException(DataSourceErrorCode.NOT_FOUND);
        }
        return instance;
    }

    private void copy(DataSourceCommand source, ConnectorDescriptor descriptor, DataSourceInstance target) {
        target.setDatasourceName(source.getDatasourceName());
        target.setConnectorCode(source.getConnectorCode().toLowerCase(Locale.ROOT));
        target.setConnectorVersion(descriptor.getConnectorVersion());
        target.setEndpointConfigJson(JsonUtils.toJsonString(source.getEndpointConfig()));
        target.setSecretRef(source.getSecretRef());
        target.setEnvironment(DataSourceEnums.environment(source.getEnvironment()));
        target.setOwnerDeptId(source.getOwnerDeptId());
        target.setAccessMode(DataSourceEnums.accessMode(source.getAccessMode()));
        target.setClassificationCode(DataSourceEnums.classification(source.getClassificationCode()));
        target.setSecurityLevel(source.getSecurityLevel());
    }

    private ConnectorDescriptor prepare(DataSourceCommand command) {
        DataSourceEnums.environment(command.getEnvironment());
        DataSourceEnums.accessMode(command.getAccessMode());
        DataSourceEnums.classification(command.getClassificationCode());
        Map<String, Object> endpoint = command.getEndpointConfig();
        if (endpoint == null || endpoint.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "endpointConfig 不能为空");
        }
        if (containsSecretKey(endpoint)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "endpointConfig 禁止包含密码、Token、密钥等敏感字段，请使用 secretRef");
        }
        if (JsonUtils.toJsonString(endpoint).length() > 8192) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "endpointConfig 不能超过 8KB");
        }
        Map<String, Object> normalized = new HashMap<>(endpoint);
        ConnectorDescriptor descriptor = connectorCatalog.validate(command.getConnectorCode(), normalized);
        command.setEndpointConfig(normalized);
        return descriptor;
    }

    private void validateQuery(DataSourceQuery query) {
        if (StringUtils.isNotBlank(query.getEnvironment())) DataSourceEnums.environment(query.getEnvironment());
        if (StringUtils.isNotBlank(query.getAccessMode())) DataSourceEnums.accessMode(query.getAccessMode());
        if (StringUtils.isNotBlank(query.getClassificationCode())) DataSourceEnums.classification(query.getClassificationCode());
        if (StringUtils.isNotBlank(query.getStatus())) DataSourceEnums.status(query.getStatus());
        if (StringUtils.isNotBlank(query.getLastTestStatus())) DataSourceEnums.testStatus(query.getLastTestStatus());
        if (query.getSecurityLevel() != null && (query.getSecurityLevel() < 1 || query.getSecurityLevel() > 5)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "安全等级无效");
        }
    }

    private boolean containsSecretKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry -> {
                String normalized = String.valueOf(entry.getKey())
                    .replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                return FORBIDDEN_CONFIG_KEYS.contains(normalized) || containsSecretKey(entry.getValue());
            });
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(this::containsSecretKey);
        }
        return false;
    }

    private DataSourceView toView(DataSourceInstance instance) {
        Map<String, Object> endpoint = JsonUtils.parseObject(instance.getEndpointConfigJson(),
            new TypeReference<Map<String, Object>>() { });
        return DataSourceView.builder()
            .datasourceId(instance.getDatasourceId())
            .authzResourceId(instance.getAuthzResourceId())
            .datasourceCode(instance.getDatasourceCode())
            .datasourceName(instance.getDatasourceName())
            .connectorCode(instance.getConnectorCode())
            .connectorVersion(instance.getConnectorVersion())
            .endpointConfig(endpoint == null ? Map.of() : endpoint)
            .secretRef(instance.getSecretRef())
            .environment(instance.getEnvironment())
            .ownerDeptId(instance.getOwnerDeptId())
            .accessMode(instance.getAccessMode())
            .classificationCode(instance.getClassificationCode())
            .securityLevel(instance.getSecurityLevel())
            .status(instance.getStatus())
            .lastTestStatus(instance.getLastTestStatus())
            .lastTestTime(instance.getLastTestTime())
            .lockVersion(instance.getLockVersion())
            .createTime(instance.getCreateTime())
            .updateTime(instance.getUpdateTime())
            .build();
    }

}
