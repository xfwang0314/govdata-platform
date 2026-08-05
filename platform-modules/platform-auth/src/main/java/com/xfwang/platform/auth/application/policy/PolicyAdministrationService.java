package com.xfwang.platform.auth.application.policy;

import com.xfwang.platform.auth.application.policy.command.PolicyDraftCommand;
import com.xfwang.platform.auth.application.policy.view.PolicyView;

/** Policy 生命周期管理 seam。 */
public interface PolicyAdministrationService {

    PolicyView createDraft(PolicyDraftCommand command);

    PolicyView publish(Long policyId);

    PolicyView get(Long policyId);
}
