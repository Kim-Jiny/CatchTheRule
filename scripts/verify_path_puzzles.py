#!/usr/bin/env python3
"""경로 추적(track:"path") 독립 검증기. 생성기(gen_path_puzzles.py)를 import 하지 않는다.
명세 docs/fill-path-mode-spec.md §2 §3 의 조건을 puzzles.json 의 84문제와 테스트 벡터에 대해 재검증한다.

- 유일성: 방향 규칙(cycle p=1..4 의 모든 8^p 방향 목록, rot 의 시작 8 x cw/ccw x 45/90) x 길이 규칙(const, cycle q=2..3,
  arith(a, +-1, cap 래핑 또는 순수), grow) 의 전체 가설 공간을 무식하게 전수 열거해 trail 을 재현하고 total 번 이동 내내
  격자 안에 머무는 가설의 최종 칸이 모두 같은지(= answer) 본다.
- 사다리: 챕터별 N, m, total-m, 방향/길이 규칙 계열, 대각선 사용 여부를 관측 가능한 것(재현 가능한 가설)으로 확인한다.
- 힌트/해설: 3개 힌트 x 7개 언어, 힌트 3 의 다음 칸·해설의 이동열이 trail/answer 와 일치하는지 교차 검증한다.

실행: python3 scripts/verify_path_puzzles.py [--puzzles PATH] [--vectors PATH]   (실패 시 종료 코드 1)
"""
import argparse, itertools, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
KEYS = ["id", "type", "track", "chapter", "order", "size", "start", "trail", "total", "answer", "inputType",
        "choices", "hints", "explanation"]
V8 = [(-1, 0), (-1, 1), (0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1)]  # U UR R DR D DL L UL
ARROWS = "↑↗→↘↓↙←↖"
NAMES = ["U", "UR", "R", "DR", "D", "DL", "L", "UL"]

# 챕터 -> (N, m 범위, total-m 범위, 방향 조건, 길이 조건, 대각선 필요)
#  방향 조건: ("cyc", p) 최소 반복 주기 == p  /  ("rot", step) 해당 rot 가설 존재 / ("any",)
#  길이 조건: ("const", lo, hi) const a 가능 / ("cyc", q) 최소 길이 주기 == q (상수 불가) / ("arith",) arith 가설 존재 /
#            ("grow",) grow 가설 존재 / ("nonconst",)
LADDER = {
    1: (5, (4, 4), (2, 2), ("cyc", 2), ("const", 1, 2), False),
    2: (5, (5, 5), (2, 3), ("cyc", 4), ("const", 1, 1), False),
    3: (5, (5, 5), (2, 3), ("rot", 90), ("const", 1, 2), False),
    4: (6, (5, 5), (2, 3), ("cyc", 2), ("cyc", 2), False),
    5: (6, (6, 6), (3, 3), ("rot", 90), ("cyc", 2), False),
    6: (6, (6, 6), (3, 3), ("cyc", 3), ("arith",), False),
    7: (6, (6, 6), (3, 3), ("rot", 90), ("grow",), False),
    8: (6, (5, 5), (3, 3), ("cyc", 2), ("const", 1, 2), True),
    9: (7, (6, 6), (3, 3), ("rot", 45), ("const", 1, 2), True),
    10: (7, (7, 7), (3, 4), ("cyc", 3), ("cyc", 2), True),
    11: (7, (7, 7), (3, 4), ("rot", 90), ("arith",), False),
    12: (7, (8, 8), (4, 4), ("rot", 45), ("cyc", 3), True),
    13: (7, (9, 9), (4, 4), ("cyc", 4), ("cyc", 3), True),
    14: (7, (8, 9), (4, 5), ("any",), ("nonconst",), False),
}
INTRAIL_EXEMPT = (3, 5, 9)  # 주기 이동이라 최종 칸이 자취와 겹치는 게 본질적인 챕터

errors = []


def err(msg):
    errors.append(msg)


