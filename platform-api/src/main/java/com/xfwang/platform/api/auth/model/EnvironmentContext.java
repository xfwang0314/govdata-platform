package com.xfwang.platform.api.auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ABAC Environment 属性快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String purpose;

    private LocalDateTime requestTime;

    private String ip;

    private String networkZone;

    private String traceId;

    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();
}
