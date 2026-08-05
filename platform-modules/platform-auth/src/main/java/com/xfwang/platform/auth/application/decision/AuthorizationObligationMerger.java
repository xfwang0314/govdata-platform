package com.xfwang.platform.auth.application.decision;

import com.xfwang.platform.api.auth.model.AuthorizationObligation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

/**
 * 以最严格原则合并多个匹配策略的附加约束。
 */
@Component
public class AuthorizationObligationMerger {

    public AuthorizationObligation merge(List<AuthorizationObligation> obligations) {
        List<AuthorizationObligation> values = obligations == null
            ? List.of()
            : obligations.stream().filter(java.util.Objects::nonNull).toList();
        List<com.xfwang.platform.api.auth.model.RowFilter> rowFilters = new ArrayList<>();
        Set<String> allowedColumns = null;
        Map<String, String> maskingRules = new HashMap<>();
        Integer maxRows = null;
        Boolean exportAllowed = null;
        for (AuthorizationObligation value : values) {
            if (value.getRowFilters() != null) {
                rowFilters.addAll(value.getRowFilters());
            }
            if (value.getAllowedColumns() != null) {
                if (allowedColumns == null) {
                    allowedColumns = new HashSet<>(value.getAllowedColumns());
                } else {
                    allowedColumns.retainAll(value.getAllowedColumns());
                }
            }
            if (value.getMaskingRules() != null) {
                value.getMaskingRules().forEach((field, rule) ->
                    maskingRules.merge(field, normalizeMaskingRule(rule), this::stricterMaskingRule));
            }
            if (value.getMaxRows() != null) {
                maxRows = maxRows == null ? value.getMaxRows() : Math.min(maxRows, value.getMaxRows());
            }
            if (Boolean.FALSE.equals(value.getExportAllowed())) {
                exportAllowed = false;
            } else if (exportAllowed == null && Boolean.TRUE.equals(value.getExportAllowed())) {
                exportAllowed = true;
            }
        }
        return AuthorizationObligation.builder()
            .rowFilters(rowFilters)
            .allowedColumns(allowedColumns)
            .maskingRules(maskingRules)
            .maxRows(maxRows)
            .exportAllowed(exportAllowed)
            .build();
    }

    private String stricterMaskingRule(String current, String incoming) {
        if (current.equals(incoming)) {
            return current;
        }
        if ("NONE".equals(current)) {
            return incoming;
        }
        if ("NONE".equals(incoming)) {
            return current;
        }
        if ("FULL".equals(current) || "FULL".equals(incoming)) {
            return "FULL";
        }
        return "FULL";
    }

    private String normalizeMaskingRule(String rule) {
        return rule == null || rule.isBlank() ? "FULL" : rule.trim().toUpperCase(Locale.ROOT);
    }
}
