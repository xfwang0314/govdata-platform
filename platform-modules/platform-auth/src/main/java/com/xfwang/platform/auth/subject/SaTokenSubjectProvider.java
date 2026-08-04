package com.xfwang.platform.auth.subject;

import com.xfwang.platform.auth.api.model.SubjectContext;
import com.xfwang.platform.auth.api.service.SubjectProvider;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.domain.RoleDTO;
import org.dromara.system.api.model.LoginUser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 Sa-Token 登录态适配为 ABAC Subject。
 */
@Component
public class SaTokenSubjectProvider implements SubjectProvider {

    @Override
    public SubjectContext currentSubject() {
        LoginUser loginUser = LoginHelper.getLoginUser();
        if (loginUser == null) {
            throw new ServiceException(GlobalErrorCode.UNAUTHORIZED);
        }
        Set<Long> roleIds = loginUser.getRoles() == null
            ? Set.of()
            : loginUser.getRoles().stream().map(RoleDTO::getRoleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> roleKeys = loginUser.getRoles() == null
            ? Set.of()
            : loginUser.getRoles().stream().map(RoleDTO::getRoleKey)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> deptPathIds = loginUser.getDeptId() == null
            ? Set.of()
            : Set.of(loginUser.getDeptId());
        return SubjectContext.builder()
            .tenantId(loginUser.getTenantId())
            .userId(loginUser.getUserId())
            .deptId(loginUser.getDeptId())
            .deptPathIds(deptPathIds)
            .roleIds(roleIds)
            .roleKeys(roleKeys)
            .attributes(Map.of(
                "deptCategory", loginUser.getDeptCategory() == null ? "" : loginUser.getDeptCategory(),
                "userType", loginUser.getUserType() == null ? "" : loginUser.getUserType()
            ))
            .build();
    }
}
