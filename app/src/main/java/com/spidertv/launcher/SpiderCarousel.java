package com.spidertv.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * The Spider theme's app row: tiles hanging on threads in a wave, the focused one in the centre slot
 * under the hero, who holds it with a web line. Right/Left: the hero shoots silk at the next app,
 * yanks it into the centre and flings the old one aside, where it swings on its thread (Shoot, Pull,
 * Settle keyframes of the FigCraft design). Everything is drawn in design px (1920x1080) and scaled.
 */
final class SpiderCarousel extends View {

    /** Concept (1376x768) to design px (1920x1080). */
    static final float S = 1920f / 1376f;

    static float s(float v) {
        return v * S;
    }

    /** Slot centres and sizes, concept coords; 3 is the focus slot, 0 and 6 are just off the row. */
    private static final float[][] SLOT = {
            {404f, 432f, 115f}, {564.5f, 527.5f, 115f}, {724.5f, 432.5f, 115f}, {900.5f, 531.5f, 177f},
            {1076.5f, 432.5f, 115f}, {1245.5f, 527.5f, 115f}, {1416f, 432.5f, 115f}};
    private static final int CENTER = 3;
    /** Keyframe boundaries in the step's 0..1 progress: Shoot until P1, Pull until P2, then Settle. */
    private static final float P1 = 0.18f, P2 = 0.62f;
    /** Hero (concept): thread x, crop scale, top; the thread sits 140 crop px from each crop's left edge. */
    private static final float HERO_X = 889f, HERO_K = 0.396f, HERO_TOP = 122f;

    static final class Item {
        static final int APP = 0, SETTINGS = 1;
        final int kind;
        final String label;
        final String pkg;
        final ComponentName component;
        final Drawable icon;
        /** Icon art from the design (spider_icon_*): box as fractions of the tile size, else null. */
        final float[] designBox;
        /** Spotify keeps its dark card from the design. */
        final boolean darkCard;

        Item(int kind, String label, ComponentName component, Drawable icon, float[] designBox, boolean darkCard) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component != null ? component.getPackageName() : "";
            this.icon = icon;
            this.designBox = designBox;
            this.darkCard = darkCard;
        }

