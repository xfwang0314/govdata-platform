package com.xfwang.platform.auth.domain.policy;

import com.xfwang.platform.api.auth.model.EnvironmentContext;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.model.SubjectContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 读取 Subject、Resource、Environment 的安全属性。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AttributeResolver {

    static Object resolve(String path, SubjectContext subject, ResourceDescriptor resource,
                          EnvironmentContext environment) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        Object value = switch (parts[0]) {
            case "subject" -> subjectValue(subject, parts[1]);
            case "resource" -> resourceValue(resource, parts[1]);
            case "environment" -> environmentValue(environment, parts[1]);
            default -> null;
        };
        for (int i = 2; i < parts.length && value != null; i++) {
            value = nestedValue(value, parts[i]);
        }
        return value;
    }

    private static Object subjectValue(SubjectContext subject, String name) {
        if (subject == null) {
            return null;
        }
        return switch (name) {
            case "tenantId" -> subject.getTenantId();
            case "principalType" -> subject.getPrincipalType() == null
                ? null : subject.getPrincipalType().name();
            case "principalId" -> subject.getPrincipalId();
            case "userId" -> subject.getUserId();
            case "deptId" -> subject.getDeptId();
            case "deptPathIds" -> subject.getDeptPathIds();
            case "roleIds" -> subject.getRoleIds();
            case "roleKeys" -> subject.getRoleKeys();
            default -> subject.getAttributes() == null ? null : subject.getAttributes().get(name);
        };
    }

    private static Object resourceValue(ResourceDescriptor resource, String name) {
        if (resource == null) {
            return null;
        }
        return switch (name) {
            case "resourceId" -> resource.getResourceId();
            case "tenantId" -> resource.getTenantId();
            case "resourceCode" -> resource.getResourceCode();
            case "resourceName" -> resource.getResourceName();
            case "resourceType" -> resource.getResourceType();
            case "parentId" -> resource.getParentId();
            case "businessId" -> resource.getBusinessId();
            case "sourceModule" -> resource.getSourceModule();
            case "ownerDeptId" -> resource.getOwnerDeptId();
            case "ownerUserId" -> resource.getOwnerUserId();
            case "ownerDeptPathIds" -> resource.getOwnerDeptPathIds();
            case "regionCode" -> resource.getRegionCode();
            case "classification" -> resource.getClassification();
            case "securityLevel" -> resource.getSecurityLevel();
            case "status" -> resource.getStatus();
            default -> resource.getAttributes() == null ? null : resource.getAttributes().get(name);
        };
    }

    private static Object environmentValue(EnvironmentContext environment, String name) {
        if (environment == null) {
            return null;
        }
        return switch (name) {
            case "purpose" -> environment.getPurpose();
            case "requestTime" -> environment.getRequestTime();
            case "ip" -> environment.getIp();
            case "networkZone" -> environment.getNetworkZone();
            case "traceId" -> environment.getTraceId();
            default -> environment.getAttributes() == null ? null : environment.getAttributes().get(name);
        };
    }

    private static Object nestedValue(Object value, String name) {
        if (value instanceof Map<?, ?> map) {
            return map.get(name);
        }
        return null;
    }

    static boolean contains(Object container, Object item) {
        if (container instanceof Collection<?> collection) {
            return collection.contains(item);
        }
        if (container != null && container.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(container); i++) {
                if (java.util.Objects.equals(Array.get(container, i), item)) {
                    return true;
                }
            }
            return false;
        }
        return container instanceof CharSequence sequence
            && item != null
            && sequence.toString().contains(item.toString());
    }
}
