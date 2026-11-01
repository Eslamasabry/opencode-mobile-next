#!/usr/bin/env bash
# Nightly iOS klib compile (ADR §6.3 item 2, §7.1; plan 03 §8.3 "T2 nightly"). Runs on the VPS only, never per change.
# It compiles common code to iOS klibs so JDK-only APIs in commonMain fail the night they land. No cinterop deps, no
# linking, no Xcode: a Linux host can build klibs for Apple targets. Not run in slice 0 (P1 writes it; the VPS runs it).
#
# Install on the VPS (00:30, inside the midnight–2 am VPS-only window, AGENTS.md):
#   30 0 * * *  cd /srv/oc_app/native && tool/qa/ios-klib-nightly.sh >> ~/logs/ios-klib.log 2>&1
#
# Scope (ADR §11 P1 acceptance and fallback): core and engine modules always; UI modules only once P1 on the VPS
# shows CMP compiles them for iOS (set OC_IOS_UI=1 then).
set -euo pipefail
cd "$(dirname "$0")/../.."

modules=(":core:common" ":core:model" ":core:security" ":core:domain" ":engine:connection" ":engine:transcript"
  ":ui:routes" ":ui:design")
if [[ "${OC_IOS_UI:-0}" == "1" ]]; then modules+=(":ui:words" ":ui:kit" ":ui:shell" ":feature:chats"); fi

tasks=()
for m in "${modules[@]}"; do tasks+=("$m:compileKotlinIosArm64" "$m:compileKotlinIosSimulatorArm64"); done

started=$(date +%s)
./gradlew -Poc.ios=true -Pkotlin.incremental.native=true --continue "${tasks[@]}"
echo "ios-klib-nightly: ${#modules[@]} modules green in $(( $(date +%s) - started )) s at $(date -u +%FT%TZ)"
