package com.xfwang.platform.api.auth.catalog;

/** 授权资源目录的后端受控虚拟分类；不参与 Resource 父子关系或授权继承。 */
public enum ResourceNavigationCategory {
    TECHNICAL_DATA_ASSET("技术数据资产", 10),
    DATA_PRODUCT_SERVICE("数据产品", 20),
    DATA_GOVERNANCE_OPERATION("数据治理作业", 30),
    PLATFORM_MANAGEMENT("平台管理资源", 40);

    private final String label;
    private final int order;

    ResourceNavigationCategory(String label, int order) {
        this.label = label;
        this.order = order;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public int order() {
        return order;
    }
}
