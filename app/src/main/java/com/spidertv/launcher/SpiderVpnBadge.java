package com.spidertv.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Rounded VPN tag drawn after the network name in the Spider TV network pill.
 *
 * With a padlock drawable it renders the "on" tag (padlock plus dark label on green); with null it
 * renders the "off" tag (white label on red, no padlock).
 */
final class SpiderVpnBadge extends android.text.style.ReplacementSpan {

    private static final int GREEN_FILL = 0xFF30D158;
    private static final int RED_FILL = 0xFFFF453A;
    private static final int DARK_INK = 0xFF06290F;
    private static final int WHITE_INK = 0xFFFFFFFF;

    /** Sizes as fractions of the surrounding text size. */
    private static final float INK_RATIO = 0.78f;
    private static final float PADLOCK_RATIO = 0.8f;
    private static final float PILL_RATIO = 1.3f;

    private final float unit;
    private final Drawable padlock;
    private final int fill;
    private final int ink;
    private final RectF bounds = new RectF();

    /** @param lockIcon padlock for the "on" tag; null gives the "off" tag */
    SpiderVpnBadge(Drawable lockIcon, float density) {
        unit = density;
        if (lockIcon == null) {
            fill = RED_FILL;
            ink = WHITE_INK;
            padlock = null;
        } else {
            fill = GREEN_FILL;
            ink = DARK_INK;
            padlock = lockIcon.mutate();
            padlock.setTint(ink);
        }
    }

    private float padlockSize(float baseSize) {
        return padlock != null ? baseSize * PADLOCK_RATIO : 0;
    }

    /** Gap in front of the label: after the padlock, or after the left inset when there is none. */
    private float inkGap() {
        return padlock != null ? 4 * unit : 2 * unit;
    }

    @Override
    public int getSize(@androidx.annotation.NonNull Paint p, CharSequence s, int from, int to,
                       Paint.FontMetricsInt metricsOut) {
        final float base = p.getTextSize();
        p.setTextSize(base * INK_RATIO);
        final float inkWidth = p.measureText(s, from, to);
        p.setTextSize(base);
        // Added left to right exactly as drawn: margin, left inset, padlock, gap, label, right inset.
        float width = 8 * unit;
        width += 7 * unit;
        width += padlockSize(p.getTextSize());
        width += inkGap();
        width += inkWidth;
        width += 9 * unit;
        return (int) width;
    }

    @Override
    public void draw(@androidx.annotation.NonNull Canvas c, CharSequence s, int from, int to, float left,
                     int lineTop, int baselineY, int lineBottom, @androidx.annotation.NonNull Paint p) {
        final float base = p.getTextSize();
        final float lockPx = padlockSize(base);
        final float radius = base * PILL_RATIO / 2f;
        final float midY = (lineTop + lineBottom) / 2f;
        final float pillStart = left + 8 * unit;
        final float pillEnd = left + getSize(p, s, from, to, null);
        final int previousColor = p.getColor();

        bounds.set(pillStart, midY - radius, pillEnd, midY + radius);
        p.setColor(fill);
        c.drawRoundRect(bounds, radius, radius, p);

        final int innerStart = (int) (pillStart + 7 * unit);
        if (padlock != null) {
            final float half = lockPx / 2f;
            padlock.setBounds(innerStart, (int) (midY - half), (int) (innerStart + lockPx), (int) (midY + half));
            padlock.draw(c);
        }

        p.setColor(ink);
        p.setTextSize(p.getTextSize() * INK_RATIO);
        final Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, from, to, innerStart + lockPx + inkGap(), midY - (fm.ascent + fm.descent) / 2f, p);

        p.setTextSize(base);
        p.setColor(previousColor);
    }
}
