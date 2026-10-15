#!/usr/bin/env python3
"""Google Play store assets: app icon 512, feature graphic 1024x500, phone screenshots 1080x1920.

Input : dist/store-assets/raw-android/<lang>/<scene>.png  (real-device captures, any ~1080x2300-2400)
Output: dist/store-assets/play/icon/icon-512.png
        dist/store-assets/play/feature/<lang>.{png,jpg}           (1024x500, no alpha)
        dist/store-assets/play/phone/<lang>/0N-<scene>.{png,jpg}  (1080x1920, no alpha)
Reuses look + localized copy from compose_screenshots.py / compose_banners.py (imported, unchanged).

Usage : python3 scripts/store_assets/compose_play.py [--langs ko,en] [--scenes home]
                 [--stand-in] [--only icon|feature|phone]
                 [--crop-top N] [--crop-bottom N=126] [--status-bar keep|overlay]
  --crop-top/--crop-bottom  pixels removed from the raw capture (default bottom 126 = 3-button nav bar).
  --status-bar overlay      draw a clean white status bar (12:00, wifi, cellular, battery) over the capture,
                            for captures where the app hid system bars; 'keep' leaves the capture as is.
                            Recommended: demo-mode capture -> --crop-bottom 126 (default) keep;
                            immersive capture (no bars) -> --crop-bottom 0 --status-bar overlay.
  --stand-in  use iOS captures (dist/store-assets/raw) as placeholders, cover-cropped to 1080x2400.
              Layout check only; NOT for release (iPhone status bar would show).
"""
import argparse, io, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import compose_screenshots as cs
import compose_banners as cb
from compose_screenshots import hx, lerp, rr_mask, font, FONTS, THEME, SCENES, T

ROOT = cs.ROOT
RAW_ANDROID = os.path.join(ROOT, "dist/store-assets/raw-android")
RAW_IOS = cs.RAW
OUT = os.path.join(ROOT, "dist/store-assets/play")
LANGS, DIR = cb.LANGS, cb.DIR
SW_, SH_ = 1080, 2340          # stand-in size only (real device capture SM-S911N); real input may be any ~1080x2300-2400
CROP = dict(top=0, bottom=126, status="keep")  # set from CLI in main()
PW_, PH_ = 1080, 1920          # phone screenshot canvas
K = PW_ / 1320.0               # scale of iOS layout constants

def raw_path(lang, scene, stand_in):
    base = RAW_IOS if stand_in else RAW_ANDROID
    for l in (lang, DIR.get(lang, lang)):
        p = os.path.join(base, l, scene + ".png")
        if os.path.exists(p): return p
    return os.path.join(base, lang, scene + ".png")

