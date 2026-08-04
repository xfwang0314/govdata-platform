package com.xfwang.platform.auth.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.auth.api.model.ResourceDescriptor;
import com.xfwang.platform.auth.api.service.ResourceRegistry;
import com.xfwang.platform.auth.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.domain.AuthzResource;
import com.xfwang.platform.auth.mapper.AuthzResourceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            .eq(AuthzResource::getResourceCode, descriptor.getResourceCode()));
        if (descriptor.getParentId() != null) {
            AuthzResource parent = resourceMapper.selectById(descriptor.getParentId());
            if (parent == null) {
                throw new ServiceException(GlobalErrorCode.NOT_FOUND, "父授权资源不存在");
            }
            if (resource != null && resource.getResourceId().equals(parent.getResourceId())) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源不能以自身作为父资源");
            }
        }
        if (resource == null) {
            resource = new AuthzResource();
            resource.setStatus("ACTIVE");
            resource.setDelFlag("0");
            resource.setInheritPolicy(Boolean.TRUE);
            resource.setLockVersion(0);
        }
        copy(descriptor, resource);
        resource.setTenantId(tenantId);
        if (resource.getResourceId() == null) {
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
            || StringUtils.isBlank(resource.getResourceCode())
            || StringUtils.isBlank(resource.getResourceName())
            || StringUtils.isBlank(resource.getResourceType())
            || StringUtils.isBlank(resource.getBusinessId())
            || StringUtils.isBlank(resource.getSourceModule())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "资源编码、名称、类型、业务标识和来源模块不能为空");
        }
    }

    private void copy(ResourceDescriptor source, AuthzResource target) {
        target.setResourceCode(source.getResourceCode());
        target.setResourceName(source.getResourceName());
        target.setResourceType(source.getResourceType());
        target.setParentId(source.getParentId());
        target.setBusinessId(source.getBusinessId());
        target.setSourceModule(source.getSourceModule());
        target.setOwnerDeptId(source.getOwnerDeptId());
        target.setRegionCode(source.getRegionCode());
        target.setClassification(source.getClassification());
        target.setSecurityLevel(source.getSecurityLevel());
        target.setAttributesJson(jsonCodec.write(source.getAttributes()));
    }

    private ResourceDescriptor toDescriptor(AuthzResource resource) {
        Map<String, Object> attributes = StringUtils.isBlank(resource.getAttributesJson())
            ? Map.of()
            : jsonCodec.read(resource.getAttributesJson(), new TypeReference<Map<String, Object>>() { });
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
            .regionCode(resource.getRegionCode())
            .classification(resource.getClassification())
            .securityLevel(resource.getSecurityLevel())
            .status(resource.getStatus())
            .attributes(attributes == null ? Map.of() : attributes)
            .build();
    }
}
