package com.xfwang.govdata.metadata.application.catalog.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 元数据页面固定选项集合。 */
@Value
@Builder
public class MetadataOptionsView {
    List<MetadataOptionView> assetTypes;
    List<MetadataOptionView> changeLevels;
    List<MetadataOptionView> changeIssueStatuses;
    List<MetadataOptionView> changeIssueTypes;
    List<MetadataOptionView> technicalStatuses;
    List<MetadataOptionView> governanceStatuses;
    List<MetadataOptionView> classifications;
    List<MetadataOptionView> securityLevels;
}
