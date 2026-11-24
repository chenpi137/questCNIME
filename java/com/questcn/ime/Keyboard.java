package com.questcn.ime;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.DisplayMetrics;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 VrShell 风格的键盘 XML。
 *
 * 与原生一致的关键参数：48dp 键宽/键高、8dp 横纵间距、8dp 内边距，
 * 支持 spanDefaultKeys_N 跨列宽度与 85dp 回车键。
 */
public class Keyboard {
    private static final String TAG = "QuestKeyboard";

    public final List<Row> rows = new ArrayList<>();

    public float keyWidth;
    public float keyHeight;
    public float gapX;
    public float gapY;
    /** 水平内边距（保持原版 8dp，宽度仍为 624dp） */
    public float padding;
    /** 垂直内边距（为加高状态栏压缩到 2dp） */
    public float paddingV;

    public float textSize;
    public float subTextSize;
    public int textColor;
    public String defaultBgName;

    public int width;
    public int height;

    public static class Row {
        public final List<Key> keys = new ArrayList<>();
        public float width;
    }

    public static Keyboard load(Context context, int resId) {
        Resources res = context.getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Keyboard kb = new Keyboard();
        XmlResourceParser parser = null;
        try {
            parser = res.getXml(resId);
            int event = parser.getEventType();
            while (event != XmlPullParser.START_TAG) {
                event = parser.next();
            }
            if (!"Keyboard".equals(parser.getName())) {
                throw new IllegalStateException("root tag is not Keyboard: " + parser.getName());
            }

            kb.keyWidth = dim(attr(parser, "keyWidth"), dp(48f, dm), dm);
            kb.keyHeight = dim(attr(parser, "keyHeight"), dp(48f, dm), dm);
            kb.gapX = dim(attr(parser, "horizontalGap"), dp(8f, dm), dm);
            kb.gapY = dim(attr(parser, "verticalGap"), dp(8f, dm), dm);
            kb.padding = dp(8f, dm);
            kb.paddingV = dp(0f, dm);
            kb.textSize = dim(attr(parser, "keyTextSize"), sp(18f, dm), dm, true);
            kb.subTextSize = dim(attr(parser, "keySubTextSize"), sp(10f, dm), dm, true);
            kb.textColor = color(res, attrRes(parser, "keyTextColor"), 0xFFF2F2F2);
            kb.defaultBgName = entryName(res, attrRes(parser, "keyBackground"));

            Row row = null;
            while (true) {
                event = parser.next();
                if (event == XmlPullParser.END_DOCUMENT) {
                    break;
                }
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("Row".equals(name)) {
                        row = new Row();
                        kb.rows.add(row);
                    } else if ("Key".equals(name) && row != null) {
                        Key key = parseKey(parser, kb, res, dm);
                        row.keys.add(key);
                    }
                } else if (event == XmlPullParser.END_TAG && "Row".equals(parser.getName())) {
                    row = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to load keyboard xml 0x" + Integer.toHexString(resId), e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }

        kb.layout(dm);
        String first = "";
        if (!kb.rows.isEmpty() && !kb.rows.get(0).keys.isEmpty()) {
            Key k = kb.rows.get(0).keys.get(0);
            first = k.label + "/" + k.output + "/w=" + Math.round(k.width);
        }
        Log.i(TAG, "loaded 0x" + Integer.toHexString(resId) + ": "
                + kb.width + "x" + kb.height + "px rows=" + kb.rows.size()
                + " first=" + first);
        return kb;
    }

    private static Key parseKey(XmlResourceParser p, Keyboard kb, Resources res, DisplayMetrics dm) {
        Key k = new Key();
        k.label = unescape(attr(p, "keyLabel"));
        k.output = unescape(attr(p, "keyOutputText"));
        k.shiftLabel = unescape(attr(p, "shiftKeyLabel"));
        k.shiftOutput = unescape(attr(p, "shiftKeyOutputText"));
        k.altLabel = unescape(attr(p, "keyAltLabel"));
        k.codes = parseCodes(attr(p, "codes"));
        k.repeatable = "true".equalsIgnoreCase(attr(p, "isRepeatable"));

        k.iconRes = attrRes(p, "keyIcon");
        k.shiftIconRes = attrRes(p, "shiftKeyIcon");
        k.capsIconRes = attrRes(p, "capsLockIcon");
        k.keyboardRes = attrRes(p, "keyboard");

        String w = attr(p, "keyWidth");
        k.width = w == null ? kb.keyWidth : resolveWidth(w, kb.keyWidth, kb.gapX, dm);

        String gap = attr(p, "horizontalGap");
        k.gapBefore = gap == null ? 0f : dim(gap, 0f, dm);

        String textSize = attr(p, "textSize");
        k.textSize = textSize == null ? 0f : dim(textSize, 0f, dm);

        k.textColor = color(res, attrRes(p, "textColor"), 0);

        String bg = attr(p, "keyBackground");
        int bgId = attrRes(p, "keyBackground");
        k.bgName = bgId != 0 ? entryName(res, bgId) : (bg != null ? bg : kb.defaultBgName);
        if (k.bgName == null) {
            k.bgName = kb.defaultBgName;
        }
        return k;
    }

    private void layout(DisplayMetrics dm) {
        float maxRowWidth = 0f;
        float y = paddingV;
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            float x = padding;
            for (int i = 0; i < row.keys.size(); i++) {
                Key k = row.keys.get(i);
                x += k.gapBefore;
                k.rect.set(x, y, x + k.width, y + keyHeight);
                x += k.width;
                if (i < row.keys.size() - 1) {
                    x += gapX;
                }
            }
            row.width = x - padding;
            if (row.width > maxRowWidth) {
                maxRowWidth = row.width;
            }
            y += keyHeight;
            if (r < rows.size() - 1) {
                y += gapY;
            }
        }
        width = Math.round(maxRowWidth + padding * 2f);
        height = Math.round(rows.size() * keyHeight
                + Math.max(0, rows.size() - 1) * gapY
                + paddingV * 2f);
    }

    private static float resolveWidth(String spec, float defaultWidth, float gap, DisplayMetrics dm) {
        String s = spec.trim();
        if (s.startsWith("spanDefaultKeys_")) {
            try {
                int n = Integer.parseInt(s.substring("spanDefaultKeys_".length()));
                return n * defaultWidth + Math.max(0, n - 1) * gap;
            } catch (NumberFormatException ignored) {
                return defaultWidth;
            }
        }
        if (s.startsWith("spanKeyboardFraction_")) {
            // VrShell 的分数宽度按“基准列数”计算；当前布局未使用，做保守回退。
            try {
                String[] parts = s.substring("spanKeyboardFraction_".length()).split("_");
                int num = Integer.parseInt(parts[0]);
                int den = Integer.parseInt(parts[1]);
                int basis = Integer.parseInt(parts[2]);
                float basisWidth = basis * defaultWidth + Math.max(0, basis - 1) * gap;
                return basisWidth * num / (float) den;
            } catch (Exception ignored) {
                return defaultWidth;
            }
        }
        return dim(s, defaultWidth, dm);
    }

    private static int[] parseCodes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new int[0];
        }
        String[] parts = value.split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            try {
                out[n++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {
                // 跳过非法项
            }
        }
        if (n == out.length) {
            return out;
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static String attr(XmlResourceParser p, String name) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (name.equals(p.getAttributeName(i))) {
                return p.getAttributeValue(i);
            }
        }
        return null;
    }

