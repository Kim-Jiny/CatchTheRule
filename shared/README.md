# shared — 플랫폼 공유 콘텐츠 (단일 소스)

`content/puzzles.json` 이 **퍼즐 콘텐츠의 유일한 정본**이다.
iOS·Android 양쪽이 이 한 파일을 빌드 시점에 사용한다 — 복제본을 두지 않는다.

## 어떻게 공유되는가

| 플랫폼 | 방식 |
|---|---|
| **Android** | `app/build.gradle.kts` 의 `assets.srcDirs` 에 `../../shared/content` 추가 → Gradle 이 빌드 시 `assets/puzzles.json` 으로 패키징 (복사 없음) |
| **iOS** | `CatchTheRule` 타깃의 Run Script 빌드 페이즈가 `$(SRCROOT)/../shared/content/puzzles.json` 을 앱 번들 리소스로 복사 |

## 콘텐츠 추가/수정

`content/puzzles.json` **한 곳만** 고치면 양쪽에 반영된다.
각 퍼즐 스키마:

```json
{
  "id": "ch1_s01",            // 고유 id (chN_sMM 권장)
  "type": "arith",            // 규칙 유형 코드
  "chapter": 1,               // 챕터
  "order": 1,                 // 챕터 내 순서
  "sequence": [1,2,3,4,null], // null = 맞혀야 할 빈칸 (끝/중간 모두 가능)
  "answer": 5,
  "inputType": "keypad",      // "keypad" | "choices"
  "choices": null,            // choices 일 때 보기 배열
  "hints": ["...","...","..."], // 3단계 점진 힌트
  "explanation": "..."        // 정답 해설
}
```

> 빌드 후 확인: iOS → `*.app/puzzles.json`, Android → `aapt list app-debug.apk | grep puzzles.json`.

## 트랙(모드) — `track`

`track` 으로 홈 화면의 모드를 나눈다. 생략/`"numbers"` = 기본 "규칙찾기" 캠페인, `"shapes"` = "도형에서 규칙찾기".
트랙별로 독립 진행도(별·이어하기)를 가지며, 챕터 번호는 트랙 내부에서만 의미가 있다. 타임어택은 `numbers` 트랙만 출제한다.

## 도형 퍼즐 — `figure` / `figureTokens` / `figureChoices`

직접 그리는 도형(triangle·square·circle·arrow·dot)으로 출제한다. 두 형태:

- **숫자형(keypad)**: `figures` — 완성된 예시 도형 + 빈칸 도형을 한 줄에 나란히(규칙 추론용).
  각 도형의 `slots`(시계방향, `null`=빈칸)에 숫자. 레이아웃은 `shape`+`slots` 길이로 결정 —
  triangle 3=세 꼭짓점/4=+중앙, square 4=네 모서리/5=+중앙, circle N=N세그먼트. 빈칸(`null`)은 보통 마지막 도형에 하나, `answer`=그 숫자.
  ```json
  "figures": [
    { "shape": "triangle", "slots": ["5","2","3","10"] },   // 예시
    { "shape": "triangle", "slots": ["4","1","2","7"] },    // 예시
    { "shape": "triangle", "slots": ["6","2","3", null] }   // 빈칸 → answer "11"
  ]
  ```
- **시각형(choices)**: `figureTokens`(시퀀스, `null`=빈칸 셀) + `figureChoices`(보기 도형들).
  각 도형은 `rotation`/`filled`/`count` 로 모양을 정하고, 보기는 `code` 로 식별 — 정답 도형의 `code` == `answer`.
  ```json
  "figureTokens":  [{"shape":"arrow","rotation":0}, {"shape":"arrow","rotation":90}, null],
  "figureChoices": [{"shape":"arrow","rotation":0,"code":"up"}, {"shape":"arrow","rotation":90,"code":"right"}],
  "answer": "up"
  ```
