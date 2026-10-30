# Report a problem: it arrives knowing what went wrong (vision slice, 2026-10-20)

Branch `feat/vision-report-problem` (from int `dd2d79bde`). Concept:
`launch-footage/screen-concepts/b3-report-problem/concept-b.png`, trimmed as the vision page's
"Report a problem" section says (pick "B, trimmed"; `LEARNINGS.md`; vision decisions 2026-10-20).

Finish line: Report a problem opens on one calm card that already says what went wrong and what
goes with the report, an optional "Anything to add?" field, and one mint "Send as a GitHub issue"
with Copy beside it; Details shows exactly the text that leaves the phone. Non-goal: a new
destination (no server upload, no email), a screen tracker, or changing what the diagnostics
store records.

## 1. Data audit (written before building)

The question per element: what in the app or on the server makes it true?

| Element in the pick / concept | Source | Verdict |
|---|---|---|
| Plain sentence of what went wrong ("Something went wrong with the server") | The failure that opened the page: `KitReport.title` from `KitStateView` / `KitNotice` / a failed job (`openReportProblem(context, error:)`), already in plain words. Opened from Settings, search or a chat command with no failure: the newest error kept on this phone (`ReportProblem` store, else `AppDiagnosticsController`), named in plain words by `recentErrorTitle` with the time it was noticed (`KitTime.moment`). | **Real.** Neither: the card says the app noticed no error and asks for words (the field is then required, as today). |
| "Your error details and app information are attached" | What `ProblemReport.build` really puts in the report: the attached error's type, source and redacted details, the failed job's log, the recent events, the app version and platform | **Real.** Worded from what is present (a job with no log says so). |
| The screen where it happened | `KitReport.source` (the page id a `KitStateView`/`KitNotice` passes as `reportSource`). There is no route tracker in the app, so a failure surface that passes no id (and every entry without a failure) has none. | **Real only when passed.** It is an id, so it stays in the report text under Details ("Where: files-viewer"); never on the surface, never guessed. |
| App version | `problemReportAppVersion()` (`package_info_plus`, 2 s timeout, "unknown+0") | **Real.** In the report text. |
| Connection kind (new) | `ConnectionController.profile` (`backend`: OpenCode, Codex or Paseo; `serverVersion` from the last probe), `isPhoneOwnServer(profile)` and `connectionStatus.phase`, read from the page's controller or the app's `connProvider` | **Real, added** to the report's Environment as "Connection: OpenCode 2.0.28 · on this phone · connected". Never the address, the server's name or a folder (the report is public). It describes the connection for the maintainer; nothing on the page is gated on it. No connection: "none". |
| Recent diagnostics with secrets removed | `ReportProblem` (persisted, redacted at write) or the in-memory `AppDiagnosticsController`, then `problemReportScrub` (KitRedact keys/tokens/passwords/registered secrets, server addresses, IPs, long opaque tokens) | **Real.** Included as today. The include switch is removed: Details shows every line before anything leaves, and GitHub's form is still editable before posting. |
| "Attached to this report · 2 items" pill, attachment rows with checkmarks (concept B) | — | **Removed by the pick** (duplicate of the sentence above them). |
| Details fold with the mono text and Copy | `ProblemReport.text`, the exact text Copy sends and GitHub's fields carry (title, what happened, version, diagnostics) | **Real.** One `KitDetailsFold` inside the card; the copy action is the one Copy at the bottom (no second copy icon). The link-too-long case still says, before sending, that the diagnostics (or the whole report) are copied for pasting. |
| "Passwords and keys are excluded" | The scrub above | **Real, worded honestly:** they are taken out by pattern, so the line says "Passwords, keys and server addresses are taken out" rather than a promise that nothing secret can ever slip. |
| "Anything to add?" · Optional, "What were you doing when it happened?" | `KitField` multiline with the app-wide `KitDraft` (`oc.draft.report-problem.app`) | **Real.** Optional when a failure or a kept error is attached; required otherwise. |
| "Review your report on GitHub before posting." | `openExternalLink` opens GitHub's prefilled issue form; nothing is filed until the person submits it there; issues are public | **Real.** One quiet line above the buttons. |
| "Send as a GitHub issue" (mint, GitHub mark) | `ProblemReport.link()` → `openExternalLink` (asks first, names github.com) → `bugReportRepoUrl/issues/new` | **Real.** The one primary. The GitHub mark is Phosphor's monochrome `github-logo` (the app's icon set). |
| "Copy" | `KitAction.copy` of `ProblemReport.text` | **Real.** Secondary beside the primary. |
| Share report | `ShareOut.text` | **Removed from this page** (not in the pick): Copy covers handing the text elsewhere. |
| The errors list with Clear, opt-in crash reports, Android's exit history, performance timings | `AppDiagnosticsController` / `ReportProblem`, `CrashDiagnosticsController`, `AppDiagnosticsGateway.exitHistory`, `PerfTrace` | **Real, moved** one tap down: "Everything this phone kept" at the end of Details opens a page with those sections unchanged. The report still carries the same events. |
| Server icon at the card's top (concept) | The failure's kind is not known from a `KitReport` | **Adapted:** one neutral glyph (warning outline in the accent, never red or amber), the same for every failure. |
| Aurora behind the page | `KitScreen` page ground | **Not this screen's.** Captures show what the ground paints today. |

