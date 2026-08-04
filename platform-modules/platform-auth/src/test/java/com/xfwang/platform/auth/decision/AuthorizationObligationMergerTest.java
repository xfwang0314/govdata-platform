package com.xfwang.platform.auth.decision;

import com.xfwang.platform.auth.api.model.AuthorizationObligation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AuthorizationObligationMergerTest {

    private final AuthorizationObligationMerger merger = new AuthorizationObligationMerger();

    @Test
    void shouldMergeWithStrictestConstraints() {
        AuthorizationObligation first = AuthorizationObligation.builder()
            .allowedColumns(Set.of("name", "phone", "idCard"))
            .maskingRules(Map.of("phone", "MOBILE"))
            .maxRows(1000)
            .exportAllowed(true)
            .build();
        AuthorizationObligation second = AuthorizationObligation.builder()
            .allowedColumns(Set.of("name", "phone"))
            .maskingRules(Map.of("idCard", "ID_CARD"))
            .maxRows(200)
            .exportAllowed(false)
            .build();

        AuthorizationObligation result = merger.merge(List.of(first, second));

        assertThat(result.getAllowedColumns()).containsExactlyInAnyOrder("name", "phone");
        assertThat(result.getMaskingRules()).containsEntry("phone", "MOBILE")
            .containsEntry("idCard", "ID_CARD");
        assertThat(result.getMaxRows()).isEqualTo(200);
        assertThat(result.getExportAllowed()).isFalse();
    }

    @Test
    void emptyColumnIntersectionMustRemainDenyAll() {
        AuthorizationObligation first = AuthorizationObligation.builder()
            .allowedColumns(Set.of("name"))
            .build();
        AuthorizationObligation second = AuthorizationObligation.builder()
            .allowedColumns(Set.of("phone"))
            .build();

        AuthorizationObligation result = merger.merge(List.of(first, second));

        assertThat(result.getAllowedColumns()).isEmpty();
    }

    @Test
    void conflictingMaskingRulesMustNeverDowngradeProtection() {
        AuthorizationObligation first = AuthorizationObligation.builder()
            .maskingRules(Map.of("idCard", "ID_CARD", "phone", "MOBILE"))
            .build();
        AuthorizationObligation second = AuthorizationObligation.builder()
            .maskingRules(Map.of("idCard", "NONE", "phone", "EMAIL"))
            .build();

        AuthorizationObligation result = merger.merge(List.of(first, second));

        assertThat(result.getMaskingRules())
            .containsEntry("idCard", "ID_CARD")
            .containsEntry("phone", "FULL");
    }
}
