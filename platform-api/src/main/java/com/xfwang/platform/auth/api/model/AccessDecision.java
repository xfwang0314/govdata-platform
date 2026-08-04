package com.xfwang.platform.auth.api.model;

import com.xfwang.platform.auth.api.enums.DecisionType;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ABAC 授权决策。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private DecisionType decision;

    private String reasonCode;

    private String reason;

    @Builder.Default
    private List<Long> matchedPolicyIds = new ArrayList<>();

    @Builder.Default
    private List<Long> matchedGrantIds = new ArrayList<>();

    private AuthorizationObligation obligation;

    public boolean isAllowed() {
        return decision == DecisionType.PERMIT;
    }
}
