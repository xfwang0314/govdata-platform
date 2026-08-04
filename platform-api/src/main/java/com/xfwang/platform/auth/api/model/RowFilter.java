package com.xfwang.platform.auth.api.model;

import com.xfwang.platform.auth.api.enums.ConditionOperator;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 授权决策返回的结构化行级约束。
 *
 * <p>attribute 是逻辑属性名，由资源适配器映射为实际数据库列；不得直接保存 SQL。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowFilter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String attribute;

    private ConditionOperator operator;

    @Builder.Default
    private List<Object> values = new ArrayList<>();
}
