VERIFIED: green

# P12-CHAT-BUGS — wave 1 QA blockers (chat)

Branch `claude/chat-parity-native-android-1jkypc`, based on `native/p12-qa-wave1-2026-11-03` (`42b8aa2d`).
Source of the bugs: `docs/native/qa/wave1/README.md` (+ `LOG.md`, screenshots). Every fix has a test named after
its bug id (`B<n> QA wave1 …`); the S1/S2 engine tests were run red against the unfixed code first (noted below).

## Bugs, fixes and tests

| Bug | Sev | Root cause found | Fix | Test(s) |
|---|---|---|---|---|
| B0 | S1 crash | OC2 gives every notice part the id `v2-0` (`Oc2History.special()`, `Oc2EventAdapter.instructions()`); `TranscriptModel` keyed notice rows `notice:${part.id}` → two notices = duplicate LazyColumn key. | Notice row key is `notice:<messageId>/<partId>` (like `ThoughtRow`); OC2 notice parts get the stable unique id `notice-<messageId>` (`adapter/noticePartId`, shared by live adapter and refetch mapper); `ChatTranscriptList` drops any repeated key at the list boundary (`distinctKeys`, same instance when unique). | `engine:transcript` `B0 … two notices that share a part id get distinct row keys`; `protocol:oc2` `B0 … every notice message gets its own part id`; `feature:chat` `QaWave1ChatTest` `B0 …` ×2 |
| B2 | S1 | `Oc2Gateway` publishes `status = Connected` and `connects += 1` as two updates; `ConnectionDelivery` wraps the same gateway in a **new** `GatewaySendLink` on each, and every delivery fence compared link **instances**. A send that suspended across a (re)connect (the GET before the POST, the model POST + readback) was refused `StaleScope` → "wasn't sent", no POST. Matches the QA pattern (first send in a new chat, after the file picker, after a model chip, first steer). | `SendLink.epoch` (default: the instance; `GatewaySendLink`: its gateway). `PromptDelivery.sameTarget` and both `OfflineQueue` fences compare `epoch`, so a status flicker over the same gateway is the same target, while a different gateway (B-STATE-119 "retired transport") is still refused. Never resends: the fence still runs before bytes leave. The not-sent bubble now says why in plain words and shows the failure code under **Details**. | `engine:delivery` `QaWave1DeliveryTest` `B2 … a stream flicker during the first send still sends it exactly once` (**red before**: session readback happened, 0 prompts — the QA symptom); `QaWave1ChatTest` `B2 … failure kind under Details` |
| B10 | S1 | Same fence: a Stop whose transport wait crossed a flicker returned `StaleScope` with no `/interrupt`. Also the presenter dropped "Stopping…" the moment the call returned. | Epoch fence (above). `GraphChatWorld.stop()` falls back to the gateway's own interrupt when the engine refused with a stale fence. `ChatPresenter` keeps "Stopping…" after an accepted Stop until the turn ends (`settleStop`), then settles; a refused Stop shows the action failure. | `QaWave1DeliveryTest` `B10 … Stop during a stream flicker still interrupts` (**red before**: `Refused(StaleScope)`); `ChatPresenterTest` `B10 … stopping until the turn ends, then settles` |
| B11 | S2 | The "delivered steer marked unsent" bubble is the **first** tap's bubble (refused by B2, nothing sent); the second tap's send was delivered, but the first bubble kept "wasn't sent" + Try again (duplicate risk). | When a send is accepted or settles, earlier **failed** bubbles with the same words and files are retired (`retireFailedTwins`). B2's fix removes the first refusal itself. | `WorldComposerFilesModelsTest` `B11 … a delivered resend retires the earlier not-sent bubble` |
| B1 | S2 | `PreviewRuntime.refreshOne` read page 1 and set `complete = nextCursor == null`. | `readSessionPages` (androidApp `graph/ChatBugsWiring.kt`) follows `nextCursor` up to 10 pages (1000 conversations), never the same cursor twice; more pages is not a failure, only a failed later page marks the list incomplete. | `androidApp` `SessionPagesTest` `B1 …` ×3 |
| B3 | S2 | Flat `KitChipWrap` of every model, label only. | Picker sheet: search field (`KitSearchField`), "Recent" group first (verified picks this app run, per server, memory only), then one `KitRowGroup` per provider **named by the provider** (current provider first), `KitChoiceRow` rows, the current model marked "Current" (the server default row when the session names no model). Provider name comes from the catalog (`ComposerModel.providerName`). The keyboard is dismissed when the picker opens. | `ModelPickerTest` `B3 …` ×3; `QaWave1ComposerTest` `B3 … groups models under their provider`; `WorldComposerFilesModelsTest` `B3 … a verified pick becomes the first recent model` |
| B5 | S2 | The footer only knew `failed: Boolean`. | `FooterRow.reason` carries the assistant's (already redacted) error text; the chat shows "Didn't finish" + a plain reason with the next step (sign in to the provider again / pick another model / wait / send again — `ReplyFailureKind`), and the server's words only inside a `KitDetailsFold` ("Details", copyable). | `TranscriptReducerTest` `B5 …`; `QaWave1ChatTest` `B5 …` |
| B6 | S2 | On a 640 dp phone the composer took ~165 dp: the message field is `Multiline` = **3 empty lines** minimum. | `KitField(multilineLines = …)` (kit, default unchanged 3..8); the chat composer uses 1..6, so an empty box is one line and the transcript keeps its room above the keyboard (the reverse list stays anchored on the newest row). | `QaWave1ComposerTest` `B6 … one line tall, not three` |
| B8 | S2 | Field, Stop and "Add to this turn" shared one row with `weight(1f)` on the field. | While a reply runs, the field takes the full width and Stop + Add to this turn sit on their own row underneath (end-aligned). | `QaWave1ComposerTest` `B8 … keeps the full width` |
| B4 | S3 | Not reproduced in code (no path found that fails without a request). | Mitigated: the model choice now loads when the chat opens (B13), so the first picker open finds it; a failed load keeps its Try again. | covered by B13 test |
| B13 | S3 | Models were read only when the picker opened, so the chip said "Server default" after a restart. | The world loads the catalog + the session's selection once the server's real capabilities are known (not the `Unknown` defaults). | `WorldComposerFilesModelsTest` `B13 … the chip names the session's model on open` |
| B14 | S3 | After the language-switch `recreate()`, the bars showed the platform theme's grey window background. | `applySystemBars` paints the window background with the app ground; `enableEdgeToEdge()` moved after `super.onCreate` in `MainActivity`. **Needs an emulator check** (Robolectric cannot reproduce the recreate). | `ui:kit` `AndroidKitPlatformTest` `B14 …` |
| B15 | S3 | — | First-run buttons keep a 16 dp side margin; Conversations header says "New chat" and "Settings" (the door opens Settings; the title is no longer cut). Empty state still says "New conversation". | `WelcomeScreenTest` `B15 …`; `ChatsScreenTest` `B-NAV-037 B15 …` |

