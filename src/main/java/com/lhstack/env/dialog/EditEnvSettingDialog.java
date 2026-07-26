package com.lhstack.env.dialog;

import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.lhstack.data.component.MultiLanguageTextField;
import com.lhstack.env.AsyncLoader;
import com.lhstack.env.PluginImpl;
import com.lhstack.env.service.RuntimeEnvironment;
import com.lhstack.env.service.RuntimeEnvironmentService;
import com.lhstack.tools.plugins.Logger;
import kotlin.Unit;
import org.jdesktop.swingx.VerticalLayout;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EditEnvSettingDialog extends DialogWrapper {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(EditEnvSettingDialog.class);
    private final JTable table;
    private final AbstractComboBoxAction<RuntimeEnvironment> runtimeEnvironmentComboBox;
    private final Project project;
    private final Module module;
    private final MultiLanguageTextField vmTextField;
    private final MultiLanguageTextField argsTextField;
    private final MultiLanguageTextField envTextField;
    private final DefaultTableModel model;
    private RuntimeEnvironment runtimeEnvironment;

    /**
     * 环境数据由调用方在后台线程加载后传入, 构造器不访问数据库。
     *
     * @param environment 新增时传入空实例, 编辑时传入已加载的环境
     */
    public EditEnvSettingDialog(Project project, DefaultTableModel model, Module module, JTable table, AbstractComboBoxAction<RuntimeEnvironment> comboBox,
                                RuntimeEnvironment environment, MultiLanguageTextField vmTextField, MultiLanguageTextField argsTextField, MultiLanguageTextField envTextField) {
        super(project, true);
        this.table = table;
        this.module = module;
        this.project = project;
        this.vmTextField = vmTextField;
        this.argsTextField = argsTextField;
        this.envTextField = envTextField;
        this.model = model;
        this.runtimeEnvironmentComboBox = comboBox;
        this.runtimeEnvironment = environment;
        this.setTitle(environment.getId() != null ? "更新环境" : "新增环境");
        this.setSize(600, 881);
        this.setAutoAdjustable(false);
        this.init();
    }

    /** 在后台线程读取环境后再在EDT上打开对话框, 避免在EDT访问数据库。 */
    public static void open(Project project, DefaultTableModel model, Module module, JTable table,
                            AbstractComboBoxAction<RuntimeEnvironment> comboBox, Integer id,
                            MultiLanguageTextField vmTextField, MultiLanguageTextField argsTextField, MultiLanguageTextField envTextField) {
        if (id == null) {
            new EditEnvSettingDialog(project, model, module, table, comboBox, new RuntimeEnvironment(), vmTextField, argsTextField, envTextField).show();
            return;
        }
        AsyncLoader.loadThenOnEdt(
                () -> RuntimeEnvironmentService.execute(service -> service.getById(id)),
                environment -> {
                    if (environment == null) {
                        Messages.showWarningDialog("运行环境不存在: " + id, "提示");
                        return;
                    }
                    new EditEnvSettingDialog(project, model, module, table, comboBox, environment, vmTextField, argsTextField, envTextField).show();
                });
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{
                new AbstractAction("保存") {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        boolean isCreate = runtimeEnvironment.getId() == null;
                        if (isCreate) {
                            fillCreationFields();
                        }
                        // 校验与写库都在后台线程完成, 结果回到EDT再更新界面
                        AsyncLoader.loadThenOnEdt(() -> saveInBackground(isCreate), result -> {
                            if (result == SaveResult.DUPLICATED_NAME) {
                                Messages.showInfoMessage("环境名字已存在,请修改名字之后再保存或者更新", "提示");
                                return;
                            }
                            updateTableRow(isCreate);
                            if (result == SaveResult.SAVED_SELECTED) {
                                syncPanelTextFields();
                            }
                            doCancelAction();
                            refreshComboBox();
                        });
                    }
                },
                new AbstractAction("取消") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        doCancelAction();
                    }
                }
        };
    }

    private enum SaveResult {
        DUPLICATED_NAME,
        SAVED,
        SAVED_SELECTED
    }

    /**
     * 校验与持久化在同一个事务内完成, 并且不在事务里弹模态框、不碰表格模型。
     * 必须在后台线程调用。
     */
    private SaveResult saveInBackground(boolean isCreate) {
        return RuntimeEnvironmentService.execute(service -> {
            if (service.countByName(project, module, runtimeEnvironment) > 0) {
                return SaveResult.DUPLICATED_NAME;
            }
            Integer selectedId = service.getSelectEnvId(project, module);
            if (isCreate) {
                service.save(runtimeEnvironment);
            } else {
                service.updateById(runtimeEnvironment);
            }
            return Objects.equals(selectedId, runtimeEnvironment.getId())
                    ? SaveResult.SAVED_SELECTED
                    : SaveResult.SAVED;
        });
    }

    private void fillCreationFields() {
        runtimeEnvironment.setProjectHash(project.getLocationHash());
        runtimeEnvironment.setProjectName(project.getName());
        runtimeEnvironment.setProjectPath(project.getBasePath());
        runtimeEnvironment.setModule(module.toString());
        runtimeEnvironment.setIsDefault(0);
    }

    private void updateTableRow(boolean isCreate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (isCreate) {
            model.addRow(new Object[]{
                    false,
                    String.valueOf(runtimeEnvironment.getId()),
                    runtimeEnvironment.getName(),
                    runtimeEnvironment.getRemark(),
                    runtimeEnvironment.getCreated().format(formatter),
                    runtimeEnvironment.getUpdated().format(formatter),
            });
            return;
        }
        for (int i = 0; i < model.getRowCount(); i++) {
            int currentId = Integer.parseInt(String.valueOf(model.getValueAt(i, 1)));
            if (Objects.equals(runtimeEnvironment.getId(), currentId)) {
                model.setValueAt(runtimeEnvironment.getName(), i, 2);
                model.setValueAt(runtimeEnvironment.getRemark(), i, 3);
                model.setValueAt(runtimeEnvironment.getUpdated().format(formatter), i, 5);
            }
        }
    }

    private void syncPanelTextFields() {
        envTextField.setText(runtimeEnvironment.getEnvValue());
        vmTextField.setText(runtimeEnvironment.getVmValue());
        argsTextField.setText(runtimeEnvironment.getArgsValue());
    }

    /** 重新加载环境列表并刷新下拉框, 数据库访问在后台线程完成。 */
    private void refreshComboBox() {
        RuntimeEnvironment selection = runtimeEnvironmentComboBox.getSelection();
        Integer selectionId = selection == null ? null : selection.getId();
        AsyncLoader.loadThenOnEdt(
                () -> RuntimeEnvironmentService.execute(service -> {
                    List<RuntimeEnvironment> environments = service.getRuntimeEnvironments(project, module);
                    if (environments.isEmpty()) {
                        return null;
                    }
                    RuntimeEnvironment selected = environments.stream()
                            .filter(item -> Objects.equals(item.getId(), selectionId))
                            .findFirst().orElse(environments.get(0));
                    // 保持用户原有的启用/禁用状态, 只更新选中的环境
                    service.updateSelectEnv(selected.getId());
                    return new Object[]{environments, selected};
                }),
                loaded -> {
                    if (loaded == null) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    List<RuntimeEnvironment> environments = (List<RuntimeEnvironment>) loaded[0];
                    runtimeEnvironmentComboBox.setItems(environments, (RuntimeEnvironment) loaded[1]);
                });
    }


    private JComponent createPaddingPanel(int padding) {
        JPanel panel = new JPanel();
        panel.setBorder(JBUI.Borders.empty(padding));
        return panel;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new VerticalLayout());
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            JBTextField textField = new JBTextField();
            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    runtimeEnvironment.setName(textField.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    runtimeEnvironment.setName(textField.getText());
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    runtimeEnvironment.setName(textField.getText());
                }
            });
            Optional.ofNullable(runtimeEnvironment.getName()).ifPresent(textField::setText);
            namePanel.add(new JLabel("环境名称: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        panel.add(createPaddingPanel(5));
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            JBTextField textField = new JBTextField();
            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    runtimeEnvironment.setRemark(textField.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    runtimeEnvironment.setRemark(textField.getText());
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    runtimeEnvironment.setRemark(textField.getText());
                }
            });
            Optional.ofNullable(runtimeEnvironment.getRemark()).ifPresent(textField::setText);
            namePanel.add(new JLabel("环境描述: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        panel.add(createPaddingPanel(5));
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            MultiLanguageTextField textField = createTextField(PlainTextFileType.INSTANCE, false, true);
            textField.getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                @Override
                public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
                    runtimeEnvironment.setVmValue(textField.getText());
                }
            });
            Optional.ofNullable(runtimeEnvironment.getVmValue()).ifPresent(textField::setText);
            textField.setPreferredSize(new Dimension(0, 180));
            namePanel.add(new JLabel("VM参数: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        panel.add(createPaddingPanel(5));
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            MultiLanguageTextField textField = createTextField(PropertiesFileType.INSTANCE, false, true);
            textField.getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                @Override
                public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
                    runtimeEnvironment.setArgsValue(textField.getText());
                }
            });
            Optional.ofNullable(runtimeEnvironment.getArgsValue()).ifPresent(textField::setText);
            textField.setPreferredSize(new Dimension(0, 180));
            namePanel.add(new JLabel("ARGS参数: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        panel.add(createPaddingPanel(5));
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            MultiLanguageTextField textField = createTextField(PropertiesFileType.INSTANCE, false, true);
            textField.getDocument().addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                @Override
                public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
                    runtimeEnvironment.setEnvValue(textField.getText());
                }
            });
            Optional.ofNullable(runtimeEnvironment.getEnvValue()).ifPresent(textField::setText);
            textField.setPreferredSize(new Dimension(0, 180));
            namePanel.add(new JLabel("环境变量: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        return new JBScrollPane(panel);
    }

    public MultiLanguageTextField createTextField(LanguageFileType fileType, boolean oneLineMode, boolean lineNumbersShown) {
        MultiLanguageTextField textField = new MultiLanguageTextField(fileType, project, "", lineNumbersShown, false, oneLineMode, ex -> {
            return Unit.INSTANCE;
        });
        Disposer.register(myDisposable, textField);
        return textField;
    }
}
