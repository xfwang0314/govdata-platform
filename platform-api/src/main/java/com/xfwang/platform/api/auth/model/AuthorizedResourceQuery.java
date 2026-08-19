package com.xfwang.platform.api.auth.model;

import lombok.Builder;
import lombok.Value;

import java.io.Serial;
import java.io.Serializable;

/** 查询当前主体对某类资源可执行指定动作的资源集合。 */
@Value
@Builder
public class AuthorizedResourceQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    String resourceType;
    String action;
    String purpose;
}
