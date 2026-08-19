package com.xfwang.platform.auth.application.policy.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** 策略管理唯一主视图，一条记录对应一个策略组聚合。 */
@Value
@Builder
public class PolicyGroupView {
    Long policyGroupId;
    String policyGroupCode;
    String status;
    Integer groupLockVersion;
    String policyName;
    String description;
    String effect;
    String riskLevel;
    Integer priority;
    boolean hasDraft;
    Integer currentVersionNo;
    Integer draftVersionNo;
    PolicyVersionView currentVersion;
    PolicyVersionView draftVersion;
    boolean deleted;
    Long deletedBy;
    LocalDateTime deletedTime;
    String deleteReason;
    LocalDateTime createTime;
    LocalDateTime updateTime;
}
