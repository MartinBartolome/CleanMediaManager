package com.cleanmediamanager.ui;

import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MatchStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
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
    private Consumer<Integer> onRemoveRow;
    private Consumer<MediaFile> onManualSearch;

    public void setOnRemoveRow(Consumer<Integer> onRemoveRow) {
        this.onRemoveRow = onRemoveRow;
    }

    public void setOnManualSearch(Consumer<MediaFile> onManualSearch) {
        this.onManualSearch = onManualSearch;
    }

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

        // Context menus
        setupContextMenu(leftTable, leftModel, true);
        setupContextMenu(rightTable, rightModel, false);

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

    private void setupContextMenu(JTable table, FileTableModel model, boolean isLeft) {
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) { handlePopup(e); }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) { handlePopup(e); }

            private void handlePopup(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                table.setRowSelectionInterval(row, row);

                MediaFile file = model.getFileAt(row);
                if (file == null) return;

                JPopupMenu menu = new JPopupMenu();

                // --- Open actions ---
                JMenuItem openFolder = new JMenuItem("Open Containing Folder");
                openFolder.addActionListener(ae -> {
                    try {
                        new ProcessBuilder("xdg-open",
                                file.getPath().getParent().toAbsolutePath().toString())
                                .start();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(table,
                                "Ordner konnte nicht geöffnet werden:\n" + ex.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                });
                menu.add(openFolder);

                if (isLeft) {
                    JMenuItem openFile = new JMenuItem("Open File");
                    openFile.addActionListener(ae -> {
                        try {
                            new ProcessBuilder("xdg-open",
                                    file.getPath().toAbsolutePath().toString())
                                    .start();
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(table,
                                    "Datei konnte nicht geöffnet werden:\n" + ex.getMessage(),
                                    "Fehler", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    menu.add(openFile);
                }

                menu.addSeparator();

                // --- Clipboard actions ---
                String displayName = isLeft
                        ? file.getOriginalName()
                        : (file.getNewName() != null ? file.getNewName() : file.getOriginalName());

                JMenuItem copyName = new JMenuItem("Copy Filename");
                copyName.addActionListener(ae ->
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(displayName), null));
                menu.add(copyName);

                JMenuItem copyPath = new JMenuItem("Copy Full Path");
                copyPath.addActionListener(ae ->
                        Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new StringSelection(
                                        file.getPath().toAbsolutePath().toString()), null));
                menu.add(copyPath);

                menu.addSeparator();

                // --- Manual search (always available) ---
                JMenuItem manualSearch = new JMenuItem("\uD83D\uDD0D Manuell suchen\u2026");
                manualSearch.addActionListener(ae -> {
                    if (onManualSearch != null) onManualSearch.accept(file);
                });
                menu.add(manualSearch);

                menu.addSeparator();

                // --- List management ---
                JMenuItem removeItem = new JMenuItem("Remove from List");
                removeItem.addActionListener(ae -> {
                    if (onRemoveRow != null) onRemoveRow.accept(row);
                });
                menu.add(removeItem);

                menu.show(table, e.getX(), e.getY());
            }
        });
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
