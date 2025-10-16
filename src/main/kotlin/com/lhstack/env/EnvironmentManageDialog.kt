package com.lhstack.env

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * @author reisen7
 * @date 2025-10-16 16:40
 * @description 环境管理对话框，用于统一管理环境
 */

class EnvironmentManageDialog(project: Project, private val envComboBox: JComboBox<String>) : DialogWrapper(project) {
    
    private val project = project
    private val environmentList = JBList<String>()
    private val listModel = DefaultListModel<String>()
    private val addButton = JButton("添加")
    private val removeButton = JButton("删除")
    private val renameButton = JButton("重命名")
    
    init {
        title = "环境管理"
        init()
        loadEnvironments()
    }
    
    override fun createCenterPanel(): JComponent {
        // 初始化列表
        environmentList.model = listModel
        environmentList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        
        // 添加列表选择监听器
        environmentList.addListSelectionListener(object : ListSelectionListener {
            override fun valueChanged(e: ListSelectionEvent?) {
                updateButtonStates()
            }
        })
        
        // 添加按钮事件
        addButton.addActionListener {
            addEnvironment()
        }
        
        removeButton.addActionListener {
            removeEnvironment()
        }
        
        renameButton.addActionListener {
            renameEnvironment()
        }
        
        // 创建按钮面板
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(5)
            add(addButton)
            add(Box.createVerticalStrut(5))
            add(removeButton)
            add(Box.createVerticalStrut(5))
            add(renameButton)
        }
        
        // 创建主面板
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(10)
            add(JBScrollPane(environmentList).apply {
                preferredSize = JBUI.size(300, 200)
            })
            add(Box.createHorizontalStrut(10))
            add(buttonPanel)
        }
    }
    
    /**
     * 加载环境列表
     */
    private fun loadEnvironments() {
        listModel.clear()
        val environments = EnvironmentConfigManager.getEnvironments()
        environments.forEach { env ->
            listModel.addElement(env)
        }
        updateButtonStates()
    }
    
    /**
     * 更新按钮状态
     */
    private fun updateButtonStates() {
        val selectedIndex = environmentList.selectedIndex
        val hasSelection = selectedIndex >= 0
        removeButton.isEnabled = hasSelection && listModel.size > 1
        renameButton.isEnabled = hasSelection
    }
    
    /**
     * 添加环境
     */
    private fun addEnvironment() {
        val envName = Messages.showInputDialog(
            project,
            "请输入新环境名称:",
            "添加环境",
            Messages.getQuestionIcon()
        )
        
        if (envName != null && envName.isNotBlank()) {
            if (EnvironmentConfigManager.addEnvironment(envName)) {
                Messages.showMessageDialog(
                    project,
                    "环境 '$envName' 添加成功",
                    "成功",
                    Messages.getInformationIcon()
                )
                loadEnvironments()
                // 选择新添加的环境
                val index = listModel.indexOf(envName)
                if (index >= 0) {
                    environmentList.selectedIndex = index
                }
            } else {
                Messages.showMessageDialog(
                    project,
                    "环境 '$envName' 已存在或添加失败",
                    "错误",
                    Messages.getErrorIcon()
                )
            }
        }
    }
    
    /**
     * 删除环境
     */
    private fun removeEnvironment() {
        val selectedIndex = environmentList.selectedIndex
        if (selectedIndex < 0) return
        
        val selectedEnv = listModel.getElementAt(selectedIndex)
        val result = Messages.showYesNoDialog(
            project,
            "确定要删除环境 '$selectedEnv' 吗?",
            "删除环境",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            if (EnvironmentConfigManager.removeEnvironment(selectedEnv, project)) {
                // 触发envComboBox的ItemEvent来强制更新显示
                val event = ItemEvent(envComboBox, ItemEvent.ITEM_STATE_CHANGED,
                    envComboBox.selectedItem, ItemEvent.SELECTED)
                envComboBox.itemListeners.forEach { it.itemStateChanged(event) }


                Messages.showMessageDialog(
                    project,
                    "环境 '$selectedEnv' 删除成功",
                    "成功",
                    Messages.getInformationIcon()
                )
                loadEnvironments()
                // 选择第一个环境
                if (listModel.size > 0) {
                    environmentList.selectedIndex = 0
                }
            } else {
                Messages.showMessageDialog(
                    project,
                    "环境 '$selectedEnv' 删除失败，可能是最后一个环境",
                    "错误",
                    Messages.getErrorIcon()
                )
            }
        }
    }
    
    /**
     * 重命名环境
     */
    private fun renameEnvironment() {
        val selectedIndex = environmentList.selectedIndex
        if (selectedIndex < 0) return
        
        val oldName = listModel.getElementAt(selectedIndex)
        val newName = Messages.showInputDialog(
            project,
            "请输入新的环境名称:",
            "重命名环境",
            Messages.getQuestionIcon(),
            oldName,
            null
        )
        
        if (newName != null && newName.isNotBlank() && newName != oldName) {
            if (EnvironmentConfigManager.renameEnvironment(oldName, newName, project)) {

                // 触发envComboBox的ItemEvent来强制更新显示
                val event = ItemEvent(envComboBox, ItemEvent.ITEM_STATE_CHANGED,
                    envComboBox.selectedItem, ItemEvent.SELECTED)
                envComboBox.itemListeners.forEach { it.itemStateChanged(event) }

                Messages.showMessageDialog(
                    project,
                    "环境 '$oldName' 已重命名为 '$newName'",
                    "成功",
                    Messages.getInformationIcon()
                )
                loadEnvironments()
                // 选择重命名后的环境
                val index = listModel.indexOf(newName)
                if (index >= 0) {
                    environmentList.selectedIndex = index
                }
            } else {
                Messages.showMessageDialog(
                    project,
                    "环境重命名失败，可能新名称已存在",
                    "错误",
                    Messages.getErrorIcon()
                )
            }
        }
    }
    
    override fun doOKAction() {
        // 更新主界面的环境下拉框
        EnvironmentConfigManager.updateComboBoxModel(envComboBox)

        super.doOKAction()
    }

    override fun doCancelAction() {
        // 取消时不更新环境下拉框
        EnvironmentConfigManager.updateComboBoxModel(envComboBox)
        super.doCancelAction()
    }
}