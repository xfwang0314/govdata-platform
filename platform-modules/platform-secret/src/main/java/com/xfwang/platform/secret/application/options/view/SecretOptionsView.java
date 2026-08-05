package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class SecretOptionsView {
    List<SecretOptionView> secretTypes;
    List<SecretOptionView> statuses;
    List<SecretOwnerModuleOptionView> ownerModules;
}
