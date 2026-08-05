package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorFieldDescriptor;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 汇总 Connector 描述并统一校验页面提交的非敏感配置。 */
@Component
@RequiredArgsConstructor
public class ConnectorCatalog {

    private final List<ConnectorDescriptorProvider> providers;

    public List<ConnectorDescriptor> list() {
        return providers.stream().map(ConnectorDescriptorProvider::descriptor)
            .sorted(Comparator.comparing(ConnectorDescriptor::getConnectorCode)).toList();
    }

    public ConnectorDescriptor require(String connectorCode) {
        Map<String, ConnectorDescriptor> descriptors = descriptors();
        ConnectorDescriptor descriptor = descriptors.get(normalize(connectorCode));
        if (descriptor == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "不支持的 Connector: " + connectorCode);
        }
        return descriptor;
    }

    public ConnectorDescriptor validate(String connectorCode, Map<String, Object> endpoint) {
        ConnectorDescriptor descriptor = require(connectorCode);
        Map<String, ConnectorFieldDescriptor> fields = descriptor.getEndpointFields().stream()
            .collect(Collectors.toMap(ConnectorFieldDescriptor::getCode, Function.identity()));
        Set<String> unknown = new HashSet<>(endpoint.keySet());
        unknown.removeAll(fields.keySet());
        if (!unknown.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "Connector 包含未注册配置项: " + String.join(",", unknown));
        }
        for (ConnectorFieldDescriptor field : fields.values()) {
            Object value = endpoint.get(field.getCode());
            if (value == null && field.getDefaultValue() != null) {
                endpoint.put(field.getCode(), field.getDefaultValue());
                value = field.getDefaultValue();
            }
            if (field.isRequired() && (value == null || value.toString().isBlank())) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                    field.getLabel() + "不能为空");
            }
            validateType(field, value);
        }
        return descriptor;
    }

    private void validateType(ConnectorFieldDescriptor field, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        if ("INTEGER".equals(field.getValueType())) {
            try {
                int parsed = Integer.parseInt(text);
                if (parsed < 0 || ("port".equals(field.getCode()) && (parsed < 1 || parsed > 65535))) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ex) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                    field.getLabel() + "必须是有效整数");
            }
        }
        if ("BOOLEAN".equals(field.getValueType())
            && !"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                field.getLabel() + "必须是布尔值");
        }
        if (!field.getOptions().isEmpty()
            && field.getOptions().stream().noneMatch(option -> option.equalsIgnoreCase(text))) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                field.getLabel() + "不在允许值范围内");
        }
    }

    private Map<String, ConnectorDescriptor> descriptors() {
        Map<String, ConnectorDescriptor> result = providers.stream()
            .map(ConnectorDescriptorProvider::descriptor)
            .collect(Collectors.toMap(value -> normalize(value.getConnectorCode()), Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("重复 Connector 编码: " + left.getConnectorCode());
                }));
        return Map.copyOf(result);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
