package com.questcn.ime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

/** 输入法状态查询与系统设置跳转的公共入口。 */
final class ImeUtil {
    private static final String TAG = "QuestIME";

    private ImeUtil() {
    }

    /**
     * Quest 默认会把 INPUT_METHOD_SETTINGS 解析到 VrShell 的 Quest 设置，
     * 首次引导必须强制指定 com.android.settings，走 Android 原生的
     * “可用虚拟键盘”页面。
     */
    static void openSettings(Activity activity) {
        try {
            Intent nativeSettings = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            nativeSettings.setPackage("com.android.settings");
            activity.startActivity(nativeSettings);
            return;
        } catch (Exception nativeFailed) {
            Log.w(TAG, "native Android settings unavailable", nativeFailed);
        }
        try {
            activity.startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (Exception first) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception second) {
                Toast.makeText(activity, R.string.settings_unavailable,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    static boolean isOurImeEnabled(Context context) {
        try {
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) {
                return false;
            }
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (context.getPackageName().equals(info.getPackageName())) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "isOurImeEnabled failed", t);
        }
        return false;
    }

    static boolean isOurImeCurrent(Context context) {
        try {
            String current = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            return current != null && current.startsWith(context.getPackageName() + "/");
        } catch (Throwable t) {
            Log.w(TAG, "isOurImeCurrent failed", t);
            return false;
        }
    }

    static String currentImeName(Context context) {
        try {
            String current = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            if (current == null) {
                return "系统默认键盘";
            }
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                    if (current.equals(info.getId())) {
                        CharSequence label = info.loadLabel(context.getPackageManager());
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
}
