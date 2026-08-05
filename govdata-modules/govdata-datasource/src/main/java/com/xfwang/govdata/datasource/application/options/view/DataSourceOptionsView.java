package com.xfwang.govdata.datasource.application.options.view;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class DataSourceOptionsView {
    List<OptionView> environments;
    List<OptionView> accessModes;
    List<OptionView> classifications;
    List<OptionView> securityLevels;
    List<OptionView> statuses;
    List<OptionView> testStatuses;
}
