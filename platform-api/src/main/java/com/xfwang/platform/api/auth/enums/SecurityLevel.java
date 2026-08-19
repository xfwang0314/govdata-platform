package com.xfwang.platform.api.auth.enums;

import java.util.Arrays;
import java.util.Optional;

/** 平台统一的数据安全保护等级，适用于主体许可等级与资源安全等级。 */
public enum SecurityLevel {
    LEVEL_1(1, "安全等级 1"),
    LEVEL_2(2, "安全等级 2"),
    LEVEL_3(3, "安全等级 3"),
    LEVEL_4(4, "安全等级 4"),
    LEVEL_5(5, "安全等级 5");

    public static final int MIN = 1;
    public static final int MAX = 5;

    private final int level;
    private final String label;

    SecurityLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    public static Optional<SecurityLevel> fromLevel(Integer level) {
        if (level == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(value -> value.level == level).findFirst();
    }

    public static boolean supports(Integer level) {
        return fromLevel(level).isPresent();
    }
}
