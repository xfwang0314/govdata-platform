package com.xfwang.platform.auth.domain.service;

import com.xfwang.platform.auth.domain.enums.PolicyLifecycleEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyLifecycleTest {

    private final PolicyLifecycle lifecycle = new PolicyLifecycle();

    @Test
    void publishesDraftAndDisablesPublishedPolicy() {
        assertThat(lifecycle.transitionAggregate("DRAFT", PolicyLifecycleEvent.PUBLISH))
            .isEqualTo("ACTIVE");
        assertThat(lifecycle.transitionVersion("DRAFT", PolicyLifecycleEvent.PUBLISH))
            .isEqualTo("PUBLISHED");
        assertThat(lifecycle.transitionAggregate("ACTIVE", PolicyLifecycleEvent.DISABLE))
            .isEqualTo("DISABLED");
    }

    @Test
    void onlyCreatesAndEditsNextVersionAfterCurrentVersionIsDisabled() {
        assertThat(lifecycle.transitionAggregate("DISABLED", PolicyLifecycleEvent.CREATE_VERSION))
            .isEqualTo("DISABLED");
        assertThat(lifecycle.transitionAggregate("DISABLED", PolicyLifecycleEvent.EDIT_DRAFT))
            .isEqualTo("DISABLED");
        assertThatThrownBy(() -> lifecycle.transitionAggregate("ACTIVE", PolicyLifecycleEvent.CREATE_VERSION))
            .hasMessageContaining("不允许");
        assertThatThrownBy(() -> lifecycle.transitionAggregate("ACTIVE", PolicyLifecycleEvent.EDIT_DRAFT))
            .hasMessageContaining("不允许");
    }

    @Test
    void archivesThePreviousRuntimeVersionWhenReplacementIsPublished() {
        assertThat(lifecycle.transitionVersion("PUBLISHED", PolicyLifecycleEvent.REPLACE_CURRENT))
            .isEqualTo("SUPERSEDED");
    }

    @Test
    void rejectsEditingPublishedVersionAndDisablingDraft() {
        assertThatThrownBy(() -> lifecycle.transitionVersion("PUBLISHED", PolicyLifecycleEvent.EDIT_DRAFT))
            .hasMessageContaining("不允许");
        assertThatThrownBy(() -> lifecycle.transitionAggregate("DRAFT", PolicyLifecycleEvent.DISABLE))
            .hasMessageContaining("不允许");
        assertThatThrownBy(() -> lifecycle.transitionAggregate("ACTIVE", PolicyLifecycleEvent.PUBLISH))
            .hasMessageContaining("不允许");
    }

    @Test
    void abandonsOnlyUnpublishedVersionAndEnablesOnlyDisabledGroup() {
        assertThat(lifecycle.transitionVersion("DRAFT", PolicyLifecycleEvent.DISCARD_PENDING))
            .isEqualTo("DISCARDED");
        assertThat(lifecycle.transitionAggregate("DISABLED", PolicyLifecycleEvent.ENABLE))
            .isEqualTo("ACTIVE");
    }
}
