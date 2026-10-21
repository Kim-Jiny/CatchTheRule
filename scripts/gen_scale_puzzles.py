#!/usr/bin/env python3
"""저울 추리(track:"scale") 퍼즐 생성기. 명세: docs/cipher-scale-mode-spec.md §1·§3·§5·§6

14챕터 x 6문제 = 84문제(id sc{ch}_s{01..06})를 시드 고정으로 생성해 puzzles.json 에 병합한다.
재실행 안전: track=="scale" 항목만 교체한다(기존 scale 블록 위치에 그대로 끼워 넣어 트랙별 연속 블록 유지,
나머지 퍼즐은 바이트 동일). 같은 실행에서 플랫폼 검증용 scripts/scale_test_vectors.json 도 만든다.

실행:  python3 scripts/gen_scale_puzzles.py [--puzzles PATH] [--dry]
  --puzzles PATH  병합 대상(기본 shared/content/puzzles.json)
  --dry           파일을 쓰지 않고 챕터별 지표 표만 출력

생성 방식(2026-10-21 난이도 상향): 챕터 1 은 '직접 저울'(상쇄 후 미지수 하나 + 숫자만)로 시작해도 되지만
챕터 2~14 는 시작 시 직접 저울이 하나도 없어야 한다(저울끼리 빼거나 한 기호를 다른 기호로 표현해 대입해야 한다).
미지수 수 == 저울 수(모든 저울이 꼭 필요 → 선형독립 → 해가 유일). query 는 저울들의 '정수 계수 선형 결합'으로 만든다
(손으로 풀 수 있음). 저울 순서는 시드 고정 셔플(풀이 순서가 아님).
"""
import argparse, json, os, random
from collections import Counter
from fractions import Fraction

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PUZZLES = os.path.join(ROOT, "shared", "content", "puzzles.json")
VECTORS = os.path.join(ROOT, "scripts", "scale_test_vectors.json")
LANGS = ["ko", "en", "ja", "zh", "es", "fr", "de"]
EMOJI = list("🍎🍌🍇🍒🍋🍓🥝🍑🌙⭐🔥💧🌿🔔🎈🐟🐱🐸🚗🎲")


# ---------------------------------------------------------------- 챕터 설정
class P:  # 챕터/순서별 프로필
    pass


