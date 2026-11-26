package org.micromanager.lightsheetmanager.model.autofocus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * The O3 focus search of scapecontrol's {@code scapecontrol.focus} (scapecontrol ADR-0037), step
 * for step, so the Experiment Setup tab's "Test autofocus" finds the correction scapecontrol will.
 * A coarse sweep over reference ± range, a fine sweep one coarse step either side of the best coarse
 * point, and a parabola through the best fine point. Both check against the same fixture
 * ({@code src/test/resources/focus/cases.json}, written by scapecontrol's
 * {@code tools/make_focus_fixture.py}); change one, regenerate it and change the other.
 */
public final class FocusSearch {

    /** µm: positions this close to a window edge count as on it. */
    private static final double EPS = 1e-9;
    /** px: the central square measured. */
    public static final int CROP_PX = 256;

    private FocusSearch() {
    }

    /** The search, in µm. */
    public static final class Settings {
        public final double rangeUm;
        public final double coarseUm;
        public final double fineUm;
        public final double minContrast;

        public Settings(final double rangeUm, final double coarseUm, final double fineUm,
                        final double minContrast) {
            if (!(rangeUm > 0) || !(coarseUm > 0) || !(fineUm > 0)) {
                throw new IllegalArgumentException("the range and both steps must be > 0");
            }
            if (fineUm >= coarseUm) {
                throw new IllegalArgumentException("the fine step must be smaller than the coarse step");
            }
            this.rangeUm = rangeUm;
            this.coarseUm = coarseUm;
            this.fineUm = fineUm;
            this.minContrast = minContrast;
        }

        /** scapecontrol's defaults: ±1 µm, coarse 0.25 µm, fine 0.05 µm. */
        public static Settings defaults() {
            return new Settings(1.0, 0.25, 0.05, 0.02);
        }
    }

    /** One measured position. */
    public static final class Sample {
        public final double positionUm;
        public final double score;

        Sample(final double positionUm, final double score) {
            this.positionUm = positionUm;
            this.score = score;
        }
    }

    /** {@code deltaUm} is NaN when the focus is not trusted; {@code reason} says why. */
    public static final class Result {
        public final boolean ok;
        public final double deltaUm;
        public final String reason;
        public final List<Sample> samples;

        Result(final boolean ok, final double deltaUm, final String reason,
               final List<Sample> samples) {
            this.ok = ok;
            this.deltaUm = deltaUm;
            this.reason = reason;
            this.samples = Collections.unmodifiableList(samples);
        }
    }

    /** The search cannot start: its window lies outside the taught piezo window. */
    public static final class Refused extends IllegalArgumentException {
        Refused(final String message) {
            super(message);
        }
    }

    static List<Double> steps(final double lo, final double hi, final double center,
                              final double step) {
        final int below = (int) Math.floor((center - lo) / step + EPS);
        final int above = (int) Math.floor((hi - center) / step + EPS);
        final List<Double> out = new ArrayList<>();
        for (int k = -below; k <= above; k++) {
            out.add(center + k * step);
        }
        return out;
    }

    private static double parabolaPeak(final double z0, final double z1, final double s0,
                                       final double s1, final double s2) {
        final double denom = s0 - 2 * s1 + s2;
        if (denom >= 0) {
            return z1;
        }
        return z1 + 0.5 * (z1 - z0) * (s0 - s2) / denom;
    }

    /**
     * Runs the search. {@code measureAt} moves the piezo to a position in µm and returns its
     * score; it is never asked for a position outside the window.
     *
     * @param taughtUm the taught piezo window {lower, upper}, or null for none
     */
    public static Result find(final DoubleUnaryOperator measureAt, final double referenceUm,
                              final Settings settings, final double[] taughtUm) {
        double lo = referenceUm - settings.rangeUm;
        double hi = referenceUm + settings.rangeUm;
        if (taughtUm != null) {
            lo = Math.max(lo, taughtUm[0]);
            hi = Math.min(hi, taughtUm[1]);
            if (!(lo - EPS <= referenceUm && referenceUm <= hi + EPS)) {
                throw new Refused("the reference " + referenceUm
                        + " µm is outside the taught piezo window");
            }
        }
        final double winLo = lo;
        final double winHi = hi;
        final List<Sample> samples = new ArrayList<>();
        final DoubleUnaryOperator measure = z -> {
            if (!(winLo - EPS <= z && z <= winHi + EPS)) {
                throw new Refused(z + " µm is outside the window (" + winLo + ", " + winHi + ")");
            }
            final double score = measureAt.applyAsDouble(z);
            if (!Double.isFinite(score)) { // NaN would slip past every check below
                throw new Refused("the score at " + z + " µm is not a number (" + score + ")");
            }
            samples.add(new Sample(z, score));
            return score;
        };

        final List<Sample> coarse = new ArrayList<>();
        for (final double z : steps(lo, hi, referenceUm, settings.coarseUm)) {
            coarse.add(new Sample(z, measure.applyAsDouble(z)));
        }
        Sample best = coarse.get(0);
        double low = coarse.get(0).score;
        for (final Sample s : coarse) {
            if (s.score > best.score) {
                best = s;
            }
            low = Math.min(low, s.score);
        }
        if (best.score - low <= settings.minContrast * Math.abs(low) || best.score == low) {
            return new Result(false, Double.NaN,
                    "no structure: the scores barely change across the window", samples);
        }
        if (best.positionUm <= coarse.get(0).positionUm + EPS
                || best.positionUm >= coarse.get(coarse.size() - 1).positionUm - EPS) {
            return new Result(false, Double.NaN,
                    "the best focus is on the edge of the window: it may lie outside", samples);
        }

        final double fineLo = Math.max(lo, best.positionUm - settings.coarseUm);
        final double fineHi = Math.min(hi, best.positionUm + settings.coarseUm);
        final List<Sample> fine = new ArrayList<>();
        for (final double z : steps(fineLo, fineHi, best.positionUm, settings.fineUm)) {
            fine.add(new Sample(z, measure.applyAsDouble(z)));
        }
        int i = 0;
        for (int k = 1; k < fine.size(); k++) {
            if (fine.get(k).score > fine.get(i).score) {
                i = k;
            }
        }
        double z = fine.get(i).positionUm;
        if (0 < i && i < fine.size() - 1) {
            z = parabolaPeak(fine.get(i - 1).positionUm, fine.get(i).positionUm,
                    fine.get(i - 1).score, fine.get(i).score, fine.get(i + 1).score);
        }
        z = Math.min(Math.max(z, lo), hi);
        return new Result(true, z - referenceUm, "focus found", samples);
    }

