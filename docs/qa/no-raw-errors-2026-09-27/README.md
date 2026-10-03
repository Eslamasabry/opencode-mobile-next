# No raw error text as the words (2026-09-27)

Owner bug report: a list showed `ApiException: upstream answered 502 while
reading page 2` in red as the words a person reads.

Branch `revamp/no-raw-errors` (from `feat/phone-setup-v2` at `05473fe1`).

## The rule, now in code

An error shows:

1. plain words saying what failed, in the app's voice, naming the thing
   (the title: "Could not load more conversations");
2. a way forward (Try again, the fix, Report a problem);
3. the technical text only under a Details fold / Copy details /
   `showKitTechnicalDetails`, redacted (`KitRedact`).

Never `'$error'`, `error.toString()`, an `ApiException` / `Api2Error` /
`PlatformException` message, native or Termux output, or an HTTP body as the
headline or the body. Sentences the app wrote for people
(`ProductException`, `SecureStorageUnavailable`, a `BuiltinLinuxException`
or `TermuxBridgeException` that is one plain sentence) still pass through.

## Shared root: `lib/ui/widgets/product_states.dart`

- `productErrorText(error)` classifies instead of passing messages through:
  - HTTP 401/403: "The server didn't accept the sign-in. Check the password in the server's settings."
  - 404/410: "The server couldn't find it. It may have been moved or deleted."
  - 409: "It changed on the server in the meantime. Refresh, then try again."
  - 429: "The server is busy. Wait a moment, then try again."
  - 5xx (502, 503…): "The server had a problem (error 502). Try again in a moment."
  - 400/422 with a short human reason: "The server didn't accept it: {reason}"; otherwise "The server didn't accept the request. Try again, or report the problem."
  - timeout: "The server took too long to answer. Try again."
  - TLS/certificate: "The server's security certificate isn't trusted, so the app stopped. Check the server address."
  - network (refused, unreachable, DNS, reset, `SocketException`…): "OpenCode is unreachable. Try again." (unchanged line)
  - `PlatformException` / native bridge text: "Something on this device didn't work. Try again."
  - storage (`FileSystemException`, no space): "The app couldn't read or save a file on this device."
  - Termux command output: "Termux didn't finish that. Check that Termux is installed and open, then try again."
  - an answer the app could not read (`ApiException` with no status and no transport cause): "The server's answer didn't make sense to the app. Try again, or report the problem."
  - a `String` is kept as words unless it reads as exception/transport text (class name, `HTTP 5xx`, `OS Error`, a body, `Cannot reach …:`), which is classified the same way. This covers `ConnectionController.lastError`, which holds raw `toString()` text.
- New `productErrorDetails(error)`: the redacted technical text for the fold
  (null when there is nothing beyond the words).
- New `productErrorKind(error)`: `KitErrorKind.network` also for the app's
  own transport failures, so the network fix (Switch server) comes first.
- `showProductError` folds the details under "Error details";
  `ProductErrorState` fills its fold from `error` when no `details` are given;
  `ProductRefreshBody` takes the `error` for Copy details.
- Kit contracts now say "words, not exception text": `KitStateView.body`,
  `KitNotice.message`, `KitField.error`.

## Direct leaks fixed

