package com.xfwang.govdata.datasource.application.authorization.event;

/** 数据源授权资源投影事件，只携带构建投影所需的稳定快照。 */
public record DataSourceAuthorizationProjectionEvent(
    Operation operation,
    Long datasourceId,
    Long authzResourceId,
    String datasourceCode,
    String datasourceName,
    String connectorCode,
    String environment,
    Long ownerDeptId,
    Long ownerUserId,
    String accessMode,
    String classificationCode,
    Integer securityLevel,
    String status
) {

    public enum Operation {
        UPSERT,
        ARCHIVE
    }
}