        String key() {
            return component != null ? component.flattenToShortString() : "spider-settings";
        }
    }

    interface Listener {
        /** The centre app changed (animated: during a step, as the new app is pulled in). */
        void onFocusApp(Item item, boolean animated);

        void onLaunch(Item item);

        void onLongPress(Item item);

        /** D-pad Up from the row. */
        void onUp();
    }

    private final List<Item> items = new ArrayList<>();
    private int focus;
    private Listener listener;
    private final SilkRope silk = new SilkRope();

    // step animation
    private ValueAnimator anim;
    private float p;
    private int dir;          // +1 Right, -1 Left
    private int fromFocus;
    private int queued;
    private boolean titleSent;
    private boolean animations = true, reducedMotion;

    // art
    private final Bitmap heroHang, heroShoot, heroPull;
    private Bitmap shadowBmp, glowBmp;
    private float shadowPad, glowPad;
    private final float baseW = s(115f), focusW = s(177f);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final LinearGradient glass, focusFill, dark;
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final Path gear = new Path();
    private final Path clip = new Path();
    private final Path iconClip = new Path();
    private final RectF iconRect = new RectF();
    /** Gear face, unit gradient (-1..1) scaled to the gear's radius. */
    private final LinearGradient gearFill = new LinearGradient(0, -1f, 0, 1f, 0xFFD9DBE3, 0xFF9A9DAA, Shader.TileMode.CLAMP);
    private final android.graphics.CornerPathEffect gearCorners = new android.graphics.CornerPathEffect(2f);
    private final T st = new T(), gh = new T();

    /** One tile's pose for a frame, concept coords except where noted. */
    private static final class T {
        float cx, cy, sz, rot, alpha, f, anchor, threadAlpha;
        float sfx, sfy, sfr, splatAlpha;
        boolean ghost;
    }

    SpiderCarousel(Context ctx) {
        super(ctx);
        setFocusable(true);
        setFocusableInTouchMode(true);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        heroHang = BitmapFactory.decodeResource(getResources(), R.drawable.spider_hero_hang, o);
        heroShoot = BitmapFactory.decodeResource(getResources(), R.drawable.spider_hero_shoot, o);
        heroPull = BitmapFactory.decodeResource(getResources(), R.drawable.spider_hero_pull, o);
        // unit-square gradients (-0.5..0.5), scaled to each tile with a local matrix
        glass = new LinearGradient(0, -0.5f, 0, 0.5f, 0xFF3A3458, 0xFF37305A, Shader.TileMode.CLAMP);
        dark = new LinearGradient(0, -0.5f, 0, 0.5f, 0xFF221A1B, 0xFF151012, Shader.TileMode.CLAMP);
        focusFill = new LinearGradient(-0.5f, -0.5f, 0.5f, 0.5f, new int[]{0xFF736C90, 0xFF5A4C86, 0xFF4F3F7A},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        stroke.setStyle(Paint.Style.STROKE);
        buildEffects();
    }

    /** Drop shadow (unfocused) and two-layer focus glow, blurred once in software like FigCraft does. */
    private void buildEffects() {
        float rad = s(20f);
        float sigma = s(22f) / 2f;
        shadowPad = (float) Math.ceil(sigma * 3.2f);
        int sw = (int) Math.ceil(baseW + 2 * shadowPad);
        shadowBmp = Bitmap.createBitmap(sw, sw, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(shadowBmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x8C000000);
        p.setMaskFilter(new BlurMaskFilter(SilkRope.androidBlurRadius(s(22f)), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(shadowPad, shadowPad, shadowPad + baseW, shadowPad + baseW, rad, rad, p);

        float out = s(4f);   // the white outline sits outside the card; the glow comes off its outer edge
        float frad = s(24f) + out;
        float big = s(44f) / 2f;
        glowPad = (float) Math.ceil(big * 3.2f + out);
        int gw = (int) Math.ceil(focusW + 2 * glowPad);
        glowBmp = Bitmap.createBitmap(gw, gw, Bitmap.Config.ARGB_8888);
        c = new Canvas(glowBmp);
        float l = glowPad - out, t = glowPad - out, rgt = glowPad + focusW + out, b = glowPad + focusW + out;
        p.setColor(0x66B9A8FF);
        p.setMaskFilter(new BlurMaskFilter(SilkRope.androidBlurRadius(s(44f)), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(l, t, rgt, b, frad, frad, p);
        p.setColor(0xB3FFFFFF);
        p.setMaskFilter(new BlurMaskFilter(SilkRope.androidBlurRadius(s(22f)), BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(l, t, rgt, b, frad, frad, p);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setMotion(boolean animations, boolean reducedMotion) {
        this.animations = animations;
        this.reducedMotion = reducedMotion;
    }

    /** New app list; focus follows the app with this key when it is still there. */
    void setItems(List<Item> list, String focusKey) {
        if (anim != null) anim.cancel();
        items.clear();
        items.addAll(list);
        int at = 0;
        for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(focusKey)) at = i;
        focus = items.isEmpty() ? 0 : at;
        if (listener != null && !items.isEmpty()) listener.onFocusApp(items.get(focus), false);
        invalidate();
    }

    Item focused() {
        return items.isEmpty() ? null : items.get(focus);
    }

    void focusFirst() {
        if (items.isEmpty()) return;
        if (anim != null) anim.cancel();
        focus = 0;
        if (listener != null) listener.onFocusApp(items.get(0), false);
        invalidate();
    }

    /** The row wraps round like a carousel once it can fill all five visible slots. */
    private boolean wraps() {
        return items.size() >= 5;
    }

    private Item itemAt(int index) {
        int n = items.size();
        if (n == 0) return null;
        if (wraps()) return items.get(((index % n) + n) % n);
        return index >= 0 && index < n ? items.get(index) : null;
    }

    private boolean canStep(int d) {
        if (items.size() < 2) return false;
        return wraps() || (focus + d >= 0 && focus + d < items.size());
    }

    // ---- keys ------------------------------------------------------------------------------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_LEFT: {
                int d = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) d = -d;
                step(d, event.getRepeatCount() > 0);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (listener != null) listener.onUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                event.startTracking();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (isSelectKey(keyCode)) {
            Item it = focused();
            if (it != null && listener != null) listener.onLongPress(it);
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isSelectKey(keyCode)) {
            if (event.isTracking() && !event.isCanceled() && !event.isLongPress()) {
                Item it = settledFocus();
                if (it != null && listener != null) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    listener.onLaunch(it);
                }
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private static boolean isSelectKey(int k) {
        return k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /** OK during a step opens the app being pulled in, not the one being thrown away. */
    private Item settledFocus() {
        return focused();
    }

    private void step(int d, boolean held) {
        if (!canStep(d)) return;
        if (anim != null && anim.isRunning()) {
            if (d == dir) {
                queued = Math.min(1, queued + 1);
                if (held) anim.end();
            } else {
                anim.end();
                queued = 0;
                start(d, true);
            }
            return;
        }
        start(d, held);
    }

    private void start(int d, boolean fast) {
        if (!canStep(d)) return;
        dir = d;
        fromFocus = focus;
        int n = items.size();
        focus = wraps() ? ((focus + d) % n + n) % n : focus + d;
        playSoundEffect(d > 0 ? SoundEffectConstants.NAVIGATION_RIGHT : SoundEffectConstants.NAVIGATION_LEFT);
        if (!animations) {
            p = 1f;
            anim = null;
            if (listener != null) listener.onFocusApp(items.get(focus), false);
            invalidate();
            return;
        }
        titleSent = false;
        long ms = reducedMotion ? 420 : fast ? 340 : 900;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(ms);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            p = (float) a.getAnimatedValue();
            if (!titleSent && p >= P1) {
                titleSent = true;
                if (listener != null) listener.onFocusApp(items.get(focus), true);
            }
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                p = 1f;
                if (!titleSent) {
                    titleSent = true;
                    if (listener != null) listener.onFocusApp(items.get(focus), true);
                }
                anim = null;
                invalidate();
                if (!cancelled && queued > 0) {
                    queued--;
                    start(dir, true);
                }
            }
        });
        anim.start();
    }

    private boolean animating() {
        return anim != null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.scale(getWidth() / 1920f, getHeight() / 1080f);
        if (animating()) drawStep(canvas); else drawRest(canvas);
        canvas.restore();
    }

    private void rest(T t, int slot) {
        t.cx = SLOT[slot][0];
        t.cy = SLOT[slot][1];
        t.sz = SLOT[slot][2];
        t.rot = 0;
        t.alpha = 1;
        t.f = slot == CENTER ? 1 : 0;
        t.threadAlpha = 1 - t.f;
        t.anchor = topX(t);
        t.ghost = false;
        t.splatAlpha = slot == CENTER ? 1 : 0;
        t.sfx = 0.76f;
        t.sfy = 0.20f;
        t.sfr = 0.27f;
    }

    private void drawRest(Canvas c) {
        for (int slot = 1; slot <= 5; slot++) {
            Item it = itemAt(focus + slot - CENTER);
            if (it == null) continue;
            rest(st, slot);
            drawTile(c, it, st);
        }
        drawHero(c, heroHang, false);
        if (focused() != null) {
            rest(st, CENTER);
            float[] h = hand(124, 662, heroHang, false);
            silk.rope(c, s(h[0]), s(h[1]), s(topX(st) + 2.5f), s(topY(st) + 4f), 0.04f, 1f, true, 3.6f, 1f, 1f);
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOut(float x) {
        return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    private static float easeOut(float x) {
        return 1 - (1 - x) * (1 - x);
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    private static float sinPi(float x) {
        return (float) Math.sin(Math.PI * x);
    }

    /** Pose of the tile that sat at offset r from the old focus, at pull progress e and settle progress v. */
    private void pose(T t, int r, float e, float v) {
        int a = CENTER + r, b = a - dir;
        boolean target = r == dir, flung = r == 0;
        // The thrown tile leaves faster than the new one arrives (Pull keyframe: it is ~3/4 of the way out
        // while the pulled one is halfway in).
        if (flung) e = easeOut(e);
        t.cx = lerp(SLOT[a][0], SLOT[b][0], e);
        t.sz = lerp(SLOT[a][2], SLOT[b][2], e);
        float dip = target ? 28f : flung ? 30f : 8f;
        t.cy = lerp(SLOT[a][1], SLOT[b][1], e) + dip * sinPi(e);
        float sg = dir;
        if (target) {
            t.rot = 13f * sg * sinPi(e);
        } else if (flung) {
            t.rot = e < 1 ? 18f * sg * sinPi((float) Math.pow(e, 2.4)) : 0f;
            t.rot += -10f * sg * (float) Math.sin(1.5 * Math.PI * v) * (1 - v);
        } else {
            float alt = ((r + 10) % 2 == 0 ? 1f : -1f) * sg;
            t.rot = 3.5f * alt * (float) Math.sin(1.5 * Math.PI * v) * (1 - v);
        }
        t.alpha = (b == 0 || b == 6) ? 1 - e : (a == 0 || a == 6) ? e : 1;
        // Neither glows mid-flight (Pull keyframe): the old outline drops at once, the new one lights on arrival.
        t.f = target ? smooth(clamp((e - 0.7f) / 0.3f)) : flung ? 1 - clamp(e / 0.3f) : 0;
        t.threadAlpha = 1 - t.f;
        t.anchor = t.cx + 0.85f * t.rot + (SLOT[a][0] - t.cx) * 0.6f * (1 - e * e * e);
        t.ghost = false;
        if (flung) {
            t.sfx = 0.76f;
            t.sfy = 0.20f;
            t.sfr = 0.27f;
            t.splatAlpha = 1 - clamp((p - P1) / 0.06f);
        } else if (target) {
            float m = dir > 0 ? 0 : 1;   // mirrored for Left
            float shootX = Math.abs(m - 0.14f), pullX = Math.abs(m - 0.18f);
            t.splatAlpha = clamp((p - 0.12f) / 0.04f);
            if (p < P2) {
                t.sfx = lerp(shootX, pullX, e);
                t.sfy = 0.18f;
                t.sfr = lerp(0.30f, 0.32f, e);
            } else {
                float k = easeOut(v);
                t.sfx = lerp(pullX, 0.76f, k);
                t.sfy = lerp(0.18f, 0.20f, k);
                t.sfr = lerp(0.32f, 0.27f, k);
            }
        } else {
            t.splatAlpha = 0;
        }
    }

    private void drawStep(Canvas c) {
        float e = easeInOut(clamp((p - P1) / (P2 - P1)));
        float v = clamp((p - P2) / (1 - P2));
        float pullWave = (p > P1 && p < P2) ? sinPi(e) : 0f;
        int rMin = dir > 0 ? -2 : -3, rMax = dir > 0 ? 3 : 2;
        // ordinary tiles first, then the flung one, then the one being pulled in
        for (int r = rMin; r <= rMax; r++) {
            if (r == 0 || r == dir) continue;
            Item it = itemAt(fromFocus + r);
            if (it == null) continue;
            pose(st, r, e, v);
            drawTile(c, it, st);
        }
        for (int r : new int[]{0, dir}) {
            Item it = itemAt(fromFocus + r);
            if (it == null) continue;
            if (!reducedMotion && pullWave > 0.01f) {
                float[] alphas = r == 0 ? new float[]{0.30f, 0.18f, 0.10f} : new float[]{0.22f, 0.12f};
                for (int k = alphas.length; k >= 1; k--) {
                    float ek = Math.max(0f, e - 0.09f * k);
                    pose(gh, r, ek, 0f);
                    gh.ghost = true;
                    gh.alpha *= alphas[k - 1] * pullWave;
                    gh.threadAlpha = 0;
                    gh.splatAlpha = 0;
                    drawTile(c, it, gh);
                }
            }
            pose(st, r, e, v);
            drawTile(c, it, st);
            if (!reducedMotion && pullWave > 0.01f) {
                if (r == 0) {
                    silk.speedLines(c, s(st.cx + dir * 84f), s(st.cy - 16f), 4, 70f, s(18f), 0.4f * pullWave, dir);
                } else {
                    silk.speedLines(c, s(st.cx + dir * 100f), s(st.cy - 42f), 3, 55f, s(18f), 0.3f * pullWave, dir);
                }
            }
        }

        // hero and silk
        Item target = itemAt(fromFocus + dir), old = itemAt(fromFocus);
        T tt = st, to = gh;
        pose(tt, dir, e, v);
        pose(to, 0, e, v);
        boolean mirror = dir < 0;
        if (p < P1) {
            drawHero(c, heroShoot, mirror);
            float q = clamp(p / (P1 * 0.75f));
            float[] h = hand(449, 378, heroShoot, mirror);
            float half = tt.sz / 2f;
            float ex = dir > 0 ? tt.cx - half + 15f : tt.cx + half - 15f, ey = tt.cy - half + 18f;
            if (old != null) {
                float k = p / P1;
                silk.rope(c, s(HERO_X), s(352f), s(topX(to) + 2.5f), s(topY(to) + 4f), lerp(0.04f, 0.18f, k), 1f,
                        false, lerp(3.6f, 2.6f, k), 1f, 1f);
            }
            if (target != null) {
                silk.rope(c, s(h[0]), s(h[1]), s(ex), s(ey), 0.22f, 1f, true, 3.6f, 1f, easeOut(q));
            }
        } else if (p < P2) {
            drawHero(c, heroPull, mirror);
            float[] h = hand(262, 255, heroPull, mirror);
            if (old != null) {
                silk.rope(c, s(HERO_X), s(352f), s(topX(to) + 2.5f), s(topY(to) + 4f), 0.25f, 1f, false, 2.6f,
                        1 - clamp((p - P1) / 0.1f), 1f);
            }
            if (target != null) {
                silk.rope(c, s(h[0]), s(h[1]), s(topX(tt) + 4f), s(topY(tt) + 6f), 0f, 0.6f, true, 4f, 1f, 1f);
            }
        } else {
            drawHero(c, heroHang, false);
            float[] h = hand(124, 662, heroHang, false);
            if (target != null) {
                silk.rope(c, s(h[0]), s(h[1]), s(topX(tt) + 2.5f), s(topY(tt) + 4f), lerp(0.12f, 0.04f, easeOut(v)),
                        1f, true, 3.6f, 1f, 1f);
            }
        }
    }

    private static float topX(T t) {
        return t.cx - (t.sz / 2f) * (float) Math.sin(Math.toRadians(t.rot));
    }

    private static float topY(T t) {
        return t.cy - (t.sz / 2f) * (float) Math.cos(Math.toRadians(t.rot));
    }

    /** A point on a hero pose (crop px) in concept coords. */
    private float[] hand(float ax, float ay, Bitmap pose, boolean mirror) {
        float left = HERO_X - 140f * HERO_K;
        float x = left + ax * HERO_K, y = HERO_TOP + ay * HERO_K;
        if (mirror) x = 2 * HERO_X - x;
        return new float[]{x, y};
    }

    private void drawHero(Canvas c, Bitmap pose, boolean mirror) {
        silk.thread(c, s(HERO_X), s(-2f), s(HERO_X), s(HERO_TOP + 6f), 1f);
        if (pose == null) return;
        float left = HERO_X - 140f * HERO_K;
        r.set(s(left), s(HERO_TOP), s(left + pose.getWidth() * HERO_K), s(HERO_TOP + pose.getHeight() * HERO_K));
        c.save();
        if (mirror) c.scale(-1f, 1f, s(HERO_X), 0f);
        bmp.setAlpha(255);
        c.drawBitmap(pose, null, r, bmp);
        c.restore();
    }

    private void drawTile(Canvas c, Item it, T t) {
        if (t.alpha <= 0.004f) return;
        float cx = s(t.cx), cy = s(t.cy), w = s(t.sz), h = w / 2f;
        float f = t.f;
        if (!t.ghost && t.threadAlpha > 0.004f) {
            silk.thread(c, s(t.anchor), s(-2f), s(topX(t)), s(topY(t) + 2f), t.alpha * t.threadAlpha);
        }
        int save = c.save();
        boolean layer = t.alpha < 0.999f;
        if (layer) {
            float reach = h + glowPad * (w / focusW) + 40f;
            c.saveLayerAlpha(cx - reach, cy - reach, cx + reach, cy + reach, Math.round(t.alpha * 255));
        }
        c.translate(cx, cy);
        c.rotate(-t.rot);
        float rad = s(20f + 4f * f);
        if (!t.ghost) {
            if (f < 0.999f) {
                float k = w / baseW;
                m.setScale(k, k);
                m.postTranslate(-h - shadowPad * k, -h - shadowPad * k + s(8f) * k);
                bmp.setAlpha(Math.round(255 * (1 - f)));
                c.drawBitmap(shadowBmp, m, bmp);
            }
            if (f > 0.001f) {
                float k = w / focusW;
                m.setScale(k, k);
                m.postTranslate(-h - glowPad * k, -h - glowPad * k);
                bmp.setAlpha(Math.round(255 * f));
                c.drawBitmap(glowBmp, m, bmp);
            }
        }
        // card
        Shader base = it.darkCard ? dark : glass;
        m.setScale(w, w);
        base.setLocalMatrix(m);
        fill.setShader(base);
        fill.setAlpha(255);
        r.set(-h, -h, h, h);
        c.drawRoundRect(r, rad, rad, fill);
        if (f > 0.001f) {
            focusFill.setLocalMatrix(m);
            fill.setShader(focusFill);
            fill.setAlpha(Math.round(255 * f));
            c.drawRoundRect(r, rad, rad, fill);
        }
        fill.setShader(null);
        // icon and splat, clipped to the card
        c.save();
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        drawIcon(c, it, w, h);
        if (t.splatAlpha > 0.004f) {
            silk.splat(c, -h + w * t.sfx, -h + w * t.sfy, w * t.sfr, t.sz > 150 ? 2.0f : 1.6f, t.splatAlpha);
        }
        c.restore();
        // rim (inside, unfocused) and white outline (outside, focused)
        if (f < 0.999f) {
            stroke.setStrokeWidth(1.5f);
            stroke.setColor(0x26FFFFFF);
            stroke.setAlpha(Math.round(0x26 * (1 - f)));
            r.set(-h + 0.75f, -h + 0.75f, h - 0.75f, h - 0.75f);
            c.drawRoundRect(r, rad - 0.75f, rad - 0.75f, stroke);
        }
        if (f > 0.001f) {
            float ow = s(4f);
            stroke.setStrokeWidth(ow);
            stroke.setColor(0xFFFFFFFF);
            stroke.setAlpha(Math.round(255 * f));
            r.set(-h - ow / 2, -h - ow / 2, h + ow / 2, h + ow / 2);
            c.drawRoundRect(r, rad + ow / 2, rad + ow / 2, stroke);
        }
        c.restoreToCount(save);
    }

    private void drawIcon(Canvas c, Item it, float w, float h) {
        if (it.kind == Item.SETTINGS) {
            drawGear(c, w);
            return;
        }
        Drawable d = it.icon;
        if (d == null) return;
        float x, y, bw, bh;
        if (it.designBox != null) {
            x = -h + it.designBox[0] * w;
            y = -h + it.designBox[1] * w;
            bw = it.designBox[2] * w;
            bh = it.designBox[3] * w;
        } else {
            // an installed app's own icon, centred at the size of the design's icon art
            bw = bh = w * 0.62f;
            x = -bw / 2;
            y = -bh / 2;
        }
        int iw = 512, ih = Math.max(1, Math.round(512 * bh / bw));
        d.setBounds(0, 0, iw, ih);
        c.save();
        c.translate(x, y);
        c.scale(bw / iw, bh / ih);
        if (it.designBox == null) {
            iconClip.reset();
            float iconRad = Math.min(iw, ih) * 0.22f;
            iconRect.set(0, 0, iw, ih);
            iconClip.addRoundRect(iconRect, iconRad, iconRad, Path.Direction.CW);
            c.clipPath(iconClip);
        }
        d.setAlpha(255);
        d.draw(c);
        c.restore();
    }

    /** The design's Settings tile: ten-point gear, ring and hole (scene.py, icon_settings). */
    private void drawGear(Canvas c, float t) {
        float g = t * 0.62f, R = g / 2f, ri = R * 0.78f;
        gear.reset();
        for (int i = 0; i < 20; i++) {
            double a = -Math.PI / 2 + Math.PI * i / 10;
            float rr = i % 2 == 0 ? R : ri;
            float px = rr * (float) Math.cos(a), py = rr * (float) Math.sin(a);
            if (i == 0) gear.moveTo(px, py); else gear.lineTo(px, py);
        }
        gear.close();
        m.setScale(R, R);
        gearFill.setLocalMatrix(m);
        fill.setShader(gearFill);
        fill.setAlpha(255);
        fill.setPathEffect(gearCorners);
        c.drawPath(gear, fill);
        fill.setPathEffect(null);
        fill.setShader(null);
        float cr = g * 0.62f / 2f;
        fill.setColor(0xFFB7BAC6);
        c.drawCircle(0, 0, cr, fill);
        stroke.setColor(0xFF7E8190);
        stroke.setStrokeWidth(2f);
        c.drawCircle(0, 0, cr, stroke);
        fill.setColor(0xFF3A3458);
        c.drawCircle(0, 0, g * 0.26f / 2f, fill);
        fill.setColor(0xFFFFFFFF);
    }
}
