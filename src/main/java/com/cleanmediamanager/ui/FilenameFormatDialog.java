package com.cleanmediamanager.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.prefs.Preferences;

public class FilenameFormatDialog extends JDialog {

    private final Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
    private final JTextField movieField = new JTextField();
    private final JTextField episodeField = new JTextField();
    private final JLabel moviePreview = new JLabel();
    private final JLabel episodePreview = new JLabel();

    public FilenameFormatDialog(Frame owner) {
        super(owner, "Filename Format", true);
        setLayout(new BorderLayout(8,8));
        setPreferredSize(new Dimension(700, 420));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Movie", buildMoviePanel());
        tabs.addTab("Series/Episode", buildEpisodePanel());

        add(tabs, BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);

        loadValues();
        attachListeners();
        updatePreviews();
    }

    private JPanel buildMoviePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        p.add(new JLabel("Template:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(movieField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        p.add(new JLabel("Preview:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        moviePreview.setBorder(BorderFactory.createEtchedBorder());
        p.add(moviePreview, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1;
        p.add(buildHintPanelForMovie(), gbc);
        return p;
    }

    private JPanel buildEpisodePanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        p.add(new JLabel("Template:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(episodeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        p.add(new JLabel("Preview:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        episodePreview.setBorder(BorderFactory.createEtchedBorder());
        p.add(episodePreview, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1;
        p.add(buildHintPanelForEpisode(), gbc);
        return p;
    }

    private JPanel buildHintPanelForMovie() {
        String html = "<html>Platzhalter: <b>{title}</b>, <b>{year}</b>, <b>{ext}</b><br>" +
                "Beispiele: {title} ({year}){ext}  →  'Die Sendung (2020).mkv'<br>" +
                "Sie können numerische Padding-Angaben verwenden: z.B. {season:02d}</html>";
        JLabel l = new JLabel(html);
        JPanel p = new JPanel(new BorderLayout());
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildHintPanelForEpisode() {
        String html = "<html>Platzhalter: <b>{series}</b>, <b>{season}</b>, <b>{episode}</b>, <b>{title}</b>, <b>{ext}</b><br>" +
                "Padding: {season:02d} erzeugt z.B. '01' für Staffel 1.<br>" +
                "Beispiel-Template: {series} - S{season:02d}E{episode:02d} - {title}{ext}</html>";
        JLabel l = new JLabel(html);
        JPanel p = new JPanel(new BorderLayout());
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBottomPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(this::onOk);
        cancel.addActionListener(e -> setVisible(false));
        p.add(cancel);
        p.add(ok);
        return p;
    }

    private void loadValues() {
        movieField.setText(node.get("format.movie", "{title} ({year}){ext}"));
        episodeField.setText(node.get("format.episode", "{series} - S{season:02d}E{episode:02d} - {title}{ext}"));
    }

    private void attachListeners() {
        DocumentListener dl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updatePreviews(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePreviews(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePreviews(); }
        };
        movieField.getDocument().addDocumentListener(dl);
        episodeField.getDocument().addDocumentListener(dl);
    }

    private void updatePreviews() {
        // Sample values for live preview
        String movieTemplate = movieField.getText();
        String episodeTemplate = episodeField.getText();
        String sampleMovie = movieTemplate
                .replace("{title}", "Die große Reise")
                .replace("{year}", "2021")
                .replace("{ext}", ".mkv");
        String sampleEpisode = episodeTemplate
                .replace("{series}", "Meine Serie")
                .replace("{season}", "1")
                .replace("{episode}", "2")
                .replace("{title}", "Pilotfolge")
                .replace("{ext}", ".mkv");
        // rudimentary padding handling for preview
        sampleEpisode = sampleEpisode.replaceAll("\\{season:02d\\}", "01");
        sampleEpisode = sampleEpisode.replaceAll("\\{episode:02d\\}", "02");

        moviePreview.setText(sampleMovie);
        episodePreview.setText(sampleEpisode);
    }

    private void onOk(ActionEvent e) {
        node.put("format.movie", movieField.getText());
        node.put("format.episode", episodeField.getText());
        setVisible(false);
    }

    public void showDialog() {
        setVisible(true);
    }
}
