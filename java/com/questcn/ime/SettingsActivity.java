package com.questcn.ime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 启用/切换本输入法的简单说明页。 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getSharedPreferences("questcn_ime", MODE_PRIVATE)
                .getBoolean("oobe_done", false)) {
            startActivity(new Intent(this, OobeActivity.class));
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(24);
        title.setTextColor(0xFFF2F2F2);
        box.addView(title);

        TextView body = new TextView(this);
        body.setText(R.string.settings_body);
        body.setTextSize(15);
        body.setTextColor(0xFFCCCCCC);
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(0, dp(16), 0, dp(16));
        box.addView(body);

        Button open = new Button(this);
        open.setText(R.string.open_ime_settings);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this,
                            R.string.settings_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(open, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        Button picker = new Button(this);
        picker.setText(R.string.switch_ime);
        picker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showInputMethodPicker();
                    }
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this,
                            R.string.ime_picker_unavailable, Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        plp.topMargin = dp(12);
        box.addView(picker, plp);

        Button rime = new Button(this);
        rime.setText(R.string.rime_setup);
        rime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, RimeProbeActivity.class));
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        rlp.topMargin = dp(12);
        box.addView(rime, rlp);

        TextView adb = new TextView(this);
        adb.setText("ADB 启用命令：\n"
                + "adb shell ime enable com.questcn.ime/.QuestImeService\n"
                + "adb shell ime set com.questcn.ime/.QuestImeService");
        adb.setTextSize(13);
        adb.setTextColor(0xFF99AACC);
        adb.setGravity(Gravity.START);
        adb.setPadding(0, dp(24), 0, 0);
        box.addView(adb);

        EditText test = new EditText(this);
        test.setHint("在此测试输入（应弹出 VrShell 布局键盘）");
        test.setHintTextColor(0xFF888888);
        test.setTextSize(16);
        test.setTextColor(0xFFF2F2F2);
        test.setSingleLine(true);
        test.setBackgroundColor(0xFF2B2B2B);
        test.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        tlp.topMargin = dp(24);
        box.addView(test, tlp);

        test.requestFocus();
        test.post(new Runnable() {
            @Override
            public void run() {
                try {
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(test, InputMethodManager.SHOW_FORCED);
                    }
                } catch (Exception ignored) {
                }
            }
        });

        setContentView(scroll);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
