package com.cleanmediamanager.ui;

import com.cleanmediamanager.api.ImdbClient;
import com.cleanmediamanager.api.MetadataProvider;
import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.core.FileScanner;
import com.cleanmediamanager.core.FilenameParser;
import com.cleanmediamanager.core.MovieMatcher;
import com.cleanmediamanager.core.FormatService;
import com.cleanmediamanager.core.RenameService;
import com.cleanmediamanager.core.SeriesMatcher;
import com.cleanmediamanager.model.MatchStatus;
import com.cleanmediamanager.model.MediaFile;
import com.cleanmediamanager.model.MediaType;
import com.cleanmediamanager.model.SeriesMatch;
import com.cleanmediamanager.smb.SmbCredentials;
import com.cleanmediamanager.smb.SmbFileSystem;

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
    private static final String PREF_PROVIDER = "metadata_provider";
    private static final String PROVIDER_TMDB = "TMDB";
    private static final String PROVIDER_IMDB = "IMDB";

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
        loadPaths(files.stream().map(File::toPath).collect(Collectors.toList()));
    }

    /**
     * Scans the given paths for media files. Unlike {@link #loadFiles(List)}, this
     * accepts any {@link Path} implementation, including SMB network locations that
     * are addressed directly over the network (no local mount required).
     */
    public void loadPaths(List<Path> paths) {
        List<MediaFile> loaded = new ArrayList<>();
        for (Path p : paths) {
            try {
                if (Files.isDirectory(p)) {
                    loaded.addAll(fileScanner.scan(p));
                } else if (Files.isRegularFile(p)) {
                    loaded.addAll(fileScanner.scanFile(p));
                }
            } catch (Exception e) {
                log("[ERROR] Failed to scan: " + p + " - " + e.getMessage());
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

        if (isTmdbProvider() && prefs.get(PREF_API_KEY, "").isBlank()) {
            log("[WARN] TMDB API key not configured. Open Settings to add your API key.");
            int choice = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                    "TMDB API key is not configured.\nWould you like to open Settings now?",
                    "API Key Required", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                onSettingsButtonClicked();
            }
            return;
        }

        MetadataProvider client = createProvider();

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
                        // Prompt user for any low-confidence candidates
                        promptForLowConfidenceMatches();
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
                        // Prompt user for any low-confidence candidates
                        promptForLowConfidenceMatches();
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

        // General settings panel: just the API key, relevant only when TMDB is
        // selected as metadata source (chosen via the "DB" dropdown on the main toolbar).
        JPanel general = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        general.add(new JLabel("TMDB API Key:"), gbc);
        gbc.gridx = 1;
        JPasswordField keyField = new JPasswordField(currentKey, 30);
        keyField.setEnabled(isTmdbProvider());
        general.add(keyField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel hint = new JLabel("<html><small>Nur nötig, wenn oben rechts im Toolbar unter „DB“ <b>TMDB</b> ausgewählt ist – " +
            "kostenlosen Key auf <a href='#'>themoviedb.org</a> anlegen.<br>" +
            "Bei <b>IMDB</b> wird kein Key benötigt (öffentliche IMDb-Suche); Episodentitel bleiben dabei leer.</small></html>");
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
        String movieHint = "<html>Placeholders: <b>{title}</b>, <b>{year}</b>, <b>{tmdbid}</b>, <b>{imdbid}</b>, <b>{ext}</b><br>" +
            "Example: {title} ({year}){ext}  →  'My Movie (2020).mkv'<br>" +
            "{imdbid} and {tmdbid} already include the brackets and are filled automatically from TMDB after matching.<br>" +
            "Example: {title} ({year}) {imdbid}{ext}  →  'Movie (2021) [imdbid-tt12801262].mkv'<br>" +
            "You can include a single '/' to rename the parent folder and file: e.g. {title}/{title}{ext} (only one folder level).</html>";
        String episodeHint = "<html>Placeholders: <b>{series}</b>, <b>{year}</b>, <b>{season}</b>, <b>{episode}</b>, <b>{title}</b>, <b>{tmdbid}</b>, <b>{imdbid}</b>, <b>{ext}</b><br>" +
            "{year} is the series' first air year.<br>" +
            "Padding: use e.g. {season:02d} to get '01' for season 1.<br>" +
            "Example: {series} - S{season:02d}E{episode:02d} - {title}{ext}<br>" +
            "With year and IMDB id: {series} ({year}) {imdbid} - S{season:02d}E{episode:02d} - {title}{ext}</html>";
        JLabel hintPanel = new JLabel(movieHint + "<hr>" + episodeHint);
        fmt.add(hintPanel, fgbc);

        DocumentListener dl = new DocumentListener() {
            private void upd() {
                Map<String,String> movieVals = new HashMap<>();
                movieVals.put("title", "Sample Movie");
                movieVals.put("year", "2020");
                movieVals.put("tmdbid", "[tmdbid-12345]");
                movieVals.put("imdbid", "[imdbid-tt1280126]");
                movieVals.put("ext", ".mkv");

                Map<String,String> epVals = new HashMap<>();
                epVals.put("series", "My Series");
                epVals.put("year", "2020");
                epVals.put("season", "1");
                epVals.put("episode", "2");
                epVals.put("title", "Pilot");
                epVals.put("tmdbid", "[tmdbid-67890]");
                epVals.put("imdbid", "[imdbid-tt7654321]");
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
            prefs.put(PREF_API_KEY, newKey);
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
            log("[INFO] Settings saved.");
        }
    }

    public void onFilenameFormatClicked() {
        // removed: formatting UI is now part of Settings dialog
    }

    public void onLanguageChanged(String language) {
        prefs.put(PREF_LANGUAGE, language);
    }

    /** Called when the "DB" dropdown on the main toolbar changes (TMDB vs. IMDB). */
    public void onProviderChanged(String provider) {
        String normalized = PROVIDER_IMDB.equalsIgnoreCase(provider) ? PROVIDER_IMDB : PROVIDER_TMDB;
        prefs.put(PREF_PROVIDER, normalized);
        log("[INFO] Metadaten-Quelle geändert zu: " + normalized);
    }

    public void onOpenNetworkPathClicked() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Netzwerkpfad (z. B. smb://server/share/Ordner):"), gbc);

        gbc.gridy = 1;
        JTextField pathField = new JTextField("smb://", 40);
        panel.add(pathField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        panel.add(new JLabel("Benutzername:"), gbc);
        gbc.gridx = 1;
        JTextField userField = new JTextField(20);
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Passwort:"), gbc);
        gbc.gridx = 1;
        JPasswordField passField = new JPasswordField(20);
        panel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(new JLabel("Domain (optional):"), gbc);
        gbc.gridx = 1;
        JTextField domainField = new JTextField(20);
        panel.add(domainField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        panel.add(new JLabel("<html><small>Leer lassen für anonymen Gast-Zugriff.<br>" +
                "Die Freigabe wird direkt über das Netzwerk angesprochen \u2013 " +
                "ein Einbinden als Netzlaufwerk ist nicht nötig.</small></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(mainWindow.getFrame(), panel,
                "Netzwerkpfad öffnen (SMB)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String input = pathField.getText().trim();
        if (input.isBlank()) return;

        if (!input.startsWith("smb://")) {
            Path resolved = resolveLocalPath(input);
            if (resolved == null || !Files.exists(resolved)) {
                log("[ERROR] Pfad nicht gefunden oder nicht erreichbar: " + input);
                JOptionPane.showMessageDialog(mainWindow.getFrame(),
                        "Pfad nicht erreichbar:\n" + input,
                        "Pfad nicht gefunden", JOptionPane.ERROR_MESSAGE);
                return;
            }
            loadPaths(List.of(resolved));
            return;
        }

        SmbTarget target;
        try {
            target = parseSmbUri(input);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainWindow.getFrame(),
                    "Ungültiger Netzwerkpfad:\n" + input + "\n" + e.getMessage(),
                    "Ungültiger Pfad", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String username = !userField.getText().isBlank() ? userField.getText().trim() : target.username();
        char[] password = passField.getPassword();
        String domain = domainField.getText().trim();
        SmbCredentials credentials = new SmbCredentials(target.host(), target.port(), target.share(), domain, username, password);

        log("[INFO] Verbinde mit \\\\" + target.host() + "\\" + target.share() + " ...");
        SmbFileSystem fs;
        try {
            fs = SmbFileSystem.connect(credentials);
        } catch (IOException e) {
            log("[ERROR] SMB-Verbindung fehlgeschlagen: " + e.getMessage());
            JOptionPane.showMessageDialog(mainWindow.getFrame(),
                    "Verbindung fehlgeschlagen:\n" + e.getMessage() +
                    "\n\nBitte Server-Adresse, Freigabename sowie Benutzername/Passwort prüfen.",
                    "SMB-Verbindung fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            java.util.Arrays.fill(password, '\0');
        }

        Path root = fs.getPath(target.subPath());
        if (!Files.exists(root)) {
            log("[ERROR] Pfad auf der Freigabe nicht gefunden: " + target.subPath());
            JOptionPane.showMessageDialog(mainWindow.getFrame(),
                    "Der Ordner \"" + target.subPath() + "\" wurde auf der Freigabe \\\\" +
                    target.host() + "\\" + target.share() + " nicht gefunden.",
                    "Pfad nicht gefunden", JOptionPane.ERROR_MESSAGE);
            return;
        }

        log("[INFO] Verbunden mit \\\\" + target.host() + "\\" + target.share() +
                ". Lade Dateien direkt über das Netzwerk...");
        loadPaths(List.of(root));
    }

    private Path resolveLocalPath(String input) {
        if (input.startsWith("file://")) {
            try { return Paths.get(URI.create(input)); } catch (Exception ignored) {}
        }
        try { return Paths.get(input); } catch (Exception e) { return null; }
    }

    private record SmbTarget(String host, int port, String share, String subPath, String username) {}

    /** Parses an {@code smb://[user@]host[:port]/share/sub/path} URI into its components. */
    private SmbTarget parseSmbUri(String input) {
        String normalized = input.endsWith("/") ? input.substring(0, input.length() - 1) : input;
        URI uri = URI.create(normalized);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Kein Servername gefunden (erwartet: smb://server/share/...)");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 445;
        String uriPath = uri.getPath() != null ? uri.getPath() : "";
        String[] parts = uriPath.split("/", 3);
        String share = parts.length > 1 ? parts[1] : "";
        if (share.isBlank()) {
            throw new IllegalArgumentException("Kein Freigabename gefunden (erwartet: smb://server/share/...)");
        }
        String subPath = parts.length > 2 ? parts[2] : "";
        String username = "";
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int colon = userInfo.indexOf(':');
            username = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
        }
        return new SmbTarget(host, port, share, subPath, username);
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
        if (isTmdbProvider() && prefs.get(PREF_API_KEY, "").isBlank()) {
            log("[WARN] TMDB API key nicht konfiguriert. Bitte zuerst Einstellungen öffnen.");
            int choice = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                    "TMDB API Key nicht konfiguriert.\nEinstellungen jetzt öffnen?",
                    "API Key erforderlich", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) onSettingsButtonClicked();
            return;
        }

        FilenameParser parser = new FilenameParser();
        MetadataProvider client = createProvider();

        if (currentMode == MediaType.EPISODE) {
            // Determine the series group: all files with the same parsed title (any status)
            String groupTitle = parser.parseEpisode(triggerFile.getOriginalName()).getTitle();

            List<MediaFile> group = mediaFiles.stream()
                    .filter(f -> parser.parseEpisode(f.getOriginalName()).getTitle()
                                       .equalsIgnoreCase(groupTitle))
                    .collect(Collectors.toList());

                    ManualSearchDialog dialog = new ManualSearchDialog(
                        mainWindow.getFrame(), client, groupTitle, triggerFile.getOriginalName(), triggerFile.getSeriesMatch());
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
        } else {
            // Movie manual search for single file
            String title = parser.parse(triggerFile.getOriginalName()).getTitle();
                    MovieManualSearchDialog dialog = new MovieManualSearchDialog(
                        mainWindow.getFrame(), client, title, triggerFile.getOriginalName(), triggerFile.getMatch());
            com.cleanmediamanager.model.MovieMatch selected = dialog.showAndWait();
            if (selected == null) return;
            log("[INFO] Manuelles Matching: '" + selected.getTitle() + "' angewendet auf " + triggerFile.getOriginalName());
            FormatService fmt = new FormatService();
            triggerFile.setMatch(selected);
            triggerFile.setStatus(MatchStatus.MATCHED);
            triggerFile.setNewName(fmt.format(triggerFile, selected));
            tableView.getLeftModel().refresh();
            tableView.getRightModel().refresh();
        }
    }

    public void log(String message) {
        mainWindow.appendLog(message);
    }

    /** Whether the currently configured metadata provider is TMDB (as opposed to the key-free IMDb provider). */
    private boolean isTmdbProvider() {
        return PROVIDER_TMDB.equals(prefs.get(PREF_PROVIDER, PROVIDER_TMDB));
    }

    /** Builds the {@link MetadataProvider} configured via the toolbar's "DB" dropdown (TMDB requires an API key, IMDb does not). */
    private MetadataProvider createProvider() {
        String language = prefs.get(PREF_LANGUAGE, "en-US");
        if (isTmdbProvider()) {
            String apiKey = prefs.get(PREF_API_KEY, "");
            return new TmdbClient(apiKey, language);
        }
        return new ImdbClient(language);
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

    // Finds low-confidence candidates and invokes manual search dialogs:
    // - For series: group by parsed title and prompt once per group
    // - For movies: prompt per file
    private void promptForLowConfidenceMatches() {
        if (isTmdbProvider() && prefs.get(PREF_API_KEY, "").isBlank()) return;

        FilenameParser parser = new FilenameParser();

        if (currentMode == MediaType.EPISODE) {
            Map<String, MediaFile> groups = new HashMap<>();
            for (MediaFile f : mediaFiles) {
                if (f.getStatus() == MatchStatus.UNMATCHED && f.getSeriesMatch() != null) {
                    String title = parser.parseEpisode(f.getOriginalName()).getTitle();
                    String key = title == null ? "" : title.toLowerCase();
                    groups.putIfAbsent(key, f);
                }
            }
            for (MediaFile trigger : groups.values()) {
                handleManualSearch(trigger);
            }
        } else {
            for (MediaFile f : new ArrayList<>(mediaFiles)) {
                if (f.getStatus() == MatchStatus.UNMATCHED && f.getMatch() != null) {
                    handleManualSearch(f);
                }
            }
        }
    }
}
