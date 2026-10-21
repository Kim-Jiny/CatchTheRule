#!/usr/bin/env python3
"""Code-drawn app icon concepts (1024x1024 RGB, full-bleed square, no alpha).
Output: dist/store-assets/icon-concepts/concept-{1,2,3}.png + compare.png
Usage : python3 scripts/store_assets/gen_icon_concepts.py
"""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops, ImageOps

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "dist/store-assets/icon-concepts")
CUR = os.path.join(ROOT, "appIcon.png")
S = 4; N = 1024 * S
BOLD = "/System/Library/Fonts/HelveticaNeue.ttc"
def hx(s): return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))
def font(size): return ImageFont.truetype(BOLD, int(size * S), index=1)
def P(v): return int(v * S)

def background():
    g = Image.radial_gradient("L").resize((N, N))  # 0 centre -> 255 edge
    g = g.point(lambda v: min(255, int(v * 1.15)))
    bg = Image.composite(Image.new("RGB", (N, N), hx("#090C15")), Image.new("RGB", (N, N), hx("#1A2140")), g)
    return bg.convert("RGBA")

def mask(): return Image.new("L", (N, N), 0)

def grad(c1, c2, box=None, angle=45):
    """linear gradient image N x N from c1 to c2 along angle across box."""
    x0, y0, x1, y1 = box or (0, 0, N, N)
    g = Image.linear_gradient("L").resize((N, N))
    # build diagonal t via two linear gradients
    gx = Image.linear_gradient("L").rotate(90, expand=False).resize((N, N))  # horizontal-ish
    t = ImageChops.blend(gx, ImageOps.flip(g) if False else g, 0.5)
    return Image.composite(Image.new("RGB", (N, N), c2), Image.new("RGB", (N, N), c1), t.transpose(Image.FLIP_LEFT_RIGHT) if False else t)

def lin(c1, c2, box):
    """Diagonal gradient (top-left c1 -> bottom-right c2) spanning box, as RGB N x N."""
    x0, y0, x1, y1 = box
    sm = 256
    im = Image.new("RGB", (sm, sm))
    px = im.load()
    for yy in range(sm):
        for xx in range(sm):
            X = (xx / sm) * N; Y = (yy / sm) * N
            t = ((X - x0) / (x1 - x0) + (Y - y0) / (y1 - y0)) / 2
            t = min(1, max(0, t))
            px[xx, yy] = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
    return im.resize((N, N), Image.BICUBIC)

