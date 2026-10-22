#!/usr/bin/env python3
"""경로 추적(track:"path") 퍼즐 생성기. 명세: docs/fill-path-mode-spec.md §2 §3 §4

14챕터 x 6문제 = 84문제(id pc{ch}_s{01..06})를 시드 고정으로 생성해 puzzles.json 에 병합한다.
재실행 안전: track=="path" 항목만 기존 위치에서 교체한다(없으면 맨 뒤에 추가; 다른 퍼즐은 바이트 동일).
같은 실행에서 플랫폼 검증용 scripts/path_test_vectors.json 도 만든다.

실행:  python3 scripts/gen_path_puzzles.py [--puzzles PATH] [--vectors PATH] [--dry]

규칙(rule)은 puzzles.json 에 저장하지 않는다(테스트 벡터에만 있음).
  방향 규칙 dir : cycle(방향 목록 p=1~4 반복) | rot(시작 방향 + cw/ccw + 45/90도 회전)
  길이 규칙 len : const(a) | cycle(q=2~3, 각 1~4) | arith(a, delta=+-1, cap: 범위를 벗어나면 래핑) | grow(1,2,..cap,1,..)
유일성: 보여 주는 자취를 재현하는 모든 (방향 규칙 x 길이 규칙) 가설을 전수 열거(전체 규칙 집합)하고,
total 번 이동하는 동안 격자를 벗어나지 않는 가설의 최종 칸이 모두 같을 때만 채택한다.
명세와 달리 arith 는 래핑(cap)을 가진다: 순수 등차는 격자 안에서 total>=8 번 이동이 불가능하기 때문(보고서 참고).
"""
import argparse, json, os, random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
VECTORS = os.path.join(ROOT, "scripts", "path_test_vectors.json")
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
DIRS = ["U", "UR", "R", "DR", "D", "DL", "L", "UL"]
VEC = [(-1, 0), (-1, 1), (0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1)]
ARROW = "↑↗→↘↓↙←↖"
CARD = [0, 2, 4, 6]
DIAG = [1, 3, 5, 7]


# ---------------------------------------------------------------- 규칙 평가
def arith_seq(a, d, cap, T):
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
    return out


def dir_seq(rule, T):
    if rule["type"] == "cycle":
        ds = [DIRS.index(x) for x in rule["dirs"]]
        return [ds[k % len(ds)] for k in range(T)]
    s = DIRS.index(rule["start"])
    sign = 1 if rule["sense"] == "cw" else -1
    k = rule["step"] // 45
    return [(s + sign * k * i) % 8 for i in range(T)]


def len_seq(rule, T):
    t = rule["type"]
    if t == "const":
        return [rule["a"]] * T
    if t == "cycle":
        ls = rule["ls"]
        return [ls[k % len(ls)] for k in range(T)]
    if t == "arith":
        return arith_seq(rule["a"], rule["delta"], rule["cap"], T)
    if t == "grow":
        return arith_seq(1, 1, rule["cap"], T)
    raise ValueError(t)


def simulate(size, start, ds, ls):
    """모든 칸이 격자 안이면 위치 목록(길이 T+1), 아니면 None."""
    r, c = start
    pos = [(r, c)]
    for d, l in zip(ds, ls):
        r += VEC[d][0] * l
        c += VEC[d][1] * l
        if not (0 <= r < size and 0 <= c < size):
            return None
        pos.append((r, c))
    return pos


# ---------------------------------------------------------------- 가설 전수 열거 (유일성)
def observed_moves(trail):
    obs = []
    for i in range(len(trail) - 1):
        dr = trail[i + 1][0] - trail[i][0]
        dc = trail[i + 1][1] - trail[i][1]
        L = max(abs(dr), abs(dc))
        if L < 1 or not (dr == 0 or abs(dr) == L) or not (dc == 0 or abs(dc) == L):
            return None
        sg = lambda x: (x > 0) - (x < 0)
        obs.append((VEC.index((sg(dr), sg(dc))), L))
    return obs


