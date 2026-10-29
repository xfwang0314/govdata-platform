package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 目录有界批次写入摘要。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataBatchWriteResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer observedCount;
    private Integer changedTableCount;
    private Integer breakingChangeCount;
}
