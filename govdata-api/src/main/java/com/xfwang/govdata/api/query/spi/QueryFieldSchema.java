package com.xfwang.govdata.api.query.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 元数据模块提供给查询网关的受信任字段定义。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryFieldSchema {
    private Long fieldId;
    private String fieldCode;
    private String displayName;
    private String authorizationAttribute;
    private String physicalName;
    private Boolean selectable;
}
