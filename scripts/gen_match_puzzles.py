#!/usr/bin/env python3
"""성냥개비 퍼즐(track "match") 생성기: 솔버 + 생성 + 검증 + 병합 + 테스트 벡터.

명세: docs/match-mode-spec.md
사용:
  python3 scripts/gen_match_puzzles.py          # puzzles.json 병합 + match_test_vectors.json 생성
  python3 scripts/gen_match_puzzles.py --dry    # 파일 쓰기 없이 챕터 지표만 출력
시드 고정(SEED)이라 재실행해도 같은 결과. track=="match" 항목만 지우고 다시 추가하므로 재실행 안전.

모델
- 숫자 셀 7슬롯(a..g) / 연산자 셀 3슬롯(hu,hl,v). 비트마스크로 표현.
- 초기 연산자 슬롯(정준형): "=":{hu,hl}  "-":{hu}  "+":{hu,v}.
  (hu<->hl 대칭이라 글리프별 (추가,제거) 비용 집합은 정준형 선택과 무관.)
- 도달 가능 상태 = 초기에 없던 슬롯에 놓인 막대 수(순변위) ≤ moves 이면서 막대 총수 보존.
  셀별 유효 글리프 선택지를 곱해(가지치기) 열거한다. 최소 이동 수 = 순변위.
"""
import json
import math
import os
import random
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
VECTORS = os.path.join(ROOT, "scripts", "match_test_vectors.json")
SEED = 20260714
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]

# ---------------------------------------------------------------- 글리프
DSLOT = {c: 1 << i for i, c in enumerate("abcdefg")}
DIGIT_SETS = {"0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg",
              "5": "acdfg", "6": "acdefg", "7": "abc", "8": "abcdefg", "9": "abcdfg"}
OSLOT = {"hu": 1, "hl": 2, "v": 4}
DIGIT_MASK = {k: sum(DSLOT[c] for c in v) for k, v in DIGIT_SETS.items()}
MASK_DIGIT = {m: k for k, m in DIGIT_MASK.items()}
OP_VARIANTS = {"=": [3], "-": [1, 2], "+": [1 | 4, 2 | 4]}
MASK_OP = {3: "=", 1: "-", 2: "-", 5: "+", 6: "+"}
CANON_OP = {"=": 3, "-": 1, "+": 5}
POP = [bin(i).count("1") for i in range(128)]


def kind_of(ch):
    return "d" if ch.isdigit() else "o"


def initial_masks(chars):
    return [DIGIT_MASK[c] if c.isdigit() else CANON_OP[c] for c in chars]


def glyph(kind, mask):
    return (MASK_DIGIT if kind == "d" else MASK_OP).get(mask)


def evaluate(text):
    """text -> True/False/None(식 형태 아님)."""
    if text.count("=") != 1:
        return None
    sides = text.split("=")
    vals = []
    for s in sides:
        if not re.fullmatch(r"\d+([+-]\d+)*", s):
            return None
        for num in re.findall(r"\d+", s):
            if len(num) > 1 and num[0] == "0":
                return None
        vals.append(eval(s, {"__builtins__": {}}, {}))  # 숫자와 +,- 만 통과한 문자열
    return vals[0] == vals[1]


def cell_options(kind, init, moves):
    """셀의 유효 글리프 선택지: (글자, 마스크, 추가수, 제거수)."""
    out = []
    pool = DIGIT_MASK.items() if kind == "d" else [(c, m) for c, ms in OP_VARIANTS.items() for m in ms]
    for ch, m in pool:
        a = POP[m & ~init]
        r = POP[init & ~m]
        if a <= moves and r <= moves:
            out.append((ch, m, a, r))
    return out


def reach(chars, moves):
    """순변위 <= moves 인 모든 도달 가능 상태 중 글리프가 모두 유효한 것.
    returns dict text -> (최소 이동 수, masks)"""
    inits = initial_masks(chars)
    opts = [cell_options(kind_of(c), i, moves) for c, i in zip(chars, inits)]
    res = {}
    n = len(chars)

    def rec(i, a, r, txt, masks):
        if i == n:
            if a == r:
                t = "".join(txt)
                if t not in res or res[t][0] > a:
                    res[t] = (a, tuple(masks))
            return
        for ch, m, da, dr in opts[i]:
            if a + da > moves or r + dr > moves:
                continue
            txt.append(ch)
            masks.append(m)
            rec(i + 1, a + da, r + dr, txt, masks)
            txt.pop()
            masks.pop()

    rec(0, 0, 0, [], [])
    return res


