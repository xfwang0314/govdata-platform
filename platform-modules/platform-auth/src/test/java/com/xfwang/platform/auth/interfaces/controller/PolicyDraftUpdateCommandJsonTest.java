package com.xfwang.platform.auth.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.auth.application.policy.command.PolicyDraftUpdateCommand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class PolicyDraftUpdateCommandJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsVersionLockVersionFromUpdateRequest() throws Exception {
        PolicyDraftUpdateCommand command = objectMapper.readValue(
            "{\"versionLockVersion\":0}", PolicyDraftUpdateCommand.class);

        assertThat(command.getVersionLockVersion()).isZero();
        assertThat(objectMapper.writeValueAsString(command)).doesNotContain("versionLockVersion");
    }
}
