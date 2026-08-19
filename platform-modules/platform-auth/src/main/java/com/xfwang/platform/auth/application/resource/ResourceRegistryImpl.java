package com.xfwang.platform.auth.application.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import com.xfwang.platform.api.auth.enums.DataClassification;
import com.xfwang.platform.api.auth.enums.SecurityLevel;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.dromara.system.api.DeptService;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 租户内统一资源注册表。
 */
@Service
@RequiredArgsConstructor
public class ResourceRegistryImpl implements ResourceRegistry {

    private static final Set<String> RESOURCE_STATUSES = Set.of("ACTIVE", "DISABLED", "ARCHIVED");

    private final AuthzResourceMapper resourceMapper;
    private final AuthorizationJsonCodec jsonCodec;
    private final AuthorizationCatalog authorizationCatalog;
    private final DeptService deptService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceDescriptor register(ResourceDescriptor descriptor) {
        validate(descriptor);
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isNotBlank(descriptor.getTenantId())
            && !StringUtils.equals(descriptor.getTenantId(), tenantId)) {
            throw new ServiceException(GlobalErrorCode.FORBIDDEN, "禁止跨租户注册授权资源");
        }
        AuthzResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<AuthzResource>()
            .eq(AuthzResource::getSourceModule, descriptor.getSourceModule())
            .eq(AuthzResource::getResourceType, descriptor.getResourceType())
            .eq(AuthzResource::getBusinessId, descriptor.getBusinessId()));
        boolean newResource = resource == null;
        if (descriptor.getParentId() != null) {
            AuthzResource parent = resourceMapper.selectById(descriptor.getParentId());
            if (parent == null) {
                throw new ServiceException(GlobalErrorCode.NOT_FOUND, "父授权资源不存在");
            }
            if (resource != null && resource.getResourceId().equals(parent.getResourceId())) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源不能以自身作为父资源");
            }
            var definition = authorizationCatalog.findResourceType(descriptor.getResourceType()).orElseThrow();
            if (!definition.parentTypeCodes().contains(parent.getResourceType())) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源父类型不符合目录定义");
            }
        }
        if (resource == null) {
            long resourceId = IdWorker.getId();
            resource = new AuthzResource();
            resource.setResourceId(resourceId);
            resource.setResourceCode("res_" + Long.toUnsignedString(resourceId, 36));
            resource.setStatus("ACTIVE");
            resource.setDelFlag("0");
            resource.setInheritPolicy(Boolean.TRUE);
            resource.setLockVersion(0);
        }
        copy(descriptor, resource);
        resource.setTenantId(tenantId);
        if (newResource) {
            resourceMapper.insert(resource);
        } else {
            resourceMapper.updateById(resource);
        }
        return toDescriptor(resource);
    }

    @Override
    public Optional<ResourceDescriptor> findById(Long resourceId) {
        if (resourceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resourceMapper.selectById(resourceId)).map(this::toDescriptor);
    }

    @Override
    public Optional<ResourceDescriptor> findByCode(String resourceCode) {
        if (StringUtils.isBlank(resourceCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(resourceMapper.selectOne(new LambdaQueryWrapper<AuthzResource>()
                .eq(AuthzResource::getResourceCode, resourceCode)))
            .map(this::toDescriptor);
    }

    @Override
    public Optional<ResourceDescriptor> findByBusinessKey(String sourceModule, String resourceType,
                                                          String businessId) {
        if (StringUtils.isAnyBlank(sourceModule, resourceType, businessId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(resourceMapper.selectOne(new LambdaQueryWrapper<AuthzResource>()
                .eq(AuthzResource::getSourceModule, sourceModule)
                .eq(AuthzResource::getResourceType, resourceType)
                .eq(AuthzResource::getBusinessId, businessId)))
            .map(this::toDescriptor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceDescriptor changeStatus(Long resourceId, String status) {
        if (resourceId == null || !RESOURCE_STATUSES.contains(status)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源状态参数无效");
        }
        AuthzResource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new ServiceException(GlobalErrorCode.NOT_FOUND, "授权资源不存在");
        }
        resource.setStatus(status);
        resourceMapper.updateById(resource);
        return toDescriptor(resource);
    }

    private void validate(ResourceDescriptor resource) {
        if (resource == null
            || StringUtils.isBlank(resource.getResourceName())
            || StringUtils.isBlank(resource.getResourceType())
            || StringUtils.isBlank(resource.getBusinessId())
            || StringUtils.isBlank(resource.getSourceModule())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "资源名称、类型、业务标识和来源模块不能为空");
        }
        var definition = authorizationCatalog.findResourceType(resource.getResourceType())
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "资源类型未在授权目录注册"));
        if (!definition.sourceModule().equals(resource.getSourceModule())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "资源类型不属于声明的来源模块");
        }
        if (StringUtils.isNotBlank(resource.getClassification())
            && !DataClassification.supports(resource.getClassification())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "资源安全分类无效");
        }
        if (resource.getSecurityLevel() != null && !SecurityLevel.supports(resource.getSecurityLevel())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "资源安全等级必须在1到5之间");
        }
    }

    private void copy(ResourceDescriptor source, AuthzResource target) {
        target.setResourceName(source.getResourceName());
        target.setResourceType(source.getResourceType());
        target.setParentId(source.getParentId());
        target.setBusinessId(source.getBusinessId());
        target.setSourceModule(source.getSourceModule());
        target.setOwnerDeptId(source.getOwnerDeptId());
        target.setOwnerUserId(source.getOwnerUserId());
        Set<Long> ownerDeptPathIds = source.getOwnerDeptPathIds();
        if ((ownerDeptPathIds == null || ownerDeptPathIds.isEmpty()) && source.getOwnerDeptId() != null) {
            ownerDeptPathIds = deptService.selectDeptPathIds(source.getOwnerDeptId());
        }
        target.setOwnerDeptPathJson(jsonCodec.write(ownerDeptPathIds == null ? Set.of() : ownerDeptPathIds));
        target.setRegionCode(source.getRegionCode());
        target.setClassification(source.getClassification());
        target.setSecurityLevel(source.getSecurityLevel());
        target.setAttributesJson(jsonCodec.write(source.getAttributes()));
    }

    private ResourceDescriptor toDescriptor(AuthzResource resource) {
        Map<String, Object> attributes = StringUtils.isBlank(resource.getAttributesJson())
            ? Map.of()
            : jsonCodec.read(resource.getAttributesJson(), new TypeReference<Map<String, Object>>() { });
        Set<Long> ownerDeptPathIds = StringUtils.isBlank(resource.getOwnerDeptPathJson())
            ? Set.of()
            : jsonCodec.read(resource.getOwnerDeptPathJson(), new TypeReference<Set<Long>>() { });
        return ResourceDescriptor.builder()
            .resourceId(resource.getResourceId())
            .tenantId(resource.getTenantId())
            .resourceCode(resource.getResourceCode())
            .resourceName(resource.getResourceName())
            .resourceType(resource.getResourceType())
            .parentId(resource.getParentId())
            .businessId(resource.getBusinessId())
            .sourceModule(resource.getSourceModule())
            .ownerDeptId(resource.getOwnerDeptId())
            .ownerUserId(resource.getOwnerUserId())
            .ownerDeptPathIds(ownerDeptPathIds == null ? Set.of() : ownerDeptPathIds)
            .regionCode(resource.getRegionCode())
            .classification(resource.getClassification())
            .securityLevel(resource.getSecurityLevel())
            .status(resource.getStatus())
            .attributes(attributes == null ? Map.of() : attributes)
            .build();
    }
}
