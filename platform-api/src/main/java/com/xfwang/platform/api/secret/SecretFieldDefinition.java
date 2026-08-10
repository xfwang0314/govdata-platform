package com.xfwang.platform.api.secret;

/** 凭据敏感字段的稳定页面与校验契约。 */
public record SecretFieldDefinition(String code, String label, String inputType,
                                    boolean required, int maxLength) {
}
