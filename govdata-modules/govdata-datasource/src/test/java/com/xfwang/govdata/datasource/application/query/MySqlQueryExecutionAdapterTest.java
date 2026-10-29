package com.xfwang.govdata.datasource.application.query;

import com.xfwang.govdata.api.query.spi.AuthorizedExecutionPlan;
import com.xfwang.govdata.api.query.spi.QueryResourceSchema;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.service.AuthorizationContextAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@Tag("dev")
class MySqlQueryExecutionAdapterTest {
    @Test
    void refusesDirectExecutionWithoutPermitContext() {
        var adapter = new MySqlQueryExecutionAdapter(mock(DataSourceInstanceMapper.class),
            List.of(), mock(AuthorizationContextAccessor.class));
        assertThatThrownBy(() -> adapter.execute(AuthorizedExecutionPlan.builder()
            .resource(QueryResourceSchema.builder().resourceId(1L).datasourceId(2L).build()).build()))
            .hasFieldOrPropertyWithValue("errorCode", "GOV-DS-0403-QUERY");
    }
}
