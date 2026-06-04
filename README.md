# CatchTheRule (규칙찾기)

수열·패턴의 **규칙을 찾아 빈칸/다음 항을 맞히는** 싱글플레이 퍼즐 게임.
서버·로그인 없이 단계별 콘텐츠를 내장하고, 타임어택으로 랭킹에 도전한다.

## 디렉토리 구조

```
CatchTheRule/
├─ shared/content/puzzles.json   # ★ 퍼즐 콘텐츠 단일 정본 (양쪽이 빌드 시 사용)
│
├─ iOS/                      # SwiftUI 네이티브 앱 (먼저 개발)
│  ├─ CatchTheRule.xcodeproj # Run Script 가 shared/ 의 puzzles.json 을 번들에 복사
│  └─ CatchTheRule/
│     ├─ App/                # 진입점, RootView(탭)
│     ├─ Core/               # 모델, PuzzleStore (UI 비종속)
│     ├─ Services/           # ProgressStore, RankingService(+Mock/Minigame)
│     ├─ Features/           # Home / Challenge / Settings / Play
│     └─ DesignSystem/       # Theme, Components, Haptics
│
├─ Android/                  # Kotlin + Jetpack Compose 앱
│  ├─ app/src/main/java/com/jiny/catchtherule/
│  │  ├─ core/               # model, PuzzleStore
│  │  ├─ data/              # ProgressStore, RankingService(+Mock/Minigame)
│  │  └─ ui/                 # theme, home, challenge, settings, play
│  └─ app/build.gradle.kts   # assets.srcDirs 에 ../../shared/content 추가
│
└─ docs/기획서.md            # 제품/기술 기획서
```

> **퍼즐 콘텐츠는 `shared/content/puzzles.json` 한 곳이 정본이다.** 한 파일만 고치면
> 양쪽 빌드에 반영된다 — 복제본 없음. (Android=Gradle asset srcDir, iOS=Run Script 복사.
> 자세한 내용은 [shared/README.md](shared/README.md))

## 빌드

### iOS
```bash
cd iOS
xcodebuild -project CatchTheRule.xcodeproj -scheme CatchTheRule \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```
또는 `iOS/CatchTheRule.xcodeproj` 를 Xcode 로 열어 실행. (iOS 17+, Swift 5)

### Android
```bash
cd Android
./gradlew :app:assembleDebug      # APK 빌드
./gradlew :app:installDebug       # 연결된 기기/에뮬레이터에 설치
```
또는 `Android/` 를 Android Studio 로 열어 실행. (minSdk 24, Kotlin 2.0, Compose)

## 아키텍처 메모

- **게임 로직/콘텐츠는 플랫폼 비종속**: 규칙 판정은 단순 `answer` 비교, 데이터는 JSON.
- **랭킹은 인터페이스로 추상화**: 현재 `MockRankingService`(메모리)로 동작.
  실제 Minigame 서버 스펙 확정 시 `MinigameRankingService` 채우고 한 줄 교체:
  - iOS: `Ranking.service` (RankingService.swift)
  - Android: `Ranking.service` (RankingService.kt)
- **진행도/설정**은 로컬 영속화(iOS `UserDefaults`, Android `SharedPreferences`).

## 상태

| | iOS | Android |
|---|---|---|
| 홈(이어하기/진행도) | ✅ | ✅ |
| 캠페인 플레이 + 힌트 | ✅ | ✅ |
| 타임어택 + 랭킹(Mock) | ✅ | ✅ |
| 설정(문의/초기화) | ✅ | ✅ |
| 콘텐츠 (챕터 1~5, 28문제) | ✅ | ✅ |
| 광고(리워드 힌트) | ⬜ MVP 제외 | ⬜ MVP 제외 |
| Minigame 서버 랭킹 결합 | ✅ (배포 대기) | ✅ (배포 대기) |

### 랭킹 백엔드 (Minigame 서버)

`MinigameRankingService` 가 **https://duo.jiny.shop** 의 CatchTheRule 전용 API를 호출한다.
서버 코드는 별도 리포 `../Minigame/server` (Express + PostgreSQL, 테이블 프리픽스 `ctr_`):

- `POST /api/catchtherule/scores` — `{ nickname, score, mode, deviceId }` → `{ rank }`
- `GET  /api/catchtherule/leaderboard?mode=timeAttack&limit=100` → `{ entries: [{ rank, nickname, score }] }`

로그인이 없으므로 인증 없이 호출하고, 기기당 1행(최고점) 유지를 위해 로컬 영속 UUID `deviceId` 를 보낸다.
`ctr_rankings` 테이블은 서버 부팅 시 자동 생성된다. **서버를 배포(또는 재시작)하면 즉시 동작.**

### 약관 · 개인정보처리방침

설정 탭의 링크가 서버의 정적 페이지를 연다 (서버 `public/ctr/`, `express.static` 으로 `/ctr` 마운트):
- 이용약관: `https://duo.jiny.shop/ctr/terms`
- 개인정보처리방침: `https://duo.jiny.shop/ctr/privacy`

앱과 동일한 다크 테마의 HTML. 랭킹 API와 마찬가지로 **서버 배포 후 라이브.**

### 문의(인앱) · 백스테이지 관리

설정 탭의 "문의하기"는 (mailto 가 아니라) **인앱 폼**으로 서버에 문의를 등록하고, "내 문의"에서 운영자 답변을 본다 (로그인 없이 `deviceId` 기반).
- 앱→서버: `POST /api/catchtherule/inquiries`, `GET /api/catchtherule/inquiries?deviceId=`
- 관리자: **`https://duo.jiny.shop/backstage`** (기존 `/admin` 폐지). 로그인 → **게임 허브**(듀오 아레나 / 규칙찾기, `GAMES` 배열로 확장) → 게임 선택 → 게임별 admin.
  - 규칙찾기 admin: 문의 목록(대기/답변 필터, 대기 배지) + 답변 전송/삭제. API `GET|PUT|DELETE /api/admin/ctr/inquiries...`
- 테이블 `ctr_inquiries` (서버 부팅 시 자동 생성). **서버 배포 후 라이브.**

**콘텐츠 구성** (`shared/content/puzzles.json`, 토큰을 문자열로 통합해 숫자·문자·모양 공용)
- Ch1 기초 패턴 · Ch2 곱셈과 제곱 · Ch3 수학 수열 (숫자, 키패드/4지선다)
- Ch4 문자 패턴 (A C E G ? … 알파벳, 4지선다)
- Ch5 모양 찾기 (⬆️➡️⬇️⬅️ ? … 이모지, 4지선다)
