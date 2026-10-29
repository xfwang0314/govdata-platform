package com.xfwang.govdata.metadata.harvest.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 元数据采集计划聚合根。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gd_metadata_harvest_plan")
public class HarvestPlan extends TenantEntity {
    @Serial
    private static final long serialVersionUID = 1L;
    @TableId
    private Long harvestPlanId;
    private String harvestPlanCode;
    private Long authzResourceId;
    private String harvestPlanName;
    private Long datasourceId;
    private Long datasourceResourceId;
    private String scheduleType;
    private String cronExpression;
    private Long ownerDeptId;
    private String planStatus;
    private Integer currentPlanRevisionNo;
    private String scopeJson;
    private Boolean rawArchiveFlag;
    @Version
    private Integer lockVersion;
    @TableLogic
    private String delFlag;
}
