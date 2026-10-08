# Full suite — 2026-10-08

Candidate: `feat/genui-fe` at `4338dad6d` (tree unchanged during the run).
Manifest: every `test/**/*_test.dart` file (1,179 files, nested goldens included), split into 16 chunks
and run one after another in the foreground, each under 10 minutes:
`tool/qa/machine_lock.sh test -- flutter test --concurrency 2 <chunk files>` with the pinned Flutter 3.47.1.

Result: **all 16 chunks passed — 17,349 tests passed, 20 skipped, 0 failed.**

## Run before this one

The same run on `aa3847d3` (1,176 files) found 38 failing tests in 11 files, all integration drift from
the day's merges. Fixed before this run:

| File | Cause | Fix |
|---|---|---|
| builtin_project_lifecycle_guard, perf_builtin_pollers | native authority status checks (BB runtime) | tests follow (sol/bd-suite-fix) |
| nudge_moments, perf_catalog_budget | startup inventory refresh (BD2) | fixtures follow (sol/ba-suite-fix) |
| chat_question_card (7) | debounced inventory refresh timer | pump past it (fe/suite-fix) |
| demo_isolation (3) | **real bug**: the isolated demo reached per-folder network reads | guard in feed_question_reads.dart (4338dad6) |
| language_picker | native locale refresh channel unmocked | mock oc/background (fe/suite-fix) |
| settings_golden about, shared_settings_1 language sheet | FG6 About / FG5 languages | goldens regenerated and viewed |
| chat_4, slice_p3_5 team goldens (16) | one-spinner "Hide steps" chip | goldens regenerated; pixel diff only in the chip |

Also fixed during the day: ARCH-2 (runtime name via ProfilePresentation), G26 (gen-l10n output excluded like
the generated SDK), two kotlinc fixture tests after the BB9 merge, and the UI ledger.

Not covered here: APK build, Kotlin JVM/Gradle tests, device checks (APK 2197 pending).
