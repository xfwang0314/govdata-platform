package com.xfwang.govdata.metadata.interfaces.controller;

import com.xfwang.govdata.metadata.application.history.MetadataHistoryService;
import org.dromara.common.core.domain.PageResult;
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
class MetadataHistoryControllerTest {
    @Test
    void queriesIssuesForPathTableWithoutAcceptingAnotherTableFromQuery() throws Exception {
        MetadataHistoryService service = mock(MetadataHistoryService.class);
        when(service.pageIssues(eq(42L), any())).thenReturn(PageResult.build(List.of(), 0L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MetadataHistoryController(service)).build();

        mvc.perform(get("/govdata/metadata/tables/42/change-issues").param("tableAssetId", "99"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        verify(service).pageIssues(eq(42L), any());
    }

    @Test
    void removesUnscopedIssuesEndpoint() throws Exception {
        MetadataHistoryService service = mock(MetadataHistoryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MetadataHistoryController(service)).build();

        mvc.perform(get("/govdata/metadata/change-issues")).andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }
}
