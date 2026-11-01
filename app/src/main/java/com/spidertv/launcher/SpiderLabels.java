package com.spidertv.launcher;

/**
 * Text helpers for the names drawn on home-screen tiles.
 *
 * Installed apps hand us whatever label they like, and some of them carry invisible or layout
 * characters (direction overrides, zero-width joiners, newlines). Those must never reach a tile, so
 * {@link #tileName} turns an untrusted label into one tidy, bounded line of text.
 *
 * Pure Java on purpose: no regex, no Android types, nothing that can fail while the class loads.
 */
final class SpiderLabels {

    /** Upper bound on the tidied label, counted in UTF-16 units. */
    private static final int TILE_NAME_LIMIT = 40;

    private SpiderLabels() {
    }

    /**
     * Returns {@code raw} as a single tidy line, or {@code fallback} when nothing printable is left.
     *
     * Every run of "gap" characters (see {@link #isBlankUnit}) becomes one space, and gaps at either end
     * disappear. Output stops before the first visible character (with the space in front of it, if
     * any) that would push it past {@value #TILE_NAME_LIMIT} units; a surrogate pair is kept or dropped as a
     * whole, never split.
     */
    static String tileName(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        final int n = raw.length();
        StringBuilder line = new StringBuilder(Math.min(n, 48));
        boolean gapPending = false;

        int i = 0;
        while (i < n) {
            char unit = raw.charAt(i);
            if (isBlankUnit(unit)) {
                // Only matters between two visible characters; leading gaps are never emitted.
                gapPending = line.length() > 0;
                i++;
                continue;
            }
            // A high surrogate directly followed by a low one travels as a pair.
            int width = Character.charCount(raw.codePointAt(i));
            int separator = gapPending ? 1 : 0;
            if (line.length() + separator + width > TILE_NAME_LIMIT) {
                break;
            }
            if (gapPending) {
                line.append(' ');
                gapPending = false;
            }
            line.append(raw, i, i + width);
            i += width;
        }
        return line.length() == 0 ? fallback : line.toString();
    }

    /**
     * True for a UTF-16 unit that should read as blank: C0/C1 controls and DEL, and anything of the
     * space, line-separator, paragraph-separator or format (Cf) categories. The test is made per
     * unit, not per code point, so a supplementary character is never treated as a gap.
     */
    private static boolean isBlankUnit(char unit) {
        if (Character.isISOControl(unit)) {
            return true;
        }
        switch (Character.getType(unit)) {
            case Character.SPACE_SEPARATOR:
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
            case Character.FORMAT:
                return true;
            default:
                return false;
        }
    }
}
