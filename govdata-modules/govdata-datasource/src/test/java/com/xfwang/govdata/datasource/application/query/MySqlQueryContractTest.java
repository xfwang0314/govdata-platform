package com.xfwang.govdata.datasource.application.query;

import com.xfwang.govdata.api.query.spi.*;
import com.xfwang.govdata.datasource.domain.entity.DataSourceInstance;
import com.xfwang.govdata.datasource.infrastructure.persistence.mapper.DataSourceInstanceMapper;
import com.xfwang.platform.api.auth.enums.DecisionType;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.*;
import com.xfwang.platform.api.auth.service.AuthorizationContextAccessor;
import com.xfwang.platform.api.secret.SecretProvider;
import com.xfwang.platform.api.secret.SecretValue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅在明确启用时创建随机临时库及仅 SELECT 账号，不修改业务库。 */
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "GOVDATA_MYSQL_QUERY_TEST", matches = "true")
class MySqlQueryContractTest {
    private final String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private final String database = "govdata_query_verify_" + suffix;
    private final String reader = "gq_" + suffix;
    private final String password = UUID.randomUUID().toString();
    private Connection admin;
    private boolean databaseCreated;
    private boolean userCreated;
    private QueryExecutionAdapter adapter;
    private DataSourceInstance source;

    @BeforeEach
    void prepare() throws Exception {
        admin = DriverManager.getConnection(System.getenv("GOVDATA_TEST_JDBC_URL"),
            System.getenv("GOVDATA_TEST_JDBC_USER"), System.getenv("GOVDATA_TEST_JDBC_PASSWORD"));
        assertThat(admin.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
        try (Statement sql = admin.createStatement()) {
            sql.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4");
            databaseCreated = true;
            sql.execute("CREATE USER '" + reader + "'@'%' IDENTIFIED BY '" + password + "'");
            userCreated = true;
            sql.execute("GRANT SELECT ON `" + database + "`.* TO '" + reader + "'@'%'");
            sql.execute("CREATE TABLE `" + database + "`.`记录` (record_id BIGINT PRIMARY KEY, `select` VARCHAR(100))");
            sql.execute("INSERT INTO `" + database + "`.`记录` (record_id, `select`) VALUES (1, '张三'), (2, '李四')");
        }
        source = new DataSourceInstance();
        source.setDatasourceId(2L);
        source.setTenantId("000000");
        source.setEnvironment("TEST");
        source.setConnectorCode("mysql");
        source.setConnectorVersion("1.0");
        source.setStatus("ACTIVE");
        source.setAccessMode("READ_ONLY");
        source.setLastTestStatus("SUCCESS");
        source.setLastTestTime(java.time.LocalDateTime.now());
        source.setSecretRef("secret:contract");
        source.setEndpointConfigJson("{\"host\":\"localhost\",\"port\":3306,\"database\":\"" + database + "\"}");
        var mapper = mock(DataSourceInstanceMapper.class);
        when(mapper.selectById(2L)).thenReturn(source);
        SecretProvider secrets = request -> {
            assertThat(request.getSecretRef()).isEqualTo("secret:contract");
            assertThat(request.getPurpose()).isEqualTo("DATASOURCE_QUERY");
            return Optional.of(new SecretValue(Map.of("username", reader.toCharArray(), "password", password.toCharArray())));
        };
        var context = new AuthorizationContextAccessor() {
            public Optional<AccessRequest> currentRequest() {
                return Optional.of(AccessRequest.builder().resourceId(1L).action("QUERY")
                    .subject(SubjectContext.builder().tenantId("000000").principalType(PrincipalType.HUMAN)
                        .principalId("1").userId(1L).build()).build());
            }
            public Optional<AccessDecision> currentDecision() {
                return Optional.of(AccessDecision.builder().decision(DecisionType.PERMIT).build());
            }
        };
        adapter = new MySqlQueryExecutionAdapter(mapper, List.of(secrets), context);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (admin == null) return;
        try (Connection cleanupConnection = admin; Statement sql = cleanupConnection.createStatement()) {
            if (userCreated) sql.execute("DROP USER '" + reader + "'@'%'");
            if (databaseCreated) sql.execute("DROP DATABASE `" + database + "`");
        }
    }

    @Test
    void productionRequiresVerifiedServerIdentity() {
        source.setEnvironment("PROD");
        assertThatThrownBy(this::returnsBoundedRowsFromRegisteredUnicodeTableUsingSecretRef)
            .hasFieldOrPropertyWithValue("errorCode", "GOV-DS-0503");
    }

    @Test
    void rejectsExcessConcurrentQueriesInsteadOfQueuingBehindASlowSource() throws Exception {
        var id = QueryFieldSchema.builder().fieldId(11L).physicalName("record_id").selectable(true).build();
        var plan = AuthorizedExecutionPlan.builder().resource(QueryResourceSchema.builder().resourceId(1L)
                .datasourceId(2L).catalogName(database).objectName("记录").fields(List.of(id)).build())
            .selectedFields(List.of(id)).offset(0).limit(1).timeoutMs(10000).build();
        var workers = java.util.concurrent.Executors.newFixedThreadPool(4);
        try (Statement sql = admin.createStatement()) {
            sql.execute("LOCK TABLES `" + database + "`.`记录` WRITE");
            try {
                for (int index = 0; index < 4; index++) workers.submit(() -> adapter.execute(plan));
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> {
                    try (var count = admin.prepareStatement("SELECT COUNT(1) FROM information_schema.processlist WHERE USER = ?")) {
                        count.setString(1, reader);
                        try (var rows = count.executeQuery()) { rows.next(); return rows.getInt(1) == 4; }
                    }
                });
                assertThatThrownBy(() -> adapter.execute(plan)).hasFieldOrPropertyWithValue("errorCode", "GOV-DS-0429-QUERY");
            } finally {
                sql.execute("UNLOCK TABLES");
            }
        } finally {
            workers.shutdown();
            assertThat(workers.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void cancelsQueriesBlockedByAnExternalTableLockWithinTheDeadline() throws Exception {
        var id = QueryFieldSchema.builder().fieldId(11L).physicalName("record_id").selectable(true).build();
        var plan = AuthorizedExecutionPlan.builder().resource(QueryResourceSchema.builder().resourceId(1L)
                .datasourceId(2L).catalogName(database).objectName("记录").fields(List.of(id)).build())
            .selectedFields(List.of(id)).offset(0).limit(1).timeoutMs(250).build();
        try (Statement sql = admin.createStatement()) {
            sql.execute("LOCK TABLES `" + database + "`.`记录` WRITE");
            long started = System.nanoTime();
            try {
                assertThatThrownBy(() -> adapter.execute(plan)).hasFieldOrPropertyWithValue("errorCode", "GOV-DS-0504-QUERY");
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(2));
            } finally {
                sql.execute("UNLOCK TABLES");
            }
        }
    }

    @Test
    void refusesAccountsWhoseActualGrantsIncludeWrites() throws Exception {
        try (Statement sql = admin.createStatement()) {
            sql.execute("GRANT INSERT ON `" + database + "`.* TO '" + reader + "'@'%'");
        }
        assertThatThrownBy(this::returnsBoundedRowsFromRegisteredUnicodeTableUsingSecretRef)
            .hasFieldOrPropertyWithValue("errorCode", "GOV-DS-0403-QUERY");
    }

    @Test
    void rejectsOversizedCellWithoutReturningPartialSensitiveData() throws Exception {
        try (Statement sql = admin.createStatement()) {
            sql.execute("ALTER TABLE `" + database + "`.`记录` ADD payload MEDIUMTEXT");
            sql.execute("UPDATE `" + database + "`.`记录` SET payload = REPEAT('x', 1100000) WHERE record_id = 1");
        }
        var id = QueryFieldSchema.builder().fieldId(11L).physicalName("record_id").selectable(true).build();
        var payload = QueryFieldSchema.builder().fieldId(13L).physicalName("payload").selectable(true).build();
        var plan = AuthorizedExecutionPlan.builder().resource(QueryResourceSchema.builder().resourceId(1L)
            .datasourceId(2L).catalogName(database).objectName("记录").fields(List.of(id, payload)).build())
            .selectedFields(List.of(payload)).offset(0).limit(1).timeoutMs(5000).build();
        assertThatThrownBy(() -> adapter.execute(plan)).hasFieldOrPropertyWithValue("errorCode", "GOV-DS-0413-QUERY");
    }

    @Test
    void treatsInjectionAsBoundDataAndCombinesFiltersWithAnd() {
        var field = QueryFieldSchema.builder().fieldId(11L).physicalName("record_id").selectable(true).build();
        var name = QueryFieldSchema.builder().fieldId(12L).physicalName("select").selectable(true).build();
        var plan = AuthorizedExecutionPlan.builder().resource(QueryResourceSchema.builder().resourceId(1L)
                .datasourceId(2L).catalogName(database).objectName("记录").fields(List.of(field, name)).build())
            .selectedFields(List.of(name)).offset(0).limit(10).timeoutMs(5000)
            .filters(List.of(com.xfwang.govdata.api.query.QueryFilter.builder().fieldId(12L)
                .operator(com.xfwang.govdata.api.query.QueryOperator.EQ).values(List.of("张三")).build())).build();
        assertThat(adapter.execute(plan).getRows()).containsExactly(Map.of(12L, "张三"));
        plan.getFilters().get(0).setValues(List.of("' OR 1=1 --"));
        assertThat(adapter.execute(plan).getRows()).isEmpty();
    }

    @Test
    void returnsBoundedRowsFromRegisteredUnicodeTableUsingSecretRef() {
        var id = QueryFieldSchema.builder().fieldId(11L).physicalName("record_id").selectable(true).build();
        var name = QueryFieldSchema.builder().fieldId(12L).physicalName("select").selectable(true).build();
        var schema = QueryResourceSchema.builder().resourceId(1L).datasourceId(2L).connectorCode("mysql")
            .catalogName(database).objectName("记录").fields(List.of(id, name)).build();
        var result = adapter.execute(AuthorizedExecutionPlan.builder().resource(schema).selectedFields(List.of(id, name))
            .offset(0).limit(1).timeoutMs(5000).build());
        assertThat(result.getRows()).containsExactly(Map.of(11L, 1L, 12L, "张三"));
        assertThat(result.getHasMore()).isTrue();
    }
}
