package com.xfwang.platform.auth.application.policy.command;

import java.util.List;
import java.util.Set;

/** 策略版本内容的内部校验接口。 */
public interface PolicyContentCommand {

    String getPolicyName();

    String getEffect();

    String getConditionJson();

    String getObligationJson();

    Integer getPriority();

    String getRiskLevel();

    List<PolicySubjectCommand> getSubjects();

    List<PolicyResourceCommand> getResources();

    Set<String> getActions();
}
