#!/usr/bin/env bash
# detekt for the native modules (E02-S03, plan 03 check 11). Same family as the Flutter app's Kotlin check
# (tool/qa/kotlin_static_analysis.sh on master): the pinned detekt-cli 1.23.8 jar, checksum-checked, its active default
# rules plus native/config/detekt/detekt.yml, zero new findings. Syntax-based: no Gradle, no Android SDK, no classpath.
#
#   native/tool/qa/detekt.sh [<module>...] [--all] [--base <ref>] [--create-baseline] [--report-dir <dir>]
#
#   <module>           :core:common, core/common, build-logic ... (t1.sh passes its touched modules)
#   --all              every native module (the settings.gradle.kts layout, plus build-logic)
#   --base <ref>       the ref the ratchet compares with (default: $OC_BASE, native/main, origin/native/main)
#   --create-baseline  write native/config/detekt/baseline/<module>.xml from today's findings (the ratchet still runs
#                      first in t1, so a baseline that grows fails there; use it to shrink one or to adopt detekt)
#   --new-against <ref> the true ratchet (CI): run detekt with NO baseline on each module here and on the same module at <ref>
#                      (git archive), and fail only on findings that exist here and not there, compared by SIGNATURE
#                      (rule + declaration, without the file name or line), so a moved or renamed file keeps its old
#                      findings old. Prints TOTAL, new and old counts per module. The baselines are not read.
#   --report-dir <dir> reports (default: native/build/reports/detekt/<module>.{txt,xml})
#
# Frozen exceptions (--new-against only; 3.0.1, coordinator 2026-11-18): native/tool/ci/detekt_exceptions_301.tsv (or the
# file OC_DETEKT_EXCEPTIONS names) lists findings by rule + file + function (detekt's [name]). A module passes only when
# its new findings are exactly those listed for it; any other new finding fails, and a listed finding that no longer
# exists in the module fails until its line is removed (the list only shrinks). The count is printed with every run.
#
# Every run first checks the ratchet: no baseline may gain an entry compared with merge-base(<base>, HEAD), and no
# baseline may appear for a module that had none. Before the base carries native/config/detekt/baseline/ at all
# (the adoption commit) the ratchet reports "bootstrap" and passes. With no module and no --all only the ratchet runs.
#
# Environment: OC_DETEKT_CACHE_DIR (jar cache), OC_DETEKT_CMD (replaces "java -jar <jar>", for the self-tests).
# Exit: 0 pass, 1 findings or a growing baseline, 2 could not run (download, checksum, config, lock, no git).
set -uo pipefail
# shellcheck source=../lib/common.sh
source "$(dirname "$0")/../lib/common.sh"

version=1.23.8
jar_name="detekt-cli-${version}-all.jar"
jar_sha256=2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1
url="https://github.com/detekt/detekt/releases/download/v${version}/${jar_name}"
config="$OC_NATIVE/config/detekt/detekt.yml"
baseline_dir="$OC_NATIVE/config/detekt/baseline"

mods=() all=0 base_arg="" create=0 new_against="" report_dir="$OC_NATIVE/build/reports/detekt"
while (($#)); do
  case "$1" in
    --all) all=1; shift ;;
    --base) base_arg="${2:-}"; shift 2 ;;
    --create-baseline) create=1; shift ;;
    --new-against) new_against="${2:-}"; shift 2 ;;
    --report-dir) report_dir="${2:-}"; shift 2 ;;
    -h | --help) sed -n '2,20p' "$0"; exit 0 ;;
    -*) oc_usage "detekt.sh [<module>...] [--all] [--base R] [--create-baseline] [--report-dir D]" ;;
    *) mods+=("$1"); shift ;;
  esac
done

