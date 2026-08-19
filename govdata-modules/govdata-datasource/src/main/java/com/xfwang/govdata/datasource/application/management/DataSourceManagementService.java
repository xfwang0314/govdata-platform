package com.xfwang.govdata.datasource.application.management;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.govdata.datasource.application.authorization.DataSourceAuthorizationScope;
import com.xfwang.govdata.datasource.application.authorization.event.DataSourceAuthorizationProjectionEvent;
import com.xfwang.govdata.datasource.application.authorization.view.ProjectionReconcileView;
import com.xfwang.govdata.datasource.application.management.command.DataSourceCommand;
import com.xfwang.govdata.datasource.application.management.query.DataSourceQuery;
import com.xfwang.govdata.datasource.application.management.view.DataSourceView;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.authorization.DataSourceAuthorizationCodes;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.domain.enums.DataSourceEnums;
import com.xfwang.govdata.datasource.domain.enums.DataSourceSecretUsage;
import com.xfwang.govdata.datasource.domain.exception.DataSourceErrorCode;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.enums.SecurityLevel;
import com.xfwang.platform.api.secret.SecretCatalog;
import com.xfwang.platform.api.secret.SecretReferenceValidation;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据源实例管理应用服务。
 *
 * <p>负责数据源的生命周期编排（创建、修改、启用/停用、删除、分页查询与详情检索），
 * 严格执行 Connector 端点规范校验、敏感字段禁用、凭据有效性校验及乐观锁控制。</p>
 */
@Service
@RequiredArgsConstructor
public class DataSourceManagementService {

    private static final Set<String> FORBIDDEN_CONFIG_KEYS = Set.of(
        "password", "passwd", "pwd", "token", "secret", "privatekey", "accesskey");

    private final DataSourceInstanceMapper instanceMapper;
    private final ConnectorCatalog connectorCatalog;
    private final SecretCatalog secretCatalog;
    private final ApplicationEventPublisher eventPublisher;
    private final DataSourceAuthorizationScope authorizationScope;

    /**
     * 分页查询数据源列表。
     *
     * @param query     查询条件对象
     * @param pageQuery 分页参数
     * @return 数据源视图分页结果
     */
    public PageResult<DataSourceView> page(DataSourceQuery query, PageQuery pageQuery) {
        validateQuery(query);
        var authorizedScope = authorizationScope.administrationViewScope();
        if (authorizedScope.denied()) {
            return PageResult.build(java.util.List.of(), 0L);
        }
        LambdaQueryWrapper<DataSourceInstance> wrapper = new LambdaQueryWrapper<DataSourceInstance>()
            .in(!authorizedScope.unrestricted(), DataSourceInstance::getAuthzResourceId,
                authorizedScope.resourceIds())
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

    /**
     * 查询指定数据源详情视图。
     *
     * @param datasourceId 数据源 ID
     * @return 数据源视图
     * @throws ServiceException 如果数据源不存在
     */
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.VIEW,
        purpose = DataSourceAuthorizationCodes.Purpose.ADMINISTRATION)
    public DataSourceView get(Long datasourceId) {
        return toView(require(datasourceId));
    }

