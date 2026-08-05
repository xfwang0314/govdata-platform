package com.xfwang.platform.api.secret;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 只在一次受控调用期间存活的凭据值。 */
public final class SecretValue implements AutoCloseable {

    private final Map<String, char[]> values;

    public SecretValue(Map<String, char[]> values) {
        Map<String, char[]> copy = new HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> copy.put(key, value == null ? null : value.clone()));
        }
        this.values = copy;
    }

    public char[] get(String key) {
        char[] value = values.get(key);
        return value == null ? null : value.clone();
    }

    public Map<String, char[]> snapshot() {
        Map<String, char[]> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, value == null ? null : value.clone()));
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public void close() {
        values.values().forEach(value -> {
            if (value != null) {
                java.util.Arrays.fill(value, '\0');
            }
        });
        values.clear();
    }

    @Override
    public String toString() {
        return "SecretValue[REDACTED]";
    }
}
