package com.lhstack.env.dialog;

import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.lhstack.env.service.RuntimeEnvironment;
import com.lhstack.env.service.RuntimeEnvironmentService;
import com.lhstack.tools.plugins.Logger;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class EnvSettingDialog extends DialogWrapper {
    private final AbstractComboBoxAction<RuntimeEnvironment> runtimEnvironmentComboBox;
    private final Project project;
    private final Logger logger;

    private DefaultTableModel model;

    public EnvSettingDialog(Logger logger, Project project, Module module, AbstractComboBoxAction<RuntimeEnvironment> comboBox) {
        super(project, false);
        this.runtimEnvironmentComboBox = comboBox;
        this.setSize(800, 600);
        this.setTitle("环境列表");
        this.setAutoAdjustable(false);
        this.project = project;
        this.logger = logger;
        RuntimeEnvironmentService.getService(service -> {
            List<RuntimeEnvironment> runtimeEnvironments = service.getRuntimeEnvironments(project, module);
            Object[][] array = runtimeEnvironments.stream().map(item -> new Object[]{
                    false,
                    String.valueOf(item.getId()),
                    item.getName(),
                    item.getRemark(),
                    item.getCreated().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    item.getUpdated().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }).toArray(Object[][]::new);
            model = new DefaultTableModel(array, new Object[]{
                    "选择",
                    "ID",
                    "环境名称",
                    "描述",
                    "创建时间",
                    "更新时间"
            });
        });
        this.init();
    }

    @Override
    protected Action[] createActions() {
        return new Action[0];
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        class CenterCheckBoxRenderer extends JCheckBox implements TableCellRenderer {
            public CenterCheckBoxRenderer() {
                setHorizontalAlignment(JLabel.CENTER);
                setOpaque(true); // 背景不透明以便显示选中行颜色
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                setSelected((value != null && (Boolean) value));
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                return this;
            }
        }

        class CheckBoxCellEditor extends AbstractCellEditor implements TableCellEditor {
            private final JCheckBox checkBox;

            public CheckBoxCellEditor() {
                checkBox = new JCheckBox();
                checkBox.setHorizontalAlignment(JCheckBox.CENTER);
                // 设置双击才开始编辑:cite[10]
            }

            @Override
            public Object getCellEditorValue() {
                return checkBox.isSelected();
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                                                         boolean isSelected, int row, int column) {
                checkBox.setSelected((Boolean) value);
                checkBox.setBackground(table.getSelectionBackground());
                return checkBox;
            }
        }

        class CenterLabelRenderer extends JLabel implements TableCellRenderer {

            private final boolean hasToolTipText;

            public CenterLabelRenderer(boolean hasToolTipText) {
                this.setHorizontalAlignment(JLabel.CENTER);
                this.setBackground(Color.LIGHT_GRAY); // 灰色背景提示不可编辑
                this.setForeground(Color.LIGHT_GRAY);
                this.hasToolTipText = hasToolTipText;
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                this.setText((String) value);
                if (hasToolTipText) {
                    this.setToolTipText((String) value);
                }
                return this;
            }
        }

        class NoCellEditor extends AbstractTableCellEditor {


            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                return null;
            }

            @Override
            public Object getCellEditorValue() {
                return null;
            }

            @Override
            public boolean isCellEditable(EventObject e) {
                return false;
            }
        }


        JTable jbTable = new JTable(model);
        JTableHeader tableHeader = jbTable.getTableHeader();
        tableHeader.setReorderingAllowed(false);
        TableColumn column = tableHeader.getColumnModel().getColumn(0);
        AtomicBoolean bool = new AtomicBoolean(false);
        column.setHeaderValue(bool.get());
        column.setHeaderRenderer(new CenterCheckBoxRenderer());

        tableHeader.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = tableHeader.columnAtPoint(e.getPoint());
                if (i == 0) {
                    bool.set(!bool.get());
                    column.setHeaderValue(bool.get());
                    jbTable.clearSelection();
                    TableCellEditor cellEditor = jbTable.getCellEditor();
                    if(jbTable.isEditing()){
                        cellEditor.stopCellEditing();
                    }
                    for (int j = 0; j < jbTable.getRowCount(); j++) {
//                        jbTable.setValueAt(bool.get(), j, 0);
                        model.setValueAt((Boolean) bool.get(), j, 0);
                    }
                    tableHeader.validate();
                    tableHeader.repaint();
                }
            }
        });

        jbTable.getColumnModel().getColumn(0).setMaxWidth(40);
        jbTable.getColumnModel().getColumn(0).setCellRenderer(new CenterCheckBoxRenderer());
        jbTable.getColumnModel().getColumn(0).setCellEditor(new CheckBoxCellEditor());
        TableColumn one = jbTable.getColumnModel().getColumn(1);
        one.setMaxWidth(35);
        one.setMinWidth(35);
        one.setCellRenderer(new CenterLabelRenderer(false));
        one.setCellEditor(new NoCellEditor());
        TableColumn two = jbTable.getColumnModel().getColumn(2);
        two.setMaxWidth(120);
        two.setMinWidth(120);
        two.setCellRenderer(new CenterLabelRenderer(false));
        two.setCellEditor(new NoCellEditor());

        TableColumn three = jbTable.getColumnModel().getColumn(3);
//        three.setMaxWidth(180);
//        three.setMinWidth(180);
        three.setCellRenderer(new CenterLabelRenderer(true));
        three.setCellEditor(new NoCellEditor());

        TableColumn four = jbTable.getColumnModel().getColumn(4);
        four.setMaxWidth(160);
        four.setMinWidth(160);
        four.setCellRenderer(new CenterLabelRenderer(false));
        four.setCellEditor(new NoCellEditor());

        TableColumn five = jbTable.getColumnModel().getColumn(5);
        five.setMaxWidth(160);
        five.setMinWidth(160);
        five.setCellRenderer(new CenterLabelRenderer(false));
        five.setCellEditor(new NoCellEditor());

        return new JBScrollPane(jbTable);
    }
}