    // ---- the measures, on a central crop of a row-major image

    private static double[][] crop(final double[] pixels, final int width, final int height,
                                   final int crop) {
        final int ch = Math.min(crop, height);
        final int cw = Math.min(crop, width);
        final int y0 = (height - ch) / 2;
        final int x0 = (width - cw) / 2;
        final double[][] a = new double[ch][cw];
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                a[y][x] = pixels[(y0 + y) * width + x0 + x];
            }
        }
        return a;
    }

    /** Mean squared Sobel gradient over the squared mean: higher is sharper; the default. */
    public static double tenengrad(final double[] pixels, final int width, final int height) {
        final double[][] a = crop(pixels, width, height, CROP_PX);
        final int h = a.length;
        final int w = a[0].length;
        double mean = 0;
        for (final double[] row : a) {
            for (final double v : row) {
                mean += v;
            }
        }
        mean /= (double) h * w;
        if (mean <= 0) {
            return 0.0;
        }
        double sum = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                final double gx = (a[y - 1][x + 1] + 2 * a[y][x + 1] + a[y + 1][x + 1])
                        - (a[y - 1][x - 1] + 2 * a[y][x - 1] + a[y + 1][x - 1]);
                final double gy = (a[y + 1][x - 1] + 2 * a[y + 1][x] + a[y + 1][x + 1])
                        - (a[y - 1][x - 1] + 2 * a[y - 1][x] + a[y - 1][x + 1]);
                sum += gx * gx + gy * gy;
            }
        }
        return sum / ((double) (h - 2) * (w - 2)) / (mean * mean);
    }

    /** Normalized DCT Shannon entropy; for dense samples, it misranks sparse beads. */
    public static double dcts(final double[] pixels, final int width, final int height) {
        double[][] a = crop(pixels, width, height, CROP_PX);
        final int n = Math.min(a.length, a[0].length);
        double mean = 0;
        for (final double[] row : a) {
            for (final double v : row) {
                mean += v;
            }
        }
        mean /= (double) a.length * a[0].length;
        final double[][] b = new double[n][n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                b[y][x] = a[y][x] - mean;
            }
        }
        final double[][] c = new double[n][n];
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                c[k][i] = Math.cos(Math.PI * (2 * i + 1) * k / (2.0 * n)) * Math.sqrt(2.0 / n);
            }
        }
        for (int i = 0; i < n; i++) {
            c[0][i] /= Math.sqrt(2.0);
        }
        final double[][] t = multiply(c, b);
        final double[][] coef = multiply(t, transpose(c));
        double norm = 0;
        for (final double[] row : coef) {
            for (final double v : row) {
                norm += v * v;
            }
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return 0.0;
        }
        final double r0 = n;
        double sum = 0;
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                if (u + v < r0) {
                    final double p = Math.abs(coef[u][v]) / norm;
                    if (p > 0) {
                        sum += p * (Math.log(p) / Math.log(2.0));
                    }
                }
            }
        }
        return -2.0 / (r0 * r0) * sum;
    }

    private static double[][] multiply(final double[][] x, final double[][] y) {
        final int n = x.length;
        final int m = y[0].length;
        final int k = y.length;
        final double[][] out = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int p = 0; p < k; p++) {
                final double xv = x[i][p];
                for (int j = 0; j < m; j++) {
                    out[i][j] += xv * y[p][j];
                }
            }
        }
        return out;
    }

    private static double[][] transpose(final double[][] x) {
        final double[][] out = new double[x[0].length][x.length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                out[j][i] = x[i][j];
            }
        }
        return out;
    }
}
