# scripts

## gen_l10n.py — UI 문자열 로컬라이즈 생성기

UI 문자열을 **한 곳(마스터 테이블)** 에서 관리하고 양 플랫폼 리소스를 자동 생성합니다.

```bash
python3 scripts/gen_l10n.py
```

생성물 (직접 수정 금지 — 이 스크립트로만 갱신):
- `iOS/CatchTheRule/Localizable.xcstrings` — UI 문자열(키별 7개국어)
- `iOS/CatchTheRule/InfoPlist.xcstrings` — `CFBundleDisplayName`(앱 이름/아이콘 라벨)
- `Android/app/src/main/res/values{,-ko,-ja,-zh-rCN,-es,-fr,-de}/strings.xml`

### 문자열 추가/수정
`gen_l10n.py` 의 `T` 딕셔너리에 한 줄 추가 후 재실행:

```python
"key": ["English", "한국어", "日本語", "简体中文", "Español", "Français", "Deutsch"],
```

- 언어 순서: **en, ko, ja, zh, es, fr, de** (en = 기본/폴백)
- 정수 포맷: `%d`, 위치 인자 `%1$d %2$d` (iOS 는 자동으로 `%lld` 변환)
- 코드 사용:
  - iOS: `String.loc("key")` 또는 `String.loc("key", arg)`
  - Android: `stringResource(R.string.key)` 또는 `stringResource(R.string.key, arg)`

### 언어
en(기본) · ko · ja · zh-Hans · es · fr · de — 기기 언어 자동 적용, 미지원 언어는 en 폴백.

## 퍼즐 콘텐츠 로컬라이즈
퍼즐 힌트/해설은 `shared/content/puzzles.json` 의 `hints`/`explanation` 에
로케일별 맵(`{"en":..., "ko":...}`)으로 들어 있습니다. 앱이 기기 언어로 읽고 en 폴백합니다.

## 정책 페이지(개인정보처리방침·이용약관·고객지원)
Minigame 서버 `server/public/ctr/{privacy,terms,support}.html` 에 7개국어가 한 페이지에
내장돼 있으며 `?lang=<code>` 또는 브라우저 언어로 자동 표시됩니다(앱이 기기 언어를 전달).

## 숫자 만들기(target) 퍼즐 생성
`python3 scripts/gen_target_puzzles.py` — 카운트다운식 브루트포스 솔버(부분집합 DP)로 14챕터×6문제(84개)를 시드 고정으로 생성해 `shared/content/puzzles.json` 에 병합합니다(track=="target" 항목만 지우고 다시 추가하므로 재실행 안전, 기존 퍼즐은 변경 없음). `--dry` 는 파일 쓰기 없이 챕터별 난이도 표만 출력. 생성 후 `python3 scripts/verify_target_puzzles.py` 로 설명 문자열을 실제 실행해 해법·힌트·언어 개수·id/order 를 독립 검증합니다.

## 성냥개비(match) 퍼즐 생성
`python3 scripts/gen_match_puzzles.py` — 명세 `docs/match-mode-spec.md` 대로 셀/슬롯 비트마스크 모델에서 순변위 ≤ moves 인 모든 도달 상태를 열거하는 솔버로 14챕터×6문제(84개, id `mt{N}_s{01..06}`)를 시드 고정으로 생성해 `shared/content/puzzles.json` 에 병합합니다(track=="match" 항목만 지우고 다시 추가하므로 재실행 안전). 참인 식에서 역방향으로 막대를 옮겨 "모든 셀이 유효 글리프인 거짓 식"을 만들고, 해 개수·이동 유형(같은 셀/다른 셀/연산자 연루)·최소 이동 수로 챕터 조건을 필터하며 moves=2 챕터는 1이동으로 풀리면 제외합니다. 같은 실행에서 플랫폼 단위 검증용 `scripts/match_test_vectors.json`(글리프 해석·식 판정·순변위·샘플 퍼즐)도 만듭니다. `--dry` 는 파일 쓰기 없이 챕터별 지표만 출력(약 2분). 생성 후 `python3 scripts/verify_match_puzzles.py` 가 생성기와 독립된 BFS(막대를 실제로 한 개씩 옮겨 탐색)로 초기 거짓·해 존재·1이동 불가·힌트 셀 번호·7개 언어·id/order·테스트 벡터를 재검증합니다.

