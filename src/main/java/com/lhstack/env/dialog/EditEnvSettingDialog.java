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
import com.lhstack.env.service.RuntimeEnvironment;
import com.lhstack.env.service.RuntimeEnvironmentService;
import kotlin.Unit;
import org.jdesktop.swingx.VerticalLayout;
import org.jetbrains.annotations.Nullable;

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

    private final JTable table;
    private final AbstractComboBoxAction<RuntimeEnvironment> runtimeEnvironmentComboBox;
    private final Project project;
    private final Module module;
    private final MultiLanguageTextField vmTextField;
    private final MultiLanguageTextField argsTextField;
    private final MultiLanguageTextField envTextField;
    private final DefaultTableModel model;
    private RuntimeEnvironment runtimeEnvironment;

    public EditEnvSettingDialog(Project project, DefaultTableModel model, Module module, JTable table, AbstractComboBoxAction<RuntimeEnvironment> comboBox,
                                Integer id, MultiLanguageTextField vmTextField, MultiLanguageTextField argsTextField, MultiLanguageTextField envTextField) {
        super(project, true);
        this.table = table;
        this.module = module;
        this.project = project;
        this.vmTextField = vmTextField;
        this.argsTextField = argsTextField;
        this.envTextField = envTextField;
        this.model = model;
        this.runtimeEnvironmentComboBox = comboBox;
        this.setTitle(id != null ? "更新环境" : "新增环境");
        this.setSize(800, 881);
        this.setAutoAdjustable(false);
        if (id != null) {
            runtimeEnvironment = RuntimeEnvironmentService.execute(service -> service.getById(id));
        } else {
            runtimeEnvironment = new RuntimeEnvironment();
        }
        this.init();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{
                new AbstractAction("保存") {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Boolean result = RuntimeEnvironmentService.execute(service -> {
                            Long total = service.lambdaQuery()
                                    .eq(RuntimeEnvironment::getProjectHash, project.getLocationHash())
                                    .eq(RuntimeEnvironment::getModule, module.toString())
                                    .ne(runtimeEnvironment.getId() != null, RuntimeEnvironment::getId, runtimeEnvironment.getId())
                                    .eq(RuntimeEnvironment::getName, runtimeEnvironment.getName()).count();
                            if (total > 0) {
                                Messages.showInfoMessage("环境名字已存在,请修改名字之后再保存或者更新", "提示");
                                return false;
                            } else {
                                runtimeEnvironment.setProjectHash(project.getLocationHash());
                                runtimeEnvironment.setProjectName(project.getName());
                                runtimeEnvironment.setProjectPath(project.getBasePath());
                                runtimeEnvironment.setModule(module.toString());
                                runtimeEnvironment.setIsDefault(0);
                                Integer id = service.getSelectEnvId(project, module);
                                if (Objects.equals(id, runtimeEnvironment.getId())) {
                                    SwingUtilities.invokeLater(() -> {
                                        envTextField.setText(runtimeEnvironment.getEnvValue());
                                        vmTextField.setText(runtimeEnvironment.getVmValue());
                                        argsTextField.setText(runtimeEnvironment.getArgsValue());
                                    });
                                }
                                if (runtimeEnvironment.getId() == null) {
                                    service.save(runtimeEnvironment);
                                    model.addRow(new Object[]{
                                            false,
                                            String.valueOf(runtimeEnvironment.getId()),
                                            runtimeEnvironment.getName(),
                                            runtimeEnvironment.getRemark(),
                                            runtimeEnvironment.getCreated().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                                            runtimeEnvironment.getUpdated().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                                    });
                                } else {
                                    service.updateById(runtimeEnvironment);
                                }

                                doCancelAction();
                            }
                            return true;
                        });
                        if (result) {
                            refreshComboBox();
                        }
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

    private void refreshComboBox() {
        RuntimeEnvironmentService.getService(service -> {
            List<RuntimeEnvironment> runtimeEnvironments = service.getRuntimeEnvironments(project, module);
            RuntimeEnvironment selection = runtimeEnvironmentComboBox.getSelection();
            RuntimeEnvironment selectionRuntimeEnvironment = runtimeEnvironments.stream().filter(item -> item.getId().equals(selection.getId())).findFirst().orElseGet(() -> runtimeEnvironments.get(0));
            runtimeEnvironmentComboBox.setItems(runtimeEnvironments, selectionRuntimeEnvironment);
            service.updateActive(selectionRuntimeEnvironment, true);
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
