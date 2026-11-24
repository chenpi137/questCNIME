package com.questcn.ime;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 首次启动引导：免责声明/用户协议 -> 启用输入法 -> 初始化文件 -> 选择输入法。
 * 全部完成后写入 oobe_done，后续启动直接进入常规设置页。
 */
public class OobeActivity extends Activity {
    private static final String TAG = "QuestIME";
    private static final String PREFS = "questcn_ime";
    private static final String KEY_AGREED = "oobe_agreed";
    private static final String KEY_DONE = "oobe_done";

    private SharedPreferences prefs;
    private int step; // 0=协议, 1=启用, 2=初始化, 3=选择输入法
    private TextView statusView;
    private ProgressBar progress;
    private Button actionButton;
    private Button nextButton;
    private boolean initializing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) {
            goMain();
            return;
        }
        if (prefs.getBoolean(KEY_AGREED, false)) {
            showStep(1);
        } else {
            showAgreement();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null && (step == 1 || step == 3)) {
            refreshStatus();
        }
    }

    @Override
    public void onBackPressed() {
        if (initializing) {
            Toast.makeText(this, "正在初始化，请稍候…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (step > 1) {
            showStep(step - 1);
        } else if (step == 1) {
            showAgreement();
        } else {
            finishAffinity();
        }
    }

    // ---------------------------------------------------------------- agreement

    private void showAgreement() {
        step = 0;
        LinearLayout root = baseRoot();

        TextView title = text("欢迎使用 Quest 中文输入法", 26, 0xFFF2F2F2, Typeface.BOLD);
        root.addView(title);

        TextView intro = text("首次使用前，请阅读以下免责声明、用户协议和开源致谢。"
                + "继续即表示你理解并接受这些条款。", 14, 0xFFAAAAAA, Typeface.NORMAL);
        intro.setPadding(0, dp(8), 0, dp(4));
        root.addView(intro);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSection(content, "免责声明", getString(R.string.oobe_disclaimer));
        addSection(content, "用户协议", getString(R.string.oobe_agreement));
        addSection(content, "开源致谢", getString(R.string.oobe_acknowledgements));

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final CheckBox agree = new CheckBox(this);
        agree.setText(R.string.oobe_agree_check);
        agree.setTextSize(14);
        agree.setTextColor(0xFFF2F2F2);
        agree.setPadding(0, dp(8), 0, dp(8));
        root.addView(agree, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, 0);

        Button exit = makeButton(getString(R.string.oobe_exit), false);
        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
            }
        });
        row.addView(exit, new LinearLayout.LayoutParams(0, dp(52), 1f));

        final Button ok = makeButton(getString(R.string.oobe_agree), true);
        ok.setEnabled(false);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(KEY_AGREED, true).apply();
                showStep(1);
            }
        });
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(0, dp(52), 1.4f);
        okLp.leftMargin = dp(12);
        row.addView(ok, okLp);
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        agree.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                ok.setEnabled(isChecked);
            }
        });

        setContentView(root);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, 0);
            }
        });
    }

    // -------------------------------------------------------------------- steps

    private void showStep(int target) {
        step = target;
        LinearLayout root = baseRoot();

        TextView kicker = text("步骤 " + step + " / 3", 13, 0xFF6699FF, Typeface.BOLD);
        root.addView(kicker);

        String title;
        String body;
        String action;
        if (step == 1) {
            title = "启用输入法";
            body = "点击下方按钮打开系统输入法设置，在列表中找到并启用"
                    + "“Quest 中文输入法”。\n\n如果系统设置页没有出现，请检查是否允许打开系统设置，"
                    + "或稍后从系统设置中手动进入“输入法”页面。";
            action = "打开系统设置";
        } else if (step == 2) {
            title = "初始化文件（必要）";
            body = "首次使用需要把词库复制到应用目录并编译 Rime 索引，"
                    + "通常需要 10～40 秒，请保持头显唤醒并留在本页面。\n\n"
                    + "初始化完成后，键盘才能显示中文候选。";
            action = "初始化文件";
        } else {
            title = "选择输入法";
            body = "点击下方按钮弹出系统输入法选择器，选择“Quest 中文输入法”。\n\n"
                    + "选择成功后，“进入键盘”按钮会变为可用。";
            action = "选择输入法";
        }

        TextView titleView = text(title, 24, 0xFFF2F2F2, Typeface.BOLD);
        titleView.setPadding(0, dp(6), 0, dp(8));
        root.addView(titleView);

        TextView bodyView = text(body, 15, 0xFFCCCCCC, Typeface.NORMAL);
        bodyView.setLineSpacing(dp(3), 1f);
        root.addView(bodyView);

        actionButton = makeButton(action, true);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        actionLp.topMargin = dp(20);
        root.addView(actionButton, actionLp);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        progressLp.topMargin = dp(14);
        root.addView(progress, progressLp);

        statusView = text("", 14, 0xFFAAAAAA, Typeface.NORMAL);
        statusView.setLineSpacing(dp(3), 1f);
        statusView.setPadding(0, dp(12), 0, 0);
        root.addView(statusView);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(12), 0, 0);

        Button back = makeButton("上一步", false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (step > 1) {
                    showStep(step - 1);
                } else {
                    showAgreement();
                }
            }
        });
        nav.addView(back, new LinearLayout.LayoutParams(0, dp(52), 1f));

        nextButton = makeButton(step == 3 ? "进入键盘" : "下一步", true);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (step == 3) {
                    finishOobe();
                } else {
                    showStep(step + 1);
                }
            }
        });
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(52), 1.4f);
        nextLp.leftMargin = dp(12);
        nav.addView(nextButton, nextLp);
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        if (step == 1) {
            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openImeSettings();
                }
            });
        } else if (step == 2) {
            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startRimeInit();
                }
            });
        } else {
            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickIme();
                }
            });
        }
        refreshStatus();
    }

    // ------------------------------------------------------------------ actions

    private void openImeSettings() {
        // Quest 默认会把该 action 解析到 VrShell 的 Quest 设置；这里强制指定
        // com.android.settings，打开 Android 原生的“可用虚拟键盘”页面。
        try {
            Intent nativeSettings = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            nativeSettings.setPackage("com.android.settings");
            startActivity(nativeSettings);
            return;
        } catch (Exception nativeFailed) {
            Log.w(TAG, "native Android settings unavailable", nativeFailed);
        }
        try {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception second) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pickIme() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) {
                Toast.makeText(this, R.string.ime_picker_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            imm.showInputMethodPicker();
            statusView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshStatus();
                }
            }, 800);
        } catch (Exception e) {
            Toast.makeText(this, R.string.ime_picker_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void startRimeInit() {
        if (initializing) {
            return;
        }
        final RimeCore core = RimeCore.get();
        if (core.isReady()) {
            refreshStatus();
            return;
        }
        initializing = true;
        actionButton.setEnabled(false);
        nextButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("正在复制词库并编译索引，请稍候…", 0xFF6699FF);
        core.initializeAsync(getApplicationContext(), true, new RimeCore.Callback() {
            @Override
            public void onReady(final boolean success, final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initializing = false;
                        if (isFinishing()) {
                            return;
                        }
                        progress.setVisibility(View.GONE);
                        actionButton.setEnabled(true);
                        boolean ok = success || core.isReady();
                        if (ok) {
                            setStatus("初始化完成，词库已就绪。", 0xFF66CC88);
                            nextButton.setEnabled(true);
                        } else {
                            String reason = (error == null || error.isEmpty())
                                    ? core.getError() : error;
                            setStatus("初始化失败：" + reason + "\n请点击按钮重试。", 0xFFFF8888);
                            Log.w(TAG, "OOBE rime init failed: " + reason);
                        }
                    }
                });
            }
        });
    }

    private void finishOobe() {
        if (!isOurImeCurrent()) {
            setStatus("当前仍未选中“Quest 中文输入法”，请重新选择。", 0xFFFFAA66);
            return;
        }
        prefs.edit().putBoolean(KEY_DONE, true).apply();
        goMain();
    }

    private void goMain() {
        startActivity(new Intent(this, SettingsActivity.class));
        finish();
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        if (step == 1) {
            if (isOurImeEnabled()) {
                setStatus("状态：已启用。现在可以进入下一步。", 0xFF66CC88);
            } else {
                setStatus("状态：尚未启用。请点击上方按钮，在系统设置中启用本输入法。", 0xFFFFAA66);
            }
        } else if (step == 2) {
            RimeCore core = RimeCore.get();
            if (core.isReady()) {
                setStatus("状态：已初始化完成。", 0xFF66CC88);
                nextButton.setEnabled(true);
            } else if (!initializing) {
                setStatus("状态：尚未初始化。首次使用必须完成此步骤。", 0xFFFFAA66);
                nextButton.setEnabled(false);
            }
        } else if (step == 3) {
            boolean current = isOurImeCurrent();
            String name = currentImeName();
            if (current) {
                setStatus("状态：当前输入法为“" + name + "”。", 0xFF66CC88);
            } else if (isOurImeEnabled()) {
                setStatus("状态：已启用，但当前输入法是“" + name + "”。请点击上方按钮切换。",
                        0xFFFFAA66);
            } else {
                setStatus("状态：输入法尚未启用。请先返回第 1 步启用。", 0xFFFF8888);
            }
            nextButton.setEnabled(current);
        }
    }

    private boolean isOurImeEnabled() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) {
                return false;
            }
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (getPackageName().equals(info.getPackageName())) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "isOurImeEnabled failed", t);
        }
        return false;
    }

    private boolean isOurImeCurrent() {
        try {
            String current = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            return current != null && current.startsWith(getPackageName() + "/");
        } catch (Throwable t) {
            Log.w(TAG, "isOurImeCurrent failed", t);
            return false;
        }
    }

    private String currentImeName() {
        try {
            String current = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            if (current == null) {
                return "系统默认键盘";
            }
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    if (current.equals(info.getId())) {
                        CharSequence label = info.loadLabel(getPackageManager());
                        if (label != null && label.length() > 0) {
                            return label.toString();
                        }
                    }
                }
            }
            int slash = current.indexOf('/');
            return slash > 0 ? current.substring(0, slash) : current;
        } catch (Throwable t) {
            return "未知输入法";
        }
    }

    private void setStatus(String message, int color) {
        if (statusView != null) {
            statusView.setText(message);
            statusView.setTextColor(color);
        }
    }

    // -------------------------------------------------------------------- views

    private LinearLayout baseRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1C1C1E);
        root.setPadding(dp(28), dp(24), dp(28), dp(20));
        return root;
    }

    private void addSection(LinearLayout parent, String heading, String body) {
        TextView h = text(heading, 18, 0xFF6699FF, Typeface.BOLD);
        h.setPadding(0, dp(18), 0, dp(6));
        parent.addView(h);
        TextView b = text(body, 14, 0xFFCCCCCC, Typeface.NORMAL);
        b.setLineSpacing(dp(3), 1f);
        parent.addView(b);
    }

    private TextView text(String value, int sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setGravity(Gravity.START);
        return view;
    }

    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(primary ? 0xFF3B6FD4 : 0xFF333333);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
