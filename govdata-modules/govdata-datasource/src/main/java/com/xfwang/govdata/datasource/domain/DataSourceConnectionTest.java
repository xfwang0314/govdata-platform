package com.xfwang.govdata.datasource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 数据源连接测试追加写记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gd_datasource_connection_test")
public class DataSourceConnectionTest extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long testId;
    private Long datasourceId;
    private String connectorCode;
    private String connectorVersion;
    private String result;
    private String reasonCode;
    private Long latencyMs;
    private String serverInfoJson;
    private String actorType;
    private String actorId;
    private String traceId;
    private LocalDateTime occurredTime;
}
