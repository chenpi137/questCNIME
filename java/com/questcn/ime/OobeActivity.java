package com.questcn.ime;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 首次启动引导，三步：
 * 1. 用户协议条款和免责声明
 * 2. 输入法设置（启用输入法 + 初始化词典）
 * 3. 做的好！最后一步！（选择输入法）
 *
 * 全部完成后写入 oobe_done，后续启动直接进入设置主界面。
 */
public class OobeActivity extends Activity {
    private static final String TAG = "QuestIME";
    private static final String PREFS = "questcn_ime";
    private static final String KEY_AGREED = "oobe_agreed";
    private static final String KEY_DONE = "oobe_done";

    private SharedPreferences prefs;
    private int step; // 0=协议, 1=输入法设置, 2=选择输入法
    private boolean initializing;

    private TextView enableStatus;
    private TextView initStatus;
    private ProgressBar progress;
    private Ui.PillButton initButton;
    private Ui.PillButton nextButton;
    private TextView finishStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.applyWindow(this);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        // 调试入口：adb shell am start -n com.questcn.ime/.OobeActivity \
        //   --ez force_oobe true --ei start_step 1
        // 不清理应用数据也能重新查看引导页，不影响正常启动流程。
        boolean force = getIntent().getBooleanExtra("force_oobe", false);
        if (force) {
            int start = getIntent().getIntExtra("start_step", 0);
            showStep(Math.max(0, Math.min(2, start)));
            return;
        }
        if (prefs.getBoolean(KEY_DONE, false)) {
            goMain();
            return;
        }
        if (prefs.getBoolean(KEY_AGREED, false)) {
            showStep(1);
        } else {
            showStep(0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (step == 1) {
            refreshSetupStatus();
        } else if (step == 2) {
            refreshFinishStatus();
        }
    }

    @Override
    public void onBackPressed() {
        if (initializing) {
            Toast.makeText(this, R.string.oobe_status_dict_running, Toast.LENGTH_SHORT).show();
            return;
        }
        if (step > 0) {
            showStep(step - 1);
        } else {
            finishAffinity();
        }
    }

    // ---------------------------------------------------------------- steps

    private void showStep(int target) {
        step = target;
        enableStatus = null;
        initStatus = null;
        progress = null;
        initButton = null;
        nextButton = null;
        finishStatus = null;
        if (target == 0) {
            showAgreement();
        } else if (target == 1) {
            showSetup();
        } else {
            showFinish();
        }
    }

    // ------------------------------------------------------------ agreement

    private void showAgreement() {
        Ui.Page page = Ui.page(this, false);

        page.body.addView(Ui.title(this,
                getString(R.string.oobe_agreement_title), true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = Ui.card(this);
        page.body.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView scroll = Ui.cardScroll(this, card);
        LinearLayout legal = new LinearLayout(this);
        legal.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(legal, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addSection(legal, "免责声明", getString(R.string.oobe_disclaimer));
        addSection(legal, "用户协议", getString(R.string.oobe_agreement));
        addSection(legal, "开源致谢", getString(R.string.oobe_acknowledgements));

        final CheckBox agree = new CheckBox(this);
        agree.setText(R.string.oobe_agree_check);
        agree.setTextSize(12);
        agree.setTextColor(Ui.TEXT_BODY);
        agree.setButtonTintList(ColorStateList.valueOf(Ui.TEXT));
        agree.setPadding(0, Ui.dp(this, 8), 0, 0);
        page.body.addView(agree, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Ui.addDots(this, page.footer, 0);
        final Ui.PillButton next = Ui.button(this, getString(R.string.action_next));
        next.setEnabled(false);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(KEY_AGREED, true).apply();
                showStep(1);
            }
        });
        nextButton = next;
        Ui.addFooterButton(page.footer, next);

        agree.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                next.setEnabled(isChecked);
            }
        });

