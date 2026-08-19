package com.xfwang.platform.auth.domain.policy.model;

import com.xfwang.platform.api.auth.model.AuthorizationObligation;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 策略组版本的唯一事实文档。
 *
 * <p>运行时索引只能由该文档编译得到，不能反向拼装或修改策略事实。</p>
 */
public record PolicyDocument(int schemaVersion,
                             String policyName,
                             String description,
                             String effect,
                             String riskLevel,
                             int priority,
                             SubjectExpression subjects,
                             List<Rule> rules,
                             Map<String, CatalogSnapshot> catalogSnapshots) implements Serializable {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PolicyDocument {
        rules = rules == null ? List.of() : List.copyOf(rules);
        catalogSnapshots = catalogSnapshots == null ? Map.of() : Map.copyOf(catalogSnapshots);
    }

    public record SubjectExpression(String operator, List<SubjectSelector> items) implements Serializable {
        public SubjectExpression {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record SubjectSelector(String subjectType, String subjectId,
                                  boolean includeChildren) implements Serializable {
    }

    public record Rule(String ruleCode,
                       String resourceType,
                       String resourceScope,
                       List<ResourceTarget> targets,
                       Set<String> actions,
                       Set<String> purposes,
                       PolicyConditionGroup condition,
                       AuthorizationObligation obligation) implements Serializable {
        public Rule {
            targets = targets == null ? List.of() : List.copyOf(targets);
            actions = actions == null ? Set.of() : Set.copyOf(actions);
            purposes = purposes == null ? Set.of() : Set.copyOf(purposes);
        }
    }

    public record ResourceTarget(Long resourceId, boolean includeChildren) implements Serializable {
    }

    /** 只读发布快照；稳定编码仍是运行时语义。 */
    public record CatalogSnapshot(String code, String label, String definitionVersion) implements Serializable {
    }
}
