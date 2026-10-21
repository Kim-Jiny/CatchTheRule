#!/usr/bin/env python3
"""조각 숫자(track:"fragment") 독립 검증기. 생성기(gen_fragment_puzzles.py)를 import 하지 않고
7세그먼트 합성을 불 대수(비트마스크)로 새로 구현해 puzzles.json 의 84문제와 테스트 벡터를 재검증한다.
실행: python3 scripts/verify_fragment_puzzles.py   (실패 시 종료 코드 1)
"""
import json, os, sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
BIT = {c: 1 << i for i, c in enumerate("abcdefg")}


def mask(s):
    m = 0
    for c in s:
        m |= BIT[c]  # KeyError => 허용되지 않은 문자
    return m


DIGIT = {mask(p): str(d) for d, p in enumerate(
    ["abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg"])}
MASK_OF = {v: k for k, v in DIGIT.items()}

errors = []


def err(msg):
    errors.append(msg)


def synth(frames, rule):
    """비트마스크 합성. 무효 None."""
    if not frames or not frames[0] or any(len(f) != len(frames[0]) for f in frames):
        return None
    res = ""
    for d in range(len(frames[0])):
        acc = 0
        for f in frames:
            m = mask(f[d])
            acc = (acc | m) if rule == "or" else (acc ^ m)
        if acc not in DIGIT:
            return None
        res += DIGIT[acc]
    return res


def traps(frames, ans):
    return sum(1 for f in frames for d, s in enumerate(f) if mask(s) in DIGIT and DIGIT[mask(s)] != ans[d])


def xor_noise(frames):
    """짝수(>=2)번 나와 사라지는 (자리,획) 수."""
    n = 0
    for d in range(len(frames[0])):
        for c in "abcdefg":
            k = sum(1 for f in frames if c in f[d])
            if k >= 2 and k % 2 == 0:
                n += 1
    return n


def or_dups(frames):
    n = 0
    for d in range(len(frames[0])):
        for c in "abcdefg":
            k = sum(1 for f in frames if c in f[d])
            if k >= 2:
                n += k - 1
    return n


# 챕터 조건(명세 §5) — 생성기와 별개로 여기서 다시 적는다.
def expect(ch):
    if ch <= 2:
        return dict(n=1, k=(2, 2), rules={"or"}, ms=(1700, 2000), inp={"keypad"})
    if ch <= 4:
        return dict(n=2, k=(2, 2), rules={"or"}, ms=(1500, 1800), inp={"keypad"})
    if ch <= 6:
        return dict(n=3, k=(3, 3), rules={"or"}, ms=(1300, 1500), inp={"keypad"})
    if ch <= 8:
        return dict(n=3, k=(3, 3), rules={"or"}, ms=(1000, 1200), inp={"keypad"})
    if ch <= 10:
        return dict(n=4, k=(3, 3), rules={"or"}, ms=(800, 1000), inp={"keypad"})
    if ch <= 12:
        return dict(n=3, k=(2, 3), rules={"xor"}, ms=(1000, 1200), inp={"keypad"})
    return dict(n=4, k=(3, 4), rules={"xor", "or"}, ms=(700, 1000), inp={"keypad"})


