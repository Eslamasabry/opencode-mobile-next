# Unit S1-WORDS: all words converted once (report)

Branch `native/words-2026-11-01`, from `claude/native-strings-conversion-oh7ee5` at `6627d320`. Date: 2026-11-01.
Plan: `plan/06` §13, roadmap row S1-WORDS, `docs/native/slice0/P6-strings.md`. Write set: `native/tools/strings/**`,
`native/ui/words/**` (plus this report).

Finish line (roadmap): *All 7 languages are converted once into `strings_<feature>.xml`, `strings_os` is generated
into Android res, the drift check is green, and `convert-report.md` (unused keys) is reviewed.*

Status: **implemented, verified locally, committed, pushed.** The first three parts are done and green. The last part, the owner's review of the unused keys, is still to come: the 130 keys are listed in the report.

## What was built

### `:tools:strings` (promoted from `native/tools/arb2xml`)

- **The move.** The P6 tool moved into the main build as `:tools:strings`. It uses the `oc.jvm.library` convention,
  takes `kotlinx-serialization-json` from the catalog, and is a Gradle `application`. Its standalone wrapper,
  `settings.gradle.kts` and the 3.4 MB `build-sample/` are deleted, and the package is renamed to
  `io.github.eslamasabry.oc.tools.strings`. The `native/tools/arb2xml` folder is gone.
- **CLI.** Every path defaults from `--repo`, and each one can be overridden (see `strings --help`).
  - `strings convert --repo ..`
  - `strings check --repo ..`
- **Hand recipes** (`hand-recipes.tsv`, `Hand.kt`). These rewrite the 13 keys (38 messages) that CMP XML cannot
  hold. The rewrite is applied to every language *before* conversion. The output is plain ICU, which the converter
  already handles, so the converter still does the plural categories (Arabic gets all six), the `__eqN` strings, the
  escaping and the argument numbering. The drift check can redo the rewrite. A refused message with no recipe still
  fails the run.
- **`HandWords.kt`** (generated into `:ui:words`). This is the code half of the hand conversions:
  - for a select, an exhaustive `when` (any other value gives `other`, as ICU does);
  - for a split message, `suspend` and `@Composable` composers that check `__eqN` first and then the plural.
- **Android strings** (`AndroidStrings.kt`). The Flutter app's native Android strings come from 6 files in
  `android/app/src/main/res/values{,-ar}`: 222 strings and 5 plurals per language. They are read with aapt2's rules
  (escapes, quotes, collapsed whitespace) and merged into `strings_os.xml` with **their names kept**. A name clash
  stops the run.
- **`strings_os` in Android res.** Each `strings_os.xml` is also written, re-escaped for aapt2, to
  `native/ui/words/android-res/values*/`. In a string with arguments, a literal `%` becomes `%%` there. This made the
  P6 `OS_PERCENT_CONFLICT` refusal unnecessary, so it was removed.
- **Copy added after the conversion is kept.** A rerun keeps any `<string>`/`<plurals>` in `:ui:words` whose name no
  source produces. Examples are the P1 `chats_*` strings that `:feature:chats` uses. A rerun with no source change
  writes byte-identical files.
- **The drift check** (`Drift.kt`, `strings check`, and the `DriftTest` real-repo test). It covers plan 06 §13.8
  items 1–4 and 6.
  - **ARB and Android strings against the XML.** It converts the sources again in memory and reports four kinds of
    difference: missing, changed, wrong shape, and stale names. Which feature file holds a key does not matter.
  - **The XML on its own.** It reports:
    - duplicate names;
    - orphans;
    - a placeholder index or type the key does not have;
    - plural categories that are not exactly the language's own;
    - a missing `__eqN`;
    - raw invisible or bidi characters;
    - copy added after the conversion that has no Arabic.
  - **Android res against CMP.** The `android-res` copy must say exactly what the CMP `strings_os.xml` says.

### `:ui:words`

- `src/commonMain/composeResources/values{,-ar,-es,-ja,-pt,-ru,-zh}/strings_<feature>.xml`: 111 files. There are 21
  feature files (the 20 of plan §13.2 plus `strings_usage.xml`); `strings_team.xml` and `strings_factsbook.xml` are
  converted now and used after the switch (D6).
- `android-res/values{,-ar,-es,-zh}/strings_os.xml`: the generated Android copy, with a README.
- `src/commonMain/kotlin/…/words/HandWords.kt`: generated.
- `src/desktopTest/…/WordsTest.kt`: CMP reads the result back.

