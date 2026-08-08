package com.xfwang.platform.secret.application.options.view;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 凭据管理下拉字典总视图对象。
 */
@Value
@Builder
public class SecretOptionsView {
    /** 凭据类型集合（含属性 Schema） */
    List<SecretTypeOptionView> secretTypes;
    /** 凭据状态枚举字典列表 */
    List<SecretOptionView> statuses;
    /** 业务模块及所属用途字典树 */
    List<SecretOwnerModuleOptionView> ownerModules;
}
