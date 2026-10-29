package com.xfwang.govdata.metadata.harvest.interfaces.controller;

import com.xfwang.govdata.metadata.harvest.application.run.HarvestRunService;
import com.xfwang.govdata.metadata.harvest.application.run.query.HarvestRunQuery;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class HarvestRunControllerTest {
    @Test
    void listsRunsUnderRequiredPlanAndPassesStatusFilter() throws Exception {
        HarvestRunService service = mock(HarvestRunService.class);
        when(service.page(eq(42L), any(), any())).thenReturn(PageResult.build(List.of(), 0L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HarvestRunController(service)).build();

        mvc.perform(get("/govdata/metadata-harvest/plans/42/runs")
                .param("runStatus", "FAILED").param("harvestPlanId", "99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        org.mockito.ArgumentCaptor<HarvestRunQuery> query =
            org.mockito.ArgumentCaptor.forClass(HarvestRunQuery.class);
        verify(service).page(eq(42L), query.capture(), any(PageQuery.class));
        org.assertj.core.api.Assertions.assertThat(query.getValue().getRunStatus()).isEqualTo("FAILED");
    }

    @Test
    void removesUnscopedRunListEndpoint() throws Exception {
        HarvestRunService service = mock(HarvestRunService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HarvestRunController(service)).build();

        mvc.perform(get("/govdata/metadata-harvest/runs")).andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }
}
