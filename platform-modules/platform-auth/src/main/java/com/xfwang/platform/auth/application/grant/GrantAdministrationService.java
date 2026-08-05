package com.xfwang.platform.auth.application.grant;

import com.xfwang.platform.auth.application.grant.command.GrantDraftCommand;
import com.xfwang.platform.auth.application.grant.view.GrantView;

/** AuthorizationGrant 生命周期管理 seam。 */
public interface GrantAdministrationService {
    GrantView createDraft(GrantDraftCommand command);
    GrantView activate(Long grantId);
    GrantView revoke(Long grantId, String reason);
    GrantView get(Long grantId);
}
