package com.lhstack.env

import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.JBSplitter
import com.intellij.ui.ToggleActionButton
import com.intellij.util.ui.JBUI
import com.lhstack.data.component.MultiLanguageTextField
import com.lhstack.tools.plugins.*
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*


class PluginImpl : IPlugin {

    companion object {
        val disposers = mutableMapOf<String, Disposable>()
        val components = mutableMapOf<String, JComponent>()
        val loggers = mutableMapOf<String, Logger>()
    }

    override fun pluginIcon(): Icon = Helper.findIcon("pane.svg", PluginImpl::class.java)

    override fun pluginTabIcon(): Icon = Helper.findIcon("tab.svg", PluginImpl::class.java)

    override fun createPanel(project: Project): JComponent {
        return components.computeIfAbsent(project.locationHash) {
            val disposable = Disposer.newDisposable()
            val logger = loggers[project.locationHash]!!
            disposers[project.locationHash] = disposable
            val envTextField = MultiLanguageTextField(PropertiesFileType.INSTANCE, project, "")
            val argsTextField = MultiLanguageTextField(PropertiesFileType.INSTANCE, project, "")
            Disposer.register(disposable, envTextField)
            Disposer.register(disposable, argsTextField)
            JPanel(BorderLayout()).apply {
                val modules = ModuleManager.getInstance(project).modules.filter { it ->
                    it.isMainModule()
                }.toTypedArray()
                val changeState = java.util.concurrent.atomic.AtomicBoolean(true)
                val comboBox = ComboBox<Module>(modules)
                val envComboBox = ComboBox<String>(EnvironmentConfigManager.getEnvironments().toTypedArray())
                ComboboxSpeedSearch.installSpeedSearch<Module>(comboBox) {
                    it.toString()
                }
                if (modules.isNotEmpty()) {
                    comboBox.selectedItem = modules[0]
                    envComboBox.selectedItem = this.getActiveEnv(project,comboBox.selectedItem as Module)
                    envTextField.text = this.getEnv(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                    argsTextField.text = this.getArgs(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                }
                comboBox.addItemListener {
                    changeState.set(false)
                    envTextField.text = this.getEnv(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                    argsTextField.text = this.getArgs(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                    envComboBox.selectedItem = this.getActiveEnv(project,comboBox.selectedItem as Module)
                    changeState.set(true)
                }

                envTextField.addDocumentListener(object: DocumentListener{
                    override fun documentChanged(event: DocumentEvent) {
                        if(changeState.get()){
                            this.setEnv(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String,event.document.text)
                        }
                    }
                })

                argsTextField.addDocumentListener(object: DocumentListener{
                    override fun documentChanged(event: DocumentEvent) {
                        if(changeState.get()){
                            this.setArgs(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String,event.document.text)
                        }
                    }
                })



                envComboBox.addItemListener {
                    changeState.set(false)
                    this.setActiveEnv(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                    envTextField.text = this.getEnv(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                    argsTextField.text = this.getArgs(project,comboBox.selectedItem as Module,envComboBox.selectedItem as String)
                    changeState.set(true)
                }


                this.add(JPanel(BorderLayout()).apply {
                    this.add(JPanel(BorderLayout()).apply {
                        this.add(JPanel().apply {
                            this.layout = BoxLayout(this,BoxLayout.X_AXIS)
                            val toggle =
                                object : ToggleActionButton("", Helper.findIcon("tab.svg", PluginImpl::class.java)) {
                                    override fun isSelected(e: AnActionEvent): Boolean = this.getActive(project,comboBox.selectedItem as Module)

                                    override fun setSelected(
                                        e: AnActionEvent,
                                        state: Boolean
                                    ) {
                                        this.setActive(project,comboBox.selectedItem as Module,state)
                                    }

                                    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                                }
                            
                            // 环境管理按钮
                            val manageEnvButton = JButton("管理环境")
                            manageEnvButton.preferredSize = Dimension(90,0)
                            manageEnvButton.toolTipText = "管理环境配置"
                            manageEnvButton.addActionListener {
                                val dialog = EnvironmentManageDialog(project, envComboBox)
                                if (dialog.showAndGet()) {
                                    // 对话框关闭后，更新活动环境
                                    val newSelectedEnv = envComboBox.selectedItem as String
                                    this@PluginImpl.setActiveEnv(project, comboBox.selectedItem as Module, newSelectedEnv)
                                    
                                    // 更新参数和环境变量
                                    val selectedModule = comboBox.selectedItem as Module
                                    envTextField.text = this@PluginImpl.getEnv(project, selectedModule, newSelectedEnv)
                                    argsTextField.text = this@PluginImpl.getArgs(project, selectedModule, newSelectedEnv)
                                }
                            }
                            
                            this.add(ActionButton(toggle,toggle.templatePresentation,ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, Dimension(34,20)))
                            this.add(comboBox)
                            this.add(envComboBox)
                            this.add(manageEnvButton)
                        }, BorderLayout.EAST)
                    }, BorderLayout.NORTH)
                    this.add(JBSplitter(true).apply {
                        firstComponent = JPanel(BorderLayout()).apply {
                            this.add(JLabel("附加main函数启动的args参数").apply {
                                this.border = JBUI.Borders.empty(5, 0)
                                this.font = JBUI.Fonts.label(14.0f)
                            }, BorderLayout.NORTH)
                            this.add(argsTextField, BorderLayout.CENTER)
                        }
                        secondComponent = JPanel(BorderLayout()).apply {
                            this.add(JLabel("附加应用启动的环境变量").apply {
                                this.border = JBUI.Borders.empty(5, 0)
                                this.font = JBUI.Fonts.label(14.0f)
                            }, BorderLayout.NORTH)
                            this.add(envTextField, BorderLayout.CENTER)
                        }
                    }, BorderLayout.CENTER)
                }, BorderLayout.CENTER)
            }
        }
    }


    override fun closePanel(project: Project, pluginPanel: JComponent) {
        super.closePanel(project, pluginPanel)
        disposers.remove(project.locationHash)?.let { Disposer.dispose(it) }
        components.remove(project.locationHash)
    }

    override fun openProject(project: Project, logger: Logger, openThisPage: Runnable?) {
        loggers[project.locationHash] = logger
    }

    override fun closeProject(project: Project) {
        super.closeProject(project)
        loggers.remove(project.locationHash)
    }


    override fun support(jToolsVersion: Int, ideInfo: IdeInfo): Support {
        if (!ideInfo.fullApplicationName.lowercase().contains("idea")) {
            return Support(false, "仅支持idea产品")
        }
        return Support.SUPPORT
    }

    override fun install() {
        AttachJavaProgramPatcher.install()
    }

    override fun unInstall() {
        AttachJavaProgramPatcher.uninstall()
    }

    override fun supportMultiOpens(): Boolean {
        return false
    }

    override fun pluginName(): String = "运行时环境"

    override fun pluginDesc(): String = "为你的应用增加运行时的环境"

    override fun pluginVersion(): String = "0.0.1"
}