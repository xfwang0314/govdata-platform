package com.xfwang.govdata.metadata.application.catalog.view;

import lombok.Builder;
import lombok.Value;

/** 表字段目录视图。 */
@Value
@Builder
public class MetadataColumnView {
    Long assetId;
    String assetCode;
    String originalName;
    String displayName;
    Integer ordinalPosition;
    String dataType;
    Integer lengthValue;
    Integer precisionValue;
    Integer scaleValue;
    Boolean nullableFlag;
    Boolean primaryKeyFlag;
    String defaultExpression;
    String commentText;
    Integer revisionNo;
    String technicalStatus;
}
