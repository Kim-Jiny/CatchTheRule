#!/usr/bin/env python3
"""조각 숫자(track:"fragment") 퍼즐 생성기. 명세: docs/fragment-mode-spec.md

14챕터 x 6문제 = 84문제(id fg{ch}_s{01..06})를 시드 고정으로 생성해 shared/content/puzzles.json 에 병합한다.
재실행 안전: track=="fragment" 항목만 지우고 다시 추가한다(나머지 퍼즐은 바이트 동일).
같은 실행에서 플랫폼 검증용 scripts/fragment_test_vectors.json 도 만든다.

실행:  python3 scripts/gen_fragment_puzzles.py [--dry]   (--dry: 파일을 쓰지 않고 챕터별 지표 표만 출력)
"""
import json, os, random, sys
from itertools import combinations

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
VECTORS = os.path.join(ROOT, "scripts", "fragment_test_vectors.json")
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
SEG = "abcdefg"
PAT = {"0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg",
       "5": "acdfg", "6": "acdefg", "7": "abc", "8": "abcdefg", "9": "abcdfg"}
PATSET = {d: frozenset(p) for d, p in PAT.items()}
BY_SET = {v: d for d, v in PATSET.items()}


# ---------------------------------------------------------------- 합성(명세 §3)
def combine(frames, rule):
    """frames[f][d] -> 최종 숫자 문자열, 유효하지 않으면 None."""
    if not frames:
        return None
    n = len(frames[0])
    if n == 0 or any(len(f) != n for f in frames):
        return None
    out = ""
    for d in range(n):
        cnt = {}
        for f in frames:
            for ch in f[d]:
                cnt[ch] = cnt.get(ch, 0) + 1
        if rule == "or":
            on = frozenset(cnt)
        else:
            on = frozenset(c for c, v in cnt.items() if v % 2 == 1)
        if on not in BY_SET:
            return None
        out += BY_SET[on]
    return out


# ---------------------------------------------------------------- 챕터 설정
def lerp_ms(a, b, o):
    v = a + (b - a) * (o - 1) / 5
    return int(round(v / 50.0)) * 50