## 조각 숫자(fragment) 퍼즐 생성
`python3 scripts/gen_fragment_puzzles.py` — 명세 `docs/fragment-mode-spec.md` 대로 7세그먼트 숫자를 여러 장의 획 조각(or=합집합 / xor=홀수 번 나온 획)으로 쪼개 14챕터×6문제(84개, id `fg{N}_s{01..06}`)를 시드 고정으로 생성해 `shared/content/puzzles.json` 에 병합합니다(track=="fragment" 항목만 지우고 다시 추가하므로 재실행 안전, 다른 퍼즐은 바이트 동일). 최종 숫자에서 역방향으로 획을 장별로 분배하며(함정 칸에는 다른 유효 숫자 패턴을 고정, xor 는 짝수 번 나와 사라지는 노이즈 획과 3번 나오는 획 추가, or 9~10 은 중복 획 추가), 합성 결과가 `answer` 와 일치하는지 assert 하고 챕터별 함정/노이즈/중복 개수·frameMs 로 난이도를 올립니다. 같은 실행에서 플랫폼 단위 검증용 `scripts/fragment_test_vectors.json`(합성 케이스 49개 + 실제 퍼즐 샘플 10개)도 만듭니다. `--dry` 는 파일 쓰기 없이 챕터별 지표 표만 출력. 생성 후 `python3 scripts/verify_fragment_puzzles.py` 가 생성기와 독립된 비트마스크 합성기로 합성·챕터 조건·함정/노이즈·choices·7개 언어 힌트·id/order·테스트 벡터를 재검증합니다.

## 암호 해독(cipher) 퍼즐 생성
`python3 scripts/gen_cipher_puzzles.py [--puzzles PATH] [--vectors PATH] [--dry]` — 명세 `docs/cipher-scale-mode-spec.md` §1·§2 대로 이모지 기호 → 숫자 규칙(map/rev/sum/diff/prod/wsum/cnt/dsum)을 챕터 사다리(1~2 map … 13~14 전 규칙 혼합)에 맞춰 14챕터×6문제(84개, id `cp{N}_s{01..06}`, pairs/query 스키마)를 시드 고정으로 생성해 `shared/content/puzzles.json`(또는 `--puzzles` 경로)에 병합합니다(track=="cipher" 항목만 지우고 맨 뒤에 다시 추가하므로 재실행 안전, 다른 퍼즐은 바이트 동일). 값 할당 전수 탐색으로 query 결과가 유일하고 map/rev/sum 으로 해석해도 모호하지 않은 예시 3~6개만 남기며, 힌트 3개·해설을 7개 언어로 만듭니다. 같은 실행에서 `scripts/cipher_test_vectors.json`(규칙 평가 케이스 + 실제 퍼즐 샘플 10개)도 만듭니다. `--dry` 는 파일 쓰기 없이 챕터별 지표 표만 출력. 생성 후 `python3 scripts/verify_cipher_puzzles.py [--puzzles PATH] [--vectors PATH]` 가 생성기와 독립된 가지치기 DFS 로 유일성·모호함·사다리·id/order·7개 언어 힌트·테스트 벡터를 재검증합니다(실패 시 종료 코드 1).

