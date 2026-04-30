package com.cleanmediamanager.ui;

import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MatchStatus;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class FileTableModel extends AbstractTableModel {

    public enum PanelType { LEFT, RIGHT }

    private final PanelType panelType;
    private List<MediaFile> files;

    private static final String[] LEFT_COLUMNS = {"Original Filename", "Status"};
    private static final String[] RIGHT_COLUMNS = {"New Filename", "Confidence", "Status"};

    public FileTableModel(PanelType panelType) {
        this.panelType = panelType;
        this.files = new ArrayList<>();
    }

    public void setFiles(List<MediaFile> files) {
        this.files = files != null ? files : new ArrayList<>();
        fireTableDataChanged();
    }

    public void refresh() {
        fireTableDataChanged();
    }

    public MediaFile getFileAt(int row) {
        if (row >= 0 && row < files.size()) {
            return files.get(row);
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return files.size();
    }

    @Override
    public int getColumnCount() {
        String[] cols = panelType == PanelType.LEFT ? LEFT_COLUMNS : RIGHT_COLUMNS;
        return cols.length;
    }

    @Override
    public String getColumnName(int column) {
        String[] cols = panelType == PanelType.LEFT ? LEFT_COLUMNS : RIGHT_COLUMNS;
        return column < cols.length ? cols[column] : "";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= files.size()) return null;
        MediaFile file = files.get(rowIndex);
        if (panelType == PanelType.LEFT) {
            if (columnIndex == 0) {
                return file.getOriginalName();
            } else {
                return formatStatus(file.getStatus());
            }
        } else {
            // RIGHT panel: 0=newName, 1=confidence, 2=status
            if (columnIndex == 0) {
                return file.getNewName() != null ? file.getNewName() : "\u2014";
            } else if (columnIndex == 1) {
                return formatConfidence(file.getMatchScore());
            } else {
                return formatStatus(file.getStatus());
            }
        }
    }

    private String formatStatus(MatchStatus status) {
        return switch (status) {
            case PENDING -> "\u23F3 Pending";
            case MATCHED -> "\u2705 Matched";
            case UNMATCHED -> "\u26A0\uFE0F No Match";
            case ERROR -> "\u274C Error";
        };
    }

    private String formatConfidence(double score) {
        if (score <= 0.0) return "—";
        double pct = score * 100.0;
        return String.format("%.1f%%", pct);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