| File | Was | Now |
| --- | --- | --- |
| `screens/global_sessions_screen.dart` (owner's screenshot) | raw ApiException text under "Could not load more conversations"; load-failure state had no details | `KitNotice.error`: words + Try again + Copy details (redacted raw); state gets `details` |
| `screens/run_result_screen.dart:169` | `'$error'` kept as details text | `productErrorDetails` (redacted) |
| `screens/team/merge_section.dart:338` | `teamUiMergeUnavailable('$error')` | `productErrorText(error)` |
| `screens/project_folder_actions.dart:95,224,290,333` | `projectFolderCreate/CheckFailed(error.message / toString())` (native text) | `productErrorText(error)` |
| `widgets/form_renderer.dart:658` | `error.toString()` minus prefix as the banner | words; raw via Copy details / Report |
| `screens/staged_revert_screen.dart:223,399` | `'$error'` as details | `productErrorDetails` |
| `screens/session_note_screen.dart:222` | `Api2Error.message` as the body | `productErrorText` |
| `screens/termux_processes_screen.dart:134,226` | `TermuxBridgeException.message` as the body | `productErrorText` |
| `screens/termux_storage_screen.dart:136,169,180` | same | `productErrorText` |
| `screens/local_terminal_screen.dart:88` | `'$error'` as details | `productErrorDetails` |
| `screens/servers_screen.dart:519` | `ProductException(conn.lastError)` passed raw connect text as words | words, raw kept as `cause` |
| `screens/workspace_screen.dart`, `worktrees_screen.dart` | `error is String ? error : …` let raw strings through | all through `productErrorText` |
| `screens/running_work_sheet.dart`, `session_context_screen.dart`, `session_destination_sheet.dart` | `details: productErrorText(error)` (words in the fold, raw lost) | `details: productErrorDetails(error)` |
| `widgets/folder_browser.dart:1199` | `details: error.toString()` | `productErrorDetails` |
| `widgets/tool_card.dart:586,1567` | `chatUiFileLoadFailed(error)`, `snapshot.error.toString()` as notice text | `productErrorText` |
| `widgets/local_agent_onboarding.dart` (4 sites), `local_agent_server_entry.dart:279` | `LocalAgentFailure.message` / `lastError` (script output) as words | `productErrorText` (sentences stay, output said in words) |
| `widgets/phone_server_card.dart:326,473,512` | `BuiltinLinuxException.message` (Java/native text) | `productErrorText` |
| `widgets/phone_server_restart.dart:69`, `termux_running_server_entry.dart:216` | `LocalServerControlFailure.message` (Termux output) | `productErrorText` |

Checked and left as they are (raw text already only in details, or words):
`widgets/team_host_form.dart:490` (verdict Details fold),
`screens/settings/plugins_screen.dart:703` (technical value in a Details
fold, redacted), `screens/team/team_states.dart:51` (details),
`screens/mcp_setup_screen.dart:911` and `screens/web_sources_screen.dart:109`
(app-authored `ProductException` / domain `FormatException` sentences),
`kit/kit_scanner.dart:168` (device message shown only as details).

## Leaks in files other agents own now (hand to the owner; not edited)

Most of these already improve through the shared `productErrorText` change
where they call it; the lines below bypass it.

| Owner | File:line | What shows | Proposed fix / words |
| --- | --- | --- | --- |
| P5.3 phone setup | `screens/phone_setup/phone_setup_termux_job_screen.dart:210` | `TermuxBridgeException.message` (Termux output) | `productErrorText(failure)` |
| P5.3 | `…/phone_setup_termux_job_screen.dart:215` | `PlatformException.message` (native) | `l10n.e7SetupInspectTermuxFailed`, message to details |
| P5.3 | `…/phone_setup_termux_job_screen.dart:218` | `'$failure'` | `productErrorText(failure)`; `productErrorDetails` to a fold |
| P5.3 | `…/phone_setup_termux_job_screen.dart:344` | `PlatformException.message` | `l10n.termuxGuideCopyOpenFailed` |
| P5.3 | `screens/phone_setup/phone_setup_ready_screen.dart:177` | `phoneSetupReadyCreateFailed(error.message)` | `phoneSetupReadyCreateFailed(productErrorText(error))` |
| P5.3 | `…/phone_setup_ready_screen.dart:153,199` | `connection.lastError` (raw `toString()`) | `productErrorText(connection.lastError!)` |
| P5.3 | `screens/phone_setup/phone_setup_start_screen.dart:303` | `connection.lastError` | same |
| chat lane | `screens/chat_screen.dart:5898` | transcript export writes `> Error: {raw}` | export text, not UI copy; review whether the export should carry words |
| chat lane | `screens/chat_screen.dart:3720` | `_showActionError(_conn.connectionError …)` passes raw `lastError` | now said in words by `productErrorText`'s string check; nothing to do unless the chat lane wants its own words |

`test/no_raw_error_text_test.dart` allowlists the pending lines its scan
detects (the `lastError`/`status` rows are plain strings it cannot tell from
words); each entry fails the test once its line is fixed, so the owner
removes it with the fix.

## Follow-up after the merge (601b4ec1)

The files below were freed by their owners and are now fixed on this branch:

| File | Was | Now |
| --- | --- | --- |
| `widgets/team_phone_onboarding.dart` (rewritten by P1.7) | killed-team Start again: `teamUiPhoneActionFailed(error.message)`; failure fallback `Reason: {lastError / reason id / raw phase}`; turn-on failure `… ${e.message}` and `aiteamComponentFailed('$other')` | words through `productErrorText`; a bare reason id or phase says the Termux words; the notice becomes `KitNotice.error` with Start again and Copy details (`teamPhoneFailureDetails`) |
| `widgets/team_phone_section.dart:200,207,275,305` | `teamUiPhoneActionFailed(status.lastError ?? reason ?? rawPhase)` and `(error.message)` | `teamPhoneFailureText` / `productErrorText`; `KitNotice.error` with Copy details |
| `widgets/builtin_team_section.dart:111,345` | the script's last output line as the words; `aiteamComponentFailed('$error')` | the last line only when it is a sentence, otherwise words; raw via `productErrorDetails` into the existing Details. "Turn off AI Team" (P1.4) unchanged |
| `screens/usage_screen.dart:131` | `Api2Error() => error.message` | `productErrorText`; the notice has Copy details |
| `screens/settings/notifications_settings_screen.dart:83` | `backgroundLive.lastError` (native / `toString()`) as the words | the app's own Android sentence stays, exception text is said in words and kept under Copy details; a failed turn-off says "Android did not turn background mode off." |
| `widgets/first_reply_notify_card.dart:99` | same | same words rule (dismissible notice keeps its form) |

Still owned by others and forwarded by the coordinator: the phone setup
screens, `this_phone_screen`, `lib/builtin/setup` (P5.3) and
`chat_screen.dart` + `chat/**` (chat lane).

Follow-up checks: `flutter analyze` clean; the guard, `product_error_text`
and the tests of the six changed files (27 files) run once — every failure
also fails at `601b4ec1` in a temporary worktree except one
(`first_reply_notify_card_test` expected Android's own "Notification access
is required.", which the words rule now keeps), fixed and re-run.
`builtin_team_section_test` now expects words, not the script's
"dirty tables".

## Guard and behaviour tests

- `test/no_raw_error_text_test.dart` (new): source scan of `lib/ui` for
  interpolated caught errors (`'$error'`, `'${e}'`), `error.toString()`,
  `snapshot.error.toString()`, and `.message` of `ApiException`,
  `Api2Error*`, `PlatformException`, `TermuxBridgeException`,
  `BuiltinLinuxException`, `LocalAgentFailure`,
  `LocalServerControlFailure` and IO exceptions; lines that feed a Details
  fold are allowed. Self-test proves the scan finds each pattern; a
  stale-allowlist test keeps the list shrinking.
- `test/product_error_text_test.dart`: mapping per kind (502 → words, raw in
  details; 401/403/404/409/429/5xx/other 4xx; timeout, certificate, network;
  OpenCode 2 errors; raw strings; PlatformException; built-in Linux and
  Termux; redaction), plus widget tests: `ProductErrorState` shows words +
  Try again with the raw text only after opening Details; `showProductError`
  shows words with a Details fold.
- `test/global_sessions_screen_test.dart`: the owner's scenario end to end —
  page 2 fails with a 502, the list keeps its rows, says "Could not load more
  conversations" / "The server had a problem (error 502)…", no
  `ApiException`/`upstream`/`HTTP 502` on screen, and Copy details puts the
  raw text on the clipboard.

## Verification (local, 2026-09-27)

- `flutter analyze`: no issues.
- Tests of changed files: 124 files (every test importing a changed lib
  file, plus tests asserting raw HTTP/exception text), run once with
  `--concurrency=3` in six chunks. 578 failing tests; the same files at the
  base commit in a temporary second worktree (`05473fe1`) fail 567 of them
  (pre-existing: mostly goldens that differ on this machine). The 11 new
  failures were all fixed and re-run:
  - `chat_live_events_test` (2), `form_renderer_test` (1): asserted the raw
    `ApiException`/`Exception` text on screen; now assert the words and that
    the raw text is absent.
  - `goldens/chat_form_sheet_golden_test` send failed: the fixture now
    throws an app-authored `ProductException` so the golden keeps its words.
  - `revamp/screen_review_2_test`: kept the screen's own sentence for
    unknown errors (run_result_screen keeps its fallback body).
  - `revamp/shared_system_1_golden_test` network error (4 goldens): the
    state now has Copy details and a Details fold; goldens updated.
  - `safety_confirms_test` server page (2): the longer health-error words
    left the Disconnect button at the viewport edge; the test now
    `ensureVisible`s it before tapping.
  - Re-run of those files and the new tests: no failure outside the base
    list.

## Images

Before (base `05473fe1`) and after, 412×915, light, rendered by the widget
tests' capture harness (`tool/capture/fixtures.dart`):

| | Before | After |
| --- | --- | --- |
| Conversations, page 2 answers 502 (the report) | `before_1_conversations_page2_502.png` | `after_1_conversations_page2_502.png` |
| New worktree, server 500 with git's text | `before_2_worktree_create_500.png` | `after_2_worktree_create_500.png` |
| Agent form, send refused (400) | `before_3_form_send_400.png` | `after_3_form_send_400.png` |
| Attach failed on the device (PlatformException) | `before_4_attach_failed_device.png` | `after_4_attach_failed_device.png` |

`contact_sheet.png` puts the four pairs side by side.

## Notes

- Accessibility: words are shorter and plain; the Details fold and Copy
  details are the kit's own (focusable, labelled). No new controls.
- Privacy/security: raw text now reaches only the fold, Copy details and
  Report, each through `KitRedact`; before, credentials inside a server body
  could show as the words.
- Copy: English only in `lib/l10n/app_en.arb` (keys `productError*`),
  generated with `flutter gen-l10n`.
- Shipping state: implemented and verified locally; committed on
  `revamp/no-raw-errors`; not merged, not deployed.
