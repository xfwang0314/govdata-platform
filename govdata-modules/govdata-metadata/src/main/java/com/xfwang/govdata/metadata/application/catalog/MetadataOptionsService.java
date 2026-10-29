package com.xfwang.govdata.metadata.application.catalog;

import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataOptionView;
import com.xfwang.govdata.metadata.application.catalog.view.MetadataOptionsView;
import com.xfwang.govdata.metadata.domain.enums.MetadataGovernanceStatus;
import com.xfwang.govdata.metadata.domain.enums.MetadataChangeIssueStatus;
import com.xfwang.govdata.metadata.domain.enums.MetadataChangeIssueType;
import com.xfwang.govdata.metadata.domain.enums.MetadataChangeLevel;
import com.xfwang.govdata.metadata.domain.enums.MetadataTechnicalStatus;
import com.xfwang.platform.api.auth.enums.DataClassification;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.stream.IntStream;

/** 为元数据页面提供与后端枚举同源的固定选项。 */
@Service
public class MetadataOptionsService {

    /** 返回元数据目录页面允许提交的全部封闭值。 */
    public MetadataOptionsView options() {
        return MetadataOptionsView.builder()
            .assetTypes(Arrays.stream(MetadataObjectType.values())
                .filter(value -> value != MetadataObjectType.FILE)
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .changeLevels(Arrays.stream(MetadataChangeLevel.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .changeIssueStatuses(Arrays.stream(MetadataChangeIssueStatus.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .changeIssueTypes(Arrays.stream(MetadataChangeIssueType.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .technicalStatuses(Arrays.stream(MetadataTechnicalStatus.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .governanceStatuses(Arrays.stream(MetadataGovernanceStatus.values())
                .map(value -> option(value.getLabel(), value.getCode())).toList())
            .classifications(Arrays.stream(DataClassification.values())
                .map(value -> option(value.label(), value.code())).toList())
            .securityLevels(IntStream.rangeClosed(1, 5)
                .mapToObj(value -> option("安全等级 " + value, String.valueOf(value))).toList())
            .build();
    }

    private MetadataOptionView option(String label, String code) {
        return new MetadataOptionView(label, code);
    }
}
