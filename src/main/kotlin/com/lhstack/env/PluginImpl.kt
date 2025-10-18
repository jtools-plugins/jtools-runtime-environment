package com.lhstack.env

import com.intellij.designer.actions.AbstractComboBoxAction
import com.intellij.icons.AllIcons
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.lhstack.data.component.MultiLanguageTextField
import com.lhstack.env.dialog.EnvSettingDialog
import com.lhstack.env.dialog.GlobalEnvSettingDialog
import com.lhstack.env.service.RuntimeEnvironment
import com.lhstack.env.service.RuntimeEnvironmentService
import com.lhstack.tools.plugins.*
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


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
            val vmTextField = MultiLanguageTextField(PlainTextFileType.INSTANCE, project, "")
            Disposer.register(disposable, envTextField)
            Disposer.register(disposable, argsTextField)
            JPanel(BorderLayout()).apply {
                val modules = ModuleManager.getInstance(project).modules.filter { it ->
                    it.isMainModule()
                }.toList()
                val changeState = java.util.concurrent.atomic.AtomicBoolean(true)
                var envComboBox: AbstractComboBoxAction<RuntimeEnvironment>? = null

                val modulesBox = object : AbstractComboBoxAction<Module>() {

                    init {
                        setItems(
                            modules, if (modules.isNotEmpty()) {
                                modules[0]
                            } else {
                                null
                            }
                        )
                    }

                    override fun update(
                        p0: Module,
                        p1: Presentation,
                        p2: Boolean
                    ) {
                        p1.text = p0.name
                    }

                    override fun selectionChanged(p0: Module): Boolean {
                        if (p0 != selection) {
                            changeState.set(false)
                            ApplicationManager.getApplication().runWriteAction {
                                RuntimeEnvironmentService.getService {
                                    val list = it.getRuntimeEnvironments(project, this.selection)
                                    val envId = it.getSelectEnvId(project, this.selection)
                                    val select: RuntimeEnvironment? = if (envId != null) {
                                        list.firstOrNull { item -> item.id == envId }?.also { env ->
                                            envTextField.text = env.envValue
                                            argsTextField.text = env.argsValue
                                            vmTextField.text= env.vmValue
                                        }
                                    } else {
                                        list[0]?.also { env ->
                                            envTextField.text = env.envValue
                                            argsTextField.text = env.argsValue
                                            vmTextField.text= env.vmValue
                                        }
                                    }
                                    envComboBox?.setItems(list, select)
                                }
                            }
                            changeState.set(true)
                            return true
                        }
                        return false
                    }

                }


                envComboBox = object : AbstractComboBoxAction<RuntimeEnvironment>() {

                    init {
                        ApplicationManager.getApplication().runWriteAction {
                            RuntimeEnvironmentService.getService {
                                val list = it.getRuntimeEnvironments(project, modulesBox.selection)
                                val envId = it.getSelectEnvId(project, modulesBox.selection)
                                val select: RuntimeEnvironment? = if (envId != null) {
                                    list.firstOrNull { item -> item.id == envId }?.also { env ->
                                        envTextField.text = env.envValue
                                        argsTextField.text = env.argsValue
                                        vmTextField.text= env.vmValue
                                    }
                                } else {
                                    list[0]?.also { env ->
                                        envTextField.text = env.envValue
                                        argsTextField.text = env.argsValue
                                        vmTextField.text= env.vmValue
                                    }
                                }
                                setItems(list, select)
                            }
                        }
                    }

                    override fun update(
                        p0: RuntimeEnvironment,
                        p1: Presentation,
                        p2: Boolean
                    ) {
                        p1.text = "${p0.name}: ${
                            if ((p0.remark?.length?:0) > 5) {
                                p0.remark?.substring(0, 3) + ".."
                            } else {
                                p0.remark
                            }
                        }"
                        p1.description = p0.remark
                    }

                    override fun selectionChanged(p0: RuntimeEnvironment): Boolean {
                        if (p0.id != selection?.id) {
                            changeState.set(false)
                            ApplicationManager.getApplication().runWriteAction {
                                RuntimeEnvironmentService.getService { service ->
                                    service.updateSelectEnv(p0.id)
                                    envTextField.text = p0.envValue
                                    argsTextField.text = p0.argsValue
                                    vmTextField.text= p0.vmValue
                                }
                            }

                            changeState.set(true)
                            return true
                        }
                        return false
                    }

                }
                val enabledAction =
                    object : ToggleAction({
                        "开启"
                    }, AllIcons.Actions.Selectall) {
                        override fun isSelected(p0: AnActionEvent): Boolean {
                            var isActive = false
                            ApplicationManager.getApplication().runReadAction {
                                RuntimeEnvironmentService.getService { service ->
                                    isActive = service.isActive(project, modulesBox.selection)
                                }
                            }
                            return isActive
                        }

                        override fun setSelected(p0: AnActionEvent, p1: Boolean) {
                            ApplicationManager.getApplication().runWriteAction{
                                RuntimeEnvironmentService.getService { service ->
                                    if(p1){
                                        p0.presentation.text = "关闭"
                                    }else {
                                        p0.presentation.text = "开启"
                                    }
                                    service.updateActive(envComboBox.selection, p1)
                                }
                            }
                        }

                    }

                envTextField.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        if (changeState.get()) {
                            ApplicationManager.getApplication().runWriteAction {
                                RuntimeEnvironmentService.getService { service ->
                                    envComboBox.selection?.also { env ->
                                        env.envValue = event.document.text
                                        service.updateById(env)
                                    }
                                }
                            }
                        }
                    }
                })
                vmTextField.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        if (changeState.get()) {
                            ApplicationManager.getApplication().runWriteAction {
                                RuntimeEnvironmentService.getService { service ->
                                    envComboBox.selection?.also { env ->
                                        env.vmValue = event.document.text
                                        service.updateById(env)
                                    }
                                }
                            }
                        }
                    }
                })

                argsTextField.addDocumentListener(object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        if (changeState.get()) {
                            ApplicationManager.getApplication().runWriteAction {
                                RuntimeEnvironmentService.getService { service ->
                                    envComboBox.selection?.also { env ->
                                        env.argsValue = event.document.text
                                        service.updateById(env)
                                    }
                                }
                            }
                        }
                    }
                })

                val settingAction = object:AnAction({"Settings"}, AllIcons.General.Settings) {
                    override fun actionPerformed(p0: AnActionEvent) {
                        val envSettingDialog = EnvSettingDialog(logger,project,modulesBox.selection,envComboBox,vmTextField,argsTextField,envTextField)
                        envSettingDialog.show()
                    }
                }

                val globalEnvAction = object: AnAction({"全局环境"}, Helper.findIcon("globalEnv.svg", PluginImpl::class.java)){
                    override fun update(e: AnActionEvent) {
                        super.update(e)
                        val isActive = RuntimeEnvironmentService.execute { it.globalEnvironmentActive() }
                        Toggleable.setSelected(e.presentation,isActive)
                    }
                    override fun actionPerformed(p0: AnActionEvent) {
                        GlobalEnvSettingDialog(project).show()
                    }
                }

                val actionManager = ActionManager.getInstance()
                val toolbar =
                    actionManager.createActionToolbar("JTools@RuntimeEnvironment@Toolbar", DefaultActionGroup().also {
                        it.add(globalEnvAction)
                        it.add(enabledAction)
                        it.add(modulesBox)
                        it.add(envComboBox)
                        it.add(settingAction)
                    }, true)
                toolbar.targetComponent = this
                this.add(JPanel(BorderLayout()).apply {
                    this.add(JPanel(BorderLayout()).apply {
                        this.add(toolbar.component, BorderLayout.EAST)
                    }, BorderLayout.NORTH)
                    this.add(JBSplitter(true).apply {
                        this.proportion = 0.667f
                        firstComponent = JBSplitter(true).apply {
                            firstComponent = JPanel(BorderLayout()).apply {
                                this.add(JLabel("附加JVM参数,多个回车隔开").apply {
                                    this.border = JBUI.Borders.empty(5, 0)
                                    this.font = JBUI.Fonts.label(14.0f)
                                }, BorderLayout.NORTH)
                                this.add(vmTextField, BorderLayout.CENTER)
                            }
                            secondComponent = JPanel(BorderLayout()).apply {
                                this.add(JLabel("附加main函数启动的args参数").apply {
                                    this.border = JBUI.Borders.empty(5, 0)
                                    this.font = JBUI.Fonts.label(14.0f)
                                }, BorderLayout.NORTH)
                                this.add(argsTextField, BorderLayout.CENTER)
                            }
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
        RuntimeEnvironmentService.init();
        AttachJavaProgramPatcher.install()
    }

    override fun unInstall() {
        try{
            AttachJavaProgramPatcher.uninstall()
        }catch (e:Throwable){

        }
        try{
            RuntimeEnvironmentService.destroy()
        }catch (e:Throwable){

        }

    }

    override fun supportMultiOpens(): Boolean {
        return false
    }

    override fun pluginName(): String = "运行时环境"

    override fun pluginDesc(): String = "为你的应用增加运行时的环境"

    override fun pluginVersion(): String = "0.0.1"
}