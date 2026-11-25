package com.questcn.ime;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.kingzcheung.xime.rime.RimeCandidate;
import com.kingzcheung.xime.rime.RimeProcessResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 独立中文输入法。
 *
 * 键盘布局、按键尺寸、图标与 VrShell 原生键盘一致：
 *  - 窗口 624 x 340 dp（Quest 200dpi 下为 780 x 425 px）
 *  - 顶部 52dp 状态栏 + 48dp 候选条 + 232dp 键盘（4 行 x 48dp，8dp 间距，8dp 内边距）
 */
public class QuestImeService extends InputMethodService implements KeyboardView.Listener {

    private static final String TAG = "QuestIME";

    private static final int CODE_SPACE = 1;
    private static final int CODE_SHIFT = -1;
    private static final int CODE_ENTER = -2;
    private static final int CODE_MODE_SWITCH = -3;
    private static final int CODE_CLOSE = -4;
    private static final int CODE_BACKSPACE = -5;
    private static final int CODE_ALPHABET = -7;
    private static final int CODE_SETTINGS = -20;
    private static final int CODE_LANGUAGE = -25;
    private static final int CODE_SWITCH_KEYBOARD = -26;

    private static final int MAX_CANDIDATES = 24;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final PinyinEngine engine = new PinyinEngine();
    private final StringBuilder composing = new StringBuilder();
    private final List<String> candidates = new ArrayList<>();
    /** 显示候选对应的 Rime 引擎索引（过滤空文本候选后仍然对得上）。 */
    private final List<Integer> rimeCandidateIndices = new ArrayList<>();

    private RimeCore rime;
    private volatile boolean rimeReady;
    private boolean rimeComposing;
    private boolean rimeCandidateMode;
    private String rimeInput = "";
    private String rimePreedit = "";

    private PanelRoot root;
    private KeyboardView keyboardView;
    private VariantPopupView variantPopup;
    private CursorScrubberView cursorScrubber;
    private LinearLayout suggestionList;
    private HorizontalScrollView fieldScroll;
    private LinearLayout inputRow;
    private TextView beforeText;
    private View caretView;
    private TextView afterText;
    private String readoutBefore = "";
    private String readoutAfter = "";
    private TextView pinyinTag;
    private boolean scrubbing;
    private boolean caretBlinkOn = true;
    private float scrubAccumPx;
    private float scrubLastBallOffset;
    private int scrubEdgeDirection;
    private int scrubEdgeTicks;
    private static final int SCRUB_EDGE_INTERVAL_MS = 80;

    private boolean chinese = true;
    private boolean shift;
    private boolean caps;
    private int keyboardRes = R.xml.keyboard_qwerty;
    private boolean dictReady;
    private static final int READOUT_HEIGHT_DP = 56;
    private static final int KEYBOARD_HEIGHT_DP = 216;
    private static final int VERTICAL_MARGIN_DP = 4;
    /** 输入框镜像光标前后各取多少字符；同时是空格拖光标的左右上限。 */
    private static final int READOUT_CONTEXT_CHARS = 128;
    /** 空格滑轨每移动多少 dp 光标走一个字。 */
    private static final float SCRUB_DP_PER_CHAR = 8f;
    private static final int PANEL_HEIGHT_DP = READOUT_HEIGHT_DP + KEYBOARD_HEIGHT_DP
            + VERTICAL_MARGIN_DP * 3;

    /** 临时调试开关：启动时强制输入 zh 以验证候选条与窗口高度。 */
    private static final boolean DEBUG_DEMO = false;

    private final Runnable dictLoaded = new Runnable() {
        @Override
        public void run() {
            dictReady = true;
            if (chinese && composing.length() > 0) {
                refreshCandidates();
            } else {
                updateSuggestionPlaceholder();
            }
        }
    };

