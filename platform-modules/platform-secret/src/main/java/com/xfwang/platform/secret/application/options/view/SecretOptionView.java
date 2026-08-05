package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SecretOptionView {
    String value;
    String label;
}
