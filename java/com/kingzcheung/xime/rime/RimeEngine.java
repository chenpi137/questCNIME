package com.kingzcheung.xime.rime;

import android.util.Log;

/**
 * 最小 librime JNI 桥。
 *
 * 该类的包名/类名/native 方法名必须与 CyIME 的 librime_jni.so 中导出的
 * Java_com_kingzcheung_xime_rime_RimeEngine_native... 符号一致。
 * 这里只声明本项目实际使用的方法，不包含 T9/插件等扩展。
 */
public class RimeEngine {
    private static final String TAG = "RimeEngine";

    static {
        // onnxruntime 是 librime_jni.so 的 DT_NEEDED；先加载它和可选模型库。
        try {
            System.loadLibrary("c++_shared");
        } catch (Throwable t) {
            Log.w(TAG, "libc++_shared not loaded: " + t);
        }
        try {
            System.loadLibrary("onnxruntime");
        } catch (Throwable t) {
            Log.w(TAG, "libonnxruntime not loaded: " + t);
        }
        try {
            System.loadLibrary("cylm");
        } catch (Throwable t) {
            Log.w(TAG, "libcylm not loaded (T9 model optional): " + t);
        }
        System.loadLibrary("rime_jni");
        // JNI 在第一次调用时会 FindClass 这些结果类，提前加载避免 native 线程的类加载问题。
        try {
            Class.forName("com.kingzcheung.xime.rime.RimeCandidate");
            Class.forName("com.kingzcheung.xime.rime.RimeProcessResult");
            Class.forName("com.kingzcheung.xime.rime.RimeComposition");
        } catch (Throwable t) {
            Log.w(TAG, "result class preload failed: " + t);
        }
    }

    public RimeEngine() {
    }

    // ---- native declarations (signatures mirror CyIME RimeEngine.kt) ----

    private native void nativeInitialize(String userDataDir, String sharedDataDir);
    private native void nativeInstallSignalHandler(String userDataDir);
    private native void nativeSetVerboseLogging(boolean enabled);
    private native boolean nativeCreateSession();
    private native boolean nativeHasSession();
    private native boolean nativeIsMaintaining();
    private native String nativeGetCurrentSchema();
    private native boolean nativeProcessKey(int keycode, int mask);
    private native RimeProcessResult nativeProcessKeyAndGetResult(int keycode, int mask);
    private native RimeProcessResult nativeGetProcessResult(boolean processed);
    private native String[] nativeGetCandidates();
    private native String[][] nativeGetCandidatesWithComments();
    private native String[][] nativeGetAllCandidates(int maxCount);
    private native String nativeGetInput();
    private native RimeComposition nativeGetComposition();
    private native boolean nativeSelectCandidate(int index);
    private native boolean nativeSelectCandidateByGlobalIndex(int index);
    private native boolean nativePageDown();
    private native boolean nativePageUp();
    private native boolean nativeHasNextPage();
    private native boolean nativeHasPrevPage();
    private native String nativeCommit();
    private native void nativeClearComposition();
    private native boolean nativeSetInput(String input);
    private native boolean nativeToggleAsciiMode();
    private native boolean nativeIsAsciiMode();
    private native void nativeSetOption(String option, boolean value);
    private native boolean nativeGetOption(String option);
    private native boolean nativeSwitchSchema(String schemaId);
    private native String[] nativeGetAvailableSchemas();
    private native String[] nativeGetSchemaList(String schemaId, String key);
    private native String nativeGetSchemaString(String schemaId, String key);
    private native boolean nativeDeploy();
    private native boolean nativeStartMaintenance(boolean full);
    private native void nativeJoinMaintenance();
    private native void nativeReleaseSessionForMaintenance();
    private native boolean nativeReloadSessionAfterMaintenance();
    private native void nativeUpdateLastBuildTime();
    private native void nativeSetPageSize(String schemaId, int pageSize);
    private native void nativeDestroy();

    // ---- public API used by QuestCNIME ----

    public void initialize(String dataDir) {
        nativeInitialize(dataDir, dataDir);
    }

    public void installSignalHandler(String dataDir) {
        nativeInstallSignalHandler(dataDir);
    }

    public void setVerboseLogging(boolean enabled) {
        nativeSetVerboseLogging(enabled);
    }

    public boolean createSession() {
        return nativeCreateSession();
    }

    public boolean hasSession() {
        return nativeHasSession();
    }

    public boolean isMaintaining() {
        return nativeIsMaintaining();
    }

    public String getCurrentSchema() {
        return nativeGetCurrentSchema();
    }

    public boolean processKey(int keycode, int mask) {
        return nativeProcessKey(keycode, mask);
    }

    public RimeProcessResult processKeyAndGetResult(int keycode, int mask) {
        return nativeProcessKeyAndGetResult(keycode, mask);
    }

    public RimeProcessResult getProcessResult(boolean processed) {
        return nativeGetProcessResult(processed);
    }

    public RimeComposition getComposition() {
        return nativeGetComposition();
    }

    public String getInput() {
        return nativeGetInput();
    }

    public boolean selectCandidate(int index) {
        return nativeSelectCandidate(index);
    }

    public boolean selectCandidateByGlobalIndex(int index) {
        return nativeSelectCandidateByGlobalIndex(index);
    }

    public boolean pageDown() {
        return nativePageDown();
    }

    public boolean pageUp() {
        return nativePageUp();
    }

    public boolean hasNextPage() {
        return nativeHasNextPage();
    }

    public boolean hasPrevPage() {
        return nativeHasPrevPage();
    }

    public String commit() {
        return nativeCommit();
    }

    public void clearComposition() {
        nativeClearComposition();
    }

    public boolean setInput(String input) {
        return nativeSetInput(input);
    }

    public boolean toggleAsciiMode() {
        return nativeToggleAsciiMode();
    }

    public boolean isAsciiMode() {
        return nativeIsAsciiMode();
    }

    public void setOption(String option, boolean value) {
        nativeSetOption(option, value);
    }

    public boolean getOption(String option) {
        return nativeGetOption(option);
    }

    public boolean switchSchema(String schemaId) {
        return nativeSwitchSchema(schemaId);
    }

    public String[] getAvailableSchemas() {
        return nativeGetAvailableSchemas();
    }

    public String[] getSchemaList(String schemaId, String key) {
        return nativeGetSchemaList(schemaId, key);
    }

    public String getSchemaString(String schemaId, String key) {
        return nativeGetSchemaString(schemaId, key);
    }

    public boolean deploy() {
        return nativeDeploy();
    }

    public boolean startMaintenance(boolean full) {
        return nativeStartMaintenance(full);
    }

    public void joinMaintenance() {
        nativeJoinMaintenance();
    }

    public void releaseSessionForMaintenance() {
        nativeReleaseSessionForMaintenance();
    }

    public boolean reloadSessionAfterMaintenance() {
        return nativeReloadSessionAfterMaintenance();
    }

    public void updateLastBuildTime() {
        nativeUpdateLastBuildTime();
    }

    public void setPageSize(String schemaId, int pageSize) {
        nativeSetPageSize(schemaId, pageSize);
    }

    public void destroy() {
        nativeDestroy();
    }
}
