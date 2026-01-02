package com.lhstack.env

import com.intellij.execution.Executor
import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.JavaProgramPatcher
import com.lhstack.env.service.RuntimeEnvironmentService

class AttachJavaProgramPatcher: JavaProgramPatcher() {

    companion object {
        val instance = AttachJavaProgramPatcher()
        fun install(){
            EP_NAME.point.registerExtension(instance){}
        }
        fun uninstall(){
            EP_NAME.point.unregisterExtension(AttachJavaProgramPatcher::class.java)
        }
    }
    override fun patchJavaParameters(
        p0: Executor,
        p1: RunProfile,
        p2: JavaParameters
    ) {
        if(p1 is JavaRunConfigurationBase){
            val project = p1.project
            val logger = PluginImpl.loggers[project.locationHash]
            p1.configurationModule.module?.let { module ->
                // 注入插件配置的参数
                RuntimeEnvironmentService.getService { service ->
                    try{
                        val envMap = mutableMapOf<String, String>()
                        val argsMap = mutableMapOf<String, String>()
                        val vmArgs = mutableSetOf<String>()
                        
                        service.globalEnvironment?.let { environment ->
                            if(environment.isDefault == 1){
                                parseKeyValueLines(environment.argsValue, argsMap)
                                parseVmArgs(environment.vmValue, vmArgs)
                                parseKeyValueLines(environment.envValue, envMap)
                            }
                        }
                        
                        if(service.isActive(project, module)){
                            service.getSelectEnvId(project, module)?.also { envId ->
                                service.getById(envId)?.also { environment ->
                                    parseKeyValueLines(environment.argsValue, argsMap)
                                    parseVmArgs(environment.vmValue, vmArgs)
                                    parseKeyValueLines(environment.envValue, envMap)
                                }
                            }
                        }
                        
                        envMap.forEach { (key, value) ->
                            p2.addEnv(key, value)
                        }

                        argsMap.forEach { (key, value) ->
                            p2.programParametersList.add("$key=$value")
                        }
                        
                        vmArgs.forEach {
                            p2.vmParametersList.add(it)
                        }
                    }catch (e:Throwable){
                        logger?.error(e.message + "\n" + e.stackTrace.joinToString("\n") { it.toString() })
                    }
                }
                
                // 输出完整启动参数到日志（不管是否注入都会输出）
                try {
                    logger?.info("=== 启动参数 [${module.name}] ===")
                    
                    val ideaVmParams = p2.vmParametersList.parameters
                    if (ideaVmParams.isNotEmpty()) {
                        logger?.info("JVM参数: \n${ideaVmParams.joinToString("\n")}")
                    }
                    
                    val ideaProgramParams = p2.programParametersList.parameters
                    if (ideaProgramParams.isNotEmpty()) {
                        logger?.info("程序参数: \n${ideaProgramParams.joinToString("\n")}")
                    }
                    
                    val allEnv = p2.env
                    if (allEnv.isNotEmpty()) {
                        logger?.info("环境变量: \n${allEnv.entries.joinToString("\n") { "${it.key}=${it.value}" }}")
                    }
                    
                    logger?.info("==============================")
                } catch (e: Throwable) {
                    logger?.error(e.message + "\n" + e.stackTrace.joinToString("\n") { it.toString() })
                }
            }
        }
    }
    
    /**
     * 解析键值对格式的行，支持 key=value 格式
     * 值中可以包含等号
     */
    private fun parseKeyValueLines(content: String?, targetMap: MutableMap<String, String>) {
        content?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.forEach { line ->
                val trimmedLine = line.trim()
                val idx = trimmedLine.indexOf('=')
                if (idx > 0) {
                    val key = trimmedLine.substring(0, idx).trim()
                    val value = if (idx < trimmedLine.length - 1) {
                        trimmedLine.substring(idx + 1).trim()
                    } else {
                        ""
                    }
                    if (key.isNotEmpty()) {
                        targetMap[key] = value
                    }
                }
            }
    }
    
    /**
     * 解析VM参数，每行一个参数
     */
    private fun parseVmArgs(content: String?, targetSet: MutableSet<String>) {
        content?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    targetSet.add(trimmed)
                }
            }
    }
}