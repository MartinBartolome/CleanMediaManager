package com.cleanmediamanager.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class AppIcon {

    private static final int[] ICON_SIZES = {16, 22, 24, 32, 48, 64, 128, 256, 512};

    private AppIcon() {}

    /**
     * Returns a list of icon images at all standard sizes.
     * Prefers packaged PNGs from resources; falls back to programmatic rendering.
     */
    public static List<Image> getIconImages() {
        List<Image> images = new ArrayList<>();
        for (int size : ICON_SIZES) {
            Image img = loadResourceIcon("/icons/app-icon-" + size + ".png");
            if (img == null) {
                img = renderIcon(size);
            }
            images.add(img);
        }
        return images;
    }

    private static BufferedImage renderIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int arc = Math.max(4, size * 40 / 256);

        // Blue gradient background
        GradientPaint gp = new GradientPaint(0, 0, new Color(0x1E88E5), 0, size, new Color(0x1976D2));
        g.setPaint(gp);
        g.fillRoundRect(0, 0, size, size, arc, arc);

        // Subtle inner highlight
        g.setColor(new Color(255, 255, 255, 35));
        int margin = Math.max(1, size / 25);
        g.fillOval(margin, margin, size - margin * 2, size - margin * 2);

        // Initials "CM"
        g.setColor(Color.WHITE);
        // Scale font so it fits nicely; skip text for tiny sizes
        if (size >= 16) {
            int fontSize = size <= 24 ? size - 4 : size / 2;
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics fm = g.getFontMetrics();
            String s = size <= 24 ? "C" : "CM";
            int tw = fm.stringWidth(s);
            int x = (size - tw) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(s, x, y);
        }

        g.dispose();
        return img;
    }

    private static Image loadResourceIcon(String path) {
        try (InputStream in = AppIcon.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}