def prof(ch, o):
    p = P()
    p.ch, p.o = ch, o
    # (미지수 == 저울 수)
    if ch == 1:
        nv = 2
    elif ch == 2:
        nv = 2 if o <= 2 else 3
    elif ch in (3, 4):
        nv = 3
    elif ch == 5:
        nv = 3 if o <= 2 else 4
    elif ch == 6:
        nv = 3 if o <= 1 else 4
    elif ch in (7, 8):
        nv = 4
    elif ch == 9:
        nv = 4 if o <= 3 else 5
    elif ch == 10:
        nv = 4 if o <= 1 else 5
    elif ch in (11, 12):
        nv = 5
    elif ch == 13:
        nv = 5 if o <= 3 else 6
    else:
        nv = 5 if o <= 1 else 6
    p.nv = nv
    p.kmin = 1 if ch == 1 else 2
    p.kmax = min(nv, 2 if ch <= 2 else 3 if ch <= 10 else 4)
    p.mm = 3 if ch <= 8 else 4
    p.pan, p.tot = (6, 10) if ch >= 13 else (5, 8)
    p.numcap = 20 if ch <= 8 else 40 if ch <= 12 else 60
    p.wlo = 1
    p.wmax = {1: 6, 2: 9, 3: 8, 4: 10}.get(ch, 12 if ch <= 8 else 20 if ch <= 12 else 30)
    if ch == 1 and o <= 2:
        p.wmax = 5
    p.cmax = 2 if ch <= 6 else 3
    p.qs = {1: (1, 2), 2: (1, 3), 3: (2, 3), 4: (2, 3), 5: (2, 4), 6: (2, 4), 7: (2, 4), 8: (2, 4),
            9: (3, 4), 10: (3, 4), 11: (3, 5), 12: (3, 5), 13: (4, 6), 14: (4, 6)}[ch]
    p.qrep = ch in (9, 10)
    p.qnum_p = 0.3 if ch >= 11 else 0.0
    p.direct = ch == 1           # True: 직접 저울 >= 1 필요 / False: 0 이어야 함
    p.mixed = 0                  # 양쪽 접시 모두에 기호가 있는 저울의 최소 개수
    p.common = 0                 # 같은 기호가 양쪽 접시에 있는(상쇄) 저울의 최소 개수
    p.mult = 0                   # 한 접시에 같은 기호 3개 이상이 있는 저울의 최소 개수
    p.numeral = 0                # 숫자 추가 있는 저울의 최소 개수
    p.pure = 0                   # 숫자 추 없이 기호만으로 균형인 저울(생성 시도)
    p.nboth = 0.0
    p.amin = 1
    if ch in (3, 4):
        p.mixed, p.pure = 1, (1 if ch == 4 else 0)
    elif ch in (5, 6):
        p.mixed, p.pure, p.amin = 2, 1, 10
    elif ch in (7, 8):
        p.mixed, p.common, p.pure, p.amin = 2, 2, 1, 10
    elif ch in (9, 10):
        p.mixed, p.mult, p.amin = 1, 1, 20
        p.common = 1 if ch == 10 else 0
    elif ch in (11, 12):
        p.mixed, p.common, p.numeral, p.nboth, p.amin = 1, 1, 3, 0.3, 20
    elif ch >= 13:
        p.mixed, p.common, p.mult, p.numeral, p.nboth, p.amin = 2, 1, 1, 3, 0.3, 40
    p.a100 = ch >= 13 and o >= 4  # 정답 100 이상 (챕터당 3문제)
    if p.a100:
        p.amin = 100
    return p


# ---------------------------------------------------------------- 도구
def net_vec(L, R, nv):
    return [L.get(i, 0) - R.get(i, 0) for i in range(nv)]


def rank_of(rows):
    basis = []
    for r in rows:
        r = [Fraction(x) for x in r]
        for pv, b in basis:
            if r[pv] != 0:
                f = r[pv]
                r = [x - f * y for x, y in zip(r, b)]
        pv = next((i for i, x in enumerate(r) if x != 0), None)
        if pv is not None:
            basis.append((pv, [x / r[pv] for x in r]))
    return len(basis)


def plate_tokens(rng, counter, numeral):
    toks = []
    vs = [v for v in counter if counter[v] > 0]
    rng.shuffle(vs)
    for v in vs:
        toks += [v] * counter[v]
    if numeral:
        if rng.random() < 0.25:
            toks.insert(0, str(numeral))
        else:
            toks.append(str(numeral))
    return toks


def sample_scale(rng, W, pf, flags):
    """저울 하나(L, R: Counter(변수->개수), a, b: 양쪽 숫자 추). 실패하면 None."""
    nv = pf.nv
    mixed, common, mult, pure, numeral = (f in flags for f in ("mixed", "common", "mult", "pure", "numeral"))
    for _ in range(700 if pure else 300):
        k = rng.randint(pf.kmin, pf.kmax)
        vs = rng.sample(range(nv), k)
        sides = [rng.random() < 0.5 for _ in vs]
        if (mixed or common) and k >= 2 and len(set(sides)) == 1:
            sides[0] = not sides[0]
        L, R = Counter(), Counter()
        for v, s in zip(vs, sides):
            cnt = rng.choice([1, 1, 1, 2, 2, 3, 4][:3 + pf.mm])
            cnt = min(cnt, pf.mm)
            (L if s else R)[v] += cnt
        if mult:
            v = rng.choice(vs)
            side = L if v in L else R
            side[v] = rng.randint(3, max(3, pf.mm))
        if common:
            x = rng.randrange(nv)
            n = rng.randint(1, 2)
            L[x] += n
            R[x] += n
        if mixed and not (L and R):
            continue
        d = sum(W[v] * n for v, n in L.items()) - sum(W[v] * n for v, n in R.items())
        a, b = (-d, 0) if d < 0 else (0, d)
        if pure and d != 0:
            continue
        if numeral and d == 0:
            continue
        if not pure and pf.nboth and rng.random() < pf.nboth:
            e = rng.randint(1, 8)
            a += e
            b += e
        if a > pf.numcap or b > pf.numcap:
            continue
        nl = sum(L.values()) + (1 if a else 0)
        nr = sum(R.values()) + (1 if b else 0)
        if not (1 <= nl <= pf.pan and 1 <= nr <= pf.pan and nl + nr <= pf.tot and nl + nr >= 2):
            continue
        nz = sum(1 for x in net_vec(L, R, nv) if x)
        if nz < (1 if pf.ch == 1 else 2):
            continue
        return L, R, a, b
    return None


