package org.micromanager.lightsheetmanager.gui.tabs.experiment;

import ij.gui.Plot;
import ij.gui.PlotWindow;
import java.util.List;
import org.micromanager.lightsheetmanager.model.autofocus.FocusSearch;

/**
 * The scores of the last Test autofocus against the piezo position, with the reference and the
 * focus found: where the curve still rises at an edge, the focus lies further that way. Drawn with
 * ImageJ's Plot (Micro-Manager ships ImageJ), in one window reused by every test.
 */
final class FocusPlot {

    private static PlotWindow window_;

    private FocusPlot() {
    }

    /** On the EDT. {@code focusUm} is NaN when no focus was found. */
    static void show(final FocusSearch.Result r, final double referenceUm, final double focusUm,
                     final String measure) {
        if (r.samples.isEmpty()) {
            return;
        }
        final Plot plot = build(r, referenceUm, focusUm, measure);
        if (window_ != null && window_.isVisible()) {
            window_.drawPlot(plot);
            window_.toFront();
        } else {
            window_ = plot.show();
        }
    }

    static Plot build(final FocusSearch.Result r, final double referenceUm, final double focusUm,
                      final String measure) {
        final List<FocusSearch.Sample> samples = r.samples;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        double left = Math.min(referenceUm, Double.isNaN(focusUm) ? referenceUm : focusUm);
        double right = Math.max(referenceUm, Double.isNaN(focusUm) ? referenceUm : focusUm);
        for (final FocusSearch.Sample s : samples) {
            low = Math.min(low, s.score);
            high = Math.max(high, s.score);
            left = Math.min(left, s.positionUm);
            right = Math.max(right, s.positionUm);
        }
        final Plot plot = new Plot("Test autofocus", "Piezo [µm]", measure + " score");
        // each object styled by its index: setColor alone left the lines black
        int index = 0;
        String legend = "coarse";
        addSweep(plot, samples.subList(0, r.coarseCount));
        plot.setStyle(index++, "blue,blue,2,connected circle");
        if (samples.size() > r.coarseCount) {
            addSweep(plot, samples.subList(r.coarseCount, samples.size()));
            plot.setStyle(index++, "#009600,#009600,2,connected circle");
            legend += "\nfine";
        }
        plot.add("line", new double[] {referenceUm, referenceUm}, new double[] {low, high});
        plot.setStyle(index++, "gray,none,1,line");
        legend += String.format("\nreference %.3f µm", referenceUm);
        if (!Double.isNaN(focusUm)) {
            plot.add("line", new double[] {focusUm, focusUm}, new double[] {low, high});
            plot.setStyle(index, "red,none,2,line");
            legend += String.format("\nfocus %.3f µm", focusUm);
        }
        plot.addLegend(legend);
        // a margin all round: the best point sat on the frame's top edge
        final double dx = Math.max(right - left, 1e-3) * 0.05;
        final double dy = Math.max(high - low, Math.abs(high) * 1e-3 + 1e-12) * 0.1;
        plot.setLimits(left - dx, right + dx, low - dy, high + dy);
        return plot;
    }

    private static void addSweep(final Plot plot, final List<FocusSearch.Sample> sweep) {
        final double[] z = new double[sweep.size()];
        final double[] score = new double[sweep.size()];
        for (int k = 0; k < z.length; k++) {
            z[k] = sweep.get(k).positionUm;
            score[k] = sweep.get(k).score;
        }
        plot.add("connected circle", z, score);
    }
}
