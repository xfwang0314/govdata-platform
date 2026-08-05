package com.xfwang.architecture;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 自研 SQL 静态门禁；遗留文件仅作为迁移基线，新文件必须严格通过。 */
@Tags({@Tag("local"), @Tag("dev"), @Tag("prod")})
class SqlStandardsTest {

    private static final Pattern SELECT_STAR = Pattern.compile("(?is)\\bselect\\s+(?:[a-z0-9_]+\\s*\\.\\s*)?\\*");
    private static final Pattern FOREIGN_KEY = Pattern.compile(
        "(?is)\\b(?:constraint\\s+[a-z0-9_]+\\s+)?foreign\\s+key\\s*\\(|\\breferences\\s+[a-z0-9_]+");
    private static final Pattern ENUM_OR_SET = Pattern.compile("(?is)\\b(?:enum|set)\\s*\\(");
    private static final Pattern AUTO_INCREMENT = Pattern.compile("(?is)\\bauto_increment\\b");
    private static final Pattern INTEGER_DISPLAY_WIDTH = Pattern.compile(
        "(?is)\\b(?:smallint|mediumint|int|integer|bigint)\\s*\\(\\s*\\d+\\s*\\)"
            + "|\\btinyint\\s*\\(\\s*(?!1\\s*\\))\\d+\\s*\\)");
    private static final Pattern UNSAFE_MYBATIS_SUBSTITUTION = Pattern.compile("\\$\\{");

    /** 上游兼容基线只迁移目录，不以自研表规则重写。 */
    private static final Set<String> UPSTREAM_COMPATIBILITY_BASELINE = Set.of(
        "script/sql/baseline/00_ruoyi.sql",
        "script/sql/baseline/10_workflow.sql"
    );

    /**
     * 精确记录整改前已有的可执行 SQL。新增文件不得加入此集合；完成 Baseline 迁移后删除对应项。
     */
    private static final Set<String> LEGACY_SQL = Set.of(
        "script/sql/update/update_5.0-5.1.sql",
        "script/sql/update/update_5.1.0-5.1.1.sql",
        "script/sql/update/update_5.1.1-5.1.2.sql",
        "script/sql/update/update_5.1.2-5.2.0.sql",
        "script/sql/update/update_5.3.0-5.3.1.sql",
        "script/sql/update/update_5.3.1-5.4.0.sql",
        "script/sql/update/update_5.4.1-5.5.0.sql",
        "script/sql/update/update_5.5.0-5.5.1.sql",
        "script/sql/update/update_5.5.1-5.5.2.sql",
        "script/sql/update/update_5.5.3_5.6.0.sql",
        "script/sql/upgrade/20260801_001_remove_ruoyi_generator.sql",
        "script/sql/upgrade/20260802_002_remove_platform_auth_foreign_keys.sql",
        "script/sql/upgrade/20260803_001_create_govdata_datasource.sql",
        "script/sql/upgrade/20260803_002_remove_snailjob.sql",
        "script/sql/upgrade/20260803_003_extend_datasource_management.sql",
        "script/sql/upgrade/20260803_004_add_platform_auth_menus.sql"
    );

    private static final List<String> REQUIRED_UPGRADE_HEADERS = List.of(
        "-- module:",
        "-- depends-on:",
        "-- purpose:",
        "-- expected-rows:",
        "-- lock-risk:",
        "-- downtime-required:",
        "-- pre-check:",
        "-- post-check:",
        "-- rollback-or-forward-fix:"
    );

    private final Path repositoryRoot = findRepositoryRoot();

