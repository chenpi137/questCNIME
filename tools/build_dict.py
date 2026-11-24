#!/usr/bin/env python3
"""Convert Rime pinyin_simp.dict.yaml to the compact IME dictionary asset.

Input lines:  <word>\t<space separated pinyin>\t<weight>
Output lines: <word>\t<concatenated pinyin>\t<weight>
"""
import sys

src = sys.argv[1]
dst = sys.argv[2]

entries = {}
with open(src, encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\n\r")
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        word = parts[0].strip()
        pinyin = "".join(parts[1].split()).lower()
        if not word or not pinyin:
            continue
        if any(c < "a" or c > "z" for c in pinyin):
            continue
        try:
            weight = int(parts[2].strip())
        except (IndexError, ValueError):
            weight = 0
        key = (word, pinyin)
        if entries.get(key, -1) < weight:
            entries[key] = weight

rows = sorted(entries.items(), key=lambda kv: (-kv[1], kv[0][1], kv[0][0]))
with open(dst, "w", encoding="utf-8", newline="\n") as f:
    for (word, pinyin), weight in rows:
        f.write("%s\t%s\t%d\n" % (word, pinyin, weight))

print("entries:", len(rows))