def main():
    with open(os.path.join(ROOT, "shared", "content", "puzzles.json"), encoding="utf8") as f:
        data = json.load(f)
    fg = [p for p in data if p.get("track") == "fragment"]
    if len(fg) != 84:
        err(f"fragment 퍼즐 수 {len(fg)} != 84")
    ids = [p["id"] for p in fg]
    if len(set(ids)) != len(ids):
        err("id 중복")
    seen = set()
    for ch in range(1, 15):
        ps = [p for p in fg if p["chapter"] == ch]
        if [p["order"] for p in ps] != list(range(1, 7)) or [p["id"] for p in ps] != [f"fg{ch}_s{o:02d}" for o in range(1, 7)]:
            err(f"ch{ch}: id/order 불연속")
        e = expect(ch)
        answers = [p["answer"] for p in ps]
        if e["n"] == 1 and len(set(answers)) != 6:
            err(f"ch{ch}: 1자리 정답 중복 {answers}")
        if e["n"] > 1 and len(set(answers)) != len(answers):
            err(f"ch{ch}: 정답 중복 {answers}")
        prev_ms = 10 ** 9
        orcount = 0
        for p in ps:
            pid = p["id"]
            where = f"{pid}"
            if p.get("type") != "fragment" or p.get("track") != "fragment":
                err(f"{where}: type/track")
            if list(p.keys()) != ["id", "type", "track", "chapter", "order", "frames", "combine", "frameMs",
                                  "answer", "inputType", "choices", "hints", "explanation"]:
                err(f"{where}: 키 순서 {list(p.keys())}")
            fr, rule, ans = p["frames"], p["combine"], p["answer"]
            n = len(ans)
            if not ans.isdigit() or ans[0] == "0":
                err(f"{where}: answer 형식 {ans}")
            if rule not in ("or", "xor"):
                err(f"{where}: combine {rule}")
            if any(len(f) != n for f in fr):
                err(f"{where}: frames 자릿수 불일치")
            for f in fr:
                for s in f:
                    if len(set(s)) != len(s) or any(c not in "abcdefg" for c in s) or "".join(sorted(s)) != s:
                        err(f"{where}: 획 문자열 형식 {s!r}")
            got = synth(fr, rule)
            if got != ans:
                err(f"{where}: 합성 {got} != answer {ans}")
            if n != e["n"]:
                err(f"{where}: 자릿수 {n} != {e['n']}")
            if not (e["k"][0] <= len(fr) <= e["k"][1]):
                err(f"{where}: 장 수 {len(fr)}")
            if rule not in e["rules"]:
                err(f"{where}: combine {rule} 허용 아님")
            if not (e["ms"][0] <= p["frameMs"] <= e["ms"][1]) or p["frameMs"] < 600:
                err(f"{where}: frameMs {p['frameMs']}")
            if p["frameMs"] > prev_ms:
                err(f"{where}: frameMs 가 증가")
            prev_ms = p["frameMs"]
            if p["inputType"] not in e["inp"]:
                err(f"{where}: inputType {p['inputType']}")
            # 빈 장 / 몰림
            for i, f in enumerate(fr):
                if not any(f):
                    err(f"{where}: {i+1}장이 비어 있음")
            if len({tuple(f) for f in fr}) != len(fr):
                err(f"{where}: 같은 장 반복")
            for f in fr:
                for d, s in enumerate(f):
                    if mask(s) == MASK_OF[ans[d]]:
                        err(f"{where}: 한 장에 {d+1}번째 자리 정답이 통째로 있음")
            # 함정/노이즈
            t = traps(fr, ans)
            if ch >= 7 and t < 1:
                err(f"{where}: 함정 없음")
            if ch >= 7 and ch <= 8 and rule == "or" and or_dups(fr) != 0:
                pass
            if rule == "xor":
                if xor_noise(fr) < 1:
                    err(f"{where}: xor 노이즈 없음")
                if synth(fr, "or") == ans:
                    err(f"{where}: xor 인데 or 로 합쳐도 같은 답(규칙 구분 불가)")
            if ch in (9, 10) and or_dups(fr) < 2:
                err(f"{where}: 중복 획 부족 {or_dups(fr)}")
            if ch in (13, 14) and rule == "or":
                orcount += 1
                if or_dups(fr) < 1:
                    err(f"{where}: or 인데 중복 없음")
            # choices
            ch_ = p["choices"]
            if p["inputType"] == "choices":
                if not (isinstance(ch_, list) and len(ch_) == 4 and len(set(ch_)) == 4 and ans in ch_
                        and all(len(c) == n and c.isdigit() and c[0] != "0" for c in ch_)):
                    err(f"{where}: choices {ch_}")
            elif ch_ is not None:
                err(f"{where}: keypad 인데 choices 존재")
            # 힌트/설명
            h, ex = p["hints"], p["explanation"]
            if list(h.keys()) != LANGS or list(ex.keys()) != LANGS:
                err(f"{where}: 언어 키")
            else:
                for l in LANGS:
                    if len(h[l]) != 3 or any(not x.strip() for x in h[l]):
                        err(f"{where}: hints[{l}] 3개 아님")
                    if ex[l] != ans:
                        err(f"{where}: explanation[{l}] != answer")
                    if len(h[l]) == 3 and n > 1:
                        if ans[0] not in h[l][1]:
                            err(f"{where}: 힌트2 [{l}] 첫 자리 숫자 누락")
                        s = str(sum(int(c) for c in ans))
                        if s not in h[l][2]:
                            err(f"{where}: 힌트3 [{l}] 합 {s} 누락")
                    if len(h[l]) == 3 and n == 1:
                        if str(len(bin(MASK_OF[ans]).replace("0b", "").replace("0", ""))) not in h[l][1]:
                            err(f"{where}: 1자리 힌트2 획수 [{l}]")
                if len({h[l][0] for l in LANGS}) != 7:
                    err(f"{where}: 힌트1 번역 중복")
            key = (ans, json.dumps(fr))
            if key in seen:
                err(f"{where}: answer+frames 중복")
            seen.add(key)
        if ch in (13, 14) and orcount < 1:
            err(f"ch{ch}: or 퍼즐 섞이지 않음")
        if ch in (13, 14) and orcount > 3:
            err(f"ch{ch}: or 가 너무 많음")
    # 다른 트랙 퍼즐은 건드리지 않았는지: 트랙 카운트만 점검
    cnt = Counter(p.get("track") for p in data)
    for tr, want in (("target", 84), ("match", 84), ("logic", 84), ("shapes", 84), ("contradiction", 78)):
        if cnt[tr] != want:
            err(f"{tr} 퍼즐 수 {cnt[tr]} != {want}")
    # 테스트 벡터
    vp = os.path.join(ROOT, "scripts", "fragment_test_vectors.json")
    with open(vp, encoding="utf8") as f:
        v = json.load(f)
    if "format" not in v or list(v.keys())[0] != "format":
        err("vectors: format 키가 맨 위가 아님")
    cases = v["combine_cases"]
    if len(cases) < 30:
        err(f"vectors: 케이스 {len(cases)} < 30")
    if not any(c["result"] is None for c in cases) or not any(c["result"] is not None for c in cases):
        err("vectors: 유효/무효 모두 필요")
    if {c["combine"] for c in cases} != {"or", "xor"}:
        err("vectors: or/xor 모두 필요")
    for c in cases:
        if synth(c["frames"], c["combine"]) != c["result"]:
            err(f"vectors: 불일치 {c}")
    byid = {p["id"]: p for p in fg}
    if len(v["samples"]) != 10:
        err("vectors: samples != 10")
    for s in v["samples"]:
        p = byid.get(s["id"])
        if not p or any(p[k] != s[k] for k in ("chapter", "order", "frames", "combine", "frameMs", "answer",
                                                 "inputType", "choices")):
            err(f"vectors: 샘플 불일치 {s['id']}")
        elif synth(s["frames"], s["combine"]) != s["answer"]:
            err(f"vectors: 샘플 합성 불일치 {s['id']}")
    if errors:
        print(f"FAIL: {len(errors)}개 오류")
        for e in errors[:60]:
            print(" -", e)
        sys.exit(1)
    print(f"OK: fragment {len(fg)}문제, 테스트 벡터 케이스 {len(cases)}개/샘플 {len(v['samples'])}개 검증 통과")


if __name__ == "__main__":
    main()