    private static int attrRes(XmlResourceParser p, String name) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (name.equals(p.getAttributeName(i))) {
                return p.getAttributeResourceValue(i, 0);
            }
        }
        return 0;
    }

    private static int color(Resources res, int id, int def) {
        if (id == 0) {
            return def;
        }
        try {
            return res.getColor(id, null);
        } catch (Exception e) {
            return def;
        }
    }

    private static String entryName(Resources res, int id) {
        if (id == 0) {
            return null;
        }
        try {
            return res.getResourceEntryName(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static String unescape(String s) {
        if (s != null && s.length() > 1 && s.charAt(0) == '\\') {
            return s.substring(1);
        }
        return s;
    }

    private static float dim(String value, float def, DisplayMetrics dm) {
        return dim(value, def, dm, false);
    }

    private static float dim(String value, float def, DisplayMetrics dm, boolean text) {
        if (value == null) {
            return def;
        }
        String s = value.trim();
        try {
            if (s.endsWith("dip")) {
                return Float.parseFloat(s.substring(0, s.length() - 3).trim()) * dm.density;
            }
            if (s.endsWith("dp")) {
                return Float.parseFloat(s.substring(0, s.length() - 2).trim()) * dm.density;
            }
            if (s.endsWith("sp")) {
                return Float.parseFloat(s.substring(0, s.length() - 2).trim()) * dm.scaledDensity;
            }
            if (s.endsWith("px")) {
                return Float.parseFloat(s.substring(0, s.length() - 2).trim());
            }
            return Float.parseFloat(s) * (text ? dm.scaledDensity : dm.density);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float dp(float v, DisplayMetrics dm) {
        return v * dm.density;
    }

    private static float sp(float v, DisplayMetrics dm) {
        return v * dm.scaledDensity;
    }
}
