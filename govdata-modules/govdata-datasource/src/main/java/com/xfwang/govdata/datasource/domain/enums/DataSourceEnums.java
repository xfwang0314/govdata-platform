package com.xfwang.govdata.datasource.domain.enums;

import com.xfwang.platform.api.auth.enums.DataClassification;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;

import java.util.Arrays;

/**
 * 数据源状态机与 ABAC 使用的封闭值集。
 */
public final class DataSourceEnums {
    private DataSourceEnums() {
    }

    public enum Environment implements Coded {
        DEV("开发环境"), TEST("测试环境"), PROD("生产环境");
        private final String label;

        Environment(String label) {
            this.label = label;
        }

        public String code() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    public enum AccessMode implements Coded {
        READ_ONLY("只读"), READ_WRITE("读写");
        private final String label;

        AccessMode(String label) {
            this.label = label;
        }

        public String code() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    public enum Status implements Coded {
        DISABLED("已停用"), ACTIVE("已启用");
        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String code() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    public enum TestStatus implements Coded {
        NOT_TESTED("未测试"), SUCCESS("成功"), FAILED("失败");
        private final String label;

        TestStatus(String label) {
            this.label = label;
        }

        public String code() {
            return name();
        }

        public String label() {
            return label;
        }
    }

    public static String environment(String code) {
        return require(Environment.values(), code, "数据源环境");
    }

    public static String accessMode(String code) {
        return require(AccessMode.values(), code, "访问模式");
    }

    public static String classification(String code) {
        return DataClassification.fromCode(code)
            .map(DataClassification::code)
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "安全分类无效"));
    }

    public static String status(String code) {
        return require(Status.values(), code, "数据源状态");
    }

    public static String testStatus(String code) {
        return require(TestStatus.values(), code, "测试状态");
    }

    private static String require(Coded[] values, String code, String field) {
        return Arrays.stream(values)
            .filter(value -> value.code().equals(code))
            .map(Coded::code)
            .findFirst()
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR, field + "无效"));
    }

    public interface Coded {
        String code();

        String label();
    }
}
