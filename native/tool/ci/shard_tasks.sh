#!/usr/bin/env bash
# Prints the Gradle desktop-test tasks of shard <index> of <count> (0-based), one per line, dealt by WEIGHT: a module's
# weight is the number of lines containing @Test under its test source folders (src/*Test*/, at least 1); tasks are
# taken heaviest first (ties by name) and each goes to the shard whose total weight is lowest so far (ties to the
# lowest index). Computed from the tree at plan time, deterministic, every task in exactly one shard. Same discovery
# rule as tool/lib/common.sh oc_module_test_tasks: KMP modules run desktopTest, JVM modules and :desktopApp/:quality
# run test. Android host tests (Robolectric/Roborazzi goldens) are not run here.
#
# <count> 1 prints the whole list in the balanced dealing order of the workflow's default 8 module shards
# (plan_shards.sh keeps count=8 for every ALL lane; keep the two in step): the greedy plan under the class sizes a
# positional k % 8 dealing yields, laid out round-major (first task of every shard, then every second, ...), then
# :quality:test and :quality:secTest last, so the workflow's positional picker reproduces that plan. The unconstrained
# <index> <count> dealing may isolate one heavy task on its own shard; only this order carries the workflow's fixed
# position-to-shard shape. Smaller counts (2, 4) apply only to a fast lane's list mode, whose order comes from fast_tasks.sh.
# The workflow keeps :quality:secTest with :quality:test in one shard; never rotate it independently.
set -euo pipefail
idx=${1:?shard index (0-based)} count=${2:?shard count}
cd "$(dirname "$0")/../.."
{
  for layer in core protocol engine ui feature android tools; do
    for bf in "$layer"/*/build.gradle.kts; do
      [ -f "$bf" ] || continue
      m=":${bf%/build.gradle.kts}"; m="${m//\//:}"
      if grep -qE 'id\("oc\.kmp\.' "$bf"; then echo "$m:desktopTest"
      elif grep -qE 'id\("oc\.jvm\.library"\)|kotlin\("jvm"\)' "$bf"; then echo "$m:test"; fi
    done
  done
  for m in desktopApp quality; do [ -f "$m/build.gradle.kts" ] && echo ":$m:test"; done
  # The workflow keeps this with :quality:test in one shard; never rotate it independently.
  echo ":quality:secTest"
} | python3 -c '
import itertools, os, sys

idx, count = int(sys.argv[1]), int(sys.argv[2])
tasks = [line.rstrip("\n") for line in sys.stdin if line.strip()]


def weight(task):
    """A module weight: the number of lines containing @Test under its src/*Test*/ folders (at least 1)."""
    module = task[1:].rsplit(":", 1)[0].replace(":", "/")
    n = 0
    src = os.path.join(module, "src")
    if os.path.isdir(src):
        for d in sorted(os.listdir(src)):
            if "Test" in d:
                for root, _, files in os.walk(os.path.join(src, d)):
                    for f in files:
                        try:
                            with open(os.path.join(root, f), errors="replace") as fh:
                                n += sum(1 for line in fh if "@Test" in line)
                        except OSError:
                            pass
    return n or 1


weights = {t: weight(t) for t in tasks}


def deal(items, n):
    """Heaviest first (ties by name); each task to the shard whose total weight is lowest so far (lowest index)."""
    sums = [0] * n
    shards = [[] for _ in range(n)]
    for t in sorted(items, key=lambda t: (-weights[t], t)):
        lightest = min(range(n), key=lambda j: (sums[j], j))
        shards[lightest].append(t)
        sums[lightest] += weights[t]
    return shards


if count == 1 and idx == 0:
    # The whole list in the balanced dealing order for the default 8 module shards of the workflow: the greedy plan
    # under the class sizes a positional k % 8 dealing yields (13,13,...,12 for 102 module tasks), laid out
    # round-major (first task of every shard, then every second, ...), the quality pair last. native-ci.yml deals
    # list positions k to shard k % n, so round-major order makes the picker of the workflow reproduce that plan
    # with no workflow change. Smaller counts (2, 4) apply only to fast-lane list mode, whose order comes from
    # fast_tasks.sh.
    quality = [t for t in (":quality:test", ":quality:secTest") if t in tasks]
    items = [t for t in tasks if t not in quality]
    capacity = [-(-(len(items) - i) // 8) for i in range(8)]
    sums = [0] * 8
    plan = [[] for _ in range(8)]
    for t in sorted(items, key=lambda t: (-weights[t], t)):
        lightest = min((j for j in range(8) if len(plan[j]) < capacity[j]), key=lambda j: (sums[j], j))
        plan[lightest].append(t)
        sums[lightest] += weights[t]
    for round_tasks in itertools.zip_longest(*plan):
        for t in round_tasks:
            if t is not None:
                print(t)
    for t in quality:
        print(t)
elif 0 <= idx < count:
    for t in deal(tasks, count)[idx]:
        print(t)
' "$idx" "$count"
