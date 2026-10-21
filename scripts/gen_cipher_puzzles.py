#!/usr/bin/env python3
"""암호 해독(track:"cipher") 퍼즐 생성기. 명세: docs/cipher-scale-mode-spec.md §1 §2 §5 §6

14챕터 x 6문제 = 84문제(id cp{ch}_s{01..06})를 시드 고정으로 생성해 puzzles.json 에 병합한다.
재실행 안전: track=="cipher" 항목만 지우고 맨 뒤에 다시 추가한다(나머지 퍼즐은 바이트 동일).
같은 실행에서 플랫폼 검증용 scripts/cipher_test_vectors.json 도 만든다.

실행:  python3 scripts/gen_cipher_puzzles.py [--puzzles PATH] [--vectors PATH] [--dry]
  --puzzles  병합 대상 (기본 shared/content/puzzles.json)
  --vectors  테스트 벡터 출력 (기본 scripts/cipher_test_vectors.json)
  --dry      파일을 쓰지 않고 챕터별 지표 표만 출력

규칙(rule)은 puzzles.json 에 저장하지 않는다(명세 스키마). 검증기가 챕터 사다리의 후보 규칙으로 역추론한다.
유일성: 규칙에 맞는 모든 기호->값 할당(값 서로 다름)을 전수 탐색해 예시와 모두 일치하는 할당들의 query 결과가
단 하나임을 보장하고, 다른 규칙(map/rev/sum)으로 해석해도 결과가 다르게 나오지 않음을 확인한다.
(diff 는 "항상 양수" 규칙이므로 query 결과가 0 이하가 되는 할당은 후보에서 제외한다.)
"""
import argparse, json, os, random
from itertools import permutations

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
VECTORS = os.path.join(ROOT, "scripts", "cipher_test_vectors.json")
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
EMOJI = ["🍎", "🍌", "🍇", "🍒", "🍋", "🍓", "🥝", "🍑", "🌙", "⭐", "🔥", "💧", "🌿", "🔔", "🎈", "🐟",
         "🐱", "🐸", "🚗", "🎲"]
RULES = ["map", "rev", "sum", "diff", "prod", "wsum", "cnt", "dsum"]
AMBIG = ["map", "rev", "sum"]  # 모호함 검사 대상 규칙


def vmax(rule):
    return 12 if rule == "diff" else 9


# ---------------------------------------------------------------- 규칙 평가 (정수식)
def expr_of(rule, idxs):
    L = len(idxs)
    if rule == "map":
        return "+".join(f"v[{i}]*{10 ** (L - 1 - p)}" for p, i in enumerate(idxs))
    if rule == "rev":
        return "+".join(f"v[{i}]*{10 ** p}" for p, i in enumerate(idxs))
    if rule == "sum":
        return "+".join(f"v[{i}]" for i in idxs)
    if rule == "diff":
        return "-".join(f"v[{i}]" for i in idxs)
    if rule == "prod":
        return "*".join(f"v[{i}]" for i in idxs)
    if rule == "wsum":
        return "+".join(f"{p + 1}*v[{i}]" for p, i in enumerate(idxs))
    if rule == "cnt":
        return "+".join(f"v[{i}]" for i in idxs) + f"+{L}"
    if rule == "dsum":
        return expr_of("map", idxs) + "+" + expr_of("rev", idxs)
    raise ValueError(rule)


_FN = {}


def fn(rule, idxs):
    k = (rule, tuple(idxs))
    f = _FN.get(k)
    if f is None:
        f = _FN[k] = eval("lambda v: " + expr_of(rule, idxs))
    return f


def evaluate(rule, values, idxs):
    """정수 결과. 규칙상 무효(diff<=0)면 None."""
    r = fn(rule, idxs)(values)
    return r if r > 0 else None


_UNI = {}


def universe(n, vm):
    k = (n, vm)
    if k not in _UNI:
        _UNI[k] = list(permutations(range(1, vm + 1), n))
    return _UNI[k]


