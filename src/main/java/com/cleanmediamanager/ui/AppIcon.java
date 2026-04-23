package com.cleanmediamanager.ui;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class AppIcon {

    private AppIcon() {}

    public static Image getAppIcon() {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // background rounded rectangle with gradient
        GradientPaint gp = new GradientPaint(0, 0, new Color(0x1E88E5), 0, size, new Color(0x1976D2));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, size, size, 40, 40);

        // subtle inner circle highlight
        g.setColor(new Color(255, 255, 255, 35));
        g.fillOval(10, 10, size - 20, size - 20);

        // Draw initials
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, size / 2));
        FontMetrics fm = g.getFontMetrics();
        String s = "CM";
        int textWidth = fm.stringWidth(s);
        int x = (size - textWidth) / 2;
        int y = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(s, x, y);

        g.dispose();

        // Return a scaled instance suitable for window icons
        return img.getScaledInstance(64, 64, Image.SCALE_SMOOTH);
    }
}
