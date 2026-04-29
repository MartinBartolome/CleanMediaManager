package com.cleanmediamanager.ui;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.core.FileScanner;
import com.cleanmediamanager.core.FilenameParser;
import com.cleanmediamanager.core.MovieMatcher;
import com.cleanmediamanager.core.RenameService;
import com.cleanmediamanager.core.SeriesMatcher;
import com.cleanmediamanager.model.MatchStatus;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MediaType;
import com.cleanmediamanager.model.SeriesMatch;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Map;
import java.util.HashMap;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class Controller {

    private static final String PREF_API_KEY = "tmdb_api_key";
    private static final String PREF_LANGUAGE = "tmdb_language";

    private final MainWindow mainWindow;
    private final FileScanner fileScanner;
    private final RenameService renameService;
    private final Preferences prefs;

    private List<MediaFile> mediaFiles = new ArrayList<>();
    private FileTableView tableView;
    private MediaType currentMode = MediaType.MOVIE;

    public Controller(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.fileScanner = new FileScanner();
        this.renameService = new RenameService();
        this.prefs = Preferences.userNodeForPackage(Controller.class);
    }

    public void setTableView(FileTableView tableView) {
        this.tableView = tableView;
        tableView.setOnManualSearch(this::handleManualSearch);
    }

    public void onLoadButtonClicked() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Select Media Files or Folder");

        int result = chooser.showOpenDialog(mainWindow.getFrame());
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selected = chooser.getSelectedFiles();
            loadFiles(List.of(selected));
        }
    }

    public void loadFiles(List<File> files) {
        List<MediaFile> loaded = new ArrayList<>();
        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    loaded.addAll(fileScanner.scan(f.toPath()));
                } else if (f.isFile()) {
                    loaded.addAll(fileScanner.scanFile(f.toPath()));
                }
            } catch (Exception e) {
                log("[ERROR] Failed to scan: " + f.getPath() + " - " + e.getMessage());
            }
        }

        if (loaded.isEmpty()) {
            log("[INFO] No media files found in selection.");
            return;
        }

        if (!mediaFiles.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                    "Es sind bereits " + mediaFiles.size() + " Datei(en) geladen.\n" +
                    "Sollen die vorherigen Ergebnisse gelöscht werden?",
                    "Vorherige Ergebnisse löschen?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                log("[INFO] Laden abgebrochen.");
                return;
            }
            if (choice == JOptionPane.YES_OPTION) {
                mediaFiles.clear();
                log("[INFO] Vorherige Ergebnisse gelöscht.");
            }
        }

        mediaFiles.addAll(loaded);
        mediaFiles.sort(Comparator.comparing(MediaFile::getOriginalName, String.CASE_INSENSITIVE_ORDER));
        refreshTables();
        log("[INFO] Loaded " + loaded.size() + " media file(s). Total: " + mediaFiles.size());
    }

    public void onMatchButtonClicked() {
        if (mediaFiles.isEmpty()) {
            log("[WARN] No files loaded. Use Load button first.");
            return;
        }

        String apiKey = prefs.get(PREF_API_KEY, "");
        if (apiKey.isBlank()) {
            log("[WARN] TMDB API key not configured. Open Settings to add your API key.");
            int choice = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                    "TMDB API key is not configured.\nWould you like to open Settings now?",
                    "API Key Required", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                onSettingsButtonClicked();
            }
            return;
        }

        String language = prefs.get(PREF_LANGUAGE, "en-US");
        TmdbClient client = new TmdbClient(apiKey, language);

        log("[INFO] Starting matching for " + mediaFiles.size() + " file(s)...");
        mainWindow.setMatchButtonEnabled(false);

        if (currentMode == MediaType.EPISODE) {
            SeriesMatcher matcher = new SeriesMatcher(client);
            SwingWorker<Void, MediaFile> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    matcher.matchFiles(mediaFiles, file -> publish(file)).get();
                    return null;
                }

                @Override
                protected void process(List<MediaFile> chunks) {
                    tableView.getLeftModel().refresh();
                    tableView.getRightModel().refresh();
                }

                @Override
                protected void done() {
                    try {
                        get();
                        log("[INFO] Matching complete.");
                    } catch (Exception e) {
                        log("[ERROR] Matching failed: " + e.getMessage());
                    } finally {
                        mainWindow.setMatchButtonEnabled(true);
                        tableView.getLeftModel().refresh();
                        tableView.getRightModel().refresh();
                    }
                }
            };
            worker.execute();
        } else {
            MovieMatcher matcher = new MovieMatcher(client);
            SwingWorker<Void, MediaFile> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    matcher.matchFiles(mediaFiles, file -> publish(file)).get();
                    return null;
                }

                @Override
                protected void process(List<MediaFile> chunks) {
                    tableView.getLeftModel().refresh();
                    tableView.getRightModel().refresh();
                }

                @Override
                protected void done() {
                    try {
                        get();
                        log("[INFO] Matching complete.");
                    } catch (Exception e) {
                        log("[ERROR] Matching failed: " + e.getMessage());
                    } finally {
                        mainWindow.setMatchButtonEnabled(true);
                        tableView.getLeftModel().refresh();
                        tableView.getRightModel().refresh();
                    }
                }
            };
            worker.execute();
        }
    }

    public void onRenameButtonClicked() {
        if (mediaFiles.isEmpty()) {
            log("[WARN] No files loaded.");
            return;
        }

        List<String> preview = renameService.previewRename(mediaFiles);
        if (preview.isEmpty()) {
            log("[WARN] No matched files to rename.");
            return;
        }

        StringBuilder msg = new StringBuilder("The following files will be renamed:\n\n");
        int maxShow = Math.min(preview.size(), 10);
        for (int i = 0; i < maxShow; i++) {
            msg.append("  ").append(preview.get(i)).append("\n");
        }
        if (preview.size() > maxShow) {
            msg.append("  ... and ").append(preview.size() - maxShow).append(" more\n");
        }
        msg.append("\nProceed?");

        int choice = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                msg.toString(), "Confirm Rename", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) {
            log("[INFO] Rename cancelled.");
            return;
        }

        final boolean dryRun = false;
        List<String> results = renameService.executeRename(mediaFiles, dryRun);
        for (String line : results) {
            log(line);
        }
        refreshTables();
    }

    public void onSettingsButtonClicked() {
        String currentKey = prefs.get(PREF_API_KEY, "");
        String currentLang = prefs.get(PREF_LANGUAGE, "en-US");

        // General settings panel (API key, language)
        JPanel general = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        general.add(new JLabel("TMDB API Key:"), gbc);
        gbc.gridx = 1;
        JPasswordField keyField = new JPasswordField(currentKey, 30);
        general.add(keyField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        general.add(new JLabel("Language:"), gbc);
        gbc.gridx = 1;
        String[] languages = {"en-US", "de-DE", "fr-FR", "es-ES", "ja-JP"};
        JComboBox<String> langCombo = new JComboBox<>(languages);
        langCombo.setSelectedItem(currentLang);
        general.add(langCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel hint = new JLabel("<html><small>Get a free API key at <a href='#'>themoviedb.org</a></small></html>");
        general.add(hint, gbc);

        // Filename format panel (moved into Settings as a new tab)
        Preferences node = Preferences.userRoot().node("com/cleanmediamanager");
        JPanel fmt = new JPanel(new GridBagLayout());
        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.insets = new Insets(6,6,6,6);
        fgbc.fill = GridBagConstraints.HORIZONTAL;
        fgbc.gridx = 0; fgbc.gridy = 0; fgbc.weightx = 0;
        fmt.add(new JLabel("Movie template:"), fgbc);
        fgbc.gridx = 1; fgbc.weightx = 1;
        JTextField movieField = new JTextField(node.get("format.movie", "{title} ({year}){ext}"));
        fmt.add(movieField, fgbc);

        fgbc.gridx = 0; fgbc.gridy = 1; fgbc.weightx = 0;
        fmt.add(new JLabel("Preview:"), fgbc);
        fgbc.gridx = 1; fgbc.weightx = 1;
        JLabel moviePreview = new JLabel();
        moviePreview.setBorder(BorderFactory.createEtchedBorder());
        fmt.add(moviePreview, fgbc);

        fgbc.gridx = 0; fgbc.gridy = 2; fgbc.weightx = 0;
        fmt.add(new JLabel("Episode template:"), fgbc);
        fgbc.gridx = 1; fgbc.weightx = 1;
        JTextField episodeField = new JTextField(node.get("format.episode", "{series} - S{season:02d}E{episode:02d} - {title}{ext}"));
        fmt.add(episodeField, fgbc);

        fgbc.gridx = 0; fgbc.gridy = 3; fgbc.weightx = 0;
        fmt.add(new JLabel("Preview:"), fgbc);
        fgbc.gridx = 1; fgbc.weightx = 1;
        JLabel episodePreview = new JLabel();
        episodePreview.setBorder(BorderFactory.createEtchedBorder());
        fmt.add(episodePreview, fgbc);

        fgbc.gridx = 0; fgbc.gridy = 4; fgbc.gridwidth = 2; fgbc.weightx = 1;
        String movieHint = "<html>Placeholders: <b>{title}</b>, <b>{year}</b>, <b>{ext}</b><br>" +
            "Example: {title} ({year}){ext}  →  'My Movie (2020).mkv'<br>" +
            "You can include a single '/' to rename the parent folder and file: e.g. {title}/{title}{ext} (only one folder level).</html>";
        String episodeHint = "<html>Placeholders: <b>{series}</b>, <b>{season}</b>, <b>{episode}</b>, <b>{title}</b>, <b>{ext}</b><br>" +
            "Padding: use e.g. {season:02d} to get '01' for season 1.<br>" +
            "Example: {series} - S{season:02d}E{episode:02d} - {title}{ext}</html>";
        JLabel hintPanel = new JLabel(movieHint + "<hr>" + episodeHint);
        fmt.add(hintPanel, fgbc);

        DocumentListener dl = new DocumentListener() {
            private void upd() {
                Map<String,String> movieVals = new HashMap<>();
                movieVals.put("title", "Sample Movie");
                movieVals.put("year", "2020");
                movieVals.put("ext", ".mkv");

                Map<String,String> epVals = new HashMap<>();
                epVals.put("series", "My Series");
                epVals.put("season", "1");
                epVals.put("episode", "2");
                epVals.put("title", "Pilot");
                epVals.put("ext", ".mkv");

                moviePreview.setText(applySampleTemplate(movieField.getText(), movieVals));
                episodePreview.setText(applySampleTemplate(episodeField.getText(), epVals));
            }
            @Override public void insertUpdate(DocumentEvent e) { upd(); }
            @Override public void removeUpdate(DocumentEvent e) { upd(); }
            @Override public void changedUpdate(DocumentEvent e) { upd(); }
        };
        movieField.getDocument().addDocumentListener(dl);
        episodeField.getDocument().addDocumentListener(dl);
        dl.insertUpdate(null); // initialize previews

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("General", general);
        tabs.addTab("Filename Format", fmt);

        int result = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
            tabs, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newKey = new String(keyField.getPassword()).trim();
            String newLang = (String) langCombo.getSelectedItem();
            prefs.put(PREF_API_KEY, newKey);
            prefs.put(PREF_LANGUAGE, newLang != null ? newLang : "en-US");
            // save formats to the shared node used by FormatService
            Preferences nodePref = Preferences.userRoot().node("com/cleanmediamanager");
            nodePref.put("format.movie", movieField.getText());
            nodePref.put("format.episode", episodeField.getText());
            log("[DEBUG] Saved templates: movie='" + movieField.getText() + "' episode='" + episodeField.getText() + "'");
            // Recompute preview/newName for currently matched files
                try {
                    com.cleanmediamanager.core.FormatService fs = new com.cleanmediamanager.core.FormatService();
                    String readMovie = Preferences.userRoot().node("com/cleanmediamanager").get("format.movie", "<none>");
                    log("[DEBUG] FormatService will read movie template: '" + readMovie + "'");
                    int shown = 0;
                    for (com.cleanmediamanager.model.MediaFile mf : mediaFiles) {
                        if (mf.getStatus() == com.cleanmediamanager.model.MatchStatus.MATCHED) {
                            // ensure we have metadata for movies
                            if (mf.getMediaType() == com.cleanmediamanager.model.MediaType.MOVIE && mf.getMatch() == null) {
                                log("[WARN] Matched file has null MovieMatch: " + mf.getOriginalName());
                                continue;
                            }
                            String before = mf.getNewName();
                            if (mf.getMediaType() == com.cleanmediamanager.model.MediaType.MOVIE) {
                                mf.setNewName(fs.format(mf, mf.getMatch()));
                            } else {
                                mf.setNewName(fs.formatEpisode(mf, mf.getSeriesMatch(), mf.getEpisodeMatch()));
                            }
                            if (shown < 5) {
                                log("[DEBUG] Updated newName: '" + before + "' -> '" + mf.getNewName() + "' for file: " + mf.getOriginalName());
                                shown++;
                            }
                        }
                    }
                    refreshTables();
                } catch (Exception e) {
                    log("[ERROR] Failed to recompute formats: " + e.getMessage());
                }
            mainWindow.updateLanguageCombo(newLang != null ? newLang : "en-US");
            log("[INFO] Settings saved.");
        }
    }

    public void onFilenameFormatClicked() {
        // removed: formatting UI is now part of Settings dialog
    }

    public void onLanguageChanged(String language) {
        prefs.put(PREF_LANGUAGE, language);
    }

    public void onOpenNetworkPathClicked() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Netzwerkpfad eingeben:"), BorderLayout.NORTH);

        JTextField pathField = new JTextField("smb://", 40);
        panel.add(pathField, BorderLayout.CENTER);

        JLabel hint = new JLabel("<html><small>Beispiele:&nbsp; smb://server/share &nbsp;|&nbsp; //server/share &nbsp;|&nbsp; /mnt/nas</small></html>");
        panel.add(hint, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(mainWindow.getFrame(), panel,
                "Netzwerkpfad öffnen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String input = pathField.getText().trim();
        if (input.isBlank()) return;

        Path resolved = resolveNetworkPath(input);
        if (resolved == null || !Files.exists(resolved)) {
            log("[ERROR] Pfad nicht gefunden oder nicht erreichbar: " + input);
            JOptionPane.showMessageDialog(mainWindow.getFrame(),
                    "Pfad nicht erreichbar:\n" + input +
                    "\n\nBitte sicherstellen, dass das Netzlaufwerk eingebunden ist.",
                    "Pfad nicht gefunden", JOptionPane.ERROR_MESSAGE);
            return;
        }

        loadFiles(List.of(resolved.toFile()));
    }

    private Path resolveNetworkPath(String input) {
        if (input.startsWith("smb://")) {
            Path gvfs = tryResolveViaGvfs(input);
            if (gvfs != null) return gvfs;
        }
        if (input.startsWith("file://")) {
            try { return Paths.get(URI.create(input)); } catch (Exception ignored) {}
        }
        try { return Paths.get(input); } catch (Exception e) { return null; }
    }

    /**
     * Resolves an smb:// URI to the local gvfs mount point.
     * On Linux, gvfs mounts SMB shares under
     * /run/user/&lt;uid&gt;/gvfs/smb-share:server=&lt;host&gt;,share=&lt;share&gt;
     */
    private Path tryResolveViaGvfs(String smbUri) {
        try {
            String normalized = smbUri.endsWith("/") ? smbUri.substring(0, smbUri.length() - 1) : smbUri;
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;

            String uriPath = uri.getPath() != null ? uri.getPath() : "";
            String[] parts = uriPath.split("/", 3);
            String share = parts.length > 1 ? parts[1] : "";
            String subPath = parts.length > 2 ? parts[2] : "";

            for (String base : new String[]{"/run/user", "/var/run/user"}) {
                Path runUser = Paths.get(base);
                if (!Files.isDirectory(runUser)) continue;
                try (var stream = Files.list(runUser)) {
                    Optional<Path> mountPoint = stream
                            .map(uidDir -> uidDir.resolve("gvfs/smb-share:server=" + host + ",share=" + share))
                            .filter(Files::isDirectory)
                            .findFirst();
                    if (mountPoint.isPresent()) {
                        Path mp = mountPoint.get();
                        return subPath.isEmpty() ? mp : mp.resolve(subPath);
                    }
                } catch (IOException ignored) {}
            }
        } catch (Exception e) {
            log("[WARN] gvfs-Auflösung fehlgeschlagen: " + e.getMessage());
        }
        return null;
    }

    public void onModeChanged(String mode) {
        currentMode = "Series".equals(mode) ? MediaType.EPISODE : MediaType.MOVIE;
        log("[INFO] Mode changed to: " + mode);
    }

    public void clearFiles() {
        mediaFiles.clear();
        refreshTables();
        log("[INFO] File list cleared.");
    }

    public void removeFileAt(int row) {
        if (row >= 0 && row < mediaFiles.size()) {
            MediaFile removed = mediaFiles.remove(row);
            refreshTables();
            log("[INFO] Removed: " + removed.getOriginalName());
        }
    }

    private void refreshTables() {
        tableView.getLeftModel().setFiles(mediaFiles);
        tableView.getRightModel().setFiles(mediaFiles);
    }

    /**
     * Opens the {@link ManualSearchDialog} for an unmatched file, then re-matches
     * all files that share the same parsed title (i.e. the same series group)
     * against the user-selected {@link SeriesMatch}.
     */
    public void handleManualSearch(MediaFile triggerFile) {
        String apiKey = prefs.get(PREF_API_KEY, "");
        if (apiKey.isBlank()) {
            log("[WARN] TMDB API key nicht konfiguriert. Bitte zuerst Einstellungen öffnen.");
            int choice = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                    "TMDB API Key nicht konfiguriert.\nEinstellungen jetzt öffnen?",
                    "API Key erforderlich", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) onSettingsButtonClicked();
            return;
        }

        // Determine the series group: all files with the same parsed title (any status)
        FilenameParser parser = new FilenameParser();
        String groupTitle = parser.parseEpisode(triggerFile.getOriginalName()).getTitle();

        List<MediaFile> group = mediaFiles.stream()
                .filter(f -> parser.parseEpisode(f.getOriginalName()).getTitle()
                                   .equalsIgnoreCase(groupTitle))
                .collect(Collectors.toList());

        String language = prefs.get(PREF_LANGUAGE, "en-US");
        TmdbClient client = new TmdbClient(apiKey, language);

        ManualSearchDialog dialog = new ManualSearchDialog(
                mainWindow.getFrame(), client, groupTitle);
        SeriesMatch selected = dialog.showAndWait();
        if (selected == null) return; // user cancelled

        log("[INFO] Manuelles Matching: '" + selected.getName()
                + "' wird auf " + group.size() + " Datei(en) angewendet…");
        mainWindow.setMatchButtonEnabled(false);

        SeriesMatcher matcher = new SeriesMatcher(client);
        SwingWorker<Void, MediaFile> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                matcher.matchFilesWithSeries(group, selected, f -> publish(f)).get();
                return null;
            }

            @Override
            protected void process(List<MediaFile> chunks) {
                tableView.getLeftModel().refresh();
                tableView.getRightModel().refresh();
            }

            @Override
            protected void done() {
                try {
                    get();
                    log("[INFO] Manuelles Matching abgeschlossen.");
                } catch (Exception e) {
                    log("[ERROR] Matching fehlgeschlagen: " + e.getMessage());
                } finally {
                    mainWindow.setMatchButtonEnabled(true);
                    tableView.getLeftModel().refresh();
                    tableView.getRightModel().refresh();
                }
            }
        };
        worker.execute();
    }

    public void log(String message) {
        mainWindow.appendLog(message);
    }

    private String applySampleTemplate(String template, Map<String,String> values) {
        if (template == null) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{(\\w+)(?::0(\\d+)d)?\\}");
        java.util.regex.Matcher m = p.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String pad = m.group(2);
            String val = values.getOrDefault(key, "");
            if (pad != null && !pad.isEmpty()) {
                try {
                    int width = Integer.parseInt(pad);
                    long num = Long.parseLong(val.isEmpty() ? "0" : val);
                    val = String.format("%0" + width + "d", num);
                } catch (Exception e) {
                    int width = Integer.parseInt(pad);
                    if (val.length() < width) {
                        val = String.format("%" + width + "s", val).replace(' ', '0');
                    }
                }
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public List<MediaFile> getMediaFiles() {
        return mediaFiles;
    }
}
