# 격자 채우기(fill) · 경로 추적(path) 모드 명세

생성기(Python) / iOS(Swift) / Android(Kotlin)가 **같은 규칙**을 쓰도록 정의한 단일 명세. 홈 허브·해금 게이트·캠페인 흐름·타임어택 믹스·진행도 저장은 기존 모드와 **완전히 같은 방식**으로 붙인다. 변경 지점 체크리스트 = `git show 7685fa5 --stat`(cipher/scale 추가 커밋)의 파일 목록. 참고 명세: `docs/cipher-scale-mode-spec.md`, `docs/match-mode-spec.md`(키패드가 아닌 입력 + `확인` 버튼 패턴).

공통:
- 둘 다 **언어 중립 퍼즐**(숫자·화살표·격자만). 번역이 필요한 건 모드명/프롬프트/챕터명/UI 문구와 `hints`/`explanation`(7개 언어: ko,en,ja,zh,es,fr,de)뿐.
- 각 모드 14챕터 × 6문제 = 84문제. id `fxN_sMM`(fill) / `pcN_sMM`(path). 챕터 안 order 1→6 점점 어려워짐.
- 입력은 키패드가 아니라 **격자 탭 + `확인` 버튼**(match/target 처럼). `inputType:"choices"`, `choices:null`. 정답 판정은 기존 공통 경로 `submit(value: puzzle.answer)`(정답) / 오답은 answer 와 절대 같지 않은 문자열(예 `"x"+현재상태`).
- 모드 키·색·아이콘 (이미 쓰는 색과 겹치지 않는지 `Theme` 를 보고 필요하면 인접 색으로 조정하되 구현 양쪽이 같은 색을 쓸 것):
  - fill = `fill`, 색 **#A3E635(라임)**, iOS 아이콘 `square.grid.3x3.fill`, Android `Icons.Filled.GridOn`
  - path = `path`, 색 **#38BDF8(스카이)**, iOS 아이콘 `point.topleft.down.curvedto.point.bottomright.up`(없으면 `arrow.triangle.turn.up.right.diamond.fill`), Android `Icons.Filled.Route`(없으면 `Icons.Filled.Timeline`)
  - `PuzzleStore.trackOrder` 는 `[..., "cipher", "scale", "fill", "path"]` (9→11개, 홀수 → 마지막 카드 가로 꽉; 기존 로직).
- l10n 키(**`scripts/gen_l10n.py` 의 T 테이블에만 추가** — 리소스 직접 편집 금지, 메모리 `catchtherule-l10n-gotcha`): `mode_fill`, `fill_prompt`, `fill_chapter_1..14`, `fill_check`(확인), `fill_clear`(지우기), `fill_invalid`(규칙에 어긋나는 칸이 있어요), `mode_path`, `path_prompt`, `path_chapter_1..14`, `path_check`. 한 칸 입력 UI 는 숫자 키패드(1..N)를 직접 그리므로 별도 문자열 불필요. 힌트/해설 UI 는 기존 재사용. 모드 카드 설명이 있으면 기존 모드 패턴대로 키 추가.
- 타임어택 믹스: match/target 처럼 키패드가 아닌 입력 모드가 믹스에서 어떻게 다뤄지는지 `TimeAttack*` 를 보고 **같은 방식으로** 처리(제외라면 제외, 포함이라면 해당 화면을 그대로 렌더).

## 1. fill — 격자 채우기

### 규칙
N×N 격자(N=4,5,6)에 숫자 1..N 을 채운다. **모든 규칙을 만족하는 해가 정확히 하나**. 규칙 집합(`rules`, 문자열 배열, 조합 가능):
- `row` : 각 행에 1..N 이 한 번씩 (모든 퍼즐에 항상 포함)
- `col` : 각 열에 1..N 이 한 번씩 (항상 포함)
- `box` : 상자 구획에도 1..N 이 한 번씩. N=4 → 2×2 상자, N=6 → 2행×3열 상자. N=5 는 `box` 금지.
- `diag` : 두 대각선(주·반대)에도 1..N 이 한 번씩 (N=4,5 에서만)
- `lt` 목록이 있으면 부등호 제약: 항목 `[r1,c1,r2,c2]` = 칸(r1,c1) < 칸(r2,c2) (두 칸은 상하좌우 인접, 0-기반 행/열)
- `cages` 목록이 있으면 합 제약: 항목 `[합, r1,c1, r2,c2, …]` = 그 칸들(2~3칸, 서로 인접하게 이어짐)의 합이 `합`. 한 케이지 안에서 숫자 중복 금지(생성기가 보장).

