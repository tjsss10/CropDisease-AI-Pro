package com.cropdisease.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Computer-vision leaf analyzer (pure Java, no external model files).
 *
 * Pipeline
 *  1. Resize the photo (max 420 px side) for fast, stable analysis.
 *  2. Classify every pixel in HSV colour space: healthy green, chlorotic yellow,
 *     necrotic brown, dark/black lesion, white/grey (powdery / mildew), rust/orange.
 *  3. Segment the leaf: green seed pixels -> morphological closing -> largest
 *     connected regions -> limited growth into lesion colours -> hole filling.
 *  4. Measure lesions inside the leaf: colour shares, % area affected, number and
 *     size of spots (connected components), share of damage on the leaf margin.
 *  5. Image-quality checks: sharpness (Laplacian variance) and brightness.
 *  6. Render an "AI heatmap" PNG that highlights each lesion class.
 *
 * The resulting feature vector is matched against disease signatures by
 * {@link DiagnosisService} (handcrafted-feature classification, the same idea as
 * the colour/texture-feature + SVM branch of the original CropDisease-AI design).
 */
@Service
public class LeafAnalyzer {

    public static final int BG = 0, GREEN = 1, YELLOW = 2, BROWN = 3, DARK = 4, WHITE = 5, RUST = 6;

    private static final int MAX_SIDE = 420;

    public record Spot(int x, int y, int w, int h, int area, String kind) { }

    public record Features(
            int width, int height,
            boolean leafFound,
            double leafCoverage,      // leaf pixels / image pixels
            double damage,            // lesion pixels / leaf pixels
            double yellow, double brown, double dark, double white, double rust,   // share of leaf area
            int spotCount,
            double meanSpotSize,      // mean spot area / leaf area
            double largestSpotShare,  // largest spot / all lesion pixels
            double marginShare,       // lesion pixels near the leaf edge / lesion pixels
            double sharpness,         // Laplacian variance
            double brightness,        // mean V (0..1)
            List<Spot> topSpots) { }

    public record Result(Features features, byte[] heatmapPng, byte[] normalizedJpeg) { }

    public Result analyze(byte[] imageBytes) throws IOException {
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            src = null;
        }
        if (src == null) {
            throw new IllegalArgumentException("Couldn't read that image. Please upload a JPG or PNG photo of a leaf.");
        }
        if (src.getWidth() < 64 || src.getHeight() < 64) {
            throw new IllegalArgumentException("That image is too small. Use a photo at least 200 x 200 pixels.");
        }

