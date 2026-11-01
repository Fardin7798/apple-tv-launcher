package com.spidertv.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.Calendar;

/**
 * Spider Home flip clock: two dark cards (hour, minutes) with tall condensed digits split by a hinge line,
 * an AM/PM tag in the hour card's corner. When a value changes, its top half folds down onto the new one.
 * Sizes follow the concept at 1920x1080: hour card 250x250px, minute card 300x250px, 16px gap (px / 2 = dp).
 */
public class FlipClockView extends View {

    private static final float HOUR_W = 125f, MIN_W = 150f, CARD_H = 125f, GAP = 8f, RADIUS = 9f;

    private final float dp;
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hinge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hingeHi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tag = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private String hour = "", minute = "", ampm = "";
    private String oldHour = "", oldMinute = "";
    private float flipH = 1f, flipM = 1f;
    private ValueAnimator anim;
    private boolean animations = true;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            update(true);
        }
    };

    public FlipClockView(Context c) {
        this(c, null);
    }

    public FlipClockView(Context c, AttributeSet a) {
        super(c, a);
        dp = getResources().getDisplayMetrics().density;
        card.setColor(0xEB161428);
        cardTop.setColor(0xF0201D37);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f * dp);
        edge.setColor(0x16FFFFFF);
        hinge.setColor(0xFF05040E);
        hinge.setStrokeWidth(2f * dp);
        hingeHi.setColor(0x12FFFFFF);
        hingeHi.setStrokeWidth(0.5f * dp);
        Typeface bold = getResources().getFont(R.font.spider_oswald_bold);
        Typeface medium = getResources().getFont(R.font.spider_oswald_medium);
        digit.setTypeface(bold);
        digit.setColor(0xFFC6C6D2);
        digit.setTextAlign(Paint.Align.CENTER);
        digit.setTextSize(100f * dp);
        tag.setTypeface(medium);
        tag.setColor(0xFFAAAAB9);
        tag.setTextSize(15f * dp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        update(false);
    }

    void setAnimations(boolean on) {
        animations = on;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(tick, f);
        update(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        getContext().unregisterReceiver(tick);
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) Math.ceil((HOUR_W + GAP + MIN_W) * dp), (int) Math.ceil((CARD_H + 6f) * dp));
    }

    void update(boolean animate) {
        Calendar c = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        int h = c.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && h == 0) h = 12;
        String nh = h24 ? String.format("%02d", h) : String.valueOf(h);
        String nm = String.format("%02d", c.get(Calendar.MINUTE));
        ampm = h24 ? "" : (c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        boolean ch = !nh.equals(hour), cm = !nm.equals(minute);
        oldHour = hour;
        oldMinute = minute;
        hour = nh;
        minute = nm;
        setContentDescription(nh + ":" + nm + (ampm.isEmpty() ? "" : " " + ampm));
        if (animate && animations && isAttachedToWindow() && (ch || cm)) {
            flipH = ch ? 0f : 1f;
            flipM = cm ? 0f : 1f;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f).setDuration(520);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            final boolean fh = ch, fm = cm;
            anim.addUpdateListener(a -> {
                float v = (float) a.getAnimatedValue();
                if (fh) flipH = v;
                if (fm) flipM = v;
                invalidate();
            });
            anim.start();
        } else {
            flipH = flipM = 1f;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        drawCard(cv, 0f, HOUR_W, hour, oldHour, flipH, ampm);
        drawCard(cv, (HOUR_W + GAP) * dp, MIN_W, minute, oldMinute, flipM, null);
    }

    private void drawCard(Canvas cv, float x, float wDp, String now, String old, float p, String label) {
        float w = wDp * dp, h = CARD_H * dp, rad = RADIUS * dp, mid = h / 2f;
        r.set(x, 0, x + w, h);
        cv.drawRoundRect(r, rad, rad, card);
        cv.save();
        cv.clipRect(x, 0, x + w, mid);
        cv.drawRoundRect(r, rad, rad, cardTop);
        cv.restore();

        Paint.FontMetrics fm = digit.getFontMetrics();
        float base = mid - (fm.ascent + fm.descent) / 2f;
        float cx = x + w / 2f;
        if (p >= 1f || old.isEmpty()) {
            cv.drawText(now, cx, base, digit);
        } else {
            // Static halves: new value on top, old value below until the leaf lands.
            cv.save();
            cv.clipRect(x, 0, x + w, mid);
            cv.drawText(now, cx, base, digit);
            cv.restore();
            cv.save();
            cv.clipRect(x, mid, x + w, h);
            cv.drawText(old, cx, base, digit);
            cv.restore();
            // Falling leaf: old top half folding down (p < .5), then new bottom half (p >= .5).
            float s = (float) Math.abs(Math.cos(Math.PI * p));
            boolean first = p < 0.5f;
            cv.save();
            cv.translate(0, mid);
            cv.scale(1f, s);
            cv.translate(0, -mid);
            if (first) cv.clipRect(x, 0, x + w, mid);
            else cv.clipRect(x, mid, x + w, h);
            cv.drawRoundRect(r, rad, rad, card);
            if (first) cv.drawRoundRect(r, rad, rad, cardTop);
            cv.drawText(first ? old : now, cx, base, digit);
            cv.restore();
        }
        cv.drawRoundRect(r, rad, rad, edge);
        cv.drawLine(x, mid, x + w, mid, hinge);
        cv.drawLine(x, mid + 1.5f * dp, x + w, mid + 1.5f * dp, hingeHi);
        if (label != null && !label.isEmpty()) cv.drawText(label, x + 11f * dp, h - 12f * dp, tag);
    }
}
