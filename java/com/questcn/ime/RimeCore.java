package com.questcn.ime;

import android.content.Context;
import android.util.Log;

import com.kingzcheung.xime.rime.RimeCandidate;
import com.kingzcheung.xime.rime.RimeEngine;
import com.kingzcheung.xime.rime.RimeProcessResult;

import java.io.File;

/**
 * 进程内唯一的 Rime 核心。
 *
 * 负责复制词库、初始化 librime、首次部署、创建会话；IME 与测试页共享同一实例，
 * 避免在同一个进程里重复 startup / 多个会话互相覆盖。
 */
public final class RimeCore {
    private static final String TAG = "RimeCore";
    private static final String SCHEMA_ID = "rime_ice";
    private static final int PAGE_SIZE = 9;

    private static volatile RimeCore instance;

    private final RimeEngine engine = new RimeEngine();
    private final Object lock = new Object();

    private volatile boolean ready;
    private volatile boolean initializing;
    private volatile String error = "";
    private boolean nativeInitialized;
    private File rimeDir;

    private RimeCore() {
    }

    public static RimeCore get() {
        RimeCore local = instance;
        if (local == null) {
            synchronized (RimeCore.class) {
                local = instance;
                if (local == null) {
                    local = new RimeCore();
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean isReady() {
        return ready;
    }

    public String getError() {
        return error;
    }

    public File getRimeDir() {
        return rimeDir;
    }

    public interface Callback {
        void onReady(boolean success, String error);
    }

    public void initializeAsync(final Context context, final boolean allowDeploy,
                                final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = initializeBlocking(context, allowDeploy);
                if (callback != null) {
                    callback.onReady(ok, error);
                }
            }
        }, "rime-core-init").start();
    }

    public boolean initializeBlocking(Context context, boolean allowDeploy) {
        synchronized (lock) {
            if (ready) {
                return true;
            }
            if (initializing) {
                return false;
            }
            initializing = true;
            long started = System.currentTimeMillis();
            try {
                rimeDir = RimeAssetInstaller.prepare(context);
                if (!nativeInitialized) {
                    engine.setVerboseLogging(true);
                    engine.installSignalHandler(rimeDir.getAbsolutePath());
                    engine.initialize(rimeDir.getAbsolutePath());
                    nativeInitialized = true;
                }
                boolean hasBuild = hasBuild(rimeDir);
                if (!hasBuild) {
                    if (!allowDeploy) {
                        error = "rime build missing";
                        return false;
                    }
                    Log.i(TAG, "deploying rime-ice (first run)...");
                    long t = System.currentTimeMillis();
                    boolean deployed = engine.deploy();
                    Log.i(TAG, "deploy=" + deployed + " in " + (System.currentTimeMillis() - t) + "ms");
                    if (!deployed) {
                        error = "rime deploy failed";
                        return false;
                    }
                }
                if (!engine.createSession()) {
                    error = "rime createSession failed";
                    return false;
                }
                engine.setPageSize(SCHEMA_ID, PAGE_SIZE);
                ready = true;
                error = "";
                Log.i(TAG, "rime ready schema=" + engine.getCurrentSchema()
                        + " in " + (System.currentTimeMillis() - started) + "ms");
                return true;
            } catch (Throwable t) {
                error = t.toString();
                Log.e(TAG, "rime initialize failed", t);
                return false;
            } finally {
                initializing = false;
            }
        }
    }

    private static boolean hasBuild(File dir) {
        File build = new File(dir, "build");
        File[] files = build.listFiles();
        return files != null && files.length > 0;
    }

    public RimeProcessResult processKey(int keycode) {
        if (!ready) {
            return null;
        }
        synchronized (lock) {
            return engine.processKeyAndGetResult(keycode, 0);
        }
    }

    public RimeProcessResult processBackspace() {
        return processKey(0xff08);
    }

    public boolean selectCandidate(int index) {
        if (!ready) {
            return false;
        }
        synchronized (lock) {
            return engine.selectCandidate(index);
        }
    }

    public String commit() {
        if (!ready) {
            return "";
        }
        synchronized (lock) {
            String text = engine.commit();
            return text == null ? "" : text;
        }
    }

    public RimeProcessResult getProcessResult(boolean processed) {
        if (!ready) {
            return null;
        }
        synchronized (lock) {
            return engine.getProcessResult(processed);
        }
    }

    public void clearComposition() {
        if (!ready) {
            return;
        }
        synchronized (lock) {
            engine.clearComposition();
        }
    }

    public boolean setInput(String input) {
        if (!ready) {
            return false;
        }
        synchronized (lock) {
            return engine.setInput(input);
        }
    }

    public String getInput() {
        if (!ready) {
            return "";
        }
        synchronized (lock) {
            String input = engine.getInput();
            return input == null ? "" : input;
        }
    }

    public String getCurrentSchema() {
        if (!ready) {
            return "";
        }
        synchronized (lock) {
            return engine.getCurrentSchema();
        }
    }

    public String[] getAvailableSchemas() {
        if (!ready) {
            return new String[0];
        }
        synchronized (lock) {
            String[] schemas = engine.getAvailableSchemas();
            return schemas == null ? new String[0] : schemas;
        }
    }

    public RimeCandidate[] currentCandidates() {
        if (!ready) {
            return new RimeCandidate[0];
        }
        synchronized (lock) {
            RimeProcessResult result = engine.getProcessResult(true);
            return result == null || result.candidates == null
                    ? new RimeCandidate[0] : result.candidates;
        }
    }
}
