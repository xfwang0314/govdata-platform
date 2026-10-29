package com.xfwang.govdata.metadata.harvest.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一次采集计划运行。
 */
@Data
@TableName("gd_metadata_harvest_run")
public class HarvestRun implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long harvestRunId;
    private String tenantId;
    private String harvestRunNo;
    private Long harvestPlanId;
    private Long planRevisionId;
    private Long datasourceId;
    private Long datasourceResourceId;
    private Long ownerDeptId;
    private String connectorCode;
    private String connectorVersion;
    private String triggerType;
    private String triggerRef;
    private String actorType;
    private String actorId;
    private String runStatus;
    private String activeDatasourceKey;
    private Boolean completeRunFlag;
    private Integer discoveredTableCount;
    private Integer changedTableCount;
    private Integer failedTableCount;
    private Integer activeTableCount;
    private Integer missingCandidateCount;
    private Integer missingMarkedCount;
    private Boolean missingSuppressedFlag;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    private String failureCode;
    private String failureSummary;
    private String traceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