def n_direct(rows):
    return sum(1 for r in rows if sum(1 for x in r if x) == 1)


def assign_flags(rng, pf):
    ns = pf.nv
    fl = [set() for _ in range(ns)]
    for name, cnt in (("common", pf.common), ("mult", pf.mult), ("numeral", pf.numeral), ("mixed", pf.mixed),
                      ("pure", pf.pure)):
        idx = list(range(ns))
        rng.shuffle(idx)
        placed = 0
        for i in idx:
            if placed >= cnt:
                break
            if name == "pure" and ({"numeral", "common"} & fl[i]):
                continue
            if name == "numeral" and "pure" in fl[i]:
                continue
            fl[i].add(name)
            placed += 1
    return fl


def pick_weights(rng, pf):
    lo = pf.wlo
    if pf.a100:
        lo = max(lo, 9)
    elif pf.amin >= 40:
        lo = max(lo, 4)
    elif pf.amin >= 20:
        lo = max(lo, 2)
    return rng.sample(range(lo, pf.wmax + 1), pf.nv)


def one_puzzle(ch, o, seen, used_answers):
    rng = random.Random(f"sc{ch}_s{o}")
    pf = prof(ch, o)
    nv = pf.nv
    for attempt in range(200000):
        W = pick_weights(rng, pf)
        flags = assign_flags(rng, pf)
        scs = []
        for fl in flags:
            r = sample_scale(rng, W, pf, fl)
            if r is None:
                break
            scs.append(r)
        if len(scs) != nv:
            continue
        rows = [net_vec(L, R, nv) for L, R, _, _ in scs]
        dc = n_direct(rows)
        if pf.direct and dc != 1:
            continue
        if not pf.direct and dc:
            continue
        if rank_of(rows) < nv:
            continue
        # 요구 구조 개수
        nmixed = sum(1 for L, R, _, _ in scs if L and R)
        ncommon = sum(1 for L, R, _, _ in scs if set(L) & set(R))
        nmult = sum(1 for L, R, _, _ in scs if max(list(L.values()) + list(R.values())) >= 3)
        nnum = sum(1 for _, _, a, b in scs if a or b)
        if nmixed < pf.mixed or ncommon < pf.common or nmult < pf.mult or nnum < pf.numeral:
            continue
        if ch <= 6 and ncommon and ch >= 3 and False:
            continue
        # 같은 저울(정규화 키) 중복 방지
        keys = set()
        bad = False
        for (L, R, a, b), r in zip(scs, rows):
            k = tuple(r) + (a - b,)
            f = next((x for x in k if x), 0)
            if f < 0:
                k = tuple(-x for x in k)
            if k in keys:
                bad = True
            keys.add(k)
        if bad:
            continue
        # query = sum c_i * row_i  (정수 계수, 모두 0 이 아님 → 모든 저울이 필요)
        qlo, qhi = pf.qs
        found = None
        for _ in range(250):
            c = [rng.choice([x for x in range(-pf.cmax, pf.cmax + 1) if x]) for _ in range(nv)]
            q = [sum(c[i] * rows[i][j] for i in range(nv)) for j in range(nv)]
            if min(q) < 0:
                continue
            size = sum(q)
            if not (qlo <= size <= qhi):
                continue
            if pf.qrep and max(q) < 2:
                continue
            if pf.ch >= 2 and max(abs(x) for x in c) == 1 and nv >= 4 and rng.random() < 0.5:
                continue  # 너무 단순한 합/차만 쓰는 조합은 가끔 거름
            qs = sum(q[j] * W[j] for j in range(nv))
            qnum = 0
            if pf.qnum_p and rng.random() < pf.qnum_p and size < pf.pan:
                qnum = rng.randint(1, 15)
            ans = qs + qnum
            if not (max(1, pf.amin) <= ans <= 999):
                continue
            found = (q, c, qnum, ans)
            break
        if not found:
            continue
        q, c, qnum, ans = found
        # 챕터 4 이하: 정답 중복 회피
        if ans in used_answers and attempt < 100000:
            continue
        # 표시용 토큰
        perm = list(range(nv))
        syms = rng.sample(EMOJI, nv)
        scales = []
        for L, R, a, b in scs:
            scales.append([[syms[t] if isinstance(t, int) else t for t in plate_tokens(rng, L, a)],
                           [syms[t] if isinstance(t, int) else t for t in plate_tokens(rng, R, b)]])
        qt = []
        for j in range(nv):
            qt += [syms[j]] * q[j]
        rng.shuffle(qt)
        if qnum:
            qt.insert(rng.randint(0, len(qt)), str(qnum))
        if len(qt) > pf.pan:
            continue
        key = json.dumps([sorted(map(json.dumps, scales)), qt])
        if key in seen:
            continue
        # 저울 순서 셔플(시드 고정). 챕터 1 은 직접 저울이 항상 맨 앞이 되지 않도록 한다.
        srng = random.Random(f"sh{ch}_{o}")
        order = list(range(nv))
        srng.shuffle(order)
        if ch == 1:
            dpos = next(i for i, r in enumerate(rows) if sum(1 for x in r if x) == 1)
            want_first = (o % 2 == 1)   # 홀수 order: 직접 저울이 먼저, 짝수: 나중
            order = [dpos] + [i for i in range(nv) if i != dpos] if want_first else \
                [i for i in range(nv) if i != dpos] + [dpos]
        scales = [scales[i] for i in order]
        # 힌트 기호: query 만으로 정답이 드러나지 않도록(단일 기호 query 에서 그 기호는 제외), 무게 != 정답
        cand = [i for i in range(nv) if W[i] != ans and not (len(qt) == 1 and q[i])]
        notq = [i for i in cand if not q[i]]
        hv = rng.choice(notq or cand)
        return dict(syms=syms, scales=scales, query=qt, qvars=[j for j in range(nv) for _ in range(q[j])],
                    qnum=qnum, answer=ans, hint_var=hv, hint_val=W[hv], sol=tuple(W), unique=True,
                    forced=list(range(nv)), coef=c, key=key, ndirect=dc)
    raise AssertionError(f"sc{ch}_s{o} 생성 실패")


