#!/usr/bin/env python3
"""암호 해독(track:"cipher") 독립 검증기. 생성기(gen_cipher_puzzles.py)를 import 하지 않는다.
명세 docs/cipher-scale-mode-spec.md §1 §2 §5 의 모든 조건을 puzzles.json 의 84문제와 테스트 벡터에 대해 재검증한다.

- 규칙(rule)은 puzzles.json 에 없으므로 챕터 사다리의 후보 규칙 중 "예시와 일치 + query 결과 유일 + answer 와 같음 +
  map/rev/sum 으로 해석해도 모호하지 않음"을 모두 만족하는 규칙(들)을 찾아 그 집합을 기준으로 사다리 조건을 본다.
- 유일성: 기호->값 할당(서로 다름, 범위 내)을 전수 탐색(가지치기 DFS). diff 는 "항상 양수" 규칙이라 query 결과가 0 이하가
  되는 할당은 후보에서 제외.

실행: python3 scripts/verify_cipher_puzzles.py [--puzzles PATH] [--vectors PATH]   (실패 시 종료 코드 1)
"""
import argparse, json, math, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
POOL = set("🍎🍌🍇🍒🍋🍓🥝🍑🌙⭐🔥💧🌿🔔🎈🐟🐱🐸🚗🎲")
KEYS = ["id", "type", "track", "chapter", "order", "pairs", "query", "answer", "inputType", "choices",
        "hints", "explanation"]
ALL_RULES = ["map", "rev", "sum", "diff", "prod", "wsum", "cnt", "dsum"]
AMBIG = ["map", "rev", "sum"]

# 챕터 -> (후보 규칙, 기호 수 범위, query 길이 범위)
# 1챕터만 map(직접 대응). 2챕터부터 map/rev 와 기호 하나짜리 예시는 금지(2026-10-21 난이도 상향).
NON_DIRECT = ["sum", "diff", "prod", "wsum", "cnt", "dsum"]
LADDER = {
    1: (["map"], (3, 3), (2, 3)),
    2: (["sum"], (3, 3), (2, 3)),
    3: (["sum"], (4, 4), (3, 3)),
    4: (["diff"], (4, 4), (2, 3)),
    5: (["prod"], (4, 4), (2, 3)),
    6: (["wsum"], (4, 4), (3, 3)),
    7: (["cnt"], (5, 5), (3, 4)),
    8: (["diff", "prod"], (5, 5), (3, 4)),
    9: (["wsum"], (5, 5), (4, 4)),
    10: (["dsum"], (4, 5), (3, 3)),
    11: (["prod"], (5, 5), (4, 4)),
    12: (["wsum", "dsum"], (5, 6), (4, 4)),
    13: (NON_DIRECT, (6, 6), (4, 4)),
    14: (NON_DIRECT, (6, 6), (4, 4)),
}

errors = []


def err(msg):
    errors.append(msg)


def vrange(rule):
    return 12 if rule == "diff" else 9


def compute(rule, digits):
    """digits: 기호 값 리스트 -> 결과 문자열 또는 None(무효)."""
    if rule == "map":
        return "".join(str(d) for d in digits)
    if rule == "rev":
        return "".join(str(d) for d in digits)[::-1]
    if rule == "sum":
        r = sum(digits)
    elif rule == "diff":
        r = digits[0] - sum(digits[1:])
    elif rule == "prod":
        r = math.prod(digits)
    elif rule == "wsum":
        r = sum((i + 1) * d for i, d in enumerate(digits))
    elif rule == "cnt":
        r = sum(digits) + len(digits)
    elif rule == "dsum":
        s = "".join(str(d) for d in digits)
        r = int(s) + int(s[::-1])
    else:
        raise ValueError(rule)
    return str(r) if r > 0 else None


def search(rule, examples, query, lo=1):
    """examples: [(입력 토큰들, 출력 문자열)]. 일치하는 모든 할당에서 query 결과 집합과 할당 수를 돌려준다."""
    hi = vrange(rule)
    order = []
    for inp, _ in examples:
        for t in inp:
            if t not in order:
                order.append(t)
    for t in query:
        if t not in order:
            order.append(t)
    pos = {t: i for i, t in enumerate(order)}
    n = len(order)
    done_at = [[] for _ in range(n)]  # 이 깊이에서 처음 완성되는 예시
    for inp, out in examples:
        done_at[max(pos[t] for t in inp)].append((inp, out))
    cur = [0] * n
    outs, cnt = set(), 0
    used = [False] * (hi + 1)

    def rec(k):
        nonlocal cnt
        if k == n:
            r = compute(rule, [cur[pos[t]] for t in query])
            cnt += 1
            if r is not None:
                outs.add(r)
            return
        for v in range(lo, hi + 1):
            if used[v]:
                continue
            cur[k] = v
            ok = True
            for inp, out in done_at[k]:
                if compute(rule, [cur[pos[t]] for t in inp]) != out:
                    ok = False
                    break
            if ok:
                used[v] = True
                rec(k + 1)
                used[v] = False

    rec(0)
    return outs, cnt


