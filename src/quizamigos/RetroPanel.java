package quizamigos;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

@SuppressWarnings("serial")
class RetroPanel extends JPanel {

    static final Color INK = new Color(22, 8, 45);
    static final Color PURPLE = new Color(100, 34, 170);
    static final Color PINK = new Color(255, 80, 190);
    static final Color LILAC = new Color(211, 178, 255);
    static final Color PALE = new Color(250, 240, 255);

    RetroPanel() {
        setOpaque(false);
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setPaint(new GradientPaint(0, 0, new Color(35, 8, 65), w, h,
                new Color(13, 5, 30)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(156, 65, 220, 24));
        g.fillOval(w - 320, -150, 440, 440);
        g.setColor(new Color(255, 60, 190, 20));
        g.fillOval(-180, h - 260, 480, 360);
        g.setColor(new Color(255, 255, 255, 12));
        for (int y = 0; y < h; y += 4) g.drawLine(0, y, w, y);
        g.setStroke(new java.awt.BasicStroke(2f));
        g.setColor(new Color(221, 151, 255, 120));
        g.draw(new RoundRectangle2D.Float(7, 7, Math.max(0, w - 15), Math.max(0, h - 15), 22, 22));
        g.dispose();
    }
}
