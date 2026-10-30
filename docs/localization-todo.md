# Localization: TODO

Status (2026-10-08): **English and Arabic.** `lib/l10n/app_en.arb` and
`app_ar.arb` carry about 7,370 English messages, each with an Arabic one;
`tool/l10n/check_completeness.py` reports none missing (new English must ship
with Arabic in the same change), and there is a language picker in Settings
(`lib/ui/widgets/language_picker.dart`). The hardcoded-literal ratchet
(`test/l10n_coverage_test.dart`) has an empty baseline: no `Text('...')` or
named label literal is left under `lib/ui/**`, `lib/voice/**` or
`lib/main.dart`. It does not see other kinds of literal (the sections below),
and it says nothing about translation quality.

Since 2026-10-08 Spanish, Japanese, Brazilian Portuguese, Russian and
Simplified Chinese also carry the first-run screens and the chat core (668
messages, `docs/l10n/fg5-key-set.md`, QA record
`docs/qa/fg5-2026-10-08/README.md`) and read English for the rest; the
picker marks them "Partly translated". Since 2026-10-16 Spanish and Simplified
Chinese carry a second tier, 2,083 more messages (2,750 each of 8,916): what a
new user meets in the first hour (`docs/l10n/tier2-key-set.md`). Japanese,
Portuguese and Russian stay at the core. `tool/l10n/check_partial_locales.py`
keeps the core keys complete in all five and the tier-2 keys
(`tool/l10n/partial_locales_tier2_keys.txt`) complete in es and zh.

Since 2026-10-29 Simplified Chinese carries every message: all 9,262 of 9,262
keys in `app_zh.arb`, nothing left falling back to English, and the picker no
longer marks it "Partly translated". The gate above still runs on it as one of
the partial locales; its counts are only useful for es now. Spanish is still
at the core plus tier 2, and Japanese, Portuguese and Russian at the core.
The Chinese text has not been read by a native speaker yet, and terminology
was kept consistent by hand rather than by a translation memory; a review pass
is the next step, not more keys.

## Done

- [x] Externalise every `Text('...')` and named label literal under
      `lib/ui/**`, `lib/voice/**` and `lib/main.dart` (the coverage test).
- [x] Add `test/l10n_coverage_test.dart` as a per-file ratchet. Baseline now
      empty.
- [x] Add the first non-English locale (Arabic, right to left) and a locale
      picker in Settings that follows the system locale by default.
- [x] Surfaces written without a widget tree resolve their words from the
      app's language (`ConnectionController._shellStrings`): launcher shortcut
      labels, the AI Team notifications and ongoing progress line, the thermal
      notice, the phone setup notification, and, since 2026-10-08, the live
      "what is running" line of the ongoing background notification
      (`ConnectionController.toolSentence`, keys `liveTool*`) and the
      home-screen widget's untitled-conversation fallback.

## Still open

- [ ] Notifications composed on the Android side. The Dart side now sends only
      localized words, ids and counts, but Kotlin builds the rest in English
      (`android/app/src/main/kotlin/.../`): the coding-alert titles and bodies
      and the action labels ("Allow once", "Pause background", "Stop") in
      `BackgroundConnectionService.kt`, its channel names and descriptions,
      "OpenCode is connected", and the phone server notification in
      `BuiltinServerService.kt`. `res/values-ar/strings.xml` exists (17
      strings) but does not cover them; `VoiceDownloadNotifications.kt`
      switches on an `arabic` flag instead. Moving these to string resources
      is a native change (a new release, not a Shorebird patch).
- [ ] Words the app maps from a server into the transcript
      ("Compacting conversation…" in `lib/paseo/mappers.dart` and
      `lib/api2/gateway_mappers.dart`).
- [ ] Give static helpers and sheets that lack a `BuildContext` a way to
      resolve strings (pass `AppLocalizations` in, as `toolSentence` now does,
      or restructure).
- [ ] Plurals and dates through `intl` rather than string concatenation
      (`'$n files'`, relative times, cost formatting).
- [ ] Golden tests for the theme gallery in the new locale (the kit gallery
      and several screens have Arabic goldens; the full gallery does not).
- [ ] Translation quality: the completeness gate checks that a message
      exists, not that it reads well. The voice packs and the copy of several
      newer pages are English-first.

## Non-goals for now

- Translating server-provided text (tool titles, model names, agent
  descriptions). That content comes from OpenCode and stays as sent.
