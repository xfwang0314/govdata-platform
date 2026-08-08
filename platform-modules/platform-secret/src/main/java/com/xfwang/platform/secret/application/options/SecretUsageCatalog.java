package com.xfwang.platform.secret.application.options;

import com.xfwang.platform.api.secret.SecretPurposeDefinition;
import com.xfwang.platform.api.secret.SecretUsageContributor;
import com.xfwang.platform.secret.application.options.view.SecretOptionView;
import com.xfwang.platform.secret.application.options.view.SecretOwnerModuleOptionView;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 凭据消费方与用途插件化目录中心。
 * <p>
 * 遵循开闭原则与依赖倒置原则。平台核心模块零硬编码业务模块，各业务模块（如 {@code govdata-datasource}）通过实现
 * 跨模块 SPI 契约 {@link SecretUsageContributor} 在启动时声明各自模块名称、中文 Label 及允许的用途编码树。
 * 本组件在 Spring 容器启动时自动收集所有贡献者，校验唯一性并构建不可变快照树。
 */
@Component
public class SecretUsageCatalog {

    private final Map<String, RegisteredUsage> usages;

    /**
     * 构造函数：自动收集 Spring 容器中所有实现了 {@link SecretUsageContributor} 接口的 Bean 并生成不可变映射。
     *
     * @param contributors 系统中所有业务模块的凭据用途贡献者列表
     */
    public SecretUsageCatalog(List<SecretUsageContributor> contributors) {
        this.usages = contributors.stream().map(this::snapshot).collect(Collectors.toUnmodifiableMap(
            RegisteredUsage::ownerModule, definition -> definition,
            (left, right) -> {
                throw new IllegalStateException("重复凭据消费模块编码: " + left.ownerModule());
            }));
    }

    /**
     * 获取所有业务模块声明的拥有模块与用途树型下拉选项视图。
     *
     * @return 业务模块下拉选项视图列表
     */
    public List<SecretOwnerModuleOptionView> ownerModules() {
        return usages.values().stream()
            .sorted(Comparator.comparing(RegisteredUsage::ownerModule))
            .map(definition -> SecretOwnerModuleOptionView.builder()
            .value(definition.ownerModule())
            .label(definition.ownerModuleLabel())
            .purposes(definition.purposes().stream()
                .map(purpose -> SecretOptionView.builder()
                .value(purpose.code()).label(purpose.label()).build())
                .toList())
            .build()).toList();
    }

    /**
     * 规范化并校验凭据表单传入的用途范围字符串（PurposeScope）。
     * <p>
     * 确保传入的用途编码属于所选模块允许贡献的用途集合，并去除重复和空白，返回以逗号分隔的格式。
     *
     * @param ownerModule  所属业务模块编码
     * @param purposeScope 请求传入的用途范围字符串（逗号分隔或单个用途）
     * @return 规范化后的用途范围字符串
     * @throws ServiceException 如果模块未登记或包含未声明的非法用途编码
     */
    public String normalizePurposeScope(String ownerModule, String purposeScope) {
        RegisteredUsage definition = usages.get(ownerModule);
        if (definition == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "所属模块未登记");
        }
        Set<String> requested = new LinkedHashSet<>(Arrays.stream(purposeScope.split(","))
            .map(String::trim).filter(value -> !value.isEmpty()).toList());
        Set<String> allowed = definition.purposes().stream().map(SecretPurposeDefinition::code)
            .collect(java.util.stream.Collectors.toSet());
        if (requested.isEmpty() || !allowed.containsAll(requested)) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "用途范围不属于所选模块的允许用途");
        }
        return String.join(",", requested);
    }

    /** 校验并生成单个 Contributor 的不可变快照 */
    private RegisteredUsage snapshot(SecretUsageContributor contributor) {
        List<SecretPurposeDefinition> contributedPurposes = contributor == null ? null : contributor.purposes();
        if (contributor == null || isBlank(contributor.ownerModule()) || isBlank(contributor.ownerModuleLabel())
            || contributedPurposes == null || contributedPurposes.isEmpty()) {
            throw new IllegalStateException("凭据用途贡献定义不完整");
        }
        List<SecretPurposeDefinition> purposes = List.copyOf(contributedPurposes);
        Set<String> purposeCodes = new HashSet<>();
        for (SecretPurposeDefinition purpose : purposes) {
            if (purpose == null || isBlank(purpose.code()) || isBlank(purpose.label())) {
                throw new IllegalStateException("凭据用途贡献定义不完整: " + contributor.ownerModule());
            }
            if (!purposeCodes.add(purpose.code())) {
                throw new IllegalStateException("重复凭据用途编码: " + purpose.code());
            }
        }
        return new RegisteredUsage(contributor.ownerModule(), contributor.ownerModuleLabel(), purposes);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 已登记的凭据用途私有记录结构 */
    private record RegisteredUsage(String ownerModule, String ownerModuleLabel,
                                   List<SecretPurposeDefinition> purposes) {
    }
}