OpenCode 2 protocol, ServerCapabilities, Paseo and phone agents: nothing on this page calls a
server. Errors reach the report only through the app's own diagnostics (uncaught Flutter and
platform errors, start-up, thermal, Android exits, opt-in crash records, the phone agents
backend) and through the failure a surface attaches, for every backend alike. No capability flag
gates any element; no element reads a flavor to decide what shows.

## 2. What was built

- `lib/ui/screens/app_diagnostics_screen.dart`: Report a problem is one card (a bug glyph in its
  tile, the failure's title, one line saying what goes with the report, a hairline, and Details
  holding exactly the report text), the privacy line, the "Anything to add?" field (Optional when
  something is attached, "What happened?" and required when nothing was noticed), and a pinned
  "Review it on GitHub before posting. It will be public." over mint **Send as a GitHub issue**
  with **Copy** beside it. Send goes straight to `openExternalLink` (which still asks and names
  github.com); the preview sheet, the include switch and Share are gone. When the link is too
  long, that line says the diagnostics (or the whole report) will be copied to paste, and swaps
  with a cross-fade. From 1.5x text or a short window the buttons end the page instead of being
  pinned, and the title takes the headline face so a long word stays whole.
- `lib/ui/screens/app_diagnostics_kept_screen.dart` (new): **App diagnostics**, the kept errors
  with Clear, crash reports, Android's exits and timings, moved unchanged from under the old form;
  opened by "Everything this phone kept" at the end of Details.
- `lib/feedback/problem_report.dart`: `ProblemReport.build(connection:, fallbackTitle:)`; the
  version lookup can wait without a timer (the page shows "unknown+0" until the platform answers).
- Kit variants, every other screen unchanged: `KitField(optional: true)` ("Optional" at the end
  of the label line), `KitActionBlock(pair: true)` (primary and one short secondary in one row on
  a phone while both fit; stacks at large text or under `KitLayout.pairRowMinWidth`),
  `AppIconography.github` (Phosphor's filled `github-logo`, monochrome).
- Copy: 13 new keys in `app_en.arb` / `app_ar.arb` (and `kitFieldOptional`); 14 keys the old page
  used are removed. G28 kept (the no-error title is "No error noticed").

## 3. Visual passes against concept B

- Pass 0 (`pass0-before/`): today's page: intro sentence, "Attached:" line, the include switch,
  "Review report", the errors list and timings all on one page.
- Pass 1: the B structure. Gaps: the bar had no Back (capture now pushes the page); the card's
  Details box was invisible (details surface = card surface); 24 dp warning triangle read as an
  alert; the hint wrapped to two lines; the GitHub mark was an outline.
- Pass 2: card on `surface2` with a hairline and 20 dp padding so the mono box shows darker, as in
  the concept; the page's bug glyph in a tile; the filled GitHub mark; a one-line hint. Gaps: at
  320 dp and 2x text the pinned buttons took half the screen and the title broke inside
  "conversations"; the privacy glyph centred on three lines.
- Pass 3: large text puts the buttons at the end of the page and the title on the headline face;
  the privacy glyph sits on the first line; the hint cross-fades when it changes.
- Pass 4: gates. G17 refuses an accent tone outside the kit, so the card glyph and shield are
  neutral and mint is only on Send (the owner's one-mint rule); "Tell us what went wrong" was
  five words (G28), now "No error noticed" with "Say what went wrong." as its lead.
- Kept from the kit on purpose, where the concept differs: the 17 dp bar title (every page), the
  fold's "Details ⌄" row (chevron at the end), the filled field without a border, and the filled
  secondary Copy.

Final renders (`/home/eslam/Storage2/launch-footage/vision-report-problem/`): `main-dark`,
`main-light`, `details-open-dark`, `typed-dark`, `kept-error-dark`, `nothing-noticed-dark`,
`describe-first-dark`, `failed-job-dark`, `kept-on-phone-page-dark`, `small-320-text2-dark`,
`arabic-rtl-dark`. Capture: `tool/capture/vision_report_problem_test.dart`.

## 4. Checks

- Tests run (serial, `-j 1`): `app_diagnostics_screen_test`, `problem_report_test`,
  `recent_errors_plain_test`, `failed_job_report_ui_test`, `setup_progress_report_test`,
  `redaction_test`, `diagnostics_exit_share_test`, `exit_list_focus_test`,
  `crash_reports_section_test`, `coverage/diag_coverage_test`, `v2_feature_gating_test`,
  `bug_report_test`, `kit/kit_field_test`, `kit/kit_action_test`, `kit_ratchet_test`,
  `ui_glossary_test`, `l10n_coverage_test`, `l10n_new_locales_test`, `app_iconography_test`,
  `settings_server_updates_test`, `chat_live_events_test`: all pass except two outside this slice (not re-run on a base
  worktree): `redaction_test` "every verbatim copy in lib/ is reviewed" names a new
  `redact: false` in `lib/ui/kit/kit_viewer_build.dart` (Files slice, a file this branch does not
  touch), and `chat_live_events_test` "/files attaches a project artifact back to the chat" does
  not find `review.md` (the Files flow; nothing here is on that path).
- Goldens regenerated with `TZ=UTC` and looked at: `test/revamp/diagnostics_fd_golden_test`,
  `crash_reports_golden_test`, `screen_system_1_golden_test` (the diagnostics sections now sit on
  App diagnostics; the Report a problem shots show the new page).
- `flutter analyze`: no issues (after `flutter pub get` in `widgetbook/`).
