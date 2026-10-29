package com.xfwang.govdata.metadata.application.observation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** 权威全量采集的大规模消失保护参数。 */
@Data
@Validated
@ConfigurationProperties(prefix = "govdata.metadata.observation.missing-guard")
public class MetadataMissingGuardProperties {

    private boolean enabled = true;

    @Min(1)
    private int minimumBaselineCount = 20;

    @Min(1)
    private int minimumMissingCount = 10;

    @DecimalMin("0.01")
    @DecimalMax("1.00")
    private BigDecimal maximumMissingRate = new BigDecimal("0.30");
}
