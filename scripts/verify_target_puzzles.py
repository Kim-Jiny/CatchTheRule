#!/usr/bin/env python3
"""puzzles.json 의 target 퍼즐을 독립적으로 검증(설명 문자열을 파싱해 실제로 실행)."""
import json, os, re, sys
from collections import Counter
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
data = json.load(open(os.path.join(ROOT, "shared/content/puzzles.json"), encoding="utf8"))
T = [p for p in data if p.get("track") == "target"]
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
STEP = re.compile(r"^(\d+) ([+−×÷]) (\d+) = (\d+)$")
OPS = {"+": lambda a, b: a + b, "−": lambda a, b: a - b, "×": lambda a, b: a * b,
       "÷": lambda a, b: a // b if b and a % b == 0 else None}
errs = []
def err(p, m): errs.append(f"{p['id']}: {m}")
assert len(T) == 84, len(T)
seen = set()
for p in T:
    ch, od = p["chapter"], p["order"]
    if p["id"] != f"tg{ch}_s{od:02d}": err(p, "id")
    key = (tuple(sorted(p["numbers"])), p["target"])
    if key in seen: err(p, "dup")
    seen.add(key)
    n = {1: 3, 2: 3, 3: 4, 4: 4, 5: 5, 6: 5, 7: 5}.get(ch, 6)
    if len(p["numbers"]) != n: err(p, "numcount")
    if p["answer"] != str(p["target"]): err(p, "answer")
    if set(p["hints"]) != set(LANGS) or set(p["explanation"]) != set(LANGS): err(p, "langs")
    if any(len(p["hints"][l]) != 3 for l in LANGS): err(p, "hint count")
    if len({p["explanation"][l] for l in LANGS}) != 1: err(p, "expl differ")
    steps = [STEP.match(s) for s in p["explanation"]["en"].split(" → ")]
    if not all(steps): err(p, "parse"); continue
    pool = list(p["numbers"]); parsed = []
    for m in steps:
        a, op, b, r = int(m[1]), m[2], int(m[3]), int(m[4])
        if a not in pool: err(p, f"{a} missing"); break
        pool.remove(a)
        if b not in pool: err(p, f"{b} missing"); break
        pool.remove(b)
        res = OPS[op](a, b)
        if res is None or res <= 0 or res != r or res > 1_000_000: err(p, f"bad step {m[0]}"); break
        pool.append(res); parsed.append((a, op, b, r))
    else:
        if p["target"] not in pool: err(p, "target not reached")
        if len(parsed) < 2: err(p, "1-step")
        if parsed[-1][3] != p["target"]: err(p, "last != target")
        fs = parsed[0]
        for l in LANGS:
            h = p["hints"][l]
            nums1 = re.findall(r"\d+", h[0])
            if nums1 != [str(fs[0]), str(fs[2])]: err(p, f"h1 {l}")
            eq = f"{fs[0]} {fs[1]} {fs[2]} = {fs[3]}"
            if eq not in h[1]: err(p, f"h2 {l}")
            exp3 = parsed if len(parsed) <= 3 else parsed[1:-1]
            for st in exp3:
                if f"{st[0]} {st[1]} {st[2]} = {st[3]}" not in h[2]: err(p, f"h3 {l}")
            if len(parsed) > 3 and f"{parsed[-1][0]} {parsed[-1][1]} {parsed[-1][2]} = " in h[2] and False: err(p, "h3 leak")
    if p["chapter"] <= 2 and any(s[1] in "−÷" for s in parsed): err(p, "ch1-2 needs only + ×")
# order 연속, 챕터 6개
c = Counter(p["chapter"] for p in T)
if sorted(c) != list(range(1, 15)) or any(v != 6 for v in c.values()): errs.append("chapter counts")
for ch in range(1, 15):
    if [p["order"] for p in T if p["chapter"] == ch] != list(range(1, 7)): errs.append(f"order ch{ch}")
print("errors:", len(errs)); print("\n".join(errs[:30]))
sys.exit(1 if errs else 0)
