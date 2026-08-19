package com.xfwang.platform.api.secret;

import java.io.Serial;
import java.io.Serializable;

/** 凭据敏感字段的稳定页面与校验契约。 */
public record SecretFieldDefinition(String code, String label, String inputType,
                                    boolean required, int maxLength) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
