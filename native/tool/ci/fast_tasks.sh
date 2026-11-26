#!/usr/bin/env bash
# Prints the desktop-test Gradle tasks for the modules a change touches, plus their affected dependents, one per line,
# or the single word ALL when the whole suite should run. The fast lane (tool/ci/LANES.md) feeds this to its shards.
# Details go to stderr.
#
#   tool/ci/fast_tasks.sh <base-ref> [max-modules] [branch]
#
# The change is `git diff --name-only <base>...HEAD` (merge-base diff, committed changes only, like the PR view).
# Path -> module: native/<layer>/<name>/... is :<layer>:<name>; native/{androidApp,desktopApp,quality}/... likewise.
#
# Protected / integration / merge-proof branches (master, native/glass-next, native/next, native/screens-merge*,
# native/merge-proof-*, native/rf-merge-proof-*) always use conservative transitive selection and fall back to ALL when
# more than [max-modules] (default 30) modules are selected.
#
# Job branches avoid the 30-module ALL cliff on high-fanout hubs (:ui:kit, :ui:words, :ui:design, :ui:routes, :core:*):
#   * test-only edits inside a module select only that module (test sources cannot affect other modules);
#   * edits to a hub whose transitive fanout exceeds [max-modules] narrow its dependents by actual symbol / string-key
#     usage across dependent modules' sources (falling back conservatively to full transitive dependents whenever a build
#     file, non-Kotlin/XML resource, or unresolvable file changed);
#   * directly touching more than [max-modules] modules (e.g. a stale base) still returns ALL so base_check.sh catches it.
# Only paths under native/ count; the rest of the repo (Flutter app, docs) cannot affect these tests.
set -euo pipefail
base=${1:?base ref (for example origin/native/screens-merge41)}
max=${2:-30}
cd "$(dirname "$0")/../.."
branch=${3:-${OC_FAST_BRANCH:-${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)}}}

exec python3 -I - "$base" "$max" "$branch" <<'PY'
import fnmatch
import glob
import os
import re
import subprocess
import sys

base, max_mods, branch = sys.argv[1], int(sys.argv[2]), sys.argv[3]


def fail_all(reason):
    sys.stderr.write(f"fast_tasks: {reason} -> ALL\n")
    print("ALL")
    sys.exit(0)


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True)


if git("rev-parse", "--verify", "-q", f"{base}^{{commit}}").returncode != 0:
    fail_all(f"base {base} not found")

diff_proc = git("diff", "--name-only", f"{base}...HEAD", "--", ".")
if diff_proc.returncode != 0:
    fail_all("diff failed")

changed = []
for line in diff_proc.stdout.splitlines():
    if line.startswith("native/"):
        changed.append(line[len("native/"):])

if not changed:
    sys.stderr.write("fast_tasks: nothing under native/ changed\n")
    sys.exit(0)

PROTECTED_EXACT = {"master", "native/glass-next", "native/next", "HEAD", ""}
PROTECTED_GLOBS = ("native/screens-merge*", "native/merge-proof-*", "native/rf-merge-proof-*")
conservative = branch in PROTECTED_EXACT or any(fnmatch.fnmatchcase(branch, g) for g in PROTECTED_GLOBS)

BUILD_WIDE = (
    "build-logic/",
    "gradle/",
)
BUILD_WIDE_FILES = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "version.properties",
}
TEST_WIDE_FILES = {
    "tool/ci/goldens.gradle.kts",
    "tool/ci/quarantine.gradle.kts",
}
LAYER_PREFIXES = ("core/", "protocol/", "engine/", "ui/", "feature/", "android/", "tools/")
ROOT_MODULES = ("androidApp", "desktopApp", "quality", "benchmark")

hit_files = {}  # module -> list of native-relative paths
for p in changed:
    if p.startswith(BUILD_WIDE) or p in BUILD_WIDE_FILES:
        fail_all(f"build-wide file {p}")
    if p in TEST_WIDE_FILES:
        fail_all(f"test-wide file {p}")
    if p.startswith("fixtures/"):
        fail_all(f"fixtures changed ({p})")
    if p.startswith(LAYER_PREFIXES):
        parts = p.split("/")
        if len(parts) >= 2 and os.path.isfile(os.path.join(parts[0], parts[1], "build.gradle.kts")):
            mod = f":{parts[0]}:{parts[1]}"
            hit_files.setdefault(mod, []).append(p)
        else:
            fail_all(f"path {p} is under no module")
    elif any(p.startswith(rm + "/") for rm in ROOT_MODULES):
        mod = ":" + p.split("/", 1)[0]
        hit_files.setdefault(mod, []).append(p)