def filt(rule, n, examples, vm=None):
    """예시(idxs, 출력정수)와 모두 일치하는 값 할당 목록."""
    sols = universe(n, vm or vmax(rule))
    for idxs, t in examples:
        f = fn(rule, idxs)
        sols = [v for v in sols if f(v) == t]
        if not sols:
            break
    return sols


def query_outs(rule, sols, q):
    f = fn(rule, q)
    outs = set()
    for v in sols:
        r = f(v)
        if r > 0:
            outs.add(r)
    return outs


def unique_ok(rule, n, examples, q, ans):
    sols = filt(rule, n, examples)
    return query_outs(rule, sols, q) == {ans}


def ambiguity_ok(rule, n, examples, q, ans):
    for r2 in AMBIG:
        if r2 == rule:
            continue
        s2 = filt(r2, n, examples, 9)
        if s2 and not query_outs(r2, s2, q) <= {ans}:
            return False
    return True


# ---------------------------------------------------------------- 챕터 설정
def cfg(ch, o):
    """챕터 사다리. 1챕터만 예시에서 기호 값이 바로 읽히는 map. 2챕터부터는 map/rev 와 '기호 하나짜리 예시'를 쓰지 않아
    여러 예시를 엮어 값을 추론해야 풀린다(2026-10-21 사용자 결정: 난이도 상향)."""
    t = o - 1
    c = dict(exl=[2, 3], ex_rep=True, q_rep=None, req_repeat=False, vary_len=False, min_digits=1)
    if ch == 1:
        c.update(rule="map", n=3, qlen=[2, 2, 2, 3, 3, 3][t], exl=[2], ex_rep=False, q_rep=False)
    elif ch == 2:  # 합 — 기호 3개, 예시는 둘/셋짜리 합으로 연립
        c.update(rule="sum", n=3, qlen=[2, 2, 2, 3, 3, 3][t], exl=[2, 3], ex_rep=False,
                 q_rep=False if t < 3 else None)
    elif ch == 3:
        c.update(rule="sum", n=4, qlen=3, exl=[2, 3], ex_rep=False, q_rep=False if t < 3 else None)
    elif ch == 4:  # 첫 기호 − 나머지
        c.update(rule="diff", n=4, qlen=[2, 2, 3, 3, 3, 3][t], exl=[2, 3], ex_rep=False, q_rep=False)
    elif ch == 5:
        c.update(rule="prod", n=4, qlen=[2, 2, 3, 3, 3, 3][t], exl=[2, 3], ex_rep=False,
                 q_rep=False if t < 3 else None)
    elif ch == 6:
        c.update(rule="wsum", n=4, qlen=3, exl=[2, 3], q_rep=False if t < 3 else None)
    elif ch == 7:  # 합 + 기호 개수
        c.update(rule="cnt", n=5, qlen=[3, 3, 3, 4, 4, 4][t], exl=[2, 3, 4])
    elif ch == 8:
        c.update(rule=["diff", "prod"][t % 2], n=5, qlen=[3, 3, 4, 4, 4, 4][t], exl=[2, 3, 4], req_repeat=True)
    elif ch == 9:
        c.update(rule="wsum", n=5, qlen=4, exl=[2, 3, 4], req_repeat=True)
    elif ch == 10:
        c.update(rule="dsum", n=[4, 4, 4, 5, 5, 5][t], qlen=3, exl=[2, 3], req_repeat=True)
    elif ch == 11:
        c.update(rule="prod", n=5, qlen=4, exl=[2, 3, 4], req_repeat=True, q_rep=True)
    elif ch == 12:
        c.update(rule=["wsum", "dsum"][t % 2], n=[5, 5, 5, 6, 6, 6][t], qlen=4, exl=[2, 3, 4], req_repeat=True)
    elif ch == 13:
        c.update(rule=["sum", "diff", "prod", "wsum", "cnt", "dsum"][t], n=6, qlen=4,
                 exl=[2, 3, 4], vary_len=True, req_repeat=True)
    else:
        c.update(rule=["dsum", "prod", "wsum", "diff", "cnt", "sum"][t], n=6, qlen=4,
                 exl=[2, 3, 4], vary_len=True, req_repeat=True)
    if ch >= 13:
        # prod/dsum 은 3~4자리. sum/cnt/wsum 은 4기호로 100 이상이 불가능 -> 2자리 이상, diff 는 최대 9 -> 1자리
        c["min_digits"] = {"prod": 3, "dsum": 3, "diff": 1}.get(c["rule"], 2)
    return c