def hypotheses(size, trail, total):
    """(가설 수, 최종 칸 집합). 가설 = 서로 다른 (방향열, 길이열) 중 total 번 내내 격자 안에 머무는 것."""
    obs = observed_moves(trail)
    m = len(obs)
    od = [d for d, _ in obs]
    ol = [l for _, l in obs]
    dh = set()
    for p in range(1, 5):
        c = od[:p]
        if all(od[k] == c[k % p] for k in range(m)):
            dh.add(tuple(c[k % p] for k in range(total)))
    for sign in (1, -1):
        for k in (1, 2):
            seq = tuple((od[0] + sign * k * i) % 8 for i in range(total))
            if list(seq[:m]) == od:
                dh.add(seq)
    lh = set()
    for a in range(1, 7):
        if all(x == a for x in ol):
            lh.add(tuple([a] * total))
    for q in (2, 3):
        c = ol[:q]
        if all(1 <= x <= 4 for x in c) and all(ol[k] == c[k % q] for k in range(m)):
            lh.add(tuple(c[k % q] for k in range(total)))
    for cap in (None, 2, 3, 4, 5):
        for a in range(1, 6):
            if cap is not None and a > cap:
                continue
            for d in (1, -1):
                s = arith_seq(a, d, cap, total)
                if s is not None and s[:m] == ol:
                    lh.add(tuple(s))
    finals, cnt = set(), 0
    for ds in dh:
        for ls in lh:
            pos = simulate(size, trail[0], ds, ls)
            if pos is not None:
                cnt += 1
                finals.add(pos[-1])
    return cnt, finals


# ---------------------------------------------------------------- 챕터 설정
def min_period(seq):
    p = len(seq)
    for d in range(1, len(seq) + 1):
        if len(seq) % d == 0 and all(seq[i] == seq[i % d] for i in range(len(seq))):
            return d
    return p


def is_rot90(seq):
    return len(seq) >= 3 and len({(seq[(i + 1) % len(seq)] - seq[i]) % 8 for i in range(len(seq))}) == 1


def dcycle(lst):
    return {"type": "cycle", "dirs": [DIRS[i] for i in lst]}


def drot(start, sense, step):
    return {"type": "rot", "start": DIRS[start], "sense": sense, "step": step}


def sizes_ms(ch):
    """(N, [(m, extra) x 6])  명세 사다리. order 1->6 로 m 은 줄이고 total-m 은 키워 간다."""
    tab = {
        1: (5, [(4, 2)] * 6),
        2: (5, [(5, 2)] * 3 + [(5, 3)] * 3),
        3: (5, [(5, 2)] * 3 + [(5, 3)] * 3),
        4: (6, [(5, 2)] * 3 + [(5, 3)] * 3),
        5: (6, [(6, 3)] * 6),
        6: (6, [(6, 3)] * 6),
        7: (6, [(6, 3)] * 6),
        8: (6, [(5, 3)] * 6),
        9: (7, [(6, 3)] * 6),
        10: (7, [(7, 3)] * 3 + [(7, 4)] * 3),
        11: (7, [(7, 3)] * 3 + [(7, 4)] * 3),
        12: (7, [(8, 4)] * 6),
        13: (7, [(9, 4)] * 6),
        14: (7, [(9, 4)] * 2 + [(9, 5), (8, 4), (8, 5), (8, 5)]),
    }
    return tab[ch]


def pick_len_cycle(rng, q, hi=3):
    while True:
        ls = [rng.randint(1, hi) for _ in range(q)]
        if min_period(ls) == q:
            return {"type": "cycle", "ls": ls}


