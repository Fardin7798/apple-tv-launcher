package com.spidertv.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** Behaviour of the tile-label tidier. */
public class SpiderLabelsTest {

    private static final String PKG = "tv.sample.app";

    private static String tidy(String raw) {
        return SpiderLabels.tileName(raw, PKG);
    }

    private static String run(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    @Test
    public void plainLabelPassesThrough() {
        assertEquals("Spotify", tidy("Spotify"));
        assertEquals("Prime Video", tidy("Prime Video"));
        assertEquals("日本語", tidy("日本語"));
    }

    @Test
    public void missingOrInvisibleLabelUsesFallback() {
        assertSame(PKG, tidy(null));
        assertSame(PKG, tidy(""));
        assertSame(PKG, tidy("    "));
        assertSame(PKG, tidy("\u200B\u2060\uFEFF\r\n"));
        assertNull(SpiderLabels.tileName("\t", null));
    }

    @Test
    public void gapRunsCollapseToOneSpace() {
        assertEquals("Live TV", tidy("Live\r\n\tTV"));
        assertEquals("one two", tidy("one\u3000\u00A0two"));
        assertEquals("x y", tidy("x\u2029\u2028y"));
    }

    @Test
    public void edgesAreTrimmed() {
        assertEquals("Plex", tidy("\u202D  Plex \u0007"));
    }

    @Test
    public void formatCharactersCannotHide() {
        assertEquals("evil gnp", tidy("evil\u202Egnp"));
        assertEquals("a b", tidy("a\u200Db"));
    }

    @Test
    public void longLabelStopsAtForty() {
        assertEquals(run('k', 40), tidy(run('k', 41)));
        assertEquals(40, tidy(run('k', 500)).length());
        // The space is counted: a word that would land on unit 41 is cut along with its gap.
        assertEquals(run('k', 39), tidy(run('k', 39) + " k"));
    }

    @Test
    public void surrogatePairIsNeverSplit() {
        String joystick = new String(Character.toChars(0x1F579));
        assertEquals(run('w', 38) + joystick, tidy(run('w', 38) + joystick + "!"));
        assertEquals(run('w', 39), tidy(run('w', 39) + joystick));
        assertEquals("Game " + joystick, tidy("Game " + joystick));
    }

    @Test
    public void loneSurrogatesAreKeptAsText() {
        assertEquals("a\uD83Cb", tidy("a\uD83Cb"));
        assertEquals("\uDC00", tidy(" \uDC00 "));
    }
}
