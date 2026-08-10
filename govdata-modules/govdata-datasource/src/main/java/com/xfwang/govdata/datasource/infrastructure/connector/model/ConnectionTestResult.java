package com.xfwang.govdata.datasource.infrastructure.connector.model;

import java.util.Map;

/** 连通测试结果。 */
public class ConnectionTestResult {
    private final boolean success;
    private final String reasonCode;
    private final String message;
    private final long latencyMs;
    private final Map<String, Object> serverInfo;

    public ConnectionTestResult(boolean success, String reasonCode, String message, long latencyMs,
                                Map<String, Object> serverInfo) {
        this.success = success;
        this.reasonCode = reasonCode;
        this.message = message;
        this.latencyMs = latencyMs;
        this.serverInfo = serverInfo;
    }

    public boolean isSuccess() { return success; }
    public String getReasonCode() { return reasonCode; }
    public String getMessage() { return message; }
    public long getLatencyMs() { return latencyMs; }
    public Map<String, Object> getServerInfo() { return serverInfo; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean success;
        private String reasonCode;
        private String message;
        private long latencyMs;
        private Map<String, Object> serverInfo;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder reasonCode(String reasonCode) { this.reasonCode = reasonCode; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder latencyMs(long latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder serverInfo(Map<String, Object> serverInfo) { this.serverInfo = serverInfo; return this; }

        public ConnectionTestResult build() {
            return new ConnectionTestResult(success, reasonCode, message, latencyMs, serverInfo);
        }
    }
}
