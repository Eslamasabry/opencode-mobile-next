# native/tool: the check ladder and the push gates (S0-GATES)

Every script follows the plan 02 §8 contract: exit `0` pass, `1` a test or gate failed, `2` the run could not happen
(never reported as a pass). The ladder scripts write `native/build/native-t<N>/<id>/summary.txt` and then a done-file
`exit-code`, and print the summary's last 20 lines. `-h` prints each script's usage.

| Script | Who, when | What |
|---|---|---|
| `t0.sh <module> [filter] [--android] [--vps]` | builder, while editing (foreground) | one module's `desktopTest` (or Android host test), filtered by a catalog id or name part |
| `t1.sh [--base R] [--brief F \| --globs G] [--record M] [--where vps\|local] [--detach]` | builder, once when ready | touched modules + write set on the PC; their tests, Roborazzi verify, `:quality:test`, strings drift, detekt (when it exists) |
| `t2.sh [--where vps\|local] [--steps a,b] [--detach]` | coordinator, checkpoints | the steps in `ladder/t2.tasks`; a missing gate task is a failure |
| `t3.sh <apk> --signer <sha256> [--flows L] [--upgrade <apk>] [--detach]` | emulator lane holder | versionCode and signer, emulator lock, flows (animations off), E2E-05 with animations on, instrumented classes, S-28 sweep, upgrade journey |
| `t4.sh --apks <dir> [--journeys L] [--detach]` | performance lane holder | quiet-PC checks, `restore_device.sh`, Macrobenchmark journeys from `tool/perf/journeys.tsv`, `compare.py` |
| `vps_gradle.sh [--unit U] -- <gradle args>` | the ladder | rsync to `~/wt/ocn-<U>` on `$OC_VPS_HOST`, Gradle under the VPS lock, reports pulled back |
| `check_write_set.sh <brief.md> \| --globs G` | builder before reporting; coordinator before merging | every path changed since the merge-base is inside the write set |
| `check_commits.sh [<base> [<head>]]` | before a push or merge | plan 02 §9.1 message rules |
| `twin_check.sh [--kind K] [--verify-copy]` | coordinator, weekly | `TWINS.md` rows vs the Flutter tree: SHAs, paths, drift commits, byte equality |
| `scan_push.sh [--push <remote> <refspec>]` | before every push | blocks key material, tokens, watched/personal e-mail, private addresses and hosts, forbidden files, non-noreply authors |
| `snapshot_push.sh [--dry-run] \| --release ...` | coordinator | tree-equal snapshot commit with the no-reply identity, scanned, then pushed (plan 15 §11.0) |
| `test/run_tests.sh` | after editing any of the above | planted-violation self-tests (no Gradle, no network) |

Environment: `OC_BASE` (default `native/main`), `OC_VPS_HOST` (never committed), `OC_BUILD_LOCK_FILE`,
`OC_EMULATOR_LOCK`, `OC_LOCK_WAIT`, `OC_MIN_FREE_MB`, `OC_T2_MIN_DISK_GB`, `OC_SCAN_EMAILS` and
`~/.config/oc/scan_push.local` (private names and patterns, never committed), `OC_SNAPSHOT_IDENT`, `OC_OWNER_GO`.

`scan_push.allow` takes exact values (`<rule> <value>  # reason`) and reviewed fixture files
(`@path <glob> <rule|*>  # reason`). Every line needs a reason; only the coordinator adds lines for other units' files.
