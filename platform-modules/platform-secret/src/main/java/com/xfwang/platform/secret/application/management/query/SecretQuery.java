package com.xfwang.platform.secret.application.management.query;

import lombok.Data;

/**
 * 凭据分页列表筛选 Query 查询对象。
 */
@Data
public class SecretQuery {
    /** 凭据名称模糊匹配 */
    private String secretName;
    /** 凭据类型编码精准匹配 */
    private String secretType;
    /** 所属模块编码精准匹配 */
    private String ownerModule;
    /** 凭据状态精准匹配（ACTIVE / DISABLED） */
    private String status;
}
