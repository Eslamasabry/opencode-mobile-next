# Slice 0, proof P6: the strings converter (ARB → CMP XML)

Status (2026-11-01): the **converter part** of P6 is implemented, tested and run on the real ARB files, and its
output is committed for review. The on-device part of P6 is **not started**: switching the app language at
runtime on API 33 and API 30 so that a screen and a notification both change. It needs the `native/` skeleton
(P1), so it was out of scope here.

Plan references: `docs/design/native-rewrite-2026-11-01/plan/01-architecture-decision.md` row 5, §7.5 and §11 P6;
`plan/06-ui-kit-design-system.md` §13; `map/packages-build.md` PACKAGES-BUILD-24.

## What it does

`native/tools/arb2xml` is a standalone JVM Kotlin command-line tool. It has its own `settings.gradle.kts` and
Gradle wrapper and no link to `native/settings.gradle.kts` or `build-logic`. It is the W0 spike of the plan's
`:tools:strings`, which the l10n owner can move into the main build later. It runs on JDK 21 with Kotlin 2.4.21
and Gradle 8.14.3, and needs no Android SDK. Its only dependency is `kotlinx-serialization-json`, used for parsing JSON.

For every `lib/l10n/app_<lang>.arb` it:

1. **Parses** the file in key order and checks that `@@locale` matches the file name. Keys missing from the
   English template (orphans) are dropped and listed.
2. **Routes each key to a feature file** with the plan's rule (06 §13.3 step 2):
   - Keys whose names start with `team` or `fb` (followed by an upper-case letter or a digit) go to
     `strings_team.xml` or `strings_factsbook.xml`.
   - Otherwise the tool finds the Dart files that read the key. These are the files that import
     `app_localizations.dart`, plus the `part` files of those libraries, matched by a whole-identifier `.key`.
     Each file is mapped to a feature by the longest prefix in `native/tools/arb2xml/feature-routes.tsv`
     (171 rows).
   - A key read by one feature goes to that feature's file. A key read by several features goes to
     `strings_common.xml`. A key read by no file is "unused".
   - **A Dart file that reads keys but matches no route stops the run** (exit 2, with the file list), so nothing
     lands in `common` silently.
3. **Placeholders:** ARB `{name}` becomes `%N$d` for `int` and `%N$s` for `String`, `Object`, `num` and for
   `int` with a `format` such as `decimalPattern`. N is the order of first appearance in the **English** message,
   and every language uses the same map, so a translation that reorders words keeps the right values
   (`aiteamBringInDone` in ar, 19 keys in zh).
4. **Plurals:** a plural becomes `<plurals>` with **every CLDR category of that language**: Arabic always gets
   zero/one/two/few/many/other, Russian one/few/many/other, Spanish and Portuguese one/many/other, English
   one/other, and Chinese and Japanese other.
   - A category the message has no branch for takes the `=N` branch where the category is exactly {N}
     (en/es `one` = {1}; ar `zero`, `one`, `two`). Otherwise it takes `other`.
   - Each `=N` used by any language becomes `<string name="key__eqN">` in every language that has the key.
     Its text is what ICU shows for exactly N in that language.
   - An embedded plural (`{question} · {count, plural, …}`) copies the surrounding text into every item.
5. **Escaping:** the rules match what CMP actually does (see Decision 1).
6. **Reports:** the counts, the keys each language is missing, orphans, everything it could not convert, the
   argument order of every key, the exact selectors, the unreachable branches and the routes. With no flag,
   any message it cannot convert stops the run before anything is written.

## Commands

From `native/tools/arb2xml/`:

```bash
./gradlew test                       # 33 kotlin.test tests (goldens + real ARB round trip), about 20 s warm
./gradlew test -PupdateGoldens=true  # rewrite src/test/resources/golden from the fixture, then review the diff

# Convert. Without --skip-unsupported it exits 1, lists the 38 messages and writes no XML.
./gradlew run --args="convert --arb ../../../lib/l10n --dart-lib ../../../lib \
  --routes feature-routes.tsv \
  --out build-sample/composeResources --unused-out build-sample/unused \
  --report build-sample/report --skip-unsupported"

# Re-cut the golden fixture from the real ARB files (from the repo root):
python3 -I native/tools/arb2xml/fixture-tools/cut_fixture.py
```

