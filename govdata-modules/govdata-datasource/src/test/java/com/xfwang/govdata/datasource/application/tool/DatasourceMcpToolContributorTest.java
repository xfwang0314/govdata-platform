package com.xfwang.govdata.datasource.application.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.datasource.application.metadata.DataSourceMetadataInspectService;
import com.xfwang.govdata.datasource.application.metadata.view.ColumnView;
import com.xfwang.govdata.datasource.application.metadata.view.DatabaseView;
import com.xfwang.govdata.datasource.application.metadata.view.TableSchemaView;
import com.xfwang.govdata.datasource.application.metadata.view.TableView;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.model.AuthorizedResourceScope;
import com.xfwang.platform.api.auth.service.AuthorizedResourceService;
import com.xfwang.platform.api.auth.service.ResourceRegistry;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DatasourceMcpToolContributorTest {

    @Mock
    private DataSourceInstanceMapper instanceMapper;

    @Mock
    private DataSourceMetadataInspectService metadataInspectService;

    @Mock
    private AuthorizedResourceService authorizedResourceService;

    @Mock
    private ResourceRegistry resourceRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DatasourceMcpToolContributor contributor;

    @BeforeEach
    void setUp() {
        contributor = new DatasourceMcpToolContributor(
            instanceMapper,
            metadataInspectService,
            objectMapper,
            authorizedResourceService,
            resourceRegistry
        );
        lenient().when(authorizedResourceService.resolveScope(any()))
            .thenReturn(AuthorizedResourceScope.unrestrictedScope());
    }

    @Test
    void testCategoryAndTools() {
        assertEquals("DATASOURCE", contributor.module().code());
        List<McpToolDefinition> tools = contributor.tools();
        assertEquals(4, tools.size());
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("datasource.list_datasources")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("datasource.list_databases")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("datasource.list_tables")));
        assertTrue(tools.stream().anyMatch(t -> t.getName().equals("datasource.get_table_schema")));
    }

    @Test
    void testExecuteListDatasources() {
        DataSourceInstance instance = new DataSourceInstance();
        instance.setDatasourceId(101L);
        instance.setDatasourceName("政务核心库");
        instance.setDatasourceCode("gov_core");
        instance.setConnectorCode("MYSQL");
        instance.setConnectorVersion("8.0");
        instance.setSecurityLevel(1);
        instance.setStatus("ACTIVE");

        when(instanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(instance));

        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        McpCallResponse response = contributor.execute(
            "datasource.list_datasources",
            Map.of(),
            subject
        );

        assertNotNull(response);
        assertFalse(response.isError());
        String text = response.getContent().get(0).getText();
        assertTrue(text.contains("政务核心库"));
        assertTrue(text.contains("101"));
    }

    @Test
    void testExecuteListDatabases() {
        when(metadataInspectService.listDatabases(101L))
            .thenReturn(List.of(new DatabaseView(null, null, "gov_db", "人口库")));

        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        McpCallResponse response = contributor.execute(
            "datasource.list_databases",
            Map.of("datasource_id", "101"),
            subject
        );

        assertNotNull(response);
        assertFalse(response.isError());
        String text = response.getContent().get(0).getText();
        assertTrue(text.contains("gov_db"));
    }

    @Test
    void testExecuteListTables() {
        when(metadataInspectService.listTables(eq(101L), any(), any(), any(), any(), eq(0), eq(100)))
            .thenReturn(List.of(new TableView(null, "gov_db", "t_person", "TABLE", "人口基础表")));

        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        McpCallResponse response = contributor.execute(
            "datasource.list_tables",
            Map.of("datasource_id", "101", "database_name", "gov_db"),
            subject
        );

        assertNotNull(response);
        assertFalse(response.isError());
        String text = response.getContent().get(0).getText();
        assertTrue(text.contains("t_person"));
        assertTrue(text.contains("人口基础表"));
    }

    @Test
    void testExecuteGetTableSchema() {
        ColumnView col1 = ColumnView.builder().columnName("id").dataType("BIGINT").columnSize(20).primaryKey(true).nullable(false).remarks("主键ID").build();
        ColumnView col2 = ColumnView.builder().columnName("name").dataType("VARCHAR").columnSize(64).primaryKey(false).nullable(true).remarks("姓名").build();
        TableView tableView = new TableView(null, "gov_db", "t_person", "TABLE", "人口基础表");
        TableSchemaView schemaView = new TableSchemaView(tableView, List.of(col1, col2));

        when(metadataInspectService.inspectColumns(eq(101L), any(), any(), eq(List.of("t_person"))))
            .thenReturn(List.of(schemaView));

        SubjectContext subject = SubjectContext.builder().tenantId("103").build();
        McpCallResponse response = contributor.execute(
            "datasource.get_table_schema",
            Map.of("datasource_id", "101", "table_name", "t_person"),
            subject
        );

        assertNotNull(response);
        assertFalse(response.isError());
        String text = response.getContent().get(0).getText();
        assertTrue(text.contains("t_person"));
        assertTrue(text.contains("BIGINT"));
        assertTrue(text.contains("姓名"));
    }
}
