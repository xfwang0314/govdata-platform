package com.xfwang.platform.auth.application.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.policy.command.PolicyGroupDraftCommand;
import com.xfwang.platform.auth.application.policy.command.PolicyRuleCommand;
import com.xfwang.platform.auth.application.policy.command.PolicySubjectCommand;
import com.xfwang.platform.auth.domain.policy.model.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PolicyDocumentCodecTest {

    private final PolicyDocumentCodec codec = new PolicyDocumentCodec(
        new AuthorizationJsonCodec(new ObjectMapper().findAndRegisterModules()),
        mock(AuthorizationCatalog.class));

    @Test
    void createsStableRoundTripDocumentWithPurposeAndMultipleRules() {
        PolicyGroupDraftCommand command = new PolicyGroupDraftCommand();
        command.setPolicyName("数据源只读策略");
        command.setDescription("测试不可变策略文档");
        command.setEffect("permit");
        command.setRiskLevel("low");
        command.setSubjects(List.of(subject("ROLE", "data-reader")));
        command.setRules(List.of(rule("govdata:datasource", "VIEW"),
            rule("system:user", "VIEW")));

        PolicyDocument document = codec.from(command);
        String json = codec.write(document);
        PolicyDocument restored = codec.read(json);

        assertThat(restored.policyName()).isEqualTo("数据源只读策略");
        assertThat(restored.effect()).isEqualTo("PERMIT");
        assertThat(restored.rules()).hasSize(2).allMatch(value -> value.purposes().contains("ADMINISTRATION"));
        assertThat(codec.hash(json)).hasSize(64).isEqualTo(codec.hash(codec.write(restored)));
    }

    private PolicySubjectCommand subject(String type, String id) {
        PolicySubjectCommand value = new PolicySubjectCommand();
        value.setSubjectType(type);
        value.setSubjectId(id);
        return value;
    }

    private PolicyRuleCommand rule(String resourceType, String action) {
        PolicyRuleCommand value = new PolicyRuleCommand();
        value.setResourceType(resourceType);
        value.setResourceScope("TYPE");
        value.setActions(Set.of(action));
        value.setPurposes(Set.of("ADMINISTRATION"));
        return value;
    }
}
