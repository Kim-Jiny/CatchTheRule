#!/usr/bin/env python3
"""성냥개비 퍼즐 독립 검증기 (생성기와 코드 공유 없음).

생성기는 셀별 선택지 곱(DP)으로 열거하지만, 여기서는 실제 "막대 하나를 빼서 빈 슬롯에 놓기"를
BFS 로 moves 번까지 수행해 도달 상태를 구한다. 판정은 문자열 → 토큰 파서로 별도 구현.
실행: python3 scripts/verify_match_puzzles.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZ = os.path.join(ROOT, "shared", "content", "puzzles.json")
VEC = os.path.join(ROOT, "scripts", "match_test_vectors.json")
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]

DIG = {"0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg", "5": "acdfg",
       "6": "acdefg", "7": "abc", "8": "abcdefg", "9": "abcdfg"}
DIG_REV = {frozenset(v): k for k, v in DIG.items()}
OP_REV = {frozenset(["hu", "hl"]): "=", frozenset(["hu"]): "-", frozenset(["hl"]): "-",
          frozenset(["hu", "v"]): "+", frozenset(["hl", "v"]): "+"}
OP_START = {"=": ["hu", "hl"], "-": ["hu"], "+": ["hu", "v"]}
DIG_SLOTS = list("abcdefg")
OP_SLOTS = ["hu", "hl", "v"]


def start_state(eq):
    s = set()
    for i, ch in enumerate(eq):
        for sl in (DIG[ch] if ch.isdigit() else OP_START[ch]):
            s.add((i, sl))
    return frozenset(s)


def all_slots(eq):
    return [(i, sl) for i, ch in enumerate(eq) for sl in (DIG_SLOTS if ch.isdigit() else OP_SLOTS)]


def to_text(eq, state):
    out = []
    for i, ch in enumerate(eq):
        cell = frozenset(sl for (j, sl) in state if j == i)
        g = (DIG_REV if ch.isdigit() else OP_REV).get(cell)
        if g is None:
            return None
        out.append(g)
    return "".join(out)


def truth(text):
    """True/False/None(not well-formed)"""
    parts = re.split(r"(=)", text)
    if parts.count("=") != 1:
        return None
    toks = re.findall(r"\d+|[+\-=]", text)
    if "".join(toks) != text:
        return None
    side, sides = [], []
    for t in toks:
        if t == "=":
            sides.append(side)
            side = []
        else:
            side.append(t)
    sides.append(side)
    vals = []
    for sd in sides:
        if not sd or len(sd) % 2 == 0:
            return None
        total = None
        sign = 1
        for idx, t in enumerate(sd):
            if idx % 2 == 0:
                if not t.isdigit() or (len(t) > 1 and t[0] == "0"):
                    return None
                n = int(t)
                total = n if total is None else total + sign * n
            else:
                if t not in "+-":
                    return None
                sign = 1 if t == "+" else -1
        vals.append(total)
    return vals[0] == vals[1]


def bfs(eq, moves):
    """returns dict state -> min number of single-stick moves (<= moves)."""
    slots = all_slots(eq)
    start = start_state(eq)
    dist = {start: 0}
    frontier = [start]
    for d in range(1, moves + 1):
        nxt = []
        for st in frontier:
            empties = [s for s in slots if s not in st]
            for rm in st:
                base = st - {rm}
                for ad in empties:
                    if ad == rm:
                        continue
                    ns = base | {ad}
                    if ns not in dist:
                        dist[ns] = d
                        nxt.append(ns)
        frontier = nxt
    return dist


def nums(s):
    # 「 」 안의 글자와 바로 뒤의 "(막대 N개)" 같은 개수 표기는 칸 번호가 아니므로 제외하고 번호만 추출
    s = re.sub(r"「[^」]*」(\s*[（(][^）)]*[）)])?", " ", s)
    return [int(x) for x in re.findall(r"\d+", s)]


def main():
    data = json.load(open(PUZ, encoding="utf8"))
    ms = [p for p in data if p.get("track") == "match"]
    errs = []

    def err(p, m):
        errs.append(f"{p['id']}: {m}")

    if len(ms) != 84:
        errs.append(f"expected 84 match puzzles, got {len(ms)}")
    seen_eq, seen_id = set(), set()
    for p in ms:
        eq = p["equation"]
        t0 = "".join(eq)
        if p["id"] in seen_id:
            err(p, "dup id")
        seen_id.add(p["id"])
        if t0 in seen_eq:
            err(p, "dup equation")
        seen_eq.add(t0)
        ch, od = p["chapter"], p["order"]
        if p["id"] != f"mt{ch}_s{od:02d}":
            err(p, "id mismatch")
        if p["type"] != "match" or p["track"] != "match":
            err(p, "type/track")
        want_moves = 1 if ch <= 8 else 2
        if p["moves"] != want_moves:
            err(p, f"moves {p['moves']} != {want_moves}")
        if not (5 <= len(eq) <= 8):
            err(p, f"cell count {len(eq)}")
        if any(len(c) != 1 or c not in "0123456789+-=" for c in eq):
            err(p, "bad cell char")
            continue
        if truth(t0) is not False:
            err(p, f"initial must be well-formed false, got {truth(t0)}")
        dist = bfs(eq, p["moves"])
        # 모든 도달 상태 중 유효·참인 텍스트
        sol = {}
        for st, d in dist.items():
            t = to_text(eq, st)
            if t is not None and truth(t) is True:
                sol[t] = min(d, sol.get(t, 9))
        if not sol:
            err(p, "no solution")
            continue
        if p["answer"] not in sol:
            err(p, f"answer {p['answer']} not reachable true equation")
            continue
        if p["moves"] == 2 and min(sol.values()) < 2:
            err(p, "solvable in 1 move but moves=2")
        if min(sol.values()) != p["moves"]:
            err(p, f"min moves {min(sol.values())} != moves {p['moves']}")
        # 힌트 일치: answer 로 가는 최소 상태 중 하나가 힌트의 셀 번호와 일치
        best = [st for st, d in dist.items() if to_text(eq, st) == p["answer"] and d == sol[p["answer"]]]
        start = start_state(eq)
        ok_h = False
        for st in best:
            rem = sorted({i + 1 for (i, _) in start - st})
            add = sorted({i + 1 for (i, _) in st - start})
            both = set(rem) & set(add)
            ok = True
            for lang in LANGS:
                h = p["hints"][lang]
                if sorted(nums(h[0])) != rem:
                    ok = False
                exp2 = [] if (rem == add and set(rem) == both) else add
                if sorted(set(nums(h[1]))) != exp2:
                    ok = False
                if p["answer"].replace("-", "−") not in h[2]:
                    ok = False
            ok_h = ok_h or ok
        if not ok_h:
            err(p, "hint cell numbers do not match solution")
        # 언어/힌트 개수
        for lang in LANGS:
            h = p["hints"].get(lang)
            if not h or len(h) != 3 or any(not x.strip() for x in h):
                err(p, f"hints[{lang}] must be 3 non-empty")
            if not p["explanation"].get(lang):
                err(p, f"explanation[{lang}] missing")
        if p["explanation"]["ko"] != f"{t0.replace('-', chr(0x2212))} → {p['answer'].replace('-', chr(0x2212))}":
            err(p, "explanation format")
        if (p["inputType"], p["choices"]) != ("choices", None):
            err(p, "inputType/choices")
    for ch in range(1, 15):
        ords = sorted(p["order"] for p in ms if p["chapter"] == ch)
        if ords != list(range(1, 7)):
            errs.append(f"chapter {ch} orders {ords}")
    # 비-match 퍼즐 변경 금지는 gen 단계에서 원본과 바이트 비교(README 참조)
    # 테스트 벡터 재검증
    if os.path.exists(VEC):
        v = json.load(open(VEC, encoding="utf8"))
        for c in v["eval"]:
            txt, ok = "", True
            for k, s in zip(c["kinds"], c["cells"]):
                cell = frozenset(x for x in (s.split(",") if k == "o" else list(s)) if x)
                g = (DIG_REV if k == "d" else OP_REV).get(cell)
                if g is None:
                    ok = False
                    break
                txt += g
            if ok != c["valid"] or (ok and (txt != c["text"] or truth(txt) != c["truth"])):
                errs.append(f"vector eval mismatch {c}")
        for c in v["displacement"]:
            ini = {(i, sl) for i, (k, s) in enumerate(zip(c["kinds"], c["initial"]))
                   for sl in (s.split(",") if k == "o" else list(s)) if sl}
            cur = {(i, sl) for i, (k, s) in enumerate(zip(c["kinds"], c["current"]))
                   for sl in (s.split(",") if k == "o" else list(s)) if sl}
            if len(cur - ini) != c["displacement"]:
                errs.append(f"vector displacement mismatch {c}")
        for m in v["moves"]:
            dist = bfs(m["equation"], m["moves"])
            ts = {to_text(m["equation"], st) for st in dist}
            if m["solution"] not in ts or truth(m["solution"]) is not True:
                errs.append(f"vector puzzle {m['id']} bad solution")
            allsol = sorted(t for t in ts if t and truth(t) is True)
            if allsol != m["all_solutions"]:
                errs.append(f"vector puzzle {m['id']} all_solutions mismatch")
        print(f"vectors: {len(v['eval'])} eval, {len(v['displacement'])} disp, {len(v['moves'])} puzzles")
    print(f"checked {len(ms)} match puzzles")
    if errs:
        print("FAIL")
        for e in errs[:50]:
            print(" ", e)
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
