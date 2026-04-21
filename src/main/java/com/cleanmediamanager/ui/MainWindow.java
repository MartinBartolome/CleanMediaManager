package com.cleanmediamanager.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

public class MainWindow {

    private final JFrame frame;
    private final JTextArea logArea;
    private JButton matchButton;
    private final Controller controller;
    private FileTableView tableView;
    private JComboBox<String> langCombo;

    public MainWindow() {
        frame = new JFrame("CleanMediaManager - FileBot Clone");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);

        controller = new Controller(this);

        // Build UI components
        frame.setJMenuBar(buildMenuBar());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(buildToolBar(), BorderLayout.NORTH);

        // Table view
        tableView = new FileTableView(files -> {
            SwingUtilities.invokeLater(() -> controller.loadFiles(files));
        });
        controller.setTableView(tableView);
        mainPanel.add(tableView.getSplitPane(), BorderLayout.CENTER);

        // Log panel
        logArea = new JTextArea(5, 80);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(200, 200, 200));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 110));
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        mainPanel.add(logScroll, BorderLayout.SOUTH);

        frame.setContentPane(mainPanel);
        frame.setVisible(true);

        appendLog("[INFO] CleanMediaManager started. Use Load to add media files.");
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem openFolder = new JMenuItem("Open Folder...", KeyEvent.VK_O);
        openFolder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        openFolder.addActionListener(e -> controller.onLoadButtonClicked());

        JMenuItem clearItem = new JMenuItem("Clear List");
        clearItem.addActionListener(e -> controller.clearFiles());

        JMenuItem exitItem = new JMenuItem("Exit", KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> System.exit(0));

        fileMenu.add(openFolder);
        fileMenu.add(clearItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(frame,
                "CleanMediaManager v1.0\n\nA FileBot-like media renaming tool.\nPowered by TheMovieDB API.",
                "About", JOptionPane.INFORMATION_MESSAGE));

        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private JToolBar buildToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton loadButton = new JButton("\uD83D\uDCC2 Load");
        loadButton.setToolTipText("Load media files or folder");
        loadButton.addActionListener(e -> controller.onLoadButtonClicked());

        matchButton = new JButton("\uD83D\uDD0D Match");
        matchButton.setToolTipText("Fetch metadata from TMDB");
        matchButton.addActionListener(e -> controller.onMatchButtonClicked());

        JButton renameButton = new JButton("\u270F\uFE0F Rename");
        renameButton.setToolTipText("Rename matched files");
        renameButton.addActionListener(e -> controller.onRenameButtonClicked());

        JButton settingsButton = new JButton("\u2699\uFE0F Settings");
        settingsButton.setToolTipText("Configure API key and language");
        settingsButton.addActionListener(e -> controller.onSettingsButtonClicked());

        toolBar.add(loadButton);
        toolBar.add(matchButton);
        toolBar.add(renameButton);
        toolBar.addSeparator();
        toolBar.add(settingsButton);
        toolBar.addSeparator();

        toolBar.add(new JLabel("  DB: "));
        JComboBox<String> dbCombo = new JComboBox<>(new String[]{"TheMovieDB"});
        dbCombo.setMaximumSize(new Dimension(140, 28));
        toolBar.add(dbCombo);

        toolBar.add(new JLabel("  Lang: "));
        String[] languages = {"en-US", "de-DE", "fr-FR", "es-ES", "ja-JP"};
        langCombo = new JComboBox<>(languages);
        langCombo.setMaximumSize(new Dimension(90, 28));
        Preferences prefs = Preferences.userNodeForPackage(Controller.class);
        langCombo.setSelectedItem(prefs.get("tmdb_language", "en-US"));
        langCombo.addActionListener(e -> controller.onLanguageChanged((String) langCombo.getSelectedItem()));
        toolBar.add(langCombo);

        return toolBar;
    }

    public void setMatchButtonEnabled(boolean enabled) {
        SwingUtilities.invokeLater(() -> matchButton.setEnabled(enabled));
    }

    public void updateLanguageCombo(String language) {
        SwingUtilities.invokeLater(() -> langCombo.setSelectedItem(language));
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public JFrame getFrame() {
        return frame;
    }
}
