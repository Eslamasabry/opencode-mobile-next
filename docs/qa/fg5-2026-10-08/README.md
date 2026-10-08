# FG5: Japanese, Simplified Chinese, Spanish, Brazilian Portuguese, Russian (2026-10-08)

Branch `fe/languages` (from `feat/genui-fe`). Shipping state: **implemented and
locally verified; not enabled for anyone until a build ships; no device proof;
no native-speaker review yet.**

## What changed for a person

- Settings > Appearance > Language and the Language sheet list **Use system
  language, English, العربية, Español, 日本語, Português (Brasil), Русский,
  简体中文**, each under its own name. A language that is not complete says
  "Partly translated (9 %)" (the real share, recomputed by
  `test/revamp/shared_settings_1_test.dart`).
- "Use system language" now matches the phone's language to one of the five
  (any `ja`, `zh` including Traditional, `es`, `pt` including Portugal, `ru`).
- A phone in a language the app does not ship now reads **English**. Before
  this change Flutter fell back to the first locale of the generated list,
  which is Arabic (`resolveAppLocales`, `lib/state/app_locale.dart`; the three
  `MaterialApp`s use it).
- Anything a language does not carry reads in English; nothing crashes and
  gen-l10n reports no missing key.

## Coverage per language

Core set: 672 messages (docs/l10n/fg5-key-set.md), the same set in each
language. English has 7,367 messages.

| Language | ARB | Keys translated / total | Share | Falls back to English |
| --- | --- | --- | --- | --- |
| Spanish | `app_es.arb` | 672 / 7,367 | 9.1 % | 6,695 |
| Japanese | `app_ja.arb` | 672 / 7,367 | 9.1 % | 6,695 |
| Brazilian Portuguese | `app_pt.arb` | 672 / 7,367 | 9.1 % | 6,695 |
| Russian | `app_ru.arb` | 672 / 7,367 | 9.1 % | 6,695 |
| Simplified Chinese | `app_zh.arb` | 672 / 7,367 | 9.1 % | 6,695 |
| Arabic (unchanged gate) | `app_ar.arb` | all; 5 native-name keys added | 100 % | 0 |

The 672 are, by group: language picker 14, welcome 24, phone setup 116, shell
12, conversation list 70, composer 107, turn status 83, permission and
question cards 72, model and agent picker 112, shared kit words 62. Seven of
them are language names that read the same in every language.

Code choices: `zh` (not `zh_Hans`) and `pt` (not `pt_BR`) because the project
generates one catalogue per language code and a script- or region-coded file
needs a plain-language file beside it anyway; see the table in the key-set doc.

## Ratchets

- **Arabic completeness** (`tool/l10n/check_completeness.py`): unchanged and
  green (0 debt). The five new language-name keys are in both `app_en.arb` and
  `app_ar.arb`.
- **New: partly translated locales** (`tool/l10n/check_partial_locales.py`,
  list `tool/l10n/partial_locales_core_keys.txt`, CI step in
  `.github/workflows/android-quality.yml`): every core key must be translated
  in all five; the list may only grow (`--base-ref`); ICU arguments and
  plural/select shape must match English; no key English lacks, no empty or
  duplicate message, right `@@locale`. New English messages are **not**
  required to be translated into these five (they fall back), unlike Arabic;
  `tool/l10n/README.md` says how to switch a language to the Arabic pattern.
- `test/l10n_coverage_test.dart` (hardcoded-literal ratchet), `ui_glossary_test`,
  `kit_ratchet_test` and the golden harness (G23) were run and pass unchanged.

## Checks run (all through `tool/qa/machine_lock.sh`, serial, pinned Flutter 3.47.1)

| Command | Result |
| --- | --- |
| `flutter gen-l10n` (once; `pub get` also regenerates) | 7 locales, 6,695 untranslated per new language, no error |
| `flutter analyze` | No issues found |
| `test/l10n_coverage_test.dart test/ui_glossary_test.dart test/kit_ratchet_test.dart test/golden_harness_test.dart test/app_locale_test.dart test/language_picker_test.dart test/theme_packs_test.dart` | 89 passed |
| `test/search_index_test.dart test/revamp/shared_settings_1_test.dart test/first_run_welcome_test.dart test/chat_empty_start_test.dart` | passed |
| `test/l10n_new_locales_test.dart` (resolution, fallback, plurals, core keys) | 11 passed; the resolver test fails without `resolveAppLocales` (checked by removing it) |
| `test/l10n_fg5_screens_test.dart` (welcome and composer in each language, 360 dp wide, 1.0x and 1.5x text, English as control) | 24 passed |
| `test/goldens/languages_golden_test.dart` | 10 passed |
| `python3 -m unittest discover -s tool/l10n -p 'test_check_*.py'` and both gates | 42 passed; `check_completeness.py` and `check_partial_locales.py` exit 0 |

The full serial suite was **not** run (this slice is not the integration
gate; per AGENTS.md rule 5 it runs at the stable batch boundary).

## Goldens (light, 412x915 at device pixel ratio 2; fake empty profile store, so not device proof)

First-run welcome (`test/goldens/`):

- `languages_welcome_ja_light.png`
- `languages_welcome_zh_light.png`
- `languages_welcome_es_light.png`
- `languages_welcome_ptbr_light.png`
- `languages_welcome_ru_light.png`

New conversation with the composer (starter chips, a draft, the model chip and
the offline note):

- `languages_composer_ja_light.png`
- `languages_composer_zh_light.png`
- `languages_composer_es_light.png`
- `languages_composer_ptbr_light.png`
- `languages_composer_ru_light.png`

Japanese and Chinese render with Noto Sans CJK subsets (`test/fixtures/fonts/
NotoSansCJK{jp,sc}-Regular-subset.otf`, SIL OFL, 78 and 98 KB, rebuilt by
`tool/l10n/subset_cjk_fixtures.py`); on a device Android falls back to its own
system CJK font, which this slice did not check. Bold Han strokes in the
shots are the engine's synthetic bold of the Regular subset.

Regenerate: `flutter test --update-goldens test/goldens/languages_golden_test.dart`.

## Not done / for the owner

- **No native-speaker review.** The wording was written by the coding agent.
  Read the permission, composer and status groups first
  (docs/l10n/fg5-key-set.md, "Review status").
- Everything outside the core set is English in these languages, including
  Settings pages (the Language row itself is translated), Files, Terminal,
  AI Team and Usage. `docs/localization-todo.md` is the map of what remains.
- Not checked on a device: CJK font fallback on real Android, text-size and
  display-size extremes beyond 1.5x, the "System" language path on a phone set
  to Japanese or Russian (unit-tested through the platform locale override).
- Notifications, launcher shortcut labels and the setup engine read the
  phone's (or chosen) language through the same catalogues, so their core
  words are translated where the key is in the set; their other strings are
  English.
- Traditional Chinese devices see Simplified. A separate `zh_Hant` catalogue
  is a later slice.
- Product decision pending: whether new English strings should also require
  translations into these five (the Arabic pattern). Today they do not.