def solve(chars, moves):
    """해(참인 식) dict text -> (k, masks)."""
    return {t: v for t, v in reach(chars, moves).items() if evaluate(t) is True}


def changes(chars, masks):
    """해에서 막대가 빠지는 셀 / 생기는 셀 (0-based)."""
    inits = initial_masks(chars)
    rem = [i for i, (a, b) in enumerate(zip(inits, masks)) if a & ~b]
    add = [i for i, (a, b) in enumerate(zip(inits, masks)) if b & ~a]
    return rem, add


def move_type(chars, masks):
    rem, add = changes(chars, masks)
    ch = sorted(set(rem) | set(add))
    op = any(not chars[i].isdigit() for i in ch)
    if len(ch) == 1:
        return 0
    if not op:
        return 1
    return 2 if len(ch) <= 2 else 3


def pick_answer(chars, sols):
    """가장 단순한 해: (유형, 변경 셀 수, 이동 수, 텍스트) 최소."""
    best = None
    for t, (k, masks) in sols.items():
        rem, add = changes(chars, masks)
        key = (move_type(chars, masks), len(set(rem) | set(add)), k, t)
        if best is None or key < best[0]:
            best = (key, t, k, masks)
    return best[1], best[2], best[3], best[0][0]


# ---------------------------------------------------------------- 챕터 설정
# n: 셀 수 집합, two: 두 자리 수 포함 필수, types: 허용 유형, nsol: (min,max) 해 개수(텍스트 기준),
# ops2: 연산자 두 개짜리 식(a±b±c=d) 허용
CH = {
    1:  dict(moves=1, n={5}, types={0, 1}, nsol=(2, 9)),
    2:  dict(moves=1, n={5}, types={0, 1, 2}, nsol=(1, 3)),
    3:  dict(moves=1, n={5, 6}, types={1, 2}, nsol=(2, 4)),
    4:  dict(moves=1, n={5, 6}, types={1, 2}, nsol=(1, 2)),
    5:  dict(moves=1, n={6, 7}, two=True, types={0, 1, 2}, nsol=(2, 4)),
    6:  dict(moves=1, n={6, 7}, two=True, types={1, 2}, nsol=(1, 2)),
    7:  dict(moves=1, n={7, 8}, types={0, 1, 2}, nsol=(2, 4)),
    8:  dict(moves=1, n={7, 8}, types={1, 2}, nsol=(1, 1)),
    9:  dict(moves=2, n={6, 7, 8}, types={0, 1, 2, 3}, nsol=(3, 12)),
    10: dict(moves=2, n={6, 7, 8}, types={0, 1, 2, 3}, nsol=(2, 4)),
    11: dict(moves=2, n={6, 7, 8}, types={1, 2, 3}, nsol=(1, 3)),
    12: dict(moves=2, n={7, 8}, types={1, 2, 3}, nsol=(1, 1)),
    13: dict(moves=2, n={7, 8}, types={2, 3}, nsol=(1, 1)),
    14: dict(moves=2, n={7, 8}, types={2, 3}, nsol=(1, 1), maxdigit_big=True),
}


def shapes_for(n):
    """(숫자 길이 목록) 형태. 1 연산자: [la,lb,lc], 2 연산자: [la,lb,lc,ld]."""
    out = []
    d = n - 2
    for la in (1, 2):
        for lb in (1, 2):
            lc = d - la - lb
            if lc in (1, 2):
                out.append((la, lb, lc))
    d2 = n - 3  # 연산자 2개 + '='
    for ls in _comp(d2, 4):
        out.append(tuple(ls))
    return out


def _comp(total, parts):
    res = []

    def rec(i, left, cur):
        if i == parts - 1:
            if left in (1, 2):
                res.append(cur + [left])
            return
        for x in (1, 2):
            if left - x >= 1:
                rec(i + 1, left - x, cur + [x])
    rec(0, total, [])
    return res


def rand_num(rng, ln):
    if ln == 1:
        return rng.randint(0, 9)
    return rng.randint(10, 99)


