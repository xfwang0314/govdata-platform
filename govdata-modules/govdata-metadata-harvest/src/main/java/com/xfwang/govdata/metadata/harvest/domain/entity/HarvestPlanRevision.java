package com.xfwang.govdata.metadata.harvest.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 采集计划不可变配置修订。 */
@Data
@TableName("gd_metadata_harvest_plan_revision")
public class HarvestPlanRevision implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId private Long planRevisionId;
    private String tenantId;
    private Long harvestPlanId;
    private Integer planRevisionNo;
    private Long datasourceId;
    private String scheduleType;
    private String cronExpression;
    private String scopeJson;
    private Boolean rawArchiveFlag;
    private String configHash;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime createTime;
}
