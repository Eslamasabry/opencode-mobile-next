RESULT: APPROVED WITH FIXES

# Approval: job 32-build-REST-2 (NAV KIT L10N BUILD CHAT XPLAT MIGR, part 2 of 2)

Branch `native/p12x/job-32-build-REST-2`. Reviewed: the job's non-merge commits over
`origin/native/p12-head` — `adb0b001` (locale-aware USD, device language resolver, shipped locale
list, KitBidi), `587e6d23` (Search Go to menu), `5377adb2` (Chats relative times + report).
No tests were written or run and no APK was built (owner: coding first, testing afterwards);
Android compilation happens on the build VPS.

## Merge

`git merge --no-ff origin/native/p12-head` (33 commits behind). One conflict, in
`androidApp/.../graph/SettingsWiring.kt` `systemLocaleTag()`: P12 head only reformatted the old
one-line `locales[0]` reader and its KDoc; this job replaced it with the whole-list resolver
(B-L10N-009). Kept the job's resolver and P12 head's reflowed KDoc — both intents preserved.
No `build.gradle.kts` conflicts.

## Review

54 rows accounted for (6 built now / 17 already there / 31 blocked). Blocked rows are honest:
each names a missing screen, port or wiring and nothing is stubbed.

Built-now rows, checked against Flutter:

- **B-L10N-009** `AppLanguageResolver` walks the phone's whole list, matches by language code
  (so region/script variants land), skips unshipped entries, falls back to English — matches
  `l10n_new_locales_test`. Wired into `systemLocaleTag()`, used by `AppLocale.wrap/changed`.
- **B-L10N-008** `locales_config.xml` now lists all seven shipped languages, agreeing with
  `AppLanguage`, `SHIPPED_LANGUAGES` and the `values-*` folders of `:ui:words`.
- **B-L10N-012** Usage cost puts `$` after the amount in RTL and uses the phone's digits/
  separators via `Numbers.decimal`; English output (`$1,234.50`, `$0.00345`) is unchanged.
- **B-L10N-004** Chats home rows show Just now / N min / N h / N d ago from existing en/ar
  strings, numbers through `Numbers.format`. Time is recomputed whenever the feed, filters or
  briefs change (no minute ticker, same as Flutter's rebuild-driven label).
- **B-L10N-006** `KitBidi.ltr/auto` port Dart exactly (once-only, empty untouched); request words use it.
- **B-NAV-056** Search shows Go to rows then the last three conversations, gated on
  `ServerCapabilities.projectManagement` (never flavour); typing filters; rows open real routes;
  `goToFacts` is implemented in the real Android graph (`AndroidConversationSearch`).

Kit-only UI (KitSectionLabel, KitRow); all copy from `:ui:words` resources with English and Arabic.
No security or privacy impact (no credentials, logs, URLs or storage keys touched).
"Already there" citations spot-checked (MIGR M3/M5 steps, StoredProfile migration flags,
Glossary tool, PlatformFamily, request words, worktree and reset copy, notification relocalize):
true; one line reference was stale after the merge and is corrected.

## Findings and fixes

1. **Dead "Untitled" Help row in Search (bug).** `GoToMenu.rows` always adds Help, but the screen
   has no label for non-openable places, so a row titled "Untitled conversation" appeared that did
   nothing on tap. Fixed: `SearchViewModel.loadGoTo` drops rows whose place is not `openable`.
2. **Needs you opened the unfiltered All-conversations list.** Dart opens the Chats home with its
   Needs-you filter. Fixed to `nav.land()` (Chats home, which shows the Needs-you count and
   filter); pre-selecting the filter is noted in the report as not yet native.
3. **Dead code.** `Numbers.usd`, `KitBidi.address` and `KitBidi.isolateValue` had no caller, and
   `isolateValue` did not match Dart's `KitFactRow.isolateValues` (which isolates runs inside a
   sentence, not the whole text). Removed; B-L10N-006 now says the fact-row half is blocked on a
   native KitFactRow, B-L10N-012 no longer cites `Numbers.usd`.
4. **Test source would not compile.** `SearchActions.goTo` was added without updating the
   desktopTest fake in `SearchScreenTest`; added the override (no new test).
5. **Report** corrected for items 1–3 and the stale B-L10N-013 line reference.