    @Test
    void newExecutableSqlMustUseCanonicalDirectories() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sqlFiles(repositoryRoot.resolve("script/sql"))) {
            String relative = relative(file);
            if (LEGACY_SQL.contains(relative)) {
                continue;
            }
            if (!relative.startsWith("script/sql/baseline/") && !relative.startsWith("script/sql/upgrade/")) {
                violations.add(relative + "：新增 SQL 只能进入 script/sql/baseline 或 script/sql/upgrade");
            }
        }
        for (Path file : sqlFiles(repositoryRoot.resolve("docs/database/ddl"))) {
            String relative = relative(file);
            if (!LEGACY_SQL.contains(relative)) {
                violations.add(relative + "：docs/database/ddl 禁止新增重复可执行 SQL");
            }
        }
        assertNoViolations(violations);
    }

    @Test
    void newBaselineAndUpgradeSqlMustPassStaticRules() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> candidates = new ArrayList<>();
        candidates.addAll(sqlFiles(repositoryRoot.resolve("script/sql/baseline")));
        candidates.addAll(sqlFiles(repositoryRoot.resolve("script/sql/upgrade")));
        for (Path file : candidates) {
            if (LEGACY_SQL.contains(relative(file)) || UPSTREAM_COMPATIBILITY_BASELINE.contains(relative(file))) {
                continue;
            }
            inspectSql(file, violations);
        }
        assertNoViolations(violations);
    }

    @Test
    void baselineManifestMustListEveryBaselineSql() throws IOException {
        Path baseline = repositoryRoot.resolve("script/sql/baseline");
        Path manifest = baseline.resolve("manifest.md");
        List<String> violations = new ArrayList<>();
        if (!Files.isRegularFile(manifest)) {
            violations.add("script/sql/baseline/manifest.md：基线清单不存在");
        } else {
            String content = Files.readString(manifest, StandardCharsets.UTF_8);
            for (Path file : sqlFiles(baseline)) {
                if (!content.contains(file.getFileName().toString())) {
                    violations.add(relative(file) + "：未登记到 baseline/manifest.md");
                }
            }
        }
        assertNoViolations(violations);
    }

    @Test
    void newUpgradeSqlMustContainReleaseMetadata() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sqlFiles(repositoryRoot.resolve("script/sql/upgrade"))) {
            String relative = relative(file);
            if (LEGACY_SQL.contains(relative)) {
                continue;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (String header : REQUIRED_UPGRADE_HEADERS) {
                if (!content.contains(header)) {
                    violations.add(relative + "：缺少升级头部 " + header);
                }
            }
        }
        assertNoViolations(violations);
    }

    @Test
    void selfDevelopedMappersMustNotUseUnsafeSubstitution() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String moduleRoot : List.of("platform-modules", "govdata-modules")) {
            Path root = repositoryRoot.resolve(moduleRoot);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                    .filter(path -> normalize(path).contains("/src/main/"))
                    .filter(path -> path.toString().endsWith(".xml") || path.toString().endsWith(".java"))
                    .forEach(path -> inspectUnsafeSubstitution(path, violations));
            }
        }
        assertNoViolations(violations);
    }

    private void inspectSql(Path file, List<String> violations) throws IOException {
        String relative = relative(file);
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        String sql = stripComments(raw);
        reject(sql, SELECT_STAR, relative, "禁止 SELECT *", violations);
        reject(sql, FOREIGN_KEY, relative, "禁止物理外键或 REFERENCES", violations);
        reject(sql, ENUM_OR_SET, relative, "禁止 MySQL ENUM/SET", violations);
        reject(sql, AUTO_INCREMENT, relative, "自研表禁止 AUTO_INCREMENT", violations);
        reject(sql, INTEGER_DISPLAY_WIDTH, relative, "禁止整数显示宽度", violations);

        for (String statement : sql.split(";")) {
            String normalized = statement.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if ((normalized.startsWith("update ") || normalized.startsWith("delete from "))
                && !normalized.contains(" where ")) {
                violations.add(relative + "：UPDATE/DELETE 缺少 WHERE");
            }
            if (normalized.startsWith("create table ")) {
                require(normalized, "engine", relative, "CREATE TABLE 缺少 ENGINE", violations);
                require(normalized, "charset", relative, "CREATE TABLE 缺少 CHARSET", violations);
                require(normalized, "collate", relative, "CREATE TABLE 缺少 COLLATE", violations);
                require(normalized, "comment", relative, "CREATE TABLE 缺少表 COMMENT", violations);
            }
        }
    }

    private void inspectUnsafeSubstitution(Path file, List<String> violations) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (UNSAFE_MYBATIS_SUBSTITUTION.matcher(content).find()) {
                violations.add(relative(file) + "：自研 Mapper/Java 禁止 ${} SQL 替换");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取失败: " + file, ex);
        }
    }

    private void reject(String sql, Pattern pattern, String file, String message, List<String> violations) {
        if (pattern.matcher(sql).find()) {
            violations.add(file + "：" + message);
        }
    }

    private void require(String sql, String token, String file, String message, List<String> violations) {
        if (!sql.contains(token)) {
            violations.add(file + "：" + message);
        }
    }

    private List<Path> sqlFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                .sorted()
                .toList();
        }
    }

    private String stripComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ")
            .replaceAll("(?m)--[^\\r\\n]*", " ");
    }

    private String relative(Path path) {
        return normalize(repositoryRoot.relativize(path));
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private void assertNoViolations(List<String> violations) {
        assertTrue(violations.isEmpty(), () -> "SQL 规范违规:\n" + String.join("\n", violations));
    }

    private Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("AGENTS.md"))
                && Files.isDirectory(current.resolve("script/sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位仓库根目录");
    }
}
