package com.xfwang.govdata.metadata.harvest.application.plan.command;

import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestActivationStatus;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestScheduleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集计划创建与修改命令。
 */
@Data
public class HarvestPlanCommand {
    @NotBlank
    @Size(max = 200)
    private String harvestPlanName;
    @NotNull
    private Long datasourceId;
    @Size(max = 200)
    private String catalogName;
    @Size(max = 200)
    private String schemaName;
    @Min(1)
    @Max(1000)
    private Integer maxTableCount = 1000;
    @NotBlank
    private String scheduleType = HarvestScheduleType.MANUAL.getCode();
    @Size(max = 100)
    private String cronExpression;
    @NotNull
    private Long ownerDeptId;
    private String planStatus = HarvestActivationStatus.DISABLED.getCode();
    private Boolean rawArchiveFlag = false;
    private Integer lockVersion;
    @Valid
    @Size(max = 200)
    private List<HarvestRuleCommand> rules = new ArrayList<>();
}
