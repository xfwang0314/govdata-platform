package com.xfwang.platform.mcp.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/** Agent 访问凭据生命周期状态。 */
@Getter
@RequiredArgsConstructor
public enum AgentTokenStatus {

    ACTIVE("ACTIVE", "启用中"),
    DISABLED("DISABLED", "已停用"),
    REVOKED("REVOKED", "已吊销");

    private final String code;
    private final String label;

    public static AgentTokenStatus fromCode(String code) {
        return Arrays.stream(values())
            .filter(status -> status.code.equals(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知的 Agent 凭据状态: " + code));
    }
}
