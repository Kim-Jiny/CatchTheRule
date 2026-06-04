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
