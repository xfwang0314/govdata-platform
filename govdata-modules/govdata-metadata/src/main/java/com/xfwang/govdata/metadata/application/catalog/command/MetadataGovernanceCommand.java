package com.xfwang.govdata.metadata.application.catalog.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 修改人工治理属性；采集拥有的技术字段不允许通过该命令覆盖。 */
@Data
public class MetadataGovernanceCommand {
    @Size(max = 200)
    private String displayName;
    @Size(max = 200)
    private String businessName;
    @Size(max = 500)
    private String description;
    private Long stewardUserId;
    @NotBlank
    @Size(max = 32)
    private String classificationCode;
    @NotNull
    @Min(1)
    @Max(5)
    private Integer securityLevel;
    @NotBlank
    @Size(max = 32)
    private String governanceStatus;
    @NotNull
    @Min(0)
    private Integer lockVersion;
}
