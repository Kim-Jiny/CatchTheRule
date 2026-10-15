#!/usr/bin/env python3
"""App Store marketing screenshot composer (iPhone 6.9", 1320x2868, RGB PNG).

Input : dist/store-assets/raw/<lang>/<scene>.png
Output: dist/store-assets/appstore-{6.9,6.3,6.1}/<lang>/0N-<scene>.png (1320x2868, 1206x2622, 1179x2556)

Usage : python3 scripts/store_assets/compose_screenshots.py [--langs ko,en] [--scenes home] [--stand-in]
  --stand-in  use scratchpad captures as placeholders (layout check only; NOT for release).
Fonts are macOS system fonts, rasterised only.
"""
import argparse, os, random, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RAW = os.path.join(ROOT, "dist/store-assets/raw")
OUT_BASE = os.path.join(ROOT, "dist/store-assets")
# set name -> final size; layout is rendered once at 1320x2868
SETS = {"appstore-6.9": (1320, 2868), "appstore-6.3": (1206, 2622), "appstore-6.1": (1179, 2556)}
SCRATCH = "/private/tmp/claude-501/-Users-jiny-Documents-Jiny-CatchTheRule/133656b3-edd6-4295-bad5-4b0772e602e6/scratchpad"
STAND_IN = {"home": "h2", "numbers": "t2", "contradiction": "ta_s2", "target": "p8", "match": "p8",
            "challenge": "ta_s2"}

W, H = 1320, 2868
SCENES = ["home", "numbers", "contradiction", "target", "match", "challenge"]
LANGS = ["ko", "en", "ja", "zh-Hans", "es", "fr", "de"]
OUTDIR = {"zh-Hans": "zh"}

F = "/System/Library/Fonts/"
FONTS = {  # (path, index) headline bold / sub regular
    "ko": ((F + "AppleSDGothicNeo.ttc", 14), (F + "AppleSDGothicNeo.ttc", 2)),
    "ja": ((F + "ヒラギノ角ゴシック W7.ttc", 0), (F + "ヒラギノ角ゴシック W4.ttc", 0)),
    "zh-Hans": ((F + "Hiragino Sans GB.ttc", 2), (F + "Hiragino Sans GB.ttc", 0)),
    "latin": ((F + "HelveticaNeue.ttc", 1), (F + "HelveticaNeue.ttc", 10)),
}
FONTS["en"] = FONTS["es"] = FONTS["fr"] = FONTS["de"] = FONTS["latin"]
SYMBOL_FONT = "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"

def hx(s): return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))

# scene -> glows [(cx, cy, radius, color, alpha)], accent text gradient
THEME = {
    "home": dict(glows=[(200, 500, 700, "#7C5CFF", 190), (1150, 1500, 650, "#18C8FF", 150), (300, 2500, 600, "#7C5CFF", 110)],
                 grad=("#9B7CFF", "#18C8FF"), glow="#7C5CFF"),
    "numbers": dict(glows=[(1100, 400, 700, "#7C5CFF", 190), (150, 1700, 650, "#7C5CFF", 130), (1100, 2600, 500, "#5B7CFF", 100)],
                    grad=("#B79CFF", "#6C8CFF"), glow="#7C5CFF"),
    "contradiction": dict(glows=[(200, 450, 700, "#FF5C8A", 180), (1150, 1700, 650, "#FF5C8A", 120), (250, 2600, 500, "#B03CFF", 90)],
                          grad=("#FF5C8A", "#FFA0B8"), glow="#FF5C8A"),
    "target": dict(glows=[(1100, 450, 700, "#3DDC97", 160), (150, 1600, 650, "#3DDC97", 120), (1000, 2600, 500, "#18C8FF", 80)],
                   grad=("#3DDC97", "#9CF5C8"), glow="#3DDC97"),
    "match": dict(glows=[(200, 450, 700, "#FF7A45", 180), (1150, 1600, 650, "#FF7A45", 120), (300, 2600, 500, "#FFB020", 90)],
                  grad=("#FF7A45", "#FFB020"), glow="#FF7A45"),
    "challenge": dict(glows=[(1100, 450, 700, "#FFB020", 180), (150, 1700, 650, "#FF5C8A", 140), (1000, 2600, 500, "#FFB020", 90)],
                      grad=("#FFB020", "#FF5C8A"), glow="#FFB020"),
}

