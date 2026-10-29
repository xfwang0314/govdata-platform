package com.xfwang.govdata.metadata.harvest.application.plan.view;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HarvestRuleView {
    Long harvestRuleId;
    String ruleScope;
    String ruleType;
    String matchType;
    String rulePattern;
    Integer sortNo;
}
