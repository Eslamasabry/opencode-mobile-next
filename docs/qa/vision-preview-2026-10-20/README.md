# In-app preview, vision slice (2026-10-20)

Pick A from the vision page ("In-app preview"): the running app fills the
screen under one slim header, and one glowing field at the bottom takes the
change request into the conversation the preview belongs to.

## Audit: element → real source → verdict

Written before editing, from current source.

| Element (concept A) | Real source today | Verdict |
| --- | --- | --- |
| The running page, full bleed | `KitWebPreview` (`lib/ui/kit/kit_web_preview.dart`): one origin, no JS bridge, cookies cleared; target from `DevPreviewTarget.resolve` (`lib/domain/dev_preview.dart`). Works for every server kind the app can reach (phone loopback, remote host with the dev server on 0.0.0.0). Not gated on a capability: it needs only an address. | **Build** (exists). The aurora the concept paints inside the page is an image artifact: the page is the dev server's own pixels. |
| Header "Preview · localhost:8080" | Port from the link the conversation wrote (`DevPreviewLink.port`). The agent wrote `localhost:<port>` whatever host the phone actually loads, so that is the name the person knows. | **Build**: one line, "Preview · localhost:8080". The real loaded address (127.0.0.1 or the remote host) moves under Details (vision: "URL out of the header into Details"). |
| Reload icon | `KitWebPreviewController.reload()`. | **Build** (icon). |
| Open in browser icon | `openExternalLink` with the loaded address (security invariant). | **Build** (icon, always beside Reload, no ⋯). |
| ⋯ menu (Copy address, Logs) | Copy: the loaded address. Logs: only when the preview was opened for a server the app found running (`onLogs`). | **Remove the ⋯.** Both move into a Details sheet opened from the title line ("localhost:8080 ⌄"), with the remote-host note. |
| "Ask for a change…" field | No existing path sends a prompt from outside the chat library. The chat sends with `ConnectionController.prepareActionTransport()` → `waitForSessionSelection` → `ensureInteractiveSendAllowed` → `ServerGateway.promptAsync(sessionID, text, model/agent/variant for the session, delivery)`; `isolated_task_sheet.dart` uses the same public calls from outside the chat. Delivery while the agent works: `steer` when `ServerCapabilities.promptSteering` and the session is busy (`busySessions`), else the server's normal path — the same rule as the chat's `_activeDelivery`. | **Build**, through those public calls. Shown only when the preview knows its conversation: opened from a conversation (the chat calls `openDevPreview` with its own context, whose widget is `ChatScreen(sessionID)`), not isolated. Opened from Development services or the phone's ports page there is no conversation, so there is no field (honest). |
| Screenshot attached to the request | Nothing in the app captures a web view's pixels (`RepaintBoundary.toImage` cannot read a platform view; no `takeScreenshot` in webview_flutter 4.x). No "screenshot back to chat" path exists. | **Leave out.** The request is text only. |
| Mic in the field | On-device dictation: `VoiceComposerController.create()` + `showVoiceAutomaticSetupSheet` (`lib/voice/`), the same recognizer the chat uses; `platformCapabilities.supportsVoice` (Android). | **Build** where voice is supported: the words land in the field; nothing is sent until Send. Hidden elsewhere. |
| Mint glow around the field | Kit glow (as `KitStartBox`, still, no loop). | **Build** as a new kit part `KitAskField`. |
| "Live · updated 5 s ago" chip | No source: the app is not told when the page changed. | **Reject** (vision). |
| Suggested chips (Faster ball / Bigger paddle) | Invented by the image. | **Reject** (vision). |
| Phone / Tablet / Wide switch | — | **Reject** (vision). |
| Agent's last line under the page (concept B) | Would need the chat's transcript inside this route. | **Not built** (B was not picked); after sending, one quiet line says it went to the conversation. |
| Score, bricks, level text | The page's own content. | Not ours: the capture uses a stand-in page painted by the capture test (labelled as such in the capture source). |

