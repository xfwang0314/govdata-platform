package com.xfwang.govdata.datasource.connector;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/** Connector 描述构造辅助，避免各 Adapter 重复页面字段约定。 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConnectorDescriptorSupport {

    public static ConnectorFieldDescriptor textField(String code, String label, boolean required, String description) {
        return ConnectorFieldDescriptor.builder().code(code).label(label).valueType("STRING")
            .required(required).description(description).build();
    }

    public static ConnectorFieldDescriptor integerField(String code, String label, boolean required, int defaultValue) {
        return ConnectorFieldDescriptor.builder().code(code).label(label).valueType("INTEGER")
            .required(required).defaultValue(defaultValue).build();
    }

    public static ConnectorFieldDescriptor booleanField(String code, String label, boolean defaultValue, String description) {
        return ConnectorFieldDescriptor.builder().code(code).label(label).valueType("BOOLEAN")
            .defaultValue(defaultValue).description(description).build();
    }

    public static ConnectorFieldDescriptor choiceField(String code, String label, String defaultValue,
                                                   List<String> options) {
        return ConnectorFieldDescriptor.builder().code(code).label(label).valueType("ENUM")
            .defaultValue(defaultValue).options(options).build();
    }
}
