package com.questcn.ime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 高级设置：RIME 诊断、打开系统输入法设置。 */
public class AdvancedSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);

        Ui.Page page = Ui.page(this, true);
        page.body.addView(Ui.title(this, getString(R.string.settings_advanced), false),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView caption = Ui.text(this, getString(R.string.settings_advanced_caption),
                10, Ui.TEXT_DIM, android.graphics.Typeface.create("sans-serif",
                        android.graphics.Typeface.NORMAL));
        caption.setPadding(0, 0, 0, Ui.dp(this, 8));
        page.body.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View rimeRow = Ui.settingsRow(this, 0,
                getString(R.string.advanced_rime_title),
                getString(R.string.advanced_rime_subtitle),
                true, false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(AdvancedSettingsActivity.this,
                                RimeProbeActivity.class));
                    }
                }).view;

        View imeRow = Ui.settingsRow(this, 0,
                getString(R.string.advanced_ime_title),
                getString(R.string.advanced_ime_subtitle),
                false, true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ImeUtil.openSettings(AdvancedSettingsActivity.this);
                    }
                }).view;

        page.body.addView(Ui.rowGroup(this, new View[]{rimeRow, imeRow}),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        Ui.PillButton back = Ui.backButton(this, getString(R.string.action_back));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        Ui.addFooterButton(page.footer, back);

        setContentView(page.root);
    }
}
