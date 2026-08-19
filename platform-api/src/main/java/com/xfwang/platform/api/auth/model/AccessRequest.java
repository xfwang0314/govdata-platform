package com.xfwang.platform.api.auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * ABAC 授权求值请求。
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
     * 不做授权投影的类型资源上下文。调用方必须从可信业务对象构造，不能直接接收客户端属性。
     * 与 resourceId 必须且只能填写一个。
     */
    private ResourceDescriptor resource;

    /**
     * 可选的预期资源类型，用于防止调用方传错资源。
     */
    private String resourceType;

    private String action;

    private EnvironmentContext environment;
}
