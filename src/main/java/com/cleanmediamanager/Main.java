package com.cleanmediamanager;

import com.cleanmediamanager.smb.SmbConnectionRegistry;
import com.cleanmediamanager.ui.MainWindow;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // Must be set before any AWT/Swing call so that the WM_CLASS of the
        // window matches StartupWMClass in the .desktop file – required for
        // GNOME/Ubuntu to display the correct taskbar icon.
        System.setProperty("sun.awt.appName", "CleanMediaManager");
        Runtime.getRuntime().addShutdownHook(new Thread(SmbConnectionRegistry::closeAll));
        SwingUtilities.invokeLater(MainWindow::new);
    }
}