def gen_true(rng, shape):
    for _ in range(200):
        if len(shape) == 3:
            la, lb, lc = shape
            a, b = rand_num(rng, la), rand_num(rng, lb)
            op = rng.choice("+-")
            c = a + b if op == "+" else a - b
            if c < 0 or len(str(c)) != lc:
                continue
            return f"{a}{op}{b}={c}"
        la, lb, lc, ld = shape
        a, b, c = rand_num(rng, la), rand_num(rng, lb), rand_num(rng, lc)
        o1, o2 = rng.choice("+-"), rng.choice("+-")
        v = a + b if o1 == "+" else a - b
        v = v + c if o2 == "+" else v - c
        if v < 0 or len(str(v)) != ld:
            continue
        return f"{a}{o1}{b}{o2}{c}={v}"
    return None


def has_two_digit(eq):
    return any(len(x) > 1 for x in re.findall(r"\d+", eq))


def diff_score(info):
    return 4 * info["type"] + 2 * (6 - min(info["nsol"], 6)) + info["n"] + (info["moves"] - 1) * 3


SOLVE_CACHE = {}


def solve_cached(text, moves):
    key = (text, moves)
    if key not in SOLVE_CACHE:
        SOLVE_CACHE[key] = solve(list(text), moves)
    return SOLVE_CACHE[key]


def candidates(ch, rng, target_pool, max_t):
    cfg = CH[ch]
    moves = cfg["moves"]
    pool = {}
    shapes = []
    for n in sorted(cfg["n"]):
        shapes += shapes_for(n)
    tried = 0
    while len(pool) < target_pool and tried < max_t:
        tried += 1
        shape = rng.choice(shapes)
        T = gen_true(rng, shape)
        if not T or (cfg.get("two") and not has_two_digit(T)):
            continue
        states = reach(list(T), moves)
        ps = [t for t, (k, _) in states.items()
              if t != T and k >= 1 and evaluate(t) is False
              and re.fullmatch(r"\d+([+-]\d+)*=\d+", t)]
        rng.shuffle(ps)
        for P in ps[:14]:
            if P in pool:
                continue
            sols = solve_cached(P, moves)
            if not sols:
                continue
            mink = min(k for k, _ in sols.values())
            if mink != moves:  # moves=2 챕터는 1이동 이하로 풀리면 안 됨(moves=1은 자동 충족)
                continue
            ns = len(sols)
            if not (cfg["nsol"][0] <= ns <= cfg["nsol"][1]):
                continue
            ans, k, masks, tp = pick_answer(list(P), sols)
            if tp not in cfg["types"]:
                continue
            info = dict(eq=P, T=T, n=len(P), moves=moves, nsol=ns, type=tp,
                        answer=ans, masks=masks, k=k, sols=sorted(sols))
            info["score"] = diff_score(info)
            pool[P] = info
    return list(pool.values())