    /**
     * 创建新数据源。
     *
     * @param command 数据源创建命令
     * @return 创建后的数据源视图
     */
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
        publishProjection(instance, DataSourceAuthorizationProjectionEvent.Operation.UPSERT);
        return toView(instance);
    }

    /**
     * 修改现有数据源端点与凭据配置。
     *
     * @param datasourceId 数据源 ID
     * @param command      数据源更新命令
     * @return 更新后的数据源视图
     * @throws ServiceException 如果数据源不存在或乐观锁冲突
     */
    @Transactional(rollbackFor = Exception.class)
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.UPDATE,
        purpose = DataSourceAuthorizationCodes.Purpose.ADMINISTRATION)
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
        publishProjection(instance, DataSourceAuthorizationProjectionEvent.Operation.UPSERT);
        return toView(instance);
    }

    /**
     * 变更数据源状态（启用 ACTIVE / 停用 DISABLED）。
     *
     * @param datasourceId 数据源 ID
     * @return 更新后的数据源视图
     * @throws ServiceException 如果未经过连通测试成功即尝试启用
     */
    @Transactional(rollbackFor = Exception.class)
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.ENABLE,
        purpose = DataSourceAuthorizationCodes.Purpose.ADMINISTRATION)
    public DataSourceView enable(Long datasourceId) {
        return changeStatus(datasourceId, "ACTIVE");
    }

    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.DISABLE,
        purpose = DataSourceAuthorizationCodes.Purpose.ADMINISTRATION)
    public DataSourceView disable(Long datasourceId) {
        return changeStatus(datasourceId, "DISABLED");
    }

    private DataSourceView changeStatus(Long datasourceId, String status) {
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "数据源状态无效");
        }
        DataSourceInstance instance = require(datasourceId);
        if ("ACTIVE".equals(status) && !"SUCCESS".equals(instance.getLastTestStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "连接测试成功后才能启用数据源");
        }
        instance.setStatus(status);
        instanceMapper.updateById(instance);
        publishProjection(instance, DataSourceAuthorizationProjectionEvent.Operation.UPSERT);
        return toView(instance);
    }

    /**
     * 逻辑删除数据源。
     *
     * @param datasourceId 数据源 ID
     * @throws ServiceException 如果数据源处于 ACTIVE 状态
     */
    @Transactional(rollbackFor = Exception.class)
    @ResourceAuthorize(businessId = "#datasourceId",
        sourceModule = DataSourceAuthorizationCodes.MODULE,
        resourceType = DataSourceAuthorizationCodes.RESOURCE_TYPE,
        action = DataSourceAuthorizationCodes.Action.REMOVE,
        purpose = DataSourceAuthorizationCodes.Purpose.ADMINISTRATION)
    public void remove(Long datasourceId) {
        DataSourceInstance instance = require(datasourceId);
        if (!"DISABLED".equals(instance.getStatus())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "请先停用数据源再删除");
        }
        instanceMapper.deleteById(instance);
        publishProjection(instance, DataSourceAuthorizationProjectionEvent.Operation.ARCHIVE);
    }

    /**
     * 分批重放数据源授权资源投影，可修复缺失和属性漂移。
     *
     * @param afterId 上一批最后一个数据源 ID，首批传 0
     * @param limit 单批大小，接口层限制不超过 500
     * @return 对账游标与处理数量
     */
    public ProjectionReconcileView reconcileAuthorizationProjections(Long afterId, int limit) {
        Page<DataSourceInstance> batch = instanceMapper.selectPage(new Page<>(1, limit + 1L, false),
            new LambdaQueryWrapper<DataSourceInstance>()
                .gt(afterId != null && afterId > 0, DataSourceInstance::getDatasourceId, afterId)
                .orderByAsc(DataSourceInstance::getDatasourceId));
        boolean hasMore = batch.getRecords().size() > limit;
        var records = batch.getRecords().stream().limit(limit).toList();
        records.forEach(instance ->
            publishProjection(instance, DataSourceAuthorizationProjectionEvent.Operation.UPSERT));
        Long nextAfterId = records.isEmpty() ? afterId
            : records.get(records.size() - 1).getDatasourceId();
        return new ProjectionReconcileView(records.size(), nextAfterId, hasMore);
    }

    /**
     * 必须获取数据源实体，否则抛出 NOT_FOUND 异常。
     */
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
        target.setAuthProfileCode(source.getAuthProfileCode().toUpperCase(Locale.ROOT));
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
        ConnectorDescriptor descriptor = connectorCatalog.validate(command.getConnectorCode(),
            command.getAuthProfileCode(), command.getEnvironment(), normalized);
        var authProfile = connectorCatalog.requireAuthProfile(descriptor, command.getAuthProfileCode());
        validateSecretReference(command, authProfile);
        command.setEndpointConfig(normalized);
        return descriptor;
    }

    void validateSecretReference(DataSourceCommand command,
                                  com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile authProfile) {
        if (authProfile.getSecretType() == null) {
            if (StringUtils.isNotBlank(command.getSecretRef())) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "无认证方式禁止提交凭据引用");
            }
            command.setSecretRef(null);
            return;
        }
        if (StringUtils.isBlank(command.getSecretRef())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "当前认证方式必须选择连接凭据");
        }
        secretCatalog.validateReference(SecretReferenceValidation.builder()
            .secretRef(command.getSecretRef()).callerModule(DataSourceSecretUsage.OWNER_MODULE)
            .secretType(authProfile.getSecretType()).purpose(DataSourceSecretUsage.CONNECTION_TEST.code())
            .requiredKeys(authProfile.getRequiredKeys()).build());
    }

    private void validateQuery(DataSourceQuery query) {
        if (StringUtils.isNotBlank(query.getEnvironment())) DataSourceEnums.environment(query.getEnvironment());
        if (StringUtils.isNotBlank(query.getAccessMode())) DataSourceEnums.accessMode(query.getAccessMode());
        if (StringUtils.isNotBlank(query.getClassificationCode())) DataSourceEnums.classification(query.getClassificationCode());
        if (StringUtils.isNotBlank(query.getStatus())) DataSourceEnums.status(query.getStatus());
        if (StringUtils.isNotBlank(query.getLastTestStatus())) DataSourceEnums.testStatus(query.getLastTestStatus());
        if (query.getSecurityLevel() != null && !SecurityLevel.supports(query.getSecurityLevel())) {
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
            .authProfileCode(instance.getAuthProfileCode())
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

    private void publishProjection(DataSourceInstance instance,
                                   DataSourceAuthorizationProjectionEvent.Operation operation) {
        eventPublisher.publishEvent(new DataSourceAuthorizationProjectionEvent(
            operation,
            instance.getDatasourceId(),
            instance.getAuthzResourceId(),
            instance.getDatasourceCode(),
            instance.getDatasourceName(),
            instance.getConnectorCode(),
            instance.getEnvironment(),
            instance.getOwnerDeptId(),
            instance.getCreateBy(),
            instance.getAccessMode(),
            instance.getClassificationCode(),
            instance.getSecurityLevel(),
            instance.getStatus()
        ));
    }

}
