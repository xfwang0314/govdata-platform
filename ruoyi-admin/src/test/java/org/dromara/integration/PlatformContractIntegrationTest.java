package org.dromara.integration;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jsqlparser.expression.StringValue;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.GlobalErrorCode;
import org.dromara.common.json.config.JacksonConfig;
import org.dromara.common.mybatis.enums.DataScopeType;
import org.dromara.common.mybatis.helper.DataPermissionHelper;
import org.dromara.common.tenant.handle.PlusTenantLineHandler;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.common.tenant.properties.TenantProperties;
import org.dromara.common.web.filter.TraceIdFilter;
import org.dromara.workflow.domain.bo.TestLeaveBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 平台基础契约集成测试，不依赖外部数据库和 Redis。
 */
@Tag("dev")
class PlatformContractIntegrationTest {

    @Test
    void apiResponseShouldContainStableErrorCodeAndTraceId() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
            .addFilters(new TraceIdFilter())
            .build();

        mockMvc.perform(get("/contract/page").header(TraceIdFilter.TRACE_ID_HEADER, "trace-test-0001"))
            .andExpect(status().isOk())
            .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "trace-test-0001"))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.errorCode").value("0"))
            .andExpect(jsonPath("$.traceId").value("trace-test-0001"))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.rows[0]").value("row"));

        mockMvc.perform(get("/contract/error"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.errorCode").value(GlobalErrorCode.VALIDATION_ERROR.getCode()))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void localDateTimeShouldUseUnifiedStringFormat() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JacksonConfig().registerJavaTimeModule());
        LocalDateTime value = LocalDateTime.of(2026, 7, 26, 21, 30, 15);

        String json = objectMapper.writeValueAsString(new TimePayload(value));
        assertThat(json).contains("\"time\":\"2026-07-26 21:30:15\"");
        assertThat(objectMapper.readValue(json, TimePayload.class).time()).isEqualTo(value);

        TestLeaveBo leave = objectMapper.readValue("""
            {
              "startDate": "2026-07-08 00:00:00",
              "endDate": "2026-07-09 00:00:00"
            }
            """, TestLeaveBo.class);
        assertThat(leave.getStartDate()).isEqualTo(LocalDateTime.of(2026, 7, 8, 0, 0));
        assertThat(leave.getEndDate()).isEqualTo(LocalDateTime.of(2026, 7, 9, 0, 0));
    }

    @Test
    void tenantHandlerShouldInjectTenantAndRespectExcludedTables() {
        TenantProperties properties = new TenantProperties();
        properties.setEnable(true);
        properties.setExcludes(List.of("sys_config"));
        PlusTenantLineHandler handler = new PlusTenantLineHandler(properties);

        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn("000001");
            assertThat(((StringValue) handler.getTenantId()).getValue()).isEqualTo("000001");
            assertTrue(handler.ignoreTable("sys_config"));
            assertFalse(handler.ignoreTable("gov_metadata"));
        }
    }

    @Test
    void dataPermissionIgnoreShouldRestoreNestedStateAndUseRoleVariable() {
        assertFalse(InterceptorIgnoreHelper.willIgnoreDataPermission("contract.test"));
        DataPermissionHelper.ignore(() -> {
            assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission("contract.test"));
            DataPermissionHelper.ignore(() ->
                assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission("contract.test")));
            assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission("contract.test"));
        });
        assertFalse(InterceptorIgnoreHelper.willIgnoreDataPermission("contract.test"));
        assertThat(DataScopeType.CUSTOM.getSqlTemplate()).contains("#roleId");
        assertThat(DataScopeType.CUSTOM.getSqlTemplate()).doesNotContain("#user.roleId");
    }

    @Test
    void swaggerUiShouldBePresentForInteractiveApiVerification() throws ClassNotFoundException {
        assertThat(Class.forName("org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc")).isNotNull();
    }

    @RestController
    static class ContractController {

        @GetMapping("/contract/page")
        R<PageResult<String>> page() {
            return R.ok(PageResult.build(List.of("row"), 1));
        }

        @GetMapping("/contract/error")
        R<Void> error() {
            return R.fail(GlobalErrorCode.VALIDATION_ERROR, "参数错误");
        }
    }

    record TimePayload(LocalDateTime time) {
    }
}
