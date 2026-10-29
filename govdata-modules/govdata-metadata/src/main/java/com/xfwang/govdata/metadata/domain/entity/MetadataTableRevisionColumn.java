package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表修订中的完整字段快照。
 */
@Data
@TableName("gd_metadata_table_revision_column")
public class MetadataTableRevisionColumn implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long revisionColumnId;
    private String tenantId;
    private Long tableRevisionId;
    private Long columnAssetId;
    private Integer columnRevisionNo;
    private String originalName;
    private Integer ordinalPosition;
    private String dataType;
    private String nativeType;
    private Integer lengthValue;
    private Integer precisionValue;
    private Integer scaleValue;
    private Boolean nullableFlag;
    private Boolean primaryKeyFlag;
    private String defaultExpression;
    private String commentText;
    private String structureHash;
    private String contentHash;
    private LocalDateTime createTime;
}
