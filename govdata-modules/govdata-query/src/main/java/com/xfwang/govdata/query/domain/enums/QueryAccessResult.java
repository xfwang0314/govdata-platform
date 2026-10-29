package com.xfwang.govdata.query.domain.enums;

/** 查询访问追加事件结果。 */
public enum QueryAccessResult {
    STARTED("开始"), SUCCEEDED("成功"), DENIED("拒绝"), FAILED("失败");
    private final String label;
    QueryAccessResult(String label) { this.label = label; }
    public String code() { return name(); }
    public String label() { return label; }
}
