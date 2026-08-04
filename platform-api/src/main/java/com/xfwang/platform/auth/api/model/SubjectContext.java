package com.xfwang.platform.auth.api.model;

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
 * ABAC Subject 属性快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;

    private Long userId;

    private Long deptId;

    @Builder.Default
    private Set<Long> deptPathIds = new HashSet<>();

    @Builder.Default
    private Set<Long> roleIds = new HashSet<>();

    @Builder.Default
    private Set<String> roleKeys = new HashSet<>();

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
