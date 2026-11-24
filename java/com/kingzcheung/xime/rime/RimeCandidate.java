package com.kingzcheung.xime.rime;

/**
 * JNI 返回的候选词。构造函数签名必须与 librime_jni.so 中缓存的
 * (String, String, boolean, String) 完全一致。
 */
public class RimeCandidate {
    public final String text;
    public final String comment;
    public final boolean learned;
    public final String correction;

    public RimeCandidate(String text, String comment, boolean learned, String correction) {
        this.text = text;
        this.comment = comment;
        this.learned = learned;
        this.correction = correction;
    }

    @Override
    public String toString() {
        return text + (comment == null || comment.isEmpty() ? "" : "[" + comment + "]");
    }
}