def judge(rule, examples, query, answer):
    """(규칙이 성립하는가, 이유). 유일 + answer 일치 + 모호하지 않음."""
    outs, cnt = search(rule, examples, query)
    if cnt == 0:
        return False, "예시와 일치하는 할당 없음"
    if outs != {answer}:
        return False, f"query 결과 {sorted(outs)} != {{{answer}}}"
    for r2 in AMBIG:
        if r2 == rule:
            continue
        o2, c2 = search(r2, examples, query)
        if c2 and not o2 <= {answer}:
            return False, f"{r2} 로 해석하면 query 결과 {sorted(o2)} (모호함)"
    return True, ""


def has_repeat(seq):
    return len(set(seq)) < len(seq)


def check_text_block(where, p):
    h, ex = p.get("hints"), p.get("explanation")
    if not isinstance(h, dict) or list(h.keys()) != LANGS:
        err(f"{where}: hints 언어 키가 {LANGS} 가 아님")
        return
    if not isinstance(ex, dict) or list(ex.keys()) != LANGS:
        err(f"{where}: explanation 언어 키가 {LANGS} 가 아님")
        return
    for l in LANGS:
        if not (isinstance(h[l], list) and len(h[l]) == 3 and all(isinstance(s, str) and s.strip() for s in h[l])):
            err(f"{where}: hints[{l}] 은 비어있지 않은 문자열 3개여야 함")
        if not (isinstance(ex[l], str) and ex[l].strip()):
            err(f"{where}: explanation[{l}] 비어 있음")
        elif p["answer"] not in ex[l]:
            err(f"{where}: explanation[{l}] 에 정답이 없음")
    if all(isinstance(h[l], list) and len(h[l]) == 3 for l in LANGS):
        for i in range(3):
            if i != 1 and len({h[l][i] for l in LANGS}) != 7:
                err(f"{where}: hints 첫째/셋째 문장이 언어마다 달라야 함(번역 누락 의심) idx {i}")
        # 구체 단서/자릿수 단서에 정답 자릿수 정보가 맞는지
        d = len(p["answer"])
        if d == 1:
            # 한 자리 정답: 자릿수/첫 자리 단서는 정답 그대로라 홀수·짝수 단서여야 한다(정답 노출 방지)
            want = "홀수" if int(p["answer"]) % 2 else "짝수"
            if want not in h["ko"][2] or p["answer"] in h["ko"][2]:
                err(f"{where}: 한 자리 정답의 ko hint3 는 {want} 단서여야 함(정답 숫자 노출 금지)")
        elif str(d) not in h["ko"][2] or p["answer"][0] not in h["ko"][2]:
            err(f"{where}: ko hint3 에 자릿수/첫 자리가 정답과 다름")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=os.path.join(ROOT, "shared", "content", "puzzles.json"))
    ap.add_argument("--vectors", default=os.path.join(ROOT, "scripts", "cipher_test_vectors.json"))
    a = ap.parse_args()
    with open(a.puzzles, encoding="utf8") as f:
        data = json.load(f)
    cip = [p for p in data if p.get("track") == "cipher"]
    # 위치: cipher 항목은 한 덩어리로 연속(뒤에 다른 신규 모드가 붙을 수 있음)
    idx = [i for i, p in enumerate(data) if p.get("track") == "cipher"]
    if idx and idx[-1] - idx[0] + 1 != len(idx):
        err("cipher 항목이 다른 모드 항목과 섞여 있음(연속된 한 덩어리여야 함)")
    if len(cip) != 84:
        err(f"cipher 문제 수 {len(cip)} != 84")
    exp_ids = [f"cp{ch}_s{o:02d}" for ch in range(1, 15) for o in range(1, 7)]
    if [p.get("id") for p in cip] != exp_ids:
        err("id/순서가 cp1_s01..cp14_s06 순서와 다름")
    all_ids = [p.get("id") for p in data]
    if len(set(all_ids)) != len(all_ids):
        err("puzzles.json 전체에 중복 id 가 있음")

    seen_key = {}
    passing = {}  # id -> 성립 규칙 집합
    for p in cip:
        pid = p.get("id", "?")
        if list(p.keys()) != KEYS:
            err(f"{pid}: 키 구성/순서가 {KEYS} 와 다름: {list(p.keys())}")
            continue
        mm = re.fullmatch(r"cp(\d+)_s(\d\d)", pid)
        if not mm:
            err(f"{pid}: id 형식 오류")
            continue
        ch, o = int(mm.group(1)), int(mm.group(2))
        if p["chapter"] != ch or p["order"] != o:
            err(f"{pid}: chapter/order 불일치")
        if p["type"] != "cipher" or p["track"] != "cipher":
            err(f"{pid}: type/track != cipher")
        if p["inputType"] != "keypad" or p["choices"] is not None:
            err(f"{pid}: inputType 은 keypad, choices 는 null 이어야 함")
        if ch not in LADDER:
            err(f"{pid}: 챕터 범위 밖")
            continue
        ans = p["answer"]
        if not (isinstance(ans, str) and ans.isdigit() and 1 <= len(ans) <= 4 and ans[0] != "0"):
            err(f"{pid}: answer '{ans}' 는 선행 0 없는 1~4자리 숫자여야 함")
            continue
        pairs, query = p["pairs"], p["query"]
        if not (isinstance(pairs, list) and 3 <= len(pairs) <= 6):
            err(f"{pid}: 예시 수 {len(pairs) if isinstance(pairs, list) else '?'} 는 3~6 이어야 함")
            continue
        ok_shape = True
        examples = []
        for pr in pairs:
            if not (isinstance(pr, list) and len(pr) == 2 and all(isinstance(x, list) and x for x in pr)):
                ok_shape = False
                break
            inp, out = pr
            if not (1 <= len(inp) <= 4 and all(t in POOL for t in inp)):
                err(f"{pid}: 예시 입력 토큰 오류 {inp}")
                ok_shape = False
            if not all(isinstance(t, str) and len(t) == 1 and t.isdigit() for t in out):
                err(f"{pid}: 예시 출력 토큰은 한 자리 숫자 문자열이어야 함 {out}")
                ok_shape = False
                continue
            s = "".join(out)
            if s[0] == "0":
                err(f"{pid}: 예시 출력 선행 0 {s}")
            examples.append((tuple(inp), s))
        if not ok_shape or not (isinstance(query, list) and 1 <= len(query) <= 4 and all(t in POOL for t in query)):
            err(f"{pid}: pairs/query 형식 오류")
            continue
        syms = []
        for inp, _ in examples:
            for t in inp:
                if t not in syms:
                    syms.append(t)
        for t in query:
            if t not in syms:
                if t not in {x for i, _ in examples for x in i}:
                    err(f"{pid}: query 기호 {t} 가 예시에 한 번도 나오지 않아 값이 결정되지 않음")
        if len({i for i, _ in examples}) != len(examples):
            err(f"{pid}: 같은 입력의 예시가 중복됨")
        if tuple(query) in {i for i, _ in examples}:
            err(f"{pid}: query 가 예시 입력과 같음")
        key = json.dumps([pairs, query], ensure_ascii=False)
        if key in seen_key:
            err(f"{pid}: {seen_key[key]} 와 중복 퍼즐")
        seen_key[key] = pid
        allowed, (nlo, nhi), (qlo, qhi) = LADDER[ch]
        nsym = len(syms)
        if not (nlo <= nsym <= nhi):
            err(f"{pid}: 기호 수 {nsym} 가 챕터 {ch} 범위 {nlo}~{nhi} 밖")
        if not (qlo <= len(query) <= qhi):
            err(f"{pid}: query 길이 {len(query)} 가 챕터 {ch} 범위 {qlo}~{qhi} 밖")
        if ch >= 2 and any(len(i) < 2 for i, _ in examples):
            err(f"{pid}: 챕터 {ch} 는 기호 하나짜리 예시를 쓰면 안 됨(값이 바로 드러남)")
        if ch <= 1 and has_repeat(query):
            err(f"{pid}: 챕터 {ch} query 에 기호 반복이 없어야 함")
        if ch <= 4:
            if any(has_repeat(i) for i, _ in examples):
                err(f"{pid}: 챕터 {ch} 예시 입력에 기호 반복이 없어야 함")
        if ch >= 9 and not (has_repeat(query) or any(has_repeat(i) for i, _ in examples)):
            err(f"{pid}: 챕터 {ch} 는 반복 기호가 (예시 또는 query 에) 있어야 함")
        if ch >= 13 and len({len(i) for i, _ in examples}) < 2:
            err(f"{pid}: 챕터 {ch} 는 예시 입력 길이가 다양해야 함")
        if ch <= 2 and any(len(i) not in (2, 3) for i, _ in examples):
            err(f"{pid}: 챕터 {ch} 예시 입력 길이는 2~3")
        # 규칙 성립 판정
        good = set()
        why = {}
        for r in allowed:
            ok, reason = judge(r, examples, tuple(query), ans)
            if ok:
                good.add(r)
            else:
                why[r] = reason
        passing[pid] = good
        for r in good:
            for i, _ in examples:
                same = (sorted(i) == sorted(query)) if r in ("sum", "prod", "cnt") else (
                    len(i) == len(query) and i[0] == query[0] and sorted(i[1:]) == sorted(query[1:]) if r == "diff"
                    else list(i) == list(query))
                if same:
                    err(f"{pid}: {r} 규칙에서 예시 {''.join(i)} 가 query 와 같은 계산이라 정답이 그대로 드러남")
        if not good:
            err(f"{pid}: 후보 규칙 {allowed} 중 유일하고 모호하지 않게 성립하는 규칙이 없음: {why}")
        else:
            if ch >= 13:
                need = 1 if good == {"diff"} else (2 if good - {"map", "rev", "prod", "dsum"} else 3)
                if len(ans) < need:
                    err(f"{pid}: 챕터 {ch} 정답 자릿수 {len(ans)} < {need}")
            if good == {"prod"} and ch == 11 and not has_repeat(query):
                err(f"{pid}: prod+반복인데 query 에 반복 기호가 없음")
        check_text_block(pid, p)

    # 챕터 단위 규칙 구성
    def cover(chs, rules, label):
        have = set()
        for p in cip:
            if p["chapter"] in chs:
                have |= passing.get(p["id"], set())
        miss = set(rules) - have
        if miss:
            err(f"{label}: 규칙 {sorted(miss)} 가 한 문제도 없음")

    cover((8,), ["diff", "prod"], "챕터 8")
    cover((12,), ["wsum", "dsum"], "챕터 12")
    cover((13, 14), NON_DIRECT, "챕터 13~14")

    verify_vectors(a.vectors, cip)

    if errors:
        print(f"FAIL: {len(errors)} 건")
        for e in errors[:80]:
            print(" -", e)
        sys.exit(1)
    cnt = {}
    for pid, g in passing.items():
        k = "/".join(sorted(g))
        cnt[k] = cnt.get(k, 0) + 1
    print(f"OK: cipher {len(cip)}문제, 성립 규칙 분포 {dict(sorted(cnt.items()))}")


