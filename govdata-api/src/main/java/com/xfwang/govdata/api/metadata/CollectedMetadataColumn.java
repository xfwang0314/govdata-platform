package com.xfwang.govdata.api.metadata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 单个物理字段的规范化采集结构。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedMetadataColumn implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String columnName;
    private String canonicalKey;
    private String nativeObjectId;
    private String dataType;
    private Integer jdbcType;
    private Integer lengthValue;
    private Integer precisionValue;
    private Integer scaleValue;
    private Boolean nullableFlag;
    private Boolean primaryKeyFlag;
    private String defaultExpression;
    private String comment;
    private Integer ordinalPosition;
}
