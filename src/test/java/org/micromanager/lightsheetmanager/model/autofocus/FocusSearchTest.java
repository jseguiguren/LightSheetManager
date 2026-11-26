package org.micromanager.lightsheetmanager.model.autofocus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.Test;

/**
 * The Java search against scapecontrol's own results (cases.json, scapecontrol
 * tools/make_focus_fixture.py): the same scores on the same images, the same positions asked for,
 * the same correction.
 */
public class FocusSearchTest {

    private static JsonObject cases() {
        return new JsonParser().parse(new InputStreamReader(
                FocusSearchTest.class.getResourceAsStream("/focus/cases.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static double[] pixels(final JsonObject image) {
        final byte[] raw = Base64.getDecoder().decode(image.get("uint16_le_base64").getAsString());
        final ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        final double[] out = new double[raw.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.getShort() & 0xFFFF;
        }
        return out;
    }

    @Test
    public void measuresGiveScapecontrolsScores() {
        for (final JsonElement e : cases().getAsJsonArray("images")) {
            final JsonObject image = e.getAsJsonObject();
            final double[] px = pixels(image);
            final int w = image.get("width").getAsInt();
            final int h = image.get("height").getAsInt();
            final double ten = image.get("tenengrad").getAsDouble();
            final double dct = image.get("dcts").getAsDouble();
            assertEquals(ten, FocusSearch.tenengrad(px, w, h), Math.abs(ten) * 1e-9);
            assertEquals(dct, FocusSearch.dcts(px, w, h), Math.abs(dct) * 1e-9);
        }
    }

    @Test
    public void searchesAskForTheSamePositionsAndFindTheSameCorrection() {
        for (final JsonElement e : cases().getAsJsonArray("searches")) {
            final JsonObject c = e.getAsJsonObject();
            final JsonObject s = c.getAsJsonObject("settings");
            final JsonObject score = c.getAsJsonObject("score");
            final double peak = score.get("peak_um").getAsDouble();
            final double width = score.get("width_um").getAsDouble();
            final double floor = score.get("floor").getAsDouble();
            final double[] taught = c.get("taught_um").isJsonNull() ? null : new double[] {
                    c.getAsJsonArray("taught_um").get(0).getAsDouble(),
                    c.getAsJsonArray("taught_um").get(1).getAsDouble()};
            final FocusSearch.Result r = FocusSearch.find(
                    z -> floor + Math.exp(-((z - peak) * (z - peak)) / (2 * width * width)),
                    c.get("reference_um").getAsDouble(),
                    new FocusSearch.Settings(s.get("range_um").getAsDouble(),
                            s.get("coarse_um").getAsDouble(), s.get("fine_um").getAsDouble(),
                            s.get("min_contrast").getAsDouble()),
                    taught);
            final JsonObject expected = c.getAsJsonObject("expected");
            assertEquals(expected.get("ok").getAsBoolean(), r.ok);
            final JsonArray asked = expected.getAsJsonArray("asked_um");
            final List<FocusSearch.Sample> samples = r.samples;
            assertEquals(asked.size(), samples.size());
            for (int i = 0; i < asked.size(); i++) {
                assertEquals(asked.get(i).getAsDouble(), samples.get(i).positionUm, 1e-9);
            }
            if (r.ok) {
                assertEquals(expected.get("delta_um").getAsDouble(), r.deltaUm, 1e-9);
            } else {
                assertTrue(Double.isNaN(r.deltaUm));
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aCoarseSweepOfMoreThanAHundredAndOneFramesIsRefused() {
        new FocusSearch.Settings(10.0, 0.05, 0.01, 0.02); // as scapecontrol's FocusSettings
    }

    @Test(expected = IllegalArgumentException.class)
    public void aFineSweepOfMoreThanAHundredAndOneFramesIsRefused() {
        new FocusSearch.Settings(1.0, 0.25, 0.001, 0.02);
    }

    @Test(expected = FocusSearch.Refused.class)
    public void aScoreThatIsNotANumberStopsTheSearch() {
        FocusSearch.find(z -> Double.NaN, 50.0, FocusSearch.Settings.defaults(), null);
    }

    @Test(expected = FocusSearch.Refused.class)
    public void aReferenceOutsideTheTaughtWindowIsRefused() {
        FocusSearch.find(z -> {
            throw new AssertionError("moved before refusing");
        }, 50.0, FocusSearch.Settings.defaults(), new double[] {40.0, 45.0});
    }

    /** The Test autofocus plot draws the two sweeps apart. */
    @Test
    public void theResultSaysWhichSamplesAreTheCoarseSweep() {
        final FocusSearch.Result found = FocusSearch.find(z -> -(z - 50.3) * (z - 50.3), 50.0,
                FocusSearch.Settings.defaults(), null);
        assertEquals(9, found.coarseCount); // 49 to 51 in 0.25 µm steps
        assertTrue(found.samples.size() > found.coarseCount);
        for (int k = 1; k < found.coarseCount; k++) {
            assertTrue(found.samples.get(k).positionUm > found.samples.get(k - 1).positionUm);
        }
        final FocusSearch.Result edge = FocusSearch.find(z -> z, 50.0,
                FocusSearch.Settings.defaults(), null);
        assertEquals(edge.samples.size(), edge.coarseCount); // no fine sweep
    }
}
