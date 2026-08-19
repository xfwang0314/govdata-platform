package com.xfwang.platform.auth.application.options;

import com.xfwang.platform.auth.application.catalog.AuthorizationCatalog;
import com.xfwang.platform.auth.application.catalog.SystemManagementAuthorizationCatalogContributor;
import com.xfwang.platform.auth.application.options.view.AuthzOptionsView;
import org.dromara.system.api.permission.SystemAuthorizationResources;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationOptionsQueryServiceTest {

    @Test
    void shouldExposeLocalizedPolicyOptions() {
        AuthorizationOptionsQueryService service =
            new AuthorizationOptionsQueryService(new AuthorizationCatalog(List.of()));

        AuthzOptionsView options = service.options();

        assertThat(options.getPolicyEffects()).extracting("label", "value")
            .containsExactly(tuple("允许", "PERMIT"), tuple("拒绝", "DENY"));
        assertThat(options.getPolicyRiskLevels()).extracting("label", "value")
            .containsExactly(
                tuple("低风险", "LOW"),
                tuple("中风险", "MEDIUM"),
                tuple("高风险", "HIGH"),
                tuple("极高风险", "CRITICAL"));
        assertThat(options.getPolicyStatuses()).extracting("label", "value")
            .containsExactly(
                tuple("待发布", "DRAFT"),
                tuple("生效中", "ACTIVE"),
                tuple("已停用", "DISABLED"));
        assertThat(options.getPolicyVersionStatuses()).extracting("label", "value")
            .containsExactly(
                tuple("待发布", "DRAFT"),
                tuple("已发布", "PUBLISHED"),
                tuple("已被替代", "SUPERSEDED"),
                tuple("已放弃", "DISCARDED"));
        assertThat(options.getGrantStatuses()).extracting("label", "value")
            .containsExactly(
                tuple("草稿", "DRAFT"),
                tuple("待激活", "PENDING"),
                tuple("生效中", "ACTIVE"),
                tuple("已过期", "EXPIRED"),
                tuple("已撤销", "REVOKED"));
        assertThat(options.getSubjectTypes()).extracting("label", "value")
            .containsExactly(
                tuple("所有主体", "ANY"),
                tuple("指定用户", "HUMAN"),
                tuple("角色", "ROLE"),
                tuple("部门", "DEPARTMENT"),
                tuple("服务身份", "SERVICE"),
                tuple("Agent 身份", "AGENT"));
        assertThat(options.getOperators()).extracting("label", "value")
            .contains(tuple("等于", "EQ"), tuple("存在", "EXISTS"));
    }

    @Test
    void shouldExposeResourceSelectionModesFromCatalog() {
        AuthorizationOptionsQueryService service = new AuthorizationOptionsQueryService(
            new AuthorizationCatalog(List.of(new SystemManagementAuthorizationCatalogContributor())));

        AuthzOptionsView options = service.options();

        assertThat(options.getResourceTypes())
            .allSatisfy(type -> assertThat(type.getSelectionModes()).containsExactly("TYPE"));
        assertThat(options.getResourceTypes())
            .allSatisfy(type -> assertThat(type.getCapabilities())
                .containsExactlyInAnyOrder("CUSTOM_POLICY", "QUERY_SCOPE"));
        assertThat(options.getResourceTypes())
            .filteredOn(type -> SystemAuthorizationResources.USER.equals(type.getTypeCode()))
            .singleElement()
            .satisfies(type -> {
                assertThat(type.getPurposes()).extracting("label", "value")
                    .containsExactly(tuple("用户管理", SystemAuthorizationResources.USER_ADMINISTRATION));
                assertThat(type.getActions()).extracting("value")
                    .containsExactly("VIEW", "EXPORT", "CREATE", "UPDATE", "DELETE");
            });
        assertThat(options.getResourceTypes())
            .filteredOn(type -> SystemAuthorizationResources.DEPARTMENT.equals(type.getTypeCode()))
            .singleElement()
            .satisfies(type -> {
                assertThat(type.getPurposes()).extracting("label", "value")
                    .containsExactly(tuple("部门管理", SystemAuthorizationResources.DEPARTMENT_ADMINISTRATION));
                assertThat(type.getActions()).extracting("value")
                    .containsExactly("VIEW", "CREATE_CHILD", "UPDATE", "DELETE");
            });
        assertThat(options.getResourceTypes())
            .filteredOn(type -> SystemAuthorizationResources.POST.equals(type.getTypeCode()))
            .singleElement()
            .satisfies(type -> {
                assertThat(type.getPurposes()).extracting("label", "value")
                    .containsExactly(tuple("岗位管理", SystemAuthorizationResources.POST_ADMINISTRATION));
                assertThat(type.getActions()).extracting("value")
                    .containsExactly("VIEW", "EXPORT", "CREATE", "UPDATE", "DELETE");
            });
        assertThat(options.getResourceTypes())
            .filteredOn(type -> SystemAuthorizationResources.ROLE.equals(type.getTypeCode()))
            .singleElement()
            .satisfies(type -> {
                assertThat(type.getPurposes()).extracting("label", "value")
                    .containsExactly(tuple("角色管理", SystemAuthorizationResources.ROLE_ADMINISTRATION));
                assertThat(type.getActions()).extracting("value")
                    .containsExactly("VIEW", "EXPORT", "CREATE", "UPDATE", "DELETE");
            });
        assertThat(options.getGrantPurposes()).isEmpty();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
