package com.xfwang.platform.auth.application.grant.query;

import lombok.Data;

/** 运行时 Grant 筛选条件。 */
@Data
public class GrantQuery {
    private String keyword;
    private String sourceType;
    private String granteeType;
    private String purpose;
    private String status;
    private Boolean deleted;
}