Paseo / phone agents: the send uses the same gateway call as the chat for
every protocol (OC1, OC2, Paseo, phone agents); `promptSteering` decides
steer vs. the normal path, never the flavor.

## What was built

- `KitAskField` (`lib/ui/kit/kit_ask_field.dart`): new kit part. One solid
  pill with a still accent ring and halo, the field, and one round accent
  button (mic while empty, Send once typed, a check while listening, progress
  while working); a quiet note line above it (check for done, info for not
  done; never red or amber). `prominent: false` turns the button quiet when
  something else is the screen's one main action.
- `DevPreviewScreen`: one slim bar "Preview · localhost:8080 ⌄" with Refresh
  page and Open in browser (no ⋯), a hairline, the page full bleed, and the
  field. Tapping the title opens "Preview details" (the real address with
  Copy, Logs when known, the 0.0.0.0 note on a remote server). The remote
  note also shows over the page when nothing answers. At 2x text on a phone
  the bar keeps "Preview" and moves Open in browser and Details to the
  overflow (the kit's 200 % rule); the address joins the title only when it
  fits (measured).
- `DevPreviewConversation` (`lib/ui/screens/dev_preview_conversation.dart`):
  the send path, read from the chat's own context; no edit to the chat
  library.
- Kit fix: a tappable top-bar title is centred on the line of Back and the
  actions (it sat at the top of its 48 dp target).

## Visual passes (capture: `tool/capture/vision_preview_test.dart`)

1. First render vs concept A: title sat ~9 dp above the icon line; no
   hairline under the bar; field inset 32 dp instead of 16; ring and halo far
   weaker than the concept.
2. Fixed those (kit title centring, header hairline, field on the gutter,
   1.5 dp ring at 85 % accent, stronger halo). New problems: at 320 dp/2x
   "Preview" broke mid-word and the field grew to 4 lines; the round button
   floated mid-field when typing several lines; the Details sheet carried a
   needless icon tile.
3. Fixed: two lines at large text, button pinned to the last line, no icon
   tile, Open in browser + Details into the overflow at 2x on a phone. New:
   the remote note's icon was clipped at the screen edge; two solid-mint
   actions in the "nothing answering" state.
4. Fixed: note on the gutter; the field's button turns quiet while Try again
   is the main action. Then the catalog's Arabic pass showed the title
   ellipsized, so the address now joins the title only when it fits; the
   hint wraps to two lines at 2x; the Arabic scene types Arabic.

Images: `/home/eslam/vision-assets/out/vision-preview/` (main-dark,
main-light, typed, sent, sent-while-working, not-sent, listening,
nothing-answering, nothing-answering-remote, details, no-conversation,
small-320-text2 (+typed), arabic-rtl (+typed)). The page in them is a
stand-in drawing of the previewed game (a platform web view cannot render in
a test).

## Checks run

- `flutter analyze`: no issues.
- `test/dev_preview_screen_test.dart`, `test/kit/kit_ask_field_test.dart`,
  `test/kit/kit_web_preview_test.dart`, `test/kit_ratchet_test.dart`,
  `test/design_standard_test.dart`, `test/kit_motion_test.dart`,
  `test/ui_glossary_test.dart`, `test/text_scale_overflow_test.dart`,
  `test/redaction_test.dart`, top-bar goldens and tests
  (`test/goldens/kit/kit_top_bar_golden_test.dart`,
  `test/kit/kit_top_bar_test.dart`, `test/kit/kit_top_bar_r1_test.dart`,
  TZ=UTC): all pass, goldens unchanged.
- widgetbook: `DevPreviewScreen` catalogued (two use-cases), coverage check no
  longer lists it; its use-cases have no standard findings. The standard
  ratchet still fails on other screens' findings (chats, servers, schedules)
  that are not part of this change.

## Follow-up (2026-10-21)

The address left the header entirely (vision gaps): the bar always says
"Preview ⌄" and the address lives only in "Preview details". The width
measuring and the `devPreviewTitleAt` copy are gone.