def make_rule(ch, o, rng):
    sense = lambda: rng.choice(["cw", "ccw"])
    if ch == 1:
        h, v = rng.choice([2, 6]), rng.choice([0, 4])
        return dcycle(rng.choice([[h, v], [v, h]])), {"type": "const", "a": 1}
    if ch == 2:
        if o == 1:
            s = rng.choice(CARD)
            sg = rng.choice([2, -2])
            return dcycle([(s + sg * k) % 8 for k in range(4)]), {"type": "const", "a": 1}
        while True:
            l = [rng.choice(CARD) for _ in range(4)]
            if min_period(l) == 4 and not is_rot90(l):
                return dcycle(l), {"type": "const", "a": 1}
    if ch == 3:
        a = 1 if o <= 2 else rng.choice([1, 2, 2])
        return drot(rng.choice(CARD), sense(), 90), {"type": "const", "a": a}
    if ch == 4:
        while True:
            l = [rng.choice(CARD) for _ in range(2)]
            if l[0] != l[1]:
                return dcycle(l), pick_len_cycle(rng, 2)
    if ch == 5:
        return drot(rng.choice(CARD), sense(), 90), pick_len_cycle(rng, 2)
    if ch == 6:
        while True:
            l = [rng.choice(CARD) for _ in range(3)]
            if min_period(l) == 3:
                return dcycle(l), {"type": "arith", "a": rng.randint(1, 3), "delta": 1, "cap": 3}
    if ch == 7:
        return drot(rng.choice(CARD), sense(), 90), {"type": "grow", "cap": 3}
    if ch in (8, 10, 13):
        p = {8: 2, 10: 3, 13: 4}[ch]
        while True:
            l = [rng.randrange(8) for _ in range(p)]
            if min_period(l) == p and any(x in DIAG for x in l) and not (ch == 8 and (l[0] - l[1]) % 8 == 4):
                break
        if ch == 8:
            return dcycle(l), {"type": "const", "a": rng.choice([1, 1, 2])}
        if ch == 10:
            return dcycle(l), pick_len_cycle(rng, 2)
        return dcycle(l), pick_len_cycle(rng, 3)
    if ch == 9:
        return drot(rng.randrange(8), sense(), 45), {"type": "const", "a": rng.choice([1, 1, 2])}
    if ch == 11:
        d = rng.choice([1, -1])
        return (drot(rng.choice(CARD if o <= 3 else list(range(8))), sense(), 90),
                {"type": "arith", "a": rng.randint(1, 3), "delta": d, "cap": 3})
    if ch == 12:
        return drot(rng.randrange(8), sense(), 45), pick_len_cycle(rng, 3)
    if ch == 14:
        kind = rng.choice(["rot45-grow", "cyc-arith", "rot90-cyc3", "rot45-arith", "cyc4-grow"])
        if kind == "rot45-grow":
            return drot(rng.randrange(8), sense(), 45), {"type": "grow", "cap": rng.choice([3, 4])}
        if kind == "cyc-arith":
            while True:
                l = [rng.randrange(8) for _ in range(3)]
                if min_period(l) == 3:
                    return dcycle(l), {"type": "arith", "a": rng.randint(1, 4), "delta": rng.choice([1, -1]), "cap": 4}
        if kind == "rot90-cyc3":
            return drot(rng.randrange(8), sense(), 90), pick_len_cycle(rng, 3)
        if kind == "rot45-arith":
            return (drot(rng.randrange(8), sense(), 45),
                    {"type": "arith", "a": rng.randint(1, 4), "delta": rng.choice([1, -1]), "cap": 4})
        while True:
            l = [rng.randrange(8) for _ in range(4)]
            if min_period(l) == 4:
                return dcycle(l), {"type": "grow", "cap": rng.choice([3, 4])}
    raise ValueError(ch)


# ---------------------------------------------------------------- 텍스트 (7개 언어)
TIP = {
    "dir": {"ko": "방향이 어떻게 바뀌는지부터 봐요.", "en": "First look at how the direction changes.",
            "ja": "まず向きがどう変わるかを見てみましょう。", "zh": "先看方向是怎么变化的。",
            "es": "Fíjate primero en cómo cambia la dirección.", "fr": "Regarde d'abord comment la direction change.",
            "de": "Schau zuerst, wie sich die Richtung ändert."},
    "len": {"ko": "한 번에 몇 칸 가는지도 규칙이 있어요.", "en": "The number of cells per move follows a rule too.",
            "ja": "一度に進むマス数にも規則があります。", "zh": "每次走几格也有规律。",
            "es": "Cuántas casillas avanza cada vez también sigue una regla.",
            "fr": "Le nombre de cases par déplacement suit aussi une règle.",
            "de": "Auch die Anzahl der Felder pro Zug folgt einer Regel."},
    "diag": {"ko": "비스듬히도 움직여요.", "en": "It can move diagonally too.", "ja": "斜めにも動きます。",
             "zh": "也会斜着走。", "es": "También se mueve en diagonal.", "fr": "Il se déplace aussi en diagonale.",
             "de": "Er bewegt sich auch diagonal."},
}
TIP_OF = {1: "dir", 2: "dir", 3: "dir", 4: "len", 5: "len", 6: "len", 7: "len", 8: "diag", 9: "diag", 10: "diag",
          11: "len", 12: "diag", 13: "diag", 14: "len"}
