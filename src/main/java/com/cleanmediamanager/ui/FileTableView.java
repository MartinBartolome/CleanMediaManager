package com.cleanmediamanager.ui;

import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MatchStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class FileTableView {

    private final JTable leftTable;
    private final JTable rightTable;
    private final FileTableModel leftModel;
    private final FileTableModel rightModel;
    private final JSplitPane splitPane;

    public FileTableView(Consumer<List<File>> onFilesDropped) {
        leftModel = new FileTableModel(FileTableModel.PanelType.LEFT);
        rightModel = new FileTableModel(FileTableModel.PanelType.RIGHT);

        leftTable = createTable(leftModel);
        rightTable = createTable(rightModel);

        // Link selection between tables
        leftTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = leftTable.getSelectedRow();
                if (row >= 0) {
                    rightTable.setRowSelectionInterval(row, row);
                    scrollToRow(rightTable, row);
                }
            }
        });

        rightTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = rightTable.getSelectedRow();
                if (row >= 0) {
                    leftTable.setRowSelectionInterval(row, row);
                    scrollToRow(leftTable, row);
                }
            }
        });

        // Setup drag and drop on left panel
        if (onFilesDropped != null) {
            setupDragAndDrop(leftTable, onFilesDropped);
        }

        JScrollPane leftScroll = new JScrollPane(leftTable);
        leftScroll.setBorder(BorderFactory.createTitledBorder("Original Files"));

        JScrollPane rightScroll = new JScrollPane(rightTable);
        rightScroll.setBorder(BorderFactory.createTitledBorder("Renamed Files"));

        // Synchronize vertical scrolling between both panels
        boolean[] syncing = {false};
        leftScroll.getVerticalScrollBar().getModel().addChangeListener(e -> {
            if (!syncing[0]) {
                syncing[0] = true;
                rightScroll.getVerticalScrollBar().getModel().setValue(
                        leftScroll.getVerticalScrollBar().getModel().getValue());
                syncing[0] = false;
            }
        });
        rightScroll.getVerticalScrollBar().getModel().addChangeListener(e -> {
            if (!syncing[0]) {
                syncing[0] = true;
                leftScroll.getVerticalScrollBar().getModel().setValue(
                        rightScroll.getVerticalScrollBar().getModel().getValue());
                syncing[0] = false;
            }
        });

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
    }

    private JTable createTable(FileTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(1).setMaxWidth(120);
        table.getColumnModel().getColumn(1).setMinWidth(100);
        table.getColumnModel().getColumn(1).setCellRenderer(new StatusCellRenderer());
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        return table;
    }

    private void scrollToRow(JTable table, int row) {
        Rectangle rect = table.getCellRect(row, 0, true);
        table.scrollRectToVisible(rect);
    }

    private void setupDragAndDrop(JTable table, Consumer<List<File>> onFilesDropped) {
        table.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Transferable t = support.getTransferable();
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    onFilesDropped.accept(files);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });
    }

    public JSplitPane getSplitPane() {
        return splitPane;
    }

    public FileTableModel getLeftModel() {
        return leftModel;
    }

    public FileTableModel getRightModel() {
        return rightModel;
    }

    public JTable getLeftTable() {
        return leftTable;
    }

    public JTable getRightTable() {
        return rightTable;
    }

    // Custom renderer for status column
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected && value instanceof String s) {
                if (s.contains("Matched")) {
                    c.setForeground(new Color(0, 128, 0));
                } else if (s.contains("No Match")) {
                    c.setForeground(new Color(200, 100, 0));
                } else if (s.contains("Error")) {
                    c.setForeground(Color.RED);
                } else {
                    c.setForeground(Color.GRAY);
                }
            } else if (isSelected) {
                c.setForeground(table.getSelectionForeground());
            }
            return c;
        }
    }
}
