package com.cleanmediamanager;

import com.cleanmediamanager.ui.MainWindow;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainWindow::new);
    }
}