        BufferedImage img = resize(src, MAX_SIDE);
        int w = img.getWidth(), h = img.getHeight(), n = w * h;
        int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);

        // ---- 1. pixel classification ------------------------------------
        byte[] cls = new byte[n];
        float[] val = new float[n];
        float[] hsv = new float[3];
        double brightnessSum = 0;
        for (int i = 0; i < n; i++) {
            int p = rgb[i];
            Color.RGBtoHSB((p >> 16) & 255, (p >> 8) & 255, p & 255, hsv);
            cls[i] = (byte) classify(hsv[0] * 360f, hsv[1], hsv[2]);
            val[i] = hsv[2];
            brightnessSum += hsv[2];
        }

        // ---- 2. leaf segmentation ---------------------------------------
        boolean[] green = new boolean[n];
        int greenCount = 0;
        for (int i = 0; i < n; i++) {
            if (cls[i] == GREEN) { green[i] = true; greenCount++; }
        }

        boolean leafFound = greenCount > n * 0.03;
        boolean[] leaf;
        if (leafFound) {
            int r = Math.max(2, Math.max(w, h) / 90);
            leaf = erode(dilate(green, w, h, r), w, h, r);              // closing
            leaf = keepLargestRegions(leaf, w, h, 0.15);
            // grow into neighbouring lesion-coloured pixels (blight often eats the edge)
            int grow = Math.max(3, Math.max(w, h) / 30);
            for (int it = 0; it < grow; it++) {
                boolean[] next = leaf.clone();
                boolean changed = false;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        if (leaf[i] || !isLesionColour(cls[i])) continue;
                        if ((x > 0 && leaf[i - 1]) || (x < w - 1 && leaf[i + 1])
                                || (y > 0 && leaf[i - w]) || (y < h - 1 && leaf[i + w])) {
                            next[i] = true;
                            changed = true;
                        }
                    }
                }
                leaf = next;
                if (!changed) break;
            }
            leaf = fillHoles(leaf, w, h);
        } else {
            // Heavily diseased (almost no green) or not a leaf: use all plant-coloured pixels
            leaf = new boolean[n];
            for (int i = 0; i < n; i++) leaf[i] = cls[i] != BG && cls[i] != WHITE;
            leaf = keepLargestRegions(leaf, w, h, 0.25);
            leaf = fillHoles(leaf, w, h);
        }

        int leafCount = 0;
        for (boolean b : leaf) if (b) leafCount++;
        if (leafCount < n * 0.02) {   // fall back to whole image
            leaf = new boolean[n];
            java.util.Arrays.fill(leaf, true);
            leafCount = n;
            leafFound = false;
        }

        // ---- 3. lesion measurements -------------------------------------
        int[] counts = new int[7];
        boolean[] lesion = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!leaf[i]) continue;
            int c = cls[i];
            if (c == BG) c = val[i] > 0.6f ? WHITE : BROWN;   // grey/odd pixels inside the leaf
            cls[i] = (byte) c;
            counts[c]++;
            if (c != GREEN) lesion[i] = true;
        }
        // remove isolated single-pixel noise from the lesion mask
        lesion = removeSmall(lesion, w, h, 3, cls, counts);

        int lesionCount = 0;
        for (boolean b : lesion) if (b) lesionCount++;

        // spots = connected lesion regions
        List<int[]> comps = components(lesion, cls, w, h, 4);  // [area, minX, minY, maxX, maxY, dominantClass]
        int spotCount = comps.size();
        int largest = 0;
        long areaSum = 0;
        for (int[] c : comps) { areaSum += c[0]; largest = Math.max(largest, c[0]); }

        // margin damage: lesion pixels outside the eroded leaf
        int edge = Math.max(3, Math.max(w, h) / 25);
        boolean[] interior = erode(leaf, w, h, edge);
        int marginLesion = 0;
        for (int i = 0; i < n; i++) if (lesion[i] && !interior[i]) marginLesion++;

        double sharp = laplacianVariance(rgb, leaf, w, h);

        double L = leafCount;
        List<Spot> top = new ArrayList<>();
        comps.sort((a, b) -> b[0] - a[0]);
        for (int k = 0; k < Math.min(6, comps.size()); k++) {
            int[] c = comps.get(k);
            top.add(new Spot(c[1], c[2], c[3] - c[1] + 1, c[4] - c[2] + 1, c[0], className(c[5])));
        }

        Features f = new Features(
                w, h, leafFound,
                round(leafCount / (double) n),
                round(lesionCount / L),
                round(counts[YELLOW] / L), round(counts[BROWN] / L), round(counts[DARK] / L),
                round(counts[WHITE] / L), round(counts[RUST] / L),
                spotCount,
                spotCount == 0 ? 0 : round(areaSum / (double) spotCount / L),
                lesionCount == 0 ? 0 : round(largest / (double) lesionCount),
                lesionCount == 0 ? 0 : round(marginLesion / (double) lesionCount),
                Math.round(sharp * 10) / 10.0,
                round(brightnessSum / n),
                top);

        return new Result(f, heatmap(rgb, cls, leaf, lesion, w, h), jpeg(img));
    }

    // ------------------------------------------------------------------
    // Pixel colour classes (h in degrees, s & v 0..1)
    // ------------------------------------------------------------------

    static int classify(float h, float s, float v) {
        if (v < 0.17f) return DARK;
        if (s < 0.17f) {
            if (v > 0.70f) return WHITE;
            return BG;                       // grey: decided later (inside leaf -> brown/white)
        }
        if (h >= 68f && h <= 175f) {
            return (v < 0.24f) ? DARK : GREEN;
        }
        if (h >= 46f && h < 68f) {           // yellow / yellow-green
            if (s > 0.33f && v > 0.48f) return YELLOW;
            return v < 0.35f ? BROWN : GREEN;
        }
        if (h >= 18f && h < 46f) {           // orange / tan / brown
            if (v < 0.25f) return DARK;      // blackish-brown (late blight, black rot)
            if (s > 0.58f && v > 0.58f && h < 40f) return RUST;
            if (h >= 38f && v > 0.62f && s > 0.3f) return YELLOW;
            return BROWN;
        }
        if (h < 18f || h >= 330f) {          // red-brown / maroon
            if (s > 0.65f && v > 0.62f) return RUST;
            return v < 0.28f ? DARK : BROWN;
        }
        return BG;                           // blue / purple: sky, tarp, background
    }

    private static boolean isLesionColour(int c) {
        return c == YELLOW || c == BROWN || c == RUST || c == DARK;
    }

    static String className(int c) {
        return switch (c) {
            case GREEN -> "healthy";
            case YELLOW -> "chlorosis";
            case BROWN -> "necrosis";
            case DARK -> "dark lesion";
            case WHITE -> "powdery / mildew";
            case RUST -> "rust pustule";
            default -> "other";
        };
    }

    // ------------------------------------------------------------------
    // Morphology & connected components
    // ------------------------------------------------------------------

    private static boolean[] dilate(boolean[] m, int w, int h, int r) {
        boolean[] tmp = new boolean[m.length], out = new boolean[m.length];
        for (int y = 0; y < h; y++) {
            int last = -100000;
            for (int x = 0; x < w; x++) {
                if (m[y * w + x]) last = x;
                if (x - last <= r) tmp[y * w + x] = true;
            }
            last = 100000;
            for (int x = w - 1; x >= 0; x--) {
                if (m[y * w + x]) last = x;
                if (last - x <= r) tmp[y * w + x] = true;
            }
        }
        for (int x = 0; x < w; x++) {
            int last = -100000;
            for (int y = 0; y < h; y++) {
                if (tmp[y * w + x]) last = y;
                if (y - last <= r) out[y * w + x] = true;
            }
            last = 100000;
            for (int y = h - 1; y >= 0; y--) {
                if (tmp[y * w + x]) last = y;
                if (last - y <= r) out[y * w + x] = true;
            }
        }
        return out;
    }

    private static boolean[] erode(boolean[] m, int w, int h, int r) {
        boolean[] inv = new boolean[m.length];
        for (int i = 0; i < m.length; i++) inv[i] = !m[i];
        boolean[] d = dilate(inv, w, h, r);
        boolean[] out = new boolean[m.length];
        for (int i = 0; i < m.length; i++) {
            int x = i % w, y = i / w;
            boolean border = x < r || y < r || x >= w - r || y >= h - r;
            out[i] = !d[i] && (!border || m[i]);
        }
        return out;
    }

    /** Fill enclosed holes (lesions in the middle of the leaf are holes in the green mask). */
    private static boolean[] fillHoles(boolean[] m, int w, int h) {
        boolean[] outside = new boolean[m.length];
        int[] queue = new int[m.length];
        int head = 0, tail = 0;
        for (int x = 0; x < w; x++) {
            tail = seed(m, outside, queue, tail, x);
            tail = seed(m, outside, queue, tail, (h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(m, outside, queue, tail, y * w);
            tail = seed(m, outside, queue, tail, y * w + w - 1);
        }
        while (head < tail) {
            int i = queue[head++];
            int x = i % w, y = i / w;
            if (x > 0) tail = seed(m, outside, queue, tail, i - 1);
            if (x < w - 1) tail = seed(m, outside, queue, tail, i + 1);
            if (y > 0) tail = seed(m, outside, queue, tail, i - w);
            if (y < h - 1) tail = seed(m, outside, queue, tail, i + w);
        }
        boolean[] out = new boolean[m.length];
        for (int i = 0; i < m.length; i++) out[i] = m[i] || !outside[i];
        return out;
    }

    private static int seed(boolean[] m, boolean[] seen, int[] q, int tail, int i) {
        if (!m[i] && !seen[i]) { seen[i] = true; q[tail++] = i; }
        return tail;
    }

    /** Keep regions at least {@code minShare} of the largest region. */
    private static boolean[] keepLargestRegions(boolean[] m, int w, int h, double minShare) {
        int[] label = new int[m.length];
        List<Integer> sizes = new ArrayList<>();
        sizes.add(0);
        int[] queue = new int[m.length];
        int next = 1;
        for (int s = 0; s < m.length; s++) {
            if (!m[s] || label[s] != 0) continue;
            int head = 0, tail = 0, size = 0;
            queue[tail++] = s;
            label[s] = next;
            while (head < tail) {
                int i = queue[head++];
                size++;
                int x = i % w, y = i / w;
                for (int d = 0; d < 4; d++) {
                    int nx = x + (d == 0 ? -1 : d == 1 ? 1 : 0), ny = y + (d == 2 ? -1 : d == 3 ? 1 : 0);
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int j = ny * w + nx;
                    if (m[j] && label[j] == 0) { label[j] = next; queue[tail++] = j; }
                }
            }
            sizes.add(size);
            next++;
        }
        int max = 0;
        for (int s : sizes) max = Math.max(max, s);
        boolean[] out = new boolean[m.length];
        for (int i = 0; i < m.length; i++) out[i] = label[i] != 0 && sizes.get(label[i]) >= max * minShare;
        return out;
    }

    /** Connected lesion regions (8-connected) of at least minArea px. */
    private static List<int[]> components(boolean[] m, byte[] cls, int w, int h, int minArea) {
        boolean[] seen = new boolean[m.length];
        int[] queue = new int[m.length];
        List<int[]> out = new ArrayList<>();
        for (int s = 0; s < m.length; s++) {
            if (!m[s] || seen[s]) continue;
            int head = 0, tail = 0, area = 0;
            int minX = w, minY = h, maxX = 0, maxY = 0;
            int[] hist = new int[7];
            queue[tail++] = s;
            seen[s] = true;
            while (head < tail) {
                int i = queue[head++];
                area++;
                hist[cls[i]]++;
                int x = i % w, y = i / w;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int j = ny * w + nx;
                        if (m[j] && !seen[j]) { seen[j] = true; queue[tail++] = j; }
                    }
                }
            }
            int dom = YELLOW;
            for (int c = YELLOW; c <= RUST; c++) if (hist[c] > hist[dom]) dom = c;
            if (area >= minArea) out.add(new int[]{area, minX, minY, maxX, maxY, dom});
        }
        return out;
    }

    private static boolean[] removeSmall(boolean[] m, int w, int h, int minArea, byte[] cls, int[] counts) {
        boolean[] seen = new boolean[m.length];
        int[] queue = new int[m.length];
        boolean[] out = m.clone();
        for (int s = 0; s < m.length; s++) {
            if (!m[s] || seen[s]) continue;
            int head = 0, tail = 0;
            queue[tail++] = s;
            seen[s] = true;
            while (head < tail) {
                int i = queue[head++];
                int x = i % w, y = i / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int j = ny * w + nx;
                        if (m[j] && !seen[j]) { seen[j] = true; queue[tail++] = j; }
                    }
                }
            }
            if (tail < minArea) {
                for (int k = 0; k < tail; k++) {
                    int i = queue[k];
                    out[i] = false;
                    counts[cls[i]]--;
                    counts[GREEN]++;
                    cls[i] = GREEN;
                }
            }
        }
        return out;
    }

    private static double laplacianVariance(int[] rgb, boolean[] mask, int w, int h) {
        double sum = 0, sq = 0;
        int count = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if (!mask[i]) continue;
                double lap = 4 * gray(rgb[i]) - gray(rgb[i - 1]) - gray(rgb[i + 1]) - gray(rgb[i - w]) - gray(rgb[i + w]);
                sum += lap;
                sq += lap * lap;
                count++;
            }
        }
        if (count == 0) return 0;
        double mean = sum / count;
        return sq / count - mean * mean;
    }

    private static double gray(int p) {
        return 0.299 * ((p >> 16) & 255) + 0.587 * ((p >> 8) & 255) + 0.114 * (p & 255);
    }

    // ------------------------------------------------------------------
    // Output images
    // ------------------------------------------------------------------

    private static byte[] heatmap(int[] rgb, byte[] cls, boolean[] leaf, boolean[] lesion, int w, int h) throws IOException {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            int p = rgb[i];
            int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            int gr = (int) gray(p);
            if (!leaf[i]) {
                px[i] = rgbInt(gr / 4 + 8, gr / 4 + 10, gr / 4 + 14);
            } else if (!lesion[i]) {
                px[i] = rgbInt(gr / 3, Math.min(255, gr / 2 + 70), gr / 3 + 20);    // healthy tissue: calm green
            } else {
                int[] c = switch (cls[i]) {
                    case YELLOW -> new int[]{255, 210, 40};
                    case BROWN -> new int[]{255, 70, 50};
                    case DARK -> new int[]{220, 40, 220};
                    case WHITE -> new int[]{60, 220, 255};
                    case RUST -> new int[]{255, 140, 20};
                    default -> new int[]{255, 255, 255};
                };
                px[i] = rgbInt((int) (c[0] * 0.8 + r * 0.2), (int) (c[1] * 0.8 + g * 0.2), (int) (c[2] * 0.8 + b * 0.2));
            }
        }
        out.setRGB(0, 0, w, h, px, 0, w);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    private static int rgbInt(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static BufferedImage resize(BufferedImage src, int maxSide) {
        double scale = Math.min(1.0, maxSide / (double) Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] jpeg(BufferedImage img) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
