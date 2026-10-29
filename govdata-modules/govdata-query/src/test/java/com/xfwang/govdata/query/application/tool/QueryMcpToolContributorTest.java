package com.xfwang.govdata.query.application.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.api.query.AuthorizedQuery;
import com.xfwang.govdata.api.query.AuthorizedQueryGateway;
import com.xfwang.govdata.api.query.QueryColumn;
import com.xfwang.govdata.api.query.QueryResult;
import com.xfwang.govdata.api.query.spi.QueryExecutionAdapter;
import com.xfwang.govdata.api.query.spi.QueryResourceSchemaProvider;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.mcp.model.McpCallResponse;
import com.xfwang.platform.api.mcp.model.McpToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryMcpToolContributorTest {

    @Mock
    private AuthorizedQueryGateway queryGateway;

    @Mock
    private QueryResourceSchemaProvider schemaProvider;

    @Mock
    private QueryExecutionAdapter executionAdapter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QueryMcpToolContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new QueryMcpToolContributor(queryGateway, objectMapper,
            List.of(schemaProvider), List.of(executionAdapter));
    }

    @Test
    void rejectsRawSqlInsteadOfSilentlyIgnoringIt() {
        var boundary = new QueryMcpToolContributor(query -> QueryResult.builder().returnedRows(0).hasMore(false).build(),
            objectMapper, List.of(schemaProvider), List.of(executionAdapter));
        var response = boundary.execute("datasource.query_dataset", Map.of("datasource_id", "1",
            "resource_id", "2", "field_ids", List.of("3"), "sql", "DROP TABLE people"),
            SubjectContext.builder().tenantId("000000").build());
        assertTrue(response.isError());
    }

    @Test
    void testCategoryAndTools() {
        assertEquals("DATASOURCE", contributor.module().code());
        List<McpToolDefinition> tools = contributor.tools();
        assertEquals(2, tools.size());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("datasource.query_dataset")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("datasource.sample_dataset_records")));
    }

    @Test
    void queryToolsAreNotAdvertisedWithoutSchemaAndExecutionAdapters() {
        QueryMcpToolContributor unavailable = new QueryMcpToolContributor(
            queryGateway, objectMapper, List.of(), List.of());

        assertTrue(unavailable.tools().isEmpty());
    }

    @Test
    void testExecuteQueryDatasetSuccess() {
        QueryResult queryResult = QueryResult.builder()
            .returnedRows(1)
            .hasMore(false)
            .columns(List.of(QueryColumn.builder().fieldId(10L).fieldCode("name").displayName("姓名").masked(false).build()))
            .rows(List.of(Map.of(10L, "张*三")))
            .build();

        when(queryGateway.execute(any(AuthorizedQuery.class))).thenReturn(queryResult);

        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        McpCallResponse response = contributor.execute(
            "datasource.query_dataset",
            Map.of(
                "datasource_id", "101",
                "resource_id", "201",
                "field_ids", List.of(10, 11),
                "limit", 10
            ),
            subject
        );

        assertNotNull(response);
        assertFalse(response.isError());
        String text = response.getContent().get(0).getText();
        assertTrue(text.contains("张*三"));
        assertTrue(text.contains("101"));
        assertTrue(text.contains("201"));
    }

    @Test
    void testExecuteSampleDatasetSuccess() {
        QueryResult queryResult = QueryResult.builder()
            .returnedRows(1)
            .hasMore(false)
            .columns(List.of(QueryColumn.builder().fieldId(10L).fieldCode("id_card").displayName("身份证").masked(true).build()))
            .rows(List.of(Map.of(10L, "440301********1234")))
            .build();

        when(queryGateway.execute(any(AuthorizedQuery.class))).thenReturn(queryResult);

        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        McpCallResponse response = contributor.execute(
            "datasource.sample_dataset_records",
            Map.of(
                "datasource_id", "101",
                "resource_id", "201",
                "field_ids", List.of(10)
            ),
            subject
        );

        assertNotNull(response);
        assertFalse(response.isError());
        String text = response.getContent().get(0).getText();
        assertTrue(text.contains("440301********1234"));
    }
}
