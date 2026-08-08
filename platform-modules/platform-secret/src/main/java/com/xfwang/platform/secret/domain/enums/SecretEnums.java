package com.xfwang.platform.secret.domain.enums;

import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;

import java.util.Arrays;

/**
 * 平台凭据领域枚举与校验工具类。
 */
public final class SecretEnums {

    private SecretEnums() {
    }

    /**
     * 凭据全局状态枚举。
     */
    public enum Status implements Coded {
        /**
         * 已启用：允许正常解析与使用
         */
        ACTIVE("已启用"),

        /**
         * 已停用：阻止解析与连接
         */
        DISABLED("已停用");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public String label() {
            return label;
        }
    }

    /**
     * 校验并返回合法的状态编码。
     *
     * @param code 待校验的状态编码
     * @return 合法的状态编码
     * @throws ServiceException 如果编码不在 Status 枚举中
     */
    public static String status(String code) {
        return require(Status.values(), code, "凭据状态");
    }

    private static String require(Coded[] values, String code, String field) {
        return Arrays.stream(values).filter(value -> value.code().equals(code)).map(Coded::code).findFirst()
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field + "无效"));
    }

    /**
     * 包含 Code 和 Label 的编码接口契约。
     */
    public interface Coded {
        String code();

        String label();
    }
}