REVEAL = {
    "dirs": {"ko": "이어지는 방향:", "en": "Directions that follow:", "ja": "続く向き:", "zh": "接下来的方向:",
             "es": "Direcciones siguientes:", "fr": "Directions suivantes :", "de": "Folgende Richtungen:"},
    "lens": {"ko": "이어지는 이동 칸 수:", "en": "Step lengths that follow:", "ja": "続く移動マス数:", "zh": "接下来的步数:",
             "es": "Longitudes siguientes:", "fr": "Longueurs suivantes :", "de": "Folgende Schrittlängen:"},
}
NEXT = {
    "ko": "다음 이동은 {r}행 {c}열에 도착해요.", "en": "The next move ends at row {r}, column {c}.",
    "ja": "次の移動は{r}行{c}列に着きます。", "zh": "下一步会到第{r}行第{c}列。",
    "es": "El siguiente movimiento termina en la fila {r}, columna {c}.",
    "fr": "Le prochain déplacement arrive à la ligne {r}, colonne {c}.",
    "de": "Der nächste Zug endet in Zeile {r}, Spalte {c}.",
}
FINAL = {
    "ko": "= {r}행 {c}열", "en": "= row {r}, col {c}", "ja": "= {r}行{c}列", "zh": "= 第{r}行第{c}列",
    "es": "= fila {r}, col. {c}", "fr": "= ligne {r}, col. {c}", "de": "= Zeile {r}, Spalte {c}",
}


def make_texts(ch, o, ds, ls, pos, m, T, rule_len):
    nxt = pos[m + 1]
    k = min(3, T - m)
    if rule_len["type"] != "const" and o % 2 == 1:
        body = " ".join(str(ls[m + i]) for i in range(k)) + " …"
        key = "lens"
    else:
        body = " ".join(ARROW[ds[m + i]] for i in range(k)) + " …"
        key = "dirs"
    hints = {}
    for lang in LANGS:
        hints[lang] = [TIP[TIP_OF[ch]][lang], f"{REVEAL[key][lang]} {body}",
                       NEXT[lang].format(r=nxt[0] + 1, c=nxt[1] + 1)]
    seq = " ".join(f"{ARROW[d]}{l}" for d, l in zip(ds, ls))
    fr, fc = pos[-1]
    expl = {lang: f"{seq} {FINAL[lang].format(r=fr + 1, c=fc + 1)}" for lang in LANGS}
    return hints, expl


# ---------------------------------------------------------------- 생성
INTRAIL_OK = (3, 5, 9)  # rot + const 길이는 주기 이동이라 최종 칸이 자취와 겹치는 게 본질적(명세 예외, 보고서 참고)


def try_build(rng, ch, o, size, m, extra, seen):
    dr, lr = make_rule(ch, o, rng)
    T = m + extra
    ds, ls = dir_seq(dr, T), len_seq(lr, T)
    if ls is None:
        return None
    # 시작 칸 없이 상대 경로의 범위를 구해 들어맞는 시작 칸 후보를 만든다
    off = [(0, 0)]
    for d, l in zip(ds, ls):
        off.append((off[-1][0] + VEC[d][0] * l, off[-1][1] + VEC[d][1] * l))
    rs, cs = [p[0] for p in off], [p[1] for p in off]
    rr = range(-min(rs), size - max(rs))
    cr = range(-min(cs), size - max(cs))
    if len(rr) == 0 or len(cr) == 0:
        return None
    start = (rng.choice(list(rr)), rng.choice(list(cr)))
    pos = [(start[0] + a, start[1] + b) for a, b in off]
    trail = [list(p) for p in pos[: m + 1]]
    key = json.dumps([size, trail, T])
    if key in seen["keys"]:
        return None
    in_trail = pos[-1] in pos[: m + 1]
    if in_trail and seen["intrail"] >= 1 and ch not in INTRAIL_OK:
        return None
    cnt, finals = hypotheses(size, trail, T)
    if finals != {pos[-1]}:
        return None
    return dict(rule={"dir": dr, "len": lr}, ds=ds, ls=ls, pos=pos, trail=trail, T=T, m=m, cnt=cnt,
                in_trail=in_trail, key=key)


