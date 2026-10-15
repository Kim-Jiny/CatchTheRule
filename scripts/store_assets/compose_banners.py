#!/usr/bin/env python3
"""App Store header (3840x1646) and search-result (1920x1280) images, 7 languages.
Output: dist/store-assets/{header,search}/<lang>.jpg  (RGB JPEG, <1MB)
Reuses styling helpers from compose_screenshots.py (imported, unchanged).
Usage: python3 scripts/store_assets/compose_banners.py [--langs ko,en] [--only header|search]
"""
import argparse, io, os, random, sys
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
import compose_screenshots as cs
from compose_screenshots import hx, lerp, rr_mask, font, parse, FONTS, SYMBOL_FONT

ROOT, RAW = cs.ROOT, cs.RAW
ICON = os.path.join(ROOT, "iOS/CatchTheRule/Assets.xcassets/AppIcon.appiconset/AppIcon1024.png")
LANGS = ["ko", "en", "ja", "zh-Hans", "es", "fr", "de"]
DIR = {"zh-Hans": "zh"}
NAME = {"ko": "규칙*찾기*", "en": "Catch the *Rule*", "ja": "ルールを*探せ*", "zh-Hans": "发现*规律*",
        "es": "Encuentra la *Regla*", "fr": "Trouve le *Motif*", "de": "Finde die *Regel*"}
SUB = {"ko": "패턴을 찾는 두뇌 퍼즐", "en": "Brain puzzles about patterns", "ja": "パターン発見の脳トレパズル",
       "zh-Hans": "发现规律的益智谜题", "es": "Puzles mentales de patrones", "fr": "Puzzles de logique et motifs",
       "de": "Logikrätsel mit Mustern"}
GRAD = ("#B79CFF", "#18C8FF")
VIO, CYA, PINK, GRN, ORA = "#7C5CFF", "#18C8FF", "#FF5C8A", "#3DDC97", "#FF7A45"

def background(W, H, glows, seed, nglyph):
    s = 4
    base = Image.composite(Image.new("RGB", (W, H), hx("#10141C")), Image.new("RGB", (W, H), hx("#0B0E14")),
                           Image.linear_gradient("L").resize((W, H))).convert("RGBA")
    lay = Image.new("RGBA", (W // s, H // s), (0, 0, 0, 0))
    for fx, fy, fr, col, a in glows:
        l = Image.new("RGBA", lay.size, (0, 0, 0, 0))
        cx, cy, r = fx * W / s, fy * H / s, fr * H / s
        ImageDraw.Draw(l).ellipse([cx - r * 1.2, cy - r, cx + r * 1.2, cy + r], fill=hx(col) + (a,))
        lay = Image.alpha_composite(lay, l.filter(ImageFilter.GaussianBlur(r * 0.55)))
    base = Image.alpha_composite(base, lay.resize((W, H), Image.BICUBIC))
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0)); rnd = random.Random(seed)
    for _ in range(nglyph):
        g = rnd.choice(list("+−×÷=?▲●■79π")); size = rnd.randint(int(H * .05), int(H * .14))
        f = ImageFont.truetype(SYMBOL_FONT, size)
        t = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
        ImageDraw.Draw(t).text((size, size), g, font=f, fill=(255, 255, 255, rnd.randint(12, 26)), anchor="mm")
        gl.alpha_composite(t.rotate(rnd.randint(-30, 30), resample=Image.BICUBIC), (rnd.randint(-40, W - size), rnd.randint(-30, H - size)))
    return Image.alpha_composite(base, gl).convert("RGB")

def make_phone(src, w, glow):
    """RGBA phone (frame + screen) of width w."""
    k = w / 1032.0; bz = 16 * k; sw = w - 2 * bz; sh = sw * 2868 / 1320
    PW, PH = w, round(sh + 2 * bz); S = 1
    ph_r, scr_r = round(144 * k), round(128 * k)
    edge = rr_mask((PW, PH), ph_r)
    frame = Image.new("RGB", (PW, PH), (18, 20, 28))
    inner = Image.new("L", (PW, PH), 0); inner.paste(rr_mask((PW - 6, PH - 6), ph_r - 3), (3, 3))
    rim = ImageChops.subtract(edge, inner)
    frame.paste(Image.new("RGB", (PW, PH), lerp(hx(glow), (255, 255, 255), .35)), (0, 0), rim.point(lambda v: int(v * .75)))
    scr = src.convert("RGB").resize((round(sw), round(sh)), Image.LANCZOS)
    frame.paste(scr, (round(bz), round(bz)), rr_mask(scr.size, scr_r))
    out = frame.convert("RGBA"); out.putalpha(edge)
    return out