### Counts (ARB files as of `6627d320`)

`in = converted + unused + not convertible + orphans` holds in every language. The keys are counted as ARB keys.

| lang | in | converted (shipped) | unused (not shipped) | not convertible | orphans dropped | missing vs en |
|---|---:|---:|---:|---:|---:|---:|
| en | 9,837 | 9,707 | 130 | **0** | 0 | 0 |
| ar | 10,855 | 9,707 | 130 | **0** | 1,018 | 2* |
| es | 2,695 | 2,685 | 10 | 0 | 0 | 7,142 |
| ja | 661 | 658 | 3 | 0 | 0 | 9,176 |
| pt | 661 | 658 | 3 | 0 | 0 | 9,176 |
| ru | 661 | 658 | 3 | 0 | 0 | 9,176 |
| zh | 8,654 | 8,530 | 124 | 0 | 0 | 1,183 |

\* The 2 are by design: `teamProjectGoalStatus(Draft)` in Arabic writes its counts as numbers, so it has no plural
pieces (see below).

Per feature file and language, see `native/tools/strings/report/convert-report.md`. English keys per file:
team 1,546 · chat 977 · phone 915 · project 841 · settings 676 · common 666 · factsbook 544 · kit 520 · servers 475
· agents 461 · chat_pages 381 · composer 342 · onboarding 316 · chats 303 · usage 211 · about 164 · search 106 ·
failures 96 · schedules 70 · os 62 (+ 227 Android names) · widget_panels 35.

## The 38 hand-converted messages

These were rewritten as close to the ICU meaning as CMP allows. Each one is listed message by message (source ICU
and every resource it became) in `report/convert-report.md` § "Hand-converted". The recipes and their reasons are in
`native/tools/strings/hand-recipes.tsv`.

**Select → one string per branch: 6 keys, 15 messages.** The text around the select is copied into each branch. A
language without a branch reads its own `other`. The select argument is no longer a format argument; the other
arguments keep one numbering across all branches. `HandWords.<key>(value)` returns the `StringResource`.

| key | languages | branches |
|---|---|---|
| `kitAgentLabel` | en, ar, es, zh | `yes`, `other` |
| `activeContextMessageTitle` | en, ar, zh | `user`, `assistant`, `system`, `synthetic`, `skill`, `shell`, `compaction`, `change`, `other` |
| `runningPhoneGroup` | en, ar | 10 kinds + `other` |
| `runningPhoneName` | en, ar | 19 kinds + `other` |
| `runningPhoneCpu` | en, ar | `idle`, `light`, `busy`, `other` (each keeps ` · %1$s%`) |
| `runningPhoneAbout` | en, ar | 10 kinds + `other` |

**Several plurals → a sentence plus one `<plurals>` per count: 7 keys, 23 messages.**

- `<key>` keeps the sentence, with `%N$s` where each plural was. Each plural becomes `<key>__<count>`, plus its
  `__eqN` strings.
- The ICU meaning is kept exactly, because in ICU the sentence never changed with the counts; only the pieces did.
- `HandWords.<key>(…)` (suspend) and `HandWords.<key>Text(…)` (`@Composable`) compose the result.

| key | languages | pieces | note |
|---|---|---|---|
| `e7SettingsStorageSummary` | en, ar, es, zh | `queued`, `drafts` (=0/1/2 from ar) | |
| `e7SetupDeleteDisclosure` | en, ar, zh | `queued`, `drafts` | `=0{}` stays empty. Unused key: not shipped, no `HandWords` entry. |
| `chatAttachmentSheetAttached` | en, ar, es, zh | `sheets`, `rows` | |
| `teamProjectGoalStatus` | en, ar, zh | `milestones`, `repos` | Arabic has no plurals ("عدد …: N"). Its sentence takes `%4$d`/`%5$d` (the raw counts), so it has no pieces. |
| `teamProjectGoalStatusDraft` | en, ar, zh | `milestones`, `repos` | As above. |
| `teamProjectBoardSummary` | en, ar, zh | `tasks`, `milestones` | Chinese puts the milestones first (`%2$s中共 %1$s`). |
| `teamProjectPlanSummary` | en, ar, zh | `phases`, `tasks`, `repos` | |

**Where this is not identical to ICU.** The text on screen is identical in every case. Callers change in two ways:

- A select call passes the selector to `HandWords` instead of as a format argument.
- A split call goes through `HandWords` instead of a single `pluralStringResource`.

## Catalog rows covered