### 스키마
```
{"id":"fx5_s03","type":"fill","track":"fill","chapter":5,"order":3,
 "board":[[1,0,0,4],[0,0,3,0],[0,4,0,0],[3,0,0,2]],   // 0 = 빈칸, 나머지는 고정 칸. board.count == N
 "rules":["row","col","box"],
 "lt":[[0,0,0,1]],              // 선택(없으면 생략 또는 null)
 "cages":[[5,1,1,1,2]],         // 선택
 "answer":"23412143",           // 빈칸들의 정답 숫자를 행 우선(row-major) 순서로 이어 붙인 문자열
 "inputType":"choices","choices":null,
 "hints":{ko:[3개],...7개 언어}, "explanation":{ko:"...",...7개 언어}}
```
- 앱 모델(Puzzle): `board: [[Int]]?`, `rules: [String]?`, `lt: [[Int]]?`, `cages: [[Int]]?`. `isFill = board 비어있지 않음`. (기존 `grid` 필드는 수열 행렬용이라 재사용 금지.)
- 정답 문자열 길이 = 빈칸 수(최대 ≈ 24).

### 유일성·품질 (생성기+검증기 필수)
- 검증기는 백트래킹 솔버로 **해의 개수 == 1** 임을 확인하고 `answer` 가 그 해와 일치하는지 본다. 고정 칸은 해와 일치, 고정 칸끼리 규칙 위반 없음.
- **고정 칸 최소화**: 챕터가 요구하는 빈칸 수 범위 안에서, 고정 칸을 하나라도 지우면 유일성이 깨지는 정도(또는 근접)로 만든다. 지나치게 쉬운(= 빈칸 1개뿐) 문제 금지.
- **난이도 등급**(생성기·검증기가 각자 독립 구현으로 계산): **S** = naked/hidden single(+lt 경계, 케이지 합 가지치기)만으로 풀림 / **P** = S 에 naked·hidden pair·triple, locked candidates(상자-줄 교차 소거)를 더해야 풀림 / **T** = 그래도 안 풀려 가정 후 모순 탐색(trial)이 필요. ch1~6 은 전부 S. 최소 등급(검증기가 강제): ch7 o4~5 P·o6 T, ch8 o3~4 P·o5~6 T, ch9 o2~4 P·o5~6 T, ch11 o2~3 P·o4~6 T, ch12·13·14 는 o1~3 P(ch12 o4~6, ch13·14 o4~6 은 T). ch10(4x4 대각선)은 빈칸 10~13 의 모든 유일해 문제가 S 라 등급을 올릴 수 없어 S 유지(전수 확인).
- 등급 목표가 있는 칸은 빈칸을 챕터 범위 최대로 밀고(그 칸은 더 못 지우는 극소거나 범위 최대) lt/cages 개수는 범위 아래쪽으로 줄여 찾는다. 빈칸 수 비감소·제약 개수 비증가 규칙은 유지.
- 챕터 내 동일 board 중복 금지, 시드 고정, `shared/content/puzzles.json` 에 **해당 track 항목만 교체하는 멱등 병합**.

