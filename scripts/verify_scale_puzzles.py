#!/usr/bin/env python3
"""저울 추리(track:"scale") 독립 검증기. 생성기(gen_scale_puzzles.py)를 import 하지 않고
명세 docs/cipher-scale-mode-spec.md §3 의 유일성(선형 풀이 + 제약 DFS)(모든 해에서 query 총 무게 동일), 자명하지 않은 저울,
챕터 사다리, 힌트/설명(7개 언어), 테스트 벡터를 puzzles.json 의 84문제에 대해 재검증한다.

실행: python3 scripts/verify_scale_puzzles.py [--puzzles PATH] [--vectors PATH]   (실패 시 종료 코드 1)
"""
import argparse, json, os, re, sys
from collections import Counter
from fractions import Fraction

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
EMOJI = set("🍎🍌🍇🍒🍋🍓🥝🍑🌙⭐🔥💧🌿🔔🎈🐟🐱🐸🚗🎲")
errors = []


def err(msg):
    errors.append(msg)


# ch -> (허용 미지수 집합, query 토큰 수 범위(숫자 추 포함))   (저울 수 == 미지수 수: 모든 저울이 꼭 필요하므로)
def ladder(ch):
    if ch == 1:
        return {2}, (1, 2)
    if ch == 2:
        return {2, 3}, (1, 3)
    if ch <= 4:
        return {3}, (2, 3)
    if ch <= 6:
        return {3, 4}, (2, 4)
    if ch <= 8:
        return {4}, (2, 4)
    if ch <= 10:
        return {4, 5}, (3, 4)
    if ch <= 12:
        return {5}, (3, 5)
    return {5, 6}, (4, 6)


def wrange(ch):  # 이모지 무게 상한
    return 12 if ch <= 8 else 20 if ch <= 12 else 30


def numcap(ch):  # 숫자 추 상한 (배지는 2자리까지)
    return 20 if ch <= 8 else 40 if ch <= 12 else 60


def caps(ch):  # (한 접시 토큰 수, 한 저울 토큰 수)
    return (6, 10) if ch >= 13 else (5, 8)


# ---------------------------------------------------------------- 도구
def is_num(t, cap=60):
    return re.fullmatch(r"[1-9][0-9]?", t) is not None and 1 <= int(t) <= cap


def net(scale, syms):
    co = {s: 0 for s in syms}
    k = 0
    for sgn, plate in ((1, scale[0]), (-1, scale[1])):
        for t in plate:
            if t in co:
                co[t] += sgn
            else:
                k += sgn * int(t)
    return co, k


def rank_rows(rows):
    """Fraction 가우스 소거로 기저 반환."""
    basis = []
    for r in rows:
        r = [Fraction(x) for x in r]
        for p, b in basis:
            if r[p] != 0:
                f = r[p]
                r = [x - f * y for x, y in zip(r, b)]
        p = next((i for i, x in enumerate(r) if x != 0), None)
        if p is not None:
            basis.append((p, [x / r[p] for x in r]))
    return basis


def in_span(rows, q):
    basis = rank_rows(rows)
    r = [Fraction(x) for x in q]
    for p, b in basis:
        if r[p] != 0:
            f = r[p]
            r = [x - f * y for x, y in zip(r, b)]
    return not any(r)


def solve_linear(A, b):
    """A x = b (Fraction 가우스-조던). 해가 유일하면 x 리스트, 아니면 None."""
    m, n = len(A), len(A[0])
    M = [[Fraction(x) for x in row] + [Fraction(bb)] for row, bb in zip(A, b)]
    piv = []
    r = 0
    for c in range(n):
        pr = next((i for i in range(r, m) if M[i][c] != 0), None)
        if pr is None:
            continue
        M[r], M[pr] = M[pr], M[r]
        M[r] = [x / M[r][c] for x in M[r]]
        for i in range(m):
            if i != r and M[i][c] != 0:
                f = M[i][c]
                M[i] = [x - f * y for x, y in zip(M[i], M[r])]
        piv.append(c)
        r += 1
        if r == m:
            break
    if len(piv) < n:
        return None
    if any(M[i][n] != 0 for i in range(r, m)):
        return None
    return [M[i][n] for i in range(n)]


