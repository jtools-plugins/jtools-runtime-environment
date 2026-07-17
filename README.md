# 运行时环境插件

版本：v4

IntelliJ IDEA 插件，用于管理 Java 项目的运行时环境变量、JVM 参数和程序参数。

## 功能

- 为不同模块配置独立的运行环境（dev/test/prod）
- 支持全局环境配置，所有项目共享
- 自动注入 JVM 参数、程序参数、环境变量
- 启动时在日志面板输出完整的启动参数

## 使用方法

1. 打开插件面板「运行时环境」
2. 选择模块和环境配置
3. 编辑 JVM 参数、程序参数、环境变量
4. 点击「开启」启用当前环境
5. 运行项目，参数会自动注入

## 参数格式

**JVM 参数**：每行一个
```
-Xmx512m
-Dspring.profiles.active=dev
```

**程序参数**：key=value 格式，每行一个
```
server.port=8080
debug=true
```

**环境变量**：key=value 格式，每行一个
```
JAVA_HOME=/path/to/java
APP_ENV=dev
```

## 启动日志

项目启动时会在插件日志面板输出完整参数：

```
=== 启动参数 [模块名] ===
JVM参数: 
-Xmx512m
-Dspring.profiles.active=dev
程序参数: 
server.port=8080
环境变量: 
APP_ENV=dev
==============================
```

## Function Calling（v4）

提供以下函数调用接口（返回 JSON 字符串）：

- `runtime_env.list_modules`：列出当前项目模块
- `runtime_env.list_environments`：列出指定模块环境
- `runtime_env.get_active_environment`：获取模块当前激活环境
- `runtime_env.set_active_environment`：设置模块激活环境并启用/禁用
- `runtime_env.update_environment`：创建或更新环境
- `runtime_env.delete_environment`：删除环境（默认环境不可删除）
- `runtime_env.get_global_environment`：获取全局环境与启用状态
- `runtime_env.update_global_environment`：更新全局环境（字段可选）

## 构建

```bash
./gradlew shadowJar

# 安装到 JTools 的插件 JAR：
# build/libs/jtools-runtime-environment-v4.jar
```