# Module id → source folder and baseline file. "build-logic" is the included build; everything else is a Gradle path.
module_dir() { if [[ "$1" == build-logic ]]; then echo "$OC_NATIVE/build-logic"; else oc_module_dir "$1"; fi; }
module_key() { local m="${1#:}"; echo "${m//:/-}"; }
all_modules() {
  local layer d top
  for layer in core protocol engine ui feature android tools; do
    for d in "$OC_NATIVE/$layer"/*/; do [[ -f "$d/build.gradle.kts" ]] && echo ":$layer:$(basename "$d")"; done
  done
  for top in androidApp desktopApp quality benchmark; do [[ -f "$OC_NATIVE/$top/build.gradle.kts" ]] && echo ":$top"; done
  [[ -d "$OC_NATIVE/build-logic" ]] && echo build-logic
}

if ((all)); then mapfile -t mods < <(all_modules); fi
norm=()
for m in "${mods[@]}"; do [[ "$m" == build-logic ]] && norm+=(build-logic) || norm+=("$(oc_norm_module "$m")"); done
mods=("${norm[@]}")

# --- ratchet: baselines only shrink (B-BUILD-023, the allowlist rule) -------------------------------------------------
ratchet() {
  local top base mb rel
  top="$(oc_repo_root)" || { echo "ratchet: could not run (needs git)"; return 2; }
  base="$(oc_resolve_base "$base_arg")" || { echo "ratchet: could not run (unknown base '${base_arg:-${OC_BASE:-native/main}}')"; return 2; }
  mb="$(git -C "$top" merge-base "$base" HEAD)" || { echo "ratchet: could not run (no merge-base with $base)"; return 2; }
  rel="${baseline_dir#"$top/"}"
  if [[ -z "$(git -C "$top" ls-tree -d --name-only "$mb" -- "$rel")" ]]; then
    echo "ratchet: bootstrap ($base has no $rel yet; this change adopts detekt)"
    return 0
  fi
  python3 -I - "$top" "$mb" "$rel" <<'PY'
import os, re, subprocess, sys
top, mb, rel = sys.argv[1:4]
ids = lambda text: set(re.findall(r"<ID>(.*?)</ID>", text, re.S))
def at_base(path):
    r = subprocess.run(["git", "-C", top, "show", f"{mb}:{path}"], capture_output=True, text=True)
    return ids(r.stdout) if r.returncode == 0 else None
grown = 0
here = os.path.join(top, rel)
for name in sorted(os.listdir(here)) if os.path.isdir(here) else []:
    if not name.endswith(".xml"):
        continue
    path = f"{rel}/{name}"
    with open(os.path.join(here, name), encoding="utf-8") as f:
        now = ids(f.read())
    before = at_base(path)
    if before is None:
        if now:
            grown += 1
            print(f"ratchet: NEW baseline {path} with {len(now)} entries (a module without one starts at zero findings)")
        continue
    added = sorted(now - before)
    if added:
        grown += 1
        print(f"ratchet: {path} GREW by {len(added)} (fix the findings; baselines only shrink):")
        for a in added[:20]:
            print(f"  + {a}")
    elif len(now) < len(before):
        print(f"ratchet: {path} shrank {len(before)} -> {len(now)}")
print("ratchet: " + ("FAIL" if grown else "ok") + f" (compared with {mb[:12]})")
sys.exit(1 if grown else 0)
PY
}

status=0
raise() { (($1 > status)) && status=$1; return 0; }

if ((create)); then
  echo "ratchet: not checked while creating baselines (t1 checks it)"
elif [[ -n "$new_against" ]]; then
  :
else
  ratchet; raise $?
fi
((${#mods[@]})) || { echo "detekt: no modules to analyse"; exit "$status"; }

# --- the pinned jar -----------------------------------------------------------------------------------------------
if [[ -n "${OC_DETEKT_CMD:-}" ]]; then
  read -r -a runner <<<"$OC_DETEKT_CMD"
else
  command -v java >/dev/null || { echo "detekt: could not run (no java)"; exit 2; }
  cache_dir="${OC_DETEKT_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/oc-mobile-tools}"
  mkdir -p "$cache_dir" || exit 2
  jar="$cache_dir/$jar_name"
  exec {dl}>"$cache_dir/.detekt-download.lock"
  flock "$dl"
  if [[ ! -f "$jar" ]]; then
    tmp="$(mktemp "$cache_dir/.detekt-download.XXXXXX")"
    if ! curl --fail --location --silent --show-error --retry 2 --connect-timeout 20 --max-time 180 \
      --proto '=https' --proto-redir '=https' "$url" -o "$tmp"; then
      rm -f "$tmp"; echo "detekt: could not run (download failed)"; exit 2
    fi
    if ! printf '%s  %s\n' "$jar_sha256" "$tmp" | sha256sum --check --status; then
      rm -f "$tmp"; echo "detekt: download checksum mismatch; analysis was not run"; exit 2
    fi
    mv "$tmp" "$jar"
  fi
  if ! printf '%s  %s\n' "$jar_sha256" "$jar" | sha256sum --check --status; then
    echo "detekt: cached jar checksum mismatch; remove $jar and retry"; exit 2
  fi
  flock -u "$dl"
  runner=(java -Xmx1g -jar "$jar")
fi

# --- the true ratchet: findings new against a ref, by signature -----------------------------------------------------
if [[ -n "$new_against" ]]; then
  top="$(oc_repo_root)" || { echo "detekt: could not run (needs git)"; exit 2; }
  git -C "$top" rev-parse --verify -q "$new_against^{commit}" >/dev/null || { echo "detekt: could not run (unknown ref '$new_against')"; exit 2; }
  # A branch is judged against where it left the ref (the ref itself may have moved on since).
  new_against="$(git -C "$top" merge-base "$new_against" HEAD 2>/dev/null || echo "$new_against")"
  mkdir -p "$report_dir" || exit 2
  exceptions="${OC_DETEKT_EXCEPTIONS:-$OC_NATIVE/tool/ci/detekt_exceptions_301.tsv}"
  if [[ -f "$exceptions" ]]; then
    echo "detekt: $(grep -cvE '^(#|[[:space:]]*$)' "$exceptions") frozen exceptions listed in ${exceptions#"$OC_NATIVE/"}"
  else
    exceptions=/dev/null; echo "detekt: no frozen exceptions file"
  fi
  snap="$(mktemp -d "${TMPDIR:-/tmp}/oc-detekt-base.XXXXXX")"; trap 'rm -rf "$snap"' EXIT
  lock="$(oc_build_lock_file)"
  scan() { # scan <dir> <base-path> <report.txt>: no baseline; 0 clean, 2 findings, anything else could not run
    local out rc
    rm -f "$3"
    out="$(oc_with_lock "$lock" "${runner[@]}" --input "$1" --excludes '**/build/**,**/.gradle/**' --config "$config" \
      --build-upon-default-config --max-issues 0 --jvm-target 17 --language-version 2.0 --base-path "$2" \
      --report "txt:$3" 2>&1)"; rc=$?
    [[ -f "$3" ]] || : >"$3"
    ((rc == 0 || rc == 2)) || { echo "detekt: could not run on $1 (exit $rc)"; tail -5 <<<"$out" | sed 's/^/  /'; return 2; }
    return 0
  }
  for m in "${mods[@]}"; do
    dir="$(module_dir "$m")" key="$(module_key "$m")"
    if [[ ! -d "$dir" ]]; then echo "detekt $m: no such module folder (skipped: deleted)"; continue; fi
    rel="${dir#"$top/"}"
    scan "$dir" "$OC_NATIVE" "$report_dir/$key.txt" || { raise 2; continue; }
    mkdir -p "$snap/$key/native"
    if git -C "$top" cat-file -e "$new_against:$rel" 2>/dev/null; then
      git -C "$top" archive "$new_against" -- "$rel" | tar -x -C "$snap/$key"
      scan "$snap/$key/$rel" "$snap/$key/native" "$snap/$key.txt" || { raise 2; continue; }
    else : >"$snap/$key.txt"; fi
    python3 -I - "$m" "$snap/$key.txt" "$report_dir/$key.txt" "${report_dir#"$OC_NATIVE/"}/$key.txt" \
      "$exceptions" "${dir#"$OC_NATIVE/"}/" <<'PY' || raise 1
import collections, re, sys
mod, base_txt, head_txt, shown, exc_path, prefix = sys.argv[1:7]
def read(path):
    out = []
    for line in open(path, encoding='utf-8', errors='replace'):
        m = re.match(r'([A-Za-z]+) - .*? at (\S+?):\d+:\d+ - Signature=(.*)$', line.rstrip('\n'))
        if not m:
            continue
        rule, file, sig = m.groups()
        base = file.rsplit('/', 1)[-1]
        out.append(((rule, sig.replace(base, '*')), line.rstrip('\n')))
    return out
old = collections.Counter(k for k, _ in read(base_txt))
head = read(head_txt)
seen = collections.Counter()
new = []
for k, line in head:
    seen[k] += 1
    if seen[k] > old[k]:
        new.append(line)
total = len(head)
# The frozen exceptions of this module: rule + file (under native/) + function, never a line number.
def where(line):
    m = re.match(r'([A-Za-z]+) - .*?\[([^\]]+)\] at (\S+?):\d+:\d+ - Signature=', line)
    if not m:
        return None
    rule, name, path = m.groups()
    return (rule, path.split('/native/', 1)[-1], name)
listed = collections.Counter()
for row in open(exc_path, encoding='utf-8'):
    cols = row.rstrip('\n').split('\t')
    if row.startswith('#') or len(cols) < 3 or not cols[1].startswith(prefix):
        continue
    listed[(cols[0], cols[1], cols[2])] += 1
present = collections.Counter(where(l) for _, l in head)
stale = [k for k in listed if present[k] < listed[k]]
budget = collections.Counter(listed)
unlisted = []
for line in new:
    k = where(line)
    if k is not None and budget[k] > 0:
        budget[k] -= 1
    else:
        unlisted.append(line)
excepted = len(new) - len(unlisted)
note = f'; {excepted} of {sum(listed.values())} frozen exceptions' if listed else ''
if not unlisted and not stale:
    print(f'detekt {mod}: ok (total {total}, new 0, old {total - len(new)}; signature ratchet vs base{note})')
    sys.exit(0)
bad = len(unlisted) + len(stale)
print(f'detekt {mod}: NEW FINDINGS (new {bad}, total {total}, old {total - len(new)}; report: {shown}{note})')
for line in unlisted[:20]:
    print('  ' + re.sub(r' at \S*/native/', ' at ', line)[:300])
for rule, path, name in stale:
    print(f'  StaleException - [{name}] {rule} at {path}: listed in the frozen exceptions but no longer found; remove it')
sys.exit(1)
PY
  done
  exit "$status"
fi

# --- one detekt run per module, each against its own baseline ---------------------------------------------------
mkdir -p "$report_dir" || exit 2
lock="$(oc_build_lock_file)"
for m in "${mods[@]}"; do
  dir="$(module_dir "$m")" key="$(module_key "$m")"
  baseline="$baseline_dir/$key.xml"
  if [[ ! -d "$dir" ]]; then echo "detekt $m: no such module folder (skipped: deleted)"; continue; fi
  if [[ -z "$(find "$dir" -type f \( -name '*.kt' -o -name '*.kts' \) ! -path '*/build/*' -print -quit)" ]]; then
    echo "detekt $m: no Kotlin sources"; continue
  fi
  args=(--input "$dir" --excludes '**/build/**,**/.gradle/**' --config "$config" --build-upon-default-config
    --max-issues 0 --jvm-target 17 --language-version 2.0 --base-path "$OC_NATIVE"
    --report "txt:$report_dir/$key.txt" --report "xml:$report_dir/$key.xml")
  if ((create)); then
    mkdir -p "$baseline_dir"
    args+=(--baseline "$baseline" --create-baseline)
  elif [[ -f "$baseline" ]]; then
    args+=(--baseline "$baseline")
  fi
  out="$(oc_with_lock "$lock" "${runner[@]}" "${args[@]}" 2>&1)"; rc=$?
  case "$rc" in
    0) if ((create)); then
         n=0; [[ -f "$baseline" ]] && n="$(grep -c '<ID>' "$baseline")"
         # A clean module keeps no baseline file at all.
         if ((n == 0)); then rm -f "$baseline"; echo "detekt $m: clean, no baseline"; else echo "detekt $m: baseline written ($n entries)"; fi
       else
         echo "detekt $m: ok"
       fi ;;
    2) echo "detekt $m: NEW FINDINGS (total $(grep -cE '^[A-Za-z]+ - ' "$report_dir/$key.txt" 2>/dev/null || echo 0); report: ${report_dir#"$OC_NATIVE/"}/$key.txt)"
       grep -E '^[A-Za-z]+ - ' "$report_dir/$key.txt" 2>/dev/null | head -20 | sed 's/^/  /'
       raise 1 ;;
    75) echo "detekt $m: could not run (build slot busy)"; raise 2 ;;
    *) echo "detekt $m: could not run (detekt exit $rc)"; tail -5 <<<"$out" | sed 's/^/  /'; raise 2 ;;
  esac
done
exit "$status"
