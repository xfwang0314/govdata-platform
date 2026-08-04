package com.xfwang.platform.auth.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 非父子型资源关系，例如 API 使用多个数据集或表。
 */
@Data
@TableName("authz_resource_relation")
public class AuthzResourceRelation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long relationId;

    private String tenantId;

    private Long sourceResourceId;

    private Long targetResourceId;

    private String relationType;

    private Long createDept;

    private Long createBy;

    private LocalDateTime createTime;
}
