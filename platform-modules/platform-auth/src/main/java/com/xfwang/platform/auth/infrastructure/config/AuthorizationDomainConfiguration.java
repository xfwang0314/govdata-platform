package com.xfwang.platform.auth.infrastructure.config;

import com.xfwang.platform.auth.domain.service.AdministratorAuthorizationPolicy;
import com.xfwang.platform.auth.domain.service.SubjectSelectorMatcher;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.constant.TenantConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 平台授权领域规则装配。 */
@Configuration
public class AuthorizationDomainConfiguration {

    @Bean
    public AdministratorAuthorizationPolicy administratorAuthorizationPolicy() {
        return new AdministratorAuthorizationPolicy(
            SystemConstants.SUPER_ADMIN_ID, TenantConstants.TENANT_ADMIN_ROLE_KEY);
    }

    @Bean
    public SubjectSelectorMatcher subjectSelectorMatcher() {
        return new SubjectSelectorMatcher();
    }
}
