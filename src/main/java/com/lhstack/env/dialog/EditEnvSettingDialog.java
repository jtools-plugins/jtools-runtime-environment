package com.lhstack.env.dialog;

import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Optional;

public class EditEnvSettingDialog extends DialogWrapper {

    private final JTable table;
    private final AbstractComboBoxAction<RuntimeEnvironment> runtimeEnvironmentComboBox;
    private final Project project;
    private RuntimeEnvironment runtimeEnvironment;

    public EditEnvSettingDialog(Project project, JTable table, AbstractComboBoxAction<RuntimeEnvironment> comboBox,
                                Integer id) {
        super(project, true);
        this.table = table;
        this.project = project;
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
                new  AbstractAction("保存") {

                    @Override
                    public void actionPerformed(ActionEvent e) {

                    }
                },
                new AbstractAction("取消") {
                    @Override
                    public void actionPerformed(ActionEvent e) {

                    }
                }
        };
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
            Optional.ofNullable(runtimeEnvironment.getName()).ifPresent(textField::setText);
            namePanel.add(new JLabel("环境名称: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        panel.add(createPaddingPanel(5));
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            JBTextField textField = new JBTextField();
            Optional.ofNullable(runtimeEnvironment.getRemark()).ifPresent(textField::setText);
            namePanel.add(new JLabel("环境描述: ", JLabel.LEFT), BorderLayout.NORTH);
            namePanel.add(textField, BorderLayout.CENTER);
            panel.add(namePanel);
        }
        panel.add(createPaddingPanel(5));
        {
            JPanel namePanel = new JPanel(new BorderLayout());
            MultiLanguageTextField textField = createTextField(PlainTextFileType.INSTANCE, false, true);
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
