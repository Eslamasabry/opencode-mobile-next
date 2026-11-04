# Flutter screens migration 40 — part 2 of 3

Counts: already there **0** · ported now **0** · not needed **12** · blocked **0**.
Reviewed: **12/67** (implementation in progress).

Base: `cde0afb45f598c00e89de766d9d0a68ba89ae470`; branch `p12x/40-port-screens-2`.
Implementation evidence only. No tests written or run; no APK, push, signing or release.
“Not needed” names Flutter plumbing or the binding native design's explicit deferred/dropped feature; it does not claim the behavior already exists natively or that the switch is qualified.

- `lib/ui/screens/factsbook/about_you_screen.dart` — not needed: FactsBook browse/filter/search hub is deferred by binding plan/01 §12 D6; its saved data stays on disk.
- `lib/ui/screens/factsbook/areas_screen.dart` — not needed: FactsBook owner-approved area sharing is deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/event_detail_screen.dart` — not needed: FactsBook trigger outcomes, rule and provenance detail are deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/factsbook_routes.dart` — not needed: Flutter route glue opens only FactsBook pages, deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/not_saved_screen.dart` — not needed: FactsBook retained capture retry/export/discard is deferred by binding plan/01 §12 D6; source captures remain untouched.
- `lib/ui/screens/factsbook/offer_card.dart` — not needed: FactsBook approve/dismiss conversation offers and first-send receipts are deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/reminder_check_sheet.dart` — not needed: FactsBook reminder edit and server-preview agreement are deferred by binding plan/01 §12 D6; native scheduled prompts are a different feature.
- `lib/ui/screens/factsbook/reminders_screen.dart` — not needed: FactsBook reminder create/edit/pause/resume lists are deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/rule_editor_screen.dart` — not needed: FactsBook signed trigger-rule CRUD and sample dry-run are deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/search_screen.dart` — not needed: FactsBook word search with provenance and locked-note disclosure is deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/settings_screen.dart` — not needed: FactsBook owner-approved brief cap/home-zone and node privacy settings are deferred by binding plan/01 §12 D6.
- `lib/ui/screens/factsbook/trigger_text.dart` — not needed: FactsBook trigger/rule/trust copy and sample grammar helper serves only the deferred D6 feature.

Verification: this first batch changes documentation only; Gradle omitted. Source files read in full; classifications checked against binding plan/01 §12 D6.
