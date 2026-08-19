package com.xfwang.platform.auth.application.policy.view;

import lombok.Builder;
import lombok.Value;
import com.xfwang.platform.auth.application.policy.command.PolicyResourceCommand;
import com.xfwang.platform.auth.application.policy.command.PolicySubjectCommand;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 策略管理视图。 */
@Value
@Builder
public class PolicyView {
    Long policyId;
    String policyCode;
    String policyName;
    String policyGroupCode;
    Long policyVersionId;
    Integer versionNo;
    String versionStatus;
    Integer versionLockVersion;
    boolean hasDraft;
    Integer draftVersionNo;
    Integer currentVersionNo;
    String effect;
    String riskLevel;
    String status;
    Integer priority;
    String conditionJson;
    String obligationJson;
    List<PolicySubjectCommand> subjects;
    List<PolicyResourceCommand> resources;
    Set<String> actions;
    LocalDateTime createTime;
    LocalDateTime updateTime;
}
