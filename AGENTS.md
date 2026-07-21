# AGENTS.md — jtools-runtime-environment

## 项目概述

JTools 插件：为 IntelliJ IDEA 中的 Java 运行配置注入运行时环境（JVM 参数、程序参数、环境变量）。
数据持久化在用户目录 `~/.jtools/jtools-runtime-environment/data.db`（SQLite）。

当前版本：`v5`（见 `build.gradle.kts`、`PluginImpl.pluginVersion()`）。

## 工程环境与主要工具

- 构建：Gradle（Wrapper），Kotlin DSL
- 语言：Java 17 + Kotlin（`jvmTarget` 17）
- 插件平台：`org.jetbrains.intellij.platform` 2.7.2，目标 IDE Community `2022.3`
- 打包：Shadow JAR（`jtools-runtime-environment`，无 classifier）
- 依赖：`sqlite-jdbc`、`HikariCP`、本地 `sdk.jar`（JTools SDK）
- 已移除：MyBatis-Plus（v5 起改为直接 JDBC）

## 目录与模块结构

- `src/main/java/com/lhstack/env/service/`：SQLite 持久化与领域模型
- `src/main/java/com/lhstack/env/dialog/`：环境列表/编辑对话框（Java）
- `src/main/kotlin/com/lhstack/env/`：插件入口、运行配置注入、Function Calling、UI
- `src/main/resources/META-INF/ToolsPlugin.txt`：插件入口类名
- `release/`：已发布 JAR
- 单模块 Gradle 项目，无多模块拆分

## 分层架构与依赖方向

1. **插件层**（`PluginImpl`、对话框、FunctionCalling）：UI 与 JTools SPI
2. **注入层**（`AttachJavaProgramPatcher`）：读取激活环境并改写 `JavaParameters`
3. **服务层**（`RuntimeEnvironmentService`）：连接池、事务、SQL CRUD
4. **模型层**（`RuntimeEnvironment`、`RuntimeEnvironmentActive`）：纯数据对象

依赖方向：插件/注入/FunctionCalling → Service → JDBC/SQLite。
Service 不依赖 UI。

## 构建、测试和验证方式

```bash
./gradlew shadowJar
# 产物：build/libs/jtools-runtime-environment-v5.jar
```

- `tasks.test` 配置了 JUnit Platform，但当前未见有意义的自动化测试覆盖。
- 本地验证：安装 JAR 到 JTools 后打开「运行时环境」面板；也可对 Service 做独立 JDBC 复现。

## 项目编码约定

- 包名：`com.lhstack.env`
- UI / IDE 扩展多为 Kotlin；持久化与部分对话框为 Java
- 数据库访问显式 SQL + `PreparedStatement`，不使用 ORM
- 时间字段以 `LocalDateTime.toString()` 写入，读取时用 `LocalDateTime.parse`（空格替换为 `T`）
- 模块标识使用 `module.toString()`（如 `Module: 'name'`），与 `module.name` 并存于 Function Calling 查找逻辑
- 链式 setter（`RuntimeEnvironment` / `RuntimeEnvironmentActive` 返回 `this`）

## 错误处理约定

- 初始化失败：`IllegalStateException("Failed to initialize RuntimeEnvironmentService: ...")`
- 业务 SQL 失败：`sqlError(...)` → `IllegalStateException(message, SQLException)`
- 会话级包装：`withConnection` 抛出 `IllegalStateException("Runtime environment database operation failed: ...", cause)`，失败时 rollback
- 未初始化时 `getService`/`execute` 返回 `null`（调用方需处理）
- `unInstall` 中对 destroy/uninstall 的异常有空 catch（有证据）

## 版本控制信息

- Git，主开发分支证据：`v5` / 历史 `master`
- 作者提交身份（git config / log）：`lhstack` / `lhstack@foxmail.com`

## 有证据支持的用户编码习惯

- 提交信息多为中文短描述（如「优化」「升级，移除 mybatis-plus」）
- 偏好显式 SQL、最小依赖；曾主动移除 MyBatis-Plus
- Service 用静态生命周期（`init`/`destroy`）+ 实例方法操作当前 `Connection`
- UI 侧使用 `Alarm` 防抖写库、`AtomicBoolean` 避免 document 回写

## 当前无法确认的事项

- JTools 宿主对 `install()` / `createPanel()` 的精确调用时序与类加载隔离细节
- `sdk.jar` 接口完整契约（仅见项目内引用）
- CI/发布流水线是否存在
- Windows 路径下 `build.gradle.kts` 中 sdk 路径（`/Users/lhstack/...`）是否始终可用
