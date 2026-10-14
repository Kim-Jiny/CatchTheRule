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