def gen_chapter(ch):
    size, plan = sizes_ms(ch)
    seen = {"keys": set(), "intrail": 0}
    built, notes = [], []
    for o, (m0, extra) in enumerate(plan, 1):
        res = None
        for bump in range(0, 4):
            m = m0 + bump
            rng = random.Random(f"path-{ch}-{o}-{m}")
            for _ in range(6000):
                r = try_build(rng, ch, o, size, m, extra, seen)
                if r:
                    res = r
                    break
            if res:
                if bump:
                    notes.append(f"ch{ch} o{o}: m {m0}->{m}")
                break
        assert res, f"pc{ch}_s{o} 생성 실패"
        seen["keys"].add(res["key"])
        seen["intrail"] += res["in_trail"]
        res["o"] = o
        built.append(res)

    def score(r):
        return len(set(zip(r["ds"], r["ls"]))) + len(set(map(tuple, r["trail"])))

    built.sort(key=lambda r: (-r["m"], r["T"] - r["m"], score(r)))
    out, meta = [], {}
    for o, r in enumerate(built, 1):
        T, m = r["T"], r["m"]
        hints, expl = make_texts(ch, o, r["ds"], r["ls"], r["pos"], m, T, r["rule"]["len"])
        fr, fc = r["pos"][-1]
        pid = f"pc{ch}_s{o:02d}"
        out.append({
            "id": pid, "type": "path", "track": "path", "chapter": ch, "order": o,
            "size": size, "start": r["trail"][0], "trail": r["trail"], "total": T,
            "answer": str(fr * size + fc + 1), "inputType": "choices", "choices": None,
            "hints": hints, "explanation": expl,
        })
        meta[pid] = {"rule": r["rule"], "final": [fr, fc], "hypotheses": r["cnt"],
                     "path": [list(p) for p in r["pos"]]}
    return out, meta, notes


# ---------------------------------------------------------------- 테스트 벡터
def build_vectors(puzzles, meta):
    cases = []
    for ch in range(1, 15):
        for o in (3,) if ch not in (1, 14) else (1, 3, 6):
            p = next(x for x in puzzles if x["id"] == f"pc{ch}_s{o:02d}")
            mt = meta[p["id"]]
            cases.append({"id": p["id"], "size": p["size"], "trail": p["trail"], "total": p["total"],
                          "rule": mt["rule"], "final": mt["final"], "answer": p["answer"],
                          "hypotheses": mt["hypotheses"], "path": mt["path"]})
    return {
        "format": ("경로 추적 테스트 벡터(명세 docs/fill-path-mode-spec.md §2~§3). cases[]: {id, size, trail:[[r,c]..](보여 주는 자취, "
                   "trail[0]=출발), total(총 이동 수), rule, final:[r,c], answer:'r*size+c+1', hypotheses:(자취를 재현하며 total 번 "
                   "격자 안에 머무는 서로 다른 가설 수; 최종 칸은 모두 동일), path:[[r,c]..](total+1 개 전체 경로)}. "
                   "rule.dir = {type:cycle,dirs:[U,UR,R,DR,D,DL,L,UL 중..]}(반복) | {type:rot,start,sense:cw|ccw,step:45|90}. "
                   "rule.len = {type:const,a} | {type:cycle,ls:[..]} | {type:arith,a,delta:+-1,cap}(a 에서 delta 씩, cap 초과면 1 로·"
                   "1 미만이면 cap 으로) | {type:grow,cap}(1,2,..,cap,1,..). 한 이동 = 방향 d 로 L 칸(대각선은 두 축 모두 L 칸). "
                   "앱은 trail/total/size/answer 를 파싱·표시하고 answer 를 그대로 쓰면 되며 규칙 평가 로직은 필요 없다."),
        "cases": cases,
    }


def print_table(puzzles, meta):
    print("ch | N | m | total-m | 규칙(dir / len) | 가설 수")
    for ch in range(1, 15):
        ps = [p for p in puzzles if p["chapter"] == ch]
        ms = [len(p["trail"]) - 1 for p in ps]
        ex = [p["total"] - (len(p["trail"]) - 1) for p in ps]
        hs = [meta[p["id"]]["hypotheses"] for p in ps]
        print(f"{ch:2d} | {ps[0]['size']} | {ms} | {ex} | hyp {hs}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=PUZZLES)
    ap.add_argument("--vectors", default=VECTORS)
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    puzzles, meta, notes = [], {}, []
    for ch in range(1, 15):
        ps, m, n = gen_chapter(ch)
        puzzles.extend(ps)
        meta.update(m)
        notes.extend(n)
    print_table(puzzles, meta)
    for n in notes:
        print("m 증가:", n)
    if a.dry:
        return
    with open(a.puzzles, encoding="utf8") as f:
        data = json.load(f)
    idx = [i for i, p in enumerate(data) if p.get("track") == "path"]
    if idx:
        first = idx[0]
        rest = [p for p in data if p.get("track") != "path"]
        data = rest[:first] + puzzles + rest[first:]
    else:
        data = data + puzzles
    with open(a.puzzles, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    with open(a.vectors, "w", encoding="utf8") as f:
        f.write(json.dumps(build_vectors(puzzles, meta), indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {len(puzzles)} path puzzles; total {len(data)}")


if __name__ == "__main__":
    main()
