package com.xfwang.platform.auth.application.resource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.resource.query.ResourceQuery;
import com.xfwang.platform.auth.application.resource.view.ResourceTreeNode;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 资源投影管理查询，不承担领域资源写入。 */
@Service
@RequiredArgsConstructor
public class ResourceQueryService {

    private static final int MAX_RESOURCE_DEPTH = 32;

    private final AuthzResourceMapper resourceMapper;
    private final AuthorizationJsonCodec jsonCodec;

    public PageResult<ResourceDescriptor> page(ResourceQuery query, PageQuery pageQuery) {
        ResourceQuery filter = query == null ? new ResourceQuery() : query;
        boolean hasKeyword = StringUtils.isNotBlank(filter.getKeyword());
        LambdaQueryWrapper<AuthzResource> wrapper = new LambdaQueryWrapper<AuthzResource>()
            .eq(filter.getResourceId() != null, AuthzResource::getResourceId, filter.getResourceId())
            .and(hasKeyword, value -> value
                .like(AuthzResource::getResourceName, filter.getKeyword())
                .or().like(AuthzResource::getResourceCode, filter.getKeyword())
                .or().like(AuthzResource::getBusinessId, filter.getKeyword()))
            .eq(StringUtils.isNotBlank(filter.getResourceType()), AuthzResource::getResourceType,
                filter.getResourceType())
            .eq(StringUtils.isNotBlank(filter.getSourceModule()), AuthzResource::getSourceModule,
                filter.getSourceModule())
            .eq(StringUtils.isNotBlank(filter.getClassification()), AuthzResource::getClassification,
                filter.getClassification())
            .eq(StringUtils.isNotBlank(filter.getStatus()), AuthzResource::getStatus, filter.getStatus());

        // 非关键字搜索时默认按根节点分页
        if (!hasKeyword && filter.getResourceId() == null) {
            wrapper.isNull(AuthzResource::getParentId);
        }

        wrapper.orderByDesc(AuthzResource::getCreateTime)
            .orderByDesc(AuthzResource::getResourceId);
        Page<AuthzResource> page = resourceMapper.selectPage(pageQuery.build(), wrapper);
        List<AuthzResource> records = new ArrayList<>(page.getRecords());
        if (!hasKeyword && !records.isEmpty()) {
            List<AuthzResource> descendants = loadDescendants(records);
            records.addAll(descendants);
        }
        Map<Long, AuthzResource> resources = loadAncestors(records);
        return PageResult.build(records.stream()
            .map(resource -> toDescriptor(resource, resources)).toList(), page.getTotal());
    }

    public List<ResourceTreeNode> tree(String resourceType, String status) {
        LambdaQueryWrapper<AuthzResource> wrapper = new LambdaQueryWrapper<AuthzResource>()
            .eq(AuthzResource::getResourceType, resourceType)
            .eq(StringUtils.isNotBlank(status), AuthzResource::getStatus, status)
            .orderByAsc(AuthzResource::getResourceName);
        List<AuthzResource> all = resourceMapper.selectList(wrapper);
        Map<Long, ResourceTreeNode> nodeMap = new LinkedHashMap<>();
        for (AuthzResource resource : all) {
            nodeMap.put(resource.getResourceId(), ResourceTreeNode.builder()
                .id(resource.getResourceId())
                .label(resource.getResourceName())
                .code(resource.getResourceCode())
                .status(resource.getStatus())
                .parentId(resource.getParentId())
                .children(new ArrayList<>())
                .build());
        }
        List<ResourceTreeNode> roots = new ArrayList<>();
        for (ResourceTreeNode node : nodeMap.values()) {
            if (node.getParentId() != null && nodeMap.containsKey(node.getParentId())) {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private List<AuthzResource> loadDescendants(List<AuthzResource> rootResources) {
        List<AuthzResource> allDescendants = new ArrayList<>();
        Set<Long> parentIds = new HashSet<>();
        Set<Long> finalParentIds = parentIds;
        rootResources.forEach(r -> finalParentIds.add(r.getResourceId()));
        int depth = 0;
        while (!parentIds.isEmpty() && depth++ < MAX_RESOURCE_DEPTH) {
            List<AuthzResource> children = resourceMapper.selectList(
                new LambdaQueryWrapper<AuthzResource>()
                    .in(AuthzResource::getParentId, parentIds)
                    .orderByAsc(AuthzResource::getResourceName));
            if (children.isEmpty()) {
                break;
            }
            allDescendants.addAll(children);
            parentIds = new HashSet<>();
            Set<Long> finalParentIds1 = parentIds;
            children.forEach(c -> finalParentIds1.add(c.getResourceId()));
        }
        return allDescendants;
    }

    private Map<Long, AuthzResource> loadAncestors(List<AuthzResource> pageResources) {
        Map<Long, AuthzResource> resources = new HashMap<>();
        pageResources.forEach(resource -> resources.put(resource.getResourceId(), resource));
        Set<Long> parentIds = parentIds(pageResources, resources.keySet());
        int depth = 0;
        while (!parentIds.isEmpty() && depth++ < MAX_RESOURCE_DEPTH) {
            List<AuthzResource> parents = resourceMapper.selectByIds(parentIds);
            parents.forEach(parent -> resources.put(parent.getResourceId(), parent));
            parentIds = parentIds(parents, resources.keySet());
        }
        return resources;
    }

    private Set<Long> parentIds(List<AuthzResource> resources, Set<Long> loadedIds) {
        Set<Long> result = new HashSet<>();
        resources.stream().map(AuthzResource::getParentId)
            .filter(java.util.Objects::nonNull)
            .filter(parentId -> !loadedIds.contains(parentId))
            .forEach(result::add);
        return result;
    }

    private ResourceDescriptor toDescriptor(AuthzResource resource, Map<Long, AuthzResource> resources) {
        Map<String, Object> attributes = StringUtils.isBlank(resource.getAttributesJson()) ? Map.of()
            : jsonCodec.read(resource.getAttributesJson(), new TypeReference<Map<String, Object>>() { });
        return ResourceDescriptor.builder()
            .resourceId(resource.getResourceId()).tenantId(resource.getTenantId())
            .resourceCode(resource.getResourceCode()).resourceName(resource.getResourceName())
            .resourcePath(resourcePath(resource, resources))
            .resourceType(resource.getResourceType()).parentId(resource.getParentId())
            .businessId(resource.getBusinessId()).sourceModule(resource.getSourceModule())
            .ownerDeptId(resource.getOwnerDeptId()).regionCode(resource.getRegionCode())
            .classification(resource.getClassification()).securityLevel(resource.getSecurityLevel())
            .status(resource.getStatus()).attributes(attributes == null ? Map.of() : attributes).build();
    }

    private String resourcePath(AuthzResource resource, Map<Long, AuthzResource> resources) {
        ArrayDeque<String> names = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        AuthzResource current = resource;
        int depth = 0;
        while (current != null && depth++ < MAX_RESOURCE_DEPTH && visited.add(current.getResourceId())) {
            names.addFirst(current.getResourceName());
            current = current.getParentId() == null ? null : resources.get(current.getParentId());
        }
        return String.join(" / ", names);
    }
}
