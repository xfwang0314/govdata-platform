package org.dromara.system.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.permission.DataAuthorizationScope;
import org.dromara.system.api.permission.DataAuthorizationScopeProvider;
import org.dromara.system.api.permission.DataAuthorizationScopeRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 将平台 ABAC 结构化范围应用到系统管理对象写操作。 */
@Component
@RequiredArgsConstructor
public class SystemDataAuthorizationGuard {

    private final ObjectProvider<DataAuthorizationScopeProvider> providerLookup;

    public void check(DataAuthorizationScopeRequest request, Long ownerDeptId, Long ownerUserId) {
        DataAuthorizationScopeProvider provider = providerLookup.getIfAvailable();
        DataAuthorizationScope scope = provider == null
            ? DataAuthorizationScope.deniedScope()
            : provider.resolve(request).orElseGet(DataAuthorizationScope::deniedScope);
        if (scope.unrestricted()) {
            return;
        }
        boolean departmentAllowed = ownerDeptId != null && scope.departmentIds().contains(ownerDeptId);
        boolean currentUserAllowed = scope.includeCurrentUser() && ownerUserId != null
            && Optional.ofNullable(LoginHelper.getUserId()).filter(ownerUserId::equals).isPresent();
        if (scope.denied() || (!departmentAllowed && !currentUserAllowed)) {
            throw new ServiceException("没有权限对目标数据执行该操作！");
        }
    }
}
