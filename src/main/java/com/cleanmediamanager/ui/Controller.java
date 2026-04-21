package com.cleanmediamanager.ui;

import com.cleanmediamanager.api.TmdbClient;
import com.cleanmediamanager.core.FileScanner;
import com.cleanmediamanager.core.MovieMatcher;
import com.cleanmediamanager.core.RenameService;
import com.cleanmediamanager.model.MediaFile;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class Controller {

    private static final String PREF_API_KEY = "tmdb_api_key";
    private static final String PREF_LANGUAGE = "tmdb_language";

    private final MainWindow mainWindow;
    private final FileScanner fileScanner;
    private final RenameService renameService;
    private final Preferences prefs;

    private List<MediaFile> mediaFiles = new ArrayList<>();
    private FileTableView tableView;

    public Controller(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        this.fileScanner = new FileScanner();
        this.renameService = new RenameService();
        this.prefs = Preferences.userNodeForPackage(Controller.class);
    }

    public void setTableView(FileTableView tableView) {
        this.tableView = tableView;
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

        mediaFiles.addAll(loaded);
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
        MovieMatcher matcher = new MovieMatcher(client);

        log("[INFO] Starting matching for " + mediaFiles.size() + " file(s)...");
        mainWindow.setMatchButtonEnabled(false);

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

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("TMDB API Key:"), gbc);
        gbc.gridx = 1;
        JPasswordField keyField = new JPasswordField(currentKey, 30);
        panel.add(keyField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Language:"), gbc);
        gbc.gridx = 1;
        String[] languages = {"en-US", "de-DE", "fr-FR", "es-ES", "ja-JP"};
        JComboBox<String> langCombo = new JComboBox<>(languages);
        langCombo.setSelectedItem(currentLang);
        panel.add(langCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel hint = new JLabel("<html><small>Get a free API key at <a href='#'>themoviedb.org</a></small></html>");
        panel.add(hint, gbc);

        int result = JOptionPane.showConfirmDialog(mainWindow.getFrame(),
                panel, "Settings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newKey = new String(keyField.getPassword()).trim();
            String newLang = (String) langCombo.getSelectedItem();
            prefs.put(PREF_API_KEY, newKey);
            prefs.put(PREF_LANGUAGE, newLang != null ? newLang : "en-US");
            mainWindow.updateLanguageCombo(newLang != null ? newLang : "en-US");
            log("[INFO] Settings saved.");
        }
    }

    public void onLanguageChanged(String language) {
        prefs.put(PREF_LANGUAGE, language);
    }

    public void clearFiles() {
        mediaFiles.clear();
        refreshTables();
        log("[INFO] File list cleared.");
    }

    private void refreshTables() {
        tableView.getLeftModel().setFiles(mediaFiles);
        tableView.getRightModel().setFiles(mediaFiles);
    }

    public void log(String message) {
        mainWindow.appendLog(message);
    }

    public List<MediaFile> getMediaFiles() {
        return mediaFiles;
    }
}
