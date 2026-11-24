package com.questcn.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 长按按键时弹出的候选字符浮层，样式对齐设计稿：
 * 灰色圆角底板，横向排列 大写/小写/符号，当前选中格为深色圆角块。
 */
public class VariantPopupView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF popupRect = new RectF();
    private final RectF cellRect = new RectF();

    private final float density;
    private final float cellSize;
    private final float padding;
    private List<String> variants = new ArrayList<>();
    private int selected;

    public VariantPopupView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        cellSize = 52f * density;
        padding = 8f * density;
        bgPaint.setColor(0xFF3A3A3A);
        cellPaint.setColor(0xFF262626);
        textPaint.setColor(0xFFF2F2F2);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setVisibility(GONE);
        setClickable(false);
        setFocusable(false);
    }

    public void show(float keyLeft, float keyTop, float keyWidth, float keyHeight,
                     List<String> values, int defaultIndex) {
        if (values == null || values.size() < 2) {
            hide();
            return;
        }
        variants = new ArrayList<>(values);
        selected = Math.max(0, Math.min(defaultIndex, variants.size() - 1));

        int panelW = getWidth() > 0 ? getWidth() : Math.round(624f * density);
        int panelH = getHeight() > 0 ? getHeight() : Math.round(284f * density);
        float w = variants.size() * cellSize + padding * 2f;
        float h = cellSize + padding * 2f;
        float x = keyLeft + keyWidth / 2f - w / 2f;
        x = Math.max(4f * density, Math.min(x, panelW - w - 4f * density));
        float y = keyTop - h - 6f * density;
        if (y < 2f * density) {
            y = keyTop + keyHeight + 6f * density;
        }
        y = Math.max(2f * density, Math.min(y, panelH - h - 2f * density));
        popupRect.set(x, y, x + w, y + h);
        setVisibility(VISIBLE);
        invalidate();
    }

    public void updateSelection(float x, float y) {
        if (variants.size() < 2) {
            return;
        }
        if (y < popupRect.top - cellSize * 0.5f || y > popupRect.bottom + cellSize * 0.5f) {
            return;
        }
        int idx = (int) ((x - popupRect.left - padding) / cellSize);
        idx = Math.max(0, Math.min(idx, variants.size() - 1));
        if (idx != selected) {
            selected = idx;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            invalidate();
        }
    }

    public String getSelected() {
        if (variants.isEmpty()) {
            return null;
        }
        return variants.get(selected);
    }

    public boolean isActive() {
        return getVisibility() == VISIBLE && variants.size() >= 2;
    }

    public void hide() {
        variants = new ArrayList<>();
        setVisibility(GONE);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 不拦截触摸，键盘 View 继续处理滑动选择
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isActive()) {
            return;
        }
        canvas.drawRoundRect(popupRect, 12f * density, 12f * density, bgPaint);
        for (int i = 0; i < variants.size(); i++) {
            float left = popupRect.left + padding + i * cellSize;
            float top = popupRect.top + padding;
            cellRect.set(left, top, left + cellSize, top + cellSize);
            if (i == selected) {
                canvas.drawRoundRect(cellRect, 8f * density, 8f * density, cellPaint);
            }
            textPaint.setColor(i == selected ? 0xFFFFFFFF : 0xFFBFBFBF);
            textPaint.setTextSize(30f * getResources().getDisplayMetrics().scaledDensity);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float baseline = cellRect.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(variants.get(i), cellRect.centerX(), baseline, textPaint);
        }
    }
}