# '*...*' marks the accent (gradient) words; '\n' explicit line break.
T = {
 "ko": {
  "home": ("*6가지 모드*,\n끝없는 두뇌 퍼즐", "숫자·도형·논리·모순·성냥개비까지"),
  "numbers": ("숨은 *규칙*을\n찾아라", "수열 속 패턴을 발견하는 짜릿함"),
  "contradiction": ("앞뒤가 안 맞는\n*문장*은?", "이야기 속 모순을 한눈에 찾아내세요"),
  "target": ("숫자로 *목표*를\n만들어라", "+ − × ÷ 와 괄호로 자유롭게 조합"),
  "match": ("성냥개비 *한 개*로\n바로잡기", "막대를 드래그해 틀린 식을 고쳐요"),
  "challenge": ("60초 *타임어택*,\n월간 랭킹", "모든 모드가 섞여 나오는 속도 대결")},
 "en": {
  "home": ("*6 Modes* of Endless\nBrain Puzzles", "Numbers, shapes, logic, contradictions & matchsticks"),
  "numbers": ("Find the\n*Hidden Rule*", "The thrill of spotting the pattern"),
  "contradiction": ("Which Sentence\n*Doesn't Add Up?*", "Spot the contradiction in the story at a glance"),
  "target": ("Build the *Target*\nfrom the Numbers", "Combine freely with + − × ÷ and parentheses"),
  "match": ("Fix It with\n*One Matchstick*", "Drag a stick to correct the equation"),
  "challenge": ("*60-Second* Time Attack,\nMonthly Ranking", "A speed duel with every mode mixed in")},
 "ja": {
  "home": ("*6つのモード*で\n終わらない頭脳パズル", "数字・図形・論理・矛盾・マッチ棒まで"),
  "numbers": ("隠れた*ルール*を\n見つけ出せ", "数列のパターンを見抜く快感"),
  "contradiction": ("つじつまが\n合わない*文*は?", "物語の矛盾をひと目で見つけよう"),
  "target": ("数字で*目標*を\n作り出せ", "+ − × ÷ とカッコで自由に組み合わせ"),
  "match": ("マッチ棒*1本*で\n正しい式に", "棒をドラッグして間違った式を直そう"),
  "challenge": ("60秒*タイムアタック*\n月間ランキング", "全モードが入り混じるスピード勝負")},
 "zh-Hans": {
  "home": ("*6种模式*\n无尽烧脑谜题", "数字、图形、逻辑、矛盾、火柴棒"),
  "numbers": ("找出隐藏的*规律*", "发现数列中模式的快感"),
  "contradiction": ("哪句话*前后矛盾*?", "一眼找出故事里的矛盾"),
  "target": ("用数字凑出*目标*", "+ − × ÷ 与括号自由组合"),
  "match": ("移动*一根*火柴\n让等式成立", "拖动火柴棒,改正错误的算式"),
  "challenge": ("60秒*限时挑战*\n月度排行榜", "所有模式混合出题的速度对决")},
 "es": {
  "home": ("*6 modos* de puzles\nmentales sin fin", "Números, formas, lógica, contradicciones y cerillas"),
  "numbers": ("Descubre la\n*regla oculta*", "La emoción de hallar el patrón"),
  "contradiction": ("¿Qué frase\n*no encaja*?", "Encuentra la contradicción de la historia al instante"),
  "target": ("Crea el *objetivo*\ncon los números", "Combina con + − × ÷ y paréntesis"),
  "match": ("Arréglalo con\n*una cerilla*", "Arrastra una cerilla y corrige la ecuación"),
  "challenge": ("*60 segundos*,\nranking mensual", "Duelo de velocidad con todos los modos mezclados")},
 "fr": {
  "home": ("*6 modes* de casse-têtes\nsans fin", "Nombres, formes, logique, contradictions, allumettes"),
  "numbers": ("Trouve la\n*règle cachée*", "Le plaisir de déceler le motif"),
  "contradiction": ("Quelle phrase\n*ne colle pas* ?", "Repérez la contradiction d'un coup d'œil"),
  "target": ("Atteins la *cible*\navec les nombres", "Combine avec + − × ÷ et parenthèses"),
  "match": ("Corrige avec\n*une allumette*", "Glisse une allumette pour rectifier l'égalité"),
  "challenge": ("*60 secondes* chrono,\nclassement mensuel", "Un duel de vitesse, tous modes mélangés")},
 "de": {
  "home": ("*6 Modi*,\nendlose Denkrätsel", "Zahlen, Formen, Logik, Widersprüche & Streichhölzer"),
  "numbers": ("Finde die\n*versteckte Regel*", "Der Kick, das Muster in der Folge zu erkennen"),
  "contradiction": ("Welcher Satz\n*passt nicht*?", "Finde den Widerspruch in der Geschichte sofort"),
  "target": ("Baue das *Ziel*\naus Zahlen", "Frei kombinieren mit + − × ÷ und Klammern"),
  "match": ("Mit *einem* Streichholz\nkorrigieren", "Ziehe ein Hölzchen und repariere die Gleichung"),
  "challenge": ("*60 Sekunden* Zeitangriff,\nMonatsranking", "Tempo-Duell mit allen Modi gemischt")},
}

