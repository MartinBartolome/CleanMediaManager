package com.cleanmediamanager.ui;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.model.MovieMatch;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Modal dialog that lets the user search TMDB for a movie by name and pick
 * one result. The caller receives the selected {@link MovieMatch} (or null
 * when the dialog is cancelled) via {@link #showAndWait()}.
 */
public class MovieManualSearchDialog extends JDialog {

    private final TmdbClient client;
    private MovieMatch selectedMatch = null;
    private final MovieMatch initialSelected;

    private final JTextField searchField;
    private final JButton searchButton;
    private final JList<MovieMatch> resultList;
    private final DefaultListModel<MovieMatch> listModel;
    private final JButton okButton;
    private final JLabel statusLabel;

    public MovieManualSearchDialog(JFrame parent, TmdbClient client, String initialQuery, String initialFilename, MovieMatch initialSelected) {
        super(parent, "Manuell suchen (Film)", true);
        this.client = client;
        this.initialSelected = initialSelected;

        setSize(520, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        JPanel searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        searchField = new JTextField(initialQuery != null ? initialQuery : "");
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchButton = new JButton("Suchen");
        searchPanel.add(new JLabel("Filmname: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        JLabel fileLabel = new JLabel("Datei: " + (initialFilename != null ? initialFilename : ""));
        fileLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        searchPanel.add(fileLabel, BorderLayout.SOUTH);
        add(searchPanel, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultList.setCellRenderer(new MovieMatchRenderer());
        JScrollPane scroll = new JScrollPane(resultList);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 8, 0, 8),
                BorderFactory.createTitledBorder("Suchergebnisse")));
        add(scroll, BorderLayout.CENTER);

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

        searchButton.addActionListener(e -> performSearch());
        searchField.addActionListener(e -> performSearch());

        resultList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                okButton.setEnabled(resultList.getSelectedValue() != null);
            }
        });

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

        SwingWorker<List<MovieMatch>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<MovieMatch> doInBackground() throws Exception {
                return client.searchMovie(query, null).get();
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                try {
                    List<MovieMatch> results = get();
                    listModel.clear();
                    for (MovieMatch mm : results) {
                        listModel.addElement(mm);
                    }
                    if (results.isEmpty()) {
                        statusLabel.setText("Keine Ergebnisse für: \"" + query + "\"");
                    } else {
                        statusLabel.setText(results.size() + " Ergebnis(se) gefunden.");
                        if (initialSelected != null) {
                            int found = -1;
                            for (int i = 0; i < results.size(); i++) {
                                if (results.get(i).getId() == initialSelected.getId()) { found = i; break; }
                            }
                            if (found >= 0) {
                                resultList.setSelectedIndex(found);
                            } else {
                                listModel.add(0, initialSelected);
                                resultList.setSelectedIndex(0);
                            }
                        } else {
                            resultList.setSelectedIndex(0);
                        }
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

    public MovieMatch showAndWait() {
        setVisible(true);
        return selectedMatch;
    }

    private static class MovieMatchRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MovieMatch mm) {
                String year = mm.getYear() != null && !mm.getYear().isEmpty() ? " (" + mm.getYear() + ")" : "";
                String overview = mm.getOverview() != null && !mm.getOverview().isBlank()
                        ? " — " + (mm.getOverview().length() > 80
                                ? mm.getOverview().substring(0, 80) + "…"
                                : mm.getOverview())
                        : "";
                setText("<html><b>" + mm.getTitle() + "</b>" + year + "<br>"
                        + "<small style='color:gray'>" + overview + "</small></html>");
            }
            return this;
        }
    }
}
