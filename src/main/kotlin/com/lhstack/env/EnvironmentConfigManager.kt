package com.lhstack.env

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/**
 * @author reisen7
 * @date 2025-10-16 16:29
 * @description 环境配置管理类，用于存储和管理自定义环境
 */

class EnvironmentConfigManager {
    
    companion object {
        private const val ENVIRONMENTS_KEY = "RUNTIME_ENVIRONMENT_environments"
        private const val DEFAULT_ENVIRONMENTS = "dev,test,prod"

        /**
         * 获取所有环境
         */
        fun getEnvironments(): List<String> {
            val environmentsStr = PropertiesComponent.getInstance().getValue(ENVIRONMENTS_KEY) ?: DEFAULT_ENVIRONMENTS
            return environmentsStr.split(",").filter { it.isNotBlank() }
        }

        /**
         * 保存环境列表
         */
        fun saveEnvironments(environments: List<String>) {
            val environmentsStr = environments.joinToString(",")
            PropertiesComponent.getInstance().setValue(ENVIRONMENTS_KEY, environmentsStr)
        }

        /**
         * 添加新环境
         */
        fun addEnvironment(envName: String): Boolean {
            if (envName.isBlank()) {
                return false
            }

            val environments = getEnvironments().toMutableList()
            if (environments.contains(envName)) {
                return false
            }

            environments.add(envName)
            saveEnvironments(environments)
            return true
        }

        /**
         * 删除环境
         */
        fun removeEnvironment(envName: String, project: Project? = null): Boolean {
            val environments = getEnvironments().toMutableList()
            if (!environments.contains(envName)) {
                return false
            }

            // 不能删除所有环境，至少保留一个
            if (environments.size <= 1) {
                return false
            }

            environments.remove(envName)
            saveEnvironments(environments)

            // 删除环境配置内容
            if (project != null) {
                deleteEnvironmentConfig(envName, project)
            }

            return true
        }

        /**
         * 删除环境配置内容
         */
        private fun deleteEnvironmentConfig(envName: String, project: Project) {
            val propertiesComponent = PropertiesComponent.getInstance()

            
            // 获取项目中的所有模块
            val modules = ModuleManager.getInstance(project).modules
            
            // 删除每个模块的环境配置
            modules.forEach { module ->
                // 构建键名并删除配置
                val argsKey = "RUNTIME_ENVIRONMENT_args_${envName}_${project.locationHash}_$module"
                val envKey = "RUNTIME_ENVIRONMENT_env_${envName}_${project.locationHash}_$module"
                
                propertiesComponent.unsetValue(argsKey)
                propertiesComponent.unsetValue(envKey)
            }
            
            // 删除活动环境配置
            modules.forEach { module ->
                val activeEnvKey = "RUNTIME_ENVIRONMENT_activeEnv_${project.locationHash}_$module"
                val currentActiveEnv = propertiesComponent.getValue(activeEnvKey)
                if (currentActiveEnv == envName) {
                    // 如果当前活动环境是要删除的环境，则重置为默认环境
                    val environments = getEnvironments()
                    if (environments.isNotEmpty()) {
                        propertiesComponent.setValue(activeEnvKey, environments.first())
                    }
                }
            }
        }

        /**
         * 重命名环境
         */
        fun renameEnvironment(oldName: String, newName: String, project: Project? = null): Boolean {
            if (newName.isBlank() || oldName == newName) {
                return false
            }

            val environments = getEnvironments().toMutableList()
            if (!environments.contains(oldName) || environments.contains(newName)) {
                return false
            }

            val index = environments.indexOf(oldName)
            environments[index] = newName
            saveEnvironments(environments)

            // 迁移环境配置内容
            if (project != null) {
                migrateEnvironmentConfig(oldName, newName, project)
            }

            return true
        }

        /**
         * 迁移环境配置内容
         */
        private fun migrateEnvironmentConfig(oldName: String, newName: String, project: Project) {
            val propertiesComponent = PropertiesComponent.getInstance()
            
            // 获取项目中的所有模块
            val modules = ModuleManager.getInstance(project).modules
            
            // 迁移每个模块的环境配置
            modules.forEach { module ->
                // 构建旧键名和新键名
                val oldArgsKey = "RUNTIME_ENVIRONMENT_args_${oldName}_${project.locationHash}_$module"
                val newArgsKey = "RUNTIME_ENVIRONMENT_args_${newName}_${project.locationHash}_$module"
                
                val oldEnvKey = "RUNTIME_ENVIRONMENT_env_${oldName}_${project.locationHash}_$module"
                val newEnvKey = "RUNTIME_ENVIRONMENT_env_${newName}_${project.locationHash}_$module"
                
                // 获取旧值并设置新值
                val oldArgsValue = propertiesComponent.getValue(oldArgsKey)
                val oldEnvValue = propertiesComponent.getValue(oldEnvKey)
                
                if (oldArgsValue != null) {
                    propertiesComponent.setValue(newArgsKey, oldArgsValue)
                    propertiesComponent.unsetValue(oldArgsKey)
                }
                
                if (oldEnvValue != null) {
                    propertiesComponent.setValue(newEnvKey, oldEnvValue)
                    propertiesComponent.unsetValue(oldEnvKey)
                }
            }
            
            // 迁移活动环境配置
            modules.forEach { module ->
                val oldActiveEnvKey = "RUNTIME_ENVIRONMENT_activeEnv_${project.locationHash}_$module"
                val newActiveEnvKey = "RUNTIME_ENVIRONMENT_activeEnv_${project.locationHash}_$module"
                
                val currentActiveEnv = propertiesComponent.getValue(oldActiveEnvKey)
                if (currentActiveEnv == oldName) {
                    propertiesComponent.setValue(newActiveEnvKey, newName)
                }
            }
        }

        /**
         * 更新ComboBox模型
         */
        fun updateComboBoxModel(comboBox: JComboBox<String>) {
            val environments = getEnvironments()
            val selectedItem = comboBox.selectedItem

            // 使用SwingUtilities确保在EDT线程中执行UI更新
            javax.swing.SwingUtilities.invokeLater {
                // 清空并重新添加所有环境
                comboBox.model = DefaultComboBoxModel(environments.toTypedArray())

                // 尝试恢复之前选中的项，如果不存在则选择第一个
                if (selectedItem != null && environments.contains(selectedItem.toString())) {
                    comboBox.selectedItem = selectedItem
                } else if (environments.isNotEmpty()) {
                    comboBox.selectedItem = environments.first()
                }

                // 强制刷新UI
                comboBox.updateUI()
            }
        }

        /**
         * 显示添加环境对话框
         */
        fun showAddEnvironmentDialog(project: Project): Boolean {
            val envName = Messages.showInputDialog(
                project,
                "请输入新环境名称:",
                "添加环境",
                Messages.getQuestionIcon()
            )

            if (envName != null && envName.isNotBlank()) {
                return if (addEnvironment(envName)) {
                    Messages.showMessageDialog(
                        project,
                        "环境 '$envName' 添加成功",
                        "成功",
                        Messages.getInformationIcon()
                    )
                    true
                } else {
                    Messages.showMessageDialog(
                        project,
                        "环境 '$envName' 已存在或添加失败",
                        "错误",
                        Messages.getErrorIcon()
                    )
                    false
                }
            }
            return false
        }

        /**
         * 显示删除环境对话框
         */
        fun showRemoveEnvironmentDialog(project: Project, currentEnv: String): Boolean {
            val result = Messages.showYesNoDialog(
                project,
                "确定要删除环境 '$currentEnv' 吗?",
                "删除环境",
                Messages.getQuestionIcon()
            )

            if (result == Messages.YES) {
                return if (removeEnvironment(currentEnv)) {
                    Messages.showMessageDialog(
                        project,
                        "环境 '$currentEnv' 删除成功",
                        "成功",
                        Messages.getInformationIcon()
                    )
                    true
                } else {
                    Messages.showMessageDialog(
                        project,
                        "环境 '$currentEnv' 删除失败，可能是最后一个环境",
                        "错误",
                        Messages.getErrorIcon()
                    )
                    false
                }
            }
            return false
        }

        /**
         * 显示重命名环境对话框
         */
        fun showRenameEnvironmentDialog(project: Project, oldName: String): Boolean {
            val newName = Messages.showInputDialog(
                project,
                "请输入新的环境名称:",
                "重命名环境",
                Messages.getQuestionIcon(),
                oldName,
                null
            )

            if (newName != null && newName.isNotBlank() && newName != oldName) {
                return if (renameEnvironment(oldName, newName)) {
                    Messages.showMessageDialog(
                        project,
                        "环境 '$oldName' 已重命名为 '$newName'",
                        "成功",
                        Messages.getInformationIcon()
                    )
                    true
                } else {
                    Messages.showMessageDialog(
                        project,
                        "环境重命名失败，可能新名称已存在",
                        "错误",
                        Messages.getErrorIcon()
                    )
                    false
                }
            }
            return false
        }
    }
}