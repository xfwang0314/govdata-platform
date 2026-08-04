package com.xfwang.platform.auth.application;

import com.xfwang.platform.auth.application.model.PolicyDraftCommand;
import com.xfwang.platform.auth.application.model.PolicyView;

/** Policy 生命周期管理 seam。 */
public interface PolicyAdministrationService {

    PolicyView createDraft(PolicyDraftCommand command);

    PolicyView publish(Long policyId);

    PolicyView get(Long policyId);
}