# ch -> dict(n 자릿수, k(order별 장 수), rule(order별), ms(시작,끝), inp(order별), trap, noise, dup)
def cfg(ch, o):
    t = o - 1
    c = {}
    if ch in (1, 2):
        c.update(n=1, k=2, rule="or", ms=(2000, 1800) if ch == 1 else (2000, 1700), inp="keypad",
                 trap=0, noise=0, dup=0)
    elif ch in (3, 4):
        c.update(n=2, k=2, rule="or", ms=(1800, 1600) if ch == 3 else (1700, 1500),
                 inp="keypad", trap=0, noise=0, dup=0)
    elif ch in (5, 6):
        c.update(n=3, k=3, rule="or", ms=(1500, 1400) if ch == 5 else (1400, 1300), inp="keypad",
                 trap=0, noise=0, dup=0)
    elif ch in (7, 8):
        tr = [1, 1, 1, 2, 2, 2] if ch == 7 else [1, 2, 2, 2, 3, 3]
        c.update(n=3, k=3, rule="or", ms=(1200, 1100) if ch == 7 else (1100, 1000), inp="keypad",
                 trap=tr[t], noise=0, dup=0)
    elif ch in (9, 10):
        tr = [1, 1, 2, 2, 2, 3] if ch == 9 else [2, 2, 3, 3, 3, 4]
        dp = [2, 2, 3, 3, 4, 4] if ch == 9 else [3, 4, 4, 5, 5, 6]
        c.update(n=4, k=3, rule="or", ms=(1000, 900) if ch == 9 else (900, 800), inp="keypad",
                 trap=tr[t], noise=0, dup=dp[t])
    elif ch == 11:
        c.update(n=3, k=2 if o <= 3 else 3, rule="xor", ms=(1200, 1100), inp="keypad",
                 trap=[1, 1, 1, 1, 2, 2][t], noise=[1, 1, 2, 2, 3, 3][t], dup=0)
    elif ch == 12:
        c.update(n=3, k=3, rule="xor", ms=(1100, 1000), inp="keypad",
                 trap=[1, 2, 2, 2, 3, 3][t], noise=[2, 3, 3, 4, 4, 5][t], dup=0)
    elif ch in (13, 14):
        orset = (2, 5) if ch == 13 else (3, 6)
        rule = "or" if o in orset else "xor"
        k = ([3, 3, 3, 4, 4, 4] if ch == 13 else [4] * 6)[t]
        trap = ([2, 2, 2, 3, 3, 3] if ch == 13 else [2, 3, 3, 3, 4, 4])[t]
        noise = ([3, 3, 4, 4, 5, 5] if ch == 13 else [4, 4, 5, 5, 6, 6])[t]
        c.update(n=4, k=k, rule=rule, ms=(1000, 850) if ch == 13 else (850, 700), inp="keypad",
                 trap=trap, noise=noise if rule == "xor" else 0,
                 dup=(2 + t // 3) if rule == "or" else 0)
    c["frameMs"] = lerp_ms(c["ms"][0], c["ms"][1], o)
    return c


# ---------------------------------------------------------------- 생성
def sym(a, b):
    return len(PATSET[a] ^ PATSET[b])


def subsets_of(k):
    return [tuple(i for i in range(k) if m >> i & 1) for m in range(1 << k)]


def weighted(rng, items, weights):
    tot = sum(weights)
    r = rng.random() * tot
    for it, w in zip(items, weights):
        r -= w
        if r <= 0:
            return it
    return items[-1]


def trap_candidates(ans_digit, rule):
    """이 자리에 함정으로 띄울 수 있는 다른 유효 숫자들."""
    out = []
    for d, s in PATSET.items():
        if d == ans_digit:
            continue
        if rule == "or" and not s < PATSET[ans_digit]:
            continue
        out.append(d)
    return out


def metrics(frames, rule, ans):
    """(trap, noise, dup, 장별 합) 독립적 정의: 함정=장 단독으로 유효하며 정답 자릿수와 다른 (장,자리)."""
    k, n = len(frames), len(ans)
    trap = 0
    for f in frames:
        for d in range(n):
            s = frozenset(f[d])
            if s in BY_SET and BY_SET[s] != ans[d]:
                trap += 1
    noise = dup = 0
    for d in range(n):
        cnt = {}
        for f in frames:
            for ch in f[d]:
                cnt[ch] = cnt.get(ch, 0) + 1
        for ch, v in cnt.items():
            if rule == "xor" and v % 2 == 0:
                noise += 1
            if rule == "or" and v >= 2:
                dup += v - 1
    return trap, noise, dup


def try_build(rng, c, ans):
    n, k, rule = c["n"], c["k"], c["rule"]
    # 함정 칸 고정
    fixed = [dict() for _ in range(n)]  # fixed[d][f] = 획 집합
    want = c["trap"]
    if want:
        elig = [d for d in range(n) if trap_candidates(ans[d], rule)]
        if not elig:
            return None
        order = []
        while len(order) < want:
            sh = elig[:]
            rng.shuffle(sh)
            order.extend(sh)
        for d in order[:want]:
            free = [f for f in range(k) if f not in fixed[d]]
            if not free:
                continue
            f = rng.choice(free)
            cands = trap_candidates(ans[d], rule)
            w = [1.0 / (1 + sym(ans[d], x)) for x in cands]
            fixed[d][f] = PATSET[weighted(rng, cands, w)]
    # 확률 가중치
    m_noise = sum(7 - len(PAT[a]) for a in ans)
    s_total = sum(len(PAT[a]) for a in ans)
    pn = min(0.8, (c["noise"] + 0.6) / max(1, m_noise)) if c["noise"] else 0.0
    pd = min(0.9, (c["dup"] + 0.6) / s_total) if c["dup"] else 0.0
    subs = subsets_of(k)
    frames = [["" for _ in range(n)] for _ in range(k)]
    cells = [[set() for _ in range(n)] for _ in range(k)]
    for d in range(n):
        S = PATSET[ans[d]]
        for s in SEG:
            must_in = [f for f, P in fixed[d].items() if s in P]
            must_out = [f for f, P in fixed[d].items() if s not in P]
            items, ws = [], []
            for T in subs:
                if any(f not in T for f in must_in) or any(f in T for f in must_out):
                    continue
                z = len(T)
                if rule == "or":
                    if s in S:
                        if z == 0:
                            continue
                        w = 1.0 if z == 1 else pd * (0.5 ** (z - 2))
                    else:
                        if z != 0:
                            continue
                        w = 1.0
                else:
                    want_odd = s in S
                    if z % 2 != (1 if want_odd else 0):
                        continue
                    if want_odd:
                        w = 1.0 if z == 1 else 0.12
                    else:
                        w = 1.0 if z == 0 else (pn * 3.0 if z == 2 else 0.03)
                if w > 0:
                    items.append(T)
                    ws.append(w)
            if not items:
                return None
            for f in weighted(rng, items, ws):
                cells[f][d].add(s)
    for f in range(k):
        for d in range(n):
            frames[f][d] = "".join(sorted(cells[f][d]))
    return frames


def quality(frames, c, ans):
    k, n = len(frames), len(ans)
    rule = c["rule"]
    if combine(frames, rule) != ans:
        return False
    tot = [sum(len(x) for x in f) for f in frames]
    if min(tot) == 0:
        return False
    allapp = sum(tot)
    if min(tot) < max(1, int(0.5 * allapp / k)):
        return False
    if len({tuple(f) for f in frames}) < k:
        return False
    for f in frames:  # 한 장에 한 자리의 정답 전체가 몰리지 않게
        for d in range(n):
            if k >= 2 and frozenset(f[d]) >= PATSET[ans[d]] and rule == "or":
                return False
            if k >= 2 and frozenset(f[d]) == PATSET[ans[d]]:
                return False
    trap, noise, dup = metrics(frames, rule, ans)
    if trap < c["trap"]:
        return False
    if c["trap"] == 0 and trap > 1 and c["n"] <= 3 and c["rule"] == "or":
        pass
    if rule == "xor":
        if not (c["noise"] <= noise <= c["noise"] + 2):
            return False
    else:
        if c["dup"] == 0 and dup != 0:
            return False
        if c["dup"] and not (c["dup"] <= dup <= c["dup"] + 2):
            return False
    return True


def make_choices(rng, ans):
    n = len(ans)
    cands = []
    for d in range(n):
        for x in PAT:
            if x == ans[d]:
                continue
            if d == 0 and x == "0":
                continue
            cands.append((sym(ans[d], x), rng.random(), ans[:d] + x + ans[d + 1:]))
    cands.sort()
    out = []
    posused = set()
    # 한 획 차이를 우선, 자리가 겹치지 않게 번갈아
    for dist, _, v in cands:
        if dist > 2 and len(out) >= 3:
            break
        if len(out) >= 3:
            break
        out.append(v)
    out = out[:3]
    ch = out + [ans]
    assert len(set(ch)) == 4
    rng.shuffle(ch)
    return ch


# ---------------------------------------------------------------- 텍스트(7개 언어)
def iyo(num):
    """한국어 '이에요/예요' 선택(마지막 숫자의 한국어 읽기 받침)."""
    return "이에요" if str(num)[-1] in "013678" else "예요"


H1 = {
    "or": {
        "ko": "모든 장에 나온 획을 한 숫자 위에 겹쳐 보세요.",
        "en": "Stack every stroke from all the frames on top of a single digit.",
        "ja": "すべてのコマに出てきた線を、ひとつの数字の上に重ねてみましょう。",
        "zh": "把所有画面中出现的笔画叠加到同一个数字上。",
        "es": "Superpón en un solo número todos los trazos que aparecen en las imágenes.",
        "fr": "Superposez sur un seul chiffre tous les traits apparus dans les images.",
        "de": "Lege alle Striche aus allen Bildern übereinander auf eine einzige Ziffer.",
    },
    "xor": {
        "ko": "짝수 번 나온 획은 사라지고, 홀수 번 나온 획만 남는다는 걸 기억하세요.",
        "en": "Remember: a stroke that appears an even number of times disappears; only odd-count strokes stay.",
        "ja": "偶数回出てきた線は消え、奇数回出てきた線だけが残ることを思い出してください。",
        "zh": "记住：出现偶数次的笔画会消失，只有出现奇数次的笔画会保留。",
        "es": "Recuerda: un trazo que aparece un número par de veces desaparece; solo quedan los de número impar.",
        "fr": "Rappelez-vous : un trait qui apparaît un nombre pair de fois disparaît ; seuls les traits en nombre impair restent.",
        "de": "Denk daran: Ein Strich, der gerade oft erscheint, verschwindet; nur ungerade oft erscheinende bleiben.",
    },
}


def make_texts(ans, rule):
    hints = {l: [] for l in LANGS}
    for l in LANGS:
        hints[l].append(H1[rule][l])
    if len(ans) == 1:
        N = len(PAT[ans])
        odd = int(ans) % 2 == 1
        h2 = {
            "ko": f"이 숫자는 모두 {N}개의 획으로 이루어져 있어요.",
            "en": f"This digit is made of {N} strokes in total.",
            "ja": f"この数字は全部で{N}本の線でできています。",
            "zh": f"这个数字一共由{N}根笔画组成。",
            "es": f"Este número se forma con {N} trazos en total.",
            "fr": f"Ce chiffre est formé de {N} traits au total.",
            "de": f"Diese Ziffer besteht aus insgesamt {N} Strichen.",
        }
        h3 = {
            "ko": "정답은 홀수예요." if odd else "정답은 짝수예요.",
            "en": "The answer is odd." if odd else "The answer is even.",
            "ja": "答えは奇数です。" if odd else "答えは偶数です。",
            "zh": "答案是奇数。" if odd else "答案是偶数。",
            "es": "La respuesta es impar." if odd else "La respuesta es par.",
            "fr": "La réponse est impaire." if odd else "La réponse est paire.",
            "de": "Die Antwort ist ungerade." if odd else "Die Antwort ist gerade.",
        }
    else:
        N = ans[0]
        S = sum(int(x) for x in ans)
        h2 = {
            "ko": f"첫 번째 자리의 숫자는 {N}{iyo(N)}.",
            "en": f"The first digit is {N}.",
            "ja": f"1桁目の数字は{N}です。",
            "zh": f"第一位数字是{N}。",
            "es": f"El primer dígito es {N}.",
            "fr": f"Le premier chiffre est {N}.",
            "de": f"Die erste Ziffer ist {N}.",
        }
        h3 = {
            "ko": f"각 자리 숫자를 모두 더하면 {S}{iyo(S)}.",
            "en": f"The digits add up to {S}.",
            "ja": f"各桁の数字をすべて足すと{S}です。",
            "zh": f"各位数字相加等于{S}。",
            "es": f"Si sumas todos los dígitos, obtienes {S}.",
            "fr": f"La somme de tous les chiffres est {S}.",
            "de": f"Die Summe aller Ziffern ist {S}.",
        }
    for l in LANGS:
        hints[l] += [h2[l], h3[l]]
    expl = {l: ans for l in LANGS}
    return hints, expl


# ---------------------------------------------------------------- 챕터 생성
def gen_chapter(ch, seen_global):
    out = []
    used = set()
    c0 = cfg(ch, 1)
    fixed_answers = None
    if c0["n"] == 1:
        rr = random.Random(f"fg-1digit")
        perm = list("123456789")
        rr.shuffle(perm)
        pick = perm[0:6] if ch == 1 else perm[6:9] + perm[0:3]
        fixed_answers = sorted(pick, key=lambda x: (len(PAT[x]), x))
    for o in range(1, 7):
        c = cfg(ch, o)
        rng = random.Random(f"fg{ch}_s{o}")
        done = None
        for attempt in range(200000):
            if fixed_answers:
                ans = fixed_answers[o - 1]
            else:
                ans = str(rng.randint(1, 9)) + "".join(str(rng.randint(0, 9)) for _ in range(c["n"] - 1))
                if ans in used or (c["n"] >= 3 and len(set(ans)) < 2):
                    continue
            frames = try_build(rng, c, ans)
            if frames is None or not quality(frames, c, ans):
                continue
            key = (ans, json.dumps(frames))
            if key in seen_global:
                continue
            done = (ans, frames)
            break
        assert done, f"fg{ch}_s{o} 생성 실패"
        ans, frames = done
        used.add(ans)
        seen_global.add((ans, json.dumps(frames)))
        assert combine(frames, c["rule"]) == ans and ans[0] != "0"
        hints, expl = make_texts(ans, c["rule"])
        make_choices(rng, ans)  # 난수열 유지용(직접 입력만 쓰므로 보기는 버림)
        choices = None
        out.append({
            "id": f"fg{ch}_s{o:02d}", "type": "fragment", "track": "fragment",
            "chapter": ch, "order": o,
            "frames": frames, "combine": c["rule"], "frameMs": c["frameMs"],
            "answer": ans, "inputType": c["inp"], "choices": choices,
            "hints": hints, "explanation": expl,
        })
    return out


# ---------------------------------------------------------------- 테스트 벡터
def build_vectors(puzzles):
    cases = []

    def add(rule, frames, expect, note):
        r = combine(frames, rule)
        assert r == expect, (rule, frames, r, expect)
        cases.append({"combine": rule, "frames": frames, "result": r, "note": note})

    # 유효: or
    add("or", [["a"], ["bc"]], "7", "or 1자리: a + bc = 7")
    add("or", [["ab"], ["cdefg"]], "8", "or 1자리: 합집합 abcdefg = 8")
    add("or", [["bc"], ["bc"]], "1", "or: 같은 획이 두 장에 중복 = 1")
    add("or", [["abg"], ["edc"]], None, "or: abg + cde = abcdeg 는 어느 숫자도 아님 -> null")
    add("or", [["abg"], ["ed"]], "2", "or: 획 순서 무관, abg + de = abdeg = 2")
    add("or", [["ab", ""], ["", "bc"], ["c", "g"]], None, "or 2자리: 둘째 자리 bcg 무효 -> null")
    add("or", [["ab", ""], ["", "bc"], ["c", ""]], "71", "or 2자리: 빈 문자열 포함, abc / bc = 71")
    add("or", [["ab", "ab"], ["c", "cdfg"]], "79", "or 2자리: 자리별 합집합 abc / abcdfg = 79")
    add("or", [["ab", "", "g"], ["", "bc", "d"], ["fc", "", "a"]], None, "or 3자리: 첫 자리 abcf 무효 -> null")
    add("or", [["abc", "bc", "abcdefg"], ["", "", ""]], "718", "or: 빈 장이 있어도 됨, 7/1/8")
    add("or", [["acdfg"], [""]], "5", "or: 한 장이 완전히 빈 자리 = 5")
    add("or", [["abcdef"], ["g"], ["b"]], "8", "or 3장: 획 중복 = 8")
    add("or", [["bcfg"], ["b"]], "4", "or: 4")
    add("or", [["abdeg"], ["a"], ["g"]], "2", "or: 2")
    add("or", [["ab"], ["bc"]], "7", "or: ab + bc = abc = 7")
    # 유효: xor
    add("xor", [["abc"], ["a"]], "1", "xor: abc 에서 a 가 두 번 -> bc = 1")
    add("xor", [["abcdefg"], ["g"]], "0", "xor: 8 에서 g 상쇄 = 0")
    add("xor", [["ab"], ["ag"], ["cg"]], "1", "xor 3장: a,g 두 번씩 상쇄 -> b,c = 1")
    add("xor", [["abcdg"], ["f"], ["f"]], "3", "xor: f 두 번 상쇄 -> abcdg = 3")
    add("xor", [["abcdfg"], ["a"], ["a"], ["e"], ["e"]], "9", "xor 5장: a,e 두 번씩 상쇄 = 9")
    add("xor", [["abc", "bcfg", "acdfg"], ["", "", ""]], "745", "xor: 한 장만 켜진 것과 같음 = 745")
    add("xor", [["abc"], ["abc"], ["abc"]], "7", "xor: 세 번(홀수) 나온 획은 유지 = 7")
    add("xor", [["bc"], ["b"], ["b"]], "1", "xor: b 세 번(홀수) 유지, c 한 번 = 1")
    add("xor", [["a"], ["b"], ["c"]], "7", "xor: 각각 한 번씩 = abc = 7")
    add("xor", [["adef", "adf", "e"], ["bcefg", "cg", "abcdefg"]], "359", "xor 3자리: 노이즈 획 포함, 실제 퍼즐형")
    # 무효
    add("or", [["a"], ["b"]], None, "or: ab 는 숫자가 아님 -> null")
    add("or", [["", ""], ["", ""]], None, "or: 모든 자리가 비어 있음 -> null")
    add("or", [["abc", "a"], ["", "b"]], None, "or: 둘째 자리 ab 무효 -> null")
    add("or", [["abcdefg", "bcg"]], None, "or 1장: 둘째 자리 bcg 무효 -> null")
    add("or", [["bc", "abc"], ["x"]], None, "장마다 자릿수가 다름 -> null")
    add("or", [["a", "bc"], ["bc"]], None, "장마다 자릿수가 다름(2 vs 1) -> null")
    add("or", [], None, "frames 가 비어 있음 -> null")
    add("or", [[]], None, "자릿수 0 -> null")
    add("xor", [["abc"], ["abc"]], None, "xor: 완전 상쇄로 빈 자리 -> null")
    add("xor", [["abc"], ["bc"]], None, "xor: 결과 a 만 남음 -> null")
    add("xor", [["abcdefg"], ["abcdefg"]], None, "xor: 전부 상쇄 -> null")
    add("xor", [["ab", "bc"], ["ab", "b"]], None, "xor: 첫 자리 완전 상쇄 -> null")
    add("xor", [["abcdefg"], ["a"], ["b"]], None, "xor: cdefg 는 숫자가 아님 -> null")
    add("xor", [["abcd"], ["dfg"], ["fgb"]], None, "xor: a,c 만 남음 -> null")
    add("or", [["abcdefg"], ["abcdefg"]], "8", "or 와 xor 의 차이: 같은 입력이 or 이면 8")
    add("xor", [["abcdefg"], ["abcdefg"], ["a"]], None, "xor: a 만 남음 -> null (xor 와 or 의 차이)")
    # 실제 퍼즐 전부에서 몇 개 더(결과는 answer 와 같아야 함)
    for ch in (3, 5, 7, 9, 11, 12, 13, 14):
        p = puzzles[(ch - 1) * 6 + 2]
        add(p["combine"], p["frames"], p["answer"], f"{p['id']} 실제 퍼즐 (결과 = answer)")
        assert cases[-1]["result"] == p["answer"]
    samples = []
    for pid in ["fg1_s01", "fg2_s05", "fg3_s02", "fg5_s04", "fg7_s03", "fg9_s05",
                "fg11_s02", "fg12_s06", "fg13_s02", "fg14_s04"]:
        p = next(x for x in puzzles if x["id"] == pid)
        samples.append({k: p[k] for k in ("id", "chapter", "order", "frames", "combine", "frameMs",
                                           "answer", "inputType", "choices")})
    return {
        "format": ("조각 숫자 테스트 벡터(명세 docs/fragment-mode-spec.md). combine_cases[]: {combine:'or'|'xor', "
                   "frames:[[획문자열 per 자리] per 장], result: 합성된 숫자 문자열 또는 null(무효), note}. "
                   "합성: 자리마다 장들의 획 출현 횟수를 세어 or=1회 이상, xor=홀수 회인 획을 켠다. 켜진 획 집합이 "
                   "숫자 패턴(0:abcdef 1:bc 2:abdeg 3:abcdg 4:bcfg 5:acdfg 6:acdefg 7:abc 8:abcdefg 9:abcdfg)과 "
                   "정확히 같지 않은 자리가 하나라도 있으면 null. frames 가 비었거나 자릿수가 0이거나 장마다 "
                   "자릿수가 다르면 null. 획 문자열 안의 문자 순서는 무관. 선행 0 은 합성에서 판정하지 않는다. "
                   "samples[]: 실제 퍼즐 10개 요약(id, chapter, order, frames, combine, frameMs, answer, inputType, "
                   "choices) — 앱이 puzzles.json 디코딩 후 합성 결과가 answer 와 같은지 확인하는 용도."),
        "patterns": PAT,
        "combine_cases": cases,
        "samples": samples,
    }


# ---------------------------------------------------------------- main
def print_table(puzzles):
    print("ch | 자릿수 | 장수 | frameMs | combine | 입력 | 함정 평균 | 노이즈 평균 | 중복 평균")
    for ch in range(1, 15):
        ps = [p for p in puzzles if p["chapter"] == ch]
        tr = ns = dp = 0
        for p in ps:
            t, n, d = metrics(p["frames"], p["combine"], p["answer"])
            tr += t; ns += n; dp += d
        ms = [p["frameMs"] for p in ps]
        ks = sorted({len(p["frames"]) for p in ps})
        cb = "/".join(sorted({p["combine"] for p in ps}))
        inp = "/".join(sorted({p["inputType"] for p in ps}))
        print(f"{ch:2d} | {len(ps[0]['answer'])} | {ks} | {ms[0]}->{ms[-1]} | {cb} | {inp} | "
              f"{tr/6:.1f} | {ns/6:.1f} | {dp/6:.1f}")


def main():
    dry = "--dry" in sys.argv
    seen = set()
    puzzles = []
    for ch in range(1, 15):
        puzzles.extend(gen_chapter(ch, seen))
    print_table(puzzles)
    if dry:
        return
    with open(PUZZLES, encoding="utf8") as f:
        data = json.load(f)
    data = [p for p in data if p.get("track") != "fragment"]
    data.extend(puzzles)
    with open(PUZZLES, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    with open(VECTORS, "w", encoding="utf8") as f:
        f.write(json.dumps(build_vectors(puzzles), indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {len(puzzles)} fragment puzzles; total {len(data)}")


if __name__ == "__main__":
    main()
