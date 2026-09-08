# Chat page review

Finish line: read the reply, open requested scope/change evidence, allow or reject, and return to the preserved draft with truthful pending/outcome state and less routine composer chrome.

Non-goal: global theme or icon-system replacement, model-picker persistence, diff rendering, server/protocol/storage changes, live permission responses, signing or release.

Persona: a developer directing and reviewing work one-handed on Android, often interrupted. The repeated job is resume → instruct → inspect → decide → leave and return. This is an inferred working persona, not participant research.

Evidence baseline: latest-dev APK current-run screenshots03/04/05/25/26/27 under the coordinator audit folder. Source starts at integration0eabc2d, including prior quiet-chat changesb057d4c. APK observations and integrated-source changes are different evidence. Screenshot optical judgments are not frame-time or TalkBack results.

## Reference observations

Inspected [GitHub composer](https://mobbin.com/screens/b25acfc7-3a72-4f4f-b769-683a63d557d5) and [Meta AI composer](https://mobbin.com/screens/689f27d1-f469-4105-854a-1fec0490e7a4), local mobbin/chat/image-01.jpg and image-03.jpg with result.json. Both let the text field lead and keep action controls optically small. We retain Android48dp targets and OpenCode-specific model/permission truth; these images do not justify copied iOS geometry, provider certainty, or hiding consequential scope.

## Findings and decisions

### CHAT-01 — Short-code controls dominated the snippet (P2, integrated)

Observed: Baseline has three labeled actions wrapping above one code line.

Impact: Reading requires scanning more chrome than content.

Decision: Keep localized accessible 48dp icon actions in one row.

Evidence: screenshots/03-chat.png, lib/ui/widgets/markdown.dart:1265.

Verification: Prior b057d4c focused tests and four screenshots; present in0eabc2d.

Commit: b057d4c

### CHAT-02 — Idle draft reserved a second empty line (P2, integrated)

Observed: Baseline empty composer starts at two lines; integration starts at one.

Impact: Keyboard leaves less conversation visible.

Decision: Grow with typed content while retaining editor identity.

Evidence: screenshots/04-keyboard.png, lib/ui/screens/chat/composer.dart:406.

Verification: Prior b057d4c growth/draft tests at320dp1x/2.5x.

Commit: b057d4c

### CHAT-03 — Inline approval precedes requested-change review (P1, tested)

Observed: Inline Allow once is stronger than Review; diff exists only inside review.

Impact: Encourages a consequential decision before its available evidence.

Decision: One inline Review action opens scope and preview before Allow once or Reject.

Evidence: screenshots/25-permission-ready.png, screenshots/26-permission-review.png, lib/ui/screens/chat/attention_card.dart:165, docs/qa/page-reviews/chat/.

Verification: chat_permission_test + demo_isolation_test and chat_review captures pass: no inline authorization; review exposes preview; dismiss sends nothing; allow/reject preserve identity and draft.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-04 — Persistent grant sits between ordinary decisions (P2, tested)

Observed: Allow once, Always allow, Reject are stacked in that order.

Impact: Reject is visually subordinate and farther from its peer decision.

Decision: Pair Reject and Allow once; keep Always allow as a lower-emphasis action with broader confirmation.

Evidence: screenshots/26-permission-review.png, lib/ui/screens/chat/permission_sheet.dart:470, docs/qa/page-reviews/chat/.

Verification: permission_sheet_test passes at320dp1x/2.5x: both ordinary decisions are hittable48dp before persistent access; always-allow confirmation retained.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-05 — Generic permission shield repeats despite different actions (P2, tested)

Observed: Edit request uses same administrative shield as every other permission.

Impact: Icon does not help recognize what the agent intends to do.

Decision: Use action-specific edit, terminal, file, web and folder symbols consistently across card and review.

Evidence: screenshots/25-permission-ready.png, screenshots/26-permission-review.png, docs/qa/page-reviews/chat/.

Verification: Reviewed dark-ready/dark-review and demo-permission-light captures: edit glyph consistent and paired with action text; bash cases compile/pass focused tests.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-06 — Routine context usage is communicated twice (P2, tested)

Observed: 4% appears beside the model and as a full-width meter separating editor and controls.

Impact: Routine metadata adds a structural divider with no immediate decision.

Decision: Retain exact known percentage; reserve meter for existing70% warning threshold and90% danger threshold.

Evidence: screenshots/03-chat.png, lib/ui/screens/chat/composer.dart:414, docs/qa/page-reviews/chat/.

Verification: Four selected chat_live_events cases pass:25% is visible without meter,75% paints actual meter, unknown limit remains absent.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-07 — Message highlight fade ignores reduced motion (P2, tested)

Observed: Highlight container always animates180ms while nearby chat transitions respect disableAnimations.

Impact: Same preference behaves inconsistently while navigating a reply.

Decision: Honor MediaQuery.disableAnimations for message highlight.

Evidence: lib/ui/screens/chat/message_view.dart:1376, docs/qa/page-reviews/chat/.

Verification: Timeline stable-anchor journey passes with reduced motion; highlight transition duration is zero and selected source remains visible.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-08 — Overflow opens a dense page of mixed tasks (P2, deferred)

Observed: Six equally framed views precede display toggles and session mutations.

Impact: Finding a result requires scanning an oversized secondary surface.

Decision: Prioritize result review and find; defer broader command taxonomy to a dedicated navigation slice.

Evidence: screenshots/05-chat-menu.png, lib/ui/screens/chat/session_sheets.dart.

Verification: Screenshot and source inspection only; no redesign claimed.

Commit: No implementation commit; deferred.

### CHAT-09 — Draft and pending-request truth must survive review (P1, tested)

Observed: Draft persistence and identity-bound request retirement already exist; demo completion explicitly says simulated.

Impact: Simplification must not turn dismissal into a decision or claim an edit from an acknowledgement.

Decision: Preserve existing persistence, request identity, failed reply recovery and capability gates.

Evidence: lib/ui/screens/chat_screen.dart:5957, lib/ui/screens/chat/permission_sheet.dart:120, screenshots/27-demo-complete.png.

Verification: Preserved safeguards verified by focused permission races/failure/dismissal, composer draft persistence, and demo isolation tests. This is retained behavior, not a newly discovered defect.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-10 — Pending permission button loses its wording (P2, tested)

Observed: Allow once and Send rejection replace labels with a spinner while sending.

Impact: The user loses the action identity at commitment.

Decision: Keep the action label alongside a small pending indicator.

Evidence: lib/ui/screens/chat/permission_sheet.dart:479, docs/qa/page-reviews/chat/.

Verification: Pending-reply test passes: Allow once text remains visible, action disabled, duplicate tap dispatches no second request.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

### CHAT-11 — Embedded demo repeats navigation and contradicts its sample task (P2, tested)

Observed: Offline-demo wrapper and nested Chat app bar stack two navigation headers; generic starter prompts compete with the prefilled small-change task.

Impact: A first-time user must choose a new task instead of following the safe prepared example.

Decision: Explicit showAppBar/emptyState presentation overrides, used only by DemoScreen; task-specific introduction and preserved contextual Review changes.

Evidence: screenshots/23-demo-start.png, lib/ui/screens/demo_screen.dart, lib/ui/screens/chat_screen.dart, docs/qa/page-reviews/chat/.

Verification: demo_isolation passes including320dpkeyboard/2.5x, reset/exit and real-state isolation; demo captures show one header, explicit task, retained review and truthful simulated completion.

Commit: 22c97ad3c3770796f28e5e918287b5cc53b9b76c

## Component decisions retained in this slice

The transcript stays opaque and selectable. Body text continues to use the app's reading face; code and literal paths keep the mono face. Global font size, palette and radius changes belong to the coordinator, so these captures must not be mistaken for their final integrated appearance. No gradient or blur was added behind text.

The composer keeps 16/14/16/8 field padding, a full-width editing rail and 48dp actions; attachments and delivery choice remain conditional on state/capability. The existing context percentage remains available at low usage. Only the redundant full-width meter becomes conditional at the existing warning threshold. Unknown context limits still show no estimate.

The permission card remains below the transcript and above the draft, with its existing live announcement and constrained width. Its resource title and summary remain text, so the new action glyph never bears the meaning alone. Review is a labeled button rather than an unexplained icon. The sheet keeps literal command/path/diff previews and a complete diff route for long changes. One-time Allow and Reject become peers before persistent grant; 2.5x text gets a vertical layout. Android Back/dismiss makes no decision. Optional rejection reasons and persistent-grant confirmation remain protocol-correct.

No measured frame rate, actual-device TalkBack pass, live approval/rejection, successful file edit, model availability or production recovery is claimed. The fixture capture will show request retirement and unchanged draft, not fabricate an agent success reply after rejection.


## Verification boundary

Implementation/capture candidate: `22c97ad3c3770796f28e5e918287b5cc53b9b76c` on `design/quiet-chat`, built from integration `0eabc2d`. All checks below ran serially on the unchanged implementation tree before committing. SDK: Shorebird Flutter3.47.2 / Dart3.13.2, framework `e16cf749ccaa38d7050335ff305def49b1c7c84c`.

- `flutter gen-l10n` passed once after copy settled; new keys `demoTaskTitle` and `demoTaskInstruction`.
- `flutter test --no-pub --concurrency=1 test/chat_permission_test.dart test/permission_sheet_test.dart test/chat_question_card_test.dart test/demo_isolation_test.dart test/composer_layout_test.dart test/markdown_reading_test.dart` — 79 passed.
- `flutter test --no-pub --concurrency=1 test/chat_live_events_test.dart --name 'composer shows known context usage|composer hides the context meter|timeline finds a stable anchor'` — 4 passed. This is selected coverage, not the full live-event file.
- `flutter test --no-pub --concurrency=1 tool/capture/chat_review_test.dart tool/capture/demo_test.dart` — 4 passed; 12PNG outputs in `../chat/`.
- Scoped `flutter analyze --no-pub` over chat_screen, chat/, markdown, demo_screen, the five changed test files and three changed capture files — clean, no issues.
- Final formatter check over17 changed/new Dart files — zero changes. `git diff --check` — clean.

Visually inspected `dark-ready`, `dark-review`, `light-returned`, `demo-start-light`, `demo-permission-light`, and `demo-complete-dark`. These cover every captured state across the two themes; complementary color-mode captures are also retained. The sample diff stays visible before the ordinary decisions. The cleared attention card does not fabricate a successful edit. The demo has one header and preserves explicit simulation disclosure.

The coordinator's new shared13/19 typography and Phosphor icon migration were not in this worker checkout. Markdown uses the requested19/13 line-height ratio, ready for replacement with the shared constant at integration, and inline code/path labels no longer shrink relative to surrounding text. Final integrated captures, full analyzer/suite, ADB deployment and actual-device frame/TalkBack evidence remain separate gates. No signing, CI, push, live provider call or real permission response was performed.