def same_as_query(rule, idxs, q):
    """예시가 query 와 '같은 계산'이라 정답이 그대로 드러나는가(합·곱·개수는 순서만 바꿔도 같은 값)."""
    if rule in ("sum", "prod", "cnt"):
        return sorted(idxs) == sorted(q)
    if rule == "diff":
        return len(idxs) == len(q) and idxs[0] == q[0] and sorted(idxs[1:]) == sorted(q[1:])
    return list(idxs) == list(q)


# ---------------------------------------------------------------- 생성
def rand_seq(rng, n, length, rep, must=None):
    """길이 length 인 기호 인덱스열. rep=False 면 중복 없이. must 는 포함시킬 인덱스들."""
    must = [m for m in (must or [])][:length]
    if rep:
        seq = list(must) + [rng.randrange(n) for _ in range(length - len(must))]
    else:
        rest = [i for i in range(n) if i not in must]
        seq = list(must) + rng.sample(rest, length - len(must))
    rng.shuffle(seq)
    return seq


def gen_query(rng, c):
    n, L = c["n"], c["qlen"]
    for _ in range(200):
        if c["q_rep"] is False:
            q = rng.sample(range(n), L)
        else:
            q = [rng.randrange(n) for _ in range(L)]
            if c["q_rep"] is True and len(set(q)) == len(q):
                continue
        if c["q_rep"] is None and rng.random() < 0.4 and len(set(q)) < len(q):
            continue
        return q
    raise RuntimeError("query 생성 실패")


def has_repeat(seq):
    return len(set(seq)) < len(seq)


def conds_ok(c, exs, q):
    """생성 조건(예시 집합 단위): 기호 전부 등장, 반복, 길이 다양성."""
    seen = set(q)
    for idxs, _ in exs:
        seen |= set(idxs)
    if len(seen) != c["n"]:
        return False
    if c["req_repeat"] and not (has_repeat(q) or any(has_repeat(i) for i, _ in exs)):
        return False
    if c["vary_len"] and len({len(i) for i, _ in exs}) < 2:
        return False
    return True


def good(c, n, exs, q, ans):
    return (conds_ok(c, exs, q) and unique_ok(c["rule"], n, exs, q, ans)
            and ambiguity_ok(c["rule"], n, exs, q, ans))


def candidate(rng, c, vals, q, used, uncovered):
    for _ in range(100):
        L = rng.choice(c["exl"])
        if not c["ex_rep"]:
            L = min(L, c["n"])
        must = [rng.choice(sorted(uncovered))] if uncovered and rng.random() < 0.85 else []
        idxs = rand_seq(rng, c["n"], L, c["ex_rep"], must)
        if same_as_query(c["rule"], idxs, q) or tuple(idxs) in used:
            continue
        r = evaluate(c["rule"], vals, idxs)
        if r is None:
            continue
        return idxs, r
    return None