### 난이도 사다리 (빈칸 수 = 0 의 개수)
| 챕터 | N | rules | 추가 제약 | 빈칸 |
|---|---|---|---|---|
| 1 | 4 | row,col | — | 5~7 |
| 2 | 4 | row,col,box | — | 7~9 |
| 3 | 4 | row,col,box | — | 9~11 |
| 4 | 4 | row,col | lt 4~6개 | 8~10 |
| 5 | 5 | row,col | — | 10~13 |
| 6 | 4 | row,col | cages 3~4개 | 10~12 |
| 7 | 5 | row,col | lt 5~8개 | 12~15 |
| 8 | 6 | row,col,box | — | 14~18 |
| 9 | 5 | row,col | cages 4~6개 | 14~17 |
| 10 | 4 | row,col,box,diag | — | 10~12 |
| 11 | 6 | row,col,box | lt 4~6개 | 17~21 |
| 12 | 5 | row,col,diag | — | 14~17 |
| 13 | 6 | row,col,box | cages 5~7개 | 19~23 |
| 14 | 6 | row,col,box | lt+cages 혼합 | 21~25 |
(ch 안에서 order 1→6 으로 빈칸 수를 범위의 아래→위로 늘리고 제약 개수를 줄여 가며 어렵게.)

### 힌트 3개
① 챕터별 요령(ch1~3 "한 줄에 같은 숫자가 두 번 나오면 안 돼요" / box 챕터 "굵은 상자 안에도 한 번씩" / lt 챕터 "부등호 ‹ 는 작은 쪽을 가리켜요" / cage 챕터 "묶음 안의 합" / diag "대각선도") ② "R행 C열은 N이에요"(빈칸 중 하나, 가장 풀기 쉬운 곳 — 정답을 일부 공개) ③ 같은 형태로 두 번째 빈칸. `explanation`: 완성된 격자의 행을 공백으로 이은 언어 중립 문자열(예 `1234 3412 2143 4321`).

### 화면/조작
- 프롬프트 `fill_prompt`, 그 아래 격자 카드(`FillBoardView`): N×N 정사각 칸, 화면 폭에 맞춰 크기 조정, 상자 경계는 굵은 선, 고정 칸은 흐린 배경+밝은 숫자, 빈칸은 비어 있고 탭하면 **선택(모드색 테두리+글로우)**, 입력한 값은 모드색 숫자. `lt` 는 두 칸 사이 경계에 작은 `‹`/`›`/`˄`/`˅` 기호(작은 쪽 방향), `cages` 는 해당 칸들 둘레에 점선 윤곽 + 첫 칸(좌상단) 모서리에 합 숫자. `diag` 는 두 대각선 칸에 옅은 색 배경.
- 격자 아래 **숫자 패드 1..N** + `fill_clear`(선택 칸 지우기). 숫자 탭 → 선택 칸에 입력(덮어쓰기 가능), 입력 후 다음 빈칸으로 자동 이동하지 않아도 됨(단순하게). 선택 칸이 없으면 패드 비활성.
- **즉각 규칙 위반 표시**: 같은 행/열/상자/대각선에 같은 숫자가 있거나 `lt`/`cage` 를 어기는 칸은 붉은 톤(정답을 직접 알려 주지는 않음).
- `fill_check` 버튼은 모든 빈칸이 채워졌을 때만 활성. 확인 → 입력값(행 우선 이어 붙임)이 `answer` 와 같으면 `submit(answer)`(정답 경로: 배지, 별, 지연 후 advance, 전면광고). 다르면 오답 경로(기존 흔들림/오답 로직), 입력 상태 유지. 규칙 위반이 있으면 확인 시 `fill_invalid` 안내 + 흔들림, 제출하지 않음(오답 카운트 없음).
- 접근성: 격자 전체에 "4x4 격자, 빈칸 N개" 정도의 라벨.

## 2. path — 경로 추적

### 규칙
N×N 격자(N=5,6,7) 위에서 **출발점에서 시작해 정해진 규칙으로 계속 움직이는 점**. 화면에는 처음 `m` 번의 이동 자취(번호가 붙은 점들과 선)만 보이고, 숨겨진 규칙을 알아내어 **총 `total` 번 이동한 뒤 점이 서 있는 칸**을 탭해 고른다. 점은 격자 밖으로 나가지 않도록 생성기가 보장한다.

