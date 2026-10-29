package com.xfwang.platform.auth.application.options.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** ABAC 策略、Grant 与资源选择器使用的后端目录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthzOptionsView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<OptionItem> resourceDomains;
    private List<OptionItem> resourceModules;
    private List<ResourceCategoryItem> resourceCategories;
    private List<ResourceTypeItem> resourceTypes;
    private List<OptionItem> resourceStatuses;
    private List<OptionItem> classifications;
    private List<OptionItem> securityLevels;
    private List<OptionItem> policyEffects;
    private List<OptionItem> policyRiskLevels;
    private List<OptionItem> policyStatuses;
    private List<OptionItem> policyVersionStatuses;
    private List<OptionItem> grantStatuses;
    private List<OptionItem> grantSourceTypes;
    private List<OptionItem> grantPurposes;
    private List<OptionItem> subjectTypes;
    private List<OptionItem> operators;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceTypeItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String domainCode;
        private String domainLabel;
        private String sourceModule;
        private String moduleLabel;
        private String categoryCode;
        private String typeCode;
        private String typeLabel;
        private List<String> selectionModes;
        private List<String> capabilities;
        private List<OptionItem> actions;
        private List<OptionItem> purposes;
        private List<AttributeItem> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceCategoryItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String code;
        private String label;
        private Integer order;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributeItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String path;
        private String label;
        private String valueType;
        private List<String> operators;
        private String operandRole;
        private String visibility;
        private List<OptionItem> valueOptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String label;
        private String value;
    }
}
