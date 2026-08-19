package com.xfwang.platform.auth.application.resource;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.platform.api.auth.model.ResourceDescriptor;
import com.xfwang.platform.auth.application.decision.AuthorizationJsonCodec;
import com.xfwang.platform.auth.application.resource.query.ResourceQuery;
import com.xfwang.platform.auth.domain.entity.AuthzResource;
import com.xfwang.platform.auth.infrastructure.persistence.mapper.AuthzResourceMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceQueryServiceTest {

    @Test
    void exposesCrossModuleAncestorPathForResourceSelection() {
        AuthzResourceMapper mapper = mock(AuthzResourceMapper.class);
        AuthzResource datasource = resource(10L, null, "省人口库", "govdata-datasource");
        AuthzResource schema = resource(20L, 10L, "public", "govdata-metadata");
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<AuthzResource> page = invocation.getArgument(0);
            page.setRecords(List.of(schema));
            page.setTotal(1);
            return page;
        });
        when(mapper.selectByIds(any())).thenReturn(List.of(datasource));

        ResourceQueryService service = new ResourceQueryService(
            mapper, new AuthorizationJsonCodec(new ObjectMapper()));
        PageResult<ResourceDescriptor> result = service.page(
            new ResourceQuery(), new PageQuery(20, 1));

        assertThat(result.getRows()).singleElement()
            .extracting(ResourceDescriptor::getResourcePath)
            .isEqualTo("省人口库 / public");
    }

    private AuthzResource resource(Long id, Long parentId, String name, String module) {
        AuthzResource resource = new AuthzResource();
        resource.setResourceId(id);
        resource.setParentId(parentId);
        resource.setResourceName(name);
        resource.setResourceCode("res_" + id);
        resource.setResourceType("test:" + name);
        resource.setSourceModule(module);
        resource.setStatus("ACTIVE");
        return resource;
    }
}
