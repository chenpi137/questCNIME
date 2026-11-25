package com.questcn.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 按 VrShell 键盘几何参数绘制的自绘键盘。
 * 按键大小/间距/圆角/图标均来自复刻的 XML 与 drawable。
 */
public class KeyboardView extends View {

    public interface Listener {
        void onKey(Key key);
        void onKeyRepeat(Key key);
        void onKeyLongPress(Key key);
        void onKeyVariant(Key key, String text);

        /** 当前是否可以进入“长按空格拖光标”模式（组合输入期间不允许）。 */
        boolean canSpaceScrub();

        void onSpaceScrubStart();

        void onSpaceScrubMove(float dx);

        void onSpaceScrubEnd();
    }

    private static final long REPEAT_DELAY = 400L;
    private static final long REPEAT_INTERVAL = 60L;
    private static final long LONG_PRESS_DELAY = 340L;
    /** 按在键缝/边缘时吸附到最近键的最大距离。 */
    private static final float KEY_SNAP_DP = 10f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint altPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path keyPath = new Path();
    private final float[] radii = new float[8];
    private final RectF iconRect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Keyboard keyboard;
    private Listener listener;
    private VariantPopupView variantPopup;
    private boolean popupActive;
    private Key pressedKey;
    private Key hoveredKey;
    private boolean shifted;
    private boolean caps;
    private boolean longPressFired;
    private boolean spaceScrubActive;
    private float spaceScrubOriginX;
    private float lastTouchX;
    private float keySnap;
    private int lastDeviceId = -1;
    private static boolean inputDevicesLogged;
    private AudioManager audioManager;
    private Object spatialMedia;
    private Method spatialPlay;
    private boolean spatialLogged;

