package com.xfwang.govdata.metadata.harvest.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 采集运行最终失败明细。 */
@Data
@TableName("gd_metadata_harvest_run_failure")
public class HarvestRunFailure implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId private Long runFailureId;
    private String tenantId;
    private Long harvestRunId;
    private String failureScope;
    private String objectKey;
    private Integer attemptNo;
    private String failureCode;
    private String failureSummary;
    private LocalDateTime occurredTime;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime createTime;
}