def font(spec, size): return ImageFont.truetype(spec[0], size, index=spec[1])

def glow_layer(theme):
    s = 4
    lay = Image.new("RGBA", (W // s, H // s), (0, 0, 0, 0))
    for cx, cy, r, col, a in theme["glows"]:
        l = Image.new("RGBA", lay.size, (0, 0, 0, 0))
        ImageDraw.Draw(l).ellipse([(cx - r) // s, (cy - r * 0.8) // s, (cx + r) // s, (cy + r * 0.8) // s], fill=hx(col) + (a,))
        l = l.filter(ImageFilter.GaussianBlur(r / s * 0.55))
        lay = Image.alpha_composite(lay, l)
    return lay.resize((W, H), Image.BICUBIC)

def background(theme, lang_seed):
    bg = Image.new("RGB", (W, H))
    top, bot = hx("#0B0E14"), hx("#10141C")
    grad = Image.linear_gradient("L").resize((W, H))
    bg = Image.composite(Image.new("RGB", (W, H), bot), Image.new("RGB", (W, H), top), grad)
    bg = Image.alpha_composite(bg.convert("RGBA"), glow_layer(theme))
    # floating glyphs
    gl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(gl)
    rnd = random.Random(lang_seed)
    glyphs = list("+−×÷=?▲●■79π")
    for _ in range(26):
        g = rnd.choice(glyphs)
        size = rnd.randint(70, 190)
        f = ImageFont.truetype(SYMBOL_FONT, size)
        tile = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
        ImageDraw.Draw(tile).text((size, size), g, font=f, fill=(255, 255, 255, rnd.randint(14, 30)), anchor="mm")
        tile = tile.rotate(rnd.randint(-30, 30), resample=Image.BICUBIC)
        gl.alpha_composite(tile, (rnd.randint(-60, W - size), rnd.randint(-40, H - size)))
    return Image.alpha_composite(bg, gl)

def parse(text):
    """-> list of lines, each list of (char, accent)"""
    lines, cur, acc = [], [], False
    for ch in text:
        if ch == "*": acc = not acc
        elif ch == "\n": lines.append(cur); cur = []
        else: cur.append((ch, acc))
    lines.append(cur)
    return lines

def is_cjk(ch): return ord(ch) > 0x2E80
NO_START = set("、。，．,.!?！？)」』）:;")

def wrap(line, f, maxw):
    def width(chars): return f.getlength("".join(c for c, _ in chars))
    if width(line) <= maxw: return [line]
    out, cur, i = [], [], 0
    # tokens: words for latin, chars for cjk
    toks, buf = [], []
    for c in line:
        if is_cjk(c[0]) :
            if buf: toks.append(buf); buf = []
            toks.append([c])
        else:
            buf.append(c)
            if c[0] == " ": toks.append(buf); buf = []
    if buf: toks.append(buf)
    for t in toks:
        if cur and width(cur + t) > maxw and not (t[0][0] in NO_START):
            out.append(cur); cur = []
            if t[0][0] == " ": continue
        cur += t
    if cur: out.append(cur)
    return out

def layout_text(text, spec, maxw, start, maxlines=2):
    size = start
    while size > 40:
        f = font(spec, size)
        lines = []
        for l in parse(text): lines += wrap(l, f, maxw)
        if len(lines) <= maxlines and all(f.getlength("".join(c for c, _ in l).rstrip()) <= maxw for l in lines):
            return f, lines, size
        size -= 4
    return f, lines, size

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def draw_headline(img, lang, text, theme, y):
    hs, _ = FONTS[lang]
    start = 124 if lang in ("ko", "ja", "zh-Hans") else 112
    f, lines, size = layout_text(text, hs, 1320 - 2 * 90, start)
    lh = int(size * 1.22)
    white = Image.new("L", (W, H), 0); acc = Image.new("L", (W, H), 0)
    wd, ad = ImageDraw.Draw(white), ImageDraw.Draw(acc)
    xmin, xmax = W, 0
    for li, line in enumerate(lines):
        full = "".join(c for c, _ in line).rstrip()
        x = (W - f.getlength(full)) / 2
        yy = y + li * lh
        xmin = min(xmin, x); xmax = max(xmax, x + f.getlength(full))
        for ch, a in line[:len(full)]:
            (ad if a else wd).text((x, yy), ch, font=f, fill=255)
            x += f.getlength(ch)  # per-char advance (no kerning; fine for display)
    # soft shadow
    sh = ImageChops.lighter(white, acc).filter(ImageFilter.GaussianBlur(14)).point(lambda v: int(v * 0.55))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 12), sh)
    img.paste(Image.new("RGB", (W, H), (255, 255, 255)), (0, 0), white)
    g = Image.new("RGB", (W, H))
    gd = ImageDraw.Draw(g)
    c1, c2 = hx(theme["grad"][0]), hx(theme["grad"][1])
    span = max(1, xmax - xmin)
    for x in range(W):
        gd.line([(x, 0), (x, H)], fill=lerp(c1, c2, min(1, max(0, (x - xmin) / span))))
    img.paste(g, (0, 0), acc)
    return y + lh * len(lines)

def rr_mask(size, r):
    s = 3
    m = Image.new("L", (size[0] * s, size[1] * s), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] * s - 1, size[1] * s - 1], r * s, fill=255)
    return m.resize(size, Image.LANCZOS)

