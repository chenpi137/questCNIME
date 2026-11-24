package com.kingzcheung.xime.rime;

/**
 * 一次按键后的完整引擎快照。构造函数签名必须与 librime_jni.so 中缓存的
 * (boolean, String, String, String, RimeCandidate[], boolean, boolean, boolean, String, String) 一致。
 */
public class RimeProcessResult {
    public final boolean processed;
    public final String committedText;
    public final String inputText;
    public final String preeditText;
    public final RimeCandidate[] candidates;
    public final boolean isAsciiMode;
    public final boolean hasNextPage;
    public final boolean hasPrevPage;
    public final String t9PanelState;
    public final String t9SyllableOptions;

    public RimeProcessResult(boolean processed, String committedText, String inputText,
                             String preeditText, RimeCandidate[] candidates,
                             boolean isAsciiMode, boolean hasNextPage, boolean hasPrevPage,
                             String t9PanelState, String t9SyllableOptions) {
        this.processed = processed;
        this.committedText = committedText;
        this.inputText = inputText;
        this.preeditText = preeditText;
        this.candidates = candidates;
        this.isAsciiMode = isAsciiMode;
        this.hasNextPage = hasNextPage;
        this.hasPrevPage = hasPrevPage;
        this.t9PanelState = t9PanelState;
        this.t9SyllableOptions = t9SyllableOptions;
    }
}
