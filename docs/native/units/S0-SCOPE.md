# Slice 0 · S0-SCOPE: the scope file and the catalog top-up

Date: 2026-11-01. Branch: `native/scope-2026-11-01`, cut from `claude/native-scope-testing-6emh5k` at `6627d3208`.
Write set: `native/catalog/**`, plus `ScopeFileTest` in `native/quality/**`; this report is the only file outside it.

**Result:**
- `native/catalog/scope.jsonl` gives all 1,650 rows a scope. That is 1,544 catalog rows plus 106 top-up rows.
- Every switch row has a slice and a module.
- Every row outside the switch has a decision and a reason.
- The 02 §3.6 amendments are in, plus the ones from plan 10 §20.3 and plan 11 §9.3.
- `ScopeFileTest` passes: 13 of 13 tests, including 11 planted-error checks.

**Shipping state:** the files are written, the test passes locally, and the work is committed and pushed to the
branch. There is no PR, no CI run and no merge. The test-writer lead has not reviewed the move-outs and the top-up
rows yet (plan 15 lists that review for S0-SCOPE and S0-TOPUP).

---

## 1. What was built

| Path | Content |
|---|---|
| `native/catalog/behaviors.jsonl` | Lines 1–1,544 are a byte copy of the plan catalog. Lines 1,545–1,650 are 106 top-up rows, each with `sources_commit`. |
| `native/catalog/behaviors.lock` | One `id<TAB>sha256(line)` per row, in file order. It is the append-only anchor that `ScopeFileTest` checks. |
| `native/catalog/scope.jsonl` | 1,650 lines with the 02 §3.6 fields. It is generated; do not edit it by hand. |
| `native/catalog/seed/seed_scope.py` | Plan 15 Appendix B, verbatim, then a module map, then `overrides.jsonl`. Flags: `--write` regenerates `scope.jsonl`; `--lock` appends to the lock and never rewrites it. |
| `native/catalog/seed/overrides.jsonl` | 652 hand decisions with `note`s, in four sections: (1) the per-row read; (2) the scope of each top-up row; (3) the amendments from the top-up; (4) the coordinator's decisions, which come last so they win. |
| `native/catalog/seed/topup_inputs.py` | Writes the three input lists for the top-up: uncited, new and changed tests. |
| `native/catalog/topup-notes.jsonl` | Records what happened to every top-up input test: it became a row, an existing row covers it, it amends a row, or it was dropped (with the reason). |
| `native/catalog/README.md` | What each file is, and who edits it how. |
| `native/quality/src/test/kotlin/.../ScopeFileTest.kt` | The gate. It has a pure `ScopeFile.problems()` checker and a small strict JSON reader, because `:quality` has no JSON library on its test compile classpath. |

### 1.1 Method

1. **Seed.** Appendix B reproduces plan 15 §14.1 exactly: S0 12, S1 132, S2 133, S3 342, S4 220, S5 402, S6 18,
   AFTER 206, DROP 50, DESKTOP 12, IOS 2, MANUAL 15. A module map then gives each row a module, first from its source
   file name and otherwise from its area.
2. **Read every row by its text** (02 §3.3 rules 1–5).
   - Four readers split the rows by area, and each read every row in its share: AGENT/MEDIA/XPLAT/BUILD (399 rows),
     LINUX/ANDR (372), SEC/STATE/MIGR (354) and NAV/CHAT/PROTO/KIT/L10N (419).
   - They emitted a line for every row outside the switch, every MANUAL row, every slice or module change, and every
     mixed row.
   - I resolved conflicts in section 4 of the overrides file.
3. **Check the result against the plan.**
   - Every switch row's (slice, module) pair has an owning plan-15 unit in that slice. This was checked by script
     against the unit write sets; the one flag was S0-SKEL's brace-list write set, which does own `:androidApp`.
   - No MANUAL rows remain.
4. **Top-up.** Three readers read the test bodies of all 659 input tests. Every test is accounted for exactly once,
   which was checked by script.

## 2. Results

### 2.1 Scope counts

| | switch | after-switch | dropped | desktop | ios | total |
|---|---|---|---|---|---|---|
| Catalog rows (1,544) | **1,299** | 207 | 28 | 8 | 2 | 1,544 |
| Top-up rows | 92 | 7 | 6 | 1 | 0 | 106 |
| **All** | **1,391** | 214 | 34 | 9 | 2 | **1,650** |

Switch rows by slice (all rows): S0 13 · S1 193 · S2 142 · S3 364 · S4 221 · S5 436 · S6 22. Of the switch rows, 223
are `portable_e2e`.

