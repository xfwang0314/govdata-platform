package com.xfwang.platform.api.secret;

import java.io.Serial;
import java.io.Serializable;

/** 业务模块声明的一个凭据使用用途。 */
public record SecretPurposeDefinition(String code, String label) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