# ---------------------------------------------------------------- 텍스트
def iyo(num):
    return "이에요" if str(num)[-1] in "013678" else "예요"


H1 = {
    1: {
        "ko": "숫자 추와 비교해서 기호 하나의 무게부터 알아내세요.",
        "en": "Compare with the number weights and find the weight of one symbol first.",
        "ja": "数字のおもりと見比べて、まず記号ひとつの重さを求めましょう。",
        "zh": "先和数字砝码比一比，求出一个符号的重量。",
        "es": "Compara con las pesas numéricas y averigua primero el peso de un símbolo.",
        "fr": "Compare avec les poids chiffrés et trouve d'abord le poids d'un symbole.",
        "de": "Vergleiche mit den Zahlengewichten und finde zuerst das Gewicht eines Symbols.",
    },
    2: {  # 두 저울 비교
        "ko": "저울 두 개를 나란히 놓고 비교해 보세요. 한쪽에서 다른 쪽을 빼면 기호가 줄어요.",
        "en": "Put two scales side by side and compare them. Taking one away from the other leaves fewer symbols.",
        "ja": "天秤を2つ並べて見比べましょう。片方からもう片方を引くと、記号が減ります。",
        "zh": "把两台天平放在一起比较。用一台减去另一台，符号就变少了。",
        "es": "Compara dos balanzas entre sí. Si restas una de otra, quedan menos símbolos.",
        "fr": "Compare deux balances côte à côte. En retirant l'une de l'autre, il reste moins de symboles.",
        "de": "Vergleiche zwei Waagen miteinander. Ziehst du die eine von der anderen ab, bleiben weniger Symbole übrig.",
    },
    3: {  # 한 기호를 다른 기호로 바꿔 쓰기
        "ko": "한 기호를 다른 기호로 바꿔 써 보세요. 그걸 다른 저울에 넣으면 기호가 하나 줄어요.",
        "en": "Rewrite one symbol in terms of another. Put that into a different scale and one symbol disappears.",
        "ja": "ある記号を別の記号で言い換えてみましょう。それを別の天秤に入れると、記号がひとつ減ります。",
        "zh": "试着用一个符号来表示另一个符号，再代入另一台天平，就少了一个符号。",
        "es": "Expresa un símbolo con otro. Al sustituirlo en otra balanza, desaparece un símbolo.",
        "fr": "Exprime un symbole à l'aide d'un autre. En le remplaçant dans une autre balance, un symbole disparaît.",
        "de": "Drücke ein Symbol durch ein anderes aus. Setzt du das in eine andere Waage ein, verschwindet ein Symbol.",
    },
    4: {  # 공통 부분 빼기
        "ko": "두 저울에 똑같이 들어 있는 부분을 빼 보세요. 남는 것만 비교하면 훨씬 간단해져요.",
        "en": "Subtract the part that two scales share. Comparing only what's left is much simpler.",
        "ja": "2つの天秤に共通して入っている部分を引いてみましょう。残りだけを比べるとぐっと簡単になります。",
        "zh": "把两台天平里相同的部分减掉，只比较剩下的，就简单多了。",
        "es": "Resta la parte que comparten dos balanzas. Comparar solo lo que queda es mucho más fácil.",
        "fr": "Soustrais la partie commune à deux balances. Comparer seulement le reste est bien plus simple.",
        "de": "Ziehe den Teil ab, den zwei Waagen gemeinsam haben. Nur den Rest zu vergleichen ist viel einfacher.",
    },
    5: {  # 양쪽 공통 기호 상쇄
        "ko": "양쪽 접시에 똑같이 있는 기호는 먼저 지우세요. 그다음 저울끼리 비교해 한 기호의 무게를 찾아요.",
        "en": "Cancel the symbols that sit on both pans first. Then compare the scales to pin down one symbol's weight.",
        "ja": "両方の皿に同じ記号があれば、まず消しましょう。そのあと天秤どうしを比べて、記号ひとつの重さを見つけます。",
        "zh": "先把两个盘里相同的符号消去，再把几台天平互相比较，找出一个符号的重量。",
        "es": "Primero elimina los símbolos que están en ambos platos. Después compara las balanzas para fijar el peso de un símbolo.",
        "fr": "Élimine d'abord les symboles présents sur les deux plateaux. Compare ensuite les balances pour fixer le poids d'un symbole.",
        "de": "Streiche zuerst die Symbole, die auf beiden Seiten liegen. Vergleiche dann die Waagen, um das Gewicht eines Symbols festzulegen.",
    },
    6: {  # 배수
        "ko": "같은 기호가 여러 개면 한 덩어리(배수)로 묶어 보세요. 저울을 몇 배 해서 더하거나 빼면 기호가 사라져요.",
        "en": "Treat repeated symbols as one multiple. Scale a scale up, then add or subtract, and a symbol vanishes.",
        "ja": "同じ記号が複数あるときはひとまとまり(倍数)と考えましょう。天秤を何倍かして足したり引いたりすると、記号が消えます。",
        "zh": "同一个符号有好几个时，把它们看成一个整体(倍数)。把天平放大几倍后相加或相减，符号就消掉了。",
        "es": "Piensa en los símbolos repetidos como un múltiplo. Multiplica una balanza y suma o resta: un símbolo desaparece.",
        "fr": "Considère les symboles répétés comme un multiple. Multiplie une balance, puis additionne ou soustrais : un symbole disparaît.",
        "de": "Sieh wiederholte Symbole als ein Vielfaches. Vervielfache eine Waage und addiere oder subtrahiere, dann verschwindet ein Symbol.",
    },
    7: {  # 숫자 추 + 큰 무게
        "ko": "숫자 추도 식의 일부로 정리하세요. 무게가 커도 기호끼리 먼저 정리하고, 숫자는 마지막에 계산해요.",
        "en": "Treat the number weights as part of the equation. Even with big weights, tidy up the symbols first and do the numbers last.",
        "ja": "数字のおもりも式の一部として整理しましょう。重さが大きくても、まず記号どうしを整理して、数字は最後に計算します。",
        "zh": "把数字砝码也当作算式的一部分。重量再大，也先整理符号，最后再算数字。",
        "es": "Trata las pesas numéricas como parte de la ecuación. Aunque los pesos sean grandes, ordena primero los símbolos y deja los números para el final.",
        "fr": "Traite les poids chiffrés comme une partie de l'équation. Même avec de gros poids, range d'abord les symboles et calcule les nombres à la fin.",
        "de": "Behandle die Zahlengewichte als Teil der Gleichung. Auch bei großen Gewichten ordnest du zuerst die Symbole und rechnest die Zahlen zuletzt.",
    },
    8: {  # 모든 기법 혼합
        "ko": "지금까지의 방법을 모두 섞어 쓰세요. 상쇄, 대입, 배수를 번갈아 쓰며 기호를 하나씩 줄여 가요.",
        "en": "Mix every method you know. Alternate cancelling, substituting and multiples to remove symbols one at a time.",
        "ja": "これまでの方法をすべて組み合わせましょう。相殺、代入、倍数を交互に使って、記号をひとつずつ減らしていきます。",
        "zh": "把学过的方法都用上。交替使用消去、代入和倍数，一个一个地减少符号。",
        "es": "Combina todos los métodos. Alterna cancelar, sustituir y los múltiplos para ir quitando símbolos uno a uno.",
        "fr": "Combine toutes les méthodes. Alterne élimination, substitution et multiples pour retirer les symboles un par un.",
        "de": "Kombiniere alle Methoden. Wechsle zwischen Wegstreichen, Einsetzen und Vielfachen, um die Symbole nacheinander loszuwerden.",
    },
}


