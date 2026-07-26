package com.lhstack.env.dialog

import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.lhstack.data.component.MultiLanguageTextField
import com.lhstack.env.AsyncLoader
import com.lhstack.env.PluginImpl
import com.lhstack.env.service.RuntimeEnvironment
import com.lhstack.env.service.RuntimeEnvironmentService
import com.lhstack.tools.plugins.Helper
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 全局环境编辑对话框。
 *
 * 全局环境数据由 [open] 在后台线程加载后传入, 构造器不访问数据库。
 */
class GlobalEnvSettingDialog private constructor(
    val project: Project,
    private val globalEnvironment: RuntimeEnvironment
): DialogWrapper(project,false) {

    companion object {
        /** 在后台线程读取全局环境后再在EDT上打开对话框, 避免在EDT访问数据库。 */
        fun open(project: Project) {
            AsyncLoader.loadThenOnEdt(
                { RuntimeEnvironmentService.execute { it.globalEnvironment } },
                { environment ->
                    if (environment != null) {
                        GlobalEnvSettingDialog(project, environment).show()
                    }
                }
            )
        }
    }
    private val envTextField = MultiLanguageTextField(PropertiesFileType.INSTANCE, project, globalEnvironment.envValue?:"").apply {
        this.document.addDocumentListener(object: DocumentListener{
            override fun documentChanged(event: DocumentEvent) {
                globalEnvironment.envValue = event.document.text
            }
        })
    }
    private val argsTextField = MultiLanguageTextField(PropertiesFileType.INSTANCE, project, globalEnvironment.argsValue?:"").apply {
        this.document.addDocumentListener(object: DocumentListener{
            override fun documentChanged(event: DocumentEvent) {
                globalEnvironment.argsValue = event.document.text
            }
        })
    }
    private val vmTextField = MultiLanguageTextField(PlainTextFileType.INSTANCE, project, globalEnvironment.vmValue?:"").apply {
        this.document.addDocumentListener(object: DocumentListener{
            override fun documentChanged(event: DocumentEvent) {
                globalEnvironment.vmValue = event.document.text
            }
        })
    }
    init {
        this.setSize(800,600)
        this.title = "全局环境"
        this.isAutoAdjustable = false
        setOKButtonText("确认")
        setCancelButtonText("取消")
        this.init()
    }

    override fun createSouthPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            val actionManager = ActionManager.getInstance()
            val toolbar =
                actionManager.createActionToolbar(
                    "JTools@Runtime@Environment@GlobalEnvSettingDialogEnabled",
                    DefaultActionGroup().apply {
                        this.add(object :
                            AnAction({ "启用" }, Helper.findIcon("globalEnv.svg", PluginImpl::class.java)) {
                            override fun update(e: AnActionEvent) {
                                super.update(e)
                                val isActive = globalEnvironment.isDefault == 1
                                if(isActive){
                                    e.presentation.text = "禁用"
                                    e.presentation.icon = Helper.findIcon("enable.svg",PluginImpl::class.java)
                                }else {
                                    e.presentation.text = "启用"
                                    e.presentation.icon = Helper.findIcon("disable.svg",PluginImpl::class.java)
                                }
                            }

                            override fun actionPerformed(p0: AnActionEvent) {
                                globalEnvironment.isDefault = if(globalEnvironment.isDefault == 0){1}else{0}
                            }
                        })
                    }, true
                )
            toolbar.targetComponent = this
            this.add(toolbar.component, BorderLayout.WEST)
            this.add(JPanel().apply {
                this.layout = BoxLayout(this, BoxLayout.X_AXIS)
                val buttons = createActions().map {
                    createJButtonForAction(it)
                }
                buttons.forEach {
                    this.add(it)
                }
            }, BorderLayout.EAST)
        }
    }


    override fun doOKAction() {
        super.doOKAction()
        globalEnvironment.envValue = envTextField.text
        globalEnvironment.vmValue = vmTextField.text
        globalEnvironment.argsValue = argsTextField.text
        // 写库挪到后台线程, 不阻塞EDT
        AsyncLoader.runInBackground {
            RuntimeEnvironmentService.getService { it.updateById(globalEnvironment) }
        }
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout()).apply {
        Disposer.register(disposable, envTextField)
        Disposer.register(disposable, argsTextField)
        Disposer.register(disposable, vmTextField)
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
    }
}