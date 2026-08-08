package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

/**
 * 凭据字段 Schema 前端渲染选项视图。
 */
@Value
@Builder
public class SecretFieldOptionView {
    /** 字段编码 */
    String code;
    /** 字段中文名称 */
    String label;
    /** 输入框控件类型（如 text, password, textarea） */
    String inputType;
    /** 是否必填 */
    boolean required;
    /** 最大文本允许长度 */
    int maxLength;
}