**Against the estimates.** The 1,299 catalog rows in the switch compare with:
- Appendix B's 1,259 + 15 MANUAL;
- 02 §3.3's 1,336 [E].

The differences come from the per-row read:
- rows moved back into the switch by rules 3 and 4;
- desktop rows that are really the updater (B-BUILD-010…012) or the release tag (B-BUILD-007);
- AI Team and agent-browser rows that moved out.

Decisions on the 259 rows outside the switch: D6 204 (AI Team, Gas City, phone engine, FactsBook, the Gemma
assistant), NG8 12, D14 9, NG22 6, S02-3 8, D11 5, N4-O4 4, D7 4, D17 3, D13 2, NG17 1, and Flutter-only tooling 1.

### 2.2 Amendments: 74 rows

- **02 §3.6 day-1 amendments:**
  - B-MIGR-004, B-MIGR-010 and B-MIGR-012;
  - B-ANDR-068 (AppUpdater);
  - B-NAV-058, B-CHAT-122, B-AGENT-229, B-PROTO-041, B-LINUX-124 and B-BUILD-004 (the OpenCode 1 clauses are
    dropped);
  - B-KIT-018 (D10 frost);
  - B-KIT-020 and B-KIT-047 move after the switch (D11);
  - the rule-3 rows B-STATE-020, B-STATE-145, B-SEC-075, B-SEC-176, B-AGENT-193, B-AGENT-301 and B-AGENT-302 (all S1
    `:engine:profiles`);
  - B-SEC-063 (S02-7, S6 `:android:migration`).
- **Not amended, as 02 §3.6 says** (all in the switch): B-LINUX-077, 107, 110, 194, 197, 230 and 241, and B-MIGR-002.
  One reader had amended B-LINUX-107; I removed that amendment.
- **Plan 10 §20.3:** B-MEDIA-025, 007, 008, 031 and 032.
- **Plan 11 §9.3 (D11-2 reply watch):** B-ANDR-013.
- **More rule-3 rows the read found:** B-STATE-058, B-STATE-061, B-NAV-061 and B-AGENT-311.
- **Rule-5 mixed rows**, where only the AI Team, Termux-host, OpenCode 1, desktop or live-background clause is
  dropped. There are 31: B-ANDR-083/107/118, B-LINUX-008/043/142/179/183/205/221, B-STATE-025,
  B-SEC-082/091, B-PROTO-089…093, B-NAV-048/059/081, B-CHAT-111, B-L10N-021, B-KIT-002/008, B-AGENT-109/139/222/225
  and B-BUILD-005/006.
- **15 freeze-tip amendments from the top-up (§3):**
  - B-AGENT-012, B-AGENT-121;
  - B-SEC-097, B-SEC-099, B-SEC-119;
  - B-PROTO-071;
  - B-LINUX-102, 112, 126, 132, 152 and 177;
  - B-STATE-060;
  - B-L10N-020;
  - B-NAV-058.

### 2.3 Catalog row ids covered

The scope file covers every id in `behaviors.jsonl`:
- the original rows: B-NAV-001…082, B-CHAT-001…124, B-AGENT-001…318, B-PROTO-001…127, B-STATE-001…158,
  B-SEC-001…183, B-ANDR-001…121, B-LINUX-001…251, B-MEDIA-001…034, B-KIT-001…062, B-L10N-001…024, B-XPLAT-001…013,
  B-BUILD-001…034 and B-MIGR-001…013;
- the top-up rows in §3.

`ScopeFileTest` proves the 02 §3.6 rules over all of them. It is not named after a catalog row because it proves none:
it is a gate (plan 14 §6.5 table).

## 3. Top-up (02 §3.5)

Three input lists came from `topup_inputs.py`.

| Input | Tests | What it is |
|---|---|---|
| uncited | 250 in 57 files (28 files wholly uncited) | In the catalog input at `65e3f70a3`, but no row cites them |
| new | 339 in 64 files | A logic test name at the freeze tip that is not in the catalog input: 300 in 44 new files, 39 in 20 old files |
| changed | 70 in 44 files | Same name, but the body differs from `e44b581aa` |

