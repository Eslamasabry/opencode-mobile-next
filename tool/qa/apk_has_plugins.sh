#!/usr/bin/env bash
# Fail unless an APK still carries Flutter's plugin registrant.
#
#   tool/qa/apk_has_plugins.sh <apk>
#
# Why: without io.flutter.plugins.GeneratedPluginRegistrant no plugin
# registers on any engine. The app still builds and installs, then dies on
# every cold start the moment Dart first asks for a folder: path_provider
# reaches package:jni, whose Java half never loaded, and libdartjni aborts
# with "JNI is not initialized" (SIGSEGV in FindClass). APK 4293 shipped to
# the emulator like that on 2026-10-31: a vps_test run rsynced
# (--delete) into the build's server folder mid-build and removed the
# generated, git-ignored registrant before Gradle compiled it.
set -euo pipefail
apk="${1:-}"
[[ -f "$apk" ]] || { echo "apk_has_plugins: no such APK: $apk" >&2; exit 64; }
dexes="$(unzip -Z1 "$apk" 2>/dev/null | grep -E '^classes[0-9]*\.dex$' || true)"
[[ -n "$dexes" ]] || { echo "apk_has_plugins: $apk holds no classes.dex" >&2; exit 1; }
# grep -c reads to the end, so unzip never dies of a closed pipe.
found="$(unzip -p "$apk" 'classes*.dex' | grep -a -c 'Lio/flutter/plugins/GeneratedPluginRegistrant;' || true)"
if [[ "${found:-0}" -gt 0 ]]; then
  exit 0
fi
echo "apk_has_plugins: $apk has no io.flutter.plugins.GeneratedPluginRegistrant;" \
  "no plugin would register and the app would crash on start (JNI is not initialized)" >&2
exit 1
