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
            p1.configurationModule.module?.let { module ->
                RuntimeEnvironmentService.getService { service ->
                    val logger = PluginImpl.loggers[project.locationHash]!!
                    try{
                        val envMap = mutableMapOf<String, String>()
                        val argsMap = mutableMapOf<String, String>()
                        val vmArgs = mutableSetOf<String>()
                        service.globalEnvironment?.let { environment ->
                            logger.info(environment.isDefault == 1)
                            if(environment.isDefault == 1){
                                (environment.argsValue?:"").split("\n").filter { it.isNotBlank() }.forEach { line ->
                                    val array = line.split("=")
                                    argsMap[array[0].trim()] = array[1].trim()
                                }
                                (environment.vmValue?:"").split("\n").filter { it.isNotBlank() }.forEach { line ->
                                    vmArgs.add(line.trim())
                                }
                                (environment.envValue?:"").split("\n").filter { it.isNotBlank() }.forEach { line ->
                                    val array = line.split("=")
                                    envMap[array[0].trim()] = array[1].trim()
                                }
                            }
                        }
                        if(service.isActive(project, module)){
                            service.getSelectEnvId(project, module)?.also { envId ->
                                service.getById(envId)?.also { environment ->
                                    (environment.argsValue?:"").split("\n").filter { it.isNotBlank() }.forEach { line ->
                                        val array = line.split("=")
                                        argsMap[array[0].trim()] = array[1].trim()
                                    }
                                    (environment.vmValue?:"").split("\n").filter { it.isNotBlank() }.forEach { line ->
                                        vmArgs.add(line.trim())
                                    }
                                    (environment.envValue?:"").split("\n").filter { it.isNotBlank() }.forEach { line ->
                                        val array = line.split("=")
                                        envMap[array[0].trim()] = array[1].trim()
                                    }
                                }
                            }

                        }
                        envMap.forEach { (key, value) ->
                            p2.addEnv(key,value)
                        }

                        argsMap.forEach { (key, value) ->
                            p2.programParametersList.add("$key=$value")
                        }
                        vmArgs.forEach {
                            p2.vmParametersList.add(it)
                        }
                    }catch (e:Throwable){
                        logger.error(e.message + "\n" + e.stackTrace.joinToString("\n") { it.toString() })
                    }
                }
            }
        }
    }
}