def try_build(rng, c):
    n, rule = c["n"], c["rule"]
    vals = tuple(rng.sample(range(1, vmax(rule) + 1), n))
    q = gen_query(rng, c)
    ans = evaluate(rule, vals, q)
    if ans is None or ans > 9999 or len(str(ans)) < c["min_digits"]:
        return None
    exs, used = [], set()
    sols = universe(n, vmax(rule))
    covered = set(q)
    done = False
    for step in range(6):
        uncovered = set(range(n)) - covered
        cands = []
        for _ in range(1 if step == 0 else 10):
            cd = candidate(rng, c, vals, q, used, uncovered)
            if cd is not None:
                cands.append(cd)
        if not cands:
            return None
        if len(cands) > 1:
            scored = []
            for idxs, r in cands:
                f = fn(rule, idxs)
                scored.append((sum(1 for v in sols if f(v) == r), rng.random(), idxs, r))
            scored.sort(key=lambda x: (x[0], x[1]))
            pick = scored[0] if rng.random() < 0.7 else scored[min(1, len(scored) - 1)]
            idxs, r = pick[2], pick[3]
        else:
            idxs, r = cands[0]
        f = fn(rule, idxs)
        sols = [v for v in sols if f(v) == r]
        exs.append((idxs, r))
        used.add(tuple(idxs))
        covered |= set(idxs)
        if len(exs) >= 3 or step >= 1:
            if conds_ok(c, exs, q) and query_outs(rule, sols, q) == {ans} and ambiguity_ok(rule, n, exs, q, ans):
                done = True
                break
    if not done:
        return None
    # 불필요한 예시 제거(정확히 필요한 만큼만)
    order = list(range(len(exs)))
    rng.shuffle(order)
    for i in order:
        trial = [e for j, e in enumerate(exs) if j != i]
        if trial and good(c, n, trial, q, ans):
            exs = trial
    # 3개 미만이면 일치하는 예시를 보태서 3개로
    tries = 0
    while len(exs) < 3 and tries < 200:
        tries += 1
        cd = candidate(rng, c, vals, q, {tuple(e[0]) for e in exs}, set())
        if cd is not None:
            exs.append(cd)
    if len(exs) < 3 or not good(c, n, exs, q, ans):
        return None
    if c["vary_len"] and len({len(i) for i, _ in exs}) < 2:
        return None
    return vals, q, ans, exs