        setContentView(page.root);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.scrollTo(0, 0);
            }
        });
    }

    // ---------------------------------------------------------------- setup

    private void showSetup() {
        Ui.Page page = Ui.page(this, true);
        page.body.addView(Ui.title(this, getString(R.string.oobe_setup_title), true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        // 卡片 1：打开系统输入法设置
        LinearLayout enableCard = Ui.card(this);
        enableCard.addView(Ui.bodyText(this, getString(R.string.oobe_setup_enable_hint)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        enableStatus = Ui.statusText(this);
        enableCard.addView(enableStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        enableCard.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 18)));

        Ui.PillButton enableButton = Ui.button(this,
                getString(R.string.oobe_setup_enable_action));
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ImeUtil.openSettings(OobeActivity.this);
            }
        });
        Ui.addCardButton(enableCard, enableButton);
        page.body.addView(enableCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View gap = new View(this);
        LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 10));
        gapLp.topMargin = Ui.dp(this, 10);
        page.body.addView(gap, gapLp);

        // 卡片 2：初始化 Rime 词典
        LinearLayout initCard = Ui.card(this);
        initCard.addView(Ui.bodyText(this, getString(R.string.oobe_setup_init_hint)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        initStatus = Ui.statusText(this);
        initCard.addView(initStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        initCard.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 16)));

        initCard.addView(Ui.captionRow(this, getString(R.string.oobe_setup_progress)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        progress = Ui.progressBar(this);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        progressLp.topMargin = Ui.dp(this, 6);
        initCard.addView(progress, progressLp);

        initButton = Ui.button(this, getString(R.string.oobe_setup_init_action));
        initButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRimeInit();
            }
        });
        Ui.addCardButton(initCard, initButton);
        page.body.addView(initCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Ui.addDots(this, page.footer, 1);
        nextButton = Ui.button(this, getString(R.string.action_next));
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ImeUtil.isOurImeEnabled(OobeActivity.this)
                        || !RimeCore.get().isReady()) {
                    Toast.makeText(OobeActivity.this, R.string.oobe_missing_setup,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                showStep(2);
            }
        });
        Ui.addFooterButton(page.footer, nextButton);

        setContentView(page.root);
        refreshSetupStatus();
    }

    // --------------------------------------------------------------- finish

    private void showFinish() {
        Ui.Page page = Ui.page(this, true);
        page.body.addView(Ui.title(this, getString(R.string.oobe_finish_title), true),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.bodyText(this, getString(R.string.oobe_finish_hint)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        finishStatus = Ui.statusText(this);
        card.addView(finishStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 16)));

        Ui.PillButton pickButton = Ui.button(this,
                getString(R.string.oobe_finish_action));
        pickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickIme();
            }
        });
        Ui.addCardButton(card, pickButton);
        page.body.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Ui.addDots(this, page.footer, 2);
        nextButton = Ui.button(this, getString(R.string.action_next));
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishOobe();
            }
        });
        Ui.addFooterButton(page.footer, nextButton);

        setContentView(page.root);
        refreshFinishStatus();
    }

    // -------------------------------------------------------------- actions

    private void pickIme() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) {
                Toast.makeText(this, R.string.ime_picker_unavailable,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            imm.showInputMethodPicker();
            View root = findViewById(android.R.id.content);
            root.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshFinishStatus();
                }
            }, 800);
        } catch (Exception e) {
            Toast.makeText(this, R.string.ime_picker_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startRimeInit() {
        if (initializing) {
            return;
        }
        final RimeCore core = RimeCore.get();
        if (core.isReady()) {
            refreshSetupStatus();
            return;
        }
        initializing = true;
        if (initButton != null) {
            initButton.setEnabled(false);
        }
        if (nextButton != null) {
            nextButton.setEnabled(false);
        }
        if (progress != null) {
            progress.setIndeterminate(true);
            progress.setVisibility(View.VISIBLE);
        }
        setStatus(initStatus, getString(R.string.oobe_status_dict_running), Ui.WARNING);

        core.initializeAsync(getApplicationContext(), true, new RimeCore.Callback() {
            @Override
            public void onReady(final boolean success, final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initializing = false;
                        if (isFinishing() || step != 1) {
                            return;
                        }
                        if (progress != null) {
                            progress.setIndeterminate(false);
                        }
                        boolean ok = success || core.isReady();
                        if (ok) {
                            if (progress != null) {
                                progress.setProgress(100);
                            }
                            setStatus(initStatus,
                                    getString(R.string.oobe_status_dict_ready), Ui.SUCCESS);
                        } else {
                            if (progress != null) {
                                progress.setProgress(0);
                            }
                            String reason = (error == null || error.isEmpty())
                                    ? core.getError() : error;
                            setStatus(initStatus,
                                    getString(R.string.oobe_status_dict_failed, reason),
                                    Ui.ERROR);
                            Log.w(TAG, "OOBE rime init failed: " + reason);
                        }
                        if (initButton != null) {
                            initButton.setEnabled(true);
                        }
                        refreshSetupStatus();
                    }
                });
            }
        });
    }

    private void finishOobe() {
        if (!ImeUtil.isOurImeCurrent(this)) {
            setStatus(finishStatus, getString(R.string.oobe_status_ime_not_current,
                    ImeUtil.currentImeName(this)), Ui.WARNING);
            return;
        }
        prefs.edit().putBoolean(KEY_DONE, true).apply();
        goMain();
    }

    private void goMain() {
        startActivity(new Intent(this, SettingsActivity.class));
        finish();
    }

    // --------------------------------------------------------------- status

    private void refreshSetupStatus() {
        if (step != 1) {
            return;
        }
        boolean enabled = ImeUtil.isOurImeEnabled(this);
        setStatus(enableStatus,
                getString(enabled ? R.string.oobe_status_enabled
                        : R.string.oobe_status_not_enabled),
                enabled ? Ui.SUCCESS : Ui.WARNING);

        RimeCore core = RimeCore.get();
        if (!initializing) {
            if (core.isReady()) {
                setStatus(initStatus, getString(R.string.oobe_status_dict_ready), Ui.SUCCESS);
                if (progress != null) {
                    progress.setIndeterminate(false);
                    progress.setProgress(100);
                }
            } else {
                setStatus(initStatus, getString(R.string.oobe_status_dict_not_ready), Ui.WARNING);
                if (progress != null) {
                    progress.setIndeterminate(false);
                    progress.setProgress(0);
                }
            }
        }

        boolean ready = enabled && core.isReady() && !initializing;
        if (nextButton != null) {
            nextButton.setEnabled(ready);
        }
    }

    private void refreshFinishStatus() {
        if (step != 2) {
            return;
        }
        boolean current = ImeUtil.isOurImeCurrent(this);
        String name = ImeUtil.currentImeName(this);
        if (current) {
            setStatus(finishStatus,
                    getString(R.string.oobe_status_ime_current, name), Ui.SUCCESS);
        } else if (ImeUtil.isOurImeEnabled(this)) {
            setStatus(finishStatus,
                    getString(R.string.oobe_status_ime_not_current, name), Ui.WARNING);
        } else {
            setStatus(finishStatus,
                    getString(R.string.oobe_status_ime_disabled), Ui.ERROR);
        }
        if (nextButton != null) {
            nextButton.setEnabled(current);
        }
    }

    private void setStatus(TextView view, String message, int color) {
        if (view != null) {
            view.setText(message);
            view.setTextColor(color);
        }
    }

    // ----------------------------------------------------------------- views

    private void addSection(LinearLayout parent, String heading, String body) {
        TextView h = Ui.text(this, heading, 15, Ui.TEXT,
                Typeface.create("sans-serif", Typeface.BOLD));
        h.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        parent.addView(h, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView b = Ui.text(this, body, 13, Ui.TEXT_BODY,
                Typeface.create("sans-serif", Typeface.NORMAL));
        b.setLineSpacing(Ui.dp(this, 3), 1f);
        parent.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
