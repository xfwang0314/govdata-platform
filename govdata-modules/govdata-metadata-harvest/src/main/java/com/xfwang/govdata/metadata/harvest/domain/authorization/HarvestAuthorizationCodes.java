package com.xfwang.govdata.metadata.harvest.domain.authorization;

/** 采集计划控制库行范围的稳定授权编码。 */
public final class HarvestAuthorizationCodes {
    public static final String MODULE = "govdata-metadata-harvest";
    public static final String RESOURCE_TYPE = "govdata:metadata:harvest-plan";
    public static final String ACTION_VIEW = "VIEW";
    public static final String ACTION_MANAGE = "MANAGE";
    public static final String ACTION_EXECUTE = "EXECUTE";
    public static final String PURPOSE = "METADATA_HARVEST_ADMINISTRATION";
    private HarvestAuthorizationCodes() { }
}