def h1_key(ch):
    return {1: 1, 2: 2, 3: 3, 4: 3, 5: 4, 6: 4, 7: 5, 8: 5, 9: 6, 10: 6, 11: 7, 12: 7, 13: 8, 14: 8}[ch]


def hint2(sym, n):
    return {
        "ko": f"{sym} 하나의 무게는 {n}{iyo(n)}.",
        "en": f"One {sym} weighs {n}.",
        "ja": f"{sym}ひとつの重さは{n}です。",
        "zh": f"一个{sym}的重量是{n}。",
        "es": f"Un {sym} pesa {n}.",
        "fr": f"Un {sym} pèse {n}.",
        "de": f"Ein {sym} wiegt {n}.",
    }


def bound_of(ans):
    return (ans // 5 + 1) * 5


def hint3(ans):
    if ans < 10:
        B = bound_of(ans)
        return {
            "ko": f"답은 {B}보다 작아요.",
            "en": f"The answer is less than {B}.",
            "ja": f"答えは{B}より小さいです。",
            "zh": f"答案小于{B}。",
            "es": f"La respuesta es menor que {B}.",
            "fr": f"La réponse est inférieure à {B}.",
            "de": f"Die Antwort ist kleiner als {B}.",
        }
    d = len(str(ans))
    return {
        "ko": f"답은 {d}자리 수예요.",
        "en": f"The answer has {d} digits.",
        "ja": f"答えは{d}桁の数です。",
        "zh": f"答案是{d}位数。",
        "es": f"La respuesta tiene {d} cifras.",
        "fr": f"La réponse a {d} chiffres.",
        "de": f"Die Antwort hat {d} Stellen.",
    }


def make_texts(ch, r):
    syms = r["syms"]
    h2 = hint2(syms[r["hint_var"]], r["hint_val"])
    h3 = hint3(r["answer"])
    hints = {l: [H1[h1_key(ch)][l], h2[l], h3[l]] for l in LANGS}
    # 설명(언어 중립): 확정된 무게 + 계산
    table = ", ".join(f"{syms[i]}={r['sol'][i]}" for i in r["forced"])
    ex = "+".join(syms[v] for v in r["qvars"])
    nums = "+".join(str(r["sol"][v]) for v in r["qvars"])
    if r["qnum"]:
        ex += f"+{r['qnum']}"
        nums += f"+{r['qnum']}"
    expl = f"{table} → {ex} = {nums} = {r['answer']}"
    return hints, {l: expl for l in LANGS}


# ---------------------------------------------------------------- 챕터 생성
def gen_chapter(ch, seen):
    out = []
    used = set()
    for o in range(1, 7):
        r = one_puzzle(ch, o, seen, used)
        seen.add(r["key"])
        used.add(r["answer"])
        hints, expl = make_texts(ch, r)
        out.append({
            "id": f"sc{ch}_s{o:02d}", "type": "scale", "track": "scale", "chapter": ch, "order": o,
            "scales": r["scales"], "query": r["query"],
            "answer": str(r["answer"]), "inputType": "keypad", "choices": None,
            "hints": hints, "explanation": expl,
            "_meta": r,
        })
    return out


# ---------------------------------------------------------------- 테스트 벡터
def total(plate, w):
    return sum(w[t] if t in w else int(t) for t in plate)


def build_vectors(puzzles):
    cases = []

    def add(w, left, right, note):
        lt, rt = total(left, w), total(right, w)
        cases.append({"weights": w, "left": left, "right": right, "leftTotal": lt, "rightTotal": rt,
                      "balanced": lt == rt, "note": note})

    add({"🍎": 3}, ["🍎", "🍎"], ["6"], "기호 2개 = 숫자 추 6")
    add({"🍎": 3, "🍌": 4}, ["🍎", "🍌"], ["7"], "서로 다른 기호 합 = 7")
    add({"🍎": 3, "🍌": 4}, ["🍎", "🍎", "🍎", "🍌"], ["13"], "배수: 3x3+4 = 13")
    add({"🍎": 3, "🍌": 4}, ["🍎", "🍌", "5"], ["🍌", "🍌", "🍌"], "숫자 추 섞임 + 양쪽 기호: 3+4+5 = 12 = 4x3")
    add({"🍎": 2, "🍌": 5, "🍇": 7}, ["🍎", "🍌"], ["🍇"], "2+5 = 7")
    add({"🍎": 2, "🍌": 5, "🍇": 7}, ["🍎", "🍇"], ["🍌", "🍌"], "2+7 = 9 != 10 (불균형)")
    add({"🍎": 2, "🍌": 5}, ["🍎", "🍌"], ["🍎", "6"], "공통 기호 상쇄 전: 2+5 != 2+6 (불균형)")
    add({"🍎": 2, "🍌": 5}, ["🍎", "🍌"], ["🍎", "5"], "공통 기호 상쇄 후 🍌=5 (균형)")
    add({"🍎": 4}, ["🍎"], ["4"], "기호 1개 = 숫자 추")
    add({"🍎": 4}, ["🍎"], ["5"], "불균형")
    add({"🍎": 12, "🍌": 20}, ["🍎", "🍎", "🍎"], ["🍌", "16"], "큰 무게: 36 = 36")
    add({"🌙": 1, "⭐": 9}, ["⭐", "🌙", "🌙"], ["11"], "9+1+1 = 11")
    add({"🍎": 3, "🍌": 4, "🍇": 5}, ["🍎", "🍌", "🍇"], ["12"], "세 기호 합")
    add({"🍎": 3, "🍌": 4, "🍇": 5}, ["🍎", "🍌", "🍇"], ["13"], "불균형")
    add({"🍎": 5, "🍌": 2}, ["🍎", "20"], ["🍌", "🍌", "🍌", "🍌", "🍌", "15"], "숫자 추 20 포함: 25 = 10+15")
    add({"🍎": 28, "🍌": 17}, ["🍎", "🍌", "🍌", "40"], ["🍎", "🍎", "🍌", "23"], "큰 무게(28,17): 102 vs 96 (불균형)")
    add({"🍎": 28, "🍌": 17}, ["🍎", "🍌", "🍌", "40"], ["🍎", "🍎", "🍌", "29"], "큰 무게(28,17): 102 = 102")
    # 실제 퍼즐 몇 개(풀이 하나로 균형인지)
    for ch in (1, 3, 5, 7, 9, 11, 13, 14):
        p = puzzles[(ch - 1) * 6 + 2]
        m = p["_meta"]
        w = {m["syms"][i]: m["sol"][i] for i in range(len(m["syms"]))}
        for s in p["scales"][:2]:
            add(w, s[0], s[1], f"{p['id']} 실제 퍼즐 저울 (한 가지 풀이에서 균형)")
            assert cases[-1]["balanced"]
    samples = []
    for pid in ["sc1_s01", "sc2_s05", "sc3_s02", "sc5_s04", "sc7_s03", "sc9_s05",
                "sc11_s02", "sc12_s06", "sc13_s02", "sc14_s04"]:
        p = next(x for x in puzzles if x["id"] == pid)
        samples.append({k: p[k] for k in ("id", "chapter", "order", "scales", "query", "answer",
                                           "inputType", "choices")})
    return {
        "format": ("저울 추리 테스트 벡터(명세 docs/cipher-scale-mode-spec.md §3, §5). eval_cases[]: "
                   "{weights:{기호:무게}, left:[토큰], right:[토큰], leftTotal, rightTotal, balanced, note}. "
                   "접시 합 = 기호 토큰은 weights[기호], 숫자 토큰(숫자 문자열)은 그 값. balanced = 양쪽 합이 같음. "
                   "samples[]: 실제 퍼즐 10개 요약(id, chapter, order, scales, query, answer, inputType, choices) — "
                   "앱이 puzzles.json 디코딩 후 파싱/표시를 확인하는 용도(규칙 평가 로직은 앱에 필요 없다, 정답은 answer)."),
        "eval_cases": cases,
        "samples": samples,
    }


# ---------------------------------------------------------------- main
def print_table(puzzles):
    print("ch | 미지수 | 저울 수 | query 토큰 | 직접 저울 수 | 정답")
    for ch in range(1, 15):
        ps = [p for p in puzzles if p["chapter"] == ch]
        print(f"{ch:2d} | {[len(p['_meta']['syms']) for p in ps]} | {[len(p['scales']) for p in ps]} | "
              f"{[len(p['query']) for p in ps]} | {[p['_meta']['ndirect'] for p in ps]} | "
              f"{[p['answer'] for p in ps]}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puzzles", default=PUZZLES)
    ap.add_argument("--dry", action="store_true")
    args = ap.parse_args()
    seen = set()
    puzzles = []
    for ch in range(1, 15):
        puzzles.extend(gen_chapter(ch, seen))
    print_table(puzzles)
    if args.dry:
        return
    with open(args.puzzles, encoding="utf8") as f:
        data = json.load(f)
    pos = next((i for i, p in enumerate(data) if p.get("track") == "scale"), len(data))
    rest = [p for p in data if p.get("track") != "scale"]
    new = [{k: v for k, v in p.items() if k != "_meta"} for p in puzzles]
    data = rest[:pos] + new + rest[pos:]
    with open(args.puzzles, "w", encoding="utf8") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=False))
    with open(VECTORS, "w", encoding="utf8") as f:
        f.write(json.dumps(build_vectors(puzzles), indent=2, ensure_ascii=False) + "\n")
    print(f"wrote {len(puzzles)} scale puzzles; total {len(data)}")


if __name__ == "__main__":
    main()
