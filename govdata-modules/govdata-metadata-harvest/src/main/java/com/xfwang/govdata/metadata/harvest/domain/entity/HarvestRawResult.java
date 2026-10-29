package com.xfwang.govdata.metadata.harvest.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 采集原始结果的受控对象存储引用。 */
@Data
@TableName("gd_metadata_harvest_raw_result")
public class HarvestRawResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long rawResultId;
    private String tenantId;
    private Long harvestRunId;
    private Integer batchNo;
    private String objectStorageRef;
    private String contentHash;
    private Long contentSize;
    private String contentFormat;
    private LocalDateTime retentionExpireTime;
    private String traceId;
    private LocalDateTime createTime;
}
