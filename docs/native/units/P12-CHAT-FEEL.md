VERIFIED: green — :feature:chat:desktopTest 143/143, :ui:kit-chat:desktopTest 112/112, :quality:test 48/48 (incl. the P3 6,000-word stream test on the new Markdown path), :androidApp:testDebugUnitTest (previewDebug) 11/11, :androidApp:assemblePreviewDebug OK. No emulator here (no KVM): the device stream benchmark was not rerun; see "Performance".

# P12-CHAT-FEEL — the chat screen feels native

Branch: `claude/chat-screen-native-parity-x5sslh` (based on `native/p12-chatbase-2026-11-03` @ `92991b8e`).
Commits: `5b7ff96f` (Markdown replies, code copy, message actions, links), `a407155f` (picture viewer, stream-safe
reveal, off-main code colour), `32f08343` (scroll place survives rotation/back), plus this report.

Finish line: on the phone, a reply reads like Flutter's (formatted Markdown, tappable links through the link policy,
code with Copy and sideways scroll, pictures that open), every message has the Flutter action list, and the reader's
place survives rotation and Back. Non-goal: the timeline sheet, agent cards, read aloud, staged undo review.

## Journeys now working (Android app, real gateway, no fakes in production)

| Journey | How to reach it | What happens |
|---|---|---|
| Read a formatted reply | Conversations → a chat | Reply rows draw through the kit's Markdown blocks (bold/italic/strike, inline code, links, headings, lists, quotes, tables, rules). Parsed by `:core:markdown` inside `:ui:kit-chat` (`KitMdSource`); revealed word by word while streaming, same reveal clock as before. |
| Open a link in a reply | Tap the link | `ChatActions.openLink` → `ChatOutside.checkLink` → `ExternalLinkPolicy.check(url, Markdown)`. Markdown links ask first: "Open link?" naming the host → **Open** → `ExternalLinkOpener` (ACTION_VIEW, BROWSABLE). Refused links (intent:, credentials, bad scheme) show "This link can't be opened from here." (dismissible), never open. |
| Copy a code block | Copy at the block's top-end corner | Copies the fence body verbatim, without fence lines, **whole across STREAM-FIX chunk rows** (`CodeCopy.of`). Lines never wrap; the block scrolls sideways. A one-row fence is syntax-coloured (`KitCodeBlock`); chunked fences draw one seamless box (`KitCodeChunk`). A fence still streaming keeps the stream-tuned block (fence lines hidden) until its reveal ends. |
| See a picture | A line of `![..](..)` in a reply | Picture tiles (`KitPictureTiles`) named by description, file name, or "Picture on the server: path"/"Picture from host". Web picture → link policy (never fetched). Server file → full-height sheet viewer (`KitPictureView`): read through the conversation's own file transport (`ChatWorld.readPicture` → `FileGateway.fileContent`, base64, ≤ 20 MB), pinch-zoom, pan, double-tap; failures are a plain line. |
| Message actions | Tap or long-press a prompt; tap a reply's "More" footer | Flutter order: Copy, Share, Select text, Edit and resend (prompt), Send again (prompt, not while a reply runs), Fork, Undo from here (saved prompt, `sessionRevert`, not while busy), Delete last. Long-press plays the kit's LongPress haptic (kit-owned). |
| Copy / Share a message | Menu → Copy / Share | Clipboard (`ClipData`) / system chooser, through `ChatOutside`; a failure shows the dismissible "That didn't finish" line. |
| Select text | Menu → Select text | A sheet with the message's words in the transcript's filtered selection container (Copy and Select all only, R17); Done or Back closes it. |
| Edit and resend | Menu → Edit and resend | The prompt's words replace the composer field once (cursor at end, focused), reported as the person's edit; nothing is sent until they press Send. Allowed while a reply runs (steer, never queue). |
| Undo from here | Prompt menu → Undo from here | "Undo from here?" (destructive confirm) → `ServerOperationsGateway.revertSession` once → the conversation is read again. Refusal: status line. |
| Follow the bottom / jump to latest | Scroll during a reply | Unchanged kit behaviour (follow while at the edge; "Jump to latest" pill). |
| Keep my place | Scroll up, rotate or open another screen and come Back | **Fixed:** the follow flag was saved from a stale copy, so returning pulled the reader to the newest reply. Now the saved state backs it; index and follow choice are restored. |
| Find in conversation | Top bar Search | Existing: "n of m" count, Previous/Next, partial-history note. |
| Predictive back | Back gesture | The guard is enabled only while a menu, sheet (select text, picture), confirm or find is open, so the system predictive preview stays on otherwise; Back closes the topmost first. |

## Catalog rows covered (tests named after them)

