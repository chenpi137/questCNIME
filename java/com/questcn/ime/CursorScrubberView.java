package com.questcn.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 长按空格后浮在空格键上方的光标滑轨。
 *
 * 只负责画：深灰圆角底板 + 浅灰轨道 + 白色圆球。圆球默认在轨道中央，
 * 手指左右滑动时由 {@link #setBallOffset(float)} 把偏移量同步过来。
 * 触摸事件一律返回 false，交给下面的 KeyboardView 继续处理。
 */
public class CursorScrubberView extends View {

    private static final float WIDTH_DP = 560f;
    private static final float HEIGHT_DP = 72f;
    private static final float TRACK_HEIGHT_DP = 5f;
    private static final float BALL_RADIUS_DP = 12f;
    private static final float SIDE_PADDING_DP = 24f;

    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panelRect = new RectF();
    private final RectF trackRect = new RectF();
    private final float density;
    private float ballOffset;

    public CursorScrubberView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        panelPaint.setColor(0xFF3A3A3C);
        trackPaint.setColor(0xFF5A5A5C);
        ballPaint.setColor(0xFFF2F2F2);
        setVisibility(GONE);
        setClickable(false);
        setFocusable(false);
    }

    public static int width(Context context) {
        return Math.round(WIDTH_DP * context.getResources().getDisplayMetrics().density);
    }

    public static int height(Context context) {
        return Math.round(HEIGHT_DP * context.getResources().getDisplayMetrics().density);
    }

    /** 圆球可以偏离中心的像素上限。 */
    public float getMaxBallOffset() {
        float width = getWidth() > 0 ? getWidth() : WIDTH_DP * density;
        float half = Math.max(0f, width / 2f - SIDE_PADDING_DP * density
                - BALL_RADIUS_DP * density);
        return half;
    }

    public void setBallOffset(float offset) {
        float max = getMaxBallOffset();
        ballOffset = Math.max(-max, Math.min(max, offset));
        invalidate();
    }

    public float getBallOffset() {
        return ballOffset;
    }

    public void show() {
        ballOffset = 0f;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void hide() {
        setVisibility(GONE);
        ballOffset = 0f;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 不拦截触摸：长按手势仍然由 KeyboardView 的空格键处理
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getVisibility() != VISIBLE) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float panelRadius = 16f * density;
        float trackRadius = TRACK_HEIGHT_DP * density / 2f;
        float ballRadius = BALL_RADIUS_DP * density;

        panelRect.set(0f, 0f, w, h);
        canvas.drawRoundRect(panelRect, panelRadius, panelRadius, panelPaint);

        float trackLeft = SIDE_PADDING_DP * density;
        float trackRight = w - SIDE_PADDING_DP * density;
        float trackTop = (h - TRACK_HEIGHT_DP * density) / 2f;
        trackRect.set(trackLeft, trackTop, trackRight,
                trackTop + TRACK_HEIGHT_DP * density);
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint);

        float cx = w / 2f + ballOffset;
        canvas.drawCircle(cx, h / 2f, ballRadius, ballPaint);
    }
}