def add_glow(canvas, m, color, radius, strength=1.0):
    """additive neon glow from mask m (N x N L)."""
    small = m.resize((N // 4, N // 4), Image.BILINEAR).filter(ImageFilter.GaussianBlur(radius * S / 4))
    gl = small.resize((N, N), Image.BICUBIC).point(lambda v: min(255, int(v * strength)))
    layer = Image.new("RGBA", (N, N), color + (0,)); layer.putalpha(gl)
    return Image.alpha_composite(canvas, layer)

def paint(canvas, m, fill):
    src = fill if isinstance(fill, Image.Image) else Image.new("RGB", (N, N), fill)
    layer = src.convert("RGBA"); layer.putalpha(m)
    return Image.alpha_composite(canvas, layer)

def outline(m, w):
    er = m.filter(ImageFilter.MinFilter(2 * P(w) // 2 * 2 + 1)) if False else m
    k = max(3, int(P(w)) | 1)
    inner = m.resize((N // 4, N // 4)).filter(ImageFilter.MinFilter(max(3, (k // 4) | 1))).resize((N, N), Image.BILINEAR)
    return ImageChops.subtract(m, inner)

def text_mask(txt, size, cx, cy):
    m = mask(); ImageDraw.Draw(m).text((P(cx), P(cy)), txt, font=font(size), fill=255, anchor="mm"); return m

# ---------- concept 1: rule tiles ----------
def concept1():
    c = background()
    c = add_glow(c, ellipse_mask(512, 520, 480, 300), hx("#7C5CFF"), 160, 0.35)
    tw, th, gap = 196, 236, 24
    x0 = (1024 - (4 * tw + 3 * gap)) / 2
    cols = [("#7C5CFF", "#9B7CFF"), ("#5B7CFF", "#7C9CFF"), ("#18C8FF", "#6CE0FF"), ("#FFB020", "#FF5C8A")]
    labels = ["2", "4", "8", "?"]
    for i in range(4):
        x = x0 + i * (tw + gap); y = 512 - th / 2 - (i - 1.5) * 26
        box = (P(x), P(y), P(x + tw), P(y + th))
        m = mask(); ImageDraw.Draw(m).rounded_rectangle(box, P(44), fill=255)
        a, b = hx(cols[i][0]), hx(cols[i][1])
        c = add_glow(c, outline(m, 12), a, 26, 1.5)
        c = paint(c, m, hx("#10162A"))
        c = paint(c, ImageChops.multiply(m, Image.new("L", (N, N), 70)), lin(a, b, box))
        c = paint(c, outline(m, 12), lin(a, b, box))
        tm = text_mask(labels[i], 170 if i < 3 else 190, x + tw / 2, y + th / 2 + 6)
        if i == 3: c = add_glow(c, tm, hx("#FFB020"), 18, 1.6)
        c = paint(c, tm, (255, 255, 255))
    return c

def ellipse_mask(cx, cy, rx, ry):
    m = mask(); ImageDraw.Draw(m).ellipse([P(cx - rx), P(cy - ry), P(cx + rx), P(cy + ry)], fill=255); return m

# ---------- concept 2: bulb + puzzle ----------
def concept2():
    c = background()
    cx, cy, r = 512, 430, 300
    bulb = mask(); d = ImageDraw.Draw(bulb)
    d.ellipse([P(cx - r), P(cy - r), P(cx + r), P(cy + r)], fill=255)
    d.polygon([(P(cx - 215), P(cy + 190)), (P(cx + 215), P(cy + 190)), (P(cx + 120), P(cy + 400)), (P(cx - 120), P(cy + 400))], fill=255)
    c = add_glow(c, ellipse_mask(cx, cy, 330, 330), hx("#7C5CFF"), 120, 0.45)
    # puzzle piece (rounded square + tabs on top/right, notch on left)
    pz = mask(); pd = ImageDraw.Draw(pz)
    s0, s1 = 360, 640; sy0, sy1 = 275, 555
    pd.rounded_rectangle([P(s0), P(sy0), P(s1), P(sy1)], P(30), fill=255)
    pd.ellipse([P(500 - 45), P(sy0 - 55), P(500 + 45), P(sy0 + 35)], fill=255)
    pd.ellipse([P(s1 - 35), P(415 - 45), P(s1 + 55), P(415 + 45)], fill=255)
    pd.ellipse([P(s0 - 45), P(415 - 40), P(s0 + 35), P(415 + 40)], fill=0)
    pd.rectangle([P(420), P(sy1 - 10), P(580), P(sy1 + 5)], fill=255)
    pbox = (P(s0), P(sy0), P(s1), P(sy1))
    c = paint(c, ImageChops.multiply(bulb, Image.new("L", (N, N), 255)), hx("#10162A"))
    c = paint(c, pz, lin(hx("#7C5CFF"), hx("#18C8FF"), pbox))
    ol = outline(bulb, 16)
    base = mask(); bd = ImageDraw.Draw(base)
    for i, yy in enumerate([cy + 430, cy + 490]):
        w = 130 - i * 40
        bd.rounded_rectangle([P(cx - w), P(yy), P(cx + w), P(yy + 34)], P(17), fill=255)
    neon = lin(hx("#18C8FF"), hx("#9B7CFF"), (P(200), P(130), P(820), P(900)))
    allol = ImageChops.lighter(ol, base)
    c = add_glow(c, allol, hx("#18C8FF"), 22, 1.5)
    c = paint(c, allol, neon)
    tm = text_mask("?", 330, cx + 4, cy + 26)
    c = add_glow(c, tm, (255, 255, 255), 14, 0.9)
    c = paint(c, tm, (255, 255, 255))
    return c

# ---------- concept 3: six-colour ring ----------
def concept3():
    c = background()
    cols = ["#7C5CFF", "#18C8FF", "#3DDC97", "#FFB020", "#FF7A45", "#FF5C8A"]
    cx = cy = 512; R = 345; wdt = 84; gap = 7
    seg = 360 / 6
    for i, col in enumerate(cols):
        m = mask(); d = ImageDraw.Draw(m)
        a0, a1 = -90 + i * seg + gap, -90 + (i + 1) * seg - gap
        d.arc([P(cx - R), P(cy - R), P(cx + R), P(cy + R)], a0, a1, fill=255, width=P(wdt))
        for a in (a0, a1):  # round caps
            x = cx + (R - wdt / 2) * math.cos(math.radians(a)); y = cy + (R - wdt / 2) * math.sin(math.radians(a))
            d.ellipse([P(x - wdt / 2), P(y - wdt / 2), P(x + wdt / 2), P(y + wdt / 2)], fill=255)
        # note: arc width draws inward from R, so caps sit at R - wdt/2
        c = add_glow(c, m, hx(col), 30, 1.1)
        c = paint(c, m, hx(col))
    tm = text_mask("?", 470, cx, cy + 22)
    c = add_glow(c, tm, hx("#7C5CFF"), 30, 1.2)
    c = paint(c, tm, (255, 255, 255))
    return c

def finish(c):
    return c.convert("RGB").resize((1024, 1024), Image.LANCZOS)

def main():
    os.makedirs(OUT, exist_ok=True)
    icons = [finish(f()) for f in (concept1, concept2, concept3)]
    for i, im in enumerate(icons, 1):
        im.save(os.path.join(OUT, f"concept-{i}.png")); print("wrote concept", i, im.size, im.mode)
    cur = Image.open(CUR).convert("RGB").resize((1024, 1024), Image.LANCZOS)
    allicons = [cur] + icons
    sheet = Image.new("RGB", (4 * 520 + 40, 520 + 190 + 90 + 80), (236, 236, 240))
    def rnd(im, sz):  # iOS-ish rounded corners for preview only
        r = im.resize((sz, sz), Image.LANCZOS)
        m = Image.new("L", (sz * 4, sz * 4), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, sz * 4 - 1, sz * 4 - 1], int(sz * 4 * 0.2237), fill=255)
        r = r.convert("RGBA"); r.putalpha(m.resize((sz, sz), Image.LANCZOS)); return r
    names = ["current", "1 tiles", "2 bulb", "3 ring"]
    d = ImageDraw.Draw(sheet); lf = ImageFont.truetype(BOLD, 28, index=1)
    for i, im in enumerate(allicons):
        x = 20 + i * 520
        for sz, y in ((500, 50), (180, 590), (60, 790)):
            r = rnd(im, sz); sheet.paste(r, (x + (500 - sz) // 2 if sz != 500 else x, y), r)
        d.text((x + 250, 24), names[i], font=lf, fill=(40, 40, 50), anchor="mm")
    sheet.save(os.path.join(OUT, "compare.png")); print("wrote compare.png")

if __name__ == "__main__":
    main()
