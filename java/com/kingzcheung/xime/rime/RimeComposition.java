package com.kingzcheung.xime.rime;

/**
 * 当前组合区快照。构造函数签名必须与 librime_jni.so 中缓存的
 * (String, String, String, RimeCandidate[], boolean, boolean, boolean) 一致。
 */
public class RimeComposition {
    public final String input;
    public final String preedit;
    public final String committedText;
    public final RimeCandidate[] candidates;
    public final boolean hasNextPage;
    public final boolean hasPrevPage;
    public final boolean isAsciiMode;

    public RimeComposition(String input, String preedit, String committedText,
                           RimeCandidate[] candidates, boolean hasNextPage,
                           boolean hasPrevPage, boolean isAsciiMode) {
        this.input = input;
        this.preedit = preedit;
        this.committedText = committedText;
        this.candidates = candidates;
        this.hasNextPage = hasNextPage;
        this.hasPrevPage = hasPrevPage;
        this.isAsciiMode = isAsciiMode;
    }
}