# Build the module dependency graph (exact match to build-logic OcKmpFeaturePlugin & OcKmpLibraryPlugin).
needs = {}  # module -> set of modules it depends on
mod_dirs = {}  # module -> relative directory under native/
build_files = sorted(
    glob.glob("*/*/build.gradle.kts")
    + [f for f in ("androidApp/build.gradle.kts", "desktopApp/build.gradle.kts", "quality/build.gradle.kts") if os.path.isfile(f)]
)
proj_rx = re.compile(r'project\(\s*"([a-zA-Z0-9:_-]+)"\s*\)')
for bf in build_files:
    d = bf[:-len("/build.gradle.kts")]
    me = ":" + d.replace("/", ":")
    mod_dirs[me] = d
    text = open(bf, errors="replace").read()
    deps = set(proj_rx.findall(text))
    if 'id("oc.kmp.feature")' in text:
        deps.update([":ui:kit", ":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common"])
    if 'id("oc.kmp.library")' in text and me != ":core:testing":
        deps.add(":core:testing")
    needs[me] = deps

rev_deps = {m: set() for m in needs}
for me, deps in needs.items():
    for dep in deps:
        rev_deps.setdefault(dep, set()).add(me)


def transitive_dependents(start):
    seen = set()
    stack = [start]
    while stack:
        cur = stack.pop()
        for nxt in rev_deps.get(cur, ()):
            if nxt != start and nxt not in seen:
                seen.add(nxt)
                stack.append(nxt)
    return seen


def is_test_only_path(mod_dir, rel_path):
    sub = rel_path[len(mod_dir) + 1:] if rel_path.startswith(mod_dir + "/") else rel_path
    if re.match(r"^src/[^/]*[Tt]est[^/]*/", sub):
        return True
    if sub == "flicker-baseline.tsv":
        return True
    return False


DECL_RX = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s+)*"
    r"(?:(private|internal|public|protected)\s+)?"
    r"(?:(?:inline|suspend|operator|infix|tailrec|external|actual|expect|override|open|abstract|final|sealed|data|value|enum|annotation|inner|const|lateinit)\s+)*"
    r"(?:class|interface|object|typealias|fun|val|var)\s+"
    r"(?:<[^>]+>\s*)?"
    r"(?:[A-Za-z0-9_.?*<>]+\.)?"
    r"([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)

XML_RES_RX = re.compile(r'<(?:string|plurals|string-array)\b[^>]*\bname="([A-Za-z0-9_.-]+)"')


def extract_kt_symbols(text):
    """Return (public_top_level_syms, internal_top_level_syms) for Kotlin source text."""
    pub, internal = set(), set()
    # Strip block comments roughly while preserving line structure
    cleaned = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    depth = 0
    for line in cleaned.splitlines():
        code = line.split("//", 1)[0]
        if depth == 0:
            m = DECL_RX.match(code.strip())
            if m:
                vis, name = m.group(1), m.group(2)
                if vis == "private":
                    pass
                elif vis == "internal":
                    internal.add(name)
                else:
                    pub.add(name)
        depth += code.count("{") - code.count("}")
        if depth < 0:
            depth = 0
    return pub, internal


def extract_xml_keys(rel_path):
    """Extract resource names added, removed, or modified in an XML file between base and HEAD."""
    keys = set()
    p = git("diff", "-U0", f"{base}...HEAD", "--", rel_path)
    if p.returncode != 0:
        return None
    # Track changed line ranges in HEAD and base to find enclosing <plurals>/<string-array> when <item> lines changed
    hunk_rx = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")
    new_lines, old_lines = [], []
    for line in p.stdout.splitlines():
        hm = hunk_rx.match(line)
        if hm:
            o_start, o_cnt = int(hm.group(1)), int(hm.group(2) or "1")
            n_start, n_cnt = int(hm.group(3)), int(hm.group(4) or "1")
            if o_cnt:
                old_lines.append((o_start, o_start + o_cnt - 1))
            if n_cnt:
                new_lines.append((n_start, n_start + n_cnt - 1))
            continue
        if line.startswith(("+++", "---")):
            continue
        if line.startswith(("+", "-")):
            for m in XML_RES_RX.finditer(line[1:]):
                keys.add(m.group(1))

    def scan_enclosing(lines_list, ranges):
        for s, e in ranges:
            for ln in range(s, min(e, len(lines_list)) + 1):
                for back in range(ln - 1, max(-1, ln - 40), -1):
                    txt = lines_list[back]
                    m = XML_RES_RX.search(txt)
                    if m:
                        keys.add(m.group(1))
                        break
                    if "</plurals>" in txt or "</string-array>" in txt or "</resources>" in txt:
                        break

    if os.path.isfile(rel_path) and new_lines:
        scan_enclosing(open(rel_path, errors="replace").read().splitlines(), new_lines)
    if old_lines:
        old_p = git("show", f"{base}:native/{rel_path}")
        if old_p.returncode == 0:
            scan_enclosing(old_p.stdout.splitlines(), old_lines)
    return keys if keys else None


def module_source_text_files(mod_dir):
    src = os.path.join(mod_dir, "src")
    if not os.path.isdir(src):
        return []
    out = []
    for root, _, files in os.walk(src):
        for f in files:
            if f.endswith((".kt", ".xml")):
                out.append(os.path.join(root, f))
    return out


def module_main_kt_files(mod_dir):
    src = os.path.join(mod_dir, "src")
    if not os.path.isdir(src):
        return []
    out = []
    for d in sorted(os.listdir(src)):
        if "Test" in d:
            continue
        sub = os.path.join(src, d)
        if not os.path.isdir(sub):
            continue
        for root, _, files in os.walk(sub):
            for f in files:
                if f.endswith(".kt"):
                    out.append(os.path.join(root, f))
    return out


