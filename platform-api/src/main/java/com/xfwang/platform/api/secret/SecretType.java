package com.xfwang.platform.api.secret;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 跨模块稳定凭据类型及其最小字段契约。 */
public enum SecretType {
    USERNAME_PASSWORD("用户名和密码", false, List.of(
        field("username", "用户名", "TEXT", true, 500),
        field("password", "密码", "PASSWORD", true, 8192))),
    PASSWORD("密码", false, List.of(
        field("password", "密码", "PASSWORD", true, 8192))),
    TOKEN("令牌", false, List.of(
        field("token", "令牌", "PASSWORD", true, 8192))),
    API_KEY("API Key", false, List.of(
        field("apiKey", "API Key", "PASSWORD", true, 8192))),
    SSH_PRIVATE_KEY("SSH私钥", false, List.of(
        field("username", "用户名", "TEXT", true, 500),
        field("privateKey", "私钥", "SECRET_TEXTAREA", true, 16384),
        field("passphrase", "私钥口令", "PASSWORD", false, 8192))),
    OAUTH2_CLIENT("OAuth2客户端", false, List.of(
        field("clientId", "Client ID", "TEXT", true, 500),
        field("clientSecret", "Client Secret", "PASSWORD", true, 8192))),
    KEY_VALUE("键值凭据", true, List.of());

    private final String label;
    private final boolean dynamicFieldsAllowed;
    private final List<SecretFieldDefinition> fields;

    SecretType(String label, boolean dynamicFieldsAllowed, List<SecretFieldDefinition> fields) {
        this.label = label;
        this.dynamicFieldsAllowed = dynamicFieldsAllowed;
        this.fields = List.copyOf(fields);
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public Set<String> requiredKeys() {
        return fields.stream().filter(SecretFieldDefinition::required)
            .map(SecretFieldDefinition::code).collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> allowedKeys() {
        return fields.stream().map(SecretFieldDefinition::code).collect(Collectors.toUnmodifiableSet());
    }

    public List<SecretFieldDefinition> fields() {
        return fields;
    }

    public boolean dynamicFieldsAllowed() {
        return dynamicFieldsAllowed;
    }

    public static Optional<SecretType> fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code().equals(code)).findFirst();
    }

    private static SecretFieldDefinition field(String code, String label, String inputType,
                                               boolean required, int maxLength) {
        return new SecretFieldDefinition(code, label, inputType, required, maxLength);
    }
}
