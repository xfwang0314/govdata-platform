package com.xfwang.govdata.metadata.application.history.view;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetadataRevisionColumnView {
    Long columnAssetId;
    Integer columnRevisionNo;
    String originalName;
    Integer ordinalPosition;
    String dataType;
    String nativeType;
    Integer lengthValue;
    Integer precisionValue;
    Integer scaleValue;
    Boolean nullableFlag;
    Boolean primaryKeyFlag;
    String defaultExpression;
    String commentText;
}
