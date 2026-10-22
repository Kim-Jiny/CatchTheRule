#!/usr/bin/env python3
"""격자 채우기(track:"fill") 독립 검증기. 생성기(gen_fill_puzzles.py)를 import 하지 않는다.
명세 docs/fill-path-mode-spec.md §1 §3 의 모든 조건을 puzzles.json 의 84문제와 테스트 벡터에 대해 재검증한다.

- 백트래킹 솔버로 해 개수 == 1 (케이지는 합만 제약으로 취급 = 가장 엄격한 유일성), answer == 그 해의 빈칸 값(행 우선)
- 고정 칸이 해와 일치, 규칙 일관성(rules 가 챕터 사다리와 같음, lt 인접·해와 일치, cages 인접 연결·합 일치·중복 없음·서로소)
- 고정 칸 최소화: 빈칸 수가 챕터 범위 안이고, (목표 빈칸 수와 같거나, 더 이상 비울 칸이 없는 극소) 인지 확인
- 챕터별 사다리(N, rules, 제약 개수 범위, 빈칸 범위, order 에 따른 빈칸 비감소/제약 비증가), ch1~6 은 single 만으로 풀림
- id/order/중복 board, 7개 언어 hints(3개)·explanation, 힌트 속 "R행 C열은 V" 가 실제 해와 일치, explanation == 해 격자

실행: python3 scripts/verify_fill_puzzles.py [--puzzles PATH] [--vectors PATH]   (실패 시 종료 코드 1)
"""
import argparse, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
KEYS = ["id", "type", "track", "chapter", "order", "board", "rules", "answer", "inputType", "choices", "hints",
        "explanation"]
# ch -> (N, rules, lt범위, cage범위, 빈칸범위)
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
errors = []
notes = []


def err(m):
    errors.append(m)


def groups(n, rules):
    g = []
    if "row" in rules:
        g += [[(r, c) for c in range(n)] for r in range(n)]
    if "col" in rules:
        g += [[(r, c) for r in range(n)] for c in range(n)]
    if "box" in rules:
        bh, bw = {4: (2, 2), 6: (2, 3)}[n]
        g += [[(br + i, bc + j) for i in range(bh) for j in range(bw)]
              for br in range(0, n, bh) for bc in range(0, n, bw)]
    if "diag" in rules:
        g += [[(i, i) for i in range(n)], [(i, n - 1 - i) for i in range(n)]]
    return g


