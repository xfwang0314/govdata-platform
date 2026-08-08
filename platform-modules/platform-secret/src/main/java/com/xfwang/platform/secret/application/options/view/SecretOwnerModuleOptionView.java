package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 业务模块与用途级联选项视图。
 */
@Value
@Builder
public class SecretOwnerModuleOptionView {
    /** 模块编码（如 "govdata-datasource"） */
    String value;
    /** 模块中文 Label */
    String label;
    /** 该模块允许贡献的凭据用途下拉列表 */
    List<SecretOptionView> purposes;
}
