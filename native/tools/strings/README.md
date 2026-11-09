# Strings: conversion, drift check and the translation pipeline

English (`ui/words/src/commonMain/composeResources/values/`, plus the Android `res` folders listed in
`translate/translate.py` `ROOTS`) is the source of truth. Flutter is frozen. Every other language is a
`values-<tag>` folder next to it. This file is the runbook for adding and keeping translations.

## Pieces

| Piece | What it is |
|---|---|
| `translate/translate.py` | the pipeline: `status`, `baseline`, `plan`, `apply` (standard-library Python 3) |
| `translation-state/<tag>.tsv` | per language, `key<TAB>hash of the English it was translated from`; a changed hash means the English changed |
| `glossary-<tag>.tsv` | product terms kept, words fixed, words banned, words allowed to stay English |
| `translation-priority.tsv` | file-name regexes, most visible screens first |
| `src/test/.../TranslationQualityTest.kt` | the validators, run by `:tools:strings:test` |
| `src/test/resources/translation-baseline.tsv` | old copy that fails a rule; shrinks only |
| `src/test/resources/untranslated-allowlist.tsv` | keys whose text may equal the English (one key per line) |
| `ui/words/build.gradle.kts`, `androidApp/build.gradle.kts` | the 95 % language gate (below) |

## The language gate

A language is offered in Settings > Language, followed from the phone's language, and listed in the generated
`locales_config.xml` only when it has at least 95 % of the English keys of `:ui:words`. Below that the whole app reads
English, never string by string. `LanguageCoverage.offered` (generated into `:ui:words` at build time) is the one list
the app reads; `androidApp` generates `locales_config.xml` with the same rule. Nothing to edit when a language crosses
95 %: it appears in the next build. `TranslationQualityTest` prints each language's coverage.

## After a batch of English changes (the factory step)

```bash
cd native
python3 tools/strings/translate/translate.py status --locales es pt ru vi id fr     # what is open
python3 tools/strings/translate/translate.py plan --locale es --out /tmp/b/es --batch-size 80
#   one agent per language and slice reads the batch files and writes <batch>.out.json next to each (below)
python3 tools/strings/translate/translate.py apply /tmp/b/es/es-*.json               # validates, writes XML + state
~/tools/vgradle <agent> :tools:strings:test :quality:test                           # the gates (or CI lane=fast)
```

`plan` lists, per language, every key that is missing or whose English changed since it was translated
(`reason: missing | changed`; a changed key carries its `previous` translation). It writes batches of at most
`--batch-size` keys per file, most visible files first, with the key, the English as plain text, the placeholders,
the plural forms the language needs, a `max_length` when the key name (`...Max40`) or a comment before the entry says
so, and the comment as `note`. `apply` refuses a batch whose placeholders or plural forms do not match, writes accepted
items into the XML (everything else in the file stays byte for byte), mirrors `strings_os.xml` into
`ui/words/android-res`, and records the English hash. English key renamed or deleted: nothing to do here, the drift
check owns that.

Landing: commit per batch of files with `[skip ci]`, trailers `Change-Class: feature`, run the gates, merge
`native/next` in, merge into `native/next`, push without force.

## What a translator (agent) must do

Read the batch, write `<batch>.out.json` = `{"key": "translation"}`; a plurals key takes
`{"one": "...", "many": "...", "other": "..."}` with exactly the batch's `forms`. Then run `apply`.

- Plain text only. Write real characters: apostrophes, `&`, `<`, line breaks as `\n` in JSON, no `\'`, no XML entities.
  The tool escapes for the file (CMP files keep quotes; Android files get `'`).
- Placeholders (`%1$s`, `%2$d`) exactly as in English, any order the language needs. Never translate a command, path,
  URL, key name, `/slash-command`, file name or code. Keep `Markdown` and punctuation that is syntax.
