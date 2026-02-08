package com.lhstack.env

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.lhstack.env.service.RuntimeEnvironment
import com.lhstack.env.service.RuntimeEnvironmentService
import com.lhstack.tools.plugins.FunctionCalling
import java.io.StringReader
import java.util.Properties

object FunctionCallingRegistry {
    fun functionCallings(project: Project?): List<FunctionCalling> {
        return listOf(
            SimpleFunctionCalling(
                "runtime_env.list_modules",
                "列出当前项目的模块列表。",
                """{"type":"object","properties":{"mainOnly":{"type":"boolean","description":"Only modules with source roots. Default true."}}}"""
            ) { input ->
                withProject(project) { prj ->
                    val args = parseArgs(input)
                    val mainOnly = args["mainOnly"]?.toBooleanStrictOrNull() ?: true
                    val modules = ModuleManager.getInstance(prj).modules
                        .filter { !mainOnly || it.isMainModule() }
                    val items = modules.joinToString(",") { module ->
                        """{"name":${jsonString(module.name)},"id":${jsonString(module.toString())},"isMain":${module.isMainModule()}}"""
                    }
                    ok("""{"modules":[$items]}""")
                }
            },
            SimpleFunctionCalling(
                "runtime_env.list_environments",
                "列出指定模块的运行环境配置。",
                """{"type":"object","properties":{"module":{"type":"string","description":"Module name or module toString()."}},"required":["module"]}"""
            ) { input ->
                withProject(project) { prj ->
                    val args = parseArgs(input)
                    val module = findModule(prj, args["module"]) ?: return@withProject error("module not found")
                    val environments = RuntimeEnvironmentService.execute { service ->
                        service.getRuntimeEnvironments(prj, module)
                    } ?: return@withProject error("runtime environment service is not initialized")
                    val items = environments.joinToString(",") { env ->
                        envToJson(env)
                    }
                    ok("""{"environments":[$items]}""")
                }
            },
            SimpleFunctionCalling(
                "runtime_env.get_active_environment",
                "获取模块当前激活的环境。",
                """{"type":"object","properties":{"module":{"type":"string","description":"Module name or module toString()."}},"required":["module"]}"""
            ) { input ->
                withProject(project) { prj ->
                    val args = parseArgs(input)
                    val module = findModule(prj, args["module"]) ?: return@withProject error("module not found")
                    val result = RuntimeEnvironmentService.execute { service ->
                        val enabled = service.isActive(prj, module)
                        val envId = service.getSelectEnvId(prj, module)
                        val env = if (envId != null) service.getById(envId) else null
                        """{"enabled":$enabled,"envId":${envId ?: "null"},"environment":${env?.let { envToJson(it) } ?: "null"}}"""
                    } ?: return@withProject error("runtime environment service is not initialized")
                    ok(result)
                }
            },
            SimpleFunctionCalling(
                "runtime_env.set_active_environment",
                "设置模块激活环境并启用/禁用。",
                """{"type":"object","properties":{"module":{"type":"string","description":"Module name or module toString()."},"envId":{"type":"integer"},"enabled":{"type":"boolean","description":"Default true."}},"required":["module","envId"]}"""
            ) { input ->
                withProject(project) { prj ->
                    val args = parseArgs(input)
                    val module = findModule(prj, args["module"]) ?: return@withProject error("module not found")
                    val envId = args["envId"]?.toIntOrNull() ?: return@withProject error("envId is required")
                    val enabled = args["enabled"]?.toBooleanStrictOrNull() ?: true
                    val result = RuntimeEnvironmentService.execute { service ->
                        val env = service.getById(envId) ?: return@execute error("environment not found")
                        if (env.projectHash != prj.locationHash || env.module != module.toString()) {
                            return@execute error("environment does not belong to module")
                        }
                        service.updateSelectEnv(envId)
                        service.updateActive(env, enabled)
                        ok("""{"enabled":$enabled,"envId":$envId}""")
                    } ?: return@withProject error("runtime environment service is not initialized")
                    result
                }
            },
            SimpleFunctionCalling(
                "runtime_env.update_environment",
                "创建或更新环境。传入 envId 则更新，否则新建。",
                """{"type":"object","properties":{"module":{"type":"string","description":"Module name or module toString()."},"envId":{"type":"integer"},"name":{"type":"string"},"remark":{"type":"string"},"argsValue":{"type":"string"},"envValue":{"type":"string"},"vmValue":{"type":"string"}}}"""
            ) { input ->
                withProject(project) { prj ->
                    val args = parseArgs(input)
                    val envId = args["envId"]?.toIntOrNull()
                    val moduleArg = args["module"]
                    val name = args["name"]
                    val remark = args["remark"]
                    val argsValue = args["argsValue"]
                    val envValue = args["envValue"]
                    val vmValue = args["vmValue"]
                    val result = RuntimeEnvironmentService.execute { service ->
                        if (envId != null) {
                            val env = service.getById(envId) ?: return@execute error("environment not found")
                            if (moduleArg != null) {
                                val module = findModule(prj, moduleArg) ?: return@execute error("module not found")
                                if (env.projectHash != prj.locationHash || env.module != module.toString()) {
                                    return@execute error("environment does not belong to module")
                                }
                            }
                            name?.let { env.setName(it) }
                            remark?.let { env.setRemark(it) }
                            argsValue?.let { env.setArgsValue(it) }
                            envValue?.let { env.setEnvValue(it) }
                            vmValue?.let { env.setVmValue(it) }
                            service.updateById(env)
                            ok(envToJson(env))
                        } else {
                            val module = findModule(prj, moduleArg) ?: return@execute error("module not found")
                            val envName = name?.takeIf { it.isNotBlank() }
                                ?: return@execute error("name is required when creating environment")
                            val env = RuntimeEnvironment()
                                .setProjectHash(prj.locationHash)
                                .setProjectName(prj.name)
                                .setProjectPath(prj.basePath)
                                .setModule(module.toString())
                                .setIsDefault(0)
                                .setName(envName)
                            remark?.let { env.setRemark(it) }
                            argsValue?.let { env.setArgsValue(it) }
                            envValue?.let { env.setEnvValue(it) }
                            vmValue?.let { env.setVmValue(it) }
                            service.save(env)
                            ok(envToJson(env))
                        }
                    } ?: return@withProject error("runtime environment service is not initialized")
                    result
                }
            },
            SimpleFunctionCalling(
                "runtime_env.delete_environment",
                "根据 envId 删除环境，默认环境不可删除。",
                """{"type":"object","properties":{"envId":{"type":"integer"}},"required":["envId"]}"""
            ) { input ->
                withProject(project) { _ ->
                    val args = parseArgs(input)
                    val envId = args["envId"]?.toIntOrNull() ?: return@withProject error("envId is required")
                    val result = RuntimeEnvironmentService.execute { service ->
                        val env = service.getById(envId) ?: return@execute error("environment not found")
                        if (env.isDefault == 1) {
                            return@execute error("default environment cannot be deleted")
                        }
                        service.removeById(envId)
                        ok("""{"deleted":true,"envId":$envId}""")
                    } ?: return@withProject error("runtime environment service is not initialized")
                    result
                }
            },
            SimpleFunctionCalling(
                "runtime_env.get_global_environment",
                "获取全局环境及其启用状态。",
                """{"type":"object","properties":{}}"""
            ) { _ ->
                val result = RuntimeEnvironmentService.execute { service ->
                    val env = service.globalEnvironment
                    val enabled = env.isDefault == 1
                    ok("""{"enabled":$enabled,"environment":${envToJson(env)}}""")
                } ?: error("runtime environment service is not initialized")
                result
            },
            SimpleFunctionCalling(
                "runtime_env.update_global_environment",
                "更新全局环境（字段可选）。",
                """{"type":"object","properties":{"argsValue":{"type":"string"},"envValue":{"type":"string"},"vmValue":{"type":"string"},"enabled":{"type":"boolean"}}}"""
            ) { input ->
                val args = parseArgs(input)
                val result = RuntimeEnvironmentService.execute { service ->
                    val env = service.globalEnvironment
                    args["argsValue"]?.let { env.setArgsValue(it) }
                    args["envValue"]?.let { env.setEnvValue(it) }
                    args["vmValue"]?.let { env.setVmValue(it) }
                    args["enabled"]?.toBooleanStrictOrNull()?.let { env.setIsDefault(if (it) 1 else 0) }
                    service.updateById(env)
                    ok(envToJson(env))
                } ?: error("runtime environment service is not initialized")
                result
            }
        )
    }

