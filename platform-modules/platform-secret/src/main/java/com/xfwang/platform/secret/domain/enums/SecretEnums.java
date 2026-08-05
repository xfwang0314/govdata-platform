package com.xfwang.platform.secret.domain.enums;

import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;

import java.util.Arrays;

public final class SecretEnums {
    private SecretEnums() { }
    public enum Type implements Coded {
        USERNAME_PASSWORD("用户名和密码"), PASSWORD("密码"), TOKEN("令牌"), KEY_VALUE("键值凭据");
        private final String label; Type(String label) { this.label = label; }
        public String code() { return name(); } public String label() { return label; }
    }
    public enum Status implements Coded {
        ACTIVE("已启用"), DISABLED("已停用");
        private final String label; Status(String label) { this.label = label; }
        public String code() { return name(); } public String label() { return label; }
    }
    public static String type(String code) { return require(Type.values(), code, "凭据类型"); }
    public static String status(String code) { return require(Status.values(), code, "凭据状态"); }
    private static String require(Coded[] values, String code, String field) {
        return Arrays.stream(values).filter(value -> value.code().equals(code)).map(Coded::code).findFirst()
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field + "无效"));
    }
    public interface Coded { String code(); String label(); }
}
