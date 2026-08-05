package com.xfwang.govdata.datasource.domain.connector;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 页面生成 Connector 非敏感配置表单所需的字段描述。 */
@Value
@Builder
public class ConnectorFieldDescriptor {
    String code;
    String label;
    String valueType;
    boolean required;
    Object defaultValue;
    @Builder.Default
    List<String> options = List.of();
    String description;
}