def place_phone(img, src, cx, top, w, angle, glow):
    W, H = img.size
    ph = make_phone(src, w * 2, glow)  # 2x for clean rotation
    if angle: ph = ph.rotate(angle, resample=Image.BICUBIC, expand=True)
    ph = ph.resize((ph.width // 2, ph.height // 2), Image.LANCZOS)
    x, y = round(cx - ph.width / 2), round(top)
    a = Image.new("L", (W, H), 0); a.paste(ph.getchannel("A"), (x, y))
    halo = a.filter(ImageFilter.GaussianBlur(w * .10)).point(lambda v: int(v * .5))
    img.paste(Image.new("RGB", (W, H), hx(glow)), (0, 0), halo)
    sh = Image.new("L", (W, H), 0); sh.paste(ph.getchannel("A"), (x, y + round(w * .04)))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), sh.filter(ImageFilter.GaussianBlur(w * .06)).point(lambda v: int(v * .6)))
    img.paste(ph.convert("RGB"), (x, y), ph.getchannel("A"))

def place_icon(img, x, y, size, glow=VIO):
    W, H = img.size
    ic = Image.open(ICON).convert("RGB").resize((size, size), Image.LANCZOS)
    m = rr_mask((size, size), round(size * .2237))
    a = Image.new("L", (W, H), 0); a.paste(m, (x, y))
    img.paste(Image.new("RGB", (W, H), hx(glow)), (0, 0), a.filter(ImageFilter.GaussianBlur(size * .16)).point(lambda v: int(v * .7)))
    sh = Image.new("L", (W, H), 0); sh.paste(m, (x, y + round(size * .04)))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), sh.filter(ImageFilter.GaussianBlur(size * .05)).point(lambda v: int(v * .55)))
    img.paste(ic, (x, y), m)

def fit(text, spec, maxw, start, minone, maxlines=2):
    """Prefer one line down to size minone; otherwise allow maxlines."""
    for ml, lo in ((1, minone), (maxlines, 24)):
        size = start
        while size >= lo:
            f = font(spec, size); lines = []
            for l in parse(text): lines += cs.wrap(l, f, maxw)
            if len(lines) <= ml and all(f.getlength("".join(c for c, _ in l).rstrip()) <= maxw for l in lines):
                return f, lines, size
            size -= 4
    return f, lines, size

def draw_block(img, lines, f, x, y, lh, accent=GRAD, shadow=True, color=(255, 255, 255)):
    W, H = img.size
    wm, am = Image.new("L", (W, H), 0), Image.new("L", (W, H), 0)
    wd, ad = ImageDraw.Draw(wm), ImageDraw.Draw(am)
    xmin, xmax = W, 0
    for li, line in enumerate(lines):
        full = "".join(c for c, _ in line).rstrip(); yy = y + li * lh
        for i, (ch, a) in enumerate(line[:len(full)]):
            (ad if a else wd).text((x + f.getlength(full[:i]), yy), ch, font=f, fill=255)
            if a: xmin = min(xmin, x + f.getlength(full[:i])); xmax = max(xmax, x + f.getlength(full[:i + 1]))
    if shadow:
        sm = ImageChops.lighter(wm, am).filter(ImageFilter.GaussianBlur(f.size * .1)).point(lambda v: int(v * .55))
        img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, round(f.size * .06)), sm)
    img.paste(Image.new("RGB", (W, H), color), (0, 0), wm)
    if xmax > xmin:
        g = Image.new("RGB", (W, H)); gd = ImageDraw.Draw(g)
        c1, c2 = hx(accent[0]), hx(accent[1]); span = max(1, xmax - xmin)
        for xx in range(max(0, int(xmin) - 2), min(W, int(xmax) + 3)):
            gd.line([(xx, 0), (xx, H)], fill=lerp(c1, c2, min(1, max(0, (xx - xmin) / span))))
        img.paste(g, (0, 0), am)

