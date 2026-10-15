# 타임어택 "모든 모드 믹스" 명세

지금까지 타임어택은 숫자 모드(`numbers`)의 문제만 키패드/보기로 풀었다. 이제 **해금된 모든 모드의 문제를 무작위로 섞어** 60초 동안 최대한 많이 푼다.
대상: iOS `Features/Challenge/TimeAttackView.swift`, Android `ui/challenge/TimeAttackScreen.kt`(+ 필요한 공용 컴포넌트 재사용). 결과 화면/랭킹 제출(`TimeAttackResultView`/대응)은 **그대로**.
스타일은 `docs/home-ui-spec.md`·`tabs-ui-spec.md` 의 네온 스타일을 따르되, 문제 푸는 화면이라 장식은 최소(배경은 정적 `ScreenBackground()` 유지).

## 1. 출제(덱) 규칙
- **풀(pool)**: 해금된 모드만. 해금 전(숫자 ch1~5 미클리어)에는 `numbers` 하나뿐 → 기존과 동일. 해금 후에는 `PuzzleStore.trackOrder` 6개 모드 전부.
- 각 모드에서 **쉬운 앞쪽 절반 챕터**만 쓴다: `chapter <= ceil(그 모드 최대 챕터 / 2)` (numbers 11→6, shapes 10→5, logic 14→7, contradiction 13→7, target 14→7, match 14→7). 60초 안에 풀 수 있게.
- 뽑기: 문제를 낼 때마다 **모드를 균등 확률로 무작위 선택**(같은 모드가 3번 연속 나오지 않게 보정), 그 모드의 **셔플된 문제 리스트에서 다음 문제**를 꺼낸다(리스트를 다 쓰면 다시 셔플). 같은 판에서 같은 문제가 중복 출제되지 않게(풀이 소진 시에만 재순환).
- 덱 구성/뽑기 로직은 UI와 분리된 작은 타입(예: `TimeAttackDeck`)으로 만들어 단위 검증 가능하게.

## 2. 문제 종류별 화면 (캠페인 화면의 컴포넌트를 재사용)
상단바(닫기 / 타이머 / 점수)는 기존 유지. 그 아래 **현재 문제의 모드 칩**(모드 아이콘 + 모드 제목, 모드색 `Theme.modeColor`)을 작게 표시해 어떤 모드인지 알려 준다.
- numbers · shapes(`shape_num`/`shape_seq`) · logic: 기존 캠페인과 같은 **문제 표시(`SequenceDisplay`) + 입력**(keypad → `KeypadView`, choices → `ChoicesView`, 도형 보기 → `FigureChoicesView`). 정답이면 즉시 +1점, 다음 문제. 오답이면 흔들림.
- contradiction(모순 찾기): 문장 카드를 직접 탭(`onPickStatement`), 하단 입력 영역 없음. 맞으면 +1.
- target(숫자 만들기): 기존 `Target*` 컴포넌트(수식 표시줄 + 패드 + `확인` 버튼)를 재사용. 확인 시 식이 목표와 같으면 +1, 아니면 오답 흔들림(식 유지).
- match(성냥개비): 기존 `Match*` 컴포넌트(드래그 보드 + 되돌리기/처음부터/확인)를 재사용. 확인 시 참인 식이면 +1, 아니면 오답 흔들림.
- **힌트·계산기 버튼은 타임어택에 없음**(기존대로). 광고(전면/오답)는 타임어택에서 없음(기존대로).
- 가운데 문제 영역이 넘치면 스크롤 가능하게(contradiction 긴 문장 등), 입력 영역은 하단 고정. 하단 배너는 기존 타임어택에 없으면 추가하지 않는다.
- **정답 판정 로직과 상태 초기화**는 캠페인과 같은 규칙(`puzzle.isCorrect`, target/match 는 각자의 제출 값 규칙)을 쓴다. 다음 문제로 넘어갈 때 target/match 의 보드 상태는 반드시 새로 초기화.

## 3. 점수·시간·건너뛰기
- 점수: **문제당 +1점**(모드 가중치 없음). 시간은 기존 60초(`duration`).
- **건너뛰기 버튼**(`ta_skip` "건너뛰기 −3초"): 현재 문제를 버리고 다음 문제로, 남은 시간에서 3초 차감(0 미만이면 0 → 종료). 모드별로 시간이 오래 걸리는 문제에서 막히지 않게 하기 위함. 화면 하단/입력 영역 위에 작게(눈에 띄되 정답 입력과 헷갈리지 않게). 점수는 변화 없음.
- 카드 전환은 기존처럼 짧은 지연(≈0.2초) 후, 전환 중 재제출 방지(`advancing`).
- 시간이 끝나면 기존 결과 화면(`TimeAttackResultView`)으로, 최고기록 갱신 로직도 그대로.

## 4. 문자열
이미 반영됨(gen_l10n.py): `challenge_mode_note`(모든 모드 믹스 안내로 교체), `ta_skip`. 화면 프롬프트는 기존 키 재사용: `play_prompt`, `contradiction_prompt`, `target_prompt`, `match_prompt`, `match_moves`, `target_check`/`match_check`, `target_reset`/`match_reset` 등. 더 필요한 키는 만들지 말고 보고(`Localizable.xcstrings`, `strings.xml` 직접 편집 금지).

## 5. 주의
- 캠페인 세션 화면(`CampaignSessionView`/`CampaignSessionScreen`)의 동작은 바꾸지 말 것. 재사용을 위해 컴포넌트를 뽑아낼 필요가 있으면 **동작을 바꾸지 않는 리팩터링**으로만(양쪽 모두 빌드 통과 확인).
- 서버 랭킹은 기존 `mode=timeAttack` 그대로(점수 정수). 서버 변경 없음.
- 성능: 문제 전환마다 무거운 뷰를 새로 만들어도 부드럽게(보드 상태 reset 은 `id(puzzle.id)` / `key(puzzle.id)` 로).