이동 `k`(k=1,2,3…)는 `방향 d_k` 방향으로 `길이 L_k` 칸. 방향은 8방향(`U,UR,R,DR,D,DL,L,UL` — 대각선은 ch8+)이고 규칙은 두 부분의 조합:
- **방향 규칙** `dir`: (a) `cycle` — 방향 목록 `[d₁..d_p]`(p=1~4)을 반복, (b) `rot` — 매 이동마다 시계(`cw`) 또는 반시계(`ccw`)로 `step`(90° 또는 45°) 회전, 시작 방향 지정.
- **길이 규칙** `len`: (a) `const` — 항상 a, (b) `cycle` — 길이 목록 `[l₁..l_q]`(q=2~3, 각 1~4)을 반복, (c) `arith` — a, a+δ, a+2δ…(δ=±1, 항상 ≥1), (d) `grow` — 매 이동 `+1` 후 지정한 최대(cap)에 도달하면 1로 초기화.
한 퍼즐은 (dir, len) 한 쌍. 한 이동의 길이는 **대각선도 칸 수 L**(두 축 모두 L 칸).

### 스키마
```
{"id":"pc5_s03","type":"path","track":"path","chapter":5,"order":3,
 "size":6,
 "start":[2,1],                         // [행,열] 0-기반
 "trail":[[2,1],[2,3],[4,3],[4,5]],     // 보여 주는 점들: trail[0]=start, trail[i]=i번 이동 직후 칸 (m = trail.count-1)
 "total":6,                              // 총 이동 횟수(> m). 점은 total 번 이동한 뒤 멈춘다
 "answer":"29",                          // 최종 칸의 1-기반 행 우선 번호 = r*size + c + 1  (문자열)
 "inputType":"choices","choices":null,
 "hints":{ko:[3개],...}, "explanation":{ko:"...",...}}
```
- 앱 모델: `size: Int?`, `start: [Int]?`, `trail: [[Int]]?`, `total: Int?`. `isPath = trail 비어있지 않음`. `start == trail[0]` 이므로 `start` 는 생략해도 되지만 필드는 둔다.
- 장애물/벽 없음. 다만 같은 칸을 다시 지나도 됨.

### 유일성·품질 (생성기+검증기 필수)
- 검증기는 **가설 공간 전수 열거**: 위 방향 규칙 × 길이 규칙의 모든 매개변수(해당 챕터가 허용한 부분집합이 아니라 **전체 규칙 집합**, 단 방향 cycle 의 p≤4, 길이 cycle 의 q≤3)에 대해 `trail` 의 모든 이동을 재현하는 가설을 모은다. 보여 주는 이동 수 `m ≥ 2·max(p,q)+1` 이 되도록 챕터별 m 을 정한다. 격자 안에 머무는(모든 칸 범위 내) 가설만 남긴다. **남은 가설이 total 번 이동한 최종 칸이 모두 동일**해야 한다(= 답 유일). 가설 중 정답 칸이 다른 것이 하나라도 있으면 폐기.
- 정답 규칙은 최소 한 이동은 "단순 반복"이 아닌 구조(챕터 사다리 참조)여야 하고 `total - m` ≥ 2.
- 시작·자취 칸이 모두 격자 안, 최종 칸도 안. 최종 칸이 trail 중 하나와 같은 경우는 챕터당 최대 1문제.
- 챕터 내 동일 (size,trail,total) 중복 금지, 시드 고정, 멱등 병합.

### 난이도 사다리
| 챕터 | N | 방향 규칙 | 길이 규칙 | m | 추가 이동(total−m) |
|---|---|---|---|---|---|
| 1 | 5 | cycle(p=2: 가로↔세로 번갈아, 예 R,D) | const 1~2 | 4 | 2 |
| 2 | 5 | cycle(p=4: R,D,L,U 사각 회전) | const 1 | 5 | 2~3 |
| 3 | 5 | rot cw/ccw(90°) | const 1~2 | 5 | 2~3 |
| 4 | 6 | cycle(p=2) | cycle(q=2) | 5 | 2~3 |
| 5 | 6 | rot(90°) | cycle(q=2) | 6 | 3 |
| 6 | 6 | cycle(p=3) | arith(+1) | 6 | 3 |
| 7 | 6 | rot(90°) | grow(cap 3) | 6 | 3 |
| 8 | 6 | cycle(p=2, 대각선 포함) | const 1~2 | 5 | 3 |
| 9 | 7 | rot(45°) | const 1~2 | 6 | 3 |
| 10 | 7 | cycle(p=3, 대각선 포함) | cycle(q=2) | 7 | 3~4 |
| 11 | 7 | rot(90°) | arith(±1) | 7 | 3~4 |
| 12 | 7 | rot(45°) | cycle(q=3) | 8 | 4 |
| 13 | 7 | cycle(p=4, 대각선 포함) | cycle(q=3) | 9 | 4 |
| 14 | 7 | rot(45°)/cycle 혼합 | grow/arith/cycle 혼합 | 8~9 | 4~5 |
(챕터 안 order 1→6 은 위 범위 안에서 m 은 줄이고 total−m 은 키워 가며 어렵게. 유일성 검증이 통과하지 못하는 조합은 m 을 늘린다.)

