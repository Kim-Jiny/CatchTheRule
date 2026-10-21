# App Store 원본 스크린샷 촬영 (iOS)

`scripts/store_assets/capture_ios.sh` 가 시뮬레이터("CTR iPhone 16 Pro Max", 1320x2868)에서
7개 언어(ko en ja zh-Hans es fr de) x 6개 장면(home numbers contradiction target match challenge)을 촬영한다.

    scripts/store_assets/capture_ios.sh                 # 전체 42장 (빌드 포함)
    scripts/store_assets/capture_ios.sh --skip-build -l "ko en" -s "home challenge"
    WAIT=5 scripts/store_assets/capture_ios.sh          # 촬영 전 대기(초) 조정, 기본 3.5

- 결과: `dist/store-assets/raw/<lang>/<scene>.png` (dist/ 는 gitignore)
- 장면에 쓰는 퍼즐 id 는 스크립트 상단 `PUZZLE_*` 상수로 변경.
- 앱 쪽 훅은 DEBUG 빌드 전용 `iOS/CatchTheRule/App/ScreenshotMode.swift`
  (`-ctr_scene home|challenge|play:<id>`): 광고/UMP/ATT/핑/서버 갱신 off, 진행도 시드, 가짜 랭킹.
  릴리스 빌드에는 포함되지 않는다.
- 끝나면 `xcrun simctl shutdown <udid>`.

# Google Play 원본 스크린샷 촬영 (Android)

`scripts/store_assets/capture_android.sh` 가 7개 언어(ko en ja zh es fr de) x 6개 장면을 `adb` 로 촬영한다.

    scripts/store_assets/capture_android.sh                    # 전체 42장 (디버그 빌드 포함)
    scripts/store_assets/capture_android.sh --skip-build -l "ko en" -s "home challenge"
    WAIT=5 scripts/store_assets/capture_android.sh

- 결과: `dist/store-assets/raw-android/<lang>/<scene>.png`
- 앱 쪽 훅은 DEBUG 소스셋 `Android/app/src/debug/.../ScreenshotMode.kt` (릴리스는 `src/release` 의 빈 stub).
  `adb shell am start -n com.jiny.catchtherule/.MainActivity --es ctr_scene home|challenge|play:<id>`
  extra 가 있을 때만 켜지며 광고/UMP/결제/핑/서버갱신 off, 진행도는 별도 prefs(`ctr_progress_shot`)에 시드(실제 `ctr_progress` 무변경),
  가짜 랭킹, 시스템 바 숨김.
- 스크립트의 기기 시리얼(`SERIAL`)/데모 모드 부분은 대상(에뮬레이터 vs 실기기)에 맞게 확인할 것.
