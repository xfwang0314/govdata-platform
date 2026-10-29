package com.xfwang.govdata.metadata.harvest.domain.service;

import com.xfwang.govdata.api.metadata.CollectedMetadataNamespace;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.metadata.harvest.domain.entity.HarvestRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HarvestRuleEvaluatorTest {
    private final HarvestRuleEvaluator evaluator = new HarvestRuleEvaluator();

    @Test
    void includeAndExcludeRulesAreAppliedPerScope() {
        CollectedMetadataNamespace gov = namespace(MetadataObjectType.CATALOG, "gov", "CATALOG/3:gov", null);
        CollectedMetadataNamespace audit = namespace(MetadataObjectType.CATALOG, "audit", "CATALOG/5:audit", null);
        List<CollectedMetadataNamespace> namespaces = evaluator.filterNamespaces(List.of(gov, audit),
            List.of(rule("CATALOG", "INCLUDE", "EXACT", "gov")));
        List<CollectedMetadataTable> tables = evaluator.filterTables(List.of(
                table("citizen", "CATALOG/3:gov/SCHEMA/6:public"),
                table("citizen_tmp", "CATALOG/3:gov/SCHEMA/6:public")),
            List.of(rule("CATALOG", "INCLUDE", "EXACT", "gov"),
                rule("TABLE", "EXCLUDE", "SUFFIX", "_tmp")));

        assertThat(namespaces).extracting(CollectedMetadataNamespace::getNamespaceName).containsExactly("gov");
        assertThat(tables).extracting(CollectedMetadataTable::getTableName).containsExactly("citizen");
    }

    @Test
    void regexRulesFilterDatabaseSchemaAndTableNames() {
        List<CollectedMetadataNamespace> namespaces = evaluator.filterNamespaces(List.of(
                namespace(MetadataObjectType.CATALOG, "gov_a", "CATALOG/5:gov_a", null),
                namespace(MetadataObjectType.CATALOG, "archive", "CATALOG/7:archive", null),
                namespace(MetadataObjectType.SCHEMA, "PUBLIC", "CATALOG/5:gov_a/SCHEMA/6:PUBLIC",
                    "CATALOG/5:gov_a")),
            List.of(rule("CATALOG", "INCLUDE", "REGEX", "gov_.*"),
                rule("SCHEMA", "INCLUDE", "REGEX", "pub.*")));
        List<CollectedMetadataTable> tables = evaluator.filterTables(List.of(
                table("citizen", "CATALOG/5:gov_a/SCHEMA/6:PUBLIC"),
                table("citizen_tmp", "CATALOG/5:gov_a/SCHEMA/6:PUBLIC"),
                table("citizen", "CATALOG/7:archive/SCHEMA/6:PUBLIC")),
            List.of(rule("CATALOG", "INCLUDE", "REGEX", "gov_.*"),
                rule("SCHEMA", "INCLUDE", "REGEX", "pub.*"),
                rule("TABLE", "EXCLUDE", "REGEX", ".*_tmp")));

        assertThat(namespaces).extracting(CollectedMetadataNamespace::getNamespaceName)
            .containsExactly("gov_a", "PUBLIC");
        assertThat(tables).extracting(CollectedMetadataTable::getTableName).containsExactly("citizen");
    }

    private CollectedMetadataNamespace namespace(MetadataObjectType type, String name, String key, String parentKey) {
        return CollectedMetadataNamespace.builder().namespaceType(type).namespaceName(name)
            .canonicalKey(key).parentCanonicalKey(parentKey).build();
    }

    private CollectedMetadataTable table(String name, String parentKey) {
        return CollectedMetadataTable.builder().tableName(name).parentCanonicalKey(parentKey).build();
    }

    private HarvestRule rule(String scope, String type, String match, String pattern) {
        HarvestRule rule = new HarvestRule();
        rule.setRuleScope(scope);
        rule.setRuleType(type);
        rule.setMatchType(match);
        rule.setRulePattern(pattern);
        return rule;
    }
}
