package com.questcn.ime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 设置主界面：高级设置 / 关于 / 检查更新。
 *
 * 检查更新读取 GitHub 仓库最新 Release 的 tag_name，与本机 versionName 比较；
 * 只请求版本元数据，见 UpdateChecker。
 */
public class SettingsActivity extends Activity {
    private static final String PREFS = "questcn_ime";
    private static final String KEY_DONE = "oobe_done";

    private Ui.Row updateRow;
    private UpdateChecker.Result lastResult;
    private boolean checking;
    private boolean manualCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_DONE, false)) {
            startActivity(new Intent(this, OobeActivity.class));
            finish();
            return;
        }
        Ui.applyWindow(this);

        Ui.Page page = Ui.page(this, true);
        page.body.addView(Ui.title(this, getString(R.string.settings_screen_title), false),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        View advancedRow = Ui.settingsRow(this,
                R.drawable.oc_icon_settings_filled_24,
                getString(R.string.settings_advanced), null,
                true, false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this,
                                AdvancedSettingsActivity.class));
                    }
                }).view;

        View aboutRow = Ui.settingsRow(this,
                R.drawable.ic_info_circle_24,
                getString(R.string.settings_about), null,
                false, false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(SettingsActivity.this,
                                AboutActivity.class));
                    }
                }).view;

        updateRow = Ui.settingsRow(this,
                R.drawable.ic_update_circle_24,
                getString(R.string.settings_update), versionLine(getString(R.string.update_checking)),
                false, true,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onUpdateClicked();
                    }
                });

        LinearLayout group = Ui.rowGroup(this,
                new View[]{advancedRow, aboutRow, updateRow.view});
        page.body.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(page.root);
        checkUpdates(false);
    }

    // --------------------------------------------------------------- update

    private void onUpdateClicked() {
        if (checking) {
            return;
        }
        if (lastResult != null && lastResult.ok && lastResult.hasUpdate) {
            showUpdateDialog(lastResult);
            return;
        }
        manualCheck = true;
        checkUpdates(true);
    }

    private void checkUpdates(boolean force) {
        if (checking) {
            return;
        }
        checking = true;
        if (updateRow != null && updateRow.subtitle != null) {
            updateRow.subtitle.setText(versionLine(getString(R.string.update_checking)));
        }
        UpdateChecker.check(this, force, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Result result) {
                checking = false;
                if (isFinishing()) {
                    return;
                }
                lastResult = result;
                if (updateRow == null || updateRow.subtitle == null) {
                    return;
                }
                String version = result.currentVersion;
                if (version == null || version.isEmpty()) {
                    version = "?";
                }
                if (!result.ok) {
                    updateRow.subtitle.setText(getString(R.string.current_version,
                            version) + " · " + getString(R.string.update_failed));
                    manualCheck = false;
                    return;
                }
                if (result.hasUpdate) {
                    updateRow.subtitle.setText(getString(R.string.current_version,
                            version) + " · " + getString(R.string.update_found, result.tag));
                    if (manualCheck) {
                        showUpdateDialog(result);
                    }
                } else {
                    updateRow.subtitle.setText(getString(R.string.current_version,
                            version) + " · " + getString(R.string.update_latest));
                    if (manualCheck) {
                        Toast.makeText(SettingsActivity.this, R.string.update_latest,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                manualCheck = false;
            }
        });
    }

    private void showUpdateDialog(final UpdateChecker.Result result) {
        manualCheck = false;
        String version = result.currentVersion == null || result.currentVersion.isEmpty()
                ? "?" : result.currentVersion;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_dialog_title, result.tag))
                .setMessage(getString(R.string.update_dialog_message, version, result.tag))
                .setPositiveButton(R.string.update_dialog_open,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openReleasePage(result.url);
                            }
                        })
                .setNegativeButton(R.string.update_dialog_cancel, null)
                .show();
    }

    private void openReleasePage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.update_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private String versionLine(String status) {
        String version = UpdateChecker.currentVersion(this);
        if (version == null || version.isEmpty()) {
            version = "?";
        }
        return getString(R.string.current_version, version) + " · " + status;
    }
}