# ---------------------------------------------------------------- 관측 / 가설
def moves_of(trail):
    """trail -> [(방향 idx, 길이)] 또는 None(8방향 직선 이동이 아닌 이동이 있음)."""
    out = []
    for (r0, c0), (r1, c1) in zip(trail, trail[1:]):
        dr, dc = r1 - r0, c1 - c0
        L = max(abs(dr), abs(dc))
        if L == 0:
            return None
        v = (dr // L if dr % L == 0 else 99, dc // L if dc % L == 0 else 99)
        if v not in V8 or (dr, dc) != (v[0] * L, v[1] * L):
            return None
        out.append((V8.index(v), L))
    return out


def dir_hypotheses(obs_d, T):
    """[(태그, 방향열)] 전수 열거."""
    m = len(obs_d)
    res = []
    for p in range(1, 5):
        for cyc in itertools.product(range(8), repeat=p):
            if all(obs_d[k] == cyc[k % p] for k in range(m)):
                res.append((("cyc", p, cyc), tuple(cyc[k % p] for k in range(T))))
    for s in range(8):
        for sense in (1, -1):
            for step in (90, 45):
                seq = tuple((s + sense * (step // 45) * k) % 8 for k in range(T))
                if list(seq[:m]) == list(obs_d):
                    res.append((("rot", step, s, sense), seq))
    return res


def arith(a, d, cap, T):
    out, x = [], a
    for _ in range(T):
        if x < 1:
            return None
        out.append(x)
        x += d
        if cap is not None:
            if x > cap:
                x = 1
            elif x < 1:
                x = cap
    return tuple(out)


def len_hypotheses(obs_l, T):
    m = len(obs_l)
    res = []
    for a in range(1, 7):
        s = tuple([a] * T)
        if list(s[:m]) == list(obs_l):
            res.append((("const", a), s))
    for q in (2, 3):
        for ls in itertools.product(range(1, 5), repeat=q):
            s = tuple(ls[k % q] for k in range(T))
            if list(s[:m]) == list(obs_l):
                res.append((("cyc", q, ls), s))
    for cap in (None, 2, 3, 4, 5):
        for a in range(1, 6):
            if cap is not None and a > cap:
                continue
            for d in (1, -1):
                s = arith(a, d, cap, T)
                if s is not None and list(s[:m]) == list(obs_l):
                    res.append((("arith", a, d, cap), s))
                    if cap is not None and a == 1 and d == 1:
                        res.append((("grow", cap), s))
    return res


def walk(size, start, ds, ls):
    r, c = start
    for d, l in zip(ds, ls):
        r += V8[d][0] * l
        c += V8[d][1] * l
        if not (0 <= r < size and 0 <= c < size):
            return None
    return (r, c)


def min_period(seq):
    for d in range(1, len(seq) + 1):
        if all(seq[i] == seq[i % d] for i in range(len(seq))):
            return d
    return len(seq)


def analyze(size, trail, total):
    """(관측 이동, 격자 안에 머무는 서로 다른 (방향열,길이열) 수, 최종 칸 집합, 가설 태그 목록 dir, len)."""
    obs = moves_of(trail)
    if obs is None:
        return None
    od, ol = [d for d, _ in obs], [l for _, l in obs]
    dh, lh = dir_hypotheses(od, total), len_hypotheses(ol, total)
    seen, finals = set(), set()
    for _, ds in dh:
        for _, ls in lh:
            if (ds, ls) in seen:
                continue
            f = walk(size, tuple(trail[0]), ds, ls)
            if f is not None:
                seen.add((ds, ls))
                finals.add(f)
    return obs, len(seen), finals, [t for t, _ in dh], [t for t, _ in lh]


# ---------------------------------------------------------------- 챕터 사다리
def ladder_check(pid, ch, size, m, extra, obs, dtags, ltags):
    n, mr, er, dcond, lcond, need_diag = LADDER[ch]
    if size != n:
        err(f"{pid}: size {size} != {n}")
    if not mr[0] <= m <= mr[1]:
        err(f"{pid}: 챕터 {ch} m={m} 가 범위 {mr} 밖")
    if not er[0] <= extra <= er[1]:
        err(f"{pid}: 챕터 {ch} total-m={extra} 가 범위 {er} 밖")
    diag_moves = sum(1 for d, _ in obs if d % 2 == 1)
    if need_diag and diag_moves == 0:
        err(f"{pid}: 챕터 {ch} 는 자취에 대각선 이동이 있어야 함")
    if not need_diag and ch <= 7 and diag_moves:
        err(f"{pid}: 챕터 {ch} 자취에 대각선 이동이 있음")
    if len({d for d, _ in obs}) < 2:
        err(f"{pid}: 방향이 한 가지뿐(단순 반복)")
    mps = [t[1] for t in dtags if t[0] == "cyc"]
    mp = min(mps) if mps else None
    if dcond[0] == "cyc" and mp != dcond[1]:
        err(f"{pid}: 챕터 {ch} 방향 반복 주기 {mp} != {dcond[1]}")
    if dcond[0] == "rot" and not any(t[0] == "rot" and t[1] == dcond[1] for t in dtags):
        err(f"{pid}: 챕터 {ch} rot({dcond[1]}) 가설 없음")
    lk = lcond[0]
    consts = [t[1] for t in ltags if t[0] == "const"]
    if lk == "const":
        if not any(lcond[1] <= a <= lcond[2] for a in consts):
            err(f"{pid}: 챕터 {ch} 길이가 const {lcond[1]}~{lcond[2]} 가 아님")
    else:
        if consts:
            err(f"{pid}: 챕터 {ch} 길이가 상수로 해석됨(길이 규칙 없음)")
        if lk == "cyc":
            qs = [t[1] for t in ltags if t[0] == "cyc"]
            if not qs or min(qs) != lcond[1]:
                err(f"{pid}: 챕터 {ch} 길이 반복 주기 {min(qs) if qs else None} != {lcond[1]}")
        if lk == "arith" and not any(t[0] == "arith" for t in ltags):
            err(f"{pid}: 챕터 {ch} arith 가설 없음")
        if lk == "grow" and not any(t[0] == "grow" for t in ltags):
            err(f"{pid}: 챕터 {ch} grow 가설 없음")


# ---------------------------------------------------------------- 텍스트
def check_texts(pid, p, obs, full_moves, pos_next):
    h, ex = p["hints"], p["explanation"]
    if not isinstance(h, dict) or list(h.keys()) != LANGS or not isinstance(ex, dict) or list(ex.keys()) != LANGS:
        err(f"{pid}: hints/explanation 언어 키가 {LANGS} 가 아님")
        return
    m, T = len(p["trail"]) - 1, p["total"]
    for l in LANGS:
        if not (isinstance(h[l], list) and len(h[l]) == 3 and all(isinstance(s, str) and s.strip() for s in h[l])):
            err(f"{pid}: hints[{l}] 은 비어있지 않은 문자열 3개여야 함")
            continue
        nums = list(map(int, re.findall(r"\d+", h[l][2])))
        if nums != [pos_next[0] + 1, pos_next[1] + 1]:
            err(f"{pid}: hints[{l}][2] 의 다음 칸 {nums} != {[pos_next[0] + 1, pos_next[1] + 1]}")
        arrows = [ARROWS.index(ch) for ch in h[l][1] if ch in ARROWS]
        lens = [int(x) for x in re.findall(r"\d+", h[l][1])]
        if arrows:
            want = [d for d, _ in full_moves[m:m + 3]]
            if arrows != want:
                err(f"{pid}: hints[{l}][1] 방향 {arrows} != {want}")
        elif lens:
            want = [x for _, x in full_moves[m:m + 3]]
            if lens != want:
                err(f"{pid}: hints[{l}][1] 길이 {lens} != {want}")
        else:
            err(f"{pid}: hints[{l}][1] 에 방향/길이 단서가 없음")
        if not (isinstance(ex[l], str) and ex[l].strip()):
            err(f"{pid}: explanation[{l}] 비어 있음")
            continue
        toks = re.findall(r"([↑↗→↘↓↙←↖])(\d+)", ex[l])
        got = [(ARROWS.index(a), int(n)) for a, n in toks]
        if got != full_moves:
            err(f"{pid}: explanation[{l}] 이동열이 trail/답과 다름")
        tail = list(map(int, re.findall(r"\d+", ex[l].split("=")[-1]))) if "=" in ex[l] else None
        size = p["size"]
        a = int(p["answer"]) - 1
        if tail != [a // size + 1, a % size + 1]:
            err(f"{pid}: explanation[{l}] 마지막 R,C {tail} != answer")
    for i in (0, 2):
        if len({h[l][i] for l in LANGS}) != 7:
            err(f"{pid}: hints 의 {i + 1}번째 문장이 언어마다 달라야 함(번역 누락 의심)")


# ---------------------------------------------------------------- 메인
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=os.path.join(ROOT, "shared", "content", "puzzles.json"))
    ap.add_argument("--vectors", default=os.path.join(ROOT, "scripts", "path_test_vectors.json"))
    a = ap.parse_args()
    with open(a.puzzles, encoding="utf8") as f:
        data = json.load(f)
    pp = [p for p in data if p.get("track") == "path"]
    idx = [i for i, p in enumerate(data) if p.get("track") == "path"]
    if idx and idx[-1] - idx[0] + 1 != len(idx):
        err("path 항목이 다른 모드 항목과 섞여 있음(연속된 한 덩어리여야 함)")
    if len(pp) != 84:
        err(f"path 문제 수 {len(pp)} != 84")
    if [p.get("id") for p in pp] != [f"pc{ch}_s{o:02d}" for ch in range(1, 15) for o in range(1, 7)]:
        err("id/순서가 pc1_s01..pc14_s06 순서와 다름")
    ids = [p.get("id") for p in data]
    if len(set(ids)) != len(ids):
        err("puzzles.json 전체에 중복 id 가 있음")

    seen_key, intrail, chap_stats, hyp_counts = {}, {}, {}, {}
    for p in pp:
        pid = p.get("id", "?")
        if list(p.keys()) != KEYS:
            err(f"{pid}: 키 구성/순서가 {KEYS} 와 다름: {list(p.keys())}")
            continue
        ch, o = p["chapter"], p["order"]
        if p["type"] != "path" or p["track"] != "path" or p["inputType"] != "choices" or p["choices"] is not None:
            err(f"{pid}: type/track/inputType/choices 오류")
        if pid != f"pc{ch}_s{o:02d}":
            err(f"{pid}: id 가 chapter/order 와 안 맞음")
        size, trail, T = p["size"], p["trail"], p["total"]
        m = len(trail) - 1
        if not all(isinstance(c, list) and len(c) == 2 and 0 <= c[0] < size and 0 <= c[1] < size for c in trail):
            err(f"{pid}: trail 에 격자 밖/형식 오류 칸")
            continue
        if p["start"] != trail[0]:
            err(f"{pid}: start != trail[0]")
        if T - m < 2:
            err(f"{pid}: total-m {T - m} < 2")
        res = analyze(size, trail, T)
        if res is None:
            err(f"{pid}: trail 에 8방향 직선이 아닌 이동이 있음")
            continue
        obs, nh, finals, dtags, ltags = res
        if nh == 0 or len(finals) != 1:
            err(f"{pid}: 최종 칸이 유일하지 않음 (가설 {nh}, 최종 칸 {sorted(finals)})")
            continue
        fr, fc = next(iter(finals))
        if p["answer"] != str(fr * size + fc + 1):
            err(f"{pid}: answer {p['answer']} != 유일 해 {fr * size + fc + 1}")
        hyp_counts[pid] = nh
        ladder_check(pid, ch, size, m, T - m, obs, dtags, ltags)
        key = json.dumps([ch, size, trail, T])
        if key in seen_key:
            err(f"{pid}: {seen_key[key]} 와 (size,trail,total) 중복")
        seen_key[key] = pid
        if [fr, fc] in trail:
            intrail[ch] = intrail.get(ch, 0) + 1
        chap_stats.setdefault(ch, []).append((o, -m, T - m))
        # 전체 이동열(참 규칙 후보 하나로 확정 가능: 유일 최종 칸을 낳는 가설들 중 첫 번째)을 해설과 대조
        obs_d = [d for d, _ in obs]
        obs_l = [l for _, l in obs]
        full = None
        for _, ds in dir_hypotheses(obs_d, T):
            for _, ls in len_hypotheses(obs_l, T):
                if walk(size, tuple(trail[0]), ds, ls) is not None:
                    full = list(zip(ds, ls))
                    break
            if full:
                break
        # 다음 칸
        r, c = trail[-1]
        r += V8[full[m][0]][0] * full[m][1]
        c += V8[full[m][0]][1] * full[m][1]
        check_texts(pid, p, obs, full, (r, c))
    for ch, n in intrail.items():
        if n > 1 and ch not in INTRAIL_EXEMPT:
            err(f"챕터 {ch}: 최종 칸이 trail 과 겹치는 문제 {n}개(최대 1)")
    for ch, st in chap_stats.items():
        key = [(mm, ee) for _, mm, ee in sorted(st)]
        if key != sorted(key):
            err(f"챕터 {ch}: order 1->6 으로 m 은 줄고(또는 같고) total-m 은 늘어야 함: {key}")

    verify_vectors(a.vectors, pp)

    if errors:
        print(f"FAIL: {len(errors)} 건")
        for e in errors[:80]:
            print(" -", e)
        sys.exit(1)
    print(f"OK: path {len(pp)}문제, 모두 최종 칸 유일(가설 수 최대 {max(hyp_counts.values())}), "
          f"trail 과 겹치는 최종 칸 {sum(intrail.values())}개 (챕터별 {dict(sorted(intrail.items()))})")


def run_rule(rule, size, start, T):
    d, l = rule["dir"], rule["len"]
    if d["type"] == "cycle":
        ds = [NAMES.index(x) for x in d["dirs"]]
        dseq = [ds[k % len(ds)] for k in range(T)]
    else:
        s, sg, k = NAMES.index(d["start"]), 1 if d["sense"] == "cw" else -1, d["step"] // 45
        dseq = [(s + sg * k * i) % 8 for i in range(T)]
    if l["type"] == "const":
        lseq = [l["a"]] * T
    elif l["type"] == "cycle":
        lseq = [l["ls"][k % len(l["ls"])] for k in range(T)]
    elif l["type"] == "arith":
        lseq = arith(l["a"], l["delta"], l["cap"], T)
    else:
        lseq = arith(1, 1, l["cap"], T)
    pos = [tuple(start)]
    for d_, l_ in zip(dseq, lseq):
        pos.append((pos[-1][0] + V8[d_][0] * l_, pos[-1][1] + V8[d_][1] * l_))
    return [list(x) for x in pos]


def verify_vectors(path, pp):
    if not os.path.exists(path):
        err(f"테스트 벡터 없음: {path}")
        return
    with open(path, encoding="utf8") as f:
        v = json.load(f)
    if not isinstance(v.get("format"), str) or not v.get("cases"):
        err("테스트 벡터에 format/cases 가 있어야 함")
        return
    if len(v["cases"]) < 10:
        err("vector cases 는 10개 이상이어야 함")
    byid = {p["id"]: p for p in pp}
    for c in v["cases"]:
        cid = c.get("id", "?")
        size, trail, T = c["size"], c["trail"], c["total"]
        path = run_rule(c["rule"], size, trail[0], T)
        if path != c["path"]:
            err(f"vector {cid}: rule 로 재현한 전체 경로가 path 와 다름")
        if path[: len(trail)] != trail:
            err(f"vector {cid}: rule 이 trail 을 재현하지 못함")
        if not all(0 <= r < size and 0 <= cc < size for r, cc in path):
            err(f"vector {cid}: 경로가 격자 밖")
        if path[-1] != c["final"] or c["answer"] != str(path[-1][0] * size + path[-1][1] + 1):
            err(f"vector {cid}: final/answer 불일치")
        res = analyze(size, trail, T)
        if res is None or res[1] != c["hypotheses"] or res[2] != {tuple(c["final"])}:
            err(f"vector {cid}: 가설 수/유일성 재계산 불일치")
        p = byid.get(cid)
        if p is None:
            err(f"vector {cid}: puzzles.json 에 없음")
        elif (p["size"], p["trail"], p["total"], p["answer"]) != (size, trail, T, c["answer"]):
            err(f"vector {cid}: puzzles.json 과 다름")


if __name__ == "__main__":
    main()
