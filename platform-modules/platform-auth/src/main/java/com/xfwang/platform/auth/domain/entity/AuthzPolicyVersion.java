package com.xfwang.platform.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 不可变 ABAC 策略版本。
 */
@Data
@TableName("authz_policy_group_version")
public class AuthzPolicyVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long policyGroupVersionId;
    private String tenantId;
    private Long policyGroupId;
    private Integer versionNo;
    private Integer schemaVersion;
    private String policyNameSnapshot;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String descriptionSnapshot;
    private String riskLevel;
    private String effect;
    private String policyDocumentJson;
    private Integer priority;
    private String versionStatus;
    private String contentHash;
    private String catalogSnapshotJson;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long publishedBy;
    private LocalDateTime publishedTime;

    @Version
    private Integer lockVersion;
}