def verify_vectors(path, cip):
    if not os.path.exists(path):
        err(f"테스트 벡터 없음: {path}")
        return
    with open(path, encoding="utf8") as f:
        v = json.load(f)
    if not isinstance(v.get("format"), str) or not v.get("eval_cases") or not v.get("samples"):
        err("테스트 벡터에 format/eval_cases/samples 가 있어야 함")
        return
    rules = set()
    for i, c in enumerate(v["eval_cases"]):
        rules.add(c["rule"])
        vals = c["values"]
        if len(set(vals.values())) != len(vals):
            err(f"vector eval_cases[{i}]: 값이 서로 달라야 함")
        if not all(1 <= x <= vrange(c["rule"]) for x in vals.values()):
            err(f"vector eval_cases[{i}]: 값 범위 밖")
        got = compute(c["rule"], [vals[t] for t in c["input"]])
        if got != c["output"]:
            err(f"vector eval_cases[{i}] {c['rule']} {c['input']}: 기대 {c['output']} 계산 {got}")
    if rules != set(ALL_RULES):
        err(f"vector eval_cases 가 모든 규칙을 포함해야 함: {sorted(rules)}")
    if len(v["samples"]) != 10:
        err("vector samples 는 10개여야 함")
    byid = {p["id"]: p for p in cip}
    for s in v["samples"]:
        p = byid.get(s["id"])
        if p is None:
            err(f"vector sample {s['id']} 가 puzzles.json 에 없음")
            continue
        for k in ("chapter", "order", "pairs", "query", "answer", "inputType", "choices"):
            if s.get(k) != p[k]:
                err(f"vector sample {s['id']}: {k} 가 puzzles.json 과 다름")
        vals = s["values"]
        if len(set(vals.values())) != len(vals):
            err(f"vector sample {s['id']}: 값이 서로 달라야 함")
        for inp, out in p["pairs"]:
            if compute(s["rule"], [vals[t] for t in inp]) != "".join(out):
                err(f"vector sample {s['id']}: 값/규칙이 예시와 안 맞음 {inp}")
        if compute(s["rule"], [vals[t] for t in p["query"]]) != p["answer"]:
            err(f"vector sample {s['id']}: 값/규칙으로 계산한 query 결과가 answer 와 다름")


if __name__ == "__main__":
    main()
