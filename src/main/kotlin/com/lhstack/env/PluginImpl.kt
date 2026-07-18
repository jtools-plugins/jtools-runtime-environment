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
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.lhstack.data.component.MultiLanguageTextField
import com.lhstack.env.dialog.EnvSettingDialog
import com.lhstack.env.dialog.GlobalEnvSettingDialog
import com.lhstack.env.service.RuntimeEnvironment
import com.lhstack.env.service.RuntimeEnvironmentService
import com.lhstack.tools.plugins.*
import java.awt.BorderLayout
import javax.swing.*


class PluginImpl : IPlugin {

    companion object {
        val disposers = mutableMapOf<String, Disposable>()
        val components = mutableMapOf<String, JComponent>()
        val loggers = mutableMapOf<String, Logger>()
        val tabActions = mutableMapOf<String, List<AnAction>>()
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
            Disposer.register(disposable, vmTextField)
            JPanel(BorderLayout()).apply {
                val modules = ModuleManager.getInstance(project).modules.filter { it ->
                    it.isMainModule()
                }.toList()
                val changeState = java.util.concurrent.atomic.AtomicBoolean(true)
                var envComboBox: AbstractComboBoxAction<RuntimeEnvironment>? = null

                // 统一在EDT上刷新文本框, 且在刷新期间关闭changeState, 防止documentChanged把新模块的文本写回旧环境
                fun applyEnvToFields(env: RuntimeEnvironment) {
                    SwingUtilities.invokeLater {
                        changeState.set(false)
                        try {
                            envTextField.text = env.envValue ?: ""
                            argsTextField.text = env.argsValue ?: ""
                            vmTextField.text = env.vmValue ?: ""
                        } finally {
                            changeState.set(true)
                        }
                    }
                }

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
                            RuntimeEnvironmentService.getService {
                                val list = it.getRuntimeEnvironments(project, p0)
                                if (list.isEmpty()) {
                                    return@getService
                                }
                                val envId = it.getSelectEnvId(project, p0)

                                val select: RuntimeEnvironment? = (if (envId != null) {
                                    list.firstOrNull { item -> item.id == envId }
                                } else {
                                    list.firstOrNull()
                                })?.also { env -> applyEnvToFields(env) }
                                envComboBox?.setItems(list, select)
                            }
                            return true
                        }
                        return false
                    }

                }


                envComboBox = object : AbstractComboBoxAction<RuntimeEnvironment>() {

                    init {
                        RuntimeEnvironmentService.getService {
                            val selection = modulesBox.selection ?: return@getService
                            val list = it.getRuntimeEnvironments(project, selection)
                            if (list.isEmpty()) return@getService

                            val envId = it.getSelectEnvId(project, selection)
                            val select: RuntimeEnvironment? = (if (envId != null) {
                                list.firstOrNull { item -> item.id == envId }
                            } else {
                                list.firstOrNull()
                            })?.also { env -> applyEnvToFields(env) }

                            setItems(list, select)
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
                            RuntimeEnvironmentService.getService { service ->
                                p0.id?.let { service.updateSelectEnv(it) }
                                applyEnvToFields(p0)
                            }
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
                            return ApplicationManager.getApplication().runReadAction<Boolean> {
                                val selection = modulesBox.selection ?: return@runReadAction false
                                RuntimeEnvironmentService.execute { service -> service.isActive(project, selection) } ?: false
                            }
                        }

                        override fun setSelected(p0: AnActionEvent, p1: Boolean) {
                            ApplicationManager.getApplication().runWriteAction{
                                RuntimeEnvironmentService.getService { service ->
                                    if(p1){
                                        p0.presentation.text = "关闭"
                                    }else {
                                        p0.presentation.text = "开启"
                                    }
                                    envComboBox.selection?.let { service.updateActive(it, p1) }
                                }
                            }
                        }

                        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

                    }

                // 防抖保存, 避免每次按键都写库; DB写入不需要runWriteAction
                fun bindAutoSave(field: MultiLanguageTextField, apply: (RuntimeEnvironment, String) -> Unit) {
                    val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
                    field.addDocumentListener(object : DocumentListener {
                        override fun documentChanged(event: DocumentEvent) {
                            if (changeState.get()) {
                                val text = event.document.text
                                alarm.cancelAllRequests()
                                alarm.addRequest({
                                    RuntimeEnvironmentService.getService { service ->
                                        envComboBox.selection?.also { env ->
                                            apply(env, text)
                                            service.updateById(env)
                                        }
                                    }
                                }, 300)
                            }
                        }
                    })
                }

                bindAutoSave(envTextField) { env, text -> env.envValue = text }
                bindAutoSave(vmTextField) { env, text -> env.vmValue = text }
                bindAutoSave(argsTextField) { env, text -> env.argsValue = text }

                val settingAction = object:AnAction({"Settings"}, AllIcons.General.Settings) {
                    override fun actionPerformed(p0: AnActionEvent) {
                        val envSettingDialog = EnvSettingDialog(logger,project,modulesBox.selection,envComboBox,vmTextField,argsTextField,envTextField)
                        envSettingDialog.show()
                    }
                }

                val globalEnvAction = object: AnAction({"全局环境"}, Helper.findIcon("globalEnv.svg", PluginImpl::class.java)){
                    override fun update(e: AnActionEvent) {
                        super.update(e)
                        val isActive = RuntimeEnvironmentService.execute { it.globalEnvironmentActive() } ?: false
                        Toggleable.setSelected(e.presentation, isActive)
                    }
                    override fun actionPerformed(p0: AnActionEvent) {
                        GlobalEnvSettingDialog(project).show()
                    }
                }

                tabActions[project.locationHash] = listOf(globalEnvAction, enabledAction, modulesBox, envComboBox, settingAction)
                this.add(JPanel(BorderLayout()).apply {
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
        tabActions.remove(project.locationHash)
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

    override fun tabPanelActions(project: Project?, pluginPanel: JComponent?): List<AnAction?>? {
        return project?.let { tabActions[it.locationHash] } ?: emptyList()
    }

    override fun functionCallings(project: Project?): List<FunctionCalling?>? {
        return FunctionCallingRegistry.functionCallings(project)
    }

    override fun supportMultiOpens(): Boolean {
        return false
    }

    override fun pluginName(): String = "运行时环境"

    override fun pluginDesc(): String = "为你的应用增加运行时的环境"

    override fun pluginVersion(): String = "v5"
}
