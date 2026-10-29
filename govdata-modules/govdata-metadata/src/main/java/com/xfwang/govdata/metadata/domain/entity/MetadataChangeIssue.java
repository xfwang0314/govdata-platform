package com.xfwang.govdata.metadata.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 高风险元数据结构变更处置事项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gd_metadata_change_issue")
public class MetadataChangeIssue extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long changeIssueId;
    private String issueCode;
    private String issueType;
    private Long tableAssetId;
    private Integer fromRevisionNo;
    private Integer toRevisionNo;
    private String changeLevel;
    private String issueStatus;
    private String changeFingerprint;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String activeDedupeKey;
    private String changeSummaryJson;
    private LocalDateTime detectedTime;
    private String assigneeType;
    private String assigneeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime resolvedTime;
    private String traceId;
}
