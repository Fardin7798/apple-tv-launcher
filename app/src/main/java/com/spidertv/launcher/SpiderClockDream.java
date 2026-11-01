package com.spidertv.launcher;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * Spider TV screensaver.
 *
 * Draws the time and date (layout spider_dream_clock) over a soft radial glow on black. Every minute the
 * text fades away, comes back at another spot, and the glow eases to the next hue, so no pixel stays lit
 * long enough to burn in. No network, no bitmaps.
 */
public class SpiderClockDream extends android.service.dreams.DreamService {

    private static final long TICK_MS = 60_000L;
    private static final long APPEAR_MS = 1200;
    private static final long VANISH_MS = 900;
    private static final long HUE_SHIFT_MS = 8000;
    /** Glow radius relative to the view's width. */
    private static final float GLOW_SPREAD = 0.55f;

    /** Glow hues, used in turn; alpha is ignored. */
    private static final int[] HUES = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Random dice = new java.util.Random();

    private View timeGroup;
    private GradientDrawable glow;
    private int hueSlot;
    private int currentHue;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            hopWithFade();
            shiftHue();
            ui.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV from the dream
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.spider_dream_clock);

        timeGroup = findViewById(R.id.spiderDreamBlock);
        final View glowLayer = findViewById(R.id.spiderDreamWash);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowLayer.setBackground(glow);
        glowLayer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * GLOW_SPREAD));
        hueSlot = dice.nextInt(HUES.length);
        applyHue(HUES[hueSlot]);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        timeGroup.setAlpha(0f);
        timeGroup.post(this::moveThenAppear);
        ui.postDelayed(tick, TICK_MS);
    }

    @Override
    public void onDreamingStopped() {
        ui.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        ui.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void moveThenAppear() {
        placeRandomly();
        timeGroup.animate().alpha(1f).setDuration(APPEAR_MS).start();
    }

    private void hopWithFade() {
        timeGroup.animate().alpha(0f).setDuration(VANISH_MS).withEndAction(this::moveThenAppear).start();
    }

    /**
     * Picks a random spot inside the central 80% of the leftover room, so the block stays fully on screen,
     * and moves the glow's centre under it.
     */
    private void placeRandomly() {
        final View host = (View) timeGroup.getParent();
        final int hostW = host.getWidth(), hostH = host.getHeight();
        final int w = timeGroup.getWidth(), h = timeGroup.getHeight();
        final float x = Math.max(0, hostW - w) * (0.1f + 0.8f * dice.nextFloat());
        final float y = Math.max(0, hostH - h) * (0.1f + 0.8f * dice.nextFloat());
        timeGroup.setX(x);
        timeGroup.setY(y);
        if (hostW > 0 && hostH > 0) {
            glow.setGradientCenter((x + w / 2f) / hostW, (y + h / 2f) / hostH);
        }
    }

    private void shiftHue() {
        final int from = currentHue;
        hueSlot = (hueSlot + 1) % HUES.length;
        android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofObject(
                new android.animation.ArgbEvaluator(), from, HUES[hueSlot]);
        fade.setDuration(HUE_SHIFT_MS);
        fade.addUpdateListener(a -> applyHue((int) a.getAnimatedValue()));
        fade.start();
    }

    /** 25% alpha in the middle, 8% halfway out, clear at the rim, all of one hue. */
    private void applyHue(int hue) {
        currentHue = hue;
        final int rgb = hue & 0x00FFFFFF;
        glow.setColors(new int[]{rgb | 0x40000000, rgb | 0x14000000, rgb});
    }
}
