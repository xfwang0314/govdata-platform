package com.xfwang.govdata.datasource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 外部数据源连接实例持久化模型。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gd_datasource")
public class DataSourceInstance extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long datasourceId;
    private Long authzResourceId;
    private String datasourceCode;
    private String datasourceName;
    private String connectorCode;
    private String connectorVersion;
    private String endpointConfigJson;
    private String secretRef;
    private String environment;
    private Long ownerDeptId;
    private Boolean readonlyFlag;
    private String classificationLevel;
    private Integer securityLevel;
    private String status;
    private String lastTestStatus;
    private LocalDateTime lastTestTime;
    @Version
    private Integer lockVersion;
    @TableLogic
    private String delFlag;
}
