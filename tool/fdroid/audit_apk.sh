#!/usr/bin/env bash
# Report what F-Droid's scanner would care about in an APK:
#   tool/fdroid/audit_apk.sh app.apk
# Prints class counts for Shorebird / Google SDK packages, the native
# libraries, and the manifest permissions. Exit 1 if a forbidden SDK is found.
set -euo pipefail
apk="${1:?usage: audit_apk.sh app.apk}"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
az="$(ls -d "$sdk"/cmdline-tools/*/bin/apkanalyzer | head -1)"
classes="$("$az" dex packages --defined-only "$apk" 2>/dev/null | awk '{print $NF}')"
bad=0
for pat in 'dev\.shorebird|io\.flutter\.plugins\.shorebird' 'com\.google\.android\.gms' 'com\.google\.mlkit' 'com\.google\.firebase' 'com\.google\.android\.play' 'com\.google\.ai\.edge' 'androidx\.camera'; do
  n=$(grep -cE "^($pat)" <<<"$classes" || true)
  printf 'dex packages matching %-45s %s\n' "$pat" "$n"
  case "$pat" in *camera*|*ai.edge*) ;; *) [ "$n" -eq 0 ] || bad=1;; esac
done
echo "--- shorebird in dex strings / assets"
unzip -l "$apk" | grep -iE 'shorebird|libupdater' || echo "(none)"
echo "--- native libraries"
unzip -l "$apk" | awk '/\.so$/ {print $4}' | sort
echo "--- permissions"
"$az" manifest permissions "$apk" | sort
echo "--- REQUEST_INSTALL_PACKAGES present?"
"$az" manifest permissions "$apk" | grep -c REQUEST_INSTALL_PACKAGES || true
exit $bad
