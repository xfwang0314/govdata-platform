package com.xfwang.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 在随机临时库验证 MySQL 8 Baseline；未显式授权时绝不运行。 */
@Tag("local")
@SpringBootTest(classes = org.dromara.DromaraApplication.class)
@EnabledIfEnvironmentVariable(named = "GOVDATA_MYSQL_BASELINE_TEST", matches = "true")
class MySqlBaselineIntegrationTest {

    private static final List<String> BASELINES = List.of(
        "00_ruoyi.sql",
        "10_workflow.sql",
        "20_platform_auth.sql",
        "21_platform_secret.sql",
        "30_govdata_datasource.sql"
    );

    @Autowired
    private DataSource dataSource;

    @Test
    void baselineMustCreateExpectedSchemaInIsolatedDatabase() throws Exception {
        String database = "govdata_baseline_verify_" + ProcessHandle.current().pid();
        assertTrue(database.matches("[a-z0-9_]+"));
        Path baselineDirectory = findRepositoryRoot().resolve("script/sql/baseline");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            statement.execute("CREATE DATABASE `" + database
                + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            try {
                statement.execute("USE `" + database + "`");
                for (String baseline : BASELINES) {
                    Path script = baselineDirectory.resolve(baseline);
                    assertTrue(Files.isRegularFile(script), baseline + " 不存在");
                    ScriptUtils.executeSqlScript(connection, new FileSystemResource(script));
                }
                assertExpectedTables(connection, database);
                assertSystemRoleOwnership(connection, database);
                assertPolicyGroupLifecycleConstraints(connection);
            } finally {
                statement.execute("USE information_schema");
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
        }
    }

    private void assertSystemRoleOwnership(Connection connection, String database) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT is_nullable
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = 'sys_role'
              AND column_name = 'owner_dept_id'
            """)) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "sys_role.owner_dept_id 不存在");
                assertEquals("NO", resultSet.getString("is_nullable"));
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = ?
              AND table_name = 'sys_role'
              AND index_name = 'idx_sys_role_owner'
            """)) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.getInt(1) > 0, "sys_role 权属索引不存在");
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT COUNT(*) FROM sys_role WHERE owner_dept_id IS NULL")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
        }
    }

    private void assertPolicyGroupLifecycleConstraints(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO authz_policy_group
                    (policy_group_id, tenant_id, policy_group_code, status, current_version_id,
                     draft_version_id, next_version_no, creation_source, lock_version, del_flag,
                     create_time, update_time)
                VALUES
                    (900000000000000001, '000000', 'pgrp_baseline_check', 'DRAFT', NULL,
                     900000000000000002, 2, 'CUSTOM', 0, '0', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """);
            assertEquals(1, statement.executeUpdate("""
                UPDATE authz_policy_group
                SET status = 'ACTIVE',
                    current_version_id = draft_version_id,
                    draft_version_id = NULL
                WHERE policy_group_id = 900000000000000001
                """));
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                UPDATE authz_policy_group
                SET draft_version_id = current_version_id
                WHERE policy_group_id = 900000000000000001
                """));
            assertEquals(1, statement.executeUpdate("""
                UPDATE authz_policy_group
                SET del_flag = '1', deleted_time = CURRENT_TIMESTAMP(3), delete_reason = 'baseline check'
                WHERE policy_group_id = 900000000000000001
                """));
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("""
                UPDATE authz_policy_group
                SET del_flag = '0'
                WHERE policy_group_id = 900000000000000001
                """));
            assertEquals(1, statement.executeUpdate("""
                UPDATE authz_policy_group
                SET del_flag = '0', deleted_time = NULL, delete_reason = NULL
                WHERE policy_group_id = 900000000000000001
                """));
        }
    }

    private void assertExpectedTables(Connection connection, String database) throws Exception {
        List<String> expected = List.of("sys_user", "flow_definition", "authz_resource", "authz_policy_group",
            "authz_policy_group_version", "authz_policy_runtime_rule",
            "authz_grant", "platform_secret", "platform_secret_version", "gd_datasource",
            "gd_datasource_connection_test");
        String placeholders = String.join(",", expected.stream().map(value -> "?").toList());
        String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN ("
            + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            for (int index = 0; index < expected.size(); index++) {
                statement.setString(index + 2, expected.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(expected.size(), result.getInt(1));
            }
        }
    }

    private Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("AGENTS.md"))
                && Files.isDirectory(current.resolve("script/sql/baseline"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录");
    }
}
