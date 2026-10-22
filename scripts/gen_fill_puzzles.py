#!/usr/bin/env python3
"""격자 채우기(track:"fill") 퍼즐 생성기. 명세: docs/fill-path-mode-spec.md §1 §3 §4

14챕터 x 6문제 = 84문제(id fx{ch}_s{01..06})를 시드 고정으로 생성해 puzzles.json 에 병합한다.
재실행 안전: track=="fill" 항목만 기존 위치에서 교체한다(없으면 맨 뒤에 추가, 나머지 퍼즐은 바이트 동일).
같은 실행에서 플랫폼 검증용 scripts/fill_test_vectors.json 도 만든다.

실행:  python3 scripts/gen_fill_puzzles.py [--puzzles PATH] [--vectors PATH] [--dry]

생성 방식: 규칙(row/col/box/diag)을 만족하는 완성 격자를 무작위로 만들고, 그 해로부터 lt(부등호)·cages(합 묶음)를 뽑은 뒤,
무작위 순서로 칸을 비우되 "백트래킹 솔버로 해가 정확히 1개"일 때만 비운다. 챕터×order 별 목표 빈칸 수(범위의 아래->위)에
도달하면 멈추고(= 고정 칸 최소화에 가깝게), 더 비울 수 없으면(극소) 거기서 멈춘다.
케이지는 합만 제약으로 취급해 유일성을 검사한다(케이지 내 중복 금지는 생성 시 해에서 보장).
난이도 등급(grade): S = naked/hidden single(+lt 경계, 케이지 합 가지치기)만으로 풀림,
P = S 에 더해 naked/hidden pair·triple, locked candidates(상자-줄 교차 소거) 로 풀림,
T = 그래도 안 풀려 가정 후 모순 탐색(trial, depth>=1) 이 필요.
MIN_GRADE 표가 (챕터, order)별 최소 등급을 정한다. 최소 등급이 S 가 아닌 칸은 빈칸을 범위 위쪽(최대)까지 밀고
제약(lt/cages) 개수를 범위 아래쪽으로 줄여 가며 조건을 만족하는 문제를 찾는다(못 찾으면 한 단계 낮춰 채택하고 보고).
"""
import argparse, json, os, random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
VECTORS = os.path.join(ROOT, "scripts", "fill_test_vectors.json")
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]

# ch: (N, rules, lt범위, cage범위, 빈칸범위)  — 범위는 (min,max) 또는 None
LADDER = {
    1: (4, ["row", "col"], None, None, (5, 7)),
    2: (4, ["row", "col", "box"], None, None, (7, 9)),
    3: (4, ["row", "col", "box"], None, None, (9, 11)),
    4: (4, ["row", "col"], (4, 6), None, (8, 10)),
    5: (5, ["row", "col"], None, None, (10, 13)),
    6: (4, ["row", "col"], None, (3, 4), (10, 12)),
    7: (5, ["row", "col"], (5, 8), None, (12, 15)),
    8: (6, ["row", "col", "box"], None, None, (14, 18)),
    9: (5, ["row", "col"], None, (4, 6), (14, 17)),
    10: (4, ["row", "col", "box", "diag"], None, None, (10, 12)),
    11: (6, ["row", "col", "box"], (4, 6), None, (17, 21)),
    12: (5, ["row", "col", "diag"], None, None, (14, 17)),
    13: (6, ["row", "col", "box"], None, (5, 7), (19, 23)),
    14: (6, ["row", "col", "box"], (2, 4), (2, 4), (21, 25)),
}


# (chapter, order) -> 목표 등급 (0=S, 1=P, 2=T). 표에 없으면 제한 없음(S 허용). 검증기가 같은 값을 최소 등급으로 강제한다.
# 목표와 정확히 같은 등급을 우선 찾고, 오래 못 찾으면 그보다 어려운 등급도 채택한다.
# ch10(4x4 대각선)은 빈칸 10~13 의 모든 유일해 문제가 S 라서(전수 확인) 등급을 올릴 수 없다 -> 표에서 제외.
def _mg():
    t = {}
    def put(ch, os_, g):
        for o in os_:
            t[(ch, o)] = g
    put(7, (4, 5), 1); put(7, (6,), 2)
    put(8, (3, 4), 1); put(8, (5, 6), 2)
    put(9, (2, 3, 4), 1); put(9, (5, 6), 2)
    put(11, (2, 3), 1); put(11, (4, 5, 6), 2)
    put(12, (1, 2, 3), 1); put(12, (4, 5, 6), 2)
    put(13, (1, 2, 3), 1); put(13, (4, 5, 6), 2)
    put(14, (1, 2, 3), 1); put(14, (4, 5, 6), 2)
    return t


MIN_GRADE = _mg()
GRADE_NAME = "SPT"