Results (317 + 339 tests; 3 tests were in both the uncited and the changed list):
- **106 new rows.** Ids by area: B-NAV-083…086, B-CHAT-125…126, B-AGENT-319…325, B-PROTO-128…141,
  B-STATE-159…164, B-SEC-184…193, B-ANDR-122…130, B-LINUX-252…283, B-MEDIA-036…044, B-KIT-063…067,
  B-XPLAT-014…017, B-BUILD-035…036 and B-MIGR-014…015.
  - B-MEDIA-036 is Wi-Fi share path safety and B-MEDIA-037 is opening a phone file / installing an `.apk`. Plan 10
    §20.3 reserved both.
  - **B-MEDIA-035 (clipboard image paste) stays reserved and unused.** All six tests in `composer_paste_image_test.dart`
    are widget tests (NG12). The Kotlin `ClipboardImagePolicyTest` moves under the twin rule.
- **78 `covered` notes**, where an existing row already states the behaviour.
- **17 `amend` notes**, merged into the 15 amendments listed in §2.2.
- **22 `dropped` notes** (31 tests). They are:
  - the Flutter golden harness and goldens;
  - the analyzer suppressions ledger;
  - the Dart file-size ratchet;
  - the `flutter_animate` ban;
  - the widget-name catalogue;
  - Dart test seams and implementation details;
  - the code-push version-skew fallback (NG17);
  - three extractor false hits.
- **Freeze-tip features:**
  - in the switch: the updater, process budget, background CPU and agent priority, services, vitals, backup and
    restore, tested pins and idle updates, What's new;
  - after the switch: the Gemma assistant (5 rows, S02-3), AI Team role voices;
  - dropped: browser mode (D17); the Claude Agent trial (S02-3, D8, which says it is not in the product); the
    Shorebird patch state (NG17);
  - B-MEDIA-040 deletes the Gemma model folder. It stays in the switch under rule 3, matching 02 §3.7 item 5.
- **The fq3 tests** (`tool/qa/fq3` harness) became the OpenCode 2 live-proof rows B-PROTO-128…139 and B-SEC-184
  (S1 `:protocol:oc2`) and the certification-evidence rows B-MIGR-014/015 and B-BUILD-036 (S6 `:androidApp`).

## 4. Commands and results

Toolchain: JDK 21.0.12 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`), Gradle wrapper 9.7.1.

Android SDK, installed first as the brief said:
- `commandlinetools-linux-16111833_latest.zip` from dl.google.com, unpacked to `/opt/android-sdk/cmdline-tools/latest`;
- `yes | sdkmanager --licenses`;
- `sdkmanager "platform-tools" "platforms;android-37.1" "build-tools;37.0.0"`;
- `native/local.properties` sets `sdk.dir` and is gitignored.

Maven Central answered **429** through the sandbox proxy, as in the P1 report. A container-only
`~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo) does three things:
- adds Google's Central mirror, `google()` and the plugin portal to plugin management;
- swaps `repo.maven.apache.org` for the mirror in the settings repositories;
- adds no project repositories (`FAIL_ON_PROJECT_REPOS`).

| # | Command (from `native/` unless stated) | Result |
|---|---|---|
| 1 | `python3 -I /tmp/appB.py docs/.../catalog/behaviors.jsonl` (Appendix B as printed) | Matches §14.1 exactly |
| 2 | `./gradlew :quality:test --tests '*ScopeFileTest*'` with the seed only (red first) | **FAILED**: 2 of 13. `the committed scope file has no problems` failed (15 MANUAL rows had no slice). The planted amendment check failed because no amended row existed yet. |
| 3 | `python3 -I native/catalog/seed/seed_scope.py native/catalog/behaviors.jsonl --write` and `--lock` (repo root) | 1,650 rows; no MANUAL rows left |
| 4 | `python3 -I native/catalog/seed/topup_inputs.py /tmp/scope/verify` (repo root) | uncited 250, new 339, changed 70 |
| 5 | `./gradlew :quality:test --tests '*ScopeFileTest*' --rerun` | **BUILD SUCCESSFUL**; 13 tests, 0 failures |
| 6 | `./gradlew :quality:test --rerun` | 30 tests: `ScopeFileTest` 13/13, Konsist 11/11, KitOnlyPlanted 2/2, GraphSmoke 2/2, DesktopShellUi 1/1. **ModuleOwnersTest 0/1, which also fails without my change** (§7.1). |
| 7 | Privacy grep over `catalog/` and the test: private IPv4 ranges and e-mail pattern | One hit: the `100.64.0.0/10` range inside an original frozen row (B-SEC address rule). It is a rule, not a host. No e-mails. |

Resolved versions: the existing catalog's versions, unchanged. The JUnit/kotlin.test setup comes from `oc.jvm.library`.
`ScopeFileTest` adds no libraries.

