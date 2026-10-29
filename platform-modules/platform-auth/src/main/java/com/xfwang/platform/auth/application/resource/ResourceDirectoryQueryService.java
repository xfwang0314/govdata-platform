package com.xfwang.platform.auth.application.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xfwang.platform.api.auth.catalog.ResourceAuthorizationCapability;
import com.xfwang.platform.api.auth.catalog.ResourceNavigationCategory;
import com.xfwang.platform.api.auth.enums.ResourceStatus;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.resource.view.ResourceDirectoryNode;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 以虚拟分类为入口、按真实 Resource parentId 懒加载授权资源目录。 */
@Service
@RequiredArgsConstructor
public class ResourceDirectoryQueryService {

    private final AuthzResourceMapper resourceMapper;
    private final AuthorizationCatalog catalog;

    public List<ResourceDirectoryNode> roots(String categoryCode, String status) {
        ResourceNavigationCategory category = category(categoryCode);
        List<String> resourceTypes = catalog.resourceTypes().stream()
            .filter(type -> catalog.navigationCategory(type.sourceModule()) == category)
            .filter(type -> type.capabilities().contains(ResourceAuthorizationCapability.RESOURCE_CATALOG))
            .map(type -> type.typeCode())
            .toList();
        if (resourceTypes.isEmpty()) return List.of();
        List<AuthzResource> resources = resourceMapper.selectList(new LambdaQueryWrapper<AuthzResource>()
            .in(AuthzResource::getResourceType, resourceTypes)
            .isNull(AuthzResource::getParentId)
            .eq(AuthzResource::getStatus, status(status))
            .orderByAsc(AuthzResource::getResourceName));
        return nodes(resources, catalogResourceTypes(), status(status));
    }

    public List<ResourceDirectoryNode> children(Long parentId, String status) {
        if (parentId == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源父节点不能为空");
        }
        String normalizedStatus = status(status);
        Set<String> resourceTypes = catalogResourceTypes();
        if (resourceTypes.isEmpty()) return List.of();
        List<AuthzResource> resources = resourceMapper.selectList(new LambdaQueryWrapper<AuthzResource>()
            .eq(AuthzResource::getParentId, parentId)
            .in(AuthzResource::getResourceType, resourceTypes)
            .eq(AuthzResource::getStatus, normalizedStatus)
            .orderByAsc(AuthzResource::getResourceName));
        return nodes(resources, resourceTypes, normalizedStatus);
    }

    private List<ResourceDirectoryNode> nodes(List<AuthzResource> resources, Set<String> resourceTypes,
                                              String status) {
        if (resources.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        resources.forEach(resource -> ids.add(resource.getResourceId()));
        Set<Long> parentIds = new HashSet<>();
        resourceMapper.selectList(new LambdaQueryWrapper<AuthzResource>()
                .select(AuthzResource::getParentId)
                .in(AuthzResource::getParentId, ids)
                .in(AuthzResource::getResourceType, resourceTypes)
                .eq(AuthzResource::getStatus, status))
            .forEach(resource -> parentIds.add(resource.getParentId()));
        return resources.stream().map(resource -> ResourceDirectoryNode.builder()
            .id(resource.getResourceId()).parentId(resource.getParentId())
            .label(resource.getResourceName()).code(resource.getResourceCode())
            .resourceType(resource.getResourceType()).sourceModule(resource.getSourceModule())
            .status(resource.getStatus()).leaf(!parentIds.contains(resource.getResourceId())).build())
            .toList();
    }

    private Set<String> catalogResourceTypes() {
        Set<String> result = new HashSet<>();
        catalog.resourceTypes().stream()
            .filter(type -> type.capabilities().contains(ResourceAuthorizationCapability.RESOURCE_CATALOG))
            .map(type -> type.typeCode()).forEach(result::add);
        return result;
    }

    private ResourceNavigationCategory category(String code) {
        try {
            return ResourceNavigationCategory.valueOf(code == null ? "" : code);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源虚拟分类无效");
        }
    }

    private String status(String code) {
        for (ResourceStatus value : ResourceStatus.values()) {
            if (value.code().equals(code)) return code;
        }
        throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "授权资源状态参数无效");
    }
}