# ---------------------------------------------------------------- 힌트 / 해설
RULE_HINT = {
    "map": {
        "ko": "먼저 기호 하나하나가 어떤 숫자인지 찾아보세요. 답은 그 숫자들을 순서대로 이어 붙인 수예요.",
        "en": "First work out which digit each symbol stands for. The answer is those digits written side by side, in order.",
        "ja": "まず記号ひとつひとつがどの数字なのかを探しましょう。答えは、その数字を順番に並べてつなげた数です。",
        "zh": "先找出每个符号分别代表哪个数字。答案就是把这些数字按顺序连在一起组成的数。",
        "es": "Primero averigua qué dígito representa cada símbolo. La respuesta son esos dígitos escritos uno junto a otro, en orden.",
        "fr": "Trouve d'abord quel chiffre représente chaque symbole. La réponse est ces chiffres écrits à la suite, dans l'ordre.",
        "de": "Finde zuerst heraus, für welche Ziffer jedes Symbol steht. Die Antwort sind diese Ziffern, der Reihe nach hintereinander geschrieben.",
    },
    "rev": {
        "ko": "기호 하나하나가 어떤 숫자인지 찾은 뒤, 이어 붙인 수를 거꾸로 뒤집어 보세요.",
        "en": "Find the digit behind each symbol, write them side by side, then flip the whole number backwards.",
        "ja": "記号ひとつひとつの数字を見つけてつなげ、できた数を逆から読んでみましょう。",
        "zh": "找出每个符号代表的数字，连在一起之后，再把整个数倒过来。",
        "es": "Averigua el dígito de cada símbolo, júntalos y luego da la vuelta al número entero.",
        "fr": "Trouve le chiffre de chaque symbole, écris-les à la suite, puis retourne le nombre à l'envers.",
        "de": "Finde die Ziffer hinter jedem Symbol, schreibe sie hintereinander und drehe die ganze Zahl dann um.",
    },
    "sum": {
        "ko": "답은 각 기호가 나타내는 숫자들을 모두 더한 값이에요.",
        "en": "The answer is the sum of the numbers the symbols stand for.",
        "ja": "答えは、各記号が表す数をすべて足した値です。",
        "zh": "答案是各个符号所代表的数相加的和。",
        "es": "La respuesta es la suma de los números que representan los símbolos.",
        "fr": "La réponse est la somme des nombres que représentent les symboles.",
        "de": "Die Antwort ist die Summe der Zahlen, für die die Symbole stehen.",
    },
    "diff": {
        "ko": "첫 번째 기호의 숫자에서 나머지 기호들의 숫자를 모두 뺀 값이에요.",
        "en": "Take the first symbol's number and subtract the numbers of all the other symbols.",
        "ja": "1つ目の記号の数から、残りの記号の数をすべて引いた値です。",
        "zh": "用第一个符号的数，减去其余所有符号的数。",
        "es": "Al número del primer símbolo se le restan los números de todos los demás.",
        "fr": "Prends le nombre du premier symbole et retranches-en les nombres de tous les autres.",
        "de": "Nimm die Zahl des ersten Symbols und ziehe die Zahlen aller anderen Symbole ab.",
    },
    "prod": {
        "ko": "답은 각 기호가 나타내는 숫자들을 모두 곱한 값이에요.",
        "en": "The answer is the product of the numbers the symbols stand for.",
        "ja": "答えは、各記号が表す数をすべて掛けた値です。",
        "zh": "答案是各个符号所代表的数相乘的积。",
        "es": "La respuesta es el producto de los números que representan los símbolos.",
        "fr": "La réponse est le produit des nombres que représentent les symboles.",
        "de": "Die Antwort ist das Produkt der Zahlen, für die die Symbole stehen.",
    },
    "wsum": {
        "ko": "첫 번째 기호는 1배, 두 번째는 2배, 세 번째는 3배… 이렇게 자리 번호를 곱한 뒤 모두 더해요.",
        "en": "Multiply the 1st symbol's number by 1, the 2nd by 2, the 3rd by 3, and so on, then add everything up.",
        "ja": "1つ目の記号は1倍、2つ目は2倍、3つ目は3倍…と位置の番号を掛けて、すべて足します。",
        "zh": "第一个符号乘1，第二个乘2，第三个乘3……按位置序号相乘后全部相加。",
        "es": "Multiplica el número del 1.er símbolo por 1, el del 2.º por 2, el del 3.º por 3, etc., y suma todo.",
        "fr": "Multiplie le nombre du 1er symbole par 1, celui du 2e par 2, celui du 3e par 3, etc., puis additionne le tout.",
        "de": "Multipliziere die Zahl des 1. Symbols mit 1, die des 2. mit 2, die des 3. mit 3 usw. und addiere alles.",
    },
    "cnt": {
        "ko": "기호들의 숫자를 모두 더한 뒤, 기호의 개수만큼 더 보태요.",
        "en": "Add up the numbers of the symbols, then add one more for each symbol.",
        "ja": "記号の数をすべて足したあと、記号の個数ぶんをさらに足します。",
        "zh": "先把各符号的数相加，再加上符号的个数。",
        "es": "Suma los números de los símbolos y luego añade tantas unidades como símbolos haya.",
        "fr": "Additionne les nombres des symboles, puis ajoute encore autant d'unités qu'il y a de symboles.",
        "de": "Addiere die Zahlen der Symbole und zähle dann noch so viele dazu, wie es Symbole gibt.",
    },
    "dsum": {
        "ko": "기호의 숫자를 이어 붙인 수에, 그 수를 뒤집은 수를 더해요. (예: 73 → 73 + 37 = 110)",
        "en": "Write the symbols' digits side by side, then add the number flipped backwards. (e.g. 73 → 73 + 37 = 110)",
        "ja": "記号の数字をつなげた数に、それを逆にした数を足します。(例: 73 → 73 + 37 = 110)",
        "zh": "把符号代表的数字连在一起，再加上它倒过来的数。（例：73 → 73 + 37 = 110）",
        "es": "Junta los dígitos de los símbolos y suma el número dado la vuelta. (p. ej. 73 → 73 + 37 = 110)",
        "fr": "Écris les chiffres des symboles à la suite, puis ajoute le nombre retourné. (ex. 73 → 73 + 37 = 110)",
        "de": "Schreibe die Ziffern der Symbole hintereinander und addiere die umgedrehte Zahl. (z. B. 73 → 73 + 37 = 110)",
    },
}


