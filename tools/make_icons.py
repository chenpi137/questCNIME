# -*- coding: utf-8 -*-
"""Generate QuestCNIME launcher icons (legacy + adaptive foreground)."""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "res")
FONT = r"C:\Windows\Fonts\msyhbd.ttc"
BG = (28, 28, 30, 255)
STROKE = (58, 58, 60, 255)
TEXT = (242, 242, 242, 255)
ACCENT = (102, 153, 255, 255)


def rounded_icon(size, circular=False):
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if circular:
        d.ellipse([0, 0, s - 1, s - 1], fill=BG, outline=STROKE, width=max(4, s // 96))
    else:
        r = int(s * 0.22)
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=BG,
                            outline=STROKE, width=max(4, s // 96))
    font = ImageFont.truetype(FONT, int(s * 0.60))
    # glyph slightly above center, leaving room for the accent spacebar
    d.text((s * 0.5, s * 0.46), "中", font=font, fill=TEXT, anchor="mm")
    bw, bh = int(s * 0.50), int(s * 0.055)
    by = int(s * 0.80)
    d.rounded_rectangle([s // 2 - bw // 2, by, s // 2 + bw // 2, by + bh],
                        radius=bh // 2, fill=ACCENT)
    return img.resize((size, size), Image.LANCZOS)


def adaptive_foreground(size):
    s = size * 4
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, int(s * 0.46))
    d.text((s * 0.5, s * 0.45), "中", font=font, fill=TEXT, anchor="mm")
    bw, bh = int(s * 0.36), int(s * 0.040)
    by = int(s * 0.76)
    d.rounded_rectangle([s // 2 - bw // 2, by, s // 2 + bw // 2, by + bh],
                        radius=bh // 2, fill=ACCENT)
    return img.resize((size, size), Image.LANCZOS)


def save(img, folder, name):
    out = os.path.join(RES, folder)
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, name), "PNG", optimize=True)


def main():
    legacy = {"mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
              "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192}
    fg = {"mipmap-mdpi": 108, "mipmap-hdpi": 162, "mipmap-xhdpi": 216,
          "mipmap-xxhdpi": 324, "mipmap-xxxhdpi": 432}
    for folder, size in legacy.items():
        save(rounded_icon(size), folder, "ic_launcher.png")
        save(rounded_icon(size, circular=True), folder, "ic_launcher_round.png")
    for folder, size in fg.items():
        save(adaptive_foreground(size), folder, "ic_launcher_foreground.png")
    print("icons generated")


if __name__ == "__main__":
    main()
