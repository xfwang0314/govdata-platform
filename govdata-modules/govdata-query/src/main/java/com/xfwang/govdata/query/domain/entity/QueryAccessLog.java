package com.xfwang.govdata.query.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 查询访问追加记录，不保存 SQL、参数值或结果数据。 */
@Data
@TableName("gd_query_access_log")
public class QueryAccessLog {
    @TableId private Long accessLogId;
    private String tenantId;
    private String queryId;
    private Long datasourceId;
    private Long resourceId;
    private String actionCode;
    private String purpose;
    private String actorType;
    private String actorId;
    private String traceId;
    private String result;
    private String errorCode;
    private Integer fieldCount;
    private Integer filterCount;
    private Integer returnedRowCount;
    private Long durationMs;
    private LocalDateTime occurredTime;
}