## 5. Deviations from the plan

1. **Where the seed script lives.** Appendix B names `native/tool/plan/seed_scope.py`, but `native/tool/**` belongs to
   S0-GATES, so the script is in `native/catalog/seed/`.
2. **`65e3f70a3` (batch1) is not in this repository.**
   - The repo has `e44b581aa` (`v2.1.0+57`) and a snapshot of the freeze tip `fc5c1e78f` (`580803d47`).
   - "Added since 65e3f70a3" was measured by test name against `catalog-input/`, which was taken at batch1.
   - "Changed" was measured by body against `e44b581aa`. That over-approximates, and it misses tests changed between
     batch1 and the tag whose name was kept. A renamed test shows up as new.
   - Top-up rows cite working-tree lines with `"sources_commit": "fc5c1e78f"`.
3. **Who handed out the top-up ids.** 02 §3.6 gives this to the test-writer lead. This unit assigned them (the
   brief's top-up), so they need the lead's review before slice 1 closes.
4. **Rule-3 sweep rows go in S1 `:engine:profiles`, not S5.**
   - Appendix B puts `profile_deletion` rows in S5, but no slice-5 unit owns `:engine:profiles`.
   - S1-ENG owns the backwards deletion transaction and S1-DATA owns the `ProfileScope` sweep.
   - 02 §3.6's own B-STATE-020 example uses slice 1.
5. **Extra decision codes.** NG22 is used for the agent browser (B-AGENT-037/152, B-SEC-019/020/021/112, all moved
   after the switch). N4-O4 (plan 04, approved in plan 15 §17) is used for session-address links. Neither is in
   02 §3.3's list of D6, D7, D17 and NG8.

## 6. Catalog requests

- **Make `catalog/**` a test input of `:quality:test`.** Today the task input is only `**/src/**/*.kt`,
  `**/build.gradle.kts` and `MODULE_OWNERS`, so a change to only `catalog/*.jsonl` leaves the task up to date. Add
  `"catalog/**"` and the frozen plan copy to `inputs.files`. Until then, run it with `--rerun`.
- **No libraries requested.** `ScopeFileTest` parses JSON itself, because `kotlinx-serialization-json` is only an
  `implementation` dependency of the KMP modules and not on `:quality`'s test compile classpath. A
  `testImplementation(libs.kotlinx.serialization.json)` line would let the test use it.

## 7. Open problems

1. **`ModuleOwnersTest` fails on the base branch.** It still fails with my changes stashed:
   `:tools:arb2xml` and `:tools:flutter-migration-readers` (P6, P5) have no rows in `native/MODULE_OWNERS`. The owner
   of that file (S0-SKEL) needs to add them.
2. **S-18 against its row.** Plan 02 S-18 says the foreground reconnect pace is 0.5 s doubling to 16 s, but
   `test/reconnect_backoff_test.dart` asserts 1 s doubling to 16 s. B-PROTO-141 states what the test asserts. Either
   the plan or the row needs an amendment before S1-NET starts.
3. **Thermal rows (B-ANDR-083…092).** The plans disagree:
   - plan 11 keeps `ThermalPolicy` and the heat hold for services in the switch;
   - plan 13 says these rows "return with AI Team".

   The rows that only concern the team (087, 089, 091, 092) moved after the switch under D6. 083 is amended, and the
   rest stay in the switch.
4. **B-LINUX-169** stays after the switch under D6: its text is AI Team engine readiness. 02 §3.6 lists it among the
   NG1 rows to recheck, so the recheck has nothing to do in the switch.
5. **Live-proof and certification rows** (B-PROTO-128…139, B-MIGR-014/015, B-BUILD-036) come from QA-harness tests,
   not app code. They are placed where the plan runs the native live proof (S1 `:protocol:oc2`) and the release
   certification (S6 `:androidApp`). The test-writer lead may prefer to put them under `:quality` or under fixtures.
6. **Calls to review when a slice starts:**
   - B-NAV-038/039 and B-L10N-020: the tips registry is in S1 `:engine:settings`, but their triggers belong to S4.
   - B-AGENT-030 (undo of an automatic action) stays in the switch, although S02-8 stops the "While you were away"
     recording.
   - B-ANDR-007/008: there is no unit for keep-alive advice in `:core:domain`.
7. **Not done:** the widget tests (NG12, owner: later) and the Kotlin tests (the twin rule, 02 §5.6) are out of this
   unit. The top-up up to `flutter-final` (S0-TOPUP) follows when that tag exists.