- `B-CHAT-056` code copy verbatim, fences dropped, whole across chunks — `ChatFeelPresenterTest`, `ChatFeelScreenTest`.
- `B-CHAT-059` pictures: web vs server, no hash names, viewer through own transport, failure plain — `KitMdSourceTest`, `ChatFeelPresenterTest`.
- `B-CHAT-060` block kinds keep their kinds (caret path unchanged) — `KitMdSourceTest`.
- `B-CHAT-064` Markdown drawn without marks; links/path candidates kept as spans; link asks first — `KitMdSourceTest`, `ChatFeelScreenTest`.
- `B-CHAT-120` Undo from here: capability-gated, not while busy, confirm, once, refusal reported — `ChatFeelPresenterTest`.
- `B-KIT-051` long-press opens the message's actions — `ChatFeelPresenterTest`, `ChatFeelScreenTest`.
- `09-6_6` copy/share/select/edit-and-resend; `09-7_3` place survives leave-and-return — `ChatFeelPresenterTest`, `ChatScrollRestoreTest`.
- `R17` link policy: ask-first and refusal — `ChatFeelPresenterTest`.
- P3 stream (`TranscriptStreamUiTest`) now exercises the shipping Markdown path.

Still missing (not in this slice): `B-CHAT-115` hit highlighting inside rows (count works, no highlight);
`B-CHAT-120` staged undo review (servers with staged revert get the direct undo only); copy label variants
("Copy complete reply / reply so far / loaded reply"); read aloud and "Continue with…" menu items; agent ```choices```
cards (drawn as plain code blocks until the cards unit).

## Performance

JVM headless P3 (6,413 words, 5,472 frames, Skia raster), same machine, run alone:

| Path | p50 | p95 | p99 | max | >8.3 ms | >16.7 ms |
|---|---|---|---|---|---|---|
| Before (plain text rows) | 3.51 | 6.04 | 8.20 | 65.8 | 51 | 2 |
| After (Markdown rows) | 3.40 | 6.49 | 9.37 | 86.0 | 89 | 10 |

The Markdown path costs ~0.5 ms at p95 on the JVM and more long-tail frames; numbers on this 4-vCPU container vary
±1 ms between runs. The test's gates (live edge every frame, every character in order, no reveal left running)
pass. Code colouring now runs on `AppDispatchers.default` (`KitChatEnvironment` provides `LocalKitChatWork`; before,
nothing provided it, so tokenising ran on the main thread). **Integrator: rerun `native/benchmark` (P3 stream
macrobenchmark) on a device/emulator** — not possible here (no `/dev/kvm`).

The P3 run found a real defect, fixed here: reveal states left half-done when a streamed row re-parsed into fewer
blocks or a fence settled into the code view; one orphan blocked every later block of its part. Blocks are now keyed
by row and position, kinds that draw at once settle any reveal under their key, and vanished indices are settled.

## Deviations / decisions

- `:feature:chat` may not see `:core:markdown` or `:core:security` (ModuleRules). Parsing lives in `:ui:kit-chat`
  (`KitMdSource`, one build line adding `:core:markdown`, allowed by the rules); the link decision reaches the feature
  as `LinkCheck` through the `ChatOutside` port.
- `ChatOutside` has a common default `NoChatOutside` (`@ContributesBinding`, nothing leaves the app) so desktop and
  `:quality`'s smoke graph resolve; Android replaces it in `graph/ChatFeelWiring.kt`. Without a port (old
  constructors, tests) the presenter keeps the slice-2 menu.
- `ExternalLinkOpener` is implemented in `ChatFeelWiring.kt` (`AndroidExternalLinkOpener`) but **not bound in the
  graph**, to avoid a duplicate binding with other units. Integrator: if another unit binds `ExternalLinkOpener`, make
  `AndroidChatOutside` take that binding instead.
- The P3 `ChatTranscript` overload now draws the shipping Markdown path and wraps itself in `KitStandaloneRedaction`
  (a fresh pattern-masking `Redactor`), since its callers (P3 test, benchmark proof screen) have no app graph.
- `KitChatEnvironment` gained an `@Inject` constructor with `AppDispatchers`; the one-argument constructor stays for
  tests (`:quality`'s `ChatReviewUiTest` uses it).
- Small edits outside the listed folders, needed for the journeys: `ui/kit-chat/build.gradle.kts` (one line),
  `ui/kit-chat/.../KitChatEnvironment.kt`, `ui/kit-chat/.../transcript/TranscriptList.kt` (follow flag fix).
- Copy: new `ui/words/.../strings_chat_feel.xml` (values + values-ar), following the `strings_composer_full.xml`
  precedent; the words owner should adopt it. Existing ids reused: `kitCopy`, `revertFromHere`, `kitSheetDismiss`.
- Inline pictures inside a sentence show a small frame glyph (▣) linked to a web address; line-only pictures are tiles.

## Integrator checklist

1. Merge; `ChatWorld` gained two default methods (`revertFrom`, `readPicture`) and `ChatActions` gained default
   methods — other chat units' implementers keep compiling.
2. Adopt `strings_chat_feel.xml` into the words layout (other locales fall back to English).
3. Decide the single `ExternalLinkOpener` graph binding (see above).
4. Run the device P3 stream benchmark (`native/benchmark`) and compare with the slice-0 numbers.
5. Commands used (JDK 21, `ANDROID_HOME=/opt/android-sdk`, Maven Central via a local init-script mirror because the
   container got HTTP 429): `./gradlew :ui:kit-chat:desktopTest :feature:chat:desktopTest :quality:test
   :androidApp:assemblePreviewDebug :androidApp:testDebugUnitTest`.
