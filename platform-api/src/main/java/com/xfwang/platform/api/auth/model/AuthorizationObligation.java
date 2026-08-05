package com.xfwang.platform.api.auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 授权通过后必须执行的附加约束。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationObligation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private List<RowFilter> rowFilters = new ArrayList<>();

    /**
     * 允许返回的字段集合。null 表示未施加字段约束，空集合表示禁止返回任何字段。
     */
    private Set<String> allowedColumns;

    @Builder.Default
    private Map<String, String> maskingRules = new HashMap<>();

    private Integer maxRows;

    private Boolean exportAllowed;
}
