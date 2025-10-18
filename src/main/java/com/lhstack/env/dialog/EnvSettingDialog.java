package com.lhstack.env.dialog;

import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.lhstack.data.component.MultiLanguageTextField;
import com.lhstack.env.service.RuntimeEnvironment;
import com.lhstack.env.service.RuntimeEnvironmentService;
import com.lhstack.tools.plugins.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class EnvSettingDialog extends DialogWrapper {
    private final AbstractComboBoxAction<RuntimeEnvironment> runtimeEnvironmentComboBox;
    private final Project project;
    private final Logger logger;
    private final JBTable jbTable;
    private final Module module;
    private final MultiLanguageTextField vmTextField;
    private final MultiLanguageTextField argsTextField;
    private final MultiLanguageTextField envTextField;

    private DefaultTableModel model;

    public EnvSettingDialog(Logger logger, Project project, Module module, AbstractComboBoxAction<RuntimeEnvironment> comboBox, @NotNull MultiLanguageTextField vmTextField, @NotNull MultiLanguageTextField argsTextField, @NotNull MultiLanguageTextField envTextField) {
        super(project, false);
        this.runtimeEnvironmentComboBox = comboBox;
        this.setSize(1000, 600);
        this.setTitle("环境列表");
        this.setAutoAdjustable(false);
        this.project = project;
        this.logger = logger;
        this.module = module;
        this.vmTextField = vmTextField;
        this.argsTextField = argsTextField;
        this.envTextField = envTextField;
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
                    "更新时间",
                    "操作",
            });
        });
        this.jbTable = new JBTable(model);
        this.init();
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{
                new AbstractAction("新增") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        new EditEnvSettingDialog(project,model,module,jbTable,runtimeEnvironmentComboBox,null, vmTextField, argsTextField, envTextField).show();
                    }
                },
                new AbstractAction("删除") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        List<Integer> delIds = new ArrayList<>();
                        for (int i = 0; i < model.getRowCount(); i++) {
                            boolean isSelect = Boolean.parseBoolean(String.valueOf(model.getValueAt(i, 0)));
                            if(isSelect){
                                Integer id = Integer.parseInt(String.valueOf(model.getValueAt(i, 1)));
                                delIds.add(id);
                            }
                        }
                        if(delIds.isEmpty()){
                            Messages.showWarningDialog("请先选择要删除的数据","提示");
                        }else {
                            int isOk = Messages.showOkCancelDialog("确定删除吗?", "警告", "确定", "取消", AllIcons.General.Warning);
                            if(isOk == Messages.OK){
                                for (Integer delId : delIds) {
                                    for (int i = 0; i < model.getRowCount(); i++) {
                                        Integer id = Integer.parseInt(String.valueOf(model.getValueAt(i, 1)));
                                        if(delId.equals(id)){
                                            model.removeRow(i);
                                        }
                                    }
                                }
                                RuntimeEnvironmentService.execute(service -> service.removeBatchByIds(delIds));
                                refreshComboBox();
                            }
                        }
                    }
                }

        };
    }

    private void refreshComboBox() {
        RuntimeEnvironmentService.getService(service -> {
            List<RuntimeEnvironment> runtimeEnvironments = service.getRuntimeEnvironments(project, module);
            RuntimeEnvironment selection = runtimeEnvironmentComboBox.getSelection();
            RuntimeEnvironment selectionRuntimeEnvironment = runtimeEnvironments.stream().filter(item -> item.getId().equals(selection.getId())).findFirst().orElseGet(() -> runtimeEnvironments.get(0));
            runtimeEnvironmentComboBox.setItems(runtimeEnvironments,selectionRuntimeEnvironment);
            SwingUtilities.invokeLater(() -> {
                envTextField.setText(selectionRuntimeEnvironment.getEnvValue());
                vmTextField.setText(selectionRuntimeEnvironment.getVmValue());
                argsTextField.setText(selectionRuntimeEnvironment.getArgsValue());
            });
            service.updateActive(selectionRuntimeEnvironment,true);
        });
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
                if(row >= 0){
                    Integer id = Integer.parseInt(String.valueOf(table.getValueAt(row, 1)));
                    RuntimeEnvironment runtimeEnvironment = RuntimeEnvironmentService.execute(service -> service.getById(id));
                    if(runtimeEnvironment.getIsDefault() == 1){
                        this.setEnabled(false);
                        setSelected(false);
                    }else {
                        this.setEnabled(true);
                        setSelected((value != null && (Boolean) value));
                        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                        setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                    }
                }else {
                    setSelected((value != null && (Boolean) value));
                    setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                    setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
                }
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
                if(row >= 0){
                    Integer id = Integer.parseInt(String.valueOf(table.getValueAt(row, 1)));
                    RuntimeEnvironment runtimeEnvironment = RuntimeEnvironmentService.execute(service -> service.getById(id));
                    if(runtimeEnvironment.getIsDefault() == 1){
                        checkBox.setEnabled(false);
                        checkBox.setSelected(false);
                    }else {
                        checkBox.setEnabled(true);
                        checkBox.setSelected((Boolean) value);
                        checkBox.setBackground(table.getSelectionBackground());
                    }
                }else {
                    checkBox.setSelected((Boolean) value);
                    checkBox.setBackground(table.getSelectionBackground());
                }
                return checkBox;
            }
        }

        class CenterLabelRenderer extends JLabel implements TableCellRenderer {

            private final boolean hasToolTipText;

            public CenterLabelRenderer(boolean hasToolTipText) {
                this.setHorizontalAlignment(JLabel.CENTER);
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

        jbTable.setCellSelectionEnabled(false);
        JTableHeader tableHeader = jbTable.getTableHeader();
        DefaultTableCellRenderer tableCellRenderer = new  DefaultTableCellRenderer();
        tableCellRenderer.setHorizontalAlignment(JLabel.CENTER);
        tableHeader.setDefaultRenderer(tableCellRenderer);
        tableHeader.setReorderingAllowed(false);
        jbTable.setRowHeight(40);
        TableColumn column = tableHeader.getColumnModel().getColumn(0);
        AtomicBoolean bool = new AtomicBoolean(false);
        column.setHeaderValue(bool.get());
        column.setHeaderRenderer(new CenterCheckBoxRenderer());
//        tableHeader.getColumnModel().getColumns().asIterator().forEachRemaining(item -> {
//            item.setHeaderRenderer(new CenterLabelRenderer(false));
//        });
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
                        Integer id = Integer.parseInt(String.valueOf(model.getValueAt(j,1)));
                        RuntimeEnvironment runtimeEnvironment = RuntimeEnvironmentService.execute(service -> service.getById(id));
                        if(runtimeEnvironment.getIsDefault() == 0){
                            model.setValueAt(bool.get(), j, 0);
                        }
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


        TableColumn six = jbTable.getColumnModel().getColumn(6);
        six.setMaxWidth(157);
        six.setMinWidth(157);
        six.setCellRenderer(new TableCellRenderer() {

            private final JButton editButton = new  JButton("编辑");

            private final JButton deleteButton = new JButton("删除");

            private final JPanel panel = new JPanel();

            {
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                panel.add(deleteButton);
                panel.add(editButton);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                RuntimeEnvironmentService.getService(service -> {
                    RuntimeEnvironment environment = service.getById(String.valueOf(table.getValueAt(row, 1)));
                    if(environment != null && environment.getIsDefault() == 1){
                        deleteButton.setEnabled(false);
                        deleteButton.setToolTipText("默认环境不可删除");
                    }else {
                        deleteButton.setEnabled(true);
                    }
                });
                return panel;
            }
        });
        six.setCellEditor(new AbstractTableCellEditor() {
            private final JButton editButton = new  JButton("编辑");

            private final JButton deleteButton = new JButton("删除");

            private final JPanel panel = new JPanel();

            private final AtomicInteger id = new AtomicInteger();

            private final AtomicInteger currentRow = new AtomicInteger();

            {
                editButton.addActionListener(e -> {
                    if (jbTable.isEditing()) {
                        jbTable.getCellEditor().stopCellEditing();
                    }
                    new EditEnvSettingDialog(project, model, module,jbTable,runtimeEnvironmentComboBox,id.get(),vmTextField,argsTextField,envTextField).show();
                });

                deleteButton.addActionListener(e -> {
                    int okCancel = Messages.showOkCancelDialog("确认要删除吗", "警告", "确认", "取消", AllIcons.General.Warning);
                    if(Messages.OK == okCancel){
                        if (jbTable.isEditing()) {
                            jbTable.getCellEditor().stopCellEditing();
                        }
                        model.removeRow(currentRow.get());
                        RuntimeEnvironmentService.getService(service -> {
                            service.removeById(id.get());
                        });
                        refreshComboBox();
                    }
                });
                panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                panel.add(deleteButton);
                panel.add(editButton);
            }

            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                RuntimeEnvironmentService.getService(service -> {
                    RuntimeEnvironment environment = service.getById(String.valueOf(table.getValueAt(row, 1)));
                    if(environment != null && environment.getIsDefault() == 1){
                        deleteButton.setEnabled(false);
                        deleteButton.setToolTipText("默认环境不可删除");
                    }else {
                        deleteButton.setEnabled(true);
                    }
                    id.set(Integer.parseInt(String.valueOf(table.getValueAt(row,1))));
                    currentRow.set(row);
                });
                return panel;
            }

            @Override
            public Object getCellEditorValue() {
                return null;
            }
        });
        return new JBScrollPane(jbTable);
    }
}
