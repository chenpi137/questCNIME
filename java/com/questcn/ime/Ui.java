package com.questcn.ime;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 设置 / OOBE 的暗色界面工具箱。
 *
 * 设计稿是一张 426x752 的竖屏画布：底色 #1C1C1E，卡片 #262626，白色胶囊按钮。
 * 这里用纯代码搭 UI（项目不引入 Gradle / AppCompat），所有尺寸走 dp，
 * 内容列限制最大宽度并在窗口内居中，横屏面板下也不会被拉成一条。
 */
final class Ui {
    static final int BG = 0xFF1C1C1E;
    static final int CARD = 0xFF262626;
    static final int CARD_PRESSED = 0xFF303030;
    static final int TEXT = 0xFFF2F2F2;
    static final int TEXT_BODY = 0xFFAAAAAA;
    static final int TEXT_DIM = 0xFF8A8A8A;
    static final int TEXT_LINK = 0xFF99AACC;
    static final int DOT_INACTIVE = 0xFF4A4A4C;
    static final int BUTTON = 0xFFF2F2F2;
    static final int BUTTON_PRESSED = 0xFFD8D8D8;
    static final int BUTTON_TEXT = 0xFF1C1C1E;
    static final int SUCCESS = 0xFF66CC88;
    static final int WARNING = 0xFFFFAA66;
    static final int ERROR = 0xFFFF8888;

    private static final int CARD_RADIUS_DP = 14;
    private static final int CONTENT_MAX_DP = 480;

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void applyWindow(Activity activity) {
        activity.getWindow().setStatusBarColor(BG);
        activity.getWindow().setNavigationBarColor(BG);
    }

    // ------------------------------------------------------------------ page

    /** 页面骨架：居中内容列 + 可滚动正文 + 固定在底部的操作区。 */
    static final class Page {
        FrameLayout root;
        LinearLayout column;
        LinearLayout body;
        LinearLayout footer;
        ScrollView scroll;
    }

    static Page page(Activity activity, boolean scrollBody) {
        Page page = new Page();

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);
        page.root = root;

