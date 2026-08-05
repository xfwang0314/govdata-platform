package com.xfwang.platform.secret.application.options;

import com.xfwang.platform.secret.application.options.view.SecretOptionView;
import com.xfwang.platform.secret.application.options.view.SecretOwnerModuleOptionView;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 凭据消费方目录。新增消费模块或用途时必须先在这里登记，避免页面配置与运行时调用约定不一致。
 */
@Component
public class SecretUsageCatalog {

    private static final List<OwnerModuleDefinition> DEFINITIONS = List.of(
        new OwnerModuleDefinition("govdata-datasource", "数据源管理", List.of(
            new PurposeDefinition("DATASOURCE_CONNECTION_TEST", "数据源连接测试"),
            new PurposeDefinition("DATASOURCE_QUERY", "数据源授权查询")
        ))
    );

    public List<SecretOwnerModuleOptionView> ownerModules() {
        return DEFINITIONS.stream().map(definition -> SecretOwnerModuleOptionView.builder()
            .value(definition.code())
            .label(definition.label())
            .purposes(definition.purposes().stream()
                .map(purpose -> SecretOptionView.builder()
                    .value(purpose.code()).label(purpose.label()).build())
                .toList())
            .build()).toList();
    }

    public String normalizePurposeScope(String ownerModule, String purposeScope) {
        OwnerModuleDefinition definition = DEFINITIONS.stream()
            .filter(item -> item.code().equals(ownerModule))
            .findFirst()
            .orElseThrow(() -> new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "所属模块未登记"));
        Set<String> requested = new LinkedHashSet<>(Arrays.stream(purposeScope.split(","))
            .map(String::trim).filter(value -> !value.isEmpty()).toList());
        Set<String> allowed = definition.purposes().stream().map(PurposeDefinition::code)
            .collect(java.util.stream.Collectors.toSet());
        if (requested.isEmpty() || !allowed.containsAll(requested)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "用途范围不属于所选模块的允许用途");
        }
        return String.join(",", requested);
    }

    private record OwnerModuleDefinition(String code, String label, List<PurposeDefinition> purposes) { }

    private record PurposeDefinition(String code, String label) { }
}
