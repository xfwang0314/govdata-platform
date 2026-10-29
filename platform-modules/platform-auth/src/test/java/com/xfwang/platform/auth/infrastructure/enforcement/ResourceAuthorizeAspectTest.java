package com.xfwang.platform.auth.infrastructure.enforcement;

import com.xfwang.platform.api.auth.annotation.ResourceAuthorize;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.model.AccessDecision;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.api.auth.service.AuthorizationService;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceAuthorizeAspectTest {

    @Test
    void shouldResolveSpringBeanInResourceExpression() {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ResourceRegistry resourceRegistry = mock(ResourceRegistry.class);
        when(authorizationService.decide(any())).thenReturn(AccessDecision.builder()
            .decision(DecisionType.PERMIT)
            .build());
        when(resourceRegistry.findByBusinessKey("test", "TEST_RESOURCE", "plan-21"))
            .thenReturn(Optional.of(ResourceDescriptor.builder().resourceId(100L).build()));

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("testResourceResolver", new TestResourceResolver());
        ResourceAuthorizeAspect aspect = new ResourceAuthorizeAspect(authorizationService, resourceRegistry,
            new ThreadLocalAuthorizationContextAccessor(), beanFactory);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new SecuredService());
        proxyFactory.addAspect(aspect);

        SecuredService service = proxyFactory.getProxy();

        assertThat(service.get(21L)).isEqualTo("detail-21");
    }

    static class TestResourceResolver {
        public String resolve(Long id) {
            return "plan-" + id;
        }
    }

    static class SecuredService {
        @ResourceAuthorize(businessId = "@testResourceResolver.resolve(#id)", sourceModule = "test",
            resourceType = "TEST_RESOURCE", action = "VIEW", purpose = "TEST")
        public String get(Long id) {
            return "detail-" + id;
        }
    }
}
