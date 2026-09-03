# Localization: TODO

Status: **English only.** The Flutter localization layer exists
(`l10n.yaml`, `lib/l10n/app_en.arb`, generated `AppLocalizations`), but only
about 30 strings go through it. Roughly 700 user-visible literals across
`lib/ui/**`, `lib/voice/**` and `lib/main.dart` are hardcoded English.

Decision (2026-09-02): ship the UI/UX refresh in English and do the
externalisation as its own change, so translators can start from a complete
ARB file rather than a moving target.

## Work items

- [ ] Externalise every user-visible string in `lib/ui/**` into
      `app_en.arb`, grouped by screen, with `@` descriptions for translators.
      Keep the English values byte-identical so the existing widget tests
      that match on text keep passing.
- [ ] Give static helpers and sheets that lack a `BuildContext` a way to
      resolve strings (pass `AppLocalizations` in, or restructure).
- [x] Add a `test/l10n_coverage_test.dart` that fails when a `Text('...')`
      literal with letters appears under `lib/ui/**`, with an allowlist for
      identifiers (model ids, paths, shortcuts). Landed as a per-file ratchet:
      751 literals in 67 files on 2026-09-03; the baseline only goes down.
- [ ] Plurals and dates through `intl` rather than string concatenation
      (`'$n files'`, relative times, cost formatting).
- [ ] Add the first non-English locale. Arabic is the natural candidate
      (maintainer's locale) and exercises RTL: verify the composer, diff
      gutters, the chevron glyph in the icon, and `Directionality` in the
      review canvas.
- [ ] Locale picker in Settings → Appearance, defaulting to the system
      locale.
- [ ] Golden tests for the theme gallery in the new locale.

## Non-goals for now

- Translating server-provided text (tool titles, model names, agent
  descriptions). That content comes from OpenCode and stays as sent.
