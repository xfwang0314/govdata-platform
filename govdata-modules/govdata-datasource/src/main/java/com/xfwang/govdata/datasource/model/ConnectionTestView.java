package com.xfwang.govdata.datasource.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/** 连接测试的脱敏结果。 */
@Data
@Builder
public class ConnectionTestView implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long testId;
    private Long datasourceId;
    private String result;
    private String reasonCode;
    private String message;
    private Long latencyMs;
    private Map<String, Object> serverInfo;
    private LocalDateTime occurredTime;
}
