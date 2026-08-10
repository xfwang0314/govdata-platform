package com.xfwang.govdata.datasource.infrastructure.connector;

import com.xfwang.govdata.datasource.domain.connector.ConnectorAuthProfile;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.connector.ConnectorFieldDescriptor;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectorEndpointValidationContext;
import com.xfwang.govdata.datasource.infrastructure.connector.support.ConnectorEndpointValidator;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Connector 注册表与端点参数校验器。
 *
 * <p>负责汇总所有 {@link ConnectorDescriptorProvider} 注册的描述符，校验前端提交的基础连接参数，
 * 执行正则防注入与敏感词过滤，并派生生成合法的连接目标预览字符串。</p>
 */
@Component
@RequiredArgsConstructor
public class ConnectorCatalog {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "user", "username", "password", "passwd", "pwd", "token", "secret", "privatekey", "accesskey");
    private static final Pattern PROPERTY_KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{0,127}$");
    private static final Pattern HOST = Pattern.compile("^[a-zA-Z0-9._-]{1,253}$");
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_$-]{1,128}$");

    private final List<ConnectorDescriptorProvider> providers;
    private final List<ConnectorEndpointValidator> endpointValidators;

    /**
     * 获取所有已注册的 Connector 描述符列表。
     *
     * @return Connector 描述符列表
     */
    public List<ConnectorDescriptor> list() {
        return providers.stream().map(ConnectorDescriptorProvider::descriptor)
            .sorted(Comparator.comparing(ConnectorDescriptor::getConnectorCode)).toList();
    }

    /**
     * 根据 Connector 编码获取描述符，不存在时抛出校验异常。
     *
     * @param connectorCode Connector 编码
     * @return Connector 描述符
     * @throws ServiceException 如果 Connector 编码未注册
     */
    public ConnectorDescriptor require(String connectorCode) {
        ConnectorDescriptor descriptor = descriptors().get(normalize(connectorCode));
        if (descriptor == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "不支持的 Connector: " + connectorCode);
        }
        return descriptor;
    }

    /**
     * 获取 Connector 支持的认证 Profile，不支持时抛出校验异常。
     *
     * @param descriptor  Connector 描述符
     * @param profileCode 认证 Profile 编码
     * @return 认证 Profile
     * @throws ServiceException 如果认证 Profile 不支持
     */
    public ConnectorAuthProfile requireAuthProfile(ConnectorDescriptor descriptor, String profileCode) {
        return descriptor.getAuthProfiles().stream()
            .filter(profile -> profile.getProfileCode().equalsIgnoreCase(profileCode))
            .findFirst().orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "Connector 不支持认证方式: " + profileCode));
    }

    /**
     * 校验数据源端点配置参数，补充默认值，生成派生字段，并调用自定义端点校验逻辑。
     *
     * @param connectorCode   Connector 编码
     * @param authProfileCode 认证 Profile 编码
     * @param environment     部署环境
     * @param endpoint        端点配置 Map（将被就地规范化修改）
     * @return 匹配的 Connector 描述符
     * @throws ServiceException 如果包含未注册字段、缺失必填项、格式非法或校验不通过
     */
    public ConnectorDescriptor validate(String connectorCode, String authProfileCode,
                                         String environment, Map<String, Object> endpoint) {
        ConnectorDescriptor descriptor = require(connectorCode);
        requireAuthProfile(descriptor, authProfileCode);
        Map<String, ConnectorFieldDescriptor> fields = descriptor.getEndpointFields().stream()
            .filter(field -> field.appliesTo(authProfileCode))
            .collect(Collectors.toMap(ConnectorFieldDescriptor::code, Function.identity()));
        Set<String> allowed = new HashSet<>(fields.keySet());
        allowed.add("properties");
        Set<String> unknown = new HashSet<>(endpoint.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                "Connector 包含未注册或不可提交的配置项: " + String.join(",", unknown));
        }
        fields.values().forEach(field -> validateField(endpoint, field));
        endpoint.put("properties", validateProperties(descriptor, endpoint.get("properties")));
        if (descriptor.getConnectionPreview() != null) {
            endpoint.put("connectionTarget", buildConnectionTarget(descriptor, endpoint));
        }
        endpointValidators.stream()
            .filter(validator -> validator.supports(descriptor.getConnectorCode(), descriptor.getConnectorVersion()))
            .forEach(validator -> validator.validate(new ConnectorEndpointValidationContext(
                descriptor, authProfileCode, environment, endpoint)));
        return descriptor;
    }

    private void validateField(Map<String, Object> endpoint, ConnectorFieldDescriptor field) {
        Object value = endpoint.get(field.code());
        if (value == null && field.defaultValue() != null) {
            value = field.defaultValue();
            endpoint.put(field.code(), value);
        }
        if (field.required() && (value == null || value.toString().isBlank())) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field.label() + "不能为空");
        }
        if (value == null) return;
        String text = value.toString().trim();
        if ("INTEGER".equals(field.valueType())) {
            try {
                int number = Integer.parseInt(text);
                int minValue = field.minValue() == null ? Integer.MIN_VALUE : field.minValue();
                int maxValue = field.maxValue() == null ? Integer.MAX_VALUE : field.maxValue();
                if (number < minValue || number > maxValue) throw new NumberFormatException();
                endpoint.put(field.code(), number);
            } catch (NumberFormatException exception) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field.label() + "超出允许范围");
            }
        } else {
            if ("host".equals(field.code()) && !HOST.matcher(text).matches()) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "主机地址格式无效");
            }
            if (Set.of("database", "serviceName").contains(field.code()) && !IDENTIFIER.matcher(text).matches()) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field.label() + "格式无效");
            }
            if (!field.options().isEmpty()
                && field.options().stream().noneMatch(option -> option.equalsIgnoreCase(text))) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field.label() + "不在允许范围内");
            }
            endpoint.put(field.code(), text);
        }
    }

    private String buildConnectionTarget(ConnectorDescriptor descriptor, Map<String, Object> endpoint) {
        String result = descriptor.getConnectionPreview().getTemplate();
        for (ConnectorFieldDescriptor field : descriptor.getEndpointFields()) {
            if (endpoint.containsKey(field.code())) {
                result = result.replace("{" + field.code() + "}", String.valueOf(endpoint.get(field.code())));
            }
        }
        if (result.contains("{") || result.length() > 4096) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "Connector 连接预览模板无效");
        }
        return result;
    }

    private Map<String, String> validateProperties(ConnectorDescriptor descriptor, Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> source) || source.size() > 64) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "连接属性必须是键值对象且不能超过 64 项");
        }
        if (!descriptor.isCustomPropertiesSupported() && !source.isEmpty()) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "该 Connector 不支持自定义连接属性");
        }
        Map<String, String> result = new HashMap<>();
        source.forEach((keyValue, propertyValue) -> {
            String key = String.valueOf(keyValue).trim();
            String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
            if (!PROPERTY_KEY.matcher(key).matches() || SENSITIVE_KEYS.contains(normalized)) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "连接属性名无效或属于敏感字段: " + key);
            }
            if (propertyValue instanceof Map<?, ?> || propertyValue instanceof Iterable<?>) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "连接属性只允许字符串值");
            }
            String property = propertyValue == null ? "" : propertyValue.toString();
            if (property.length() > 1024) {
                throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "连接属性值不能超过 1024 字符");
            }
            result.put(key, property);
        });
        return Map.copyOf(result);
    }

    private Map<String, ConnectorDescriptor> descriptors() {
        return providers.stream().map(ConnectorDescriptorProvider::descriptor)
            .collect(Collectors.toUnmodifiableMap(value -> normalize(value.getConnectorCode()), Function.identity(),
                (left, right) -> { throw new IllegalStateException("重复 Connector 编码: " + left.getConnectorCode()); }));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
