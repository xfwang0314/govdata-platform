package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表资产当前技术明细。
 */
@Data
@TableName("gd_metadata_table")
public class MetadataTable implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long assetId;
    private String tenantId;
    private String tableType;
    private Long rowEstimate;
    private Long dataSizeBytes;
    private String engineName;
    private Boolean partitionedFlag;
    private String commentText;
    private String propertiesJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