- Product terms and agent names stay as the glossary says (`OpenCode`, `AI Team`, `Gas City`, `Claude`, `Codex` ...).
  Use the glossary's words every time: `This phone`, `Try again`, `Settings`, conversation (never session or chat in
  English; each language's glossary says what to say).
- Owner rules for copy: plain words for ordinary people; an action names its target ("Delete this conversation", not
  "Delete"); no raw technical words, no error text, no codes; same length or shorter where you can. Buttons and tabs
  (short English) must not grow past about 160 %.
- Natural register, not word for word. Do not translate a sentence you do not understand: read the neighbouring keys.
- Plural categories per language (CLDR): es/pt/fr `one many other`; ru `one few many other`; vi/id/ja/zh `other`;
  ar all six. The category list in the batch is authoritative.

### Per language

- **es**: neutral Latin-American Spanish, informal "tú" imperatives ("Revisa", "Inténtalo de nuevo"), "Agregar", "Ajustes",
  "archivo", "celular" is avoided: say "teléfono". No voseo, no vosotros, no Spain-only words (ordenador, móvil, vale).
- **pt**: Brazilian Portuguese (pt-BR), "você" without the pronoun, "Configurações", "arquivo", "celular". The folder is
  `values-pt`, which Android and CMP also use for pt-PT users (they read Brazilian spellings; a `values-pt-rBR` folder
  would leave Portugal on English). Add `values-pt-rPT` only if a Portugal edition is ever translated.
- **ru**: polite "вы" imperatives without the pronoun; "чат" for conversation (as the existing screens do), "Повторить",
  "Настройки"; cases and plurals correct (use the `few`/`many` forms).
- **vi**: neutral Northern standard, "bạn" for you, no "ạ" particles; Geist (the kit font) covers Vietnamese stacked
  diacritics, checked glyph by glyph, so no fallback font is needed.
- **id**: standard Indonesian, "Anda" sparingly (prefer an imperative with no pronoun), natural loanwords (server, file,
  aplikasi) over stiff coinages. Android calls the folder `values-in`, CMP `values-id`: the pipeline writes `values-id`
  for CMP roots and `values-in` for Android roots.
- **fr**: neutral standard French, "vous" imperatives ("Réessayez"), no Québec-only or France-only slang, so it also serves
  the Maghreb; use a no-break space (U+00A0) before `: ; ? !` and inside « guillemets », or none at all; stay consistent.

## Adding a language

1. Add its plural rules to `PluralRules.kt` (and `PLURALS` in `translate.py`).
2. Write `glossary-<tag>.tsv` (copy another; fill `term`, `forbid`, `allow`).
3. `translate.py plan --locale <tag> ...`; translate; `apply`. The first batch creates `values-<tag>`.
4. Add the language to `AppLanguage` (feature/settings), `LocaleChoice` (engine/settings) and the `e7LocaleUi<Name>`
   words (en, ar and the new language); the picker and `locales_config.xml` show it once it passes 95 %.
5. Run `:tools:strings:test`; fix new offenders (do not add them to the baseline).

## Validators (`TranslationQualityTest`)

placeholders (same arguments as the English), plurals (exactly the language's categories, none empty), glossary (kept
product terms, required words, banned words), no English left (not equal to the English; no English sentences), escaping
(no `\'` in CMP files, no bare quote in Android files, no raw invisible characters, no bare `&`), and a length budget for
short labels against the pseudo-locale. `PseudoLocale.expand` makes English 40 % longer with accents, keeping
placeholders; the kit render test `PseudoLocaleOverflowTest` (`:ui:kit`) draws key parts with it at phone width.
Existing copy that predates the pipeline sits in `translation-baseline.tsv`; a fix that removes an offender must remove
its line (the test fails on a stale line). `TRANSLATION_BASELINE_UPDATE=1` rewrites the baseline: use it only to record
the starting point of a new gate.

---

The converter and drift check (`strings convert` / `strings check`) are described in `Main.kt`.