def sub_block(img, lang, text, x, y, maxw, start, lines_max=2):
    f, lines, size = fit(text, FONTS[lang][1], maxw, start, 30, lines_max)
    lh = round(size * 1.3)
    draw_block(img, lines, f, x, y, lh, shadow=False, color=(190, 198, 216))
    return y + lh * len(lines)

def raw(lang, scene):
    d = os.path.join(RAW, lang)
    if not os.path.isdir(d) and lang == "zh-Hans": d = os.path.join(RAW, "zh")
    return Image.open(os.path.join(d, scene + ".png"))

def header(lang):
    W, H = 3840, 1646
    img = background(W, H, [(.10, .25, .55, VIO, 200), (.45, .95, .45, PINK, 110), (.62, .15, .5, CYA, 150),
                            (.80, .85, .5, GRN, 120), (.92, .25, .4, ORA, 120)], LANGS.index(lang) * 5 + 1, 34)
    sx, sy = round(W * .08), round(H * .10)
    # phones (right ~55%)
    place_phone(img, raw(lang, "target"), 2135, 330, 600, -7, GRN)
    place_phone(img, raw(lang, "match"), 3195, 330, 600, 7, ORA)
    place_phone(img, raw(lang, "home"), 2665, 215, 720, 0, VIO)
    # left block
    maxw = 1330
    f, lines, size = fit(NAME[lang], FONTS[lang][0], maxw, 250, 150)
    lh = round(size * 1.18)
    sf, sl, ssz = fit(SUB[lang], FONTS[lang][1], maxw, 84, 56)
    icon = 340; gap1, gap2 = 70, 50
    total = icon + gap1 + lh * len(lines) + gap2 + round(ssz * 1.3) * len(sl)
    y0 = max(sy, (H - total) // 2)
    place_icon(img, sx, y0, icon)
    y = y0 + icon + gap1
    draw_block(img, lines, f, sx, y, lh)
    y += lh * len(lines) + gap2
    draw_block(img, sl, sf, sx, y, round(ssz * 1.3), shadow=False, color=(190, 198, 216))
    return img

def search(lang):
    W, H = 1920, 1280
    img = background(W, H, [(.12, .25, .6, VIO, 200), (.55, .95, .45, PINK, 110), (.75, .1, .5, CYA, 150),
                            (.92, .8, .45, ORA, 110)], LANGS.index(lang) * 5 + 2, 16)
    sx, sy = round(W * .08), round(H * .10)
    place_phone(img, raw(lang, "contradiction"), 1215, 290, 440, -7, PINK)
    place_phone(img, raw(lang, "home"), 1490, 190, 520, 0, VIO)
    maxw = 760
    f, lines, size = fit(NAME[lang], FONTS[lang][0], maxw, 140, 100)
    lh = round(size * 1.18)
    sf, sl, ssz = fit(SUB[lang], FONTS[lang][1], maxw, 50, 36)
    icon = 230; gap1, gap2 = 44, 30
    total = icon + gap1 + lh * len(lines) + gap2 + round(ssz * 1.3) * len(sl)
    y0 = max(sy, (H - total) // 2)
    place_icon(img, sx, y0, icon)
    y = y0 + icon + gap1
    draw_block(img, lines, f, sx, y, lh)
    y += lh * len(lines) + gap2
    draw_block(img, sl, sf, sx, y, round(ssz * 1.3), shadow=False, color=(190, 198, 216))
    return img

def save(img, path, limit):
    for q in (88, 86, 84, 82, 80, 78, 75):
        buf = io.BytesIO(); img.save(buf, "JPEG", quality=q, subsampling=2, optimize=True)
        if buf.tell() <= limit: break
    with open(path, "wb") as fh: fh.write(buf.getvalue())
    return q, buf.tell()

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--langs", default=",".join(LANGS)); ap.add_argument("--only", default="header,search")
    a = ap.parse_args()
    for l in a.langs.split(","):
        lang = "zh-Hans" if l in ("zh", "zh-Hans") else l
        for kind, fn, lim in (("header", header, 700_000), ("search", search, 350_000)):
            if kind not in a.only: continue
            d = os.path.join(ROOT, "dist/store-assets", kind); os.makedirs(d, exist_ok=True)
            q, n = save(fn(lang), os.path.join(d, DIR.get(lang, lang) + ".jpg"), lim)
            print(kind, lang, "q%d" % q, "%.0fKB" % (n / 1024))

if __name__ == "__main__":
    main()
