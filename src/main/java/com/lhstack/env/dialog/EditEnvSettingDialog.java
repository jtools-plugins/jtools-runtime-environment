package com.lhstack.env.dialog;

import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.lhstack.env.service.RuntimeEnvironment;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class EditEnvSettingDialog extends DialogWrapper {

    private final JTable table;
    private final AbstractComboBoxAction<RuntimeEnvironment> runtimeEnvironmentComboBox;

    public EditEnvSettingDialog(Project project, JTable table, AbstractComboBoxAction<RuntimeEnvironment> comboBox,
                                Integer id) {
        super(project, true);
        this.table = table;
        this.runtimeEnvironmentComboBox = comboBox;
        this.setTitle(id != null ? "更新环境" : "新增环境");
        this.setSize(800,600);
        this.setAutoAdjustable(false);
        this.init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return new JButton("测试");
    }
}
