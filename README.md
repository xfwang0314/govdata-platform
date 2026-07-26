# 政务数据中台

政务数据中台（政务数据治理服务平台，英文标识 `govdata-platform`）基于 RuoYi-Vue-Plus 5.6.2 构建，面向政务数据汇聚、治理、共享和开放，负责元数据核心模型、JDBC/OpenMetadata 适配、数据标准、数据质量及 API 服务等模块的设计与实现。

项目仓库：<https://github.com/xfwang0314/govdata-platform>

## 技术栈

- JDK 17（可使用 JDK 21）
- Spring Boot 3.5.15
- Maven 多模块工程
- Sa-Token、MyBatis-Plus、Redisson
- MySQL / Oracle / PostgreSQL / SQL Server
- SnailJob、Warm-Flow、Spring Boot Admin

具体依赖版本以根目录 `pom.xml` 为准。

## 项目结构

```text
govdata-platform
├─ ruoyi-admin                 主应用与 Web 接口入口
├─ ruoyi-api                   框架通用 API 契约、DTO 与登录模型
├─ ruoyi-common                公共能力、自动配置与基础组件
│  └─ ruoyi-common-push        SSE 与 WebSocket 统一推送模块
├─ ruoyi-modules
│  ├─ ruoyi-system             用户、角色、菜单、租户等系统功能
│  ├─ ruoyi-generator          代码生成器
│  ├─ ruoyi-job                分布式任务调度客户端
│  ├─ ruoyi-workflow           工作流模块
│  └─ ruoyi-demo               框架能力示例
├─ govdata-api                 数据中台业务 API 契约
├─ govdata-modules             数据中台领域业务模块
├─ ruoyi-extend
│  ├─ ruoyi-monitor-admin      服务监控中心
│  └─ ruoyi-snailjob-server    SnailJob 服务端
└─ script                      数据库、Docker 和启停脚本
```

`org.dromara` 包名和 `ruoyi-*` 模块名用于保持上游兼容，本项目初始化阶段不对其进行重命名。

## 环境要求

本地开发至少需要：

- JDK 17
- Maven 3.8+
- MySQL 8.x（当前 Maven 配置默认只启用 MySQL 驱动）
- Redis 6+

开发配置默认启用 Spring Boot Admin 客户端和 SnailJob 客户端。若本地不启动对应服务，需要在个人环境配置中将 `spring.boot.admin.client.enabled` 和 `snail-job.enabled` 设为 `false`。对象存储、邮件、短信、第三方登录等组件按实际业务需要配置。

## 数据库初始化

MySQL 默认使用 `script/sql` 下的脚本：

1. 执行 `ry_vue_5.X.sql` 初始化基础表和系统数据。
2. 使用工作流功能时执行 `ry_workflow.sql`。
3. 使用 SnailJob 时执行 `ry_job.sql`，并同步检查任务服务配置。

Oracle、PostgreSQL、SQL Server 的适配脚本分别位于对应的子目录。切换到非 MySQL 数据库时，还需要在 `ruoyi-admin/pom.xml` 中启用对应 JDBC 驱动，并同步修改数据源配置。初始化前应确认字符集、时区、数据库账号权限及脚本版本，禁止直接在已有生产库中重复执行初始化脚本。

## 本地配置

主应用公共配置位于：

- `ruoyi-admin/src/main/resources/application.yml`
- `ruoyi-admin/src/main/resources/application-dev.yml`
- `ruoyi-admin/src/main/resources/application-prod.yml`

根 `pom.xml` 提供 `local`、`dev`、`prod` 三个 Maven Profile，当前默认激活 `dev`。仓库暂未提供 `application-local.yml`，因此不能直接将 `local` Profile 作为完整本地配置使用；如需启用，应先通过本地文件或外部配置源补齐数据源、Redis 等环境配置。启动前至少检查以下配置：

- 数据库连接地址、库名、账号和密码
- Redis 地址、端口、数据库编号和密码
- Spring Boot Admin、SnailJob 等扩展服务地址
- 文件存储、邮件、短信和第三方登录配置
- JWT、接口加密及其他密钥材料

仓库中的默认账号、密码和密钥仅供本地开发参考。生产环境必须通过安全配置中心、环境变量或密钥管理服务覆盖，禁止将真实敏感信息提交到仓库。