def all_solutions(syms, hi, scales):
    """모든 저울이 균형인 (서로 다른 1..hi) 할당 전부. 순계수 행렬이 최대 랭크면 선형으로 푼 유일해를 범위/서로 다름/정수 검사,
    아니면 강제값 전파 DFS."""
    n = len(syms)
    eqs = []
    for sc in scales:
        co, k = net(sc, syms)
        eqs.append(([co[s] for s in syms], k))
    if len(eqs) >= n and rank_rows([e[0] for e in eqs]) and len(rank_rows([e[0] for e in eqs])) == n:
        x = solve_linear([e[0] for e in eqs], [-e[1] for e in eqs])
        if x is None:
            return []
        if any(v.denominator != 1 for v in x):
            return []
        sol = tuple(int(v) for v in x)
        if any(not 1 <= v <= hi for v in sol) or len(set(sol)) != n:
            return []
        return [sol]
    sols = []
    asg = [None] * n

    def rec():
        free = [i for i in range(n) if asg[i] is None]
        forced = None
        for co, k in eqs:
            un = [i for i in free if co[i] != 0]
            s = k + sum(co[i] * asg[i] for i in range(n) if asg[i] is not None and co[i] != 0)
            if not un:
                if s != 0:
                    return
            elif len(un) == 1 and forced is None:
                forced = (un[0], co[un[0]], s)
        if not free:
            sols.append(tuple(asg))
            return
        if forced:
            i, c, s = forced
            if (-s) % c != 0:
                return
            v = -s // c
            if v < 1 or v > hi or v in asg:
                return
            asg[i] = v
            rec()
            asg[i] = None
            return
        i = free[0]
        for v in range(1, hi + 1):
            if v in asg:
                continue
            asg[i] = v
            rec()
            asg[i] = None

    rec()
    return sols


def direct_scales(scales, syms):
    """'직접 저울' 수: 양쪽 접시의 같은 기호/숫자를 상쇄한 뒤(순계수) 미지수 종류가 정확히 하나이고 나머지는 숫자뿐인 저울."""
    cnt = 0
    for sc in scales:
        co, _ = net(sc, syms)
        if sum(1 for v in co.values() if v) == 1:
            cnt += 1
    return cnt


def propagate_rounds(scales, syms, query_syms, raw):
    """손 풀이(순수 대입): 라운드마다 풀 수 있는 저울을 모두 풀어 알게 되는 기호를 늘린다. (성공 여부, 라운드 수).
    직접 저울이 없으면 첫 라운드부터 아무것도 못 풀어 실패한다."""
    known = set()
    rounds = 0
    while True:
        new = set()
        for L, R in scales:
            cl, cr = Counter(t for t in L if t in syms), Counter(t for t in R if t in syms)
            un = [s for s in set(cl) | set(cr) if s not in known]
            if raw:
                if len(un) == 1 and (un[0] in cl) != (un[0] in cr):
                    new.add(un[0])
            else:
                un = [s for s in un if cl[s] - cr[s] != 0]
                if len(un) == 1:
                    new.add(un[0])
        if not new:
            break
        known |= new
        rounds += 1
    return all(s in known for s in query_syms), rounds


def numbers_in(text):
    return [int(x) for x in re.findall(r"\d+", text)]


