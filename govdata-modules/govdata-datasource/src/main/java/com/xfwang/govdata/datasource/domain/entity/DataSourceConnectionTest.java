package com.xfwang.govdata.datasource.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 数据源连接测试追加写记录。 */
@Data
@TableName("gd_datasource_connection_test")
public class DataSourceConnectionTest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long testId;
    private String tenantId;
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