默认 `dev` 配置连接本机 `ry-vue` 数据库和 Redis。首次启动可对照 `application-dev.yml` 准备依赖服务；其中示例数据库账号及 Redis 密码只能用于隔离的本地开发环境。

## 构建与启动

项目通过 Maven Wrapper 锁定 Maven 3.9.9，并由 Enforcer 强制使用 JDK 17。不要依赖开发机全局安装的 Maven，在项目根目录执行：

```bash
./mvnw clean package -Pdev -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

Windows PowerShell 使用 `.\mvnw.cmd clean package -Pdev -DskipTests`。

也可以通过 IDE 运行 `org.dromara.DromaraApplication`。默认主服务端口为 `8080`，访问根路径可检查服务是否启动成功。

如需同时启动扩展服务，在完整打包后分别运行：

```bash
java -jar ruoyi-extend/ruoyi-monitor-admin/target/ruoyi-monitor-admin.jar
java -jar ruoyi-extend/ruoyi-snailjob-server/target/ruoyi-snailjob-server.jar
```

常用扩展端口：

| 服务 | 默认端口 | 说明 |
| --- | ---: | --- |
| 政务数据中台主服务 | 8080 | 后端 API 与认证入口 |
| 服务监控中心 | 9090 | Spring Boot Admin，默认上下文路径为 `/admin` |
| SnailJob 服务端 | 8800 | 默认上下文路径为 `/snail-job` |
| SnailJob 通信端口 | 17888 | 主应用任务客户端连接服务端 |

端口及上下文路径以各模块 `application*.yml` 的实际配置为准。

## Docker 部署

`script/docker` 保留的是上游基础依赖和服务编排示例，并非当前项目的开箱即用部署方案。编排中仍包含上游镜像名、固定 `/docker/...` 挂载路径及 host 网络模式；使用前必须替换为本项目构建的镜像，并根据目标环境调整镜像仓库、挂载目录、网络、数据库连接、Redis、监控账号和资源限制，不应直接沿用示例密码。

## 二次开发约定

- 项目初始化和品牌相关改动在 `feature/project-init` 分支维护。
- 新业务优先放入独立业务模块，避免直接侵入公共组件。
- 分页接口统一返回 `R<PageResult<T>>`，分页数据位于 `data.rows`，总数位于 `data.total`。
- API 响应保留兼容数值 `code`，同时返回稳定的 `errorCode` 和 `traceId`；客户端可通过响应头 `X-Trace-Id` 与服务端日志关联问题。
- BO/DTO 不继承持久化实体基类；实体类可按需继承 `BaseEntity`，时间字段统一使用 `LocalDateTime`。
- 数据权限通过 `@DataPermission` 声明数据列，接口必须使用 `@SaCheckPermission` 或 `@SaCheckRole` 标明访问约束，禁止在业务 SQL 中拼接当前用户权限。
- 数据中台模块依赖 `govdata-api` 获取业务契约，使用框架能力时仍应显式声明对应公共模块依赖。
- `ruoyi-api` 禁止依赖 `govdata-api`，`govdata-api` 禁止依赖业务实现，`govdata-exchange` 禁止直接依赖 `govdata-metadata`；这些边界由 Maven Enforcer 强制检查。
- 响应字段翻译和脱敏通过 JSON 增强处理器扩展，新增实现应优先提供批量准备能力，避免逐行查询。
- 本地和开发环境使用 MyBatis SQL 日志拦截器记录执行耗时及脱敏参数，生产环境不启用，禁止重新引入 P6Spy。
- 新生成代码默认使用 `com.xfwang.govdata` 包名，并遵循上述分页、时间类型和 BO 约定。
- 不进行无必要的包名、模块名和 Maven 坐标全局替换，以便持续合并上游修复。
- 新增配置应区分开发与生产环境，不在代码中硬编码敏感信息。
- 数据库结构变更应提供可重复审核的增量脚本，不修改已投产脚本的历史语义。
- 提交前至少完成编译、相关测试和配置差异检查。

## 上游项目与许可

本项目基于 [RuoYi-Vue-Plus](https://gitee.com/dromara/RuoYi-Vue-Plus) 二次开发，上游项目由 Dromara 社区及原作者维护。

项目继续遵循根目录 [MIT License](LICENSE)。分发或再发布时必须保留许可证文件及其中的原版权声明。