# ---------------------------------------------------------------- 검사
def check_puzzle(p, ch, o):
    pid = p["id"]
    nv_ok, (qlo, qhi) = ladder(ch)
    pan_cap, tot_cap = caps(ch)
    ncap = numcap(ch)
    hi = wrange(ch)
    scales, query = p["scales"], p["query"]
    # 스키마
    if p.get("type") != "scale" or p.get("track") != "scale":
        err(f"{pid}: type/track")
    if p.get("chapter") != ch or p.get("order") != o:
        err(f"{pid}: chapter/order")
    if p.get("inputType") != "keypad" or p.get("choices") is not None:
        err(f"{pid}: keypad/choices null 이어야 함")
    if not (isinstance(p.get("answer"), str) and re.fullmatch(r"[1-9][0-9]{0,2}", p["answer"])):
        err(f"{pid}: answer 는 1~999 문자열이어야 함 ({p.get('answer')!r})")
        return
    ans = int(p["answer"])
    if not isinstance(scales, list) or not 2 <= len(scales) <= 6:
        err(f"{pid}: 저울 수 2~6")
        return
    syms = []
    for sc in scales:
        if not (isinstance(sc, list) and len(sc) == 2 and all(isinstance(x, list) for x in sc)):
            err(f"{pid}: 저울 형식")
            return
        for plate in sc:
            if not 1 <= len(plate) <= pan_cap:
                err(f"{pid}: 접시 토큰 수 1~{pan_cap} 위반 {sc}")
            for t in plate:
                if t in EMOJI:
                    if t not in syms:
                        syms.append(t)
                elif not is_num(t, ncap):
                    err(f"{pid}: 잘못된 토큰 {t!r} (숫자 추 1~{ncap})")
                    return
        if not 2 <= len(sc[0]) + len(sc[1]) <= tot_cap:
            err(f"{pid}: 저울 토큰 합 2~{tot_cap} 위반 {sc}")
    for t in query:
        if t not in EMOJI and not is_num(t, 99):
            err(f"{pid}: query 토큰 {t!r}")
            return
        if t in EMOJI and t not in syms:
            err(f"{pid}: query 기호 {t} 가 어느 저울에도 없음")
            return
    if not 1 <= len(query) <= pan_cap:
        err(f"{pid}: query 접시 토큰 1~{pan_cap}")
    # 챕터 사다리: 미지수 == 저울 수 (모든 저울이 꼭 필요)
    if len(syms) not in nv_ok:
        err(f"{pid}: 미지수 {len(syms)} 가 챕터 사다리 {sorted(nv_ok)} 밖")
    if len(scales) != len(syms):
        err(f"{pid}: 저울 수 {len(scales)} != 미지수 {len(syms)}")
    if not qlo <= len(query) <= qhi:
        err(f"{pid}: query 토큰 {len(query)} 가 챕터 사다리 {qlo}~{qhi} 밖")
    # 자명하지 않음 / 중복
    nets = []
    for sc in scales:
        if not sc[0] or not sc[1]:
            err(f"{pid}: 빈 접시")
        if sorted(sc[0]) == sorted(sc[1]):
            err(f"{pid}: 양쪽 구성이 같은 자명한 저울 {sc}")
        co, k = net(sc, syms)
        if not any(co.values()):
            err(f"{pid}: 완전 상쇄되는 자명한 저울 {sc}")
        nets.append((co, k))
    keys = []
    for co, k in nets:
        t = tuple(co[s] for s in syms) + (k,)
        f = next((x for x in t if x), 0)
        if f < 0:
            t = tuple(-x for x in t)
        keys.append(t)
    if len(set(keys)) != len(keys):
        err(f"{pid}: 같은 저울이 중복됨")
    for s in syms:
        if all(co[s] == 0 for co, _ in nets):
            err(f"{pid}: 미지수 {s} 가 모든 저울에서 상쇄돼 쓰이지 않음")
    # 유일성 (선형 풀이 + 범위/서로 다름) — 해가 없거나 query 가 유일하지 않으면 오류
    sols = all_solutions(syms, hi, scales)
    if not sols:
        err(f"{pid}: 범위 1~{hi} 내 서로 다른 무게로 모든 저울이 균형인 할당이 없음")
        return
    for s in sols[:3]:  # 독립 재확인: 실제로 모든 저울이 균형
        w = dict(zip(syms, s))
        for sc in scales:
            tl = sum(w[t] if t in w else int(t) for t in sc[0])
            tr = sum(w[t] if t in w else int(t) for t in sc[1])
            if tl != tr:
                err(f"{pid}: 해 {s} 에서 저울 {sc} 불균형")
    qnum = sum(int(t) for t in query if t not in EMOJI)
    totals = {sum(s[syms.index(t)] for t in query if t in EMOJI) + qnum for s in sols}
    if len(totals) != 1:
        err(f"{pid}: query 총 무게가 유일하지 않음 {sorted(totals)[:6]} (해 {len(sols)}개)")
        return
    if totals != {ans}:
        err(f"{pid}: answer {ans} != 탐색 결과 {totals}")
    if len(sols) != 1:
        err(f"{pid}: 미지수 값이 유일하지 않음(해 {len(sols)}개) — 이 모드는 유일해만 허용")
    # 손 풀이: query = 저울들의 정수 계수 선형 결합, 모든 저울이 필요(계수 != 0)
    rows = [[co[s] for s in syms] for co, _ in nets]
    q = [sum(1 for t in query if t == s) for s in syms]
    n = len(syms)
    c = solve_linear([[rows[i][j] for i in range(len(rows))] for j in range(n)], q) if len(rows) <= n else None
    if c is None:
        # 저울이 독립이 아니거나 query 가 span 밖
        if not in_span(rows, q):
            err(f"{pid}: query 가 저울들의 선형 조합으로 확정되지 않음")
        else:
            err(f"{pid}: 저울이 선형독립이 아님(불필요한 저울)")
    else:
        if any(x.denominator != 1 for x in c):
            err(f"{pid}: query 가 저울의 정수 계수 결합이 아님 {[str(x) for x in c]}")
        if any(x == 0 for x in c):
            err(f"{pid}: 계수 0 인 저울이 있음(불필요한 저울)")
        if any(abs(x) > 6 for x in c):
            err(f"{pid}: 정수 결합 계수가 너무 큼 {[str(x) for x in c]}")
    for i in range(len(rows)):
        if in_span(rows[:i] + rows[i + 1:], q):
            err(f"{pid}: {i + 1}번째 저울이 없어도 풀림(불필요한 저울)")
    # 챕터별 구조 조건
    qsyms = sorted({t for t in query if t in EMOJI})
    nd = direct_scales(scales, syms)
    common = sum(1 for L, R in scales if set(L) & set(R) & EMOJI)
    mixed = sum(1 for L, R in scales if set(L) & EMOJI and set(R) & EMOJI)
    mult3 = sum(1 for L, R in scales if max(list(Counter(t for t in L if t in EMOJI).values()) +
                                              list(Counter(t for t in R if t in EMOJI).values()) + [0]) >= 3)
    numer = sum(1 for L, R in scales if any(t not in EMOJI for t in L + R))
    ok_sub, rounds = propagate_rounds(scales, set(syms), qsyms, raw=False)
    if ch == 1:
        if nd < 1:
            err(f"{pid}: 1챕터는 직접 저울이 하나 이상 있어야 함")
        if not ok_sub or rounds != 2:
            err(f"{pid}: 1챕터는 2단계(직접 저울 → 대입)로 풀려야 함 (성공 {ok_sub}, 라운드 {rounds})")
    else:
        if nd != 0:
            err(f"{pid}: 2챕터 이상은 시작 시 직접 저울이 없어야 함 ({nd}개)")
        if ok_sub:
            err(f"{pid}: 순수 한-미지수 대입만으로 풀림(2챕터 이상 금지)")
        if propagate_rounds(scales, set(syms), qsyms, raw=True)[0]:
            err(f"{pid}: 상쇄 없는 대입만으로 풀림")
    # 상쇄가 필요한 저울을 모두 빼면 풀리지 않아야 함(7~8챕터: 공통 기호 저울 >= 2)
    if common:
        keep = [r for r, (L, R) in zip(rows, scales) if not (set(L) & set(R) & EMOJI)]
        if in_span(keep, q):
            err(f"{pid}: 공통 기호 저울 없이도 풀림")
    if 3 <= ch <= 4 and mixed < 1:
        err(f"{pid}: 3~4챕터는 양쪽에 기호가 있는 저울 1개 이상")
    if 5 <= ch <= 6 and mixed < 2:
        err(f"{pid}: 5~6챕터는 양쪽에 기호가 섞인 저울 2개 이상")
    if 7 <= ch <= 8 and common < 2:
        err(f"{pid}: 7~8챕터는 공통 기호 상쇄가 필요한 저울 2개 이상 (있음 {common})")
    if 9 <= ch <= 10:
        if mult3 < 1:
            err(f"{pid}: 9~10챕터는 같은 기호 3개 이상(배수) 필요")
        if max(Counter(t for t in query if t in EMOJI).values()) < 2:
            err(f"{pid}: 9~10챕터 query 에 반복 기호 필요")
    if 11 <= ch <= 12 and numer < 3:
        err(f"{pid}: 11~12챕터는 숫자 추가 섞인 저울 3개 이상")
    if ch >= 13 and (common < 1 or mixed < 2 or mult3 < 1 or numer < 3):
        err(f"{pid}: 13~14챕터는 상쇄·혼합·배수·숫자 추 모두 필요 (c{common} m{mixed} x3={mult3} n{numer})")
    # 정답 크기
    if 9 <= ch <= 12 and ans < 20:
        err(f"{pid}: 9~12챕터 정답 >= 20 필요 ({ans})")
    if ch >= 13 and ans < 40:
        err(f"{pid}: 13~14챕터 정답 >= 40 필요 ({ans})")
    # 힌트/설명
    hints, expl = p.get("hints"), p.get("explanation")
    if not isinstance(hints, dict) or set(hints) != set(LANGS):
        err(f"{pid}: hints 언어 키")
        return
    forced = {syms[i]: sols[0][i] for i in range(len(syms)) if len({s[i] for s in sols}) == 1}
    if not forced:
        err(f"{pid}: 확정된 무게를 가진 미지수가 없어 구체 힌트 불가")
    h2syms = set()
    for l in LANGS:
        h = hints[l]
        if not (isinstance(h, list) and len(h) == 3 and all(isinstance(x, str) and x.strip() for x in h)):
            err(f"{pid}: hints[{l}] 은 비어 있지 않은 문자열 3개여야 함")
            continue
        if len(set(h)) != 3:
            err(f"{pid}: hints[{l}] 중복")
        found = [s for s in syms if s in h[1]]
        if len(found) != 1:
            err(f"{pid}: hints[{l}][1] 에 기호가 정확히 하나 있어야 함")
            continue
        s = found[0]
        h2syms.add(s)
        if query == [s]:
            err(f"{pid}: hints[{l}][1] 이 정답을 직접 알려줌")
        if s in forced and forced[s] == ans:
            err(f"{pid}: hints[{l}][1] 의 무게가 정답과 같음(정답 노출)")
        if s not in forced or numbers_in(h[1]) != [forced[s]]:
            err(f"{pid}: hints[{l}][1] 의 무게가 확정값과 다름 ({h[1]})")
        n3 = numbers_in(h[2])
        if len(n3) != 1:
            err(f"{pid}: hints[{l}][2] 숫자 하나여야 함")
        elif ans < 10:
            if not (ans < n3[0] <= ans + 5):
                err(f"{pid}: hints[{l}][2] 상한 부적절 ({h[2]})")
        elif n3[0] != len(p["answer"]):
            err(f"{pid}: hints[{l}][2] 자릿수 불일치 ({h[2]})")
    if len(h2syms) > 1:
        err(f"{pid}: 언어마다 힌트 기호가 다름")
    if not isinstance(expl, dict) or set(expl) != set(LANGS) or any(
            not isinstance(expl[l], str) or not expl[l].strip() for l in LANGS):
        err(f"{pid}: explanation 7개 언어")
    else:
        e = expl["ko"]
        if ans not in numbers_in(e):
            err(f"{pid}: explanation 에 정답이 없음")
        for sym, w in re.findall(r"(\S)=(\d+)", e):
            if sym in syms and (sym not in forced or forced[sym] != int(w)):
                err(f"{pid}: explanation 의 {sym}={w} 가 확정값과 다름")
    return len(sols)


