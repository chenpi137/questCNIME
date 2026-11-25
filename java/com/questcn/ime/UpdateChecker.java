package com.questcn.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 检查 GitHub 最新 Release 的版本号。
 *
 * 只请求 api.github.com 的 releases/latest 元数据（tag_name / html_url /
 * published_at），用来和本机 versionName 比较；不上传任何输入内容或设备信息。
 * 结果缓存 30 分钟，点击“检查更新”时强制刷新。
 */
final class UpdateChecker {
    private static final String TAG = "QuestIME";
    private static final String API_URL =
            "https://api.github.com/repos/chenpi137/questCNIME/releases/latest";
    static final String RELEASES_URL =
            "https://github.com/chenpi137/questCNIME/releases/latest";
    private static final String PREFS = "questcn_ime";
    private static final String KEY_CHECKED_AT = "update_checked_at";
    private static final String KEY_TAG = "update_tag";
    private static final String KEY_URL = "update_url";
    private static final String KEY_PUBLISHED = "update_published";
    private static final long CACHE_MS = 30L * 60L * 1000L;

    private UpdateChecker() {
    }

    interface Callback {
        void onResult(Result result);
    }

    static final class Result {
        boolean ok;
        boolean fromCache;
        String currentVersion = "";
        String tag = "";
        String url = RELEASES_URL;
        String publishedAt = "";
        boolean hasUpdate;
        String error = "";
    }

    static String currentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    static void check(final Context context, final boolean force, final Callback callback) {
        final Context app = context.getApplicationContext();
        Result cached = readCache(app, force);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Result result = fetch(app);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(result);
                    }
                });
            }
        }, "update-check").start();
    }

    private static Result fetch(Context context) {
        Result result = new Result();
        result.currentVersion = currentVersion(context);
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent",
                    "QuestCNIME/" + (result.currentVersion.isEmpty()
                            ? "unknown" : result.currentVersion));
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            String body = readAll(connection.getInputStream());
            JSONObject json = new JSONObject(body);
            result.tag = json.optString("tag_name", "").trim();
            result.url = json.optString("html_url", RELEASES_URL);
            if (result.url == null || result.url.isEmpty()) {
                result.url = RELEASES_URL;
            }
            result.publishedAt = json.optString("published_at", "");
            if (result.tag.isEmpty()) {
                throw new IOException("missing tag_name");
            }
            result.hasUpdate = compareVersions(result.currentVersion, result.tag) < 0;
            result.ok = true;
            writeCache(context, result);
        } catch (Exception e) {
            result.ok = false;
            result.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Log.w(TAG, "update check failed: " + result.error, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                out.append(buffer, 0, count);
            }
        } finally {
            reader.close();
        }
        return out.toString();
    }

    private static Result readCache(Context context, boolean force) {
        if (force) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L);
        if (checkedAt <= 0 || System.currentTimeMillis() - checkedAt > CACHE_MS) {
            return null;
        }
        String tag = prefs.getString(KEY_TAG, "");
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        Result result = new Result();
        result.currentVersion = currentVersion(context);
        result.tag = tag;
        result.url = prefs.getString(KEY_URL, RELEASES_URL);
        result.publishedAt = prefs.getString(KEY_PUBLISHED, "");
        result.hasUpdate = compareVersions(result.currentVersion, tag) < 0;
        result.ok = true;
        result.fromCache = true;
        return result;
    }

    private static void writeCache(Context context, Result result) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                .putString(KEY_TAG, result.tag)
                .putString(KEY_URL, result.url)
                .putString(KEY_PUBLISHED, result.publishedAt)
                .apply();
    }

    /** 只比较数字段，忽略前面的 v 和 -beta/+build 后缀。 */
    static int compareVersions(String current, String tag) {
        int[] a = parseVersion(current);
        int[] b = parseVersion(tag);
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return left < right ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] parseVersion(String value) {
        if (value == null) {
            return new int[0];
        }
        String text = value.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        int cut = text.indexOf('-');
        if (cut < 0) {
            cut = text.indexOf('+');
        }
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        if (text.isEmpty()) {
            return new int[0];
        }
        String[] parts = text.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int end = 0;
            while (end < parts[i].length() && Character.isDigit(parts[i].charAt(end))) {
                end++;
            }
            if (end == 0) {
                numbers[i] = 0;
            } else {
                try {
                    numbers[i] = Integer.parseInt(parts[i].substring(0, end));
                } catch (NumberFormatException e) {
                    numbers[i] = 0;
                }
            }
        }
        return numbers;
    }

    static String formatDate(String publishedAt) {
        if (publishedAt == null || publishedAt.length() < 10) {
            return "";
        }
        return publishedAt.substring(0, 10).replace('-', '/');
    }
}
