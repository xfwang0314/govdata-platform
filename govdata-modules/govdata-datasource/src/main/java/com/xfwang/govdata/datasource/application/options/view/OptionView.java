package com.xfwang.govdata.datasource.application.options.view;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OptionView {
    String value;
    String label;
}