    /** 输入框光标闪烁；只在输入框可见且 IME 窗口显示时运行。 */
    private final Runnable caretBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            if (caretView == null || inputRow == null
                    || inputRow.getVisibility() != View.VISIBLE
                    || !isInputViewShown()) {
                return;
            }
            caretBlinkOn = !caretBlinkOn;
            caretView.setVisibility(caretBlinkOn ? View.VISIBLE : View.INVISIBLE);
            ui.postDelayed(this, 500);
        }
    };

    /** 滑轨贴边时持续移动光标，直到滑轨离开边缘或到达文本边界。 */
    private final Runnable scrubEdgeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!scrubbing || scrubEdgeDirection == 0) {
                return;
            }
            if (++scrubEdgeTicks > 100) {
                scrubEdgeDirection = 0;
                return;
            }
            if (moveCaretBy(scrubEdgeDirection) != 0) {
                if (keyboardView != null) {
                    keyboardView.hapticTick();
                }
                updateReadout();
                ui.postDelayed(this, SCRUB_EDGE_INTERVAL_MS);
            } else {
                scrubEdgeDirection = 0;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        engine.loadAsync(getApplicationContext(), new Runnable() {
            @Override
            public void run() {
                ui.post(dictLoaded);
            }
        });
        rime = RimeCore.get();
        rime.initializeAsync(getApplicationContext(), true, new RimeCore.Callback() {
            @Override
            public void onReady(final boolean success, final String error) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        rimeReady = success;
                        Log.i(TAG, "rimeReady=" + success + " error=" + error);
                        if (success && composing.length() == 0 && !rimeComposing) {
                            updateReadout();
                            updateSuggestionPlaceholder();
                        }
                    }
                });
            }
        });
    }

    @Override
    public View onCreateInputView() {
        if (root == null) {
            buildViews();
        }
        return root;
    }

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly);
        win.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        win.setLayout(dp(624), dp(PANEL_HEIGHT_DP));
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        try {
            if (getWindow() != null && getWindow().getWindow() != null) {
                getWindow().getWindow().setLayout(dp(624), dp(PANEL_HEIGHT_DP));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.d(TAG, "onStartInputView restarting=" + restarting
                + " inputType=0x" + Integer.toHexString(info.inputType));
        hideScrubber();
        if (rime != null && rimeReady) {
            rime.clearComposition();
        }
        resetRimeFields();
        clearComposition();
        shift = false;
        caps = false;
        keyboardRes = R.xml.keyboard_qwerty;
        if (keyboardView != null) {
            keyboardView.setKeyboard(Keyboard.load(this, keyboardRes));
            keyboardView.setShiftState(false, false);
        }
        updateLanguageUi();
        updateSuggestionPlaceholder();
        if (DEBUG_DEMO) {
            composing.append("zh");
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.setComposingText("zh", 1);
            }
            refreshCandidates();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        Log.d(TAG, "onFinishInputView finishing=" + finishingInput);
        hideScrubber();
        if (rime != null && rimeReady) {
            rime.clearComposition();
        }
        resetRimeFields();
        clearComposition();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onWindowHidden() {
        hideScrubber();
        super.onWindowHidden();
    }

    // ---------------------------------------------------------------------
    // 视图构建
    // ---------------------------------------------------------------------

    private void buildViews() {
        // Quest 的 IME 窗口实测上限为 780x355px（284dp），与原版无候选时一致。
        root = new PanelRoot(this, dp(624), dp(PANEL_HEIGHT_DP));
        root.setBackgroundResource(R.drawable.panel_bg);
        root.setClipToOutline(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams readoutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(READOUT_HEIGHT_DP));
        readoutLp.leftMargin = dp(8);
        readoutLp.rightMargin = dp(8);
        readoutLp.topMargin = dp(VERTICAL_MARGIN_DP);
        readoutLp.bottomMargin = dp(VERTICAL_MARGIN_DP);
        column.addView(buildReadout(), readoutLp);

        keyboardView = new KeyboardView(this);
        keyboardView.setListener(this);
        keyboardView.setKeyboard(Keyboard.load(this, keyboardRes));
        LinearLayout.LayoutParams keyboardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(KEYBOARD_HEIGHT_DP));
        keyboardLp.bottomMargin = dp(VERTICAL_MARGIN_DP);
        column.addView(keyboardView, keyboardLp);

        variantPopup = new VariantPopupView(this);
        root.addView(variantPopup, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        keyboardView.setVariantPopup(variantPopup);

        cursorScrubber = new CursorScrubberView(this);
        FrameLayout.LayoutParams scrubLp = new FrameLayout.LayoutParams(
                CursorScrubberView.width(this), CursorScrubberView.height(this),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // 空格键位于最后一行（y=232dp 起）；滑轨底边停在它上方 8dp。
        scrubLp.topMargin = dp(152);
        root.addView(cursorScrubber, scrubLp);
    }

    private View buildReadout() {
        // 按设计稿：整条 64dp 的灰色圆角状态栏，左上角小拼音标签，下方大字提示/候选
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundResource(R.drawable.readout_bar_bg);

        pinyinTag = new TextView(this);
        pinyinTag.setText("拼");
        pinyinTag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        pinyinTag.setTextColor(0xFFDDDDDD);
        pinyinTag.setGravity(Gravity.CENTER);
        pinyinTag.setSingleLine(true);
        pinyinTag.setEllipsize(TextUtils.TruncateAt.END);
        pinyinTag.setBackgroundResource(R.drawable.pinyin_tag_bg);
        pinyinTag.setPadding(dp(6), 0, dp(6), 0);
        FrameLayout.LayoutParams tagLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP | Gravity.START);
        tagLp.leftMargin = dp(16);
        tagLp.topMargin = dp(4);
        bar.addView(pinyinTag, tagLp);

        HorizontalScrollView fieldScroll = new HorizontalScrollView(this);
        fieldScroll.setHorizontalScrollBarEnabled(false);
        fieldScroll.setFillViewport(false);
        this.fieldScroll = fieldScroll;

        LinearLayout fieldRow = new LinearLayout(this);
        fieldRow.setOrientation(LinearLayout.HORIZONTAL);
        fieldRow.setGravity(Gravity.CENTER_VERTICAL);

        // 真正的“带光标的输入框”：光标前的文字 + 竖线光标 + 光标后的文字。
        inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        beforeText = makeReadoutText();
        inputRow.addView(beforeText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        caretView = new View(this);
        GradientDrawable caretBg = new GradientDrawable();
        caretBg.setColor(0xFFF2F2F2);
        caretBg.setCornerRadius(dp(1));
        caretView.setBackground(caretBg);
        LinearLayout.LayoutParams caretLp = new LinearLayout.LayoutParams(dp(2), dp(24));
        caretLp.leftMargin = dp(1);
        caretLp.rightMargin = dp(1);
        inputRow.addView(caretView, caretLp);

        afterText = makeReadoutText();
        inputRow.addView(afterText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fieldRow.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        suggestionList = new LinearLayout(this);
        suggestionList.setOrientation(LinearLayout.HORIZONTAL);
        suggestionList.setGravity(Gravity.CENTER_VERTICAL);
        fieldRow.addView(suggestionList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fieldScroll.addView(fieldRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fieldScroll.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        moved = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int slop = ViewConfiguration.get(QuestImeService.this)
                                .getScaledTouchSlop();
                        if (Math.abs(event.getX() - downX) > slop
                                || Math.abs(event.getY() - downY) > slop) {
                            moved = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            Log.d(TAG, "readout touch x=" + event.getX()
                                    + " y=" + event.getY()
                                    + " scroll=" + fieldScroll.getScrollX());
                            handleReadoutTap(event.getX() + fieldScroll.getScrollX());
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        moved = true;
                        break;
                    default:
                        break;
                }
                // 返回 false，横向滚动（查看长文本）仍然交给 ScrollView。
                return false;
            }
        });

        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32), Gravity.BOTTOM);
        scrollLp.leftMargin = dp(16);
        scrollLp.rightMargin = dp(16);
        scrollLp.bottomMargin = dp(4);
        bar.addView(fieldScroll, scrollLp);

        return bar;
    }

    private TextView makeReadoutText() {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    // ---------------------------------------------------------------------
    // 键盘回调
    // ---------------------------------------------------------------------

    @Override
    public void onKey(Key key) {
        if (key == null) {
            return;
        }
        Log.d(TAG, "onKey label=" + key.label + " output=" + key.output
                + " codes=" + Arrays.toString(key.codes));
        if (key.codes.length > 0) {
            for (int code : key.codes) {
                if (!handleCode(code, key)) {
                    break;
                }
            }
            return;
        }
        if (key.output == null) {
            return;
        }
        if (key.isLetter()) {
            handleLetter(key);
        } else {
            handleText(key);
        }
    }

    @Override
    public void onKeyRepeat(Key key) {
        if (key != null && key.hasCode(CODE_BACKSPACE)) {
            handleBackspace();
        }
    }

    @Override
    public void onKeyVariant(Key key, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Log.i(TAG, "variant=" + text);
        if (chinese && text.length() == 1) {
            char c = text.charAt(0);
            if (c >= 'a' && c <= 'z') {
                if (rimeComposing && rimeReady && rime != null) {
                    applyRimeResult(rime.processKey(c), getCurrentInputConnection());
                    return;
                }
                composing.append(c);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.setComposingText(composing.toString(), 1);
                }
                refreshCandidates();
                return;
            }
        }
        if (rimeComposing) {
            commitRimeRaw();
        } else if (chinese && composing.length() > 0) {
            finishComposition(true);
        }
        String out = text;
        if (chinese && keyboardRes == R.xml.keyboard_qwerty) {
            out = mapPunctuation(out);
        }
        commitText(out);
    }

    @Override
    public void onKeyLongPress(Key key) {
        if (key == null) {
            return;
        }
        Log.d(TAG, "onKeyLongPress label=" + key.label + " alt=" + key.altLabel);
        if (key.hasCode(CODE_SHIFT)) {
            caps = !caps;
            shift = false;
            keyboardView.setShiftState(caps, caps);
            return;
        }
        if (rimeComposing) {
            commitRimeRaw();
        }
        String out = key.altLabel;
        if (out == null || out.isEmpty()) {
            out = key.shiftOutput;
        }
        if (out == null || out.isEmpty()) {
            return;
        }
        if (chinese && composing.length() > 0) {
            finishComposition(true);
        }
        if (chinese && keyboardRes == R.xml.keyboard_qwerty) {
            out = mapPunctuation(out);
        }
        commitText(out);
    }

    @Override
    public boolean canSpaceScrub() {
        if (rimeComposing || composing.length() > 0) {
            return false;
        }
        // 空输入框没有可移动的光标，长按松手仍然按普通空格处理。
        String before = textBeforeCursor();
        String after = textAfterCursor();
        return (before != null && !before.isEmpty())
                || (after != null && !after.isEmpty());
    }

    @Override
    public void onSpaceScrubStart() {
        if (cursorScrubber == null) {
            return;
        }
        updateReadout();
        scrubbing = true;
        scrubAccumPx = 0f;
        scrubLastBallOffset = 0f;
        scrubEdgeDirection = 0;
        ui.removeCallbacks(scrubEdgeRunnable);
        cursorScrubber.show();
        Log.i(TAG, "space scrub start before=" + readoutBefore.length()
                + " after=" + readoutAfter.length());
    }

    @Override
    public void onSpaceScrubMove(float dx) {
        if (!scrubbing || cursorScrubber == null) {
            return;
        }
        cursorScrubber.setBallOffset(dx);
        float ball = cursorScrubber.getBallOffset();
        float perChar = SCRUB_DP_PER_CHAR * getResources().getDisplayMetrics().density;
        scrubAccumPx += ball - scrubLastBallOffset;
        scrubLastBallOffset = ball;
        int steps = (int) (scrubAccumPx / perChar);
        if (steps != 0) {
            int actual = moveCaretBy(steps);
            if (actual != 0) {
                scrubAccumPx -= actual * perChar;
                if (keyboardView != null) {
                    keyboardView.hapticTick();
                }
                updateReadout();
            }
            if (actual != steps) {
                // 撞到文本头/尾或编辑器不支持：清掉累积，避免回拖时先走空行程
                scrubAccumPx = 0f;
            }
        }

        float max = cursorScrubber.getMaxBallOffset();
        if (ball <= -max + 0.5f) {
            startScrubEdge(-1);
        } else if (ball >= max - 0.5f) {
            startScrubEdge(1);
        } else {
            stopScrubEdge();
        }
        Log.d(TAG, "space scrub dx=" + dx + " ball=" + ball + " steps=" + steps
                + " accum=" + scrubAccumPx + " edge=" + scrubEdgeDirection);
    }

    private void startScrubEdge(int direction) {
        if (scrubEdgeDirection == direction) {
            return;
        }
        scrubEdgeDirection = direction;
        scrubEdgeTicks = 0;
        ui.removeCallbacks(scrubEdgeRunnable);
        ui.postDelayed(scrubEdgeRunnable, 120);
    }

    private void stopScrubEdge() {
        scrubEdgeDirection = 0;
        scrubEdgeTicks = 0;
        ui.removeCallbacks(scrubEdgeRunnable);
    }

    @Override
    public void onSpaceScrubEnd() {
        stopScrubEdge();
        if (!scrubbing) {
            return;
        }
        scrubbing = false;
        scrubAccumPx = 0f;
        scrubLastBallOffset = 0f;
        if (cursorScrubber != null) {
            cursorScrubber.hide();
        }
        updateReadout();
        Log.i(TAG, "space scrub end");
    }

    private void hideScrubber() {
        scrubbing = false;
        scrubAccumPx = 0f;
        scrubLastBallOffset = 0f;
        stopScrubEdge();
        stopCaretBlink();
        if (cursorScrubber != null) {
            cursorScrubber.hide();
        }
    }

    private void startCaretBlink() {
        if (caretView == null) {
            return;
        }
        caretBlinkOn = true;
        caretView.setVisibility(View.VISIBLE);
        ui.removeCallbacks(caretBlinkRunnable);
        ui.postDelayed(caretBlinkRunnable, 500);
    }

    private void stopCaretBlink() {
        ui.removeCallbacks(caretBlinkRunnable);
        if (caretView != null) {
            caretView.setVisibility(View.VISIBLE);
        }
    }

    private boolean handleCode(int code, Key key) {
        switch (code) {
            case CODE_SPACE:
                handleSpace();
                return true;
            case CODE_SHIFT:
                toggleShift();
                return true;
            case CODE_ENTER:
                handleEnter();
                return true;
            case CODE_MODE_SWITCH:
                finishComposition(true);
                switchKeyboard(key.keyboardRes != 0 ? key.keyboardRes : R.xml.keyboard_symbols);
                return true;
            case CODE_CLOSE:
                requestHideSelf(0);
                return false;
            case CODE_BACKSPACE:
                handleBackspace();
                return true;
            case CODE_ALPHABET:
                finishComposition(true);
                switchKeyboard(R.xml.keyboard_qwerty);
                return true;
            case CODE_SETTINGS:
                openSettings();
                return false;
            case CODE_LANGUAGE:
                toggleLanguage();
                return false;
            case CODE_SWITCH_KEYBOARD:
                showImePicker();
                return false;
            default:
                return true;
        }
    }

    // ---------------------------------------------------------------------
    // 文本 / 拼音输入
    // ---------------------------------------------------------------------

    private void handleLetter(Key key) {
        if (!chinese) {
            String out = shifted() && key.shiftOutput != null
                    ? key.shiftOutput : key.output;
            if (shifted()) {
                out = out.toUpperCase(Locale.ROOT);
            }
            commitText(out);
            if (shift && !caps) {
                shift = false;
                keyboardView.setShiftState(false, caps);
            }
            return;
        }

        if (rimeReady && composing.length() == 0) {
            handleRimeLetter(key);
            return;
        }

        composing.append(key.output);
        Log.d(TAG, "letter=" + key.output + " composing=" + composing);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(composing.toString(), 1);
        }
        if (shifted()) {
            shift = false;
            keyboardView.setShiftState(false, caps);
        }
        refreshCandidates();
    }

    private void handleRimeLetter(Key key) {
        if (rime == null || key.output == null || key.output.isEmpty()) {
            return;
        }
        RimeProcessResult result = rime.processKey(key.output.charAt(0));
        if (result == null) {
            // 引擎尚未就绪/繁忙：退回 Java 引擎，避免丢键
            composing.append(key.output);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.setComposingText(composing.toString(), 1);
            }
            refreshCandidates();
            return;
        }
        Log.d(TAG, "rime letter=" + key.output + " input=" + result.inputText
                + " candidates=" + (result.candidates == null ? 0 : result.candidates.length));
        applyRimeResult(result, getCurrentInputConnection());
        if (shifted()) {
            shift = false;
            keyboardView.setShiftState(false, caps);
        }
    }

    private void applyRimeResult(RimeProcessResult result, InputConnection ic) {
        applyRimeResult(result, ic, true);
    }

    private void applyRimeResult(RimeProcessResult result, InputConnection ic,
                                 boolean commitCommittedText) {
        if (result == null) {
            return;
        }
        if (commitCommittedText && result.committedText != null
                && !result.committedText.isEmpty()) {
            if (ic != null) {
                ic.commitText(result.committedText, 1);
            }
            Log.i(TAG, "rime auto commit=" + result.committedText);
        }
        rimeInput = result.inputText == null ? "" : result.inputText;
        rimePreedit = result.preeditText == null || result.preeditText.isEmpty()
                ? rimeInput : result.preeditText;
        RimeCandidate[] rimeCandidates = result.candidates == null
                ? new RimeCandidate[0] : result.candidates;
        candidates.clear();
        rimeCandidateIndices.clear();
        for (int i = 0; i < rimeCandidates.length; i++) {
            RimeCandidate candidate = rimeCandidates[i];
            if (candidate != null && candidate.text != null && !candidate.text.isEmpty()) {
                candidates.add(candidate.text);
                rimeCandidateIndices.add(i);
            }
        }
        rimeComposing = !rimeInput.isEmpty();
        rimeCandidateMode = rimeComposing && !candidates.isEmpty();
        if (rimeComposing) {
            if (ic != null) {
                ic.setComposingText(rimePreedit, 1);
            }
        } else {
            if (ic != null) {
                ic.finishComposingText();
            }
            candidates.clear();
            rimeCandidateIndices.clear();
            rimeCandidateMode = false;
        }
        updateReadout();
        renderCandidates();
    }

    private void resetRimeFields() {
        rimeComposing = false;
        rimeCandidateMode = false;
        rimeInput = "";
        rimePreedit = "";
        rimeCandidateIndices.clear();
    }

    /** 提交 Rime 当前键入的原始字母（不取候选），用于回车/符号/切换语言。 */
    private void commitRimeRaw() {
        if (!rimeComposing) {
            return;
        }
        String raw = rimeInput;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && raw != null && !raw.isEmpty()) {
            ic.commitText(raw, 1);
            ic.finishComposingText();
        }
        Log.d(TAG, "rime commitRaw=" + raw);
        if (rime != null && rimeReady) {
            rime.clearComposition();
        }
        resetRimeFields();
        candidates.clear();
        rimeCandidateIndices.clear();
        updateReadout();
        renderCandidates();
    }

    private void commitRimeCandidate(int rimeIndex) {
        if (rime == null || !rimeReady) {
            return;
        }
        boolean selected = rime.selectCandidate(rimeIndex);
        if (!selected) {
            Log.w(TAG, "rime select rejected index=" + rimeIndex);
            return;
        }
        String text = rime.commit();
        RimeProcessResult after = rime.getProcessResult(true);
        String finalText = text != null && !text.isEmpty() ? text
                : (after != null && after.committedText != null ? after.committedText : "");
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (!finalText.isEmpty()) {
                ic.commitText(finalText, 1);
            } else {
                // 候选没有返回文字时清掉组合区，绝不把原始拼音字母落进输入框
                ic.setComposingText("", 1);
            }
            ic.finishComposingText();
        }
        Log.i(TAG, "rime select=" + rimeIndex + " commit=" + finalText);
        resetRimeFields();
        candidates.clear();
        if (after != null && after.inputText != null && !after.inputText.isEmpty()) {
            applyRimeResult(after, ic, false);
        } else {
            updateReadout();
            renderCandidates();
        }
    }

    private void selectCandidateAt(int index, String text) {
        if (rimeCandidateMode && rimeReady) {
            int rimeIndex = index;
            if (index >= 0 && index < rimeCandidateIndices.size()) {
                rimeIndex = rimeCandidateIndices.get(index);
            }
            commitRimeCandidate(rimeIndex);
        } else {
            commitCandidate(text);
        }
    }

    private void handleText(Key key) {
        if (chinese && rimeComposing) {
            commitRimeRaw();
        } else if (chinese && composing.length() > 0) {
            finishComposition(true);
        }
        String out = key.output;
        if (chinese && keyboardRes == R.xml.keyboard_qwerty) {
            out = mapPunctuation(out);
        }
        commitText(out);
    }

    private void handleSpace() {
        Log.d(TAG, "space composing=" + composing + " rime=" + rimeComposing
                + " candidates=" + candidates.size());
        if (chinese && rimeComposing) {
            if (!candidates.isEmpty()) {
                selectCandidateAt(0, candidates.get(0));
            } else {
                commitRimeRaw();
            }
            return;
        }
        if (chinese && composing.length() > 0) {
            if (!candidates.isEmpty()) {
                commitCandidate(candidates.get(0));
            } else {
                commitRaw();
            }
            return;
        }
        commitText(" ");
    }

    private void handleEnter() {
        if (rimeComposing) {
            commitRimeRaw();
        } else if (composing.length() > 0) {
            finishComposition(true);
        }
        if (!sendDefaultEditorAction(true)) {
            commitText("\n");
        }
    }

    private void handleBackspace() {
        if (chinese && rimeComposing && rimeReady && rime != null) {
            RimeProcessResult result = rime.processBackspace();
            applyRimeResult(result, getCurrentInputConnection());
            return;
        }
        if (composing.length() > 0) {
            composing.deleteCharAt(composing.length() - 1);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                if (composing.length() > 0) {
                    ic.setComposingText(composing.toString(), 1);
                } else {
                    ic.finishComposingText();
                }
            }
            refreshCandidates();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
            updateReadout();
        }
    }

    private void commitText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Log.d(TAG, "commitText=" + text);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            updateReadout();
        }
    }

    private void commitCandidate(String text) {
        Log.i(TAG, "commit candidate=" + text);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
            ic.finishComposingText();
        }
        clearComposition();
    }

    private void commitRaw() {
        if (composing.length() == 0) {
            return;
        }
        String raw = composing.toString();
        Log.d(TAG, "commitRaw=" + raw);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(raw, 1);
            ic.finishComposingText();
        }
        clearComposition();
    }

    private void finishComposition(boolean preferFirstCandidate) {
        if (composing.length() == 0) {
            return;
        }
        if (preferFirstCandidate && !candidates.isEmpty()) {
            commitCandidate(candidates.get(0));
        } else {
            commitRaw();
        }
    }

    private void clearComposition() {
        Log.d(TAG, "clearComposition composing=" + composing + " rime=" + rimeComposing);
        if (rimeComposing) {
            if (rime != null && rimeReady) {
                rime.clearComposition();
            }
            resetRimeFields();
        }
        if (composing.length() > 0) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
        composing.setLength(0);
        candidates.clear();
        rimeCandidateIndices.clear();
        rimeCandidateMode = false;
        updateReadout();
        updateSuggestionPlaceholder();
    }

    // ---------------------------------------------------------------------
    // 候选栏 / 状态栏
    // ---------------------------------------------------------------------

    private void refreshCandidates() {
        candidates.clear();
        rimeCandidateIndices.clear();
        rimeCandidateMode = false;
        if (chinese && composing.length() > 0 && engine.isReady()) {
            candidates.addAll(engine.query(composing.toString(), MAX_CANDIDATES));
        }
        Log.d(TAG, "refresh composing=" + composing + " ready=" + engine.isReady()
                + " count=" + candidates.size());
        updateReadout();
        if (!engine.isReady() && chinese && composing.length() > 0) {
            setSuggestionLoading();
        } else {
            renderCandidates();
        }
    }

    private void updateReadout() {
        if (inputRow == null) {
            return;
        }
        if (chinese && (rimeComposing || composing.length() > 0)) {
            // 输入中的拼音只显示为左上角小标签，下方区域全部留给候选词
            inputRow.setVisibility(View.GONE);
            stopCaretBlink();
            // 输入框隐藏后候选从左侧开始；把之前的横向滚动归零，
            // 否则长文本滚动后候选会被留在可视区域之外，看起来像“打不了字”。
            if (fieldScroll != null) {
                fieldScroll.scrollTo(0, 0);
            }
            String tag;
            if (rimeComposing) {
                tag = rimeTagText();
            } else {
                tag = engine.isReady()
                        ? engine.formatSyllables(composing.toString())
                        : composing.toString();
            }
            pinyinTag.setText(tag);
            pinyinTag.setTextColor(0xFFDDDDDD);
            return;
        }

        inputRow.setVisibility(View.VISIBLE);
        pinyinTag.setText(chinese ? "拼" : "EN");
        pinyinTag.setTextColor(0xFFDDDDDD);

        String before = textBeforeCursor();
        String after = textAfterCursor();
        readoutBefore = before == null ? "" : before;
        readoutAfter = after == null ? "" : after;

        if (readoutBefore.isEmpty() && readoutAfter.isEmpty()) {
            beforeText.setVisibility(View.GONE);
            afterText.setVisibility(View.VISIBLE);
            afterText.setText(chinese ? R.string.hint_zh : R.string.hint_en);
            afterText.setTextColor(0xFF999999);
        } else {
            beforeText.setVisibility(readoutBefore.isEmpty() ? View.GONE : View.VISIBLE);
            beforeText.setText(readoutBefore);
            beforeText.setTextColor(0xFFF2F2F2);
            afterText.setVisibility(readoutAfter.isEmpty() ? View.GONE : View.VISIBLE);
            afterText.setText(readoutAfter);
            afterText.setTextColor(0xFFF2F2F2);
        }
        caretView.setVisibility(View.VISIBLE);
        startCaretBlink();
        scrollReadoutToCaret();
    }

    private String textBeforeCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        CharSequence before = ic.getTextBeforeCursor(READOUT_CONTEXT_CHARS, 0);
        return before == null ? null : before.toString();
    }

    private String textAfterCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return null;
        }
        CharSequence after = ic.getTextAfterCursor(READOUT_CONTEXT_CHARS, 0);
        return after == null ? null : after.toString();
    }

    private void scrollReadoutToCaret() {
        if (fieldScroll == null || caretView == null) {
            return;
        }
        fieldScroll.post(new Runnable() {
            @Override
            public void run() {
                if (fieldScroll == null || caretView == null
                        || rimeComposing || composing.length() > 0) {
                    return;
                }
                int caretX = caretView.getLeft() + caretView.getWidth() / 2;
                int target = caretX - fieldScroll.getWidth() / 2;
                fieldScroll.scrollTo(Math.max(0, target), 0);
            }
        });
    }

    /** 点击输入框里的文字：把光标定位到对应字符前/后。 */
    private void handleReadoutTap(float contentX) {
        if (rimeComposing || composing.length() > 0) {
            return;
        }
        int beforeLen = readoutBefore.length();
        int afterLen = readoutAfter.length();
        if (beforeLen == 0 && afterLen == 0) {
            return;
        }
        int delta = 0;
        if (beforeText.getVisibility() == View.VISIBLE) {
            int left = beforeText.getLeft();
            int right = beforeText.getRight();
            if (contentX >= left && contentX <= right) {
                int offset = beforeText.getOffsetForPosition(contentX - left,
                        beforeText.getHeight() / 2f);
                offset = Math.max(0, Math.min(beforeLen, offset));
                delta = -(beforeLen - offset);
            }
        }
        if (delta == 0 && afterText.getVisibility() == View.VISIBLE) {
            int left = afterText.getLeft();
            int right = afterText.getRight();
            if (contentX >= left && contentX <= right) {
                int offset = afterText.getOffsetForPosition(contentX - left,
                        afterText.getHeight() / 2f);
                offset = Math.max(0, Math.min(afterLen, offset));
                delta = offset;
            } else if (contentX > right) {
                delta = afterLen;
            }
        }
        if (delta != 0) {
            moveCaretBy(delta);
            updateReadout();
        }
        Log.d(TAG, "readout tap x=" + contentX + " delta=" + delta
                + " before=" + beforeLen + " after=" + afterLen
                + " bGeo=" + beforeText.getLeft() + "," + beforeText.getRight()
                + " " + beforeText.getWidth() + "x" + beforeText.getHeight()
                + " layout=" + (beforeText.getLayout() != null)
                + " aGeo=" + afterText.getLeft() + "," + afterText.getRight()
                + " " + afterText.getWidth() + "x" + afterText.getHeight());
    }

    /**
     * 相对移动光标，返回实际移动的字符数（供滑轨贴边/累积使用）。
     * 优先使用 ExtractedText + setSelection（瞬时、无按键音）；
     * 编辑器不支持时退回 DPAD 左右键事件。
     */
    private int moveCaretBy(int delta) {
        if (delta == 0) {
            return 0;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return 0;
        }
        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = 512;
            request.hintMaxLines = 0;
            ExtractedText extracted = ic.getExtractedText(request, 0);
            if (extracted != null && extracted.text != null && extracted.selectionStart >= 0) {
                int base = extracted.startOffset;
                int selection = base + extracted.selectionStart;
                int target = Math.max(base, Math.min(base + extracted.text.length(),
                        selection + delta));
                if (target == selection) {
                    Log.d(TAG, "caret at boundary selection=" + selection + " delta=" + delta);
                    return 0;
                }
                ic.setSelection(target, target);
                Log.d(TAG, "caret selection=" + selection + " target=" + target
                        + " moved=" + (target - selection) + " delta=" + delta);
                return target - selection;
            }
        } catch (Throwable t) {
            Log.w(TAG, "getExtractedText/setSelection failed", t);
        }
        int keyCode = delta > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        int steps = Math.min(Math.abs(delta), 128);
        if (steps == 0) {
            return 0;
        }
        for (int i = 0; i < steps; i++) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        }
        Log.d(TAG, "caret dpad key=" + keyCode + " steps=" + steps);
        return delta > 0 ? steps : -steps;
    }

    private String rimeTagText() {
        String text = rimePreedit != null && !rimePreedit.isEmpty() ? rimePreedit : rimeInput;
        return text == null ? "" : text.replace(' ', '\'');
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
                                  int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (composing.length() == 0) {
            updateReadout();
        }
    }

    private void renderCandidates() {
        if (suggestionList == null) {
            return;
        }
        suggestionList.removeAllViews();
        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            final String candidate = candidates.get(i);
            TextView tv = new TextView(this);
            tv.setText(candidate);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tv.setTextColor(0xFFF2F2F2);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setMinWidth(dp(48));
            tv.setPadding(dp(12), 0, dp(12), 0);
            tv.setBackgroundResource(R.drawable.candidate_bg);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (keyboardView != null) {
                        keyboardView.hapticSelect();
                    }
                    selectCandidateAt(index, candidate);
                }
            });
            suggestionList.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
        }
        Log.d(TAG, "render candidates=" + candidates.size()
                + " views=" + suggestionList.getChildCount());
    }

    private void setSuggestionLoading() {
        if (suggestionList == null) {
            return;
        }
        suggestionList.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(R.string.loading_dict);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(0xFFE0E0E0);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(12), 0, dp(12), 0);
        suggestionList.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)));
    }

    private void updateSuggestionPlaceholder() {
        if (suggestionList == null) {
            return;
        }
        if (chinese && composing.length() > 0 && !engine.isReady()) {
            setSuggestionLoading();
        } else {
            renderCandidates();
        }
    }

    private void updateLanguageUi() {
        if (pinyinTag == null) {
            return;
        }
        if (composing.length() == 0) {
            pinyinTag.setText(chinese ? "拼" : "EN");
        }
        updateReadout();
    }

    // ---------------------------------------------------------------------
    // 状态切换
    // ---------------------------------------------------------------------

    private void toggleShift() {
        if (caps) {
            caps = false;
            shift = false;
        } else if (shift) {
            shift = false;
            caps = true;
        } else {
            shift = true;
            caps = false;
        }
        keyboardView.setShiftState(shift || caps, caps);
    }

    private boolean shifted() {
        return shift || caps;
    }

    private void toggleLanguage() {
        if (rimeComposing) {
            commitRimeRaw();
        }
        chinese = !chinese;
        Log.i(TAG, "language=" + (chinese ? "zh" : "en"));
        clearComposition();
        shift = false;
        caps = false;
        if (keyboardView != null) {
            keyboardView.setShiftState(false, false);
        }
        updateLanguageUi();
        updateSuggestionPlaceholder();
    }

    private void switchKeyboard(int resId) {
        if (rimeComposing) {
            commitRimeRaw();
        }
        keyboardRes = resId;
        shift = false;
        caps = false;
        if (keyboardView != null) {
            keyboardView.setKeyboard(Keyboard.load(this, resId));
            keyboardView.setShiftState(false, false);
        }
        clearComposition();
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /** 与原生白色键盘键对应：切换输入法。 */
    private void showImePicker() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
                return;
            }
        } catch (Exception ignored) {
        }
        openSettings();
    }

    private String mapPunctuation(String out) {
        if (out == null) {
            return null;
        }
        if (".".equals(out)) {
            return "。";
        }
        if ("/".equals(out)) {
            return "、";
        }
        if (",".equals(out)) {
            return "，";
        }
        if ("?".equals(out)) {
            return "？";
        }
        if ("!".equals(out)) {
            return "！";
        }
        if (":".equals(out)) {
            return "：";
        }
        if (";".equals(out)) {
            return "；";
        }
        if ("(".equals(out)) {
            return "（";
        }
        if (")".equals(out)) {
            return "）";
        }
        if ("'".equals(out)) {
            return "’";
        }
        if ("\"".equals(out)) {
            return "”";
        }
        return out;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 固定 624 x 340 dp 的面板，与 VrShell 键盘窗口完全一致。 */
    private static class PanelRoot extends FrameLayout {
        private final int panelWidth;
        private int panelHeight;

        PanelRoot(Context context, int width, int height) {
            super(context);
            this.panelWidth = width;
            this.panelHeight = height;
        }

        void setPanelHeight(int height) {
            if (panelHeight != height) {
                panelHeight = height;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.makeMeasureSpec(panelWidth, MeasureSpec.EXACTLY);
            int h = MeasureSpec.makeMeasureSpec(panelHeight, MeasureSpec.EXACTLY);
            super.onMeasure(w, h);
        }
    }
}
