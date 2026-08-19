package com.xfwang.platform.api.auth.catalog;

import java.io.Serial;
import java.io.Serializable;

/** 授权属性允许选择的稳定固定值。 */
public record AuthorizationValueOption(String label, String value) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