    private class SimpleFunctionCalling(
        private val functionName: String,
        private val functionDescription: String,
        private val functionParameters: String,
        private val handler: (String) -> String
    ) : FunctionCalling {
        override fun name(): String = functionName
        override fun description(): String = functionDescription
        override fun parameters(): String = functionParameters
        override fun call(input: String): String = handler(input)
    }

    private fun withProject(project: Project?, block: (Project) -> String): String {
        if (project == null) {
            return error("project is required")
        }
        return block(project)
    }

    private fun findModule(project: Project, moduleArg: String?): Module? {
        val input = moduleArg?.trim().orEmpty()
        val modules = ModuleManager.getInstance(project).modules
        if (input.isBlank()) {
            return modules.firstOrNull { it.isMainModule() } ?: modules.firstOrNull()
        }
        return modules.firstOrNull { it.name == input } ?: modules.firstOrNull { it.toString() == input }
    }

    private fun parseArgs(input: String?): Map<String, String> {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) {
            return emptyMap()
        }
        if (raw.startsWith("{") && raw.endsWith("}")) {
            try {
                return parseJsonObject(raw)
            } catch (_: Throwable) {
                // Fall through to properties parsing.
            }
        }
        val props = Properties()
        props.load(StringReader(raw))
        val result = mutableMapOf<String, String>()
        for (name in props.stringPropertyNames()) {
            result[name] = props.getProperty(name)
        }
        return result
    }

    private fun parseJsonObject(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 1
        val end = raw.length - 1
        while (i < end) {
            while (i < end && (raw[i].isWhitespace() || raw[i] == ',')) i++
            if (i >= end) break
            if (raw[i] != '"') {
                throw IllegalArgumentException("invalid json key")
            }
            i++
            val keyStart = i
            var escaped = false
            val keyBuilder = StringBuilder()
            while (i < end) {
                val ch = raw[i]
                if (escaped) {
                    keyBuilder.append(ch)
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    break
                } else {
                    keyBuilder.append(ch)
                }
                i++
            }
            val key = keyBuilder.toString()
            if (i >= end || raw[i] != '"') {
                throw IllegalArgumentException("invalid json key")
            }
            i++
            while (i < end && raw[i].isWhitespace()) i++
            if (i >= end || raw[i] != ':') {
                throw IllegalArgumentException("invalid json separator")
            }
            i++
            while (i < end && raw[i].isWhitespace()) i++
            if (i >= end) {
                result[key] = ""
                break
            }
            val value: String
            if (raw[i] == '"') {
                i++
                val valueBuilder = StringBuilder()
                escaped = false
                while (i < end) {
                    val ch = raw[i]
                    if (escaped) {
                        valueBuilder.append(ch)
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        break
                    } else {
                        valueBuilder.append(ch)
                    }
                    i++
                }
                value = valueBuilder.toString()
                if (i < end && raw[i] == '"') {
                    i++
                }
            } else {
                val valueStart = i
                while (i < end && raw[i] != ',' && raw[i] != '}') i++
                value = raw.substring(valueStart, i).trim()
            }
            result[key] = value
        }
        return result
    }

    private fun envToJson(env: RuntimeEnvironment): String {
        return buildString {
            append("{")
            append("\"id\":").append(env.id ?: "null").append(",")
            append("\"name\":").append(jsonString(env.name)).append(",")
            append("\"remark\":").append(jsonString(env.remark)).append(",")
            append("\"argsValue\":").append(jsonString(env.argsValue)).append(",")
            append("\"envValue\":").append(jsonString(env.envValue)).append(",")
            append("\"vmValue\":").append(jsonString(env.vmValue)).append(",")
            append("\"isDefault\":").append(env.isDefault ?: "null")
            append("}")
        }
    }

    private fun ok(payload: String): String = """{"ok":true,"data":$payload}"""

    private fun error(message: String): String = """{"ok":false,"error":${jsonString(message)}}"""

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        val escaped = buildString(value.length + 8) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
        return "\"$escaped\""
    }
}