MATH = set("+−×÷=")

def draw_sub(img, sub, sf, y):
    """Per-char draw; math operators come from Helvetica (CJK fonts lack U+2212)."""
    mf = font(FONTS["latin"][1], sf.size)
    adv = lambda ch: (mf if ch in MATH else sf).getlength(ch)
    x = (W - sum(adv(c) for c in sub)) / 2
    d = ImageDraw.Draw(img)
    for ch in sub:
        d.text((x, y), ch, font=mf if ch in MATH else sf, fill=(190, 198, 216), anchor="la")
        x += adv(ch)

def compose(lang, scene, src, theme):
    img = background(theme, hash((lang, scene)) & 0xFFFF if False else (LANGS.index(lang) * 7 + SCENES.index(scene))).convert("RGB")
    head, sub = T[lang][scene]
    y_end = draw_headline(img, lang, head, theme, 120)
    # sub copy
    _, ss = FONTS[lang]
    sf = None
    for size in range(54, 30, -2):
        sf = font(ss, size)
        if sf.getlength(sub) <= W - 2 * 100: break
    draw_sub(img, sub, sf, y_end + 28)
    # phone
    SW = 1000; SH = round(SW * 2868 / 1320); bz = 16
    PW, PH = SW + 2 * bz, SH + 2 * bz
    px, py = (W - PW) // 2, 700
    scr_r, ph_r = 128, 128 + bz
    # glow + shadow
    gm = Image.new("L", (W, H), 0)
    gm.paste(rr_mask((PW, PH), ph_r), (px, py))
    halo = gm.filter(ImageFilter.MaxFilter(1)).filter(ImageFilter.GaussianBlur(60)).point(lambda v: int(v * 0.55))
    img.paste(Image.new("RGB", (W, H), hx(theme["glow"])), (0, 0), halo)
    shadow = Image.new("L", (W, H), 0); shadow.paste(gm.crop((0, 0, W, H)), (0, 30))
    shadow = shadow.filter(ImageFilter.GaussianBlur(40)).point(lambda v: int(v * 0.6))
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), shadow)
    # frame: bezel with subtle metallic edge
    frame = Image.new("RGB", (PW, PH), (18, 20, 28))
    fd = ImageDraw.Draw(frame)
    edge = rr_mask((PW, PH), ph_r)
    inner = Image.new("L", (PW, PH), 0); inner.paste(rr_mask((PW - 6, PH - 6), ph_r - 3), (3, 3))
    rim = ImageChops.subtract(edge, inner)
    frame.paste(Image.new("RGB", (PW, PH), lerp(hx(theme["glow"]), (255, 255, 255), 0.35)), (0, 0), rim.point(lambda v: int(v * 0.75)))
    img.paste(frame, (px, py), edge)
    # screen
    s = src.convert("RGB")
    sw, sh_ = s.size
    tgt_ar = 1320 / 2868
    if abs(sw / sh_ - tgt_ar) > 0.002:  # stand-in aspect fix: crop bottom / pad
        nh = round(sw / tgt_ar)
        s = s.crop((0, 0, sw, min(sh_, nh))) if sh_ >= nh else s.crop((0, 0, sw, sh_))
        if s.size[1] < nh:
            pad = Image.new("RGB", (sw, nh), s.getpixel((5, s.size[1] - 5))); pad.paste(s, (0, 0)); s = pad
    s = s.resize((SW, SH), Image.LANCZOS)
    img.paste(s, (px + bz, py + bz), rr_mask((SW, SH), scr_r))
    return img.convert("RGB")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--langs", default=",".join(LANGS))
    ap.add_argument("--scenes", default=",".join(SCENES))
    ap.add_argument("--stand-in", action="store_true")
    a = ap.parse_args()
    langs = [("zh-Hans" if l in ("zh", "zh-Hans") else l) for l in a.langs.split(",")]
    scenes = a.scenes.split(",")
    missing = 0
    for lang in langs:
        for scene in scenes:
            if a.stand_in:
                p = os.path.join(SCRATCH, STAND_IN[scene] + ".png")
            else:
                p = os.path.join(RAW, lang, scene + ".png")
                if not os.path.exists(p) and lang == "zh-Hans": p = os.path.join(RAW, "zh", scene + ".png")
            if not os.path.exists(p):
                print("MISSING raw:", p); missing += 1; continue
            img = compose(lang, scene, Image.open(p), THEME[scene])
            for name, (tw, th) in SETS.items():
                out = img
                if (tw, th) != (W, H):
                    nh = round(H * tw / W)
                    out = img.resize((tw, nh), Image.LANCZOS)
                    if nh != th:  # centre-crop the few spare rows (top/bottom only margins)
                        t = (nh - th) // 2
                        out = out.crop((0, t, tw, t + th))
                od = os.path.join(OUT_BASE, name, OUTDIR.get(lang, lang)); os.makedirs(od, exist_ok=True)
                op = os.path.join(od, f"{SCENES.index(scene) + 1:02d}-{scene}.png")
                out.convert("RGB").save(op, icc_profile=None)
            print("wrote", lang, scene)
    sys.exit(1 if missing else 0)

if __name__ == "__main__":
    main()