Exit codes: 0 means converted. 1 means some messages cannot be converted: they are listed on stderr and in
`report/unsupported.tsv`, and nothing is written unless `--skip-unsupported` is given. 2 means bad input
(usage, unrouted Dart files, malformed ARB).

Note for this container only: Maven Central rate-limited its IP (HTTP 429), so dependencies were resolved
through a Google mirror of Maven Central, set in a local `~/.gradle/init.d/` script that is **not committed**. The
committed build uses plain `mavenCentral()`.

## Output (committed)

`native/tools/arb2xml/build-sample/`:

- `composeResources/values{,-ar,-es,-ja,-pt,-ru,-zh}/strings_<feature>.xml`: 111 files, about 3.4 MB. This is the
  layout `:ui:words` would take as `src/commonMain/composeResources`.
- `unused/values*/strings_unused.xml`: the keys no Dart file reads. They are kept **outside**
  composeResources so they never ship, but nothing is lost, and the owner confirms them before the ARB freeze.
- `report/convert-report.md` plus `missing-<lang>.txt`, `orphans-ar.txt`, `unsupported.tsv`, `placeholders.tsv`,
  `exact-selectors.tsv`, `unreachable-zh.txt` and `key-routes.tsv` (key → feature → readers).

### Counts per language (ARB files at commit `becde5fb`)

`in = converted + unused + not convertible + orphans` holds for every language. `RealArbTest` checks it from the
written XML read back.

| lang | in (ARB) | converted | unused | not convertible | orphans dropped | missing vs en | `<string>` | `<plurals>` | `__eqN` |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| en | 9,837 | 9,695 | 129 | 13 | 0 | 0 | 9,562 | 262 | 303 |
| ar | 10,855 | 9,695 | 129 | 13 | 1,018 | 0 | 9,562 | 262 | 303 |
| es | 2,695 | 2,682 | 10 | 3 | 0 | 7,142 | 2,614 | 78 | 72 |
| ja | 661 | 658 | 3 | 0 | 0 | 9,176 | 632 | 29 | 28 |
| pt | 661 | 658 | 3 | 0 | 0 | 9,176 | 632 | 29 | 28 |
| ru | 661 | 658 | 3 | 0 | 0 | 9,176 | 632 | 29 | 28 |
| zh | 8,654 | 8,522 | 123 | 9 | 0 | 1,183 | 8,429 | 216 | 233 |

The plan's figures (9,468 en keys, 10,486 ar keys) predate the frozen snapshot; English has 369 more keys today.
Arabic covers every English key.

English keys per feature file: team 1,542 · chat 975 · phone 911 · project 841 · settings 675 · common 666 ·
factsbook 544 · kit 520 · servers 475 · agents 461 · chat_pages 380 · composer 342 · onboarding 316 · chats 303 ·
usage 211 · about 164 · search 106 · failures 96 · schedules 70 · os 62 · widget_panels 35. The full table, for
every language, is in `report/convert-report.md`.

## What it does not convert (it fails loudly)

13 keys, which are 38 messages across the languages. They are listed on stderr, in `report/unsupported.tsv` and
in the report. `RealArbTest` pins this exact list, so a change shows up in a test.

| Kind | Keys | Languages |
|---|---|---|
| ICU `select` | `kitAgentLabel`, `activeContextMessageTitle`, `runningPhoneGroup`, `runningPhoneName`, `runningPhoneCpu`, `runningPhoneAbout` | en, ar (+ es, zh for some) |
| Several plurals in one message | `e7SettingsStorageSummary`, `e7SetupDeleteDisclosure`, `chatAttachmentSheetAttached`, `teamProjectGoalStatus`, `teamProjectGoalStatusDraft`, `teamProjectBoardSummary`, `teamProjectPlanSummary` (3 plurals) | en, ar, zh (+ es for 2) |

