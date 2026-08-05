package com.xfwang.platform.api.auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * Resource + Action 授权请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 留空时由当前 SubjectProvider 从登录态构建。
     */
    private SubjectContext subject;

    private Long resourceId;

    /**
     * 可选的预期资源类型，用于防止调用方传错资源。
     */
    private String resourceType;

    private String action;

    private EnvironmentContext environment;
}
