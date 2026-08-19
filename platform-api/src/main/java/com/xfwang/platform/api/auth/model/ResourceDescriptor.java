package com.xfwang.platform.api.auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /** 管理页面展示用的跨模块祖先路径，不参与授权匹配。 */
    private String resourcePath;

    private String resourceType;

    private Long parentId;

    private String businessId;

    private String sourceModule;

    private Long ownerDeptId;

    private Long ownerUserId;

    @Builder.Default
    private Set<Long> ownerDeptPathIds = new HashSet<>();

    private String regionCode;

    private String classification;

    private Integer securityLevel;

    private String status;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