    private int colorPrimary;
    private int colorSecondary;
    private int colorAccent;
    private int colorAccentPressed;
    private int colorPressed;
    private int colorHover;
    private int colorText;
    private int colorAlt;
    private float density;
    private float radius;
    private float radiusOuter;
    private float iconSize;

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            Key k = pressedKey;
            if (k != null && k.repeatable && listener != null) {
                listener.onKeyRepeat(k);
                handler.postDelayed(this, REPEAT_INTERVAL);
            }
        }
    };

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            Key k = pressedKey;
            if (k != null && !k.repeatable && listener != null) {
                longPressFired = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                vibrateController(18);
                ArrayList<String> variants = buildVariants(k);
                if (variantPopup != null && variants.size() >= 2) {
                    int defaultIndex = variants.indexOf(k.label);
                    variantPopup.show(getLeft() + k.rect.left, getTop() + k.rect.top,
                            k.rect.width(), k.rect.height(), variants, defaultIndex);
                    popupActive = true;
                } else {
                    listener.onKeyLongPress(k);
                }
                invalidate();
            }
        }
    };

    /**
     * 长按空格：进入光标拖动模式。组合输入（拼音候选）期间不触发，
     * 仍然按普通空格/首选候选处理。
     */
    private final Runnable spaceLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            Key k = pressedKey;
            if (k == null || listener == null || !isSpaceKey(k)) {
                return;
            }
            if (!listener.canSpaceScrub()) {
                return;
            }
            spaceScrubActive = true;
            longPressFired = true;
            // 长按生效时以当前指针位置为原点，避免按下后等待期间的小漂移
            // 让圆球一出现就跳走。
            spaceScrubOriginX = lastTouchX;
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            vibrateController(18);
            listener.onSpaceScrubStart();
            invalidate();
        }
    };

    private ArrayList<String> buildVariants(Key key) {
        ArrayList<String> variants = new ArrayList<>();
        if (key.shiftLabel != null && !key.shiftLabel.equals(key.label)) {
            variants.add(key.shiftLabel);
        }
        if (key.label != null && !variants.contains(key.label)) {
            variants.add(key.label);
        }
        if (key.altLabel != null && !variants.contains(key.altLabel)) {
            variants.add(key.altLabel);
        }
        return variants;
    }

    public void setVariantPopup(VariantPopupView popup) {
        this.variantPopup = popup;
    }

    public KeyboardView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        radius = 8f * density;
        radiusOuter = 16f * density;
        iconSize = 24f * density;
        keySnap = KEY_SNAP_DP * density;
        colorPrimary = context.getResources().getColor(R.color.key_primary, null);
        colorSecondary = context.getResources().getColor(R.color.key_secondary, null);
        colorAccent = context.getResources().getColor(R.color.key_accent, null);
        colorAccentPressed = context.getResources().getColor(R.color.key_accent_pressed, null);
        colorPressed = context.getResources().getColor(R.color.key_pressed, null);
        colorHover = context.getResources().getColor(R.color.key_hover, null);
        colorText = context.getResources().getColor(R.color.key_text, null);
        colorAlt = context.getResources().getColor(R.color.key_text_alt, null);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        altPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        setFocusable(true);
        setFocusableInTouchMode(true);
        initAudio(context);
    }

    private void initAudio(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // VrShell 的键盘音走 Meta 空间音频系统服务，事件名来自 fmod_sounds_guid.json。
        try {
            Class<?> cls = Class.forName("vros.spatial.media.SpatialMediaManager");
            Object service = context.getSystemService((Class) cls);
            if (service != null) {
                spatialMedia = service;
                try {
                    spatialPlay = cls.getMethod("playSpatialAudio",
                            String.class, String.class,
                            float.class, float.class, float.class,
                            int.class, boolean.class);
                } catch (NoSuchMethodException nsme) {
                    spatialPlay = cls.getDeclaredMethod("playSpatialAudio",
                            String.class, String.class,
                            float.class, float.class, float.class,
                            int.class, boolean.class);
                    spatialPlay.setAccessible(true);
                }
                Log.i("QuestIME", "SpatialMediaManager ready: " + cls.getName());
            } else {
                Log.w("QuestIME", "SpatialMediaManager service is null");
            }
        } catch (Throwable t) {
            Log.w("QuestIME", "SpatialMediaManager unavailable", t);
        }
    }

    private void playKeySound(Key key) {
        String event = eventForKey(key);
        if (event == null) {
            return;
        }
        if (playSpatial(event)) {
            return;
        }
        // 空间音频服务不可用时退回 Android 系统按键音。
        int effect = AudioManager.FX_KEYPRESS_STANDARD;
        if (key != null) {
            if (key.hasCode(1)) {
                effect = AudioManager.FX_KEYPRESS_SPACEBAR;
            } else if (key.hasCode(-5)) {
                effect = AudioManager.FX_KEYPRESS_DELETE;
            }
        }
        playSystemEffect(effect);
    }

    /**
     * VrShell 的按键类型映射（LX/1v0 + LX/1TM）：
     * SPACE -> keyboardDirectSecondary；BACKSPACE -> keyboardDirectBackspace；
     * SHIFT / ACTION_KEY / LAYOUT / DISMISS 本身不发声；其余字符键 -> keyboardDirectPrimary。
     */
    private String eventForKey(Key key) {
        if (key == null) {
            return "keyboardDirectPrimary";
        }
        if (key.hasCode(1)) {
            return "keyboardDirectSecondary";
        }
        if (key.hasCode(-5)) {
            return "keyboardDirectBackspace";
        }
        if (key.hasCode(-1) || key.hasCode(-2)
                || key.hasCode(-3) || key.hasCode(-4)) {
            return null;
        }
        return "keyboardDirectPrimary";
    }

    /** 与 VrShell KeyboardInputMethodService 的调用参数保持一致。 */
    private boolean playSpatial(String event) {
        if (spatialPlay == null || spatialMedia == null) {
            return false;
        }
        try {
            spatialPlay.invoke(spatialMedia, event, "spatial.media.ATTENUATION_NONE",
                    1f, 0.5f, 0.5f, 0, false);
            if (!spatialLogged) {
                Log.i("QuestIME", "spatial play ok: " + event);
                spatialLogged = true;
            }
            return true;
        } catch (Throwable t) {
            Log.w("QuestIME", "playSpatialAudio failed for " + event, t);
            spatialPlay = null;
            return false;
        }
    }

    private void playSystemEffect(int effect) {
        if (audioManager != null) {
            try {
                audioManager.playSoundEffect(effect);
                return;
            } catch (Throwable t) {
                Log.w("QuestIME", "playSoundEffect failed", t);
            }
        }
        playSoundEffect(android.view.SoundEffectConstants.CLICK);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setKeyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
        endPopup();
        clearPressed();
        hoveredKey = null;
        requestLayout();
        invalidate();
    }

    public Keyboard getKeyboard() {
        return keyboard;
    }

    public void setShiftState(boolean shifted, boolean caps) {
        this.shifted = shifted;
        this.caps = caps;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (keyboard != null) {
            setMeasuredDimension(keyboard.width, keyboard.height);
        } else {
            setMeasuredDimension((int) (624 * density), (int) (232 * density));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Keyboard kb = keyboard;
        if (kb == null) {
            return;
        }
        for (Keyboard.Row row : kb.rows) {
            for (Key key : row.keys) {
                drawKey(canvas, kb, key);
            }
        }
    }

    private void drawKey(Canvas canvas, Keyboard kb, Key key) {
        boolean accent = key.bgName != null && key.bgName.contains("accent");
        int color;
        if (key.pressed) {
            color = accent ? colorAccentPressed : colorPressed;
        } else if (key.hovered) {
            color = accent ? colorAccentPressed : colorHover;
        } else if (accent) {
            color = colorAccent;
        } else if (key.bgName != null && key.bgName.contains("secondary")) {
            color = colorSecondary;
        } else {
            color = colorPrimary;
        }

        float left = radius;
        float top = radius;
        float right = radius;
        float bottom = radius;
        if (key.bgName != null) {
            if (key.bgName.endsWith("bottom_left") || key.bgName.endsWith("accent")) {
                bottom = radiusOuter;
            } else if (key.bgName.endsWith("bottom_right")) {
                bottom = radiusOuter;
            }
        }
        // radii 顺序：TL, TR, BR, BL（每角 x/y 两个值）
        radii[0] = left;
        radii[1] = top;
        radii[2] = right;
        radii[3] = top;
        radii[4] = right;
        radii[5] = bottom;
        radii[6] = left;
        radii[7] = bottom;

        keyPath.reset();
        keyPath.addRoundRect(key.rect, radii, Path.Direction.CW);
        fillPaint.setColor(color);
        canvas.drawPath(keyPath, fillPaint);

        // 左上角的小字（数字/符号提示），与 VrShell 键帽一致
        if (key.altLabel != null && !key.altLabel.isEmpty()) {
            altPaint.setTextSize(kb.subTextSize);
            altPaint.setColor(colorAlt);
            altPaint.setTextAlign(Paint.Align.RIGHT);
            float x = key.rect.right - 6f * density;
            float y = key.rect.top + 3f * density + kb.subTextSize;
            canvas.drawText(key.altLabel, x, y, altPaint);
        }

        int iconRes = key.iconRes;
        if (shifted && key.shiftIconRes != 0) {
            iconRes = key.shiftIconRes;
        }
        if (caps && key.capsIconRes != 0) {
            iconRes = key.capsIconRes;
        }

        if (iconRes != 0) {
            Drawable d = getContext().getDrawable(iconRes);
            if (d != null) {
                float cx = key.rect.centerX();
                float cy = key.rect.centerY();
                float half = iconSize / 2f;
                iconRect.set(cx - half, cy - half, cx + half, cy + half);
                d.setBounds((int) iconRect.left, (int) iconRect.top,
                        (int) iconRect.right, (int) iconRect.bottom);
                d.draw(canvas);
            }
            return;
        }

        String label = key.label;
        if (shifted && key.shiftLabel != null) {
            label = key.shiftLabel;
        }
        if (label == null || label.isEmpty()) {
            return;
        }
        textPaint.setTextSize(key.textSize > 0 ? key.textSize : kb.textSize);
        textPaint.setColor(key.textColor != 0 ? key.textColor : kb.textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = key.rect.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, key.rect.centerX(), baseline, textPaint);
    }

    private Key findKey(float x, float y) {
        Keyboard kb = keyboard;
        if (kb == null) {
            return null;
        }
        for (Keyboard.Row row : kb.rows) {
            for (Key key : row.keys) {
                if (key.rect.contains(x, y)) {
                    return key;
                }
            }
        }
        return null;
    }

    /** 点到按键矩形边缘的距离平方（点在矩形内为 0）。 */
    private static float distanceSquaredTo(RectF rect, float x, float y) {
        float dx = 0f;
        if (x < rect.left) {
            dx = rect.left - x;
        } else if (x > rect.right) {
            dx = x - rect.right;
        }
        float dy = 0f;
        if (y < rect.top) {
            dy = rect.top - y;
        } else if (y > rect.bottom) {
            dy = y - rect.bottom;
        }
        return dx * dx + dy * dy;
    }

    /**
     * VR 光标命中：精确命中优先，落在键缝/边缘时吸附最近键。
     *
     * Quest 手柄扣扳机时射线会轻微移位，按下点可能落进 8dp 键缝；
     * 这里只做常量次数的矩形距离比较，不引入轮询或定时器。
     */
    private Key hitKey(float x, float y) {
        Key key = findKey(x, y);
        if (key != null || keyboard == null) {
            return key;
        }
        float best = keySnap * keySnap;
        Key nearest = null;
        for (Keyboard.Row row : keyboard.rows) {
            for (Key candidate : row.keys) {
                float dist = distanceSquaredTo(candidate.rect, x, y);
                if (dist <= best) {
                    best = dist;
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private void clearPressed() {
        if (pressedKey != null) {
            pressedKey.pressed = false;
            pressedKey = null;
        }
    }

    private void endPopup() {
        if (variantPopup != null) {
            variantPopup.hide();
        }
        popupActive = false;
    }

    private void startRepeat(Key key) {
        handler.removeCallbacks(repeatRunnable);
        handler.postDelayed(repeatRunnable, REPEAT_DELAY);
    }

    private void stopRepeat() {
        handler.removeCallbacks(repeatRunnable);
    }

    /** 候选上屏：对应系统触觉里的 Select 模式（两次短脉冲的近似）。 */
    public void hapticSelect() {
        if (!playSpatial("keyboardDirectPrimary")) {
            playSystemEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
        vibrateController(16);
    }

    /** 光标拖动时逐格反馈：只震动，不播放按键音。 */
    public void hapticTick() {
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        vibrateController(10);
    }

    /**
     * Quest 手柄震动：优先用产生触摸事件的输入设备，其次找带震动器的 gamepad/joystick。
     * 系统 performHapticFeedback 在部分 Quest 版本只走头显通道，这里补上控制器 Vibrator。
     */
    private void vibrateController(int milliseconds) {
        if (Build.VERSION.SDK_INT < 31) {
            return;
        }
        try {
            InputManager inputManager = (InputManager) getContext()
                    .getSystemService(Context.INPUT_SERVICE);
            if (inputManager == null) {
                return;
            }
            if (!inputDevicesLogged) {
                inputDevicesLogged = true;
                for (int id : inputManager.getInputDeviceIds()) {
                    InputDevice device = inputManager.getInputDevice(id);
                    if (device != null) {
                        Log.d("QuestIME", "input device id=" + id + " name=" + device.getName()
                                + " sources=0x" + Integer.toHexString(device.getSources())
                                + " hasVibrator=" + hasVibrator(device));
                    }
                }
            }
            InputDevice target = null;
            if (lastDeviceId >= 0) {
                InputDevice eventDevice = inputManager.getInputDevice(lastDeviceId);
                if (hasVibrator(eventDevice)) {
                    target = eventDevice;
                }
            }
            if (target == null) {
                for (int id : inputManager.getInputDeviceIds()) {
                    InputDevice device = inputManager.getInputDevice(id);
                    if (hasVibrator(device) && isController(device)) {
                        target = device;
                        break;
                    }
                }
            }
            if (target == null) {
                for (int id : inputManager.getInputDeviceIds()) {
                    InputDevice device = inputManager.getInputDevice(id);
                    if (hasVibrator(device)) {
                        target = device;
                        break;
                    }
                }
            }
            if (target == null) {
                return;
            }
            Vibrator vibrator = target.getVibrator();
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(milliseconds);
            }
            Log.d("QuestIME", "haptic " + milliseconds + "ms device="
                    + target.getName() + " id=" + target.getId());
        } catch (Throwable t) {
            Log.w("QuestIME", "controller haptic failed", t);
        }
    }

    private boolean hasVibrator(InputDevice device) {
        if (device == null || Build.VERSION.SDK_INT < 31) {
            return false;
        }
        try {
            Vibrator vibrator = device.getVibrator();
            return vibrator != null && vibrator.hasVibrator();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isController(InputDevice device) {
        if (device == null) {
            return false;
        }
        int sources = device.getSources();
        return (sources & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) != 0;
    }

    private boolean isSpaceKey(Key key) {
        return key != null && key.hasCode(1);
    }

    private boolean supportsLongPress(Key key) {
        if (key == null) {
            return false;
        }
        if (key.altLabel != null && !key.altLabel.isEmpty()) {
            return true;
        }
        if (key.hasCode(-1)) {
            return true;
        }
        return key.shiftOutput != null && !key.shiftOutput.equals(key.output);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                clearPressed();
                endPopup();
                longPressFired = false;
                spaceScrubActive = false;
                lastTouchX = event.getX();
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(spaceLongPressRunnable);
                Key key = hitKey(event.getX(), event.getY());
                if (key != null) {
                    pressedKey = key;
                    key.pressed = true;
                    lastDeviceId = event.getDeviceId();
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    vibrateController(12);
                    playKeySound(key);
                    if (key.repeatable) {
                        startRepeat(key);
                    } else if (supportsLongPress(key)) {
                        handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY);
                    }
                    if (isSpaceKey(key)) {
                        handler.postDelayed(spaceLongPressRunnable, LONG_PRESS_DELAY);
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                lastTouchX = event.getX();
                if (spaceScrubActive) {
                    if (listener != null) {
                        listener.onSpaceScrubMove(event.getX() - spaceScrubOriginX);
                    }
                    return true;
                }
                if (popupActive) {
                    if (variantPopup != null) {
                        variantPopup.updateSelection(getLeft() + event.getX(),
                                getTop() + event.getY());
                    }
                }
                // 按下后不换键：松开时结算的始终是按下时的按键，
                // 光标漂移到键缝或邻键都不影响本次点击。
                return true;
            }
            case MotionEvent.ACTION_UP: {
                Key key = pressedKey;
                boolean wasLongPress = longPressFired;
                boolean wasSpaceScrub = spaceScrubActive;
                String variant = null;
                if (popupActive && variantPopup != null) {
                    variant = variantPopup.getSelected();
                }
                stopRepeat();
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(spaceLongPressRunnable);
                endPopup();
                clearPressed();
                longPressFired = false;
                spaceScrubActive = false;
                if (wasSpaceScrub && listener != null) {
                    listener.onSpaceScrubEnd();
                } else if (wasLongPress) {
                    if (variant != null && listener != null) {
                        listener.onKeyVariant(key, variant);
                    }
                } else if (key != null && listener != null) {
                    listener.onKey(key);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (pressedKey != null) {
                    Log.i("QuestIME", "touch cancel pressed=" + pressedKey.label);
                }
                boolean wasSpaceScrub = spaceScrubActive;
                stopRepeat();
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(spaceLongPressRunnable);
                endPopup();
                clearPressed();
                longPressFired = false;
                spaceScrubActive = false;
                if (wasSpaceScrub && listener != null) {
                    listener.onSpaceScrubEnd();
                }
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE: {
                // 与点击共用吸附逻辑，高亮范围即实际可点范围
                Key key = hitKey(event.getX(), event.getY());
                if (key != hoveredKey) {
                    if (hoveredKey != null) {
                        hoveredKey.hovered = false;
                    }
                    hoveredKey = key;
                    if (hoveredKey != null) {
                        hoveredKey.hovered = true;
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_HOVER_EXIT: {
                if (hoveredKey != null) {
                    hoveredKey.hovered = false;
                    hoveredKey = null;
                    invalidate();
                }
                return true;
            }
            default:
                return super.onHoverEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
