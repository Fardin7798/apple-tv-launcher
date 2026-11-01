package com.spidertv.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * The Spider theme's silk, drawn exactly like the FigCraft design (FigCraft/designs/spider/scene.py):
 * the web line from the hero's hand (a sagging curve, a thinner strand twisting round it, a looped knot,
 * three fibres and a blob where it sticks), the hanging threads, web splats and speed lines.
 * All numbers are in design px (1920x1080); the caller scales the canvas.
 */
final class SilkRope {
    /** FigCraft blur radius r draws a gaussian of sigma r/2; Android's radius r is sigma 0.57735 r + 0.5. */
    static float androidBlurRadius(float figcraftRadius) {
        float sigma = figcraftRadius / 2f;
        return Math.max(0.1f, (sigma - 0.5f) / 0.57735f);
    }

    private static final int GLOW = 0x99FFFFFF;
    private static final float GLOW_R = androidBlurRadius(7f);

    private final Paint main = stroke(0xFFFFFFFF);
    private final Paint strand = stroke(0xFFE9E6FF);
    private final Paint knot = stroke(0xFFFFFFFF);
    private final Paint fibre = stroke(0xFFFFFFFF);
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thread = stroke(0xD9FFFFFF);
    private final Paint web = stroke(0xFFF4F2FF);
    private final Path path = new Path();

    SilkRope() {
        main.setShadowLayer(GLOW_R, 0, 0, GLOW);
        knot.setShadowLayer(GLOW_R, 0, 0, GLOW);
        blob.setColor(0xFFFFFFFF);
        blob.setShadowLayer(GLOW_R, 0, 0, GLOW);
        fibre.setStrokeWidth(2f);
        thread.setStrokeWidth(2.4f);
        web.setStrokeCap(Paint.Cap.BUTT);
    }

