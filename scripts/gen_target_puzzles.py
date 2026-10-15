#!/usr/bin/env python3
"""숫자 만들기(track:"target") 퍼즐 생성기: 솔버 + 생성 + 검증 + puzzles.json 병합.

재실행 안전: puzzles.json 에서 track=="target" 항목만 지우고 다시 추가한다(시드 고정, 결과 동일).
실행:  python3 scripts/gen_target_puzzles.py [--dry]   (--dry: 파일을 쓰지 않고 표만 출력)
"""
import json, math, os, random, sys
from itertools import combinations
from multiprocessing import Pool

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
LIMIT = 1_000_000
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]

# ---------------------------------------------------------------- solver
def popcount(x):
    return bin(x).count("1")


def solve_all(nums, ops):
    """부분집합(mask)별로 만들 수 있는 값 -> 식 트리 개수. ops 는 '+-*/' 부분집합 문자열.
    ×1, ÷1 같은 무의미한 연산은 제외. 교환법칙 동치는 (a,b) 비순서쌍으로 한 번만 센다."""
    n = len(nums)
    full = (1 << n) - 1
    D = [None] * (full + 1)
    for i, v in enumerate(nums):
        D[1 << i] = {v: 1}
    for mask in range(1, full + 1):
        if D[mask] is not None:
            continue
        low = mask & -mask
        res = {}
        sub = (mask - 1) & mask
        while sub:
            other = mask ^ sub
            if sub & low and other:
                A, B = D[sub], D[other]
                for a, ca in A.items():
                    for b, cb in B.items():
                        c = ca * cb
                        if "+" in ops:
                            r = a + b
                            if r <= LIMIT:
                                res[r] = res.get(r, 0) + c
                        if "*" in ops and a != 1 and b != 1:
                            r = a * b
                            if r <= LIMIT:
                                res[r] = res.get(r, 0) + c
                        if a != b:
                            hi, lo = (a, b) if a > b else (b, a)
                            if "-" in ops:
                                r = hi - lo
                                res[r] = res.get(r, 0) + c
                            if "/" in ops and lo != 1 and hi % lo == 0:
                                r = hi // lo
                                res[r] = res.get(r, 0) + c
                        elif "/" in ops and a != 1:
                            res[1] = res.get(1, 0) + c
            sub = (sub - 1) & mask
        D[mask] = res
    return D


def analyze(nums):
    n = len(nums)
    D = solve_all(nums, "+-*/")
    reach = {}
    for ops in ("+*", "+*/", "+-*"):  # 각각 −없이, −없이(÷포함)... 아래에서 의미 정리
        Dm = solve_all(nums, ops)
        s = set()
        for m in range(1, len(Dm)):
            s.update(Dm[m])
        reach[ops] = s
    out = {}
    byv = {}
    for m in range(1, len(D)):
        st = popcount(m) - 1
        for v, c in D[m].items():
            e = byv.setdefault(v, {})
            e[st] = e.get(st, 0) + c
    for v, e in byv.items():
        mn = min(e)
        out[v] = (mn, e[mn], sum(e.values()),
                  v in reach["+*"], v in reach["+*/"], v in reach["+-*"])
    return out  # (minsteps, nmin, total, solvable_pm, solvable_noSub, solvable_noDiv)


def analyze_job(nums):
    return nums, analyze(list(nums))


def find_min_solutions(nums, target, limit=5000):
    """최단 단계 해법(식 트리)들을 열거. 트리: 정수 또는 (op, L, R)."""
    n = len(nums)
    full = (1 << n) - 1
    D = solve_all(nums, "+-*/")
    best = min(popcount(m) for m in range(1, full + 1) if target in D[m])
    memo = {}

    def gen(mask, val):
        key = (mask, val)
        if key in memo:
            return memo[key]
        if popcount(mask) == 1:
            i = mask.bit_length() - 1
            memo[key] = [nums[i]] if nums[i] == val else []
            return memo[key]
        out = []
        low = mask & -mask
        sub = (mask - 1) & mask
        while sub and len(out) < limit:
            other = mask ^ sub
            if sub & low and other:
                for a in D[sub]:
                    for b in D[other]:
                        cs = []  # (op, 왼쪽(값,mask), 오른쪽(값,mask))
                        if a + b == val:
                            cs.append(("+", (a, sub), (b, other)))
                        if a != 1 and b != 1 and a * b == val:
                            cs.append(("*", (a, sub), (b, other)))
                        if a - b == val and a > b:
                            cs.append(("-", (a, sub), (b, other)))
                        if b - a == val and b > a:
                            cs.append(("-", (b, other), (a, sub)))
                        if b != 1 and a % b == 0 and a // b == val and (a != b or val == 1):
                            cs.append(("/", (a, sub), (b, other)))
                        if a != 1 and a != b and b % a == 0 and b // a == val:
                            cs.append(("/", (b, other), (a, sub)))
                        for op, (lv, lm), (rv, rm) in cs:
                            for l in gen(lm, lv):
                                for r in gen(rm, rv):
                                    out.append((op, l, r))
            sub = (sub - 1) & mask
        memo[key] = out
        return out

    sols = []
    for m in range(1, full + 1):
        if popcount(m) == best and target in D[m]:
            sols.extend(gen(m, target))
    return sols


