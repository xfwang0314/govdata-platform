package com.xfwang.platform.secret.application.options;

import com.xfwang.platform.secret.application.options.view.SecretOptionView;
import com.xfwang.platform.secret.application.options.view.SecretOptionsView;
import com.xfwang.platform.secret.domain.enums.SecretEnums;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SecretOptionsQueryService {

    private final SecretUsageCatalog usageCatalog;

    public SecretOptionsQueryService(SecretUsageCatalog usageCatalog) {
        this.usageCatalog = usageCatalog;
    }

    public SecretOptionsView options() {
        return SecretOptionsView.builder()
            .secretTypes(toViews(SecretEnums.Type.values()))
            .statuses(toViews(SecretEnums.Status.values()))
            .ownerModules(usageCatalog.ownerModules())
            .build();
    }

    private List<SecretOptionView> toViews(SecretEnums.Coded[] values) {
        return Arrays.stream(values)
            .map(value -> SecretOptionView.builder().value(value.code()).label(value.label()).build())
            .toList();
    }
}
