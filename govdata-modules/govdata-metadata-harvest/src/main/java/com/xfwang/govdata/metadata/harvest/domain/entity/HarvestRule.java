package com.xfwang.govdata.metadata.harvest.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 单值、可验证的采集范围规则。 */
@Data
@TableName("gd_metadata_harvest_rule")
public class HarvestRule implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId private Long harvestRuleId;
    private String tenantId;
    private Long planRevisionId;
    private String ruleScope;
    private String ruleType;
    private String matchType;
    private String rulePattern;
    private Integer sortNo;
    private LocalDateTime createTime;
}
