package com.xfwang.platform.auth.application.grant;

import com.xfwang.platform.auth.application.grant.command.GrantDraftCommand;
import com.xfwang.platform.auth.application.grant.view.GrantView;
import com.xfwang.platform.auth.application.grant.query.GrantQuery;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/** AuthorizationGrant 生命周期管理 seam。 */
public interface GrantAdministrationService {
    GrantView createDraft(GrantDraftCommand command);
    GrantView updateDraft(Long grantId, GrantDraftCommand command);
    GrantView activate(Long grantId);
    GrantView revoke(Long grantId, String reason);
    GrantView get(Long grantId);
    PageResult<GrantView> page(GrantQuery query, PageQuery pageQuery);
    void delete(Long grantId, String reason);
    GrantView restore(Long grantId);
}