def draw_status_bar(im):
    """Clean white Android status bar (12:00, wifi, cellular, battery 100%) drawn over the top of im."""
    u = im.width / 1080.0; S = 4
    bh = round(130 * u)
    lay = Image.new("RGBA", (im.width * S, bh * S), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    cy = 62 * u * S; white = (255, 255, 255, 255)
    # clock
    f = font(FONTS["latin"][0], round(44 * u * S))
    d.text((72 * u * S, cy), "12:00", font=f, fill=white, anchor="lm")
    # battery (right-most)
    x1 = 1008 * u * S; bw, bh_ = 40 * u * S, 22 * u * S
    d.rounded_rectangle([x1 - bw, cy - bh_ / 2, x1, cy + bh_ / 2], radius=6 * u * S, fill=white)
    d.rounded_rectangle([x1 + 2 * u * S, cy - 5 * u * S, x1 + 6 * u * S, cy + 5 * u * S], radius=2 * u * S, fill=white)
    # cellular: 4 ascending bars
    cx1 = x1 - bw - 22 * u * S
    for i in range(4):
        h = (10 + 6 * i) * u * S; bx = cx1 - (4 - i) * 9 * u * S + 3 * u * S
        d.rectangle([bx, cy + 14 * u * S - h, bx + 6 * u * S, cy + 14 * u * S], fill=white)
    # wifi
    wx = cx1 - 4 * 9 * u * S - 40 * u * S; wy = cy + 14 * u * S
    for r in (30, 20, 10):
        R = r * u * S
        d.arc([wx - R, wy - R, wx + R, wy + R], 225, 315, fill=white, width=round(5 * u * S))
    R = 6 * u * S; d.pieslice([wx - R, wy - R, wx + R, wy + R], 225, 315, fill=white)
    lay = lay.resize((im.width, bh), Image.LANCZOS)
    out = im.convert("RGBA"); out.alpha_composite(lay, (0, 0))
    return out.convert("RGB")

def load_capture(path, stand_in):
    im = Image.open(path).convert("RGB")
    if stand_in:  # iOS placeholder: cover-fit to device size so crops behave like a real capture
        s = max(SW_ / im.width, SH_ / im.height)
        im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
        l = (im.width - SW_) // 2
        im = im.crop((l, 0, l + SW_, SH_))
    if CROP["status"] == "overlay": im = draw_status_bar(im)
    t, b = CROP["top"], CROP["bottom"]
    if t or b: im = im.crop((0, t, im.width, im.height - b))
    return im

# ---------- Android-style phone ----------
def make_phone(src, w, glow):
    """RGBA phone of width w: thin uniform bezel, rounded corners, punch-hole camera."""
    bz = max(2.0, w * 0.017); sw = w - 2 * bz; sh = sw * src.height / src.width  # aspect follows the (cropped) capture
    PW, PH = round(w), round(sh + 2 * bz)
    ph_r = round(w * 0.095); scr_r = max(2, round(ph_r - bz))
    s = 3  # supersample for crisp edges
    edge = rr_mask((PW, PH), ph_r)
    frame = Image.new("RGB", (PW, PH), (14, 16, 22))
    inner = Image.new("L", (PW, PH), 0)
    ib = max(1, round(w * 0.004)); inner.paste(rr_mask((PW - 2 * ib, PH - 2 * ib), ph_r - ib), (ib, ib))
    rim = ImageChops.subtract(edge, inner)
    frame.paste(Image.new("RGB", (PW, PH), lerp(hx(glow), (255, 255, 255), .35)), (0, 0), rim.point(lambda v: int(v * .7)))
    scr = src.convert("RGB").resize((round(sw), round(sh)), Image.LANCZOS)
    frame.paste(scr, (round(bz), round(bz)), rr_mask(scr.size, scr_r))
    # punch hole
    d = w * 0.052; cx = PW / 2; cy = bz + sw * 0.0575
    hole = Image.new("L", (round(d * 3 * s), round(d * 3 * s)), 0)
    hd = ImageDraw.Draw(hole); c = hole.width / 2
    hd.ellipse([c - d * s * .5, c - d * s * .5, c + d * s * .5, c + d * s * .5], fill=255)
    hole = hole.resize((round(d * 3), round(d * 3)), Image.LANCZOS)
    ring = Image.new("L", hole.size, 0); rd = ImageDraw.Draw(ring)
    rr_ = d * .5 + max(1, w * .004); rc = hole.width / 2
    ring = Image.new("L", (hole.width * s, hole.height * s), 0)
    ImageDraw.Draw(ring).ellipse([rc * s - rr_ * s, rc * s - rr_ * s, rc * s + rr_ * s, rc * s + rr_ * s], fill=255)
    ring = ring.resize(hole.size, Image.LANCZOS)
    pos = (round(cx - hole.width / 2), round(cy - hole.height / 2))
    frame.paste(Image.new("RGB", hole.size, (6, 7, 10)), pos, ring)
    frame.paste(Image.new("RGB", hole.size, (0, 0, 0)), pos, hole)
    out = frame.convert("RGBA"); out.putalpha(edge)
    return out

def place_phone(img, src, cx, top, w, angle, glow, halo_k=.10, halo_a=.5):
    W, H = img.size
    ph = make_phone(src, w * 2, glow)
    if angle: ph = ph.rotate(angle, resample=Image.BICUBIC, expand=True)
    ph = ph.resize((ph.width // 2, ph.height // 2), Image.LANCZOS)
    x, y = round(cx - ph.width / 2), round(top)
    a = Image.new("L", (W, H), 0); a.paste(ph.getchannel("A"), (x, y))
    halo = a.filter(ImageFilter.GaussianBlur(w * halo_k)).point(lambda v: int(v * halo_a))
    img.paste(Image.new("RGB", (W, H), hx(glow)), (0, 0), halo)
    sh = Image.new("L", (W, H), 0); sh.paste(ph.getchannel("A"), (x, y + round(w * .03)))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), sh.filter(ImageFilter.GaussianBlur(w * .05)).point(lambda v: int(v * .6)))
    img.paste(ph.convert("RGB"), (x, y), ph.getchannel("A"))

# ---------- phone screenshot ----------
def bg_for(theme, seed):
    """Reuse the iOS background at 1080x1920 by scaling the glow layout (globals swapped temporarily)."""
    th = dict(theme); th["glows"] = [(cx * K, cy / 2868 * PH_, r * K, col, a) for cx, cy, r, col, a in theme["glows"]]
    ow, oh = cs.W, cs.H
    cs.W, cs.H = PW_, PH_
    try: return cs.background(th, seed).convert("RGB")
    finally: cs.W, cs.H = ow, oh

def draw_head(img, lang, text, theme, y):
    W, H = img.size
    start = round((124 if lang in ("ko", "ja", "zh-Hans") else 112) * K)
    f, lines, size = cs.layout_text(text, FONTS[lang][0], W - 2 * 74, start)
    lh = int(size * 1.22)
    wm, am = Image.new("L", (W, H), 0), Image.new("L", (W, H), 0)
    wd, ad = ImageDraw.Draw(wm), ImageDraw.Draw(am)
    xmin, xmax = W, 0
    for li, line in enumerate(lines):
        full = "".join(c for c, _ in line).rstrip()
        x = (W - f.getlength(full)) / 2; yy = y + li * lh
        xmin = min(xmin, x); xmax = max(xmax, x + f.getlength(full))
        for ch, a in line[:len(full)]:
            (ad if a else wd).text((x, yy), ch, font=f, fill=255); x += f.getlength(ch)
    sm = ImageChops.lighter(wm, am).filter(ImageFilter.GaussianBlur(11)).point(lambda v: int(v * .55))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 10), sm)
    img.paste(Image.new("RGB", (W, H), (255, 255, 255)), (0, 0), wm)
    g = Image.new("RGB", (W, H)); gd = ImageDraw.Draw(g)
    c1, c2 = hx(theme["grad"][0]), hx(theme["grad"][1]); span = max(1, xmax - xmin)
    for x in range(W): gd.line([(x, 0), (x, H)], fill=lerp(c1, c2, min(1, max(0, (x - xmin) / span))))
    img.paste(g, (0, 0), am)
    return y + lh * len(lines)