The real files contain no nested plurals (a plural or select inside a branch). The tool refuses them with
`NESTED_PLURAL`, and a unit test covers that case. The tool also refuses the following, none of which occur
today:

- a translation that uses a placeholder English does not have;
- a translation that pluralises a different argument;
- a selector that is neither a CLDR category nor `=N`;
- a plural with no `other` that leaves a category uncovered;
- text that already contains a `%N$s` token;
- in `strings_os.xml`, a literal `%` mixed with placeholders.

Next step, for the l10n owner: write the 6 selects as one string per branch (`<key>__<branch>`), chosen with an
exhaustive `when` (plan 01 §7.5), and split the 7 multi-plural messages into one `<plurals>` per count plus a
sentence that composes them. Arabic and Chinese need the same rewrite.

## Decisions taken

1. **Escaping follows CMP's real unescaper, not the plan's `\'` rule.** I read the CMP 1.12.2 source
   (`compose-gradle-plugin` `PrepareComposeResources.handleSpecialCharacters`). After DOM parsing, CMP turns only
   `\n`, `\t`, `\uXXXX` and `\\` back into characters. **`\'`, `\"`, `\@` and `\?` would reach the screen with
   their backslash.** Plan 06 §13.3 step 6 listed this as unverified ([U]); it is now settled. There are two
   profiles:
   - **Feature files (CMP only):** `'`, `"`, `@` and `?` stay as typed, so the 600+ English strings with
     apostrophes stay readable for the people who edit them from now on.
   - **`strings_os.xml` (read by CMP and copied into Android `res`):** `'` and `"` become `'` and `"`.
     A leading `@` or `?` and leading, trailing or repeated spaces also become `\uXXXX`. Both CMP and aapt2 decode
     these to the same character. A value with a literal `%` and no placeholders gets `formatted="false"`.
   - **Both profiles:**
     - `&`, `<` and `>` are XML-escaped;
     - a newline is written `\n`, a tab `\t` and a backslash `\\`;
     - bidi marks, zero-width characters and other invisible characters become `\uXXXX`, so a reviewer can see
       the `‎` in Arabic strings.

   The tests read every written value back through a DOM parser and a copy of CMP's `handleSpecialCharacters`,
   and require the original text. This covers the whole real catalogue and 5,000 random strings built from the
   characters that are risky to escape.
2. **`select` is refused, not converted.** Plan 06 §13.3 step 5 proposed emitting branch strings. The P6 brief
   says to fail loudly. Six messages do not justify a converter mode, and the code that picks a branch has to be
   written by hand anyway. `--skip-unsupported` converts everything else and leaves the list in the report.
3. **Several plurals in one message are refused**, like nested plurals. The plan expected "a handful" to split
   by hand; there are 7.
4. **Routing uses the plan's rule (Dart usage plus key prefix), not key prefix alone.** Key prefixes do not follow
   features: for example, `chatUi*` keys are read by the kit, `e7*` keys are spread over every screen, and
   `kit*` keys are read by screens. `team`/`fb` are the only prefixes the plan routes by name.
   `feature-routes.tsv` holds my reading of the plan's 21 feature names. The owner should review its rows; a
   changed row needs only a rerun.
5. **Unused keys are written, not dropped.** The plan says "not converted, listed". I write them to
   `unused/strings_unused.xml` outside composeResources instead. They do not ship, and keys in still equals keys
   out. There are 130 template keys: 129 converted, plus `e7SetupDeleteDisclosure`, which is also not convertible.
6. **Every plural lists every category of its language.** A missing category takes its exact `=N` branch, or
   `other`. CMP would fall back to `other` at runtime anyway, but listing every category makes the Arabic
   six-category rule visible in review and checkable.
