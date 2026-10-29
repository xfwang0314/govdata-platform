package com.xfwang.govdata.datasource.application.metadata;

import com.xfwang.govdata.api.metadata.CollectedMetadataNamespace;
import com.xfwang.govdata.api.metadata.CollectedMetadataTable;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryRequest;
import com.xfwang.govdata.api.metadata.MetadataDiscoveryResult;
import com.xfwang.govdata.api.metadata.MetadataDiscoverySink;
import com.xfwang.govdata.api.metadata.MetadataObjectType;
import com.xfwang.govdata.datasource.application.metadata.view.TableSchemaView;
import com.xfwang.govdata.datasource.application.metadata.view.TableView;
import com.xfwang.govdata.datasource.domain.connector.ConnectorDescriptor;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorCatalog;
import com.xfwang.govdata.datasource.infrastructure.connector.ConnectorMetadataInspector;
import com.xfwang.govdata.datasource.infrastructure.connector.model.ColumnMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectResult;
import com.xfwang.govdata.datasource.infrastructure.connector.model.TableMeta;
import com.xfwang.govdata.datasource.infrastructure.connector.model.TableSchema;
import com.xfwang.govdata.datasource.infrastructure.connector.registry.MetadataInspectorRegistry;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class DataSourceMetadataInspectServiceTest {

    private DataSourceInstanceMapper instanceMapper;
    private MetadataInspectorRegistry inspectorRegistry;
    private ConnectorCatalog connectorCatalog;
    private ConnectorMetadataInspector inspector;
    private ThreadPoolTaskExecutor executor;
    private DataSourceMetadataInspectService service;
    private SubjectProvider subjectProvider;

    @BeforeEach
    void setUp() {
        instanceMapper = mock(DataSourceInstanceMapper.class);
        inspectorRegistry = mock(MetadataInspectorRegistry.class);
        connectorCatalog = mock(ConnectorCatalog.class);
        inspector = mock(ConnectorMetadataInspector.class);
        subjectProvider = mock(SubjectProvider.class);
        when(subjectProvider.currentSubject()).thenReturn(SubjectContext.builder().tenantId("103")
            .principalType(PrincipalType.HUMAN).principalId("1").userId(1L).build());

        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.initialize();

        service = new DataSourceMetadataInspectService(
            instanceMapper, inspectorRegistry, connectorCatalog, subjectProvider, executor
        );
    }

    @Test
    void listTablesFailsWhenDataSourceNotFound() {
        when(instanceMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.listTables(99L, null, null, null, null, 0, 100))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不存在");
    }

    @Test
    void listTablesFailsWhenDataSourceNotActive() {
        DataSourceInstance instance = activeInstance();
        instance.setStatus("DISABLED");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        assertThatThrownBy(() -> service.listTables(1L, null, null, null, null, 0, 100))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("启用状态");
    }

    @Test
    void listTablesFailsWhenConnectorLacksCapability() {
        DataSourceInstance instance = activeInstance();
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        ConnectorDescriptor descriptor = ConnectorDescriptor.builder()
            .connectorCode("mysql")
            .capabilities(Set.of("CONNECTION_TEST"))
            .build();
        when(connectorCatalog.require("mysql")).thenReturn(descriptor);

        assertThatThrownBy(() -> service.listTables(1L, null, null, null, null, 0, 100))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("METADATA_INSPECT");
    }

    @Test
    void listTablesReturnsTableViewsSuccessfully() {
        DataSourceInstance instance = activeInstance();
        when(instanceMapper.selectById(1L)).thenReturn(instance);
        when(connectorCatalog.require("mysql")).thenReturn(activeDescriptor());

        TableMeta meta = TableMeta.builder()
            .catalog("demo_db").schema(null).tableName("sys_user").tableType("TABLE").remarks("用户表")
            .build();
        MetadataInspectResult inspectResult = MetadataInspectResult.builder()
            .success(true).tables(List.of(meta)).total(1L)
            .build();

        when(inspectorRegistry.require("mysql", "1.0")).thenReturn(inspector);
        when(inspector.listTables(any())).thenAnswer(invocation -> {
            assertThat(((com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest)
                invocation.getArgument(0)).getTenantId()).isEqualTo("000000");
            return inspectResult;
        });

        List<TableView> result = service.listTables(1L, "demo_db", null, "sys_%", "TABLE", 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTableName()).isEqualTo("sys_user");
        assertThat(result.get(0).getRemarks()).isEqualTo("用户表");
    }

    @Test
    void inspectColumnsFailsWhenTableNamesEmpty() {
        assertThatThrownBy(() -> service.inspectColumns(1L, null, null, Collections.emptyList()))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能为空");
    }

    @Test
    void inspectColumnsFailsWhenTableNamesExceedMaxBatchSize() {
        List<String> toomany = Collections.nCopies(501, "table");
        assertThatThrownBy(() -> service.inspectColumns(1L, null, null, toomany))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过 500");
    }

    @Test
    void inspectColumnsReturnsTableSchemaViewsSuccessfully() {
        DataSourceInstance instance = activeInstance();
        when(instanceMapper.selectById(1L)).thenReturn(instance);
        when(connectorCatalog.require("mysql")).thenReturn(activeDescriptor());

        TableMeta tableMeta = TableMeta.builder()
            .catalog("demo_db").tableName("sys_user").tableType("TABLE").build();
        ColumnMeta columnMeta = ColumnMeta.builder()
            .columnName("user_id").dataType("BIGINT").jdbcType(4).primaryKey(true).build();
        TableSchema schema = TableSchema.builder()
            .table(tableMeta).columns(List.of(columnMeta)).build();

        MetadataInspectResult inspectResult = MetadataInspectResult.builder()
            .success(true).tableSchemas(List.of(schema)).build();

        when(inspectorRegistry.require("mysql", "1.0")).thenReturn(inspector);
        when(inspector.inspectColumns(any(), eq(List.of("sys_user")))).thenReturn(inspectResult);

        List<TableSchemaView> result = service.inspectColumns(1L, "demo_db", null, List.of("sys_user"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTable().getTableName()).isEqualTo("sys_user");
        assertThat(result.get(0).getColumns()).hasSize(1);
        assertThat(result.get(0).getColumns().get(0).getColumnName()).isEqualTo("user_id");
        assertThat(result.get(0).getColumns().get(0).isPrimaryKey()).isTrue();
    }

    @Test
    void discoverBuildsExplicitNamespaceHierarchyAndCarriesDatasourceResource() {
        DataSourceInstance instance = activeInstance();
        when(instanceMapper.selectById(1L)).thenReturn(instance);
        when(connectorCatalog.require("mysql")).thenReturn(activeDescriptor());
        when(inspectorRegistry.require("mysql", "1.0")).thenReturn(inspector);

        TableMeta table = TableMeta.builder().catalog("government").schema("public")
            .tableName("citizen").tableType("TABLE").build();
        ColumnMeta column = ColumnMeta.builder().columnName("citizen_id").dataType("BIGINT")
            .ordinalPosition(1).primaryKey(true).build();
        when(inspector.listTables(any())).thenReturn(MetadataInspectResult.builder()
            .success(true).tables(List.of(table)).total(1L).build());
        when(inspector.inspectColumns(any(), eq(List.of("citizen"))))
            .thenReturn(MetadataInspectResult.builder().success(true)
                .tableSchemas(List.of(TableSchema.builder().table(table).columns(List.of(column)).build())).build());

        DiscoveryCollector collector = new DiscoveryCollector();
        MetadataDiscoveryResult result = service.discover(MetadataDiscoveryRequest.builder()
            .harvestRunId(10L).datasourceId(1L).maxTableCount(100).build(), collector);

        assertThat(result.getDatasourceResourceId()).isEqualTo(101L);
        assertThat(collector.namespaces).hasSize(2);
        assertThat(collector.namespaces).anySatisfy(catalog -> {
            assertThat(catalog.getNamespaceType()).isEqualTo(MetadataObjectType.CATALOG);
            assertThat(catalog.getNamespaceName()).isEqualTo("government");
        });
        assertThat(collector.namespaces).anySatisfy(schema -> {
            assertThat(schema.getNamespaceType()).isEqualTo(MetadataObjectType.SCHEMA);
            assertThat(schema.getNamespaceName()).isEqualTo("public");
            assertThat(schema.getParentCanonicalKey()).contains("CATALOG");
        });
        assertThat(collector.tables).singleElement().satisfies(value -> {
            assertThat(value.getTableName()).isEqualTo("citizen");
            assertThat(value.getTableType()).isEqualTo("TABLE");
            assertThat(value.getColumns()).singleElement()
                .satisfies(field -> assertThat(field.getColumnName()).isEqualTo("citizen_id"));
        });
    }

    @Test
    void discoverInspectsSameNamedTablesWithinTheirOwnCatalog() {
        DataSourceInstance instance = activeInstance();
        when(instanceMapper.selectById(1L)).thenReturn(instance);
        when(connectorCatalog.require("mysql")).thenReturn(activeDescriptor());
        when(inspectorRegistry.require("mysql", "1.0")).thenReturn(inspector);
        TableMeta first = TableMeta.builder().catalog("catalog_a").tableName("config").tableType("TABLE").build();
        TableMeta second = TableMeta.builder().catalog("catalog_b").tableName("config").tableType("TABLE").build();
        when(inspector.listTables(any())).thenReturn(MetadataInspectResult.builder()
            .success(true).tables(List.of(first, second)).total(2L).build());
        when(inspector.inspectColumns(any(), any())).thenAnswer(invocation -> {
            com.xfwang.govdata.datasource.infrastructure.connector.model.MetadataInspectRequest request =
                invocation.getArgument(0);
            TableMeta table = TableMeta.builder().catalog(request.getCatalog()).tableName("config")
                .tableType("TABLE").build();
            return MetadataInspectResult.builder().success(true)
                .tableSchemas(List.of(TableSchema.builder().table(table).columns(List.of()).build())).build();
        });

        DiscoveryCollector collector = new DiscoveryCollector();
        service.discover(MetadataDiscoveryRequest.builder()
            .harvestRunId(10L).datasourceId(1L).build(), collector);

        assertThat(collector.namespaces).extracting(CollectedMetadataNamespace::getNamespaceName)
            .containsExactly("catalog_a", "catalog_b");
        assertThat(collector.tables).hasSize(2).allSatisfy(table ->
            assertThat(table.getTableName()).isEqualTo("config"));
        verify(inspector, times(2)).inspectColumns(any(), eq(List.of("config")));
    }

    private static final class DiscoveryCollector implements MetadataDiscoverySink {
        private final List<CollectedMetadataNamespace> namespaces = new ArrayList<>();
        private final List<CollectedMetadataTable> tables = new ArrayList<>();

        @Override
        public void acceptNamespaces(List<CollectedMetadataNamespace> values) {
            namespaces.addAll(values);
        }

        @Override
        public void acceptTables(List<CollectedMetadataTable> values) {
            tables.addAll(values);
        }
    }

    private DataSourceInstance activeInstance() {
        DataSourceInstance instance = new DataSourceInstance();
        instance.setDatasourceId(1L);
        instance.setTenantId("000000");
        instance.setAuthzResourceId(101L);
        instance.setOwnerDeptId(103L);
        instance.setDatasourceCode("ds_mysql");
        instance.setDatasourceName("MySQL 测试源");
        instance.setConnectorCode("mysql");
        instance.setConnectorVersion("1.0");
        instance.setStatus("ACTIVE");
        instance.setSecretRef("secret:sec_1");
        instance.setEndpointConfigJson("{\"connectionTarget\":\"127.0.0.1:3306/demo_db\"}");
        return instance;
    }

    private ConnectorDescriptor activeDescriptor() {
        return ConnectorDescriptor.builder()
            .connectorCode("mysql")
            .capabilities(Set.of("CONNECTION_TEST", "METADATA_INSPECT"))
            .build();
    }
}