def iyo(num):
    """한국어 서술격 조사: 숫자를 읽은 마지막 소리에 받침이 있으면 '이에요', 없으면 '예요'(2·4·5·9)."""
    return "예요" if str(num)[-1] in "2459" else "이에요"


def clue_text(lang, sym, v):
    return {
        "ko": f"{sym}의 숫자는 {v}{iyo(v)}.",
        "en": f"{sym} stands for {v}.",
        "ja": f"{sym}は{v}です。",
        "zh": f"{sym}代表{v}。",
        "es": f"{sym} es {v}.",
        "fr": f"{sym} vaut {v}.",
        "de": f"{sym} ist {v}.",
    }[lang]


def parity_text(lang, ans):
    """한 자리 정답: '첫 자리는 N'이 곧 정답이라 홀수/짝수 단서로 대체."""
    odd = int(ans) % 2 == 1
    return {
        "ko": "답은 홀수예요." if odd else "답은 짝수예요.",
        "en": "The answer is odd." if odd else "The answer is even.",
        "ja": "答えは奇数です。" if odd else "答えは偶数です。",
        "zh": "答案是奇数。" if odd else "答案是偶数。",
        "es": "La respuesta es impar." if odd else "La respuesta es par.",
        "fr": "La réponse est impaire." if odd else "La réponse est paire.",
        "de": "Die Antwort ist ungerade." if odd else "Die Antwort ist gerade.",
    }[lang]


def digits_text(lang, n, first):
    one = n == 1
    return {
        "ko": f"답은 {n}자리 수이고, 첫 자리는 {first}{iyo(first)}.",
        "en": f"The answer has {n} digit{'' if one else 's'} and starts with {first}.",
        "ja": f"答えは{n}桁の数で、最初の桁は{first}です。",
        "zh": f"答案是{n}位数，第一位是{first}。",
        "es": f"La respuesta tiene {n} {'dígito' if one else 'dígitos'} y empieza por {first}.",
        "fr": f"La réponse a {n} {'chiffre' if one else 'chiffres'} et commence par {first}.",
        "de": f"Die Antwort hat {n} {'Ziffer' if one else 'Ziffern'} und beginnt mit {first}.",
    }[lang]


def calc_text(rule, vals, q, ans):
    vs = [vals[i] for i in q]
    if rule == "map":
        return str(ans)
    if rule == "rev":
        return f"{''.join(map(str, vs))} → {ans}"
    if rule == "sum":
        return "+".join(map(str, vs)) + f"={ans}"
    if rule == "diff":
        return "−".join(map(str, vs)) + f"={ans}"
    if rule == "prod":
        return "×".join(map(str, vs)) + f"={ans}"
    if rule == "wsum":
        return "+".join(f"{p + 1}×{v}" for p, v in enumerate(vs)) + f"={ans}"
    if rule == "cnt":
        return f"({'+'.join(map(str, vs))})+{len(vs)}={ans}"
    s = "".join(map(str, vs))
    return f"{s}+{s[::-1]}={ans}"


