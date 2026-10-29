package com.xfwang.platform.auth.infrastructure.identity;

import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectAttributeContributor;
import org.dromara.system.api.UserService;
import org.dromara.system.api.domain.UserAuthorizationProfileDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaTokenSubjectProviderTest {

    @Test
    void buildsCurrentAuthorizationAttributesForOwnerWithoutLoginSession() {
        UserService userService = mock(UserService.class);
        UserAuthorizationProfileDTO profile = new UserAuthorizationProfileDTO();
        profile.setTenantId("103");
        profile.setUserId(1001L);
        profile.setDeptId(2001L);
        profile.setDeptPathIds(Set.of(20L, 2001L));
        profile.setDeptCategory("GOVERNANCE");
        profile.setUserType("sys_user");
        profile.setRoleIds(Set.of(7L));
        profile.setRoleKeys(Set.of("data_steward"));
        profile.setStatus("0");
        when(userService.selectAuthorizationProfileById(1001L)).thenReturn(profile);
        SubjectAttributeContributor contributor = mock(SubjectAttributeContributor.class);
        when(contributor.attributes(org.mockito.ArgumentMatchers.any()))
            .thenReturn(Map.of("securityLevel", 3));
        SaTokenSubjectProvider provider = new SaTokenSubjectProvider(
            List.of(contributor), List.of(), userService);

        SubjectContext subject = provider.subjectForUser("103", 1001L);

        assertEquals(Set.of(20L, 2001L), subject.getDeptPathIds());
        assertEquals(Set.of(7L), subject.getRoleIds());
        assertEquals(Set.of("data_steward"), subject.getRoleKeys());
        assertEquals("GOVERNANCE", subject.getAttributes().get("deptCategory"));
        assertEquals(3, subject.getAttributes().get("securityLevel"));
    }
}
