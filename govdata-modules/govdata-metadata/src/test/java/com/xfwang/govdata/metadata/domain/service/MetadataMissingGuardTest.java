package com.xfwang.govdata.metadata.domain.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataMissingGuardTest {

    @Test
    void shouldSuppressMassMissingAboveConfiguredRate() {
        assertThat(MetadataMissingGuard.shouldSuppress(100, 30, 20, 10, new BigDecimal("0.30"))).isTrue();
    }

    @Test
    void shouldAllowOrdinaryMissingAndSmallBaselines() {
        assertThat(MetadataMissingGuard.shouldSuppress(100, 9, 20, 10, new BigDecimal("0.30"))).isFalse();
        assertThat(MetadataMissingGuard.shouldSuppress(10, 5, 20, 10, new BigDecimal("0.30"))).isFalse();
    }
}