def make_texts(rule, symbols, vals, q, ans):
    first_sym = q[0]
    hints = {}
    for lang in LANGS:
        hints[lang] = [RULE_HINT[rule][lang],
                       clue_text(lang, symbols[first_sym], vals[first_sym]),
                       (parity_text(lang, ans) if len(str(ans)) == 1 else digits_text(lang, len(str(ans)), str(ans)[0]))]
    order = []
    for i in q:
        if i not in order:
            order.append(i)
    table = ", ".join(f"{symbols[i]}={vals[i]}" for i in order)
    text = f"{table} → {calc_text(rule, vals, q, ans)}"
    return hints, {lang: text for lang in LANGS}


# ---------------------------------------------------------------- 챕터 생성
def gen_chapter(ch, seen):
    out = []
    meta = {}
    for o in range(1, 7):
        c = cfg(ch, o)
        rng = random.Random(f"cipher-{ch}-{o}")
        res = None
        for _ in range(3000):
            r = try_build(rng, c)
            if r is None:
                continue
            vals, q, ans, exs = r
            symbols = rng.sample(EMOJI, c["n"])
            pairs = [[[symbols[i] for i in idxs], list(str(out_))] for idxs, out_ in exs]
            key = json.dumps([pairs, [symbols[i] for i in q]], ensure_ascii=False)
            if key in seen:
                continue
            seen.add(key)
            res = (vals, q, ans, exs, symbols, pairs)
            break
        assert res, f"cp{ch}_s{o} 생성 실패"
        vals, q, ans, exs, symbols, pairs = res
        rule = c["rule"]
        assert evaluate(rule, vals, q) == ans and 1 <= ans <= 9999
        hints, expl = make_texts(rule, symbols, vals, q, ans)
        pid = f"cp{ch}_s{o:02d}"
        out.append({
            "id": pid, "type": "cipher", "track": "cipher", "chapter": ch, "order": o,
            "pairs": pairs, "query": [symbols[i] for i in q],
            "answer": str(ans), "inputType": "keypad", "choices": None,
            "hints": hints, "explanation": expl,
        })
        meta[pid] = {"rule": rule, "values": {symbols[i]: vals[i] for i in range(c["n"])}}
    return out, meta


