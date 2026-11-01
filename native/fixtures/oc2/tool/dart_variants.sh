#!/usr/bin/env bash
# Regenerates ../parity/dart-variants.json with the Flutter client's own OpenCode 2 parsers.
# Needs only a plain Dart SDK (DART=/path/to/dart to override): the parsers it runs import nothing but dart:core,
# so a one-package config pointing `opencode_mobile` at lib/ replaces `flutter pub get`.
set -euo pipefail
root="$(cd "$(dirname "$0")/../../../.." && pwd)"
dart="${DART:-dart}"
config="$(mktemp -d)/package_config.json"
trap 'rm -rf "$(dirname "$config")"' EXIT
cat > "$config" <<JSON
{"configVersion": 2, "packages": [{"name": "opencode_mobile", "rootUri": "file://$root/", "packageUri": "lib/", "languageVersion": "3.10"}]}
JSON
cd "$root"
"$dart" --packages="$config" native/fixtures/oc2/tool/dart_variants.dart