    private static Paint stroke(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    private static int a(float alpha, int baseAlpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * baseAlpha)));
    }

    /** A hanging thread (design: 2.4 px, #FFFFFFD9, round caps). */
    void thread(Canvas c, float x0, float y0, float x1, float y1, float alpha) {
        if (alpha <= 0.004f) return;
        thread.setAlpha(a(alpha, 0xD9));
        c.drawLine(x0, y0, x1, y1, thread);
    }

    /**
     * The web line, design px. sag: fraction of its length the middle drops; twist: strand amplitude;
     * grow: 0..1 how much of it has been shot out (the knot only shows once it has landed).
     */
    void rope(Canvas c, float X0, float Y0, float X1, float Y1, float sag, float twist, boolean withKnot,
              float w, float alpha, float grow) {
        if (alpha <= 0.004f || grow <= 0f) return;
        float L = (float) Math.hypot(X1 - X0, Y1 - Y0);
        float mx = (X0 + X1) / 2f, my = (Y0 + Y1) / 2f + sag * L;
        float tEnd = Math.min(1f, grow);
        // main strand
        main.setStrokeWidth(w);
        main.setAlpha(a(alpha, 255));
        main.setShadowLayer(GLOW_R, 0, 0, (a(alpha, 0x99) << 24) | 0xFFFFFF);
        path.reset();
        if (tEnd >= 1f) {
            path.moveTo(X0, Y0);
            path.quadTo(mx, my, X1, Y1);
        } else {
            int n = 24;
            for (int i = 0; i <= n; i++) {
                float t = tEnd * i / n;
                float px = qx(t, X0, mx, X1), py = qx(t, Y0, my, Y1);
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
        }
        c.drawPath(path, main);
        // twisting second strand
        int n = 40;
        float amp = 3.2f * twist;
        float turns = Math.max(2f, L / 38f);
        path.reset();
        int last = Math.round(n * tEnd);
        for (int i = 0; i <= last; i++) {
            float t = i / (float) n;
            float px = qx(t, X0, mx, X1), py = qx(t, Y0, my, Y1);
            float dx = 2 * (1 - t) * (mx - X0) + 2 * t * (X1 - mx), dy = 2 * (1 - t) * (my - Y0) + 2 * t * (Y1 - my);
            float l = (float) Math.hypot(dx, dy);
            if (l == 0) l = 1;
            float nx = -dy / l, ny = dx / l;
            float am = (float) (amp * Math.sin(t * turns * 2 * Math.PI)) * Math.min(1f, Math.min(t * 6f, (1 - t) * 6f + 0.3f));
            if (i == 0) path.moveTo(px + nx * am, py + ny * am); else path.lineTo(px + nx * am, py + ny * am);
        }
        strand.setStrokeWidth(w * 0.45f);
        strand.setAlpha(a(alpha, Math.round(0.85f * 255)));
        c.drawPath(path, strand);
        if (!withKnot || grow < 1f) return;
        float t = 0.84f;
        float kx = qx(t, X0, mx, X1), ky = qx(t, Y0, my, Y1);
        float tdx = 2 * (1 - t) * (mx - X0) + 2 * t * (X1 - mx), tdy = 2 * (1 - t) * (my - Y0) + 2 * t * (Y1 - my);
        float tl = (float) Math.hypot(tdx, tdy);
        if (tl == 0) tl = 1;
        float nx = -tdy / tl, ny = tdx / tl;
        float dx = X1 - kx, dy = Y1 - ky;
        float r = 13f;
        path.reset();
        path.moveTo(kx, ky);
        path.cubicTo(kx + nx * r * 1.6f + dx * 0.2f, ky + ny * r * 1.6f + dy * 0.2f,
                kx + nx * r * 1.4f + dx * 1.0f, ky + ny * r * 1.4f + dy * 1.0f,
                kx + dx * 0.55f, ky + dy * 0.55f);
        path.cubicTo(kx - nx * r * 1.2f + dx * 0.3f, ky - ny * r * 1.2f + dy * 0.3f,
                kx - nx * r * 0.8f + dx * 0.9f, ky - ny * r * 0.8f + dy * 0.9f, X1, Y1);
        knot.setStrokeWidth(w * 0.7f);
        knot.setAlpha(a(alpha, 255));
        knot.setShadowLayer(GLOW_R, 0, 0, (a(alpha, 0x99) << 24) | 0xFFFFFF);
        c.drawPath(path, knot);
        float ul = (float) Math.hypot(dx, dy);
        if (ul == 0) ul = 1;
        float ux = dx / ul, uy = dy / ul;
        fibre.setAlpha(a(alpha, Math.round(0.9f * 255)));
        for (float ang : new float[]{-0.9f, 0f, 0.9f}) {
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            float fx = ux * ca - uy * sa, fy = ux * sa + uy * ca;
            c.drawLine(X1, Y1, X1 + fx * 14f, Y1 + fy * 14f, fibre);
        }
        blob.setAlpha(a(alpha, 255));
        blob.setShadowLayer(GLOW_R, 0, 0, (a(alpha, 0x99) << 24) | 0xFFFFFF);
        c.drawCircle(X1, Y1, 5f, blob);
    }

    private static float qx(float t, float a, float m, float b) {
        return (1 - t) * (1 - t) * a + 2 * (1 - t) * t * m + t * t * b;
    }

    /** A full-circle web splat (design: 12 spokes, rings at 22/44/70/100 %, 20 % sag, #F4F2FF at 90 %). */
    void splat(Canvas c, float cx, float cy, float rmax, float weight, float alpha) {
        if (alpha <= 0.004f) return;
        float[] radii = {rmax * 0.22f, rmax * 0.44f, rmax * 0.70f, rmax};
        int spokes = 12;
        float sag = 0.2f;
        double[] angs = new double[spokes];
        for (int i = 0; i < spokes; i++) angs[i] = 2 * Math.PI * i / (spokes - 1);
        path.reset();
        float rm = radii[3] * 1.08f;
        for (double ang : angs) {
            path.moveTo(cx, cy);
            path.lineTo(cx + rm * (float) Math.cos(ang), cy + rm * (float) Math.sin(ang));
        }
        for (float r : radii) {
            path.moveTo(cx + r * (float) Math.cos(angs[0]), cy + r * (float) Math.sin(angs[0]));
            for (int i = 1; i < spokes; i++) {
                double am = (angs[i - 1] + angs[i]) / 2;
                float rc = r * (1 - sag);
                path.quadTo(cx + rc * (float) Math.cos(am), cy + rc * (float) Math.sin(am),
                        cx + r * (float) Math.cos(angs[i]), cy + r * (float) Math.sin(angs[i]));
            }
        }
        web.setStrokeWidth(weight);
        web.setAlpha(a(alpha, Math.round(0.9f * 255)));
        c.drawPath(path, web);
    }

    /** Motion streaks beside a flung tile; dir -1 draws them to the left. */
    void speedLines(Canvas c, float x0, float y0, int n, float length, float gap, float alpha, int dir) {
        if (alpha <= 0.004f) return;
        Paint p = fibre;
        p.setStrokeWidth(2f);
        p.setAlpha(a(alpha, 255));
        for (int i = 0; i < n; i++) {
            float yy = y0 + i * gap;
            float xx = x0 + dir * (i % 2) * 18f * SpiderCarousel.S;
            float len = (length - (i % 3) * 12f) * SpiderCarousel.S;
            c.drawLine(xx, yy, xx + dir * len, yy, p);
        }
    }
}