Not done from B15 (out of the listed scope): Settings › Server › Servers depth, add-server form keeping values,
"Connect" on a connected card, empty-chat state, thinking label, "What's new" a11y. Not touched (other units):
B7, B9, B12.

## Catalog rows

Covered/strengthened: B-NAV-034 (picker leads with the current model and its provider), B-STATE-119 (a retired
transport is still refused: the fence compares the gateway, not the wrapper), B-PROTO-025 (steer delivery path),
B-STATE-043 (never resend: B11 retires only *failed* twins). The QA bugs themselves have no catalog ids; tests are
named `B<n> QA wave1 …`.

## Strings

New file `ui/words/.../values/strings_chat_bugs.xml` + `values-ar/strings_chat_bugs.xml` (English + Arabic), so
parallel chat units never edit the same lines. Other locales fall back to English.

## Write set outside the chat feature (integrator note)

Shared modules touched because the root causes live there: `engine/delivery` (epoch fence), `engine/transcript`
(notice key, `FooterRow.reason`), `protocol/oc2` (notice ids), `ui/kit` (`KitField.multilineLines`,
`applySystemBars`), `ui/words` (new strings file), `feature/chats` + `feature/onboarding` (B15), androidApp
(`PreviewRuntime` one call, new `ChatBugsWiring.kt`, `MainActivity` one line, `:core:testing` as a test dependency).
`ChatTranscript.kt` is also edited by CHAT-TRANSCRIPT/CHAT-FEEL: my edits are the `distinctKeys` boundary, the
failed-footer branch and the not-sent Details — merge by hand.

## How to reach each screen

Conversations → any conversation (B0, B2, B5, B6, B8, B10, B11); model chip in the composer (B3, B4, B13);
Conversations list on a server with >100 conversations (B1); Settings › Language (B14); first launch (B15).

## Build environment note

Maven Central answered HTTP 429 to this container; a local `~/.gradle/init.d` script pointed Central at Google's
mirror (not committed). JDK 21 (Gradle) + foojay JDK 17.

## Gate results (candidate = this branch's head; JDK 21, Android SDK 37.1, build-tools 37.0.0)

| Command | Result |
|---|---|
| `./gradlew desktopTest` (every module) | 2132 tests, 0 failed, 1 skipped (pre-existing skip in `protocol:oc2`) |
| `./gradlew testDebugUnitTest` (every module, incl. `:androidApp` preview-debug) | 1040 + 14 tests, 0 failed |
| `:ui:kit:testAndroidHostTest` (Robolectric, incl. B14) | 180 tests, 0 failed |
| `:quality:test` | 48 tests, 0 failed |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL (`androidApp-preview-debug.apk`) |

Red-first proof for the S1 engine bugs: with `PromptDelivery.sameTarget` reverted to the instance check,
`B2 …` failed (`session` readback made, 0 prompts sent) and `B10 …` failed (`Refused(StaleScope)`); both pass with
the fix. The first full run of `android:{camera,media,notifications,voice,platform}` failed only because Robolectric
fetches its Android jars from Maven Central at run time (HTTP 429 here); with the local mirror they pass.

**Not done here:** no emulator/device run. The integrator should re-run the wave 1 sweep, especially B0 (open the
"READY" conversation), B2 (first send in a new chat / after the file picker / first steer), B10 (Stop → relay shows
`POST …/interrupt`), B14 (Language → Arabic → bars keep the app ground) and B6 (keyboard open on a phone).