### 힌트 3개
① 챕터별 요령(ch1~3 "방향이 어떻게 바뀌는지부터 봐요" / 길이 규칙 챕터 "한 번에 몇 칸 가는지도 규칙이 있어요" / 대각선 챕터 "비스듬히도 움직여요" 등) ② 방향 또는 길이 규칙 한 부분을 언어 중립 화살표로 공개(예 "다음 세 번의 방향: → ↓ ←", 길이 규칙이면 "길이: 1 2 3 …") ③ 다음 이동 하나의 도착 칸을 "행 R, 열 C" 로 공개. `explanation`: 이동 시퀀스를 화살표+길이로(예 `→2 ↓2 ←2 ↑2 …` 마지막에 `= R행 C열`), 언어 중립.

### 화면/조작
- 프롬프트 `path_prompt`("점은 어디에 도착할까요?" 같은 문구, 총 이동 횟수는 화면에서 `total` 로 표시: 숫자 배지 "N번 이동 후"). 그 아래 격자 카드(`PathBoardView`): N×N 정사각, 옅은 격자선. 자취 점들은 모드색 원+번호 0..m(0=출발은 별/이중 원), 이동 선은 모드색 선+화살촉, 마지막 보인 점은 강조. 아직 이동 전인 칸은 탭 가능, 탭한 칸은 선택(모드색 채움+글로우)되며 다시 탭하면 해제. 선택한 칸 번호 라벨은 표시하지 않아도 됨.
- `path_check` 버튼(선택 칸이 있을 때만 활성). 확인 → 선택 칸의 번호(1-기반 행 우선)가 `answer` 와 같으면 `submit(answer)`, 아니면 오답 경로(선택 유지 또는 해제는 기존 match/target 오답 처리와 동일하게).
- 접근성: 격자에 "N×N 격자, 출발 R행 C열, M번 이동 자취, 총 T번" 라벨.

## 3. 테스트 벡터
`scripts/fill_test_vectors.json`, `scripts/path_test_vectors.json`: 생성기가 만든다. fill: `{"cases":[{"board":[[..]],"rules":[..],"lt":[..],"cages":[..],"solutions":1,"answer":"...","blanks":n}]}` 샘플 10개 이상, path: `{"cases":[{"size":n,"trail":[[r,c],..],"total":T,"rule":{...},"final":[r,c],"answer":"..","hypotheses":k}]}` 샘플 10개 이상. 앱은 이 벡터로 파싱/디스플레이를 확인하면 되고, 앱에 규칙 평가 로직은 필요 없다(정답은 JSON 의 `answer`; fill 의 규칙 위반 표시와 `확인` 전 검사만 앱 로직).

## 4. 파일 이름 규칙
- 생성기: `scripts/gen_fill_puzzles.py`, `scripts/gen_path_puzzles.py` (`--puzzles <경로>`, 시드 고정, 멱등 병합). 검증기: `scripts/verify_fill_puzzles.py`, `scripts/verify_path_puzzles.py` (유일성 포함 전부 검사, 실패 시 비-0 종료). 기존 `gen_cipher_puzzles.py`/`verify_cipher_puzzles.py` 구조를 따른다. `scripts/README.md` 에 한 줄씩 추가.
- iOS: `Features/Play/FillBoardView.swift`, `PathBoardView.swift`. Android: `ui/play/FillBoard.kt`, `PathBoard.kt`.
