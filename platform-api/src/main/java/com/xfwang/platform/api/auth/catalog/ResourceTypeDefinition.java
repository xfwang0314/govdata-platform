package com.xfwang.platform.api.auth.catalog;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/** 一个业务模块可注册的 Resource 类型及其授权 Schema。 */
public record ResourceTypeDefinition(String domainCode, String domainLabel,
                                     String sourceModule, String typeCode, String typeLabel,
                                     Set<String> parentTypeCodes,
                                     Set<ResourceSelectionMode> selectionModes,
                                     Set<ResourceAuthorizationCapability> capabilities,
                                     List<AuthorizationActionDefinition> actions,
                                     List<AuthorizationAttributeDefinition> attributes)
    implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceTypeDefinition {
        parentTypeCodes = parentTypeCodes == null ? Set.of() : Set.copyOf(parentTypeCodes);
        selectionModes = selectionModes == null ? Set.of() : Set.copyOf(selectionModes);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        actions = actions == null ? List.of() : List.copyOf(actions);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    public ResourceTypeDefinition(String domainCode, String domainLabel,
                                  String sourceModule, String typeCode, String typeLabel,
                                  Set<String> parentTypeCodes,
                                  Set<ResourceSelectionMode> selectionModes,
                                  List<AuthorizationActionDefinition> actions,
                                  List<AuthorizationAttributeDefinition> attributes) {
        this(domainCode, domainLabel, sourceModule, typeCode, typeLabel, parentTypeCodes,
            selectionModes, defaultCapabilities(selectionModes), actions, attributes);
    }

    private static Set<ResourceAuthorizationCapability> defaultCapabilities(
        Set<ResourceSelectionMode> selectionModes) {
        if (selectionModes == null) {
            return Set.of();
        }
        java.util.EnumSet<ResourceAuthorizationCapability> result =
            java.util.EnumSet.noneOf(ResourceAuthorizationCapability.class);
        if (selectionModes.contains(ResourceSelectionMode.TYPE)) {
            result.add(ResourceAuthorizationCapability.CUSTOM_POLICY);
        }
        if (selectionModes.contains(ResourceSelectionMode.INSTANCE)) {
            result.add(ResourceAuthorizationCapability.RESOURCE_CATALOG);
            result.add(ResourceAuthorizationCapability.GRANT);
            result.add(ResourceAuthorizationCapability.INSTANCE_DECISION);
        }
        return Set.copyOf(result);
    }
}
