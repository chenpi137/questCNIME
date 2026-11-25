package com.questcn.ime;

import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 关于：版本、开源仓库、第三方许可证与免责声明。 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);

        Ui.Page page = Ui.page(this, false);
        page.body.addView(Ui.title(this, getString(R.string.about_title), false),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = Ui.card(this);
        page.body.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView scroll = Ui.cardScroll(this, card);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView intro = Ui.text(this,
                getString(R.string.about_intro, UpdateChecker.currentVersion(this)),
                13, Ui.TEXT_BODY, android.graphics.Typeface.create("sans-serif",
                        android.graphics.Typeface.NORMAL));
        intro.setLineSpacing(Ui.dp(this, 3), 1f);
        content.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(heading(getString(R.string.about_repo)));
        SpannableStringBuilder repo = new SpannableStringBuilder();
        Ui.appendLink(repo, "github.com/chenpi137/questCNIME",
                "https://github.com/chenpi137/questCNIME");
        content.addView(Ui.linkText(this, repo), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(heading(getString(R.string.about_license)));
        TextView acknowledgements = Ui.text(this, getString(R.string.oobe_acknowledgements),
                12, Ui.TEXT_BODY, android.graphics.Typeface.create("sans-serif",
                        android.graphics.Typeface.NORMAL));
        acknowledgements.setLineSpacing(Ui.dp(this, 2), 1f);
        content.addView(acknowledgements, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(heading(getString(R.string.about_disclaimer)));
        TextView disclaimer = Ui.text(this, getString(R.string.oobe_disclaimer),
                12, Ui.TEXT_BODY, android.graphics.Typeface.create("sans-serif",
                        android.graphics.Typeface.NORMAL));
        disclaimer.setLineSpacing(Ui.dp(this, 2), 1f);
        content.addView(disclaimer, new LinearLayout.LayoutParams(
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
        scroll.scrollTo(0, 0);
    }

    private TextView heading(String value) {
        TextView view = Ui.heading(this, value);
        return view;
    }
}
