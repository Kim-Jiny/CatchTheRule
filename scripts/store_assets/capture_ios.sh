#!/usr/bin/env bash
# App Store 원본 스크린샷 자동 촬영 (iOS 시뮬레이터, 7개 언어 x 6개 장면).
# 결과: dist/store-assets/raw/<lang>/<scene>.png (1320x2868)
# 사용: scripts/store_assets/capture_ios.sh [-l "ko en"] [-s "home numbers"] [--skip-build]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BUNDLE_ID="com.jiny.CatchTheRule"
SIM_NAME="CTR iPhone 16 Pro Max"
DD="/tmp/ctr_ss_dd"
OUT="$ROOT/dist/store-assets/raw"
WAIT="${WAIT:-3.5}"     # 실행 후 촬영까지 대기(입장 애니메이션 ~2초 포함)

# 장면에 쓰는 퍼즐 id (바꾸려면 여기만 수정)
PUZZLE_NUMBERS="ch6_s03"          # 수열(키패드)
PUZZLE_CONTRADICTION="co4_s01"    # 모순 찾기(문장 7개)
PUZZLE_TARGET="tg3_s03"           # 숫자 만들기(재료 4개)
PUZZLE_MATCH="mt5_s05"            # 성냥개비(셀 7개)

# 언어코드:AppleLanguages:AppleLocale
LANGS_ALL="ko:ko:ko_KR en:en:en_US ja:ja:ja_JP zh-Hans:zh-Hans:zh_CN es:es:es_ES fr:fr:fr_FR de:de:de_DE"
# 장면이름:launch인자
SCENES_ALL="home:home numbers:play:$PUZZLE_NUMBERS contradiction:play:$PUZZLE_CONTRADICTION target:play:$PUZZLE_TARGET match:play:$PUZZLE_MATCH challenge:challenge"

ONLY_LANGS=""; ONLY_SCENES=""; SKIP_BUILD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    -l) ONLY_LANGS="$2"; shift 2 ;;
    -s) ONLY_SCENES="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    *) echo "unknown arg $1"; exit 1 ;;
  esac
done

UDID="$(xcrun simctl list devices available | grep -F "$SIM_NAME (" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')"
if [[ -z "$UDID" ]]; then
  UDID="$(xcrun simctl create "$SIM_NAME" com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro-Max com.apple.CoreSimulator.SimRuntime.iOS-18-4)"
fi
echo "simulator: $UDID"

if [[ $SKIP_BUILD -eq 0 ]]; then
  xcodebuild -project "$ROOT/iOS/CatchTheRule.xcodeproj" -scheme CatchTheRule -configuration Debug \
    -destination "platform=iOS Simulator,id=$UDID" -derivedDataPath "$DD" build CODE_SIGNING_ALLOWED=NO | tail -3
fi

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b >/dev/null
xcrun simctl status_bar "$UDID" override --time 9:41 --batteryState charged --batteryLevel 100 \
  --wifiBars 3 --cellularMode active --cellularBars 4
xcrun simctl install "$UDID" "$DD/Build/Products/Debug-iphonesimulator/CatchTheRule.app"

for L in $LANGS_ALL; do
  IFS=: read -r code lang loc <<<"$L"
  if [[ -n "$ONLY_LANGS" && " $ONLY_LANGS " != *" $code "* ]]; then continue; fi
  mkdir -p "$OUT/$code"
  for S in $SCENES_ALL; do
    name="${S%%:*}"; arg="${S#*:}"
    if [[ -n "$ONLY_SCENES" && " $ONLY_SCENES " != *" $name "* ]]; then continue; fi
    xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
    xcrun simctl launch "$UDID" "$BUNDLE_ID" -AppleLanguages "($lang)" -AppleLocale "$loc" -ctr_scene "$arg" >/dev/null
    sleep "$WAIT"
    xcrun simctl io "$UDID" screenshot "$OUT/$code/$name.png" 2>/dev/null
    w=$(sips -g pixelWidth "$OUT/$code/$name.png" | awk '/pixelWidth/{print $2}')
    h=$(sips -g pixelHeight "$OUT/$code/$name.png" | awk '/pixelHeight/{print $2}')
    [[ "$w" == 1320 && "$h" == 2868 ]] || echo "WARN: $code/$name is ${w}x${h}"
    echo "ok $code/$name"
  done
done
xcrun simctl terminate "$UDID" "$BUNDLE_ID" 2>/dev/null || true
echo "done -> $OUT   (끝나면: xcrun simctl shutdown $UDID)"
