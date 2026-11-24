package com.questcn.ime;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * 轻量拼音引擎：
 *  - 词库来源于 AOSP PinyinIME 衍生字典（assets/pinyin_dict.txt）
 *  - 支持整串前缀匹配、完整音节单字、首音节单字候选
 *  - 纯 Java，无 native 依赖
 */
public class PinyinEngine {
    private static final String TAG = "PinyinEngine";

    private static class Word {
        final String text;
        final String pinyin;
        final int weight;

        Word(String text, String pinyin, int weight) {
            this.text = text;
            this.pinyin = pinyin;
            this.weight = weight;
        }
    }

    private static class Scored {
        final String text;
        final int score;
        final int weight;

        Scored(String text, int score, int weight) {
            this.text = text;
            this.score = score;
            this.weight = weight;
        }
    }

    private final TreeMap<String, ArrayList<Word>> map = new TreeMap<>();
    private final Set<String> syllables = new HashSet<>();
    private volatile boolean ready;

    public boolean isReady() {
        return ready;
    }

    public void loadAsync(final Context context, final Runnable onLoaded) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                load(context);
                if (onLoaded != null) {
                    onLoaded.run();
                }
            }
        }, "pinyin-dict-loader");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private void load(Context context) {
        long start = System.currentTimeMillis();
        BufferedReader reader = null;
        int count = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    context.getAssets().open("pinyin_dict.txt"), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }
                String text = parts[0].trim();
                String pinyin = normalize(parts[1]);
                if (text.isEmpty() || pinyin.isEmpty()) {
                    continue;
                }
                int weight = 0;
                if (parts.length > 2) {
                    try {
                        weight = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                ArrayList<Word> list = map.get(pinyin);
                if (list == null) {
                    list = new ArrayList<>(2);
                    map.put(pinyin, list);
                }
                list.add(new Word(text, pinyin, weight));
                count++;
                if (text.codePointCount(0, text.length()) == 1) {
                    syllables.add(pinyin);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to load pinyin dictionary", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        ready = true;
        Log.i(TAG, "dictionary loaded: " + count + " entries, "
                + map.size() + " keys, " + syllables.size() + " syllables, "
                + (System.currentTimeMillis() - start) + "ms");
    }

    public List<String> query(String input, int limit) {
        if (!ready || input == null) {
            return Collections.emptyList();
        }
        String key = normalize(input);
        if (key.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Scored> best = new LinkedHashMap<>();
        NavigableMap<String, ArrayList<Word>> sub =
                map.subMap(key, true, key + '\uffff', true);
        int scanned = 0;
        for (Map.Entry<String, ArrayList<Word>> entry : sub.entrySet()) {
            if (++scanned > 3000) {
                break;
            }
            String py = entry.getKey();
            int extra = py.length() - key.length();
            boolean exact = extra == 0;
            for (Word w : entry.getValue()) {
                int chars = w.text.codePointCount(0, w.text.length());
                if (!exact && chars < 2) {
                    // 未输完的长音节单字（如 ni -> nian 的年）不作为候选
                    continue;
                }
                int score;
                if (exact) {
                    score = 30000;
                } else {
                    score = 12000 - extra * 40;
                }
                if (chars > 1) {
                    score += 800 + Math.min(chars, 4) * 120;
                } else if (key.length() <= 2) {
                    score += 600;
                }
                score += Math.min(w.weight, 30000) / 10;
                putBetter(best, w, score);
            }
        }

        // 首音节单字/词语（如 nihao -> ni 的 你、尼、呢）
        String first = longestSyllablePrefix(key);
        if (first != null) {
            ArrayList<Word> list = map.get(first);
            if (list != null) {
                for (Word w : list) {
                    int chars = w.text.codePointCount(0, w.text.length());
                    int score = 6000 + Math.min(w.weight, 30000) / 8;
                    if (chars > 1) {
                        score += 200;
                    }
                    putBetter(best, w, score);
                }
            }
        }

        ArrayList<Scored> all = new ArrayList<>(best.values());
        Collections.sort(all, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                if (a.score != b.score) {
                    return b.score - a.score;
                }
                return b.weight - a.weight;
            }
        });

        ArrayList<String> out = new ArrayList<>();
        int n = Math.min(limit, all.size());
        for (int i = 0; i < n; i++) {
            out.add(all.get(i).text);
        }
        return out;
    }

    private void putBetter(Map<String, Scored> best, Word w, int score) {
        Scored old = best.get(w.text);
        if (old == null || score > old.score
                || (score == old.score && w.weight > old.weight)) {
            best.put(w.text, new Scored(w.text, score, w.weight));
        }
    }

    /** 按音节插入分隔符，例如 pinying -> pin'ying，用于小拼音提示。 */
    public String formatSyllables(String input) {
        String key = normalize(input);
        if (key.isEmpty()) {
            return "";
        }
        if (!ready || syllables.isEmpty()) {
            return key;
        }
        StringBuilder out = new StringBuilder(key.length() + 4);
        int pos = 0;
        while (pos < key.length()) {
            int max = Math.min(6, key.length() - pos);
            String chosen = null;
            for (int len = max; len >= 1; len--) {
                String sub = key.substring(pos, pos + len);
                if (syllables.contains(sub)) {
                    chosen = sub;
                    break;
                }
            }
            if (chosen == null) {
                if (out.length() > 0) {
                    out.append('\'');
                }
                out.append(key.substring(pos));
                break;
            }
            if (out.length() > 0) {
                out.append('\'');
            }
            out.append(chosen);
            pos += chosen.length();
        }
        return out.toString();
    }

    private String longestSyllablePrefix(String key) {
        int max = Math.min(6, key.length());
        for (int len = max; len >= 1; len--) {
            String sub = key.substring(0, len);
            if (syllables.contains(sub)) {
                return sub;
            }
        }
        return null;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