def choose_six(pool, rng, used):
    pool = [p for p in pool if p["eq"] not in used]
    rng.shuffle(pool)
    pool.sort(key=lambda p: p["score"])
    # 분위수 6개 선택, T 중복 회피
    chosen = []
    usedT = set()
    m = len(pool)
    for i in range(6):
        lo = int(m * i / 6)
        hi = max(lo + 1, int(m * (i + 1) / 6))
        seg = [p for p in pool[lo:hi] if p["T"] not in usedT and p["eq"] not in {c["eq"] for c in chosen}]
        if not seg:
            seg = [p for p in pool if p["T"] not in usedT and p["eq"] not in {c["eq"] for c in chosen}]
        if not seg:
            return None
        p = seg[len(seg) // 2] if i % 2 else seg[0]
        chosen.append(p)
        usedT.add(p["T"])
    chosen.sort(key=lambda p: p["score"])
    return chosen


# ---------------------------------------------------------------- 힌트/설명
def fmt_list(nums, lang):
    sep = "、" if lang in ("ja", "zh") else ", "
    return sep.join(str(x) for x in nums)


T_H = {
    "ko": dict(
        rem=lambda L, p: f"{L}번째 기호에서 막대를 빼야 해요.",
        same=lambda L, p: "빼낸 막대를 같은 기호 안에서 옮겨요.",
        add=lambda L, p: f"막대를 {L}번째 기호에 놓아야 해요.",
        mixed=lambda L, B, p: f"막대를 {L}번째 기호에 놓아야 해요. ({B}번째 기호는 같은 기호 안에서 옮겨요)",
        full=lambda t: f"정답 식: {t}"),
    "en": dict(
        rem=lambda L, p: f"Take matchsticks away from {'symbols' if p else 'symbol'} {L}.",
        same=lambda L, p: "Move that matchstick within the same symbol.",
        add=lambda L, p: f"Place matchsticks on {'symbols' if p else 'symbol'} {L}.",
        mixed=lambda L, B, p: f"Place matchsticks on {'symbols' if p else 'symbol'} {L}. (Symbol {B}: move within the same symbol)",
        full=lambda t: f"Answer: {t}"),
    "ja": dict(
        rem=lambda L, p: f"{L}番目の記号から棒を取り除きます。",
        same=lambda L, p: "取った棒は同じ記号の中で動かします。",
        add=lambda L, p: f"{L}番目の記号に棒を置きます。",
        mixed=lambda L, B, p: f"{L}番目の記号に棒を置きます。（{B}番目は同じ記号の中で動かします）",
        full=lambda t: f"正解の式：{t}"),
    "zh": dict(
        rem=lambda L, p: f"需要从第{L}个符号上拿走火柴棒。",
        same=lambda L, p: "把拿走的火柴棒在同一个符号内移动。",
        add=lambda L, p: f"需要把火柴棒放到第{L}个符号上。",
        mixed=lambda L, B, p: f"需要把火柴棒放到第{L}个符号上。（第{B}个符号在自身内部移动）",
        full=lambda t: f"正确算式：{t}"),
    "es": dict(
        rem=lambda L, p: f"Quita cerillas {'de los símbolos' if p else 'del símbolo'} {L}.",
        same=lambda L, p: "Mueve la cerilla dentro del mismo símbolo.",
        add=lambda L, p: f"Coloca cerillas {'en los símbolos' if p else 'en el símbolo'} {L}.",
        mixed=lambda L, B, p: f"Coloca cerillas {'en los símbolos' if p else 'en el símbolo'} {L}. (Símbolo {B}: se mueve dentro del mismo símbolo)",
        full=lambda t: f"Ecuación correcta: {t}"),
    "fr": dict(
        rem=lambda L, p: f"Retirez des allumettes {'des symboles' if p else 'du symbole'} {L}.",
        same=lambda L, p: "Déplacez l'allumette à l'intérieur du même symbole.",
        add=lambda L, p: f"Placez des allumettes {'sur les symboles' if p else 'sur le symbole'} {L}.",
        mixed=lambda L, B, p: f"Placez des allumettes {'sur les symboles' if p else 'sur le symbole'} {L}. (Symbole {B} : déplacement dans le même symbole)",
        full=lambda t: f"Équation correcte : {t}"),
    "de": dict(
        rem=lambda L, p: f"Nimm Streichhölzer {'von den Symbolen' if p else 'vom Symbol'} {L} weg.",
        same=lambda L, p: "Verschiebe das Streichholz innerhalb desselben Symbols.",
        add=lambda L, p: f"Lege Streichhölzer {'auf die Symbole' if p else 'auf das Symbol'} {L}.",
        mixed=lambda L, B, p: f"Lege Streichhölzer {'auf die Symbole' if p else 'auf das Symbol'} {L}. (Symbol {B}: innerhalb desselben Symbols verschieben)",
        full=lambda t: f"Richtige Gleichung: {t}"),
}


def pretty(t):
    return t.replace("-", "−")


def make_hints(chars, answer, masks):
    rem, add = changes(chars, masks)
    R = [i + 1 for i in rem]
    A = [i + 1 for i in add]
    B = [i + 1 for i in sorted(set(rem) & set(add))]
    hints = {}
    for lang in LANGS:
        t = T_H[lang]
        h1 = t["rem"](fmt_list(R, lang), len(R) > 1)
        if R == A == B:
            h2 = t["same"](None, False)
        elif not B:
            h2 = t["add"](fmt_list(A, lang), len(A) > 1)
        else:
            h2 = t["mixed"](fmt_list(A, lang), fmt_list(B, lang), len(A) > 1)
        hints[lang] = [h1, h2, t["full"](pretty(answer))]
    return hints


def make_explanation(eq, answer):
    s = f"{pretty(eq)} → {pretty(answer)}"
    return {lang: s for lang in LANGS}


# ---------------------------------------------------------------- 테스트 벡터
def slotstr(kind, mask):
    if kind == "d":
        return "".join(c for c in "abcdefg" if mask & DSLOT[c])
    return ",".join(n for n in ("hu", "hl", "v") if mask & OSLOT[n])


def parse_slot(kind, s):
    m = 0
    if kind == "d":
        for c in s:
            m |= DSLOT[c]
    elif s:
        for n in s.split(","):
            m |= OSLOT[n]
    return m


def tok(t):
    """작성용 토큰 -> (kind, slotstring). '6','+','=' 등 글리프 문자는 정준형, 'd:abx' 'o:hu,hl,v' 는 원시."""
    if t.startswith("d:") or t.startswith("o:"):
        return t[0], t[2:]
    if t.isdigit():
        return "d", DIGIT_SETS[t]
    if t == "+":
        return "o", "hu,v"
    if t == "+L":
        return "o", "hl,v"
    if t == "-":
        return "o", "hu"
    if t == "-L":
        return "o", "hl"
    if t == "=":
        return "o", "hu,hl"
    raise ValueError(t)


def eval_cases():
    # (tokens, valid, text, truth)
    C = [
        ("6 + 4 = 1 0", True, "6+4=10", True),
        ("6 + 4 = 4", True, "6+4=4", False),
        ("9 - 3 = 6", True, "9-3=6", True),
        ("9 -L 3 = 6", True, "9-3=6", True),
        ("1 +L 1 = 2", True, "1+1=2", True),
        ("0 + 0 = 0", True, "0+0=0", True),
        ("1 0 - 1 = 9", True, "10-1=9", True),
        ("9 9 - 9 = 9 0", True, "99-9=90", True),
        ("1 2 - 3 = 9", True, "12-3=9", True),
        ("2 + 3 + 1 = 6", True, "2+3+1=6", True),
        ("9 - 3 - 3 = 3", True, "9-3-3=3", True),
        ("3 - 8 = 5 - 1 0", True, "3-8=5-10", True),
        ("1 - 9 = 2 - 1 0", True, "1-9=2-10", True),
        ("2 - 5 = 3", True, "2-5=3", False),
        ("1 = 1", True, "1=1", True),
        ("7 = 8", True, "7=8", False),
        ("8 + 8 = 1 6", True, "8+8=16", True),
        ("5 + 5 = 9 1", True, "5+5=91", False),
        # 식 형태가 아님(truth null)
        ("0 1 + 1 = 2", True, "01+1=2", None),
        ("0 + 0 = 0 0", True, "0+0=00", None),
        ("0 0 + 1 = 1", True, "00+1=1", None),
        ("6 + 4 + 4", True, "6+4+4", None),
        ("1 = 1 = 1", True, "1=1=1", None),
        ("= 1 + 1", True, "=1+1", None),
        ("1 + 1 = 2 +", True, "1+1=2+", None),
        ("1 + + 1 = 2", True, "1++1=2", None),
        ("+ 1 = 1", True, "+1=1", None),
        ("1 + 1 =", True, "1+1=", None),
        ("5 - 8 = - 3", True, "5-8=-3", None),
        ("1 + - 1 = 0", True, "1+-1=0", None),
        ("1 1 =", True, "11=", None),
        # 무효 글리프
        ("d:abd + 1 = 2", False, None, None),
        ("d:bcdef + 1 = 2", False, None, None),
        ("d:abcf + 1 = 2", False, None, None),
        ("d: + 1 = 2", False, None, None),
        ("d:abcdefg + d:acdeg = 2", False, None, None),
        ("1 o:v 1 = 2", False, None, None),
        ("1 o:hu,hl,v 1 = 2", False, None, None),
        ("1 o: 1 = 2", False, None, None),
        ("1 + 1 o:hu,v,hl 2", False, None, None),
        ("1 + 1 o:hu 2", True, "1+1-2", None),
        ("1 + 1 o:hl,hu 2", True, "1+1=2", True),
        ("2 + 2 = d:abcdefg", True, "2+2=8", False),
        ("1 o:hu,v 1 = 2", True, "1+1=2", True),
        ("1 o:hl,v 1 = 2", True, "1+1=2", True),
    ]
    out = []
    for toks, valid, text, truth in C:
        ts = []
        for t in toks.split(" "):
            ts.append(t)
        # 'd:' 'o:' 토큰 하나가 빈 슬롯일 수 있어 공백 분리 허용
        cells, kinds = [], ""
        for t in ts:
            k, s = tok(t)
            kinds += k
            cells.append(s)
        out.append({"kinds": kinds, "cells": cells, "valid": valid, "text": text, "truth": truth})
    return out


def check_eval_cases(cases):
    """손으로 쓴 기대값이 생성기 구현과 일치하는지 자체 점검."""
    for c in cases:
        txt, ok = [], True
        for k, s in zip(c["kinds"], c["cells"]):
            g = glyph(k, parse_slot(k, s))
            if g is None:
                ok = False
                break
            txt.append(g)
        assert ok == c["valid"], c
        if ok:
            t = "".join(txt)
            assert t == c["text"], (t, c)
            assert evaluate(t) == c["truth"], (t, c)


def displacement_cases():
    init_chars = list("6+4=4")
    kinds = "".join(kind_of(c) for c in init_chars)
    init = initial_masks(init_chars)
    S = lambda masks: [slotstr(k, m) for k, m in zip(kinds, masks)]
    # (moves [(fromCell,fromSlot,toCell,toSlot)], 기대 순변위, 설명)
    seqs = [
        ([], 0, "no moves"),
        ([(0, "e", 4, "a")], 1, "one stick moved to another cell"),
        ([(0, "e", 4, "a"), (4, "a", 0, "e")], 0, "moved and moved back"),
        ([(0, "e", 4, "a"), (4, "a", 4, "d")], 1, "same stick moved twice counts once"),
        ([(0, "e", 4, "a"), (2, "f", 2, "a")], 2, "two different sticks"),
        ([(4, "b", 4, "a"), (4, "c", 4, "b")], 1, "slot initially occupied does not count"),
        ([(4, "b", 4, "a"), (4, "c", 4, "b"), (4, "a", 4, "c")], 0, "rotation back to the start"),
        ([(0, "e", 4, "a"), (2, "f", 2, "a"), (4, "g", 4, "d")], 3, "three displaced sticks (exceeds moves=2)"),
        ([(1, "v", 1, "hl")], 1, "operator: plus -> equals shape"),
        ([(1, "v", 3, "v"), (3, "v", 1, "v")], 0, "operator stick moved and returned"),
        ([(1, "v", 3, "v")], 1, "plus stick onto equals cell"),
        ([(0, "e", 0, "b"), (0, "c", 0, "e")], 1, "within one cell: e->b then c->e; e was initially occupied so only b counts"),
    ]
    out = []
    for mv, expected, note in seqs:
        cur = list(init)
        ok = True
        for fc, fs, tc, ts in mv:
            fb = (DSLOT if kinds[fc] == "d" else OSLOT)[fs]
            tb = (DSLOT if kinds[tc] == "d" else OSLOT)[ts]
            if not (cur[fc] & fb) or (cur[tc] & tb):
                ok = False
                break
            cur[fc] &= ~fb
            cur[tc] |= tb
        if not ok:
            continue  # 불법 이동 시퀀스는 벡터에서 제외
        disp = sum(POP_(cur[i] & ~init[i], kinds[i]) for i in range(len(init)))
        assert disp == expected, (mv, disp, expected)
        out.append({"initial": S(init), "kinds": kinds, "moves": [list(m) for m in mv],
                    "current": S(cur), "displacement": disp, "note": note})
    return out


def POP_(mask, kind):
    return bin(mask).count("1")


def build_vectors(puzzles):
    evals = eval_cases()
    check_eval_cases(evals)
    disp = displacement_cases()
    rng = random.Random(SEED + 7)
    samples = []
    # 챕터마다 분산해서 10개
    pick = [p for p in puzzles]
    idx = sorted(rng.sample(range(len(pick)), 10))
    for i in idx:
        p = pick[i]
        sols = solve(list(p["equation"]), p["moves"])
        samples.append({
            "id": p["id"], "equation": p["equation"], "moves": p["moves"], "solution": p["answer"],
            "ok": True,
            "all_solutions": sorted(sols),
            "min_moves": min(k for k, _ in sols.values()),
        })
    fmt = {
        "description": "match mode (docs/match-mode-spec.md) cross-platform unit-test vectors, generated by scripts/gen_match_puzzles.py",
        "cell_representation": "A cell is a slot-set string. kinds[i] is 'd' (digit cell, slots a..g, written as sorted letters e.g. 'acdefg') or 'o' (operator cell, slots hu,hl,v written comma-joined e.g. 'hu,v'). Empty set = ''.",
        "eval": "{kinds,cells,valid,text,truth}: valid=false when any cell is not a valid glyph (then text=null,truth=null). Otherwise text=joined glyph chars with + - = and truth=true/false, or null when the text is not a well-formed equation (not exactly one '=', empty side, leading/trailing/consecutive operator, leading zero in a multi-digit number).",
        "displacement": "{initial,kinds,moves,current,displacement}: moves=[[fromCell,fromSlot,toCell,toSlot],...] applied in order to initial gives current (0-based cell index). displacement = number of slots occupied in current that were NOT occupied in initial. Test: displacement(initial,current)==expected; also for moves.",
        "moves": "{id,equation,moves,solution,ok,all_solutions,min_moves}: equation is the puzzle's cell chars, with each operator in canonical start slots '=':hu,hl  '-':hu  '+':hu,v. A solver must find the solution text reachable with net displacement <= moves (ok=true); all_solutions is the complete set of reachable true equation texts (min displacement per text <= moves); min_moves is the smallest displacement among them. Minimum displacements are independent of the choice of canonical op slots (hu<->hl symmetry).",
        "glyphs": {"digits": DIGIT_SETS, "operators": {"=": ["hu,hl"], "-": ["hu", "hl"], "+": ["hu,v", "hl,v"]}},
    }
    return {"format": fmt, "eval": evals, "displacement": disp, "moves": samples}


# ---------------------------------------------------------------- 메인
def build_all(dry=False):
    rng = random.Random(SEED)
    used = set()
    result = []
    metrics = {}
    for ch in range(1, 15):
        cfg = CH[ch]
        pool_target = 300
        pool = candidates(ch, rng, pool_target, 4000)
        chosen = choose_six(pool, rng, used)
        tries = 0
        while chosen is None and tries < 3:
            pool = candidates(ch, rng, pool_target * 2, 12000)
            chosen = choose_six(pool, rng, used)
            tries += 1
        if chosen is None:
            raise SystemExit(f"chapter {ch}: could not fill 6 puzzles (pool {len(pool)})")
        metrics[ch] = (len(pool), chosen)
        for o, c in enumerate(chosen, 1):
            used.add(c["eq"])
            chars = list(c["eq"])
            result.append({
                "id": f"mt{ch}_s{o:02d}", "type": "match", "track": "match",
                "chapter": ch, "order": o,
                "equation": chars, "moves": c["moves"], "answer": c["answer"],
                "inputType": "choices", "choices": None,
                "hints": make_hints(chars, c["answer"], c["masks"]),
                "explanation": make_explanation(c["eq"], c["answer"]),
            })
    print_table(metrics)
    if dry:
        return
    with open(PUZZLES, encoding="utf8") as f:
        data = json.load(f)
    others = [p for p in data if p.get("track") != "match"]
    data = others + result
    with open(PUZZLES, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    print(f"wrote {len(result)} match puzzles; total {len(data)}")
    vec = build_vectors(result)
    with open(VECTORS, "w", encoding="utf8") as f:
        f.write(json.dumps(vec, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {VECTORS}: {len(vec['eval'])} eval, {len(vec['displacement'])} displacement, {len(vec['moves'])} puzzles")


TN = {0: "same", 1: "cross", 2: "op", 3: "op3+"}


def print_table(metrics):
    print("ch | mv | cells | nsol avg | types | pool | equations")
    for ch, (np_, chosen) in metrics.items():
        ns = sum(c["nsol"] for c in chosen) / 6
        cells = sorted({c["n"] for c in chosen})
        types = {}
        for c in chosen:
            types[TN[c["type"]]] = types.get(TN[c["type"]], 0) + 1
        print(f"{ch:2d} | {CH[ch]['moves']}  | {cells} | {ns:.2f} | {types} | {np_} | "
              + " ".join(f"{c['eq']}->{c['answer']}({c['nsol']})" for c in chosen))


if __name__ == "__main__":
    build_all(dry="--dry" in sys.argv)
