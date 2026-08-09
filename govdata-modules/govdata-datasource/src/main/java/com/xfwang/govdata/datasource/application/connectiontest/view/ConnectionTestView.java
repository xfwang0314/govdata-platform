package com.xfwang.govdata.datasource.application.connectiontest.view;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 连通测试结果 View。
 */
public record ConnectionTestView(Long testId, Long datasourceId, String connectorCode, String connectorVersion,
                                 String result, String reasonCode, String message, long latencyMs,
                                 Map<String, Object> serverInfo, String actorType, String actorId, String traceId,
                                 LocalDateTime occurredTime) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long testId;
        private Long datasourceId;
        private String connectorCode;
        private String connectorVersion;
        private String result;
        private String reasonCode;
        private String message;
        private long latencyMs;
        private Map<String, Object> serverInfo;
        private String actorType;
        private String actorId;
        private String traceId;
        private LocalDateTime occurredTime;

        public Builder testId(Long testId) {
            this.testId = testId;
            return this;
        }

        public Builder datasourceId(Long datasourceId) {
            this.datasourceId = datasourceId;
            return this;
        }

        public Builder connectorCode(String connectorCode) {
            this.connectorCode = connectorCode;
            return this;
        }

        public Builder connectorVersion(String connectorVersion) {
            this.connectorVersion = connectorVersion;
            return this;
        }

        public Builder result(String result) {
            this.result = result;
            return this;
        }

        public Builder reasonCode(String reasonCode) {
            this.reasonCode = reasonCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder serverInfo(Map<String, Object> serverInfo) {
            this.serverInfo = serverInfo;
            return this;
        }

        public Builder actorType(String actorType) {
            this.actorType = actorType;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder occurredTime(LocalDateTime occurredTime) {
            this.occurredTime = occurredTime;
            return this;
        }

        public ConnectionTestView build() {
            return new ConnectionTestView(testId, datasourceId, connectorCode, connectorVersion, result,
                reasonCode, message, latencyMs, serverInfo, actorType, actorId, traceId, occurredTime);
        }
    }
}