        int screenDp = activity.getResources().getConfiguration().screenWidthDp;
        int columnDp = Math.min(CONTENT_MAX_DP, Math.max(0, screenDp));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        FrameLayout.LayoutParams columnLp;
        if (columnDp > 0) {
            columnLp = new FrameLayout.LayoutParams(
                    dp(activity, columnDp), ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            columnLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        columnLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(column, columnLp);
        page.column = column;

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(activity, 24), 0, dp(activity, 12));

        if (scrollBody) {
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(body, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            column.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            page.scroll = scroll;
        } else {
            column.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        page.body = body;

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(0, dp(activity, 8), 0, dp(activity, 20));
        column.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.footer = footer;

        return page;
    }

    // ----------------------------------------------------------------- text

    static TextView text(Context context, CharSequence value, float sizeSp, int color,
                         Typeface typeface) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setGravity(Gravity.START);
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView title(Context context, CharSequence value, boolean centered) {
        TextView view = text(context, value, 26, TEXT,
                Typeface.create("sans-serif", Typeface.BOLD));
        view.setGravity(centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        view.setLineSpacing(dp(context, 2), 1f);
        view.setPadding(0, dp(context, 4), 0, dp(context, 16));
        return view;
    }

    static TextView bodyText(Context context, CharSequence value) {
        TextView view = text(context, value, 14, TEXT_BODY,
                Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    static TextView statusText(Context context) {
        TextView view = text(context, "", 12, TEXT_DIM,
                Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(context, 2), 1f);
        view.setPadding(0, dp(context, 6), 0, 0);
        return view;
    }

    static TextView heading(Context context, CharSequence value) {
        TextView view = text(context, value, 15, TEXT,
                Typeface.create("sans-serif", Typeface.BOLD));
        view.setPadding(0, dp(context, 16), 0, dp(context, 4));
        return view;
    }

    static TextView linkText(Context context, SpannableStringBuilder value) {
        TextView view = text(context, value, 13, TEXT_BODY,
                Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(context, 3), 1f);
        view.setLinkTextColor(TEXT_LINK);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        return view;
    }

    static void appendLink(SpannableStringBuilder builder, String label, String url) {
        int start = builder.length();
        builder.append(label);
        int end = builder.length();
        builder.setSpan(new URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ----------------------------------------------------------------- cards

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(CARD, dp(context, CARD_RADIUS_DP)));
        return card;
    }

    /** 同一张卡片里的多行列表：用背景色窄缝模拟分隔线。 */
    static LinearLayout rowGroup(Context context, View[] rows) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < rows.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = dp(context, 4);
            }
            group.addView(rows[i], lp);
        }
        return group;
    }

    /** 一行设置项；subtitle 为 null 时只有标题。 */
    static final class Row {
        final LinearLayout view;
        final TextView subtitle;

        Row(LinearLayout view, TextView subtitle) {
            this.view = view;
            this.subtitle = subtitle;
        }
    }

    static Row settingsRow(Context context, int iconRes, String label, String subtitle,
                           boolean top, boolean bottom, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        row.setMinimumHeight(dp(context, 56));
        row.setBackground(rowBackground(context, top, bottom));

        if (iconRes != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(TEXT);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dp(context, 22), dp(context, 22));
            iconLp.rightMargin = dp(context, 14);
            row.addView(icon, iconLp);
        }

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, label, 16, TEXT,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labels.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView sub = null;
        if (subtitle != null && subtitle.length() > 0) {
            sub = text(context, subtitle, 10, TEXT_DIM,
                    Typeface.create("sans-serif", Typeface.NORMAL));
            sub.setLineSpacing(dp(context, 1), 1f);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(context, 2);
            labels.addView(sub, subLp);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(listener);
        }
        return new Row(row, sub);
    }

    private static StateListDrawable rowBackground(Context context, boolean top, boolean bottom) {
        float radius = dp(context, CARD_RADIUS_DP);
        float[] radii = new float[]{
                top ? radius : 0, top ? radius : 0,
                top ? radius : 0, top ? radius : 0,
                bottom ? radius : 0, bottom ? radius : 0,
                bottom ? radius : 0, bottom ? radius : 0
        };
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(CARD);
        normal.setCornerRadii(radii);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(CARD_PRESSED);
        pressed.setCornerRadii(radii);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    /** 卡片内可独立滚动的正文区域。 */
    static ScrollView cardScroll(Context context, LinearLayout card) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return scroll;
    }

    // --------------------------------------------------------------- buttons

    static final class PillButton extends FrameLayout {
        final TextView label;

        PillButton(Context context, boolean withArrow, CharSequence value) {
            super(context);
            GradientDrawable normal = rounded(BUTTON, dp(context, 24));
            GradientDrawable pressed = rounded(BUTTON_PRESSED, dp(context, 24));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, pressed);
            states.addState(new int[]{}, normal);
            setBackground(states);
            setClickable(true);
            setFocusable(true);

            if (withArrow) {
                ImageView arrow = new ImageView(context);
                arrow.setImageResource(R.drawable.ic_arrow_left_24);
                arrow.setColorFilter(BUTTON_TEXT);
                FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(
                        dp(context, 20), dp(context, 20),
                        Gravity.START | Gravity.CENTER_VERTICAL);
                arrowLp.leftMargin = dp(context, 18);
                addView(arrow, arrowLp);
            }

            label = new TextView(context);
            label.setText(value);
            label.setTextSize(18);
            label.setTextColor(BUTTON_TEXT);
            label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            if (withArrow) {
                label.setPadding(dp(context, 44), 0, dp(context, 44), 0);
            }
            addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void setText(CharSequence value) {
            label.setText(value);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setAlpha(enabled ? 1f : 0.35f);
            setClickable(enabled);
        }
    }

    static PillButton button(Context context, CharSequence value) {
        return new PillButton(context, false, value);
    }

    static PillButton backButton(Context context, CharSequence value) {
        return new PillButton(context, true, value);
    }

    /** 底部主按钮：左右各留 24dp，和设计稿的白条宽度一致。 */
    static void addFooterButton(LinearLayout footer, PillButton button) {
        Context context = button.getContext();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48));
        lp.leftMargin = dp(context, 24);
        lp.rightMargin = dp(context, 24);
        lp.topMargin = dp(context, 12);
        footer.addView(button, lp);
    }

    /** 卡片内按钮：卡片已经有 16dp 内边距，再各留 8dp 即与底部按钮对齐。 */
    static void addCardButton(LinearLayout card, PillButton button) {
        Context context = button.getContext();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48));
        lp.leftMargin = dp(context, 8);
        lp.rightMargin = dp(context, 8);
        lp.topMargin = dp(context, 20);
        card.addView(button, lp);
    }

    // ---------------------------------------------------------------- pieces

    static void addDots(Context context, LinearLayout footer, int active) {
        LinearLayout dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View segment = new View(context);
            segment.setBackground(rounded(i == active ? TEXT : DOT_INACTIVE, dp(context, 2)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    dp(context, 64), dp(context, 3));
            lp.leftMargin = dp(context, 3);
            lp.rightMargin = dp(context, 3);
            dots.addView(segment, lp);
        }
        footer.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static LinearLayout captionRow(Context context, CharSequence value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_info_circle_24);
        icon.setAlpha(0.55f);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                dp(context, 12), dp(context, 12));
        iconLp.rightMargin = dp(context, 5);
        row.addView(icon, iconLp);
        TextView label = text(context, value, 10, TEXT_DIM,
                Typeface.create("sans-serif", Typeface.NORMAL));
        row.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    static android.widget.ProgressBar progressBar(Context context) {
        android.widget.ProgressBar progress = new android.widget.ProgressBar(
                context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setProgressTintList(ColorStateList.valueOf(TEXT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF3A3A3C));
        progress.setIndeterminateTintList(ColorStateList.valueOf(TEXT));
        return progress;
    }
}
