package com.questcn.ime;

import android.graphics.RectF;

/** 一个按键，字段与 VrShell 键盘 XML 的属性一一对应。 */
public class Key {
    public String label;
    public String output;
    public String shiftLabel;
    public String shiftOutput;
    public String altLabel;

    public int[] codes = new int[0];
    public boolean repeatable;

    public int iconRes;
    public int shiftIconRes;
    public int capsIconRes;
    public int keyboardRes;

    /** 像素宽度 */
    public float width;
    /** 该键之前额外增加的横向间距（像素） */
    public float gapBefore;
    /** 0 表示使用键盘默认值 */
    public float textSize;
    /** 0 表示使用键盘默认值 */
    public int textColor;

    public String bgName;
    public final RectF rect = new RectF();

    public boolean pressed;
    public boolean hovered;

    public boolean hasCode(int code) {
        for (int c : codes) {
            if (c == code) {
                return true;
            }
        }
        return false;
    }

    public boolean isLetter() {
        return output != null && output.length() == 1
                && output.charAt(0) >= 'a' && output.charAt(0) <= 'z';
    }
}
