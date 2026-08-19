package com.xfwang.platform.auth.application.policy;

import com.xfwang.platform.api.auth.model.AuthorizationObligation;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftCommand;
import com.xfwang.platform.auth.domain.policy.model.PolicyConditionGroup;
import com.xfwang.platform.auth.domain.policy.model.PolicyDocument;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将编辑命令规范化为完整版本文档并生成稳定内容哈希。 */
@Component
@RequiredArgsConstructor
public class PolicyDocumentCodec {

    private static final String EMPTY_CONDITION = "{\"logic\":\"ALL\",\"conditions\":[],\"groups\":[]}";

    private final AuthorizationJsonCodec jsonCodec;
    private final AuthorizationCatalog catalog;

    public PolicyDocument from(PolicyGroupDraftCommand command) {
        Map<String, PolicyDocument.CatalogSnapshot> snapshots = new LinkedHashMap<>();
        List<PolicyDocument.Rule> rules = command.getRules().stream().map(rule -> {
            String resourceType = rule.getResourceType().trim();
            catalog.findResourceType(resourceType).ifPresent(definition -> snapshots.put(resourceType,
                new PolicyDocument.CatalogSnapshot(resourceType, definition.typeLabel(), "1")));
            Set<String> actions = normalizeSet(rule.getActions());
            Set<String> purposes = normalizeSet(rule.getPurposes());
            catalog.findResourceType(resourceType).ifPresent(definition -> definition.actions().stream()
                .filter(action -> actions.contains(action.code()))
                .forEach(action -> snapshots.put(action.code(),
                    new PolicyDocument.CatalogSnapshot(action.code(), action.label(), "1"))));
            purposes.forEach(code -> catalog.findPurpose(code).ifPresent(purpose -> snapshots.put(code,
                new PolicyDocument.CatalogSnapshot(code, purpose.label(), "1"))));
            return new PolicyDocument.Rule(
                StringUtils.isBlank(rule.getRuleCode())
                    ? "rule_" + Long.toUnsignedString(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(), 36)
                    : rule.getRuleCode().trim(),
                resourceType,
                normalize(rule.getResourceScope()),
                rule.getTargets().stream()
                    .map(target -> new PolicyDocument.ResourceTarget(
                        target.getResourceId(), Boolean.TRUE.equals(target.getIncludeChildren())))
                    .sorted(Comparator.comparing(PolicyDocument.ResourceTarget::resourceId))
                    .toList(),
                actions,
                purposes,
                readCondition(rule.getConditionJson()),
                readObligation(rule.getObligationJson()));
        }).toList();
        List<PolicyDocument.SubjectSelector> subjects = command.getSubjects().stream()
            .map(value -> new PolicyDocument.SubjectSelector(
                normalize(value.getSubjectType()),
                "ANY".equals(normalize(value.getSubjectType())) ? "*" : value.getSubjectId().trim(),
                Boolean.TRUE.equals(value.getIncludeChildren())))
            .sorted(Comparator.comparing(PolicyDocument.SubjectSelector::subjectType)
                .thenComparing(PolicyDocument.SubjectSelector::subjectId))
            .toList();
        return new PolicyDocument(
            PolicyDocument.CURRENT_SCHEMA_VERSION,
            command.getPolicyName().trim(),
            StringUtils.trim(command.getDescription()),
            normalize(command.getEffect()),
            normalize(command.getRiskLevel()),
            command.getPriority() == null ? 100 : command.getPriority(),
            new PolicyDocument.SubjectExpression(normalize(command.getSubjectOperator()), subjects),
            rules,
            snapshots);
    }

    public String write(PolicyDocument document) {
        return jsonCodec.write(document);
    }

    public PolicyDocument read(String documentJson) {
        return jsonCodec.read(documentJson, PolicyDocument.class);
    }

    public String hash(String documentJson) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(documentJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 不支持 SHA-256", ex);
        }
    }

    private PolicyConditionGroup readCondition(String value) {
        return jsonCodec.read(StringUtils.isBlank(value) ? EMPTY_CONDITION : value,
            PolicyConditionGroup.class);
    }

    private AuthorizationObligation readObligation(String value) {
        return StringUtils.isBlank(value) ? null : jsonCodec.read(value, AuthorizationObligation.class);
    }

    private Set<String> normalizeSet(Set<String> values) {
        return values == null ? Set.of() : values.stream().filter(StringUtils::isNotBlank)
            .map(this::normalize).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
