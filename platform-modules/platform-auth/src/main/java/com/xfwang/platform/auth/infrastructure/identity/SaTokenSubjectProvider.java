package com.xfwang.platform.auth.infrastructure.identity;

import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectAttributeRequest;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectAttributeContributor;
import com.xfwang.platform.api.auth.service.SubjectContextOverride;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.system.api.domain.RoleDTO;
import org.dromara.system.api.domain.UserAuthorizationProfileDTO;
import org.dromara.system.api.UserService;
import org.dromara.system.api.model.LoginUser;
import org.dromara.common.tenant.helper.TenantHelper;
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
    private final List<SubjectContextOverride> subjectOverrides;
    private final UserService userService;

    public SaTokenSubjectProvider(List<SubjectAttributeContributor> attributeContributors,
                                  List<SubjectContextOverride> subjectOverrides,
                                  UserService userService) {
        this.attributeContributors = List.copyOf(attributeContributors);
        this.subjectOverrides = List.copyOf(subjectOverrides);
        this.userService = userService;
    }

    @Override
    public SubjectContext currentSubject() {
        for (SubjectContextOverride subjectOverride : subjectOverrides) {
            var overridden = subjectOverride.currentSubject();
            if (overridden.isPresent()) {
                return overridden.get();
            }
        }
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

    @Override
    public SubjectContext subjectForUser(String tenantId, Long userId) {
        if (tenantId == null || tenantId.isBlank() || userId == null) {
            throw new ServiceException(GlobalErrorCode.VALIDATION_ERROR, "租户和用户不能为空");
        }
        UserAuthorizationProfileDTO profile = TenantHelper.dynamic(tenantId,
            () -> userService.selectAuthorizationProfileById(userId));
        if (profile == null || !Objects.equals(tenantId, profile.getTenantId())) {
            throw new ServiceException(GlobalErrorCode.FORBIDDEN, "Agent 凭据归属用户不存在或租户不匹配");
        }
        if (!"0".equals(profile.getStatus())) {
            throw new ServiceException(GlobalErrorCode.FORBIDDEN, "Agent 凭据归属用户已停用");
        }
        Set<Long> roleIds = profile.getRoleIds() == null ? Set.of() : Set.copyOf(profile.getRoleIds());
        Set<String> roleKeys = profile.getRoleKeys() == null ? Set.of() : Set.copyOf(profile.getRoleKeys());
        Set<Long> deptPathIds = profile.getDeptPathIds() == null
            ? (profile.getDeptId() == null ? Set.of() : Set.of(profile.getDeptId()))
            : Set.copyOf(profile.getDeptPathIds());
        Map<String, Object> attributes = buildAttributes(tenantId, userId, profile.getDeptId(),
            deptPathIds, roleIds, roleKeys, profile.getDeptCategory(), profile.getUserType());
        return SubjectContext.builder()
            .tenantId(tenantId)
            .principalType(PrincipalType.HUMAN)
            .principalId(String.valueOf(userId))
            .userId(userId)
            .deptId(profile.getDeptId())
            .deptPathIds(deptPathIds)
            .roleIds(roleIds)
            .roleKeys(roleKeys)
            .attributes(attributes)
            .build();
    }

    private Map<String, Object> buildAttributes(String tenantId, Long userId, Long deptId,
                                                Set<Long> deptPathIds, Set<Long> roleIds,
                                                Set<String> roleKeys, String deptCategory,
                                                String userType) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("deptCategory", deptCategory == null ? "" : deptCategory);
        attributes.put("userType", userType == null ? "" : userType);
        SubjectAttributeRequest request = new SubjectAttributeRequest(
            tenantId, PrincipalType.HUMAN, String.valueOf(userId), deptId, deptPathIds, roleIds, roleKeys);
        for (SubjectAttributeContributor contributor : attributeContributors) {
            Map<String, Object> contributed = contributor.attributes(request);
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
        return Map.copyOf(attributes);
    }
}