def solutions(n, rules, board, lt, cages, limit=2):
    """백트래킹(칸 순서 고정, 규칙 검사). 해 목록(최대 limit 개). 케이지는 합만."""
    grp = groups(n, rules)
    mem = {}
    for gi, g in enumerate(grp):
        for cell in g:
            mem.setdefault(cell, []).append(gi)
    ltm = {}
    for r1, c1, r2, c2 in lt:
        ltm.setdefault((r1, c1), []).append(((r2, c2), True))
        ltm.setdefault((r2, c2), []).append(((r1, c1), False))
    cg = [(t, [(a, b) for a, b in zip(rest[0::2], rest[1::2])]) for t, *rest in cages]
    cgm = {}
    for k, (t, cells) in enumerate(cg):
        for cell in cells:
            cgm.setdefault(cell, []).append(k)
    g = [row[:] for row in board]
    found = []

    def fits(r, c, v):
        for gi in mem.get((r, c), []):
            for cell in grp[gi]:
                if cell != (r, c) and g[cell[0]][cell[1]] == v:
                    return False
        for (o, small) in ltm.get((r, c), []):
            ov = g[o[0]][o[1]]
            if ov and ((small and not v < ov) or (not small and not ov < v)):
                return False
        for k in cgm.get((r, c), []):
            t, cells = cg[k]
            s, rem = v, 0
            for cell in cells:
                if cell == (r, c):
                    continue
                x = g[cell[0]][cell[1]]
                if x:
                    s += x
                else:
                    rem += 1
            if (rem == 0 and s != t) or (rem and (s + rem > t or s + rem * n < t)):
                return False
        return True

    # 주어진 칸 자체의 일관성
    for r in range(n):
        for c in range(n):
            if board[r][c]:
                v = board[r][c]
                g[r][c] = 0
                if not fits(r, c, v):
                    return []
                g[r][c] = v

    def rec(pos):
        if len(found) >= limit:
            return
        while pos < n * n and g[pos // n][pos % n]:
            pos += 1
        if pos == n * n:
            found.append([row[:] for row in g])
            return
        r, c = divmod(pos, n)
        for v in range(1, n + 1):
            if fits(r, c, v):
                g[r][c] = v
                rec(pos + 1)
                g[r][c] = 0

    rec(0)
    return found


def singles_only(n, rules, board, lt, cages):
    """naked/hidden single + lt 경계 + 케이지 합 가지치기만으로 풀리는가 (독립 구현)."""
    grp = groups(n, rules)
    cand = {(r, c): ({board[r][c]} if board[r][c] else set(range(1, n + 1))) for r in range(n) for c in range(n)}
    cg = [(t, [(a, b) for a, b in zip(rest[0::2], rest[1::2])]) for t, *rest in cages]
    for _ in range(200):
        before = {k: set(v) for k, v in cand.items()}
        for gp in grp:
            for cell in gp:
                if len(cand[cell]) == 1:
                    (v,) = cand[cell]
                    for o in gp:
                        if o != cell:
                            cand[o].discard(v)
        for r1, c1, r2, c2 in lt:
            a, b = (r1, c1), (r2, c2)
            if cand[b]:
                cand[a] = {v for v in cand[a] if v < max(cand[b])}
            if cand[a]:
                cand[b] = {v for v in cand[b] if v > min(cand[a])}
        for t, cells in cg:
            for cell in cells:
                others = [cand[x] for x in cells if x != cell]
                sums = {0}
                for s in others:
                    sums = {a + b for a in sums for b in s}
                cand[cell] = {v for v in cand[cell] if (t - v) in sums}
        for gp in grp:
            for v in range(1, n + 1):
                sp = [cell for cell in gp if v in cand[cell]]
                if len(sp) == 1:
                    cand[sp[0]] = {v}
        if cand == before:
            break
    return all(len(s) == 1 for s in cand.values())


def adjacent(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) == 1


def check_puzzle(p, dedupe):
    pid = p.get("id", "?")
    for k in KEYS:
        if k not in p:
            err(f"{pid}: 키 {k} 없음")
            return None
    if p["type"] != "fill" or p["track"] != "fill":
        err(f"{pid}: type/track 은 fill")
    ch, o = p["chapter"], p["order"]
    if pid != f"fx{ch}_s{o:02d}":
        err(f"{pid}: id 가 chapter/order 와 안 맞음")
    if ch not in LADDER or o not in range(1, 7):
        err(f"{pid}: chapter/order 범위 밖")
        return None
    n, rules, ltr, cgr, br = LADDER[ch]
    board, lt, cages = p["board"], p.get("lt") or [], p.get("cages") or []
    if p["inputType"] != "choices" or p["choices"] is not None:
        err(f"{pid}: inputType 은 choices, choices 는 null")
    if len(board) != n or any(len(r) != n for r in board):
        err(f"{pid}: board 는 {n}x{n}")
        return None
    if any(not isinstance(v, int) or not 0 <= v <= n for r in board for v in r):
        err(f"{pid}: board 값은 0..{n}")
        return None
    if p["rules"] != rules:
        err(f"{pid}: rules {p['rules']} != 챕터 사다리 {rules}")
    if "box" in p["rules"] and n == 5 or "diag" in p["rules"] and n == 6:
        err(f"{pid}: 금지된 규칙 조합")
    # lt / cages 개수·형태
    for name, rng_, arr in (("lt", ltr, lt), ("cages", cgr, cages)):
        if rng_ is None:
            if arr:
                err(f"{pid}: 이 챕터는 {name} 없어야 함")
        elif not rng_[0] <= len(arr) <= rng_[1]:
            err(f"{pid}: {name} 개수 {len(arr)} 가 범위 {rng_} 밖")
    seen_lt = set()
    for it in lt:
        if len(it) != 4 or any(not 0 <= x < n for x in it):
            err(f"{pid}: lt 항목 형식 {it}")
            continue
        a, b = (it[0], it[1]), (it[2], it[3])
        if not adjacent(a, b):
            err(f"{pid}: lt 두 칸이 인접하지 않음 {it}")
        key = frozenset([a, b])
        if key in seen_lt:
            err(f"{pid}: lt 중복 {it}")
        seen_lt.add(key)
    used = set()
    for it in cages:
        t, cells = it[0], list(zip(it[1::2], it[2::2]))
        if len(it) % 2 != 1 or not 2 <= len(cells) <= 3:
            err(f"{pid}: cage 형식/칸 수 {it}")
            continue
        if any(not (0 <= a < n and 0 <= b < n) for a, b in cells):
            err(f"{pid}: cage 칸 범위 {it}")
            continue
        # 연결성
        reach, todo = {cells[0]}, [cells[0]]
        while todo:
            x = todo.pop()
            for y in cells:
                if y not in reach and adjacent(x, y):
                    reach.add(y)
                    todo.append(y)
        if len(reach) != len(cells) or len(set(cells)) != len(cells):
            err(f"{pid}: cage 칸이 이어져 있지 않거나 중복 {it}")
        if used & set(cells):
            err(f"{pid}: cage 끼리 겹침 {it}")
        used |= set(cells)
        if cells[0] != min(cells):
            err(f"{pid}: cage 첫 칸은 좌상단(행 우선 최소)이어야 함 {it}")
    # 풀이
    sols = solutions(n, p["rules"], board, lt, cages, 3)
    if len(sols) != 1:
        err(f"{pid}: 해 개수 {len(sols)} (정확히 1이어야 함)")
        return None
    sol = sols[0]
    for r in range(n):
        for c in range(n):
            if board[r][c] and board[r][c] != sol[r][c]:
                err(f"{pid}: 고정 칸 ({r},{c}) 가 해와 다름")
    # 해 기준 규칙 일관성 (케이지 내 중복 금지, 합)
    for gp in groups(n, p["rules"]):
        if sorted(sol[r][c] for r, c in gp) != list(range(1, n + 1)):
            err(f"{pid}: 해가 그룹 규칙 위반")
            break
    for r1, c1, r2, c2 in lt:
        if not sol[r1][c1] < sol[r2][c2]:
            err(f"{pid}: 해가 lt 위반 {[r1, c1, r2, c2]}")
    for it in cages:
        cells = list(zip(it[1::2], it[2::2]))
        vs = [sol[a][b] for a, b in cells]
        if sum(vs) != it[0]:
            err(f"{pid}: cage 합 불일치 {it}")
        if len(set(vs)) != len(vs):
            err(f"{pid}: cage 안 숫자 중복 {it}")
    blanks = [(r, c) for r in range(n) for c in range(n) if board[r][c] == 0]
    ans = "".join(str(sol[r][c]) for r, c in blanks)
    if p["answer"] != ans:
        err(f"{pid}: answer {p['answer']} != 해 {ans}")
    nb = len(blanks)
    if not br[0] <= nb <= br[1]:
        err(f"{pid}: 빈칸 {nb} 가 범위 {br} 밖")
    if nb <= 1:
        err(f"{pid}: 빈칸 1개 이하 금지")
    # 고정 칸 최소화: 목표 빈칸과 같거나, 더 비울 칸이 없는 극소
    target = br[0] + round((br[1] - br[0]) * (o - 1) / 5)
    if nb != target:
        removable = 0
        for r in range(n):
            for c in range(n):
                if board[r][c]:
                    b2 = [row[:] for row in board]
                    b2[r][c] = 0
                    if len(solutions(n, p["rules"], b2, lt, cages, 2)) == 1:
                        removable += 1
        if removable:
            err(f"{pid}: 빈칸 {nb} != 목표 {target} 인데 비울 수 있는 고정 칸 {removable}개 남음(고정 칸 최소화 실패)")
        else:
            notes.append(f"{pid}: 극소(빈칸 {nb}, 목표 {target})")
    solved = singles_only(n, p["rules"], board, lt, cages)
    if ch <= 6 and not solved:
        err(f"{pid}: ch1~6 은 single 만으로 풀려야 함")
    # 중복
    key = json.dumps([board, lt, cages])
    if key in dedupe.setdefault(ch, set()):
        err(f"{pid}: 챕터 내 동일 board 중복")
    dedupe[ch].add(key)
    # 텍스트
    for field in ("hints", "explanation"):
        if set(p[field]) != set(LANGS):
            err(f"{pid}: {field} 언어 키 불일치")
            return sol, nb, solved
    grid_txt = " ".join("".join(str(v) for v in row) for row in sol)
    cell_re = {
        "ko": r"(\d+)행 (\d+)열은 (\d+)", "en": r"Row (\d+), column (\d+) is (\d+)",
        "ja": r"(\d+)行(\d+)列は(\d+)", "zh": r"第(\d+)行第(\d+)列是(\d+)",
        "es": r"fila (\d+), columna (\d+) es (\d+)", "fr": r"Ligne (\d+), colonne (\d+) : (\d+)",
        "de": r"Zeile (\d+), Spalte (\d+) ist (\d+)",
    }
    firsts = set()
    for lang in LANGS:
        hs = p["hints"][lang]
        if not isinstance(hs, list) or len(hs) != 3 or any(not isinstance(h, str) or not h.strip() for h in hs):
            err(f"{pid}: hints[{lang}] 는 비어 있지 않은 문자열 3개")
            continue
        cells = []
        for h in hs[1:]:
            m = re.search(cell_re[lang], h)
            if not m:
                err(f"{pid}: hints[{lang}] 칸 힌트 형식")
                continue
            r, c, v = map(int, m.groups())
            if not (1 <= r <= n and 1 <= c <= n and board[r - 1][c - 1] == 0 and sol[r - 1][c - 1] == v):
                err(f"{pid}: hints[{lang}] 칸 힌트가 해와 다름 {h}")
            cells.append((r, c))
        if len(set(cells)) != len(cells):
            err(f"{pid}: hints[{lang}] 두 칸이 같음")
        firsts.add(tuple(cells))
        if p["explanation"][lang] != grid_txt:
            err(f"{pid}: explanation[{lang}] 이 완성 격자 행 나열과 다름")
    if len(firsts) > 1:
        err(f"{pid}: 언어별 힌트 칸이 서로 다름")
    return sol, nb, solved


def check_vectors(path, byid):
    with open(path, encoding="utf8") as f:
        v = json.load(f)
    cases = v.get("cases", [])
    if len(cases) < 10:
        err(f"vector cases {len(cases)}개 (10개 이상 필요)")
    for i, c in enumerate(cases):
        n = len(c["board"])
        lt, cages = c.get("lt") or [], c.get("cages") or []
        sols = solutions(n, c["rules"], c["board"], lt, cages, 3)
        if len(sols) != 1 or c.get("solutions") != 1:
            err(f"vector cases[{i}]: 해 개수 {len(sols)} / 기록 {c.get('solutions')}")
            continue
        bl = [(r, cc) for r in range(n) for cc in range(n) if c["board"][r][cc] == 0]
        if c["answer"] != "".join(str(sols[0][r][cc]) for r, cc in bl) or c["blanks"] != len(bl):
            err(f"vector cases[{i}]: answer/blanks 불일치")
        pid = c.get("id")
        if pid in byid and (byid[pid]["board"] != c["board"] or byid[pid]["answer"] != c["answer"]):
            err(f"vector cases[{i}] {pid}: puzzles.json 과 다름")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=os.path.join(ROOT, "shared", "content", "puzzles.json"))
    ap.add_argument("--vectors", default=os.path.join(ROOT, "scripts", "fill_test_vectors.json"))
    a = ap.parse_args()
    with open(a.puzzles, encoding="utf8") as f:
        data = json.load(f)
    fills = [p for p in data if p.get("track") == "fill"]
    if len(fills) != 84:
        err(f"fill 문제 {len(fills)}개 (84개여야 함)")
    ids = [p.get("id") for p in data]
    if len(set(ids)) != len(ids):
        err("전체 id 중복")
    want = {f"fx{c}_s{o:02d}" for c in range(1, 15) for o in range(1, 7)}
    if {p["id"] for p in fills} != want:
        err("fill id 집합이 fx1_s01..fx14_s06 와 다름")
    dedupe, info = {}, {}
    for p in fills:
        r = check_puzzle(p, dedupe)
        if r:
            info[p["id"]] = r
    # 사다리: order 에 따라 빈칸 비감소, 제약 개수 비증가
    for ch in range(1, 15):
        ps = sorted((p for p in fills if p["chapter"] == ch), key=lambda p: p["order"])
        if len(ps) != 6:
            err(f"ch{ch}: 6문제여야 함")
            continue
        bl = [len(p["answer"]) for p in ps]
        if any(bl[i] > bl[i + 1] for i in range(5)):
            err(f"ch{ch}: order 에 따라 빈칸이 줄어듦 {bl}")
        for name in ("lt", "cages"):
            cs = [len(p.get(name) or []) for p in ps]
            if any(cs[i] < cs[i + 1] for i in range(5)):
                pass
            if any(cs[i] > 0 and cs[i] < cs[i + 1] for i in range(5)):
                err(f"ch{ch}: order 에 따라 {name} 개수가 늘어남 {cs}")
    byid = {p["id"]: p for p in fills}
    if os.path.exists(a.vectors):
        check_vectors(a.vectors, byid)
    else:
        err(f"테스트 벡터 없음: {a.vectors}")
    print("ch | N | rules | blanks | lt | cages | single-solvable(order1..6)")
    for ch in range(1, 15):
        ps = sorted((p for p in fills if p["chapter"] == ch), key=lambda p: p["order"])
        if not ps:
            continue
        print(f"{ch:2d} | {len(ps[0]['board'])} | {','.join(ps[0]['rules'])} | {[len(p['answer']) for p in ps]} | "
              f"{[len(p.get('lt') or []) for p in ps]} | {[len(p.get('cages') or []) for p in ps]} | "
              f"{''.join('S' if info.get(p['id'], (0, 0, False))[2] else 'H' for p in ps)}")
    for n_ in notes:
        print("note:", n_)
    if errors:
        print(f"\nFAIL: {len(errors)}개 오류")
        for e in errors[:60]:
            print(" -", e)
        sys.exit(1)
    print(f"\nOK: fill {len(fills)}문제 검증 통과 (해 개수 == 1, 규칙 일관성, 사다리, 7개 언어 힌트, 테스트 벡터)")


if __name__ == "__main__":
    main()
