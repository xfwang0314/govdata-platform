package com.xfwang.platform.auth.api.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 可授权资源的通用描述。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDescriptor implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long resourceId;

    private String tenantId;

    private String resourceCode;

    private String resourceName;

    private String resourceType;

    private Long parentId;

    private String businessId;

    private String sourceModule;

    private Long ownerDeptId;

    private String regionCode;

    private String classification;

    private Integer securityLevel;

    private String status;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
