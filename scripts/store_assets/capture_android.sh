#!/usr/bin/env bash
# Google Play 원본 스크린샷 자동 촬영 (7개 언어 x 6개 장면). 기본은 USB 연결된 실기기(삼성 SM-S911N, 1080x2340).
# 결과: dist/store-assets/raw-android/<lang>/<scene>.png
# 사용: scripts/store_assets/capture_android.sh [-l "ko en"] [-s "home numbers"] [--skip-build]
#   SERIAL=emulator-5554 EMU=1 scripts/store_assets/capture_android.sh   # 에뮬레이터(1080x2400)로 촬영하려면
# 실기기 모드는 폰의 시스템 설정을 바꾸지 않는다(앱별 로케일만 바꾸고 끝나면 원복). 스크린샷 모드(DEBUG 빌드)는
# 별도 prefs(ctr_progress_shot)를 쓰므로 실제 진행도 파일을 건드리지 않고, 앱이 시스템 바를 숨긴 채로 촬영된다.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PKG="com.jiny.catchtherule"
AVD="Medium_Phone_API_35"
SERIAL="${SERIAL:-R3CW10E26FB}"
EMU="${EMU:-0}"
WANT_W=1080; WANT_H="${WANT_H:-2340}"; [[ "$EMU" == 1 ]] && WANT_H=2400
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="adb -s $SERIAL"
OUT="$ROOT/dist/store-assets/raw-android"
WAIT="${WAIT:-4}"     # 실행 후 촬영까지 대기(입장 애니메이션 포함)

# 장면에 쓰는 퍼즐 id (iOS 와 동일; 바꾸려면 여기만 수정)
PUZZLE_NUMBERS="ch6_s03"
PUZZLE_CONTRADICTION="co4_s01"
PUZZLE_TARGET="tg3_s03"
PUZZLE_MATCH="mt5_s05"

# 폴더명:로케일
LANGS_ALL="ko:ko-KR en:en-US ja:ja-JP zh:zh-CN es:es-ES fr:fr-FR de:de-DE"
# 장면이름:ctr_scene 값
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

# 1) 기기 확인 (에뮬레이터 모드일 때만 기동)
if [[ "$EMU" == 1 ]]; then
  if ! adb devices | grep -q "^$SERIAL"; then
    echo "starting emulator $AVD ..."
    nohup "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot -no-audio >/tmp/ctr_emulator.log 2>&1 &
    $ADB wait-for-device
    until [[ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 3; done
    sleep 40
  fi
fi
adb devices | grep -q "^$SERIAL" || { echo "device $SERIAL not found"; exit 1; }
$ADB shell input keyevent KEYCODE_WAKEUP

# 2) 빌드/설치 (DEBUG 빌드에만 ctr_scene 훅이 있다)
if [[ $SKIP_BUILD -eq 0 ]]; then
  (cd "$ROOT/Android" && ./gradlew :app:assembleDebug -q)
fi
$ADB install -r "$ROOT/Android/app/build/outputs/apk/debug/app-debug.apk" >/dev/null

# 3) 상태바/내비게이션: 에뮬레이터 모드에서만 데모 모드 사용(실기기는 앱이 시스템 바를 숨김)
if [[ "$EMU" == 1 ]]; then
  $ADB shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null
  $ADB shell settings put global sysui_demo_allowed 1
  demo() { $ADB shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
  demo -e command enter
  demo -e command clock -e hhmm 1200
  demo -e command battery -e level 100 -e plugged false
  demo -e command network -e wifi show -e level 4 -e fully true
  demo -e command network -e mobile show -e datatype none -e level 4 -e fully true
  demo -e command notifications -e visible false
  demo -e command status -e volume hidden -e bluetooth hidden -e location hidden -e alarm hidden -e zen hidden
fi
cleanup() { $ADB shell cmd locale set-app-locales "$PKG" --locales "" >/dev/null 2>&1 || true; $ADB shell am force-stop "$PKG" || true; }
trap cleanup EXIT

for L in $LANGS_ALL; do
  code="${L%%:*}"; loc="${L#*:}"
  if [[ -n "$ONLY_LANGS" && " $ONLY_LANGS " != *" $code "* ]]; then continue; fi
  mkdir -p "$OUT/$code"
  $ADB shell cmd locale set-app-locales "$PKG" --locales "$loc" >/dev/null
  for S in $SCENES_ALL; do
    name="${S%%:*}"; arg="${S#*:}"
    if [[ -n "$ONLY_SCENES" && " $ONLY_SCENES " != *" $name "* ]]; then continue; fi
    $ADB shell am force-stop "$PKG"
    $ADB shell am start -n "$PKG/.MainActivity" --es ctr_scene "$arg" >/dev/null
    sleep "$WAIT"
    $ADB exec-out screencap -p > "$OUT/$code/$name.png"
    w=$(sips -g pixelWidth "$OUT/$code/$name.png" | awk '/pixelWidth/{print $2}')
    h=$(sips -g pixelHeight "$OUT/$code/$name.png" | awk '/pixelHeight/{print $2}')
    [[ "$w" == "$WANT_W" && "$h" == "$WANT_H" ]] || echo "WARN: $code/$name is ${w}x${h} (want ${WANT_W}x${WANT_H})"
    echo "ok $code/$name"
  done
done
echo "done -> $OUT   (앱 로케일은 자동 원복)"
