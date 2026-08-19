package com.xfwang.platform.auth.infrastructure.identity;

import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectAttributeRequest;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectAttributeContributor;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.domain.RoleDTO;
import org.dromara.system.api.model.LoginUser;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 Sa-Token 登录态适配为 ABAC Subject。
 */
@Component
public class SaTokenSubjectProvider implements SubjectProvider {

    private final List<SubjectAttributeContributor> attributeContributors;

    public SaTokenSubjectProvider(List<SubjectAttributeContributor> attributeContributors) {
        this.attributeContributors = List.copyOf(attributeContributors);
    }

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
        Set<Long> deptPathIds = loginUser.getDeptPathIds() == null
            ? (loginUser.getDeptId() == null ? Set.of() : Set.of(loginUser.getDeptId()))
            : Set.copyOf(loginUser.getDeptPathIds());
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("deptCategory", loginUser.getDeptCategory() == null ? "" : loginUser.getDeptCategory());
        attributes.put("userType", loginUser.getUserType() == null ? "" : loginUser.getUserType());
        SubjectAttributeRequest attributeRequest = new SubjectAttributeRequest(
            loginUser.getTenantId(), PrincipalType.HUMAN, String.valueOf(loginUser.getUserId()),
            loginUser.getDeptId(), deptPathIds, roleIds, roleKeys);
        for (SubjectAttributeContributor contributor : attributeContributors) {
            Map<String, Object> contributed = contributor.attributes(attributeRequest);
            if (contributed == null) {
                continue;
            }
            contributed.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null) {
                    throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                        "Subject 属性扩展禁止返回空编码或空值");
                }
                if (attributes.putIfAbsent(key, value) != null) {
                    throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR,
                        "Subject 属性编码重复: " + key);
                }
            });
        }
        return SubjectContext.builder()
            .tenantId(loginUser.getTenantId())
            .principalType(PrincipalType.HUMAN)
            .principalId(String.valueOf(loginUser.getUserId()))
            .userId(loginUser.getUserId())
            .deptId(loginUser.getDeptId())
            .deptPathIds(deptPathIds)
            .roleIds(roleIds)
            .roleKeys(roleKeys)
            .attributes(Map.copyOf(attributes))
            .build();
    }
}