7. **Shape promotion:** when one language pluralises a key that another writes as plain text, the plain text
   becomes a `<plurals>` with the same text in every item. This affects `linuxMgrProcGroupCount` and
   `chatTurnAllFilesMore` in English (Arabic pluralises them) and one key in Chinese, so `Res.plurals.x` exists
   in every language.
8. **Branches that a language's rules can never pick are dropped** and listed. There are 47 `one{…}` branches
   in zh, where CLDR has only `other`; ICU would not pick them either.
9. **The plural rules are pinned:** `PluralRules.kt` was checked rule by rule against CMP 1.12.2
   `CLDRPluralRuleLists.kt` for the 7 languages.

## Findings for the rest of P6 and for `:ui:words`

- **A language can borrow English's plural text under its own plural rules.** When a key is missing in pt or ru
  (9,176 keys each), CMP reads the English `<plurals>` from `values/` but picks the category with the pt or ru
  rules. In pt, 0 is `one`, so 0 shows English's "1 draft queued". In ru, 21 is `one`. The generated `Plurals`
  helpers (plan 06 §13.4) should resolve a key missing in the current language with the English rules. The
  on-device P6 proof should include this case: pt, count 0, on a key only English has.
- **Exact selectors matter outside English:** in pt, `kitNeedsYouWaitingSpoken` has `=1{aguardando há 1 minuto}`,
  but pt `one` also covers 0, so the `one` item must carry `other`'s text. Only the `__eq1` string has the right
  text for 1. The helper must check `__eqN` before `pluralStringResource`. `report/exact-selectors.tsv` lists
  the 209 keys that need it.
- CMP inserts every argument with `toString()`, whether the token is `%1$d` or `%1$s`. The `d` and `s` letters
  only matter for the drift check and for Android `strings_os`. The 35 keys with `decimalPattern` (listed in the
  report) need a locale-formatted number passed in.

## Tests

`native/tools/arb2xml/src/test/kotlin/…`. The last run had 33 tests, all passing (`./gradlew test`, JDK 21).

- **Goldens** (`GoldenTest`): the fixture is 19 real keys cut verbatim from the 7 ARB files. They cover:
  - apostrophes, quotes, a leading `@`, a literal `%`, `&`, newlines and the `‎` in Arabic;
  - 4 placeholders, and reordered placeholders in ar and zh;
  - an int with `decimalPattern`;
  - a plural in all 7 languages, an embedded plural, and `=0`/`=1` in every language;
  - promotion, `strings_os` routing and team-prefix routing;
  - an unused key, a select, a double plural and an Arabic orphan.

  The output is compared byte for byte with `src/test/resources/golden/`. Named tests pin the Arabic six
  categories, the pt and ru exact-selector behaviour and the routing rule (a part file, common, a non-importing
  file, `lib/l10n` skipped). `RealArbTest` fails if the fixture drifts from the real ARB files.
- **Round trip** (`RealArbTest`): all 7 real ARB files are converted and written, then read back like CMP does.
  For every language the test checks:
  - every ARB key is written, listed as not convertible, or listed as an orphan, and the counts add up;
  - every value equals the converted text;
  - every `<plurals>` has exactly its language's categories;
  - every `%N` index exists.

  It also pins the not-convertible list and checks the CLI's exit codes 1 and 2.
- **Units:** the plural rules (ar 0, 1, 2, 3, 11, 100 → zero, one, two, few, many, other), the ICU parser, the
  converter's refusals and both escaping profiles, with a property test.

## Not done here

- The on-device part of P6: per-app language through `LocaleManager` on API 33, the API 30 path, a screen and a
  notification switching at runtime, and the `LocalResourceEnvironment` fallback.
- Generated `PluralsGenerated.kt` and `NumberArgKeys.kt` (plan 06 §13.3–13.4). The data they need is in
  `placeholders.tsv` and `exact-selectors.tsv`.
- Merging the 6 Android XML files into `strings_os.xml` (§13.3 step 7), the `check` drift command (§13.8) and the
  pseudolocales (§13.7).
