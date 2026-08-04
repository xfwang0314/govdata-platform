package com.xfwang.govdata.datasource.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.datasource.DataSourceErrorCode;
import com.xfwang.govdata.datasource.domain.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.DataSourceConnectionTest;
import com.xfwang.govdata.datasource.mapper.DataSourceConnectionTestMapper;
import com.xfwang.govdata.datasource.mapper.DataSourceInstanceMapper;
import com.xfwang.govdata.datasource.model.ConnectionTestView;
import com.xfwang.govdata.datasource.model.DataSourceCommand;
import com.xfwang.govdata.datasource.model.DataSourceQuery;
import com.xfwang.govdata.datasource.model.DataSourceView;
import com.xfwang.platform.auth.api.model.ResourceDescriptor;
import com.xfwang.platform.auth.api.service.ResourceRegistry;
import com.xfwang.govdata.datasource.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.connector.ConnectorDescriptor;
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
public class DataSourceApplicationService {

    private static final Set<String> FORBIDDEN_CONFIG_KEYS = Set.of(
        "password", "passwd", "pwd", "token", "secret", "privatekey", "accesskey");

    private final DataSourceInstanceMapper instanceMapper;
    private final DataSourceConnectionTestMapper testMapper;
    private final ResourceRegistry resourceRegistry;
    private final DataSourceAuthorizedOperations authorizedOperations;
    private final ConnectorCatalog connectorCatalog;

    public PageResult<DataSourceView> page(DataSourceQuery query, PageQuery pageQuery) {
        LambdaQueryWrapper<DataSourceInstance> wrapper = new LambdaQueryWrapper<DataSourceInstance>()
            .like(StringUtils.isNotBlank(query.getDatasourceCode()), DataSourceInstance::getDatasourceCode,
                query.getDatasourceCode())
            .like(StringUtils.isNotBlank(query.getDatasourceName()), DataSourceInstance::getDatasourceName,
                query.getDatasourceName())
            .eq(StringUtils.isNotBlank(query.getConnectorCode()), DataSourceInstance::getConnectorCode,
                query.getConnectorCode())
            .eq(StringUtils.isNotBlank(query.getEnvironment()), DataSourceInstance::getEnvironment,
                query.getEnvironment())
            .eq(StringUtils.isNotBlank(query.getStatus()), DataSourceInstance::getStatus, query.getStatus())
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
        ensureCodeAvailable(command.getDatasourceCode(), null);
        DataSourceInstance instance = new DataSourceInstance();
        copy(command, descriptor, instance);
        instance.setStatus("ACTIVE");
        instance.setLastTestStatus("NOT_TESTED");
        instance.setLockVersion(0);
        instance.setDelFlag("0");
        instanceMapper.insert(instance);

        ResourceDescriptor resource = resourceRegistry.register(ResourceDescriptor.builder()
            .resourceCode("govdata:datasource:" + instance.getDatasourceId())
            .resourceName(instance.getDatasourceName())
            .resourceType("govdata:data-source")
            .businessId(instance.getDatasourceId().toString())
            .sourceModule("govdata-datasource")
            .ownerDeptId(instance.getOwnerDeptId())
            .classification(command.getClassification())
            .securityLevel(command.getSecurityLevel())
            .attributes(Map.of(
                "connectorCode", instance.getConnectorCode(),
                "environment", instance.getEnvironment(),
                "readonly", instance.getReadonlyFlag()))
            .build());
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
        ensureCodeAvailable(command.getDatasourceCode(), datasourceId);
        copy(command, descriptor, instance);
        instance.setLockVersion(command.getLockVersion());
        if (instanceMapper.updateById(instance) != 1) {
            throw new ServiceException(GlobalErrorCode.BUSINESS_ERROR, "数据源已被其他请求修改，请刷新后重试");
        }
        resourceRegistry.register(ResourceDescriptor.builder()
            .resourceId(instance.getAuthzResourceId())
            .resourceCode("govdata:datasource:" + instance.getDatasourceId())
            .resourceName(instance.getDatasourceName())
            .resourceType("govdata:data-source")
            .businessId(instance.getDatasourceId().toString())
            .sourceModule("govdata-datasource")
            .ownerDeptId(instance.getOwnerDeptId())
            .classification(command.getClassification())
            .securityLevel(command.getSecurityLevel())
            .attributes(Map.of(
                "connectorCode", instance.getConnectorCode(),
                "environment", instance.getEnvironment(),
                "readonly", instance.getReadonlyFlag()))
            .build());
        return toView(instance);
    }

