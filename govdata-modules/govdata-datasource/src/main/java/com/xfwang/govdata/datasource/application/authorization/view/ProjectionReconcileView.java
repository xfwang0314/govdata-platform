package com.xfwang.govdata.datasource.application.authorization.view;

/** 数据源授权投影分批对账结果。 */
public record ProjectionReconcileView(int processed, Long nextAfterId, boolean hasMore) {
}
