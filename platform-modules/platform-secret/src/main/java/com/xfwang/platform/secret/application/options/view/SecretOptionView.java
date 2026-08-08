package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

/**
 * 通用键值下拉选项视图（code & label）。
 */
@Value
@Builder
public class SecretOptionView {
    String value;
    String label;
}