def draw_sub(img, lang, sub, y):
    W = img.width
    for size in range(44, 26, -2):
        sf = font(FONTS[lang][1], size)
        mf = font(FONTS["latin"][1], size)
        adv = lambda ch: (mf if ch in cs.MATH else sf).getlength(ch)
        tot = sum(adv(c) for c in sub)
        if tot <= W - 2 * 80: break
    x = (W - tot) / 2; d = ImageDraw.Draw(img)
    for ch in sub:
        d.text((x, y), ch, font=mf if ch in cs.MATH else sf, fill=(190, 198, 216), anchor="la"); x += adv(ch)

def phone_shot(lang, scene, src):
    theme = THEME[scene]
    img = bg_for(theme, LANGS.index(lang) * 7 + SCENES.index(scene))
    head, sub = T[lang][scene]
    y = draw_head(img, lang, head, theme, 96)
    draw_sub(img, lang, sub, y + 24)
    w = 860
    place_phone(img, src, PW_ / 2, 560, w, 0, theme["glow"], halo_k=.07, halo_a=.55)
    return img

# ---------- feature graphic ----------
def feature(lang, caps):
    W, H = 1024, 500
    img = cb.background(W, H, [(.10, .25, .55, cb.VIO, 200), (.50, .98, .45, cb.PINK, 110), (.62, .10, .5, cb.CYA, 150),
                               (.92, .85, .5, cb.GRN, 110)], LANGS.index(lang) * 5 + 3, 14)
    sx = round(W * .07)
    place_phone(img, caps["contradiction"], 640, 105, 232, -6, cb.PINK, halo_k=.12)
    place_phone(img, caps["home"], 836, 62, 262, 0, cb.VIO, halo_k=.12)
    maxw = 440
    f, lines, size = cb.fit(cb.NAME[lang], FONTS[lang][0], maxw, 78, 54)
    lh = round(size * 1.18)
    sf, sl, ssz = cb.fit(cb.SUB[lang], FONTS[lang][1], maxw, 28, 20)
    icon = 118; g1, g2 = 24, 14
    total = icon + g1 + lh * len(lines) + g2 + round(ssz * 1.3) * len(sl)
    y0 = max(round(H * .10), (H - total) // 2)
    cb.place_icon(img, sx, y0, icon)
    y = y0 + icon + g1
    cb.draw_block(img, lines, f, sx, y, lh)
    y += lh * len(lines) + g2
    cb.draw_block(img, sl, sf, sx, y, round(ssz * 1.3), shadow=False, color=(190, 198, 216))
    return img

# ---------- io ----------
def save_jpg(img, path, limit):
    for q in (88, 86, 84, 82, 80, 76):
        buf = io.BytesIO(); img.save(buf, "JPEG", quality=q, subsampling=2, optimize=True)
        if buf.tell() <= limit: break
    with open(path, "wb") as fh: fh.write(buf.getvalue())
    return q, buf.tell()

def save_png(img, path, limit):
    img = img.convert("RGB"); img.save(path, optimize=True)
    n = os.path.getsize(path)
    if n > limit:  # shrink via adaptive palette-free fallback: 256-colour quantise keeps it opaque
        img.quantize(256, dither=Image.FLOYDSTEINBERG).convert("RGB").save(path, optimize=True)
        n = os.path.getsize(path)
    return n

def icon_out():
    d = os.path.join(OUT, "icon"); os.makedirs(d, exist_ok=True)
    Image.open(cb.ICON).convert("RGBA").resize((512, 512), Image.LANCZOS).save(os.path.join(d, "icon-512.png"), optimize=True)
    print("icon icon-512.png")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--langs", default=",".join(LANGS)); ap.add_argument("--scenes", default=",".join(SCENES))
    ap.add_argument("--crop-top", type=int, default=0); ap.add_argument("--crop-bottom", type=int, default=126)
    ap.add_argument("--status-bar", choices=("keep", "overlay"), default="keep")
    ap.add_argument("--stand-in", action="store_true"); ap.add_argument("--only", default="icon,feature,phone")
    a = ap.parse_args()
    langs = [("zh-Hans" if l in ("zh", "zh-Hans") else l) for l in a.langs.split(",")]
    scenes = a.scenes.split(","); only = a.only.split(",")
    CROP.update(top=a.crop_top, bottom=a.crop_bottom, status=a.status_bar)
    missing = 0
    if "icon" in only: icon_out()
    for lang in langs:
        od = DIR.get(lang, lang)
        if "feature" in only:
            caps, ok = {}, True
            for sc in ("home", "contradiction"):
                p = raw_path(lang, sc, a.stand_in)
                if not os.path.exists(p): print("MISSING raw:", p); missing += 1; ok = False
                else: caps[sc] = load_capture(p, a.stand_in)
            if ok:
                d = os.path.join(OUT, "feature"); os.makedirs(d, exist_ok=True)
                im = feature(lang, caps)
                n = save_png(im, os.path.join(d, od + ".png"), 1_200_000)
                q, m = save_jpg(im, os.path.join(d, od + ".jpg"), 200_000)
                print("feature", lang, "png %dKB jpg q%d %dKB" % (n // 1024, q, m // 1024))
        if "phone" in only:
            d = os.path.join(OUT, "phone", od); os.makedirs(d, exist_ok=True)
            for sc in scenes:
                p = raw_path(lang, sc, a.stand_in)
                if not os.path.exists(p): print("MISSING raw:", p); missing += 1; continue
                im = phone_shot(lang, sc, load_capture(p, a.stand_in))
                base = os.path.join(d, f"{SCENES.index(sc) + 1:02d}-{sc}")
                im.save(base + ".png", optimize=True)
                q, m = save_jpg(im, base + ".jpg", 300_000)
                print("phone", lang, sc, "png %dKB jpg q%d %dKB" % (os.path.getsize(base + ".png") // 1024, q, m // 1024))
    sys.exit(1 if missing else 0)

if __name__ == "__main__":
    main()
