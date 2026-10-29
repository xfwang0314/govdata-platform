package com.xfwang.govdata.metadata.harvest.domain.service;

import com.xfwang.govdata.api.metadata.CollectedMetadataNamespace;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRule;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestMatchType;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRuleScope;
import com.xfwang.govdata.metadata.harvest.domain.enums.HarvestRuleType;
import com.google.re2j.Pattern;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 对扁平命名空间和完整表批次应用受控字符串规则。 */
@Component
public class HarvestRuleEvaluator {

    public List<CollectedMetadataNamespace> filterNamespaces(List<CollectedMetadataNamespace> namespaces,
                                                              List<HarvestRule> rules) {
        Map<HarvestRuleScope, List<HarvestRule>> grouped = byScope(rules);
        Map<HarvestRule, Pattern> compiled = compileRegexRules(rules);
        return safe(namespaces).stream().filter(value -> {
            HarvestRuleScope scope = value.getNamespaceType() == MetadataObjectType.CATALOG
                ? HarvestRuleScope.CATALOG : HarvestRuleScope.SCHEMA;
            return accepted(value.getNamespaceName(), grouped.get(scope), compiled);
        }).toList();
    }

    public List<CollectedMetadataTable> filterTables(List<CollectedMetadataTable> tables,
                                                      List<HarvestRule> rules) {
        Map<HarvestRuleScope, List<HarvestRule>> grouped = byScope(rules);
        Map<HarvestRule, Pattern> compiled = compileRegexRules(rules);
        return safe(tables).stream()
            .filter(value -> accepted(pathName(value.getParentCanonicalKey(), MetadataObjectType.CATALOG),
                grouped.get(HarvestRuleScope.CATALOG), compiled))
            .filter(value -> accepted(pathName(value.getParentCanonicalKey(), MetadataObjectType.SCHEMA),
                grouped.get(HarvestRuleScope.SCHEMA), compiled))
            .filter(value -> accepted(value.getTableName(), grouped.get(HarvestRuleScope.TABLE), compiled))
            .toList();
    }

    private Map<HarvestRuleScope, List<HarvestRule>> byScope(List<HarvestRule> rules) {
        return safe(rules).stream()
            .map(value -> Map.entry(HarvestRuleScope.fromCode(value.getRuleScope()).orElse(null), value))
            .filter(value -> value.getKey() != null)
            .collect(Collectors.groupingBy(Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private boolean accepted(String source, List<HarvestRule> rules, Map<HarvestRule, Pattern> compiled) {
        if (rules == null || rules.isEmpty()) return true;
        List<HarvestRule> includes = rules.stream()
            .filter(value -> HarvestRuleType.INCLUDE.getCode().equals(value.getRuleType())).toList();
        boolean included = includes.isEmpty() || includes.stream().anyMatch(value -> matches(source, value, compiled));
        boolean excluded = rules.stream()
            .filter(value -> HarvestRuleType.EXCLUDE.getCode().equals(value.getRuleType()))
            .anyMatch(value -> matches(source, value, compiled));
        return included && !excluded;
    }

    private boolean matches(String source, HarvestRule rule, Map<HarvestRule, Pattern> compiled) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String pattern = rule.getRulePattern().toLowerCase(Locale.ROOT);
        return HarvestMatchType.fromCode(rule.getMatchType()).map(type -> switch (type) {
            case EXACT -> value.equals(pattern);
            case PREFIX -> value.startsWith(pattern);
            case SUFFIX -> value.endsWith(pattern);
            case CONTAINS -> value.contains(pattern);
            case REGEX -> source != null && compiled.get(rule).matcher(source).matches();
        }).orElse(false);
    }

    private Map<HarvestRule, Pattern> compileRegexRules(List<HarvestRule> rules) {
        Map<HarvestRule, Pattern> compiled = new IdentityHashMap<>();
        for (HarvestRule rule : safe(rules)) {
            if (HarvestMatchType.REGEX.getCode().equals(rule.getMatchType())) {
                compiled.put(rule, Pattern.compile(rule.getRulePattern(), Pattern.CASE_INSENSITIVE));
            }
        }
        return compiled;
    }

    private String pathName(String canonicalKey, MetadataObjectType type) {
        if (canonicalKey == null) return null;
        String marker = type.getCode() + '/';
        int start = canonicalKey.indexOf(marker);
        if (start < 0) return null;
        int valueStart = canonicalKey.indexOf(':', start + marker.length());
        if (valueStart < 0) return null;
        int end = canonicalKey.indexOf('/', valueStart + 1);
        return canonicalKey.substring(valueStart + 1, end < 0 ? canonicalKey.length() : end);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