def narrow_hub_dependents(mod, prod_files, candidates):
    """Return a narrowed set of dependent modules for hub `mod`, or None to fall back to all `candidates`."""
    mod_dir = mod_dirs.get(mod)
    if not mod_dir:
        return None
    symbols = set()
    internal_syms = set()
    for rel_path in prod_files:
        if rel_path == f"{mod_dir}/build.gradle.kts":
            return None
        if rel_path.endswith(".xml"):
            xml_keys = extract_xml_keys(rel_path)
            if not xml_keys:
                return None
            symbols.update(xml_keys)
        elif rel_path.endswith(".kt"):
            file_pub, file_int = set(), set()
            if os.path.isfile(rel_path):
                p, i = extract_kt_symbols(open(rel_path, errors="replace").read())
                file_pub.update(p)
                file_int.update(i)
            old_p = git("show", f"{base}:native/{rel_path}")
            if old_p.returncode == 0:
                p, i = extract_kt_symbols(old_p.stdout)
                file_pub.update(p)
                file_int.update(i)
            if not file_pub and not file_int:
                return None
            symbols.update(file_pub)
            internal_syms.update(file_int)
        else:
            return None

    # Propagate internal symbols 1 hop to public declarations within the same hub module.
    if internal_syms:
        int_rx = re.compile(r"\b(?:" + "|".join(re.escape(s) for s in sorted(internal_syms)) + r")\b")
        matched_internal = False
        for kt in module_main_kt_files(mod_dir):
            if kt in prod_files:
                continue
            txt = open(kt, errors="replace").read()
            if int_rx.search(txt):
                pub, _ = extract_kt_symbols(txt)
                if pub:
                    symbols.update(pub)
                    matched_internal = True
        if not symbols and not matched_internal:
            return None

    if not symbols:
        return None

    # Short/generic identifiers (<=2 chars) are too noisy; keep only real identifiers or fall back.
    usable = sorted(s for s in symbols if len(s) >= 3)
    if not usable:
        return None

    sym_rx = re.compile(r"\b(?:" + "|".join(re.escape(s) for s in usable) + r")\b")
    direct_hits = set()
    for cand in sorted(candidates):
        cdir = mod_dirs.get(cand)
        if not cdir:
            continue
        for f in module_source_text_files(cdir):
            if sym_rx.search(open(f, errors="replace").read()):
                direct_hits.add(cand)
                break

    # Include non-hub transitive dependents of each directly matched consumer.
    narrowed = set(direct_hits)
    for d in list(direct_hits):
        sub = transitive_dependents(d)
        if len(sub) <= max_mods:
            narrowed.update(sub)
    sys.stderr.write(
        f"fast_tasks: smart-narrow {mod} ({len(usable)} symbol/key(s)) -> {len(narrowed)}/{len(candidates)} dependent(s)\n"
    )
    return narrowed


sel = set(hit_files.keys())

if conservative:
    for m in list(hit_files.keys()):
        sel.update(transitive_dependents(m))
    if len(sel) > max_mods:
        fail_all(f"{len(sel)} modules selected (limit {max_mods}, conservative branch {branch or 'unknown'})")
else:
    if len(hit_files) > max_mods:
        fail_all(f"{len(hit_files)} modules directly changed (limit {max_mods})")
    for m, files in sorted(hit_files.items()):
        mdir = mod_dirs.get(m, "")
        prod_files = [f for f in files if not is_test_only_path(mdir, f)]
        if not prod_files:
            sys.stderr.write(f"fast_tasks: {m} test-only -> no dependents\n")
            continue
        if m == ":ui:words" and ":tools:strings" in needs:
            sel.add(":tools:strings")
        if m == ":tools:strings" and ":ui:words" in needs:
            sel.add(":ui:words")
        cand = transitive_dependents(m)
        if len(cand) <= max_mods:
            sel.update(cand)
        else:
            narrowed = narrow_hub_dependents(m, prod_files, cand)
            if narrowed is None:
                sys.stderr.write(f"fast_tasks: {m} cannot narrow safely -> full {len(cand)} dependent(s)\n")
                sel.update(cand)
            else:
                sel.update(narrowed)
    if len(sel) > max_mods * 2:
        fail_all(f"{len(sel)} modules selected after smart narrowing (limit {max_mods * 2})")

known = subprocess.check_output(["bash", "tool/ci/shard_tasks.sh", "0", "1"], text=True).splitlines()
out = [t for t in known if t.rsplit(":", 1)[0] in sel]
sys.stderr.write(f"fast_tasks: branch={branch or 'unknown'} mode={'conservative' if conservative else 'smart'}\n")
sys.stderr.write("fast_tasks: changed: " + " ".join(sorted(hit_files.keys())) + "\n")
sys.stderr.write(f"fast_tasks: selected {len(out)} test tasks (from {len(sel)} modules)\n")
for t in out:
    print(t)
PY
