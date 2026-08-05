package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 可消费平台凭据的受控模块及其允许用途。 */
@Value
@Builder
public class SecretOwnerModuleOptionView {
    String value;
    String label;
    List<SecretOptionView> purposes;
}
