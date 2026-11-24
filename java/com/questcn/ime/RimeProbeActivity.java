package com.questcn.ime;

import android.app.Activity;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kingzcheung.xime.rime.RimeCandidate;
import com.kingzcheung.xime.rime.RimeProcessResult;

import java.io.File;
import java.util.Arrays;

/**
 * 一次性验证 CyIME/librime 核心能否在本项目 APK 内加载、部署、出候选。
 * 不是最终 UI；验证通过后由 QuestImeService 接入。
 */
public class RimeProbeActivity extends Activity {
    private static final String TAG = "RimeProbe";
    private TextView logView;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        logView = new TextView(this);
        logView.setTextSize(13);
        logView.setTextColor(0xFFE0E0E0);
        logView.setGravity(Gravity.START);
        logView.setPadding(24, 24, 24, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF1C1C1E);
        scroll.addView(logView);
        setContentView(scroll);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuestCNIME:RimeProbe");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(20 * 60 * 1000L);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                runProbe();
            }
        }, "rime-probe").start();
    }

    private void runProbe() {
        long started = System.currentTimeMillis();
        try {
            RimeCore core = RimeCore.get();
            log("initializing rime (first run can take ~30s)...");
            long t = System.currentTimeMillis();
            boolean ok = core.initializeBlocking(this, true);
            log("initialize=" + ok + " in " + (System.currentTimeMillis() - t)
                    + "ms error=" + core.getError());
            if (!ok) {
                log("PROBE_FAILED init");
                return;
            }
            log("assets: " + core.getRimeDir());
            log("schema=" + core.getCurrentSchema());
            String[] schemas = core.getAvailableSchemas();
            log("schemas=" + (schemas == null ? "null" : Arrays.toString(schemas)));

            probeWord(core, "nihao");
            probeWord(core, "zhongguo");
            probeWord(core, "woaini");
            probeSelect(core, "nihao", 1);
            probeSelect(core, "guggag", 1);

            log("PROBE_DONE " + (System.currentTimeMillis() - started) + "ms");
        } catch (Throwable t) {
            Log.e(TAG, "probe failed", t);
            log("ERROR: " + Log.getStackTraceString(t));
        }
    }

    private void probeWord(RimeCore core, String code) {
        core.clearComposition();
        core.setInput("");
        log("--- typing " + code + " ---");
        RimeProcessResult last = null;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            last = core.processKey(c);
            if (last == null) {
                log("  key " + c + " -> null");
                continue;
            }
            if (last.committedText != null && !last.committedText.isEmpty()) {
                log("  committed=" + last.committedText);
            }
        }
        if (last == null) {
            return;
        }
        log("input=" + last.inputText + " preedit=" + last.preeditText
                + " ascii=" + last.isAsciiMode + " next=" + last.hasNextPage);
        RimeCandidate[] candidates = last.candidates;
        if (candidates == null || candidates.length == 0) {
            log("  no candidates");
            return;
        }
        StringBuilder sb = new StringBuilder("  candidates: ");
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(i).append('=').append(candidates[i].text);
            if (candidates[i].comment != null && !candidates[i].comment.isEmpty()) {
                sb.append('[').append(candidates[i].comment).append(']');
            }
        }
        log(sb.toString());

        boolean selected = core.selectCandidate(0);
        String committed = core.commit();
        log("  select=" + selected + " commit=" + committed);
        RimeProcessResult after = core.getProcessResult(true);
        if (after != null) {
            log("  after: input=" + after.inputText + " preedit=" + after.preeditText
                    + " candidates=" + (after.candidates == null ? 0 : after.candidates.length));
        }
    }

    private void probeSelect(RimeCore core, String code, int index) {
        core.clearComposition();
        core.setInput("");
        RimeProcessResult last = null;
        for (int i = 0; i < code.length(); i++) {
            last = core.processKey(code.charAt(i));
        }
        log("--- select " + index + " for " + code + " ---");
        if (last == null || last.candidates == null) {
            log("  no candidates");
            return;
        }
        StringBuilder sb = new StringBuilder("  candidates: ");
        for (int i = 0; i < last.candidates.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(i).append('=').append(last.candidates[i].text)
                    .append('[').append(last.candidates[i].comment).append(']');
        }
        log(sb.toString());
        boolean selected = core.selectCandidate(index);
        String committed = core.commit();
        RimeProcessResult after = core.getProcessResult(true);
        log("  selected=" + selected + " commit='" + committed + "'"
                + " afterCommit='" + (after == null ? "" : after.committedText) + "'"
                + " afterInput='" + (after == null ? "" : after.inputText) + "'");
        core.clearComposition();
    }

    private void log(final String message) {
        Log.i(TAG, message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logView.append(message + "\n");
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
