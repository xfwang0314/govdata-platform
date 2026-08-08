package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 凭据类型选项视图对象（含 Schema 字段渲染提示）。
 */
@Value
@Builder
public class SecretTypeOptionView {
    /** 凭据类型编码 */
    String value;
    /** 凭据类型中文 Label */
    String label;
    /** 是否允许动态可变扩展字段 */
    boolean dynamicFieldsAllowed;
    /** 该凭据类型定义的固化 Schema 字段列表 */
    List<SecretFieldOptionView> fields;
}
