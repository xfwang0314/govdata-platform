package com.xfwang.govdata.metadata.interfaces.controller;

import com.xfwang.govdata.metadata.application.catalog.MetadataCatalogQueryService;
import com.xfwang.govdata.metadata.application.catalog.MetadataGovernanceService;
import com.xfwang.govdata.metadata.application.impact.MetadataImpactQueryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class MetadataAssetControllerRoutingTest {

    @Test
    void namespacesRouteDoesNotFallThroughToNumericAssetDetail() throws Exception {
        MetadataCatalogQueryService queryService = mock(MetadataCatalogQueryService.class);
        when(queryService.namespaceTree(1L)).thenReturn(List.of());
        MetadataAssetController controller = new MetadataAssetController(queryService,
            mock(MetadataGovernanceService.class), mock(MetadataImpactQueryService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/govdata/metadata/assets/namespaces").param("datasourceId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(queryService).namespaceTree(1L);
    }
}