    public ConnectionTestView test(Long datasourceId, String purpose) {
        DataSourceInstance instance = require(datasourceId);
        return authorizedOperations.test(instance.getAuthzResourceId(), datasourceId, purpose);
    }

    public PageResult<ConnectionTestView> testHistory(Long datasourceId, PageQuery pageQuery) {
        require(datasourceId);
        Page<DataSourceConnectionTest> page = testMapper.selectPage(pageQuery.build(),
            new LambdaQueryWrapper<DataSourceConnectionTest>()
                .eq(DataSourceConnectionTest::getDatasourceId, datasourceId)
                .orderByDesc(DataSourceConnectionTest::getOccurredTime)
                .orderByDesc(DataSourceConnectionTest::getTestId));
        return PageResult.build(page.getRecords().stream().map(this::toTestView).toList(), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public DataSourceView changeStatus(Long datasourceId, String status) {
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "数据源状态无效");
        }
        DataSourceInstance instance = require(datasourceId);
        instance.setStatus(status);
        instanceMapper.updateById(instance);
        resourceRegistry.changeStatus(instance.getAuthzResourceId(), status);
        return toView(instance);
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
        target.setDatasourceCode(source.getDatasourceCode());
        target.setDatasourceName(source.getDatasourceName());
        target.setConnectorCode(source.getConnectorCode().toLowerCase(Locale.ROOT));
        target.setConnectorVersion(descriptor.getConnectorVersion());
        target.setEndpointConfigJson(JsonUtils.toJsonString(source.getEndpointConfig()));
        target.setSecretRef(source.getSecretRef());
        target.setEnvironment(source.getEnvironment());
        target.setOwnerDeptId(source.getOwnerDeptId());
        target.setReadonlyFlag(source.getReadonlyFlag());
        target.setClassificationLevel(source.getClassification());
        target.setSecurityLevel(source.getSecurityLevel());
    }

    private void ensureCodeAvailable(String code, Long excludedId) {
        LambdaQueryWrapper<DataSourceInstance> wrapper = new LambdaQueryWrapper<DataSourceInstance>()
            .eq(DataSourceInstance::getDatasourceCode, code)
            .ne(excludedId != null, DataSourceInstance::getDatasourceId, excludedId);
        if (instanceMapper.exists(wrapper)) {
            throw new ServiceException(DataSourceErrorCode.CODE_DUPLICATE);
        }
    }

    private ConnectorDescriptor prepare(DataSourceCommand command) {
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
            .readonlyFlag(instance.getReadonlyFlag())
            .classification(instance.getClassificationLevel())
            .securityLevel(instance.getSecurityLevel())
            .status(instance.getStatus())
            .lastTestStatus(instance.getLastTestStatus())
            .lastTestTime(instance.getLastTestTime())
            .lockVersion(instance.getLockVersion())
            .createTime(instance.getCreateTime())
            .updateTime(instance.getUpdateTime())
            .build();
    }

    private ConnectionTestView toTestView(DataSourceConnectionTest record) {
        Map<String, Object> serverInfo = JsonUtils.parseObject(record.getServerInfoJson(),
            new TypeReference<Map<String, Object>>() { });
        return ConnectionTestView.builder().testId(record.getTestId()).datasourceId(record.getDatasourceId())
            .result(record.getResult()).reasonCode(record.getReasonCode()).latencyMs(record.getLatencyMs())
            .serverInfo(serverInfo == null ? Map.of() : serverInfo).occurredTime(record.getOccurredTime()).build();
    }
}