def box_dims(n):
    return {4: (2, 2), 6: (2, 3)}.get(n)


def sched(rng_, o):
    """order o(1..6) 에서 범위 (a,b) 를 아래->위로: 값 증가."""
    a, b = rng_
    return a + round((b - a) * (o - 1) / 5)


def sched_down(rng_, o):
    a, b = rng_
    return b - round((b - a) * (o - 1) / 5)


# ---------------------------------------------------------------- 구조
def units_of(n, rules):
    us = []
    if "row" in rules:
        us += [[r * n + c for c in range(n)] for r in range(n)]
    if "col" in rules:
        us += [[r * n + c for r in range(n)] for c in range(n)]
    if "box" in rules:
        bh, bw = box_dims(n)
        for br in range(0, n, bh):
            for bc in range(0, n, bw):
                us.append([(br + i) * n + bc + j for i in range(bh) for j in range(bw)])
    if "diag" in rules:
        us.append([i * n + i for i in range(n)])
        us.append([i * n + n - 1 - i for i in range(n)])
    return us


# ---------------------------------------------------------------- 백트래킹 솔버 (해 개수, limit 까지)
def count_solutions(n, rules, board, lt, cages, limit=2):
    units = units_of(n, rules)
    cu = [[] for _ in range(n * n)]
    for ui, u in enumerate(units):
        for c in u:
            cu[c].append(ui)
    full = (1 << n) - 1
    umask = [0] * len(units)
    vals = [0] * (n * n)
    fixed = [board[i // n][i % n] for i in range(n * n)]
    ltc = [[] for _ in range(n * n)]  # (other, self_is_smaller)
    for r1, c1, r2, c2 in lt:
        a, b = r1 * n + c1, r2 * n + c2
        ltc[a].append((b, True))
        ltc[b].append((a, False))
    cgs = [(t, [r * n + c for r, c in zip(rest[0::2], rest[1::2])]) for t, *rest in cages]
    cgc = [[] for _ in range(n * n)]
    for k, (t, cells) in enumerate(cgs):
        for c in cells:
            cgc[c].append(k)

    def ok(cell, v):
        for o, small in ltc[cell]:
            ov = vals[o]
            if ov and ((small and not v < ov) or (not small and not ov < v)):
                return False
        for k in cgc[cell]:
            t, cells = cgs[k]
            s, rem = v, 0
            for x in cells:
                if x == cell:
                    continue
                if vals[x]:
                    s += vals[x]
                else:
                    rem += 1
            if rem == 0:
                if s != t:
                    return False
            elif s + rem > t or s + rem * n < t:
                return False
        return True

    cnt = 0

    def rec(left):
        nonlocal cnt
        if cnt >= limit:
            return
        if left == 0:
            cnt += 1
            return
        best, bc, bm = None, 99, 0
        for cell in range(n * n):
            if vals[cell]:
                continue
            used = 0
            for ui in cu[cell]:
                used |= umask[ui]
            m = full & ~used
            if fixed[cell]:
                m &= 1 << (fixed[cell] - 1)
            pc = bin(m).count("1")
            if pc < bc:
                best, bc, bm = cell, pc, m
                if pc <= 1:
                    break
        if bc == 0:
            return
        for v in range(1, n + 1):
            if not bm >> (v - 1) & 1:
                continue
            if not ok(best, v):
                continue
            vals[best] = v
            for ui in cu[best]:
                umask[ui] |= 1 << (v - 1)
            rec(left - 1)
            for ui in cu[best]:
                umask[ui] &= ~(1 << (v - 1))
            vals[best] = 0
            if cnt >= limit:
                return

    rec(n * n)
    return cnt


def solve_one(n, rules, board, lt, cages):
    """첫 해(행렬) 또는 None. 해 하나를 얻기 위해 limit=1 변형: 간단히 칸 하나씩 시도."""
    # count_solutions 는 해를 돌려주지 않으므로, 빈칸에 값을 넣어 보며 개수>0 인 값을 고른다.
    b = [row[:] for row in board]
    for i in range(n * n):
        r, c = divmod(i, n)
        if b[r][c]:
            continue
        for v in range(1, n + 1):
            b[r][c] = v
            if count_solutions(n, rules, b, lt, cages, 1):
                break
        else:
            return None
    return b


# ---------------------------------------------------------------- single 기반 풀이(난이도 지표)
def singles_solve(n, rules, board, lt, cages):
    """(풀림 여부, 확정 순서[(cell,v)], 남은 후보 마스크). naked/hidden single + lt 경계 + 케이지 합 가지치기."""
    units = units_of(n, rules)
    full = (1 << n) - 1
    N2 = n * n
    cand = [full] * N2
    given = [board[i // n][i % n] for i in range(N2)]
    cu = [[] for _ in range(N2)]
    for ui, u in enumerate(units):
        for c in u:
            cu[c].append(ui)
    assigned = [0] * N2
    order = []
    cgs = [(t, [r * n + c for r, c in zip(rest[0::2], rest[1::2])]) for t, *rest in cages]
    ltl = [(r1 * n + c1, r2 * n + c2) for r1, c1, r2, c2 in lt]

    def place(cell, v, record):
        assigned[cell] = v
        cand[cell] = 1 << (v - 1)
        if record:
            order.append((cell, v))

    for i in range(N2):
        if given[i]:
            place(i, given[i], False)
    changed = True
    while changed:
        changed = False
        # 배정된 칸의 값을 같은 유닛 이웃에서 제거
        for ui, u in enumerate(units):
            used = 0
            for c in u:
                if assigned[c]:
                    used |= 1 << (assigned[c] - 1)
            for c in u:
                if not assigned[c] and cand[c] & used:
                    cand[c] &= ~used
                    changed = True
        for a, b in ltl:
            hi_b = cand[b].bit_length()
            lo_a = (cand[a] & -cand[a]).bit_length()
            na = cand[a] & ((1 << (hi_b - 1)) - 1)
            nb = cand[b] & ~((1 << lo_a) - 1)
            if na != cand[a]:
                cand[a], changed = na, True
            if nb != cand[b]:
                cand[b], changed = nb, True
        for t, cells in cgs:
            for ci, cell in enumerate(cells):
                others = [cand[x] for j, x in enumerate(cells) if j != ci]
                sums = {0}
                for m in others:
                    vs = [v + 1 for v in range(n) if m >> v & 1]
                    sums = {s + v for s in sums for v in vs}
                nm = cand[cell]
                for v in range(1, n + 1):
                    if nm >> (v - 1) & 1 and (t - v) not in sums:
                        nm &= ~(1 << (v - 1))
                if nm != cand[cell]:
                    cand[cell], changed = nm, True
        # naked single
        for c in range(N2):
            if not assigned[c] and cand[c] and cand[c] & (cand[c] - 1) == 0:
                place(c, cand[c].bit_length(), True)
                changed = True
        # hidden single
        for u in units:
            for v in range(n):
                bit = 1 << v
                if any(assigned[c] == v + 1 for c in u):
                    continue
                spots = [c for c in u if not assigned[c] and cand[c] & bit]
                if len(spots) == 1:
                    place(spots[0], v + 1, True)
                    changed = True
        if any(cand[c] == 0 for c in range(N2)):
            return False, order, cand
    return all(assigned), order, cand



# ---------------------------------------------------------------- 등급 판정 엔진 (비트마스크)
def _peers_and_links(n, rules):
    units = units_of(n, rules)
    N2 = n * n
    peers = [set() for _ in range(N2)]
    for u in units:
        for c in u:
            peers[c].update(x for x in u if x != c)
    # locked candidates: 교집합이 2칸 이상인 유닛 쌍
    sets = [set(u) for u in units]
    links = []
    for i, a in enumerate(sets):
        for j, b in enumerate(sets):
            if i != j and len(a & b) >= 2:
                links.append((i, j))
    return units, [sorted(p) for p in peers], sets, links


def _pc(x):
    return bin(x).count("1")


def _propagate(n, ctx, cand, level):
    """cand(비트마스크 리스트)를 고정점까지 줄인다. 모순이면 False. level 0=single, 1=+pair/triple/locked."""
    units, peers, sets, links, ltl, cgs = ctx
    N2 = n * n
    from itertools import combinations
    while True:
        before = list(cand)
        for c in range(N2):
            m = cand[c]
            if m == 0:
                return False
            if m & (m - 1) == 0:
                for o in peers[c]:
                    if cand[o] & m:
                        cand[o] &= ~m
                        if cand[o] == 0:
                            return False
        for u in units:
            for v in range(n):
                bit = 1 << v
                spots = [c for c in u if cand[c] & bit]
                if not spots:
                    return False
                if len(spots) == 1:
                    cand[spots[0]] = bit
        for a, b in ltl:
            hi_b = cand[b].bit_length()
            lo_a = (cand[a] & -cand[a]).bit_length()
            cand[a] &= (1 << (hi_b - 1)) - 1
            cand[b] &= ~((1 << lo_a) - 1)
            if cand[a] == 0 or cand[b] == 0:
                return False
        for t, cells in cgs:
            for ci, cell in enumerate(cells):
                sums = {0}
                for j, x in enumerate(cells):
                    if j != ci:
                        vs = [v + 1 for v in range(n) if cand[x] >> v & 1]
                        sums = {s + v for s in sums for v in vs}
                nm = cand[cell]
                for v in range(1, n + 1):
                    if nm >> (v - 1) & 1 and (t - v) not in sums:
                        nm &= ~(1 << (v - 1))
                if nm != cand[cell]:
                    cand[cell] = nm
                    if nm == 0:
                        return False
        if level >= 1 and cand == before:
            for u in units:
                free = [c for c in u if cand[c] & (cand[c] - 1)]
                for k in (2, 3):
                    # naked subset
                    small = [c for c in free if _pc(cand[c]) <= k]
                    for combo in combinations(small, k):
                        un = 0
                        for c in combo:
                            un |= cand[c]
                        if _pc(un) == k:
                            for c in u:
                                if c not in combo and cand[c] & un:
                                    cand[c] &= ~un
                                    if cand[c] == 0:
                                        return False
                    # hidden subset (u 안에서 아직 확정 안 된 값만)
                    vals = []
                    for v in range(n):
                        spots = [c for c in u if cand[c] >> v & 1]
                        if 2 <= len(spots) <= k:
                            vals.append((v, spots))
                    for combo in combinations(vals, k):
                        sp = set()
                        for _, s_ in combo:
                            sp.update(s_)
                        if len(sp) == k:
                            keep = 0
                            for v, _ in combo:
                                keep |= 1 << v
                            for c in sp:
                                if cand[c] & ~keep:
                                    cand[c] &= keep
            for i, j in links:
                for v in range(n):
                    bit = 1 << v
                    spots = [c for c in units[i] if cand[c] & bit]
                    if len(spots) >= 2 and all(c in sets[j] for c in spots):
                        for c in units[j]:
                            if c not in sets[i] and cand[c] & bit:
                                cand[c] &= ~bit
                                if cand[c] == 0:
                                    return False
        if cand == before:
            return True


def _ctx(n, rules, lt, cages):
    units, peers, sets, links = _peers_and_links(n, rules)
    ltl = [(r1 * n + c1, r2 * n + c2) for r1, c1, r2, c2 in lt]
    cgs = [(t, [r * n + c for r, c in zip(rest[0::2], rest[1::2])]) for t, *rest in cages]
    return units, peers, sets, links, ltl, cgs


def _start_cand(n, board):
    full = (1 << n) - 1
    return [(1 << (board[i // n][i % n] - 1)) if board[i // n][i % n] else full for i in range(n * n)]


def level_solves(n, rules, board, lt, cages, level):
    cand = _start_cand(n, board)
    ok = _propagate(n, _ctx(n, rules, lt, cages), cand, level)
    return ok and all(m & (m - 1) == 0 for m in cand)


def trial1_solves(n, rules, board, lt, cages):
    """depth-1 가정 후 모순 탐색(P 수준 전파 + 후보 하나를 가정해 모순이면 제거) 만으로 풀리는지(보고용)."""
    ctx = _ctx(n, rules, lt, cages)
    cand = _start_cand(n, board)
    if not _propagate(n, ctx, cand, 1):
        return False
    while True:
        if all(m & (m - 1) == 0 for m in cand):
            return True
        progress = False
        for c in range(n * n):
            if cand[c] & (cand[c] - 1) == 0:
                continue
            for v in range(n):
                if not cand[c] >> v & 1:
                    continue
                t = list(cand)
                t[c] = 1 << v
                if not _propagate(n, ctx, t, 1):
                    cand[c] &= ~(1 << v)
                    progress = True
                    if not _propagate(n, ctx, cand, 1):
                        return False
        if not progress:
            return False


def grade_of(n, rules, board, lt, cages):
    """(등급 0=S 1=P 2=T, singles 확정 순서, singles 후 후보). S 판정은 singles_solve 와 같다."""
    solved, order, cand = singles_solve(n, rules, board, lt, cages)
    if solved:
        return 0, order, cand
    if level_solves(n, rules, board, lt, cages, 1):
        return 1, order, cand
    return 2, order, cand


# ---------------------------------------------------------------- 완성 격자·제약 생성
def random_solution(rng, n, rules):
    units = units_of(n, rules)
    cu = [[] for _ in range(n * n)]
    for ui, u in enumerate(units):
        for c in u:
            cu[c].append(ui)
    mask = [0] * len(units)
    vals = [0] * (n * n)

    def rec(i):
        if i == n * n:
            return True
        used = 0
        for ui in cu[i]:
            used |= mask[ui]
        vs = [v for v in range(1, n + 1) if not used >> (v - 1) & 1]
        rng.shuffle(vs)
        for v in vs:
            vals[i] = v
            for ui in cu[i]:
                mask[ui] |= 1 << (v - 1)
            if rec(i + 1):
                return True
            for ui in cu[i]:
                mask[ui] &= ~(1 << (v - 1))
        vals[i] = 0
        return False

    assert rec(0)
    return [vals[r * n:(r + 1) * n] for r in range(n)]


def pick_lt(rng, n, sol, k):
    pairs = []
    for r in range(n):
        for c in range(n):
            if c + 1 < n:
                pairs.append((r, c, r, c + 1))
            if r + 1 < n:
                pairs.append((r, c, r + 1, c))
    rng.shuffle(pairs)
    out = []
    for r1, c1, r2, c2 in pairs[:k]:
        if sol[r1][c1] < sol[r2][c2]:
            out.append([r1, c1, r2, c2])
        else:
            out.append([r2, c2, r1, c1])
    return out


def pick_cages(rng, n, sol, k):
    used = set()
    out = []
    tries = 0
    while len(out) < k and tries < 500:
        tries += 1
        size = rng.choice([2, 2, 3])
        r, c = rng.randrange(n), rng.randrange(n)
        cells = [(r, c)]
        if (r, c) in used:
            continue
        while len(cells) < size:
            opts = []
            for (a, b) in cells:
                for da, db in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                    x, y = a + da, b + db
                    if 0 <= x < n and 0 <= y < n and (x, y) not in cells and (x, y) not in used:
                        opts.append((x, y))
            if not opts:
                break
            cells.append(rng.choice(opts))
        if len(cells) != size:
            continue
        vs = [sol[a][b] for a, b in cells]
        if len(set(vs)) != len(vs):
            continue
        # 첫 칸이 좌상단(행 우선 최소)이 되도록 정렬
        cells.sort()
        used.update(cells)
        item = [sum(sol[a][b] for a, b in cells)]
        for a, b in cells:
            item += [a, b]
        out.append(item)
    return out if len(out) == k else None


def remove_greedy(rng, n, rules, board, lt, cages, hi):
    """무작위 순서로 고정 칸을 지워 가며(해 1개 유지) 빈칸이 hi 개가 되거나 더 못 지울 때까지. 현재 빈칸 수를 반환."""
    blanks = sum(1 for r in range(n) for c in range(n) if board[r][c] == 0)
    cells = [(r, c) for r in range(n) for c in range(n) if board[r][c]]
    rng.shuffle(cells)
    for r, c in cells:
        if blanks >= hi:
            break
        v = board[r][c]
        board[r][c] = 0
        if count_solutions(n, rules, board, lt, cages, 2) == 1:
            blanks += 1
        else:
            board[r][c] = v
    return blanks


def hardness(n, rules, board, lt, cages, rank):
    """목표 등급 rank(1=P,2=T) 에서 '한 단계 아래 기법으로는 못 정하는 칸 수'. 0 이면 아직 목표보다 쉽다."""
    if rank == 1:
        solved, order, cand = singles_solve(n, rules, board, lt, cages)
        return sum(1 for m in cand if m & (m - 1))
    cand = _start_cand(n, board)
    _propagate(n, _ctx(n, rules, lt, cages), cand, 1)
    return sum(1 for m in cand if m & (m - 1))


def climb(rng, n, rules, sol, board, lt, cages, rank, iters):
    """고정 칸 하나를 빈칸으로, 빈칸 하나를 해의 값으로 고정하는 이동(빈칸 수 불변)을 반복해 hardness 를 올린다."""
    cur = hardness(n, rules, board, lt, cages, rank)
    for _ in range(iters):
        if cur > 0 and rank == 1 and cur >= 2:
            return
        if cur > 0 and rank == 2 and cur >= 2:
            return
        gv = [(r, c) for r in range(n) for c in range(n) if board[r][c]]
        bl = [(r, c) for r in range(n) for c in range(n) if not board[r][c]]
        if not gv or not bl:
            return
        a, b = rng.choice(gv), rng.choice(bl)
        board[a[0]][a[1]] = 0
        board[b[0]][b[1]] = sol[b[0]][b[1]]
        if count_solutions(n, rules, board, lt, cages, 2) == 1:
            h = hardness(n, rules, board, lt, cages, rank)
            if h >= cur:
                cur = h
                continue
        board[b[0]][b[1]] = 0
        board[a[0]][a[1]] = sol[a[0]][a[1]]


# ---------------------------------------------------------------- 챕터 생성
def make_one(rng, ch, o, seen, prev):
    """반환 (sol, board, lt, cages, order, cand, grade, 달성여부). prev=(직전 빈칸, 직전 lt 수, 직전 cages 수)."""
    n, rules, ltr, cgr, br = LADDER[ch]
    minrank = MIN_GRADE.get((ch, o), 0)
    if minrank == 0:
        target = sched(br, o)
        nlt = sched_down(ltr, o) if ltr else 0
        ncg = sched_down(cgr, o) if cgr else 0
        for attempt in range(400):
            sol = random_solution(rng, n, rules)
            lt = pick_lt(rng, n, sol, nlt) if nlt else []
            cages = []
            if ncg:
                cages = pick_cages(rng, n, sol, ncg)
                if cages is None:
                    continue
            board = [row[:] for row in sol]
            cells = [(r, c) for r in range(n) for c in range(n)]
            rng.shuffle(cells)
            blanks = 0
            for r, c in cells:
                if blanks >= target:
                    break
                v = board[r][c]
                board[r][c] = 0
                if count_solutions(n, rules, board, lt, cages, 2) == 1:
                    blanks += 1
                else:
                    board[r][c] = v
            if blanks != target:
                continue
            key = json.dumps([board, lt, cages])
            if key in seen:
                continue
            g, order, cand = grade_of(n, rules, board, lt, cages)
            if ch <= 6 and g != 0:
                continue
            seen.add(key)
            return sol, board, lt, cages, order, cand, g, True
        return None
    # 최소 등급이 있는 칸: 빈칸을 범위 최대까지 밀고, 제약 개수를 아래쪽으로 줄여 가며 탐색
    floor = max(sched(br, o), prev[0] if prev else 0)
    hi = br[1]
    cap_lt = min(sched_down(ltr, o), prev[1]) if ltr and prev else (sched_down(ltr, o) if ltr else 0)
    cap_cg = min(sched_down(cgr, o), prev[2]) if cgr and prev else (sched_down(cgr, o) if cgr else 0)
    best = None
    MAXATT = 600
    for attempt in range(MAXATT):
        drop = attempt // 25
        nlt = max(ltr[0], cap_lt - drop) if ltr else 0
        ncg = max(cgr[0], cap_cg - drop) if cgr else 0
        sol = random_solution(rng, n, rules)
        lt = pick_lt(rng, n, sol, nlt) if nlt else []
        cages = []
        if ncg:
            cages = pick_cages(rng, n, sol, ncg)
            if cages is None:
                continue
        board = [row[:] for row in sol]
        blanks = remove_greedy(rng, n, rules, board, lt, cages, hi)
        if blanks < floor:
            continue
        # 빈칸 수를 유지한 채 고정 칸을 옮겨 가며(유일성 유지) 등급을 올린다
        if blanks >= floor and hardness(n, rules, board, lt, cages, minrank) == 0:
            climb(rng, n, rules, sol, board, lt, cages, minrank, 120)
            blanks = remove_greedy(rng, n, rules, board, lt, cages, hi)
        key = json.dumps([board, lt, cages])
        if key in seen:
            continue
        g, order, cand = grade_of(n, rules, board, lt, cages)
        item = (sol, board, lt, cages, order, cand, g)
        if g == minrank or (attempt >= 150 and g > minrank):
            seen.add(key)
            return item + (True,)
        # 보관 우선순위: 목표 이상 중 가장 가까운 것 > 목표 미만 중 가장 높은 것
        if best is None or (g >= minrank > best[6]) or (g >= minrank and best[6] >= minrank and g < best[6]) \
                or (g < minrank and best[6] < minrank and g > best[6]):
            best = item
    if best:
        seen.add(json.dumps([best[1], best[2], best[3]]))
        return best + (best[6] >= minrank,)
    return None


# ---------------------------------------------------------------- 텍스트 (7개 언어)
TIP = {
    "rc": {
        "ko": "한 줄(가로·세로)에 같은 숫자가 두 번 나오면 안 돼요. 1부터 {n}까지 한 번씩만 써요.",
        "en": "No number may repeat in a row or a column. Use 1 to {n} exactly once in each line.",
        "ja": "同じ数字は同じ行・列に2回入りません。1から{n}までを1回ずつ使います。",
        "zh": "同一行、同一列不能出现重复数字，1到{n}各用一次。",
        "es": "Ningún número se repite en una fila o columna. Usa del 1 al {n} una sola vez en cada línea.",
        "fr": "Aucun chiffre ne se répète dans une ligne ou une colonne. Utilisez de 1 à {n} une seule fois par ligne.",
        "de": "Keine Zahl darf in einer Zeile oder Spalte doppelt vorkommen. Nutze 1 bis {n} je genau einmal.",
    },
    "box": {
        "ko": "굵은 선으로 나뉜 상자 안에도 1부터 {n}까지 한 번씩 들어가요.",
        "en": "Each box outlined by thick lines also holds 1 to {n} exactly once.",
        "ja": "太い線で区切られたブロックの中にも、1から{n}までが1回ずつ入ります。",
        "zh": "粗线围出的每个方块里，1到{n}也各出现一次。",
        "es": "Cada recuadro delimitado por líneas gruesas también contiene del 1 al {n} una sola vez.",
        "fr": "Chaque bloc délimité par des traits épais contient aussi de 1 à {n} une seule fois.",
        "de": "Auch in jedem dick umrandeten Block steht 1 bis {n} genau einmal.",
    },
    "lt": {
        "ko": "부등호 ‹ 는 뾰족한 쪽이 작은 칸을 가리켜요. 작은 쪽 숫자부터 좁혀 봐요.",
        "en": "The pointed end of an inequality sign ‹ points to the smaller cell. Narrow down the smaller side first.",
        "ja": "不等号 ‹ は、とがった側が小さいマスを指します。小さい方から絞り込みましょう。",
        "zh": "不等号 ‹ 的尖端指向较小的格子，先从较小的一侧缩小范围。",
        "es": "El extremo puntiagudo del signo ‹ señala la casilla menor. Empieza acotando la casilla menor.",
        "fr": "La pointe du signe ‹ désigne la case la plus petite. Commencez par restreindre la plus petite.",
        "de": "Die Spitze des Zeichens ‹ zeigt auf das kleinere Feld. Grenze zuerst das kleinere Feld ein.",
    },
    "cage": {
        "ko": "점선으로 묶인 칸들의 합이 모서리의 숫자와 같아요. 묶음 안에서는 같은 숫자를 쓰지 않아요.",
        "en": "The cells inside a dotted outline add up to the number in its corner. A number is not repeated inside one outline.",
        "ja": "点線で囲まれたマスの合計が、角の数字と同じになります。囲みの中で同じ数字は使いません。",
        "zh": "虚线框内的格子之和等于角上的数字，同一个框里不会有重复数字。",
        "es": "Las casillas dentro de un contorno punteado suman el número de su esquina. No se repite número dentro de un contorno.",
        "fr": "Les cases d'un contour pointillé ont pour somme le nombre indiqué dans son coin. Aucun chiffre ne se répète dans un contour.",
        "de": "Die Felder in einer gestrichelten Umrandung ergeben zusammen die Zahl in der Ecke. Innerhalb einer Umrandung wiederholt sich keine Zahl.",
    },
    "diag": {
        "ko": "두 대각선에도 1부터 {n}까지 한 번씩 들어가요. 대각선 칸도 꼭 살펴봐요.",
        "en": "Both diagonals also hold 1 to {n} exactly once. Check the diagonal cells too.",
        "ja": "2本の対角線にも、1から{n}までが1回ずつ入ります。斜めのマスも確認しましょう。",
        "zh": "两条对角线上，1到{n}也各出现一次，别忘了看对角线上的格子。",
        "es": "Las dos diagonales también contienen del 1 al {n} una sola vez. Revisa también las casillas diagonales.",
        "fr": "Les deux diagonales contiennent aussi de 1 à {n} une seule fois. Surveillez aussi les cases en diagonale.",
        "de": "Auch beide Diagonalen enthalten 1 bis {n} genau einmal. Beachte auch die Diagonalfelder.",
    },
    "mix": {
        "ko": "부등호와 점선 묶음의 합을 함께 써서 후보를 줄여 봐요. 줄·상자 규칙도 잊지 마요.",
        "en": "Use the inequality signs and the dotted-outline sums together to cut down candidates, along with the line and box rules.",
        "ja": "不等号と点線の合計を組み合わせて候補を絞りましょう。行・列・ブロックのルールも忘れずに。",
        "zh": "把不等号和虚线框的和结合起来缩小候选数，同时别忘了行、列、方块的规则。",
        "es": "Combina los signos de desigualdad y las sumas de los contornos punteados para reducir opciones, sin olvidar las reglas de línea y recuadro.",
        "fr": "Combinez les signes d'inégalité et les sommes des contours pointillés pour réduire les possibilités, sans oublier les règles de ligne et de bloc.",
        "de": "Kombiniere die Ungleichheitszeichen und die Summen der gestrichelten Umrandungen, um Kandidaten einzugrenzen. Zeilen- und Blockregeln gelten weiterhin.",
    },
}
CELL = {
    "ko": "{r}행 {c}열은 {v}이에요.",
    "en": "Row {r}, column {c} is {v}.",
    "ja": "{r}行{c}列は{v}です。",
    "zh": "第{r}行第{c}列是{v}。",
    "es": "La fila {r}, columna {c} es {v}.",
    "fr": "Ligne {r}, colonne {c} : {v}.",
    "de": "Zeile {r}, Spalte {c} ist {v}.",
}


def tip_key(ch):
    n, rules, ltr, cgr, br = LADDER[ch]
    if ltr and cgr:
        return "mix"
    if cgr:
        return "cage"
    if ltr:
        return "lt"
    if "diag" in rules:
        return "diag"
    if "box" in rules:
        return "box"
    return "rc"


def hint_cells(n, board, sol, order, cand):
    blanks = [(r * n + c) for r in range(n) for c in range(n) if board[r][c] == 0]
    picked = [cell for cell, _ in order if cell in blanks][:2]
    if len(picked) < 2:
        rest = sorted((x for x in blanks if x not in picked), key=lambda x: bin(cand[x]).count("1"))
        picked += rest[:2 - len(picked)]
    return picked


def make_texts(ch, n, board, sol, order, cand):
    tip = TIP[tip_key(ch)]
    cells = hint_cells(n, board, sol, order, cand)
    hints = {}
    for lang in LANGS:
        hs = [tip[lang].format(n=n)]
        for cell in cells:
            r, c = divmod(cell, n)
            hs.append(CELL[lang].format(r=r + 1, c=c + 1, v=sol[r][c]))
        hints[lang] = hs
    text = " ".join("".join(str(v) for v in row) for row in sol)
    return hints, {lang: text for lang in LANGS}


def gen_chapter(ch, seen_ch, report):
    n, rules, ltr, cgr, br = LADDER[ch]
    out = []
    prev = None
    for o in range(1, 7):
        rng = random.Random(f"fill-{ch}-{o}")
        res = make_one(rng, ch, o, seen_ch, prev)
        assert res, f"fx{ch}_s{o} 생성 실패"
        sol, board, lt, cages, order, cand, g, met = res
        ans = "".join(str(sol[r][c]) for r in range(n) for c in range(n) if board[r][c] == 0)
        prev = (len(ans), len(lt), len(cages))
        hints, expl = make_texts(ch, n, board, sol, order, cand)
        p = {"id": f"fx{ch}_s{o:02d}", "type": "fill", "track": "fill", "chapter": ch, "order": o,
             "board": board, "rules": list(rules)}
        if lt:
            p["lt"] = lt
        if cages:
            p["cages"] = cages
        p.update({"answer": ans, "inputType": "choices", "choices": None, "hints": hints, "explanation": expl})
        out.append(p)
        want = GRADE_NAME[MIN_GRADE.get((ch, o), 0)]
        report.append((p["id"], n, ",".join(rules), len(lt), len(cages), len(ans), GRADE_NAME[g],
                       "" if met else f"MISS(want {want})"))
    return out


def solution_of(p):
    n = len(p["board"])
    rules, lt, cages = p["rules"], p.get("lt") or [], p.get("cages") or []
    return solve_one(n, rules, p["board"], lt, cages)


def build_vectors(puzzles):
    cases = []
    for p in puzzles:
        if p["order"] not in (1, 6) and p["chapter"] not in (4, 6, 14):
            continue
        n = len(p["board"])
        case = {"id": p["id"], "board": p["board"], "rules": p["rules"]}
        if p.get("lt"):
            case["lt"] = p["lt"]
        if p.get("cages"):
            case["cages"] = p["cages"]
        case["solutions"] = count_solutions(n, p["rules"], p["board"], p.get("lt") or [], p.get("cages") or [], 3)
        case["answer"] = p["answer"]
        case["blanks"] = len(p["answer"])
        cases.append(case)
    return {
        "format": ("격자 채우기 테스트 벡터(명세 docs/fill-path-mode-spec.md §1). cases[]: {id, board(0=빈칸), rules, lt?, cages?, "
                   "solutions(=1, 백트래킹으로 센 해 개수), answer(빈칸 정답을 행 우선으로 이어 붙인 문자열), blanks}. "
                   "lt 항목 [r1,c1,r2,c2] = 칸(r1,c1) < 칸(r2,c2), cages 항목 [합, r1,c1, r2,c2, ...]. 앱은 파싱·표시만 하면 되고 "
                   "정답은 answer 를 그대로 쓴다(규칙 위반 표시 로직만 앱에 필요)."),
        "cases": cases,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=PUZZLES)
    ap.add_argument("--vectors", default=VECTORS)
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    puzzles, report = [], []
    for ch in range(1, 15):
        puzzles.extend(gen_chapter(ch, set(), report))
    print("id | N | rules | lt | cages | blanks | grade")
    for r in report:
        print(" | ".join(str(x) for x in r))
    if a.dry:
        return
    with open(a.puzzles, encoding="utf8") as f:
        data = json.load(f)
    idx = [i for i, p in enumerate(data) if p.get("track") == "fill"]
    if idx:
        pos = idx[0]
        data = [p for p in data if p.get("track") != "fill"]
        data[pos:pos] = puzzles
    else:
        data.extend(puzzles)
    with open(a.puzzles, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    with open(a.vectors, "w", encoding="utf8") as f:
        f.write(json.dumps(build_vectors(puzzles), indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {len(puzzles)} fill puzzles; total {len(data)}")


if __name__ == "__main__":
    main()