## 저울 추리(scale) 퍼즐 생성
`python3 scripts/gen_scale_puzzles.py [--puzzles PATH] [--dry]` — 명세 `docs/cipher-scale-mode-spec.md` §1·§3 대로 이모지 무게(서로 다른 양의 정수)와 숫자 추로 균형 잡은 저울들(scales/query 스키마)을 챕터 사다리(1~2 단일 대입 … 7~8 공통 기호 상쇄 · 9~10 배수 · 11~12 큰 무게+숫자 추 · 13~14 혼합)에 맞춰 14챕터×6문제(84개, id `sc{N}_s{01..06}`)를 시드 고정으로 생성해 `shared/content/puzzles.json`(또는 `--puzzles` 경로)에 병합합니다(track=="scale" 항목만 기존 위치에서 교체하므로 재실행 안전·연속 블록 유지, 다른 퍼즐은 바이트 동일). 2챕터 이상은 시작 시 '직접 저울'(상쇄 후 미지수 하나)이 없고 저울 순서는 시드 셔플이며, 미지수 수 = 저울 수(모든 저울이 꼭 필요)·query 는 저울의 정수 계수 결합(손 풀이 가능)·정답은 챕터가 올라갈수록 커지고(최대 3자리), 선형 풀이로 값이 유일함을 확인하고, 힌트의 '기호 하나의 무게'는 모든 해에서 확정된 값만 씁니다(힌트 3개·해설 7개 언어). 같은 실행에서 `scripts/scale_test_vectors.json`(균형 평가 케이스 + 실제 퍼즐 샘플 10개)도 만듭니다. `--dry` 는 파일 쓰기 없이 챕터별 지표 표만 출력. 생성 후 `python3 scripts/verify_scale_puzzles.py [--puzzles PATH] [--vectors PATH] [--table]` 가 생성기와 독립된 선형 풀이/제약 DFS 로 유일성·자명한 저울·중복·사다리·손 풀이 조건·힌트 값·테스트 벡터를 재검증합니다(실패 시 종료 코드 1).

## 격자 채우기(fill) 퍼즐 생성
`python3 scripts/gen_fill_puzzles.py [--puzzles PATH] [--vectors PATH] [--dry]` — 명세 `docs/fill-path-mode-spec.md` §1 대로 14챕터×6문제(84개, id `fx{N}_s{01..06}`)를 시드 고정으로 생성해 puzzles.json(또는 `--puzzles`)에 병합(track=="fill" 항목만 교체, 재실행 안전)하고 `scripts/fill_test_vectors.json` 도 만듭니다. 검증: `python3 scripts/verify_fill_puzzles.py [--puzzles PATH] [--vectors PATH]` — 백트래킹으로 해 개수==1, 규칙(lt/cages/diag/box) 일관성, 고정 칸 최소화, 챕터 사다리, 7개 언어 힌트를 검사(실패 시 종료 코드 1).

## 경로 추적(path) 퍼즐 생성
`python3 scripts/gen_path_puzzles.py [--puzzles PATH] [--vectors PATH] [--dry]` — 명세 `docs/fill-path-mode-spec.md` §2·§3 대로 N×N 격자 위 점의 이동 자취(방향 규칙 cycle/rot × 길이 규칙 const/cycle/arith/grow)를 챕터 사다리에 맞춰 14챕터×6문제(84개, id `pc{N}_s{01..06}`, size/start/trail/total 스키마)를 시드 고정으로 생성해 `shared/content/puzzles.json`(또는 `--puzzles` 경로)에 병합합니다(track=="path" 항목만 기존 위치에서 교체, 없으면 맨 뒤에 추가하므로 재실행 안전). 방향 규칙 전체(8^p, p≤4 + rot) × 길이 규칙 전체 가설을 전수 열거해 total 번 이동 동안 격자를 벗어나지 않는 가설의 최종 칸이 하나뿐인 문제만 남기며, 힌트 3개·해설을 7개 언어로 만듭니다. 같은 실행에서 `scripts/path_test_vectors.json`(규칙·전체 경로 포함 케이스 18개)도 만듭니다. 생성 후 `python3 scripts/verify_path_puzzles.py [--puzzles PATH] [--vectors PATH]` 가 생성기와 독립된 전수 열거로 유일성·격자 범위·total−m·챕터 사다리·힌트/해설 교차 검증·테스트 벡터를 재검증합니다(실패 시 종료 코드 1).
