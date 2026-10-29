package com.xfwang.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xfwang.govdata.query.application.tool.QueryMcpToolContributor;
import com.xfwang.platform.api.auth.enums.PrincipalType;
import com.xfwang.platform.api.auth.model.SubjectContext;
import com.xfwang.platform.api.auth.service.SubjectProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 真实 MySQL 控制库和外部库；仅替换用户身份边界，凭据、Agent Token、授权与执行均为生产实现。 */
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "GOVDATA_MYSQL_QUERY_TEST", matches = "true")
@SpringBootTest(classes = org.dromara.DromaraApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MySqlQueryAuthorizationE2ETest {
    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private static final String CONTROL = "govdata_query_e2e_" + SUFFIX;
    private static final String EXTERNAL = "govdata_query_data_" + SUFFIX;
    private static final String READER = "gqe_" + SUFFIX;
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static boolean controlCreated;
    private static boolean externalCreated;
    private static boolean readerCreated;
    private static String controlUrl;

    @Autowired private QueryMcpToolContributor tools;
    @Autowired private com.xfwang.platform.mcp.application.service.McpToolInvocationService invocationService;
    @Autowired private com.xfwang.platform.mcp.infrastructure.security.McpSubjectContextHolder mcpSubjects;
    @Autowired private com.xfwang.platform.mcp.domain.service.AgentTokenGenerator tokenGenerator;
    @MockitoBean private SubjectProvider subjects;
    @Autowired private com.xfwang.platform.secret.application.management.SecretManagementService secrets;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        String url = System.getenv("GOVDATA_TEST_JDBC_URL");
        if (url == null || !url.matches("^jdbc:mysql://(localhost|127\\.0\\.0\\.1):3306/.*"))
            throw new IllegalStateException("端到端测试仅允许显式配置的本机 MySQL 3306");
        controlUrl = url.replaceFirst("(jdbc:mysql://[^/]+/)[^?]+", "$1" + CONTROL);
        try (Connection connection = admin(); Statement sql = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(8);
            sql.execute("CREATE DATABASE `" + CONTROL + "` CHARACTER SET utf8mb4");
            controlCreated = true;
            sql.execute("USE `" + CONTROL + "`");
            for (String name : List.of("00_ruoyi.sql", "10_workflow.sql", "20_platform_auth.sql", "21_platform_secret.sql",
                "30_govdata_datasource.sql", "31_govdata_metadata.sql", "32_govdata_metadata_harvest.sql", "33_govdata_query.sql")) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root().resolve("script/sql/baseline/" + name)));
            }
            sql.execute("CREATE DATABASE `" + EXTERNAL + "` CHARACTER SET utf8mb4");
            externalCreated = true;
            sql.execute("CREATE USER '" + READER + "'@'%' IDENTIFIED BY '" + PASSWORD + "'");
            readerCreated = true;
            sql.execute("GRANT SELECT ON `" + EXTERNAL + "`.* TO '" + READER + "'@'%'");
            sql.execute("CREATE TABLE `" + EXTERNAL + "`.people (record_id BIGINT PRIMARY KEY, phone VARCHAR(20), region VARCHAR(20))");
            sql.execute("INSERT INTO `" + EXTERNAL + "`.people (record_id, phone, region) VALUES (1, '13812345678', 'north'), (2, '13987654321', 'south')");
        } catch (Exception failure) {
            cleanup();
            throw failure;
        }
        properties.add("spring.datasource.dynamic.datasource.master.url", () -> controlUrl);
        properties.add("spring.datasource.dynamic.datasource.master.username", () -> System.getenv("GOVDATA_TEST_JDBC_USER"));
        properties.add("spring.datasource.dynamic.datasource.master.password", () -> System.getenv("GOVDATA_TEST_JDBC_PASSWORD"));
        properties.add("platform.secret.encryption.master-key", () -> java.util.Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void mcpEnforcesGrantRowsMaskingExpiryAndTenantBoundaryWithDurableAudit() throws Exception {
        org.dromara.common.tenant.helper.TenantHelper.setDynamic("000000");
        var subject = SubjectContext.builder().tenantId("000000").principalType(PrincipalType.HUMAN)
            .principalId("9001").userId(9001L).deptId(103L).attributes(Map.of("region", "north")).build();
        when(subjects.currentSubject()).thenAnswer(ignored -> mcpSubjects.currentSubject().orElse(subject));
        when(subjects.subjectForUser("000000", 9001L)).thenReturn(subject);
        try (Connection connection = control(); Statement sql = connection.createStatement()) {
            fixtures(sql);
            var arguments = Map.<String, Object>of("datasource_id", "7001", "resource_id", "7101",
                "field_ids", List.of("7302"));
            assertThat(tools.tools()).hasSize(2);
            assertThat(tools.execute("datasource.query_dataset", arguments, subject).isError()).isTrue();
            sql.execute("INSERT INTO authz_grant (grant_id, tenant_id, grant_code, source_type, source_id, grantor_type, grantor_id, grantee_type, grantee_id, purpose, effective_time, expire_time, status) VALUES (7401, '000000', 'grt_e2e', 'ADMIN', 'e2e', 'HUMAN', '1', 'HUMAN', '9001', 'DATA_QUERY', DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_ADD(NOW(3), INTERVAL 1 DAY), 'ACTIVE')");
            String obligation = new ObjectMapper().writeValueAsString(Map.of("allowedColumns", List.of("mda_7302"),
                "rowFilters", List.of(Map.of("attribute", "field.mda_7303", "operator", "EQ", "values", List.of("subject.region"))),
                "maskingRules", Map.of("mda_7302", "MOBILE"), "maxRows", 1));
            try (var insert = connection.prepareStatement("INSERT INTO authz_grant_scope (scope_id, tenant_id, grant_id, resource_id, action_code, obligation_json) VALUES (7402, '000000', 7401, 7101, 'QUERY', ?)")) {
                insert.setString(1, obligation);
                insert.executeUpdate();
            }
            var response = tools.execute("datasource.query_dataset", arguments, subject);
            assertThat(response.isError()).isFalse();
            assertThat(response.getContent().get(0).getText()).contains("138****5678").doesNotContain("13812345678", "13987654321");
            assertThat(tools.execute("datasource.sample_dataset_records", arguments, subject).isError()).isFalse();
            var token = tokenGenerator.generate();
            long tokenId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
            try (var insert = connection.prepareStatement("INSERT INTO authz_agent_token (token_id, tenant_id, agent_name, agent_code, token_hash, token_prefix, user_id, dept_id, allowed_categories) VALUES (?, '000000', '测试Agent', 'agt_e2e', ?, ?, 9001, 103, 'DATASOURCE')")) {
                insert.setLong(1, tokenId);
                insert.setString(2, token.tokenHash());
                insert.setString(3, token.tokenPrefix());
                insert.executeUpdate();
            }
            var agentResponse = invocationService.invoke("Bearer " + token.plainToken(), "datasource.query_dataset", arguments);
            assertThat(agentResponse.isError()).isFalse();
            assertThat(agentResponse.getContent().get(0).getText()).contains("138****5678").doesNotContain("13812345678");
            // 调用入口清理动态租户后，测试恢复下一次 HUMAN 请求的租户上下文。
            org.dromara.common.tenant.helper.TenantHelper.setDynamic("000000");
            sql.execute("UPDATE gd_metadata_asset SET technical_status = 'DISABLED' WHERE tenant_id = '000000' AND asset_id = 7201");
            assertThat(tools.execute("datasource.query_dataset", arguments, subject).isError()).isTrue();
            sql.execute("UPDATE gd_metadata_asset SET technical_status = 'ACTIVE' WHERE tenant_id = '000000' AND asset_id = 7201");
            assertThat(tools.execute("datasource.query_dataset", Map.of("datasource_id", "7001", "resource_id", "7101",
                "field_ids", List.of("7303")), subject).isError()).isTrue();
            sql.execute("INSERT INTO authz_policy_runtime_rule (runtime_rule_id, tenant_id, policy_group_id, policy_group_version_id, rule_code, effect, priority, resource_type, resource_scope, action_codes_json, purpose_codes_json, condition_json) VALUES (7501, '000000', 7502, 7503, 'deny_query', 'DENY', 999, 'govdata:metadata:table', 'TYPE', '[\"QUERY\"]', '[\"DATA_QUERY\"]', '{}')");
            sql.execute("INSERT INTO authz_policy_runtime_subject (runtime_subject_id, tenant_id, policy_group_id, policy_group_version_id, subject_type, subject_id) VALUES (7504, '000000', 7502, 7503, 'ANY', '*')");
            assertThat(tools.execute("datasource.query_dataset", arguments, subject).isError()).isTrue();
            sql.execute("UPDATE authz_policy_runtime_rule SET action_codes_json = '[\"VIEW\"]' WHERE tenant_id = '000000' AND runtime_rule_id = 7501");
            sql.execute("UPDATE authz_grant SET expire_time = DATE_SUB(NOW(3), INTERVAL 1 HOUR) WHERE tenant_id = '000000' AND grant_id = 7401");
            assertThat(tools.execute("datasource.query_dataset", arguments, subject).isError()).isTrue();
            subject.setTenantId("other");
            org.dromara.common.tenant.helper.TenantHelper.setDynamic("other");
            assertThat(tools.execute("datasource.query_dataset", arguments, subject).isError()).isTrue();
            try (var logs = sql.executeQuery("SELECT result, COUNT(1) AS event_count FROM gd_query_access_log GROUP BY result")) {
                Map<String, Long> counts = new java.util.HashMap<>();
                while (logs.next()) counts.put(logs.getString(1), logs.getLong(2));
                assertThat(counts).containsEntry("STARTED", 9L).containsEntry("SUCCEEDED", 3L)
                    .containsEntry("DENIED", 5L).containsEntry("FAILED", 1L);
            }
            try (var logs = sql.executeQuery("SELECT COUNT(1) FROM gd_query_access_log WHERE tenant_id = '000000' AND actor_type = 'AGENT' AND actor_id = 'agt_e2e' AND result = 'SUCCEEDED'")) {
                assertThat(logs.next()).isTrue();
                assertThat(logs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void upgradeAndBaselineProduceIdenticalAuditSchema() throws Exception {
        String upgrade = "govdata_query_upgrade_" + SUFFIX;
        try (Connection connection = admin(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE `" + upgrade + "` CHARACTER SET utf8mb4");
            try {
                sql.execute("USE `" + upgrade + "`");
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(root().resolve(
                    "script/sql/upgrade/20260906_001_create_query_access_log.sql")));
                String actual;
                try (var result = sql.executeQuery("SHOW CREATE TABLE gd_query_access_log")) {
                    assertThat(result.next()).isTrue();
                    actual = result.getString(2);
                }
                try (var result = sql.executeQuery("SHOW CREATE TABLE `" + CONTROL + "`.gd_query_access_log")) {
                    assertThat(result.next()).isTrue();
                    assertThat(actual).isEqualTo(result.getString(2));
                }
                try (var result = sql.executeQuery("EXPLAIN ANALYZE SELECT access_log_id FROM gd_query_access_log WHERE tenant_id = '000000' AND occurred_time >= '2026-01-01' ORDER BY occurred_time, access_log_id LIMIT 20")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).contains("idx_gd_query_access_time");
                }
            } finally {
                sql.execute("USE information_schema");
                sql.execute("DROP DATABASE `" + upgrade + "`");
            }
        }
    }

    @org.junit.jupiter.api.AfterEach
    void clearTenant() { org.dromara.common.tenant.helper.TenantHelper.clearDynamic(); }

    private void fixtures(Statement sql) throws Exception {
        sql.execute("INSERT INTO authz_resource (resource_id, tenant_id, resource_code, resource_name, resource_type, source_module, business_id, status) VALUES (7101, '000000', 'res_e2e', '测试表', 'govdata:metadata:table', 'govdata-metadata', '7201', 'ACTIVE')");
        String endpoint = new ObjectMapper().writeValueAsString(Map.of("host", "localhost", "port", 3306, "database", EXTERNAL));
        var command = new com.xfwang.platform.secret.application.management.command.SecretCreateCommand();
        command.setSecretName("query-e2e");
        command.setSecretType("USERNAME_PASSWORD");
        command.setOwnerModule("govdata-datasource");
        command.setPurposeScope("DATASOURCE_QUERY");
        command.setValues(Map.of("username", READER, "password", PASSWORD));
        String secretRef = secrets.create(command).getSecretRef();
        try (var insert = sql.getConnection().prepareStatement("INSERT INTO gd_datasource (datasource_id, tenant_id, datasource_code, datasource_name, connector_code, connector_version, auth_profile_code, endpoint_config_json, secret_ref, environment, owner_dept_id, status, last_test_status, last_test_time) VALUES (7001, '000000', 'ds_e2e', '测试源', 'mysql', '1.0', 'PASSWORD', ?, ?, 'TEST', 103, 'ACTIVE', 'SUCCESS', NOW(3))")) {
            insert.setString(1, endpoint);
            insert.setString(2, secretRef);
            insert.executeUpdate();
        }
        asset(sql, 7200, null, "CATALOG", EXTERNAL, null);
        asset(sql, 7201, 7200L, "TABLE", "people", 7101L);
        List<String> columns = List.of("record_id", "phone", "region");
        for (int index = 0; index < columns.size(); index++) {
            long id = 7301 + index;
            asset(sql, id, 7201L, "COLUMN", columns.get(index), null);
            sql.execute("INSERT INTO gd_metadata_column (asset_id, tenant_id, table_asset_id, revision_no, ordinal_position, data_type, native_type, nullable_flag, structure_hash, content_hash, first_seen_time, last_seen_time, last_harvest_run_id) VALUES (" + id + ", '000000', 7201, 1, " + (index + 1) + ", 'VARCHAR', 'VARCHAR', 1, REPEAT('a', 64), REPEAT('b', 64), NOW(3), NOW(3), 1)");
        }
    }

    private void asset(Statement sql, long id, Long parent, String type, String name, Long resource) throws Exception {
        try (var insert = sql.getConnection().prepareStatement("INSERT INTO gd_metadata_asset (asset_id, tenant_id, authz_resource_id, asset_code, asset_type, parent_asset_id, datasource_id, connector_code, connector_version, canonical_key, original_name, owner_dept_id, first_seen_time, last_seen_time, last_harvest_run_id, structure_hash, content_hash, current_revision_no) VALUES (?, '000000', ?, ?, ?, ?, 7001, 'mysql', '1.0', ?, ?, 103, NOW(3), NOW(3), 1, REPEAT('a', 64), REPEAT('b', 64), 1)")) {
            insert.setLong(1, id);
            insert.setObject(2, resource);
            insert.setString(3, "mda_" + id);
            insert.setString(4, type);
            insert.setObject(5, parent);
            insert.setString(6, "test/" + id);
            insert.setString(7, name);
            insert.executeUpdate();
        }
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(System.getenv("GOVDATA_TEST_JDBC_URL"), System.getenv("GOVDATA_TEST_JDBC_USER"), System.getenv("GOVDATA_TEST_JDBC_PASSWORD"));
    }
    private static Connection control() throws Exception {
        return DriverManager.getConnection(controlUrl, System.getenv("GOVDATA_TEST_JDBC_USER"), System.getenv("GOVDATA_TEST_JDBC_PASSWORD"));
    }
    private static Path root() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("script/sql/baseline"))) current = current.getParent();
        if (current == null) throw new IllegalStateException("找不到Baseline目录");
        return current;
    }
    @AfterAll
    static void cleanup() throws Exception {
        try (Connection connection = admin(); Statement sql = connection.createStatement()) {
            try {
                if (readerCreated) { sql.execute("DROP USER '" + READER + "'@'%'"); readerCreated = false; }
            } finally {
                try {
                    if (externalCreated) { sql.execute("DROP DATABASE `" + EXTERNAL + "`"); externalCreated = false; }
                } finally {
                    if (controlCreated) { sql.execute("DROP DATABASE `" + CONTROL + "`"); controlCreated = false; }
                }
            }
        }
    }
}