All tests are named after their row. These rows are covered at the converter and resource level; the runtime
language switch is P6's on-device part, which is not done here.

- **B-L10N-010** (translated messages read in their language; a missing one reads English; plurals follow each
  language's rules; the catalogue is complete):
  - `HandTest` (7 tests);
  - `DriftTest`: freshly converted output is clean, and the committed `:ui:words` matches the ARB files and the
    Android strings;
  - `WordsTest` (4, CMP itself):
    - Arabic picks zero/one/two/few/many/other for 0/1/2/3/11/100, which settles plan §13.4 [U];
    - a key Arabic lacks reads English;
    - Japanese falls back to English;
    - hand-converted selects and plurals compose correctly in en, ar and zh.
- **B-L10N-013** (native notification and widget resources cover English and Arabic):
  - `AndroidStringsTest` (2 named tests): native strings are read per language and merged into `strings_os`;
  - `DriftTest`: the Android res copy matches the CMP copy;
  - the report: Arabic lacks 0 of the 227 native names;
  - `aapt2 dump` of the APK (below).

## Commands and results

Environment:

- Android SDK set up as briefed: `commandlinetools-linux-16111833` in `/opt/android-sdk/cmdline-tools/latest`,
  licences accepted, `platform-tools`, `platforms;android-37.1` and `build-tools;37.0.0`.
- `JAVA_HOME` set to JDK 21.
- `native/local.properties` (gitignored) holds `sdk.dir`.

All commands were run from `native/`:

| Command | Result |
|---|---|
| `./gradlew :tools:strings:test` | **60 tests, 0 failed**: the 33 P6 tests (adapted) plus Hand 12, AndroidStrings 6, Drift 6 + KeepNative 1, and 2 new real/golden tests |
| `./gradlew :tools:strings:run --args="convert --repo .."` | exit 0; the counts above. A second run leaves the tree unchanged. |
| `./gradlew :tools:strings:run --args="check --repo .."` | `strings check: no drift` |
| `./gradlew :ui:words:desktopTest` | **4 tests, 0 failed** (about 75 s cold, most of it CMP resource generation and compile) |
| `./gradlew :ui:words:compileKotlinDesktop :feature:chats:compileKotlinDesktop` | OK. `:feature:chats` still finds its `chats_*` strings. |
| `./gradlew :androidApp:assembleDebug` | OK, 16.9 MB preview APK. CMP strings ship as `assets/composeResources/…/values*/strings_*.cvr` (111 files). |
| `aapt2 dump resources` on that APK, with `ui/words/android-res` added to `:androidApp` res (temporary, uncommitted edit; see the requests below) | 391 `string/` entries. Examples: `usage_widget_left` = `%1$d%% left` / `تبقّى %1$d%%`; `usage_widget_cant_read` = `Can't read yet`; `tile_need_you` has 6 Arabic categories; `appExitServerStopped` in en/zh/ar/es. |
| `./gradlew :quality:test` | 17 tests, **1 failed**: `ModuleOwnersTest`, because `MODULE_OWNERS` has no `:tools:strings` row (coordinator-only file, request below). It already failed at `6627d320` for `:tools:arb2xml` and `:tools:flutter-migration-readers`. K10 is green. |
| `git diff 6627d320..HEAD` grep for private IPv4 ranges and e-mail addresses | none |

## Resolved versions

All of these come from the existing catalog and wrapper; nothing new was added.

- Gradle 9.7.1, Kotlin 2.4.21, AGP 9.3.3.
- Compose Multiplatform 1.13.0-beta01, including `components-resources`.
- kotlinx-serialization-json 1.12.0. P6 used 1.9.0 standalone; the catalog pin now applies.
- Build JDK 21.0.12. The toolchain JDK 17.0.21 (Adoptium) was provisioned by foojay.
- build-tools 37.0.0 and platform android-37.1.

## Deviations from the plan

1. **The Android copy of `strings_os` is committed and generated by the tool**, not copied at build time by an
   `:androidApp` `generateOsStrings` task (§13.2). There are two reasons:
   - ADR §7.2 (K10) forbids `androidMain` in `:ui:words`;
   - an aapt2 copy is not a byte copy (`%` → `%%`).

   The tool writes `native/ui/words/android-res/`, and `strings check` fails if it differs from the CMP copy.
   `:androidApp` only adds the folder to its res (request 2).
2. **The CMP copy of `strings_os.xml` uses the same aapt2-safe `\uXXXX` escapes as P6's ANDROID profile.** CMP
   decodes them, so the text is the same. A literal `%` next to arguments is now allowed there, because the Android
   copy doubles it.
3. **Hand conversions are recipes applied by the tool**, not typed XML.
   - The decisions are hand-made and reviewed in `hand-recipes.tsv`.
   - The output is mechanical, so the Arabic categories, `__eqN` and escaping cannot drift from the rest.
   - `HandWords.kt` is generated (§13.5's "code chooses the branch with an exhaustive `when`").
4. **Native Android strings are normalised to CMP form:**
   - `%%` → `%`;
   - non-positional `%d`/`%s` → `%1$d`, `%2$s`… (11 occurrences). `String.format` gives the same output;
   - collapsed whitespace, as aapt2 does: `usage_widget_column`'s double space was already a single space on
     device.
5. **The drift check matches by name across all feature files.** It does not care which file holds a key, so a
   feature owner can move a key. Copy added after the conversion is allowed, and the converter keeps it. This
   lets the check live past the ARB freeze (§13.3: "new copy goes into the XML only").
6. **`strings_usage.xml` is a 21st feature file.** It came from P6's routes (211 keys); §13.2 lists 20.
7. **The drift check is a JUnit test in `:tools:strings`, run against the repository.** It is not wired as a T1
   task that runs only when `:ui:words` changes: that wiring belongs to the CI owner.

## Catalog and coordinator requests

1. `native/MODULE_OWNERS`: add `:tools:strings	T	l10n-owner	slice-1` and remove nothing. The row for
   `:tools:flutter-migration-readers` is also missing; that predates this unit.
2. `native/androidApp/build.gradle.kts`: add the generated Android strings. This exact line was verified with
   `assembleDebug` and `aapt2 dump`:
   ```kotlin
   android { sourceSets.getByName("main").res.directories.add(rootProject.layout.projectDirectory.dir("ui/words/android-res").asFile.path) }
   ```
   Later, the plan's `androidResources { generateLocaleConfig = true }` and `resources.properties`
   (`unqualifiedResLocale=en`) need `values-ja/pt/ru` Android folders as well. Today only en/ar/es/zh have `os`
   strings, so the generated locale config would list 4 languages. That decision belongs to the owner of §13.6.
3. No new libraries are needed.

## Open problems

- **The owner must review the 130 unused keys.** They are listed in `report/convert-report.md` § "Unused keys" and
  kept in `native/tools/strings/unused/` (not shipped).
- **The owner should review `feature-routes.tsv`**, which is still P6's reading of the plan's feature names (P6
  decision 4).
- **Merge conflicts.**
  - Feature sessions that edit `strings_<feature>.xml` in parallel, notably `strings_chats.xml` (roadmap S1 chats
    row), will conflict with these files at merge.
  - Resolution: keep their added names; anything that comes from the ARB comes from here.
  - Rerunning `strings convert --repo ..` after a merge keeps their copy and puts the ARB values back.
- **The Flutter app keeps changing its ARB files until the freeze.** Each change fails `DriftTest`/`strings check`
  until `convert` is rerun. That is the intended alarm.
- **Not in this unit** (plan W1 work in the same area):
  - `PluralsGenerated.kt` for the 215 keys with `=N` selectors (data: `report/exact-selectors.tsv`). Until it
    exists, callers must check `__eqN` themselves, as `HandWords` does;
  - `NumberArgKeys.kt` (35 `decimalPattern` keys);
  - pseudolocales (§13.7);
  - copy rules (§13.8 items 5, 7, 8);
  - `SupportedLanguages`/`LanguageCoverage`;
  - the on-device language switch (P6 API 33/30).
- **Arabic orphans.** The 1,018 Arabic-only ARB keys are dropped and listed in `report/orphans-ar.txt`.
- **Size.**
  - `:ui:words` composeResources is 3.6 MB of XML.
  - `report/` is 1.7 MB, mostly `key-routes.tsv` and `missing-*.txt`, kept for review.
  - A cold `:ui:words` desktop compile takes about 75 s here.
- **No formatter script.** `native/AGENTS.md` names `tool/format-changed.sh`, which does not exist. The changed
  Kotlin follows the surrounding 4-space style but was not run through a formatter.
- **Local network workaround.** Maven Central answers HTTP 429 to this container. Dependencies resolved through a
  local, **uncommitted** `~/.gradle/init.d/mirror.gradle.kts` that puts Google's Maven Central mirror first, as P6
  also did. The committed build is unchanged.