def metrics(p):
    """챕터 지표: 미지수, 저울 수, 시작 직접 저울 수, 순수 대입만으로 풀리는지, 직접 저울이 맨 앞인지, 정답."""
    syms = []
    for sc in p["scales"]:
        for t in sc[0] + sc[1]:
            if t in EMOJI and t not in syms:
                syms.append(t)
    qs = sorted({t for t in p["query"] if t in EMOJI})
    first_direct = direct_scales([p["scales"][0]], syms) == 1
    return dict(nv=len(syms), ns=len(p["scales"]), direct=direct_scales(p["scales"], syms),
                subst=propagate_rounds(p["scales"], set(syms), qs, raw=False)[0], first_direct=first_direct,
                ans=int(p["answer"]))


def print_table(sc):
    print("ch | 미지수 | 저울 | 시작 직접저울(합) | 정답 min~max | >=100 | 순수대입 풀림 | 직접이 맨앞")
    for ch in range(1, 15):
        ms = [metrics(p) for p in sc if p["chapter"] == ch]
        if not ms:
            continue
        nvs = sorted({m["nv"] for m in ms})
        nss = sorted({m["ns"] for m in ms})
        print(f"{ch:2d} | {'-'.join(map(str, nvs)):>5} | {'-'.join(map(str, nss)):>4} | "
              f"{sum(m['direct'] for m in ms):>3} (max {max(m['direct'] for m in ms)}) | "
              f"{min(m['ans'] for m in ms):>3}~{max(m['ans'] for m in ms):<3} | "
              f"{sum(1 for m in ms if m['ans'] >= 100)} | {sum(1 for m in ms if m['subst'])}/{len(ms)} | "
              f"{sum(1 for m in ms if m['first_direct'])}/{len(ms)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=os.path.join(ROOT, "shared", "content", "puzzles.json"))
    ap.add_argument("--table", action="store_true", help="챕터별 지표 표 출력")
    ap.add_argument("--vectors", default=os.path.join(ROOT, "scripts", "scale_test_vectors.json"))
    args = ap.parse_args()
    with open(args.puzzles, encoding="utf8") as f:
        data = json.load(f)
    sc = [p for p in data if p.get("track") == "scale"]
    if len(sc) != 84:
        err(f"scale 퍼즐 수 {len(sc)} != 84")
    ids = [p["id"] for p in sc]
    if len(set(ids)) != len(ids):
        err("id 중복")
    all_ids = [p["id"] for p in data]
    if len(set(all_ids)) != len(all_ids):
        err("puzzles.json 전체 id 중복")
    exp = [f"sc{ch}_s{o:02d}" for ch in range(1, 15) for o in range(1, 7)]
    if ids != exp:
        err("id/순서가 sc1_s01..sc14_s06 과 다름")
    nsol = {}
    for p in sc:
        m = re.fullmatch(r"sc(\d+)_s(\d\d)", p["id"])
        if not m:
            err(f"{p['id']}: id 형식")
            continue
        ch, o = int(m.group(1)), int(m.group(2))
        if not (1 <= ch <= 14 and 1 <= o <= 6):
            err(f"{p['id']}: 범위")
            continue
        try:
            nsol[p["id"]] = check_puzzle(p, ch, o)
        except Exception as ex:  # noqa
            err(f"{p['id']}: 검사 중 예외 {ex!r}")
    # 챕터 단위 조건
    for ch in range(1, 15):
        ps = [p for p in sc if p["chapter"] == ch]
        ans = [int(p["answer"]) for p in ps if re.fullmatch(r"\d+", str(p.get("answer")))]
        if len(set(ans)) != len(ans):
            err(f"챕터 {ch}: 정답 중복 {ans}")
        if ch == 1 and sum(1 for p in ps if metrics(p)["first_direct"]) > 4:
            err("챕터 1: 직접 저울이 맨 앞인 퍼즐이 너무 많음(풀이 순서대로 나열됨)")
        if ch in (5, 6, 7, 8) and sum(1 for a in ans if a >= 10) < 5:
            err(f"챕터 {ch}: 정답 >= 10 이 대부분이어야 함 {ans}")
        if ch >= 13 and sum(1 for a in ans if a >= 100) < 3:
            err(f"챕터 {ch}: 정답 >= 100 이 3개 이상 필요 {ans}")
    # 퍼즐 중복
    seen = {}
    for p in sc:
        k = json.dumps([sorted(map(json.dumps, p["scales"])), p["query"]], ensure_ascii=False)
        if k in seen:
            err(f"{p['id']}: {seen[k]} 와 같은 퍼즐")
        seen[k] = p["id"]
    # 테스트 벡터
    try:
        with open(args.vectors, encoding="utf8") as f:
            v = json.load(f)
    except Exception as ex:  # noqa
        err(f"vectors 읽기 실패 {ex!r}")
        v = None
    ncase = nsamp = 0
    if v:
        cases = v.get("eval_cases", [])
        ncase = len(cases)
        if ncase < 15:
            err(f"vectors: 케이스 {ncase} < 15")
        if not any(c["balanced"] for c in cases) or all(c["balanced"] for c in cases):
            err("vectors: 균형/불균형 모두 필요")
        for c in cases:
            def tot(pl):
                return sum(c["weights"][t] if t in c["weights"] else int(t) for t in pl)
            lt, rt = tot(c["left"]), tot(c["right"])
            if (lt, rt, lt == rt) != (c["leftTotal"], c["rightTotal"], c["balanced"]):
                err(f"vectors: 불일치 {c}")
        byid = {p["id"]: p for p in sc}
        nsamp = len(v.get("samples", []))
        if nsamp != 10:
            err("vectors: samples != 10")
        for s in v.get("samples", []):
            p = byid.get(s["id"])
            if not p or any(p[k] != s[k] for k in ("chapter", "order", "scales", "query", "answer",
                                                     "inputType", "choices")):
                err(f"vectors: 샘플 불일치 {s['id']}")
    if args.table:
        print_table(sc)
    if errors:
        print(f"FAIL: {len(errors)}개 오류")
        for e in errors[:80]:
            print(" -", e)
        sys.exit(1)
    uniq = sum(1 for n in nsol.values() if n == 1)
    print(f"OK: scale {len(sc)}문제 검증 통과 (해가 하나뿐 {uniq}개 / query만 유일 {len(sc) - uniq}개), "
          f"테스트 벡터 케이스 {ncase}개/샘플 {nsamp}개")


if __name__ == "__main__":
    main()
