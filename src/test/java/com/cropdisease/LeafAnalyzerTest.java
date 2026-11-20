package com.cropdisease;

import com.cropdisease.service.LeafAnalyzer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the vision engine on synthetic leaves with known damage. */
class LeafAnalyzerTest {

    private final LeafAnalyzer analyzer = new LeafAnalyzer();

    static byte[] leaf(int spots, Color spotColor, int spotRadius, Color base, boolean whitePowder) throws Exception {
        BufferedImage img = new BufferedImage(600, 450, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(235, 232, 225));            // paper background
        g.fillRect(0, 0, 600, 450);
        g.setColor(base);
        g.fill(new Ellipse2D.Double(90, 60, 420, 330));  // the leaf
        g.setColor(base.darker());
        g.setStroke(new BasicStroke(3));
        g.drawLine(100, 225, 500, 225);                  // midrib
        Random r = new Random(7);
        for (int i = 0; i < spots; i++) {
            int x = 170 + r.nextInt(260), y = 120 + r.nextInt(210);
            g.setColor(spotColor);
            g.fillOval(x - spotRadius, y - spotRadius, spotRadius * 2, spotRadius * 2);
        }
        if (whitePowder) {
            g.setColor(new Color(240, 242, 240));
            for (int i = 0; i < 9; i++) g.fillOval(160 + r.nextInt(250), 110 + r.nextInt(200), 46, 34);
        }
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    void healthyLeafHasLittleDamage() throws Exception {
        LeafAnalyzer.Result res = analyzer.analyze(leaf(0, Color.BLACK, 0, new Color(52, 140, 60), false));
        assertTrue(res.features().leafFound());
        assertTrue(res.features().damage() < 0.035, "damage was " + res.features().damage());
        assertTrue(res.features().leafCoverage() > 0.3);
        assertTrue(res.heatmapPng().length > 0);
    }

    @Test
    void brownSpotsAreFoundAsNecrosis() throws Exception {
        LeafAnalyzer.Features f = analyzer.analyze(leaf(14, new Color(110, 70, 30), 12, new Color(52, 140, 60), false)).features();
        assertTrue(f.damage() > 0.04, "damage was " + f.damage());
        assertTrue(f.brown() > f.yellow() && f.brown() > f.white(), f.toString());
        assertTrue(f.spotCount() >= 6, "spots " + f.spotCount());
    }

    @Test
    void powderyMildewIsWhite() throws Exception {
        LeafAnalyzer.Features f = analyzer.analyze(leaf(0, Color.BLACK, 0, new Color(52, 140, 60), true)).features();
        assertTrue(f.white() > 0.05, f.toString());
    }

    @Test
    void rejectsNonImage() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze("not an image".getBytes()));
    }
}
