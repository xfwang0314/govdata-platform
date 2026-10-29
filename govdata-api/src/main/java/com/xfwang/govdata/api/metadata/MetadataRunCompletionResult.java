package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 一次权威采集完成后的缺失检测摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataRunCompletionResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer activeAssetCount;
    private Integer missingCandidateCount;
    private Integer missingMarkedCount;
    private Boolean missingSuppressed;

    public static MetadataRunCompletionResult skipped() {
        return MetadataRunCompletionResult.builder()
            .activeAssetCount(0)
            .missingCandidateCount(0)
            .missingMarkedCount(0)
            .missingSuppressed(false)
            .build();
    }
}
