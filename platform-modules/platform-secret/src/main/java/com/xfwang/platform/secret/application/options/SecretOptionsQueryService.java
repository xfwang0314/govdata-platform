package com.xfwang.platform.secret.application.options;

import com.xfwang.platform.api.secret.SecretType;
import com.xfwang.platform.secret.application.options.view.SecretFieldOptionView;
import com.xfwang.platform.secret.application.options.view.SecretOptionView;
import com.xfwang.platform.secret.application.options.view.SecretOptionsView;
import com.xfwang.platform.secret.application.options.view.SecretTypeOptionView;
import com.xfwang.platform.secret.domain.enums.SecretEnums;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 凭据前端下拉选项字典查询服务。
 * <p>
 * 为管理前端动态渲染表单提供凭据类型（SecretType包含字段Schema）、凭据状态枚举
 * 以及由各业务模块通过 {@link com.xfwang.platform.api.secret.SecretUsageContributor} 注入的拥有模块与用途选项。
 */
@Service
public class SecretOptionsQueryService {

    private final SecretUsageCatalog usageCatalog;

    /**
     * 构造函数（构造器注入）。
     *
     * @param usageCatalog 凭据用途与模块插件化目录
     */
    public SecretOptionsQueryService(SecretUsageCatalog usageCatalog) {
        this.usageCatalog = usageCatalog;
    }

    /**
     * 获取管理端页面所需的全部动态下拉选项字典数据（凭据类型Schema、状态枚举、各模块用途树）。
     *
     * @return 下拉选项汇总视图
     */
    public SecretOptionsView options() {
        return SecretOptionsView.builder()
            .secretTypes(Arrays.stream(SecretType.values())
                .map(value -> SecretTypeOptionView.builder().value(value.code()).label(value.label())
                    .dynamicFieldsAllowed(value.dynamicFieldsAllowed())
                    .fields(value.fields().stream().map(field -> SecretFieldOptionView.builder()
                        .code(field.code()).label(field.label()).inputType(field.inputType())
                        .required(field.required()).maxLength(field.maxLength()).build()).toList())
                    .build())
                .toList())
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
