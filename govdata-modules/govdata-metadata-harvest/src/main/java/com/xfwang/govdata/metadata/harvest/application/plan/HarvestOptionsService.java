package com.xfwang.govdata.metadata.harvest.application.plan;

import com.xfwang.govdata.metadata.harvest.application.plan.view.HarvestOptionView;
import com.xfwang.govdata.metadata.harvest.application.plan.view.HarvestOptionsView;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestMatchType;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRuleScope;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRuleType;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRunStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestScheduleType;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/** 为采集配置页面提供与领域枚举同源的固定选项。 */
@Service
public class HarvestOptionsService {

    /** 返回采集计划和运行页面允许提交的全部封闭值。 */
    public HarvestOptionsView options() {
        return HarvestOptionsView.builder()
            .scheduleTypes(Arrays.stream(HarvestScheduleType.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .planStatuses(Arrays.stream(HarvestActivationStatus.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .ruleScopes(Arrays.stream(HarvestRuleScope.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .ruleTypes(Arrays.stream(HarvestRuleType.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .matchTypes(Arrays.stream(HarvestMatchType.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .runStatuses(Arrays.stream(HarvestRunStatus.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .build();
    }

    private HarvestOptionView option(String label, String code) {
        return new HarvestOptionView(label, code);
    }
}