def steps_of(tree):
    """후위 순회 단계 목록 [(a,op,b,result)]"""
    out = []

    def val(t):
        if isinstance(t, int):
            return t
        op, l, r = t
        a, b = val(l), val(r)
        res = {"+": a + b, "*": a * b, "-": a - b, "/": a // b if b else 0}[op]
        out.append((a, op, b, res))
        return res

    val(tree)
    return out


SYM = {"+": "+", "-": "−", "*": "×", "/": "÷"}


def fmt(step):
    a, op, b, r = step
    return f"{a} {SYM[op]} {b} = {r}"


# ---------------------------------------------------------------- chapters
SMALL = list(range(1, 10))
LARGE = [25, 50, 75, 100]

# 챕터 설정: n, 재료 생성, 목표 범위, 최소 최단단계, 연산 요구, 해법 수 상한(min-step 트리 수)
# req: 'pm'=+×만으로 가능, 'sub'=−가 꼭 필요, None=제한 없음. div: 슬롯(1..6)에서 ÷ 필수
CH = {
    1: dict(n=3, pool=lambda r: r.sample(range(1, 10), 3), tg=(8, 30), minst=2, req="pm", div=[]),
    2: dict(n=3, pool=lambda r: r.sample(range(2, 13), 3), tg=(20, 80), minst=2, req="pm", div=[]),
    3: dict(n=4, pool=lambda r: r.sample(range(1, 10), 4), tg=(10, 60), minst=2, req="sub", div=[]),
    4: dict(n=4, pool=lambda r: r.sample(range(2, 16), 4), tg=(30, 120), minst=3, req="sub", div=[]),
    5: dict(n=5, pool=lambda r: r.sample(range(1, 11), 5), tg=(30, 150), minst=3, req="sub", div=[]),
    6: dict(n=5, pool=lambda r: r.sample(range(2, 21), 5), tg=(50, 250), minst=3, req="sub", div=[4, 5, 6]),
    7: dict(n=5, pool=lambda r: [r.choice([25, 50])] + r.sample(range(2, 11), 4), tg=(100, 400), minst=3, req="sub", div=[3, 4, 5, 6]),
    8: dict(n=6, pool=lambda r: [r.choice(LARGE)] + r.sample(range(1, 11), 5), tg=(100, 500), minst=4, req="sub", div=[4, 5, 6]),
    9: dict(n=6, pool=lambda r: r.sample(LARGE, 2) + r.sample(range(1, 11), 4), tg=(100, 700), minst=4, req="sub", div=[2, 3, 4, 5, 6]),
    10: dict(n=6, pool=lambda r: r.sample(LARGE, 2) + r.sample(range(1, 11), 4), tg=(150, 999), minst=4, req="sub", div=[1, 2, 3, 4, 5, 6]),
    11: dict(n=6, pool=lambda r: r.sample(LARGE, 3) + r.sample(range(1, 11), 3), tg=(200, 999), minst=4, req="sub", div=[1, 2, 3, 4, 5, 6]),
    12: dict(n=6, pool=lambda r: r.sample(LARGE, 2) + r.sample(range(2, 11), 4), tg=(250, 999), minst=5, req="sub", div=[1, 2, 3, 4, 5, 6]),
    13: dict(n=6, pool=lambda r: r.sample(LARGE, 1 + r.randint(0, 2)) , tg=(300, 999), minst=5, req="sub", div=[1, 2, 3, 4, 5, 6]),
    14: dict(n=6, pool=lambda r: r.sample(LARGE, 2) + r.sample(range(2, 10), 4), tg=(350, 999), minst=5, req="sub", div=[1, 2, 3, 4, 5, 6]),
}
# 가용 세트 수(챕터별 샘플 수)
NSETS = {1: 80, 2: 120, 3: 150, 4: 150, 5: 120, 6: 150, 7: 150, 8: 300, 9: 300, 10: 300, 11: 300, 12: 400, 13: 400, 14: 500}
# 챕터별 해법 수(총 트리 수 T) 상한: 후반일수록 빡빡
TMAX = {1: 99999, 2: 99999, 3: 99999, 4: 99999, 5: 99999, 6: 99999, 7: 99999,
        8: 99999, 9: 99999, 10: 99999, 11: 99999, 12: 99999, 13: 99999, 14: 99999}


def fill_pool(r, cfg):
    n = cfg["n"]
    nums = cfg["pool"](r)
    while len(nums) < n:
        c = r.randint(2, 10)
        if c not in nums:
            nums.append(c)
    r.shuffle(nums)
    return tuple(nums[:n])


def score(minst, nmin, total, target, n):
    return 4 * minst - math.log2(total + 1) - 0.5 * math.log2(nmin + 1) + 1.5 * math.log10(target)


def build_candidates(ch, pool_exec):
    cfg = CH[ch]
    r = random.Random(7000 + ch)
    sets, seen = [], set()
    tries = 0
    while len(sets) < NSETS[ch] and tries < 20000:
        tries += 1
        s = fill_pool(r, cfg)
        k = tuple(sorted(s))
        if len(set(k)) < len(k) or k in seen:
            continue
        seen.add(k)
        sets.append(s)
    cands = []
    for nums, st in pool_exec.imap(analyze_job, sets, chunksize=2):
        lo, hi = cfg["tg"]
        for t, (mn, nm, tot, pm, nosub, nodiv) in st.items():
            if not (lo <= t <= hi) or t in nums:
                continue
            if mn < cfg["minst"] or tot > TMAX[ch]:
                continue
            if cfg["req"] == "pm" and not pm:
                continue
            if cfg["req"] == "sub" and nosub:  # ÷ 포함해도 −없이 가능 -> −가 필수 아님
                continue
            cands.append(dict(nums=nums, target=t, minst=mn, nmin=nm, total=tot,
                              needdiv=not nodiv, pm=pm,
                              score=score(mn, nm, tot, t, cfg["n"])))
    cands.sort(key=lambda c: (c["score"], tuple(c["nums"]), c["target"]))
    return cands


def pick(ch, cands, used, ramp):
    """ramp: 이 챕터 6슬롯의 목표 난이도 점수(전체 84문제에 걸친 선형 상승)."""
    cfg = CH[ch]
    chosen, used_sets = [], set()
    for slot, want in enumerate(ramp, 1):
        need_div = slot in cfg["div"]
        best = None
        for c in cands:
            ns = tuple(sorted(c["nums"]))
            if (ns, c["target"]) in used or ns in used_sets:
                continue
            if need_div and not c["needdiv"]:
                continue
            d = abs(c["score"] - want)
            if best is None or d < best[0]:
                best = (d, c)
        if best is None:
            raise SystemExit(f"ch{ch} slot{slot}: 후보 없음")
        c = best[1]
        used.add((tuple(sorted(c["nums"])), c["target"]))
        used_sets.add(tuple(sorted(c["nums"])))
        chosen.append(c)
    chosen.sort(key=lambda c: c["score"])
    return chosen


# ---------------------------------------------------------------- text
H1 = {
    "ko": "먼저 {a}, {b} 타일을 골라 보세요.",
    "en": "Start by picking the {a} and {b} tiles.",
    "ja": "まず {a} と {b} のタイルを選んでみましょう。",
    "zh": "先选择 {a} 和 {b} 这两个数字。",
    "es": "Empieza eligiendo las fichas {a} y {b}.",
    "fr": "Commencez par choisir les tuiles {a} et {b}.",
    "de": "Beginne mit den Kacheln {a} und {b}.",
}
H2 = {
    "ko": "{eq} 부터 계산해 보세요.",
    "en": "Make {eq} first.",
    "ja": "まず {eq} を作ってみましょう。",
    "zh": "先算出 {eq}。",
    "es": "Primero haz {eq}.",
    "fr": "Calculez d'abord {eq}.",
    "de": "Rechne zuerst {eq}.",
}
H3_FULL = {
    "ko": "전체 풀이: {s}",
    "en": "Full solution: {s}",
    "ja": "解法の全体: {s}",
    "zh": "完整解法：{s}",
    "es": "Solución completa: {s}",
    "fr": "Solution complète : {s}",
    "de": "Gesamte Lösung: {s}",
}
H3_PART = {
    "ko": "이어지는 계산: {s}. 마지막 한 단계는 직접 찾아보세요.",
    "en": "Next steps: {s}. Find the last step yourself.",
    "ja": "続きの計算: {s}。最後の一手は自分で見つけましょう。",
    "zh": "接下来：{s}。最后一步请自己找出。",
    "es": "Siguientes pasos: {s}. El último paso, ¡encuéntralo tú!",
    "fr": "Étapes suivantes : {s}. Trouvez la dernière étape vous-même.",
    "de": "Nächste Schritte: {s}. Den letzten Schritt findest du selbst.",
}


def make_texts(steps):
    expl = " → ".join(fmt(s) for s in steps)
    a, b = steps[0][0], steps[0][2]
    eq = fmt(steps[0])
    if len(steps) <= 3:
        h3 = {l: H3_FULL[l].format(s=expl) for l in LANGS}
    else:
        mid = " → ".join(fmt(s) for s in steps[1:-1])
        h3 = {l: H3_PART[l].format(s=mid) for l in LANGS}
    hints = {l: [H1[l].format(a=a, b=b), H2[l].format(eq=eq), h3[l]] for l in LANGS}
    return hints, {l: expl for l in LANGS}


def choose_solution(nums, target, plus_times_only=False):
    sols = find_min_solutions(list(nums), target)
    if plus_times_only:
        sols = [t for t in sols if all(st[1] in "+*" for st in steps_of(t))] or sols
    # 결정적 선택: 단계 문자열 사전순 최소
    best = min(sols, key=lambda t: [fmt(s) for s in steps_of(t)])
    return steps_of(best)


# ---------------------------------------------------------------- main
def main():
    dry = "--dry" in sys.argv
    with Pool() as pool:
        used, result, metrics = set(), [], {}
        allc = {ch: build_candidates(ch, pool) for ch in range(1, 15)}
        s1 = sorted(c["score"] for c in allc[1])
        s14 = sorted(c["score"] for c in allc[14])
        lo, hi = s1[int(0.05 * len(s1))], s14[int(0.98 * len(s14))]
        for ch in range(1, 15):
            cands = allc[ch]
            ramp = [lo + (hi - lo) * ((ch - 1) * 6 + k) / 83 for k in range(6)]
            chosen = pick(ch, cands, used, ramp)
            metrics[ch] = (len(cands), chosen)
            for order, c in enumerate(chosen, 1):
                steps = choose_solution(c["nums"], c["target"], CH[ch]["req"] == "pm")
                assert steps[-1][3] == c["target"] and len(steps) == c["minst"]
                hints, expl = make_texts(steps)
                result.append({
                    "id": f"tg{ch}_s{order:02d}", "type": "target", "track": "target",
                    "chapter": ch, "order": order,
                    "numbers": list(c["nums"]), "target": c["target"], "answer": str(c["target"]),
                    "inputType": "choices", "choices": None,
                    "hints": hints, "explanation": expl,
                })
                c["steps"] = steps
    print_table(metrics)
    if dry:
        return
    with open(PUZZLES, encoding="utf8") as f:
        data = json.load(f)
    data = [p for p in data if p.get("track") != "target"]
    data.extend(result)
    with open(PUZZLES, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    print(f"wrote {len(result)} target puzzles; total {len(data)}")


def print_table(metrics):
    print("ch | n | target range | minsteps avg | trees(min) avg | trees(total) avg | ops | cands")
    for ch, (nc, chosen) in metrics.items():
        n = CH[ch]["n"]
        ts = [c["target"] for c in chosen]
        ops = []
        if all(c["pm"] for c in chosen):
            ops.append("+×만")
        else:
            ops.append("−필수")
        nd = sum(c["needdiv"] for c in chosen)
        if nd:
            ops.append(f"÷필수 {nd}/6")
        print(f"{ch:2d} | {n} | {min(ts)}-{max(ts)} | {sum(c['minst'] for c in chosen)/6:.1f} | "
              f"{sum(c['nmin'] for c in chosen)/6:.1f} | {sum(c['total'] for c in chosen)/6:.0f} | {', '.join(ops)} | {nc}")


if __name__ == "__main__":
    main()
