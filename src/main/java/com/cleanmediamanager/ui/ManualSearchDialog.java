package com.cleanmediamanager.ui;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.model.SeriesMatch;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Modal dialog that lets the user search TMDB for a series by name and pick
 * one result. The caller receives the selected {@link SeriesMatch} (or null
 * when the dialog is cancelled) via {@link #showAndWait()}.
 */
public class ManualSearchDialog extends JDialog {

    private final TmdbClient client;
    private SeriesMatch selectedMatch = null;

    private final JTextField searchField;
    private final JButton searchButton;
    private final JList<SeriesMatch> resultList;
    private final DefaultListModel<SeriesMatch> listModel;
    private final JButton okButton;
    private final JLabel statusLabel;

    public ManualSearchDialog(JFrame parent, TmdbClient client, String initialQuery) {
        super(parent, "Manuell suchen", true);
        this.client = client;

        setSize(520, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        // ── Top: search row ─────────────────────────────────────────────────
        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        searchField = new JTextField(initialQuery != null ? initialQuery : "");
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchButton = new JButton("Suchen");
        searchPanel.add(new JLabel("Serienname: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        add(searchPanel, BorderLayout.NORTH);

        // ── Center: result list ──────────────────────────────────────────────
        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultList.setCellRenderer(new SeriesMatchRenderer());
        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 8, 0, 8),
                BorderFactory.createTitledBorder("Suchergebnisse")));
        add(scroll, BorderLayout.CENTER);

        // ── Bottom: status label + OK / Cancel ───────────────────────────────
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));

        okButton = new JButton("Anwenden");
        okButton.setEnabled(false);
        JButton cancelButton = new JButton("Abbrechen");

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonRow.add(okButton);
        buttonRow.add(cancelButton);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(buttonRow, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // ── Wiring ───────────────────────────────────────────────────────────
        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());

        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                okButton.setEnabled(resultList.getSelectedValue() != null);
            }
        });

        // Double-click accepts immediately
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && resultList.getSelectedValue() != null) {
                    accept();
                }
            }
        });

        okButton.addActionListener(e -> accept());
        cancelButton.addActionListener(e -> dispose());

        getRootPane().setDefaultButton(searchButton);

        // Kick off search straight away when there is a meaningful initial query
        if (initialQuery != null && !initialQuery.isBlank()) {
            performSearch();
        }
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isBlank()) return;

        searchButton.setEnabled(false);
        okButton.setEnabled(false);
        listModel.clear();
        statusLabel.setText("Suche läuft…");

        SwingWorker<List<SeriesMatch>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SeriesMatch> doInBackground() throws Exception {
                return client.searchSeries(query, null).get();
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                try {
                    List<SeriesMatch> results = get();
                    listModel.clear();
                    for (SeriesMatch sm : results) {
                        listModel.addElement(sm);
                    }
                    if (results.isEmpty()) {
                        statusLabel.setText("Keine Ergebnisse für: \"" + query + "\"");
                    } else {
                        statusLabel.setText(results.size() + " Ergebnis(se) gefunden.");
                        resultList.setSelectedIndex(0);
                        resultList.requestFocusInWindow();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusLabel.setText("Fehler: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void accept() {
        selectedMatch = resultList.getSelectedValue();
        if (selectedMatch != null) {
            dispose();
        }
    }

    /**
     * Displays the dialog and blocks until the user accepts or cancels.
     *
     * @return the selected {@link SeriesMatch}, or {@code null} if cancelled
     */
    public SeriesMatch showAndWait() {
        setVisible(true); // blocks
        return selectedMatch;
    }

    // ── Cell renderer showing name + year + overview snippet ──────────────────
    private static class SeriesMatchRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SeriesMatch sm) {
                String year = sm.getFirstAirYear() != null && !sm.getFirstAirYear().isEmpty()
                        ? " (" + sm.getFirstAirYear() + ")" : "";
                String overview = sm.getOverview() != null && !sm.getOverview().isBlank()
                        ? " — " + (sm.getOverview().length() > 80
                                ? sm.getOverview().substring(0, 80) + "…"
                                : sm.getOverview())
                        : "";
                setText("<html><b>" + sm.getName() + "</b>" + year + "<br>"
                        + "<small style='color:gray'>" + overview + "</small></html>");
            }
            return this;
        }
    }
}
