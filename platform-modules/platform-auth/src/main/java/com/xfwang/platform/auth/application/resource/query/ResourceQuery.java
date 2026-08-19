package com.xfwang.platform.auth.application.resource.query;

import lombok.Data;

/** 授权资源总账筛选条件。 */
@Data
public class ResourceQuery {
    private String keyword;
    private String resourceType;
    private String sourceModule;
    private String classification;
    private String status;
}