# ---------------------------------------------------------------- 테스트 벡터
def build_vectors(puzzles, meta):
    rng = random.Random("cipher-vectors")
    cases = []

    def add(rule, values, inp, note=""):
        idxs = [list(values).index(s) for s in inp]
        vl = [values[s] for s in values]
        r = evaluate(rule, vl, idxs)
        cases.append({"rule": rule, "values": values, "input": inp,
                      "output": None if r is None else str(r), "note": note})

    A, B, C, D, E = "🍎", "🍌", "🍇", "🍒", "🍋"
    v = {A: 7, B: 3, C: 9}
    add("map", v, [B, A, C], "map: 이어 붙임 3,7,9 = 379")
    add("rev", v, [A, B, C], "rev: 739 를 뒤집어 937")
    add("sum", v, [A, B, C], "sum: 7+3+9 = 19")
    add("diff", v, [C, A], "diff: 9-7 = 2")
    add("diff", v, [B, A], "diff: 3-7 <= 0 이면 무효(null)")
    add("diff", v, [C, B, B], "diff: 9-3-3 = 3")
    add("diff", {A: 4, B: 3, C: 1}, [A, B, C], "diff: 4-3-1 = 0 이면 무효(null)")
    add("diff", {A: 11, B: 2, C: 4}, [A, B, C], "diff: 값 범위 1~12, 11-2-4 = 5")
    add("prod", v, [A, B, C], "prod: 7*3*9 = 189")
    add("wsum", v, [A, B, C], "wsum: 1*7+2*3+3*9 = 40")
    add("cnt", v, [A, B, C], "cnt: 7+3+9+3 = 22")
    add("dsum", {A: 7, B: 3}, [A, B], "dsum: 73+37 = 110")
    add("dsum", {A: 4, B: 5, C: 9, D: 1}, [A, B, C, D], "dsum: 4591+1954 = 6545")
    add("map", v, [A], "map 1토큰: 7")
    add("sum", {A: 9, B: 8}, [A, A, B, B], "sum: 반복 기호 9+9+8+8 = 34")
    add("wsum", {A: 2, B: 6}, [A, A, B], "wsum 반복: 1*2+2*2+3*6 = 24")
    add("rev", {A: 1, B: 2, C: 3, D: 4}, [D, C, B, A], "rev: 4321 의 뒤집음 1234")
    for rule in RULES:
        for _ in range(6):
            n = rng.randint(3, 5)
            syms = rng.sample(EMOJI, n)
            vl = rng.sample(range(1, vmax(rule) + 1), n)
            values = {s: x for s, x in zip(syms, vl)}
            inp = [rng.choice(syms) for _ in range(rng.randint(1, 4))]
            add(rule, values, inp, "무작위")
    samples = []
    for pid in ["cp1_s01", "cp2_s04", "cp3_s05", "cp5_s03", "cp7_s02", "cp8_s06", "cp9_s04", "cp11_s02",
                "cp13_s03", "cp14_s05"]:
        p = next(x for x in puzzles if x["id"] == pid)
        s = {k: p[k] for k in ("id", "chapter", "order", "pairs", "query", "answer", "inputType", "choices")}
        s["rule"] = meta[pid]["rule"]
        s["values"] = meta[pid]["values"]
        samples.append(s)
    return {
        "format": ("암호 해독 테스트 벡터(명세 docs/cipher-scale-mode-spec.md). eval_cases[]: {rule, values:{기호:정수}, "
                   "input:[기호...], output: 결과 숫자 문자열 또는 null(규칙상 무효), note}. 규칙: map=값을 이어 붙임, "
                   "rev=이어 붙인 뒤 뒤집음, sum=합, diff=첫 값-나머지 합(0 이하면 null), prod=곱, wsum=Σ i*v(i) (i=1,2,..), "
                   "cnt=합+기호 개수, dsum=map 수 + 그 수를 뒤집은 수. 값 범위는 diff 1~12, 나머지 1~9. "
                   "samples[]: 실제 퍼즐 10개 요약(+ 검증용 rule, values; puzzles.json 에는 rule/values 가 없다) — 앱은 "
                   "pairs/query 를 파싱·표시하고 answer 를 그대로 쓰면 되며 규칙 평가 로직은 필요 없다. pairs[i]=[입력 토큰들, "
                   "출력 토큰들(한 자리씩, 이어 붙여 표시)]."),
        "eval_cases": cases,
        "samples": samples,
    }


# ---------------------------------------------------------------- main
def print_table(puzzles, meta):
    print("ch | rules | 기호수 | query길이 | 예시수 | 정답 자릿수")
    for ch in range(1, 15):
        ps = [p for p in puzzles if p["chapter"] == ch]
        rules = [meta[p["id"]]["rule"] for p in ps]
        syms = sorted({len(meta[p["id"]]["values"]) for p in ps})
        ql = sorted({len(p["query"]) for p in ps})
        ex = [len(p["pairs"]) for p in ps]
        dg = sorted({len(p["answer"]) for p in ps})
        print(f"{ch:2d} | {','.join(rules)} | {syms} | {ql} | {ex} | {dg}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=PUZZLES)
    ap.add_argument("--vectors", default=VECTORS)
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    seen = set()
    puzzles, meta = [], {}
    for ch in range(1, 15):
        ps, m = gen_chapter(ch, seen)
        puzzles.extend(ps)
        meta.update(m)
    print_table(puzzles, meta)
    if a.dry:
        return
    with open(a.puzzles, encoding="utf8") as f:
        data = json.load(f)
    data = [p for p in data if p.get("track") != "cipher"]
    data.extend(puzzles)
    with open(a.puzzles, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    with open(a.vectors, "w", encoding="utf8") as f:
        f.write(json.dumps(build_vectors(puzzles, meta), indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {len(puzzles)} cipher puzzles; total {len(data)}")


if __name__ == "__main__":
    main()
