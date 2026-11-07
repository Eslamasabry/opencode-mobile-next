#!/usr/bin/env bash
# compile_desktop_tests.sh — every desktopTest source set compiles (scoreboard 2026-11-07 item 0.3). A merge that
# breaks another module's test sources (89ec49e55: `:ui:kit-chat` used KitTrayAction/KitTrayKind, which no longer
# existed) silently stops that module's gate cases; run this on the integration head before and after every merge.
#
#   native/tool/qa/compile_desktop_tests.sh [--print] [--modules ":a:b :c:d"]
#
# Finds every Gradle module under native/ that has desktop test sources (src/desktopTest, or src/commonTest in a
# Kotlin Multiplatform module, which compiles into the desktop test compilation) and runs
# `<module>:compileTestKotlinDesktop` for all of them in one `./gradlew --continue` (no Android SDK needed). It then
# checks that each module's task ran and lists the modules whose test sources do not compile.
#   --print      print the module list and the Gradle command, run nothing
#   --modules M  only these modules (space-separated Gradle paths)
# Environment: OC_GRADLE (default ./gradlew in native/), OC_GRADLE_ARGS (extra arguments).
# Exit: 0 every desktop test source set compiled, 1 at least one did not, 2 the run could not happen.
set -uo pipefail

native_dir="$(cd "$(dirname "$0")/../.." && pwd)"
print=0 only=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    --print) print=1; shift ;;
    --modules) [[ $# -ge 2 ]] || { echo "--modules needs a list" >&2; exit 2; }; only="$2"; shift 2 ;;
    *) echo "unknown argument $1 (see --help)" >&2; exit 2 ;;
  esac
done
cd "$native_dir" || exit 2

# A KMP module applies one of the oc.kmp.* convention plugins (OcKmpLibraryPlugin: jvm("desktop")).
modules=()
if [[ -n "$only" ]]; then
  read -r -a modules <<<"$only"
else
  while IFS= read -r build; do
    dir="${build%/build.gradle.kts}"
    # Same discovery as settings.gradle.kts: a direct child of a layer folder holding a build.gradle.kts.
    [[ "$dir" =~ ^(core|protocol|engine|ui|feature|android|tools)/[^/]+$ ]] || continue
    grep -qE 'id\("oc\.kmp\.' "$build" || continue
    [[ -d "$dir/src/desktopTest" || -d "$dir/src/commonTest" ]] || continue
    modules+=(":${dir//\//:}")
  done < <(find . -mindepth 3 -maxdepth 3 -name build.gradle.kts | sed 's|^\./||' | sort)
fi
[[ ${#modules[@]} -gt 0 ]] || { echo "compile desktop tests: no module with desktop test sources found" >&2; exit 2; }

tasks=()
for m in "${modules[@]}"; do tasks+=("$m:compileTestKotlinDesktop"); done
gradle="${OC_GRADLE:-./gradlew}"
# shellcheck disable=SC2206 # OC_GRADLE_ARGS is a word list on purpose
cmd=("$gradle" --continue --console=plain ${OC_GRADLE_ARGS:-} "${tasks[@]}")

if [[ $print -eq 1 ]]; then
  printf 'module\t%s\n' "${modules[@]}"
  echo "command: ${cmd[*]}"
  exit 0
fi

log_dir="$native_dir/build/qa"
mkdir -p "$log_dir" || exit 2
log="$log_dir/compile_desktop_tests.log"
echo "compile desktop tests: ${#modules[@]} modules (log build/qa/compile_desktop_tests.log)"
"${cmd[@]}" >"$log" 2>&1
rc=$?

failed=() missing=()
for m in "${modules[@]}"; do
  t="$m:compileTestKotlinDesktop"
  if grep -qF "Execution failed for task '$t'" "$log"; then
    failed+=("$m")
  elif ! grep -qE "^> Task ${t//./\\.}( |$)" "$log"; then
    missing+=("$m")
  fi
done

if [[ ${#failed[@]} -eq 0 && ${#missing[@]} -eq ${#modules[@]} ]]; then
  grep -E "What went wrong|^> |error:" "$log" | head -20
  echo "compile desktop tests: Gradle ran no compile task (exit $rc): could not run"
  exit 2
fi
for m in "${failed[@]}"; do
  echo "FAIL $m: desktop test sources do not compile"
  path="${m#:}"; path="${path//://}"
  grep -E "^e: .*/$path/" "$log" 2>/dev/null | sed "s|^e: .*/native/|  |" | head -5
done
for m in "${missing[@]}"; do echo "FAIL $m: compileTestKotlinDesktop did not run (no desktop target, or Gradle stopped early)"; done
if [[ ${#failed[@]} -gt 0 || ${#missing[@]} -gt 0 || $rc -ne 0 ]]; then
  echo "compile desktop tests: $((${#failed[@]} + ${#missing[@]})) of ${#modules[@]} modules failed (Gradle exit $rc)"
  exit 1
fi
echo "compile desktop tests: all ${#modules[@]} modules compiled"
exit 0
