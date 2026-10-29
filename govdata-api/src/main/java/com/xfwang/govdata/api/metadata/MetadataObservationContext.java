package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 目录批量写入所需的受控运行上下文。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataObservationContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private Long harvestRunId;
    private Long datasourceId;
    private Long datasourceResourceId;
    private Long ownerDeptId;
    private String connectorCode;
    private String connectorVersion;
    private String actorType;
    private String actorId;
    private String traceId;
}
