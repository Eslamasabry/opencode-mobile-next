VERIFIED: green

# P12-CHAT-CARDS — agent cards (gen-UI) in the native chat

Date: 2026-11-02 (parity push of 2026-11-03). Branch: `claude/chat-cards-native-parity-mvrql7`, based on
`native/p12-chatbase-2026-11-03` (`92991b8e`). Reference: Flutter `lib/domain/genui/**`, `lib/state/gen_ui_*.dart`,
`lib/state/connection/gen_ui.dart`, `lib/ui/widgets/agent_card_*.dart`, `lib/ui/screens/chat/agent_cards.dart`,
`lib/ui/screens/agents/cards_from_agents_row.dart` and the shared corpus `test/fixtures/genui/*`.

Shipping state: implemented, tested locally (commands in §4), committed and pushed to the branch. No PR, no CI run, no
signing, no release, no device run.

## 1. Journeys that now work in the Android app

| Journey | How it works now |
|---|---|
| An agent shows a card in a conversation | A completed, executed `oc-ui_show` / `mcp__oc-ui__show` call is drawn as a native card **under its turn's work line** (in the reply, never folded into it). Pending/running calls stay ordinary tool rows. The card is validated byte-for-byte like Flutter (same limits, same problem names, same normalised output); an unreadable payload is one plain line with the stable reason under **Details**. |
| Read-only card when cards are off or unproven | Every card is read-only (state Unknown: "We can't tell yet whether this card was answered…") until cards are on **and** qualified for the connection; a read-only asking card offers the visible **Cards from agents** button. |
| Turn cards on | **Cards from agents** page (`CardsSettingsRoute`): one switch per connected server, off by default (`oc.genui.enabled.<profileId>`, the Flutter key, so a migrated choice is kept), status under it in plain words naming only this connection's agents ("Not available. OpenCode 2 hasn't been checked to work with cards yet.", "On for OpenCode 2"). Turning on verifies that the server reports the app's `oc-ui` MCP server as connected; the phone never runs an installer on a server it does not manage. |
| Answer a card | Choice (one tap; several → check rows + Send), confirm (cancel/confirm buttons, danger tone), form (the kit form, required fields hold Send and say why, the "agent sees this" note). The answer is **held 3 s** with **Undo** (the card collapses to the answer's words, e.g. "SQLite"); Undo gives back what was chosen/typed. Then the card is re-checked (stale, passed over, busy, disabled) and the two-line answer goes **once** through the chat's own delivery pipeline (never steered, never queued, refused while the agent works). Accepted is "not confirmed" until the authoritative echo settles the card as **answered** (receipt; "Show the card" opens it read-only); a definite failure keeps the answer for a retry; an uncertain dispatch blocks a resend. |
| Typed reply instead | A real user message after the card passes it over ("Not answered"); a held answer is then never sent. |
| Answer bubble | The prompt bubble of a valid receipt shows only its summary line (the JSON line is hidden only for a validated receipt). |
| Links in a card | Shown as "label · host", inert until tapped, then opened only through `ExternalLinkPolicy` (`LinkOrigin.AgentCard`). |

How to reach it on the phone: open any conversation on a connected OC2 server whose agent called the `oc-ui` `show`
tool → the card is under that turn's work line. Settings: tap **Cards from agents** on a read-only card.

## 2. Catalog rows

| Row | Covered by | Notes |
|---|---|---|
| B-CHAT-004 | `ChatCardSlotTest` (7), `KitAgentCardTest` (2), `CardBoardTest` (held/Undo/send once/busy/failure/turn), `CardCodecTest` (held words) | Choice, form, confirm, receipt/held/passed-over/report, Undo restores the draft. **Photo kind:** rendered with the plain "Photos can't be added from here. Type your answer instead." (the engine validates photo answers fully, B-CHAT-053, but the chat has no card photo picker yet). |
| B-CHAT-029 | `CardCorpusTest` (shared corpus 65 cases: accept/reject, problem name, normalised output vs the JS twin, canonical text and revision stable on re-normalisation; all 12 directional controls; schema order and Dart numbers), `CardCodecTest` (cycles, non-JSON, NaN/∞, unpaired surrogate, 10 000-level nesting, Dart number/string printing) | See D2 for the revision vs Dart. |
| B-CHAT-030 | `CardCodecTest`, `CardBoardTest`, `ChatCardSlotTest` | |
| B-CHAT-031 | `CardCodecTest` (4) | |
| B-CHAT-032 | `CardCodecTest` (4), `ChatCardSlotTest` (receipt bubble) | |
| B-CHAT-033 | `CardCodecTest` | Validation and provenance only; the connector card's **Connect** flow (S5-CONNECT) is not built — a connector card renders as a report. |
| B-CHAT-034, 035, 036 | `CardsPolicyTest` (6), `ChatCardSlotTest` (2), `CardBoardTest` (disable drops a held answer) | Desired vs effective, recheck on reconnect, newest request wins, coalesced checks, storage failure keeps it off, unmanaged never installs. |
| B-CHAT-037 | `CardBoardTest` (waiting list, typed reply clears), `KitAgentCardTest` (list slot part), `ChatCardSlotTest` | **Missing:** the slot is not yet placed under Conversations rows (`:feature:chats` is not in this unit's write set) and profile deletion does not yet sweep `oc.genui.*.<profileId>` (see §6). |
| B-CHAT-038 | `CardsPolicyTest` | Pages of 50, two at most, newest first → oldest first, invalid limits send nothing. Body/frame byte caps live in `:core:net` (not re-tested here). |
| B-CHAT-039 | `CardsPolicyTest` (`IdleProof`) | Rule only; Paseo handshake checks need the Paseo protocol (not native yet). |
| B-CHAT-043, 044 | `CardsPolicyTest` (coverage: restart, prompt change, running turn, endpoint reset, never wake, explicit read, 200 cap, journaled, corrupt, closed profile) | Engine rules. The app reads only the **open** conversation (bounded); a background recovery pass across conversations is not wired. |
| B-CHAT-045 | `CardCorpusTest` (captured Paseo Claude fixture) | Through the codec; the Paseo timeline mapper is not native. |
| B-CHAT-049 | `CardsPolicyTest` (2) | `ShowCardPermission` rule (exact raw name, once, per-request recheck, sweep, location retire, failure not retried). Not wired: the native app has no Paseo gateway yet. |
| B-CHAT-052 | `CardBoardTest` (3), `CardsPolicyTest`, `ChatCardSlotTest` | |
| B-CHAT-053 | `CardBoardTest` | Matching count, image types, 10 MiB / 20 MiB, no photos on other answers. |

## 3. What was built (files)

- `native/engine/cards/**` (new module `:engine:cards`, core-only): `CardModel`, `CardJson` (Dart `jsonEncode`
  printing), `CardValidator` (port of `gen_ui_validation.dart`), `CardCodec`/`CardAnswers` (admission, state, receipts),
  `CardBoard` (per-chat state, hold/Undo, send once, delivery, photo checks), `CardsSetup` (availability), `CardRecovery`
  (bounded history, idle proof, coverage, pass caps), `ShowCardPermission`. Corpus + JS-twin expectations in
  `src/desktopTest/resources/genui/`.
- `native/ui/kit-chat/.../card/KitAgentCard.kt` (new): `KitAgentCard`, `KitCardBody`, `KitCardChoice`, `KitCardConfirm`,
  `KitCardForm`, `KitCardPhoto`, `KitCardListSlot` (kit-local models; quick 150 ms size change, none under reduced motion;
  Send haptic).
- `native/feature/chat/.../cards/` (new): `ChatCards` (app-scoped; `GraphCardsQualifier`; `ChatCardsModel` per open chat),
  `ChatCardSlot` (`LocalChatCards`, `ChatTurnCards`, words), `CardsSettings` + `CardsSettingsEntries`.
- `native/ui/routes/.../CardsRoute.kt` (new): `CardsSettingsRoute`.
- `native/androidApp/.../graph/ChatCardsWiring.kt` (new): `oc.genui.enabled.<profileId>` over the app's preferences;
  card links behind `ExternalLinkPolicy`.
- Smallest edits to existing files: `feature/chat/build.gradle.kts` (+1 dependency line), `ChatEntries.kt` (provide the
  card slot), `ChatTranscript.kt` (cards under the work line; receipt bubble text), `androidApp/build.gradle.kts`
  (+1 module line), `MODULE_OWNERS` (+1 row for `:engine:cards`, needed by `ModuleOwnersTest`).

## 4. Tests and results

Environment: JDK 21, Android SDK (`platforms;android-37.1`, `build-tools;37.0.0`, `platform-tools`), Gradle with a
Central mirror init script (container-only, not committed).

| # | Command (from `native/`) | Result |
|---|---|---|
| 1 | `./gradlew :engine:cards:desktopTest` (first run) | 41/42 — one B-CHAT-049 test reused an already-allowed request id (test bug) → fixed |
| 2 | `./gradlew :engine:cards:desktopTest` | BUILD SUCCESSFUL, 42 tests, 0 failures |
| 3 | `./gradlew :feature:chat:desktopTest --tests '*ChatCardSlotTest*'` | 13 tests, 0 failures |
| 4 | `./gradlew :ui:kit-chat:desktopTest --tests '*KitAgentCardTest*'` | 3 tests, 0 failures |
| 5 | **Gate:** `./gradlew :engine:cards:desktopTest :ui:kit-chat:desktopTest :feature:chat:desktopTest :ui:routes:desktopTest :quality:test :desktopApp:compileKotlin :androidApp:assemblePreviewDebug :androidApp:testPreviewDebugUnitTest :ui:kit-chat:testAndroidHostTest --continue` | **BUILD SUCCESSFUL.** Preview APK `androidApp-preview-debug.apk` (23.5 MB). **394 tests, 0 failures, 0 skipped**: engine/cards 42, ui/kit-chat desktop 112 + Android host 39, feature/chat 141, ui/routes 1, quality 48 (Konsist K1 kit-only imports, module rules, `ModuleOwnersTest`, graph smoke), androidApp preview unit 11. |
| 6 | `native/tool/scan_push.sh` and `native/tool/check_commits.sh` on `origin/native/p12-chatbase-2026-11-03..HEAD` | clean / passing |

Per engine test class: `CardCorpusTest` 4, `CardCodecTest` 14, `CardBoardTest` 11, `CardsPolicyTest` 13.
Not run: device/emulator journeys, a live OC2 server with the `oc-ui` helper registered, iOS targets.

## 5. Decisions and deviations

| # | Decision | Why |
|---|---|---|
| D1 | New module `:engine:cards` instead of `:engine:requests` `…cards` (plan 09 §13.3). | The unit's write set. It is not in `ModuleRules.engineOrder`, so it may use `:core:*` only; every edge it needs is a port (`CardAnswerSender`, `CardsPrefs`, `CardsQualifier`, `CardHistorySource`, `CoverageStore`). The coordinator may move it into the order or into requests. |
| D2 | Corpus expectations come from the helper's **JavaScript** validator run under Node on the same file (no Dart toolchain here); the revision uses a Dart-`jsonEncode`-exact printer. | Plan 09 §13.3 asks for a one-off Dart script writing `validation.expected.json` with revisions. Flutter's own suite proves Dart ≡ JS on acceptance and normalised output, so the native output is checked against that; **revision byte-equality with Dart is not proven** (number printing is unit-tested to Dart's rules). The integrator should still run the Dart script on the v2.1.0+57 tag and add the revisions. |
| D3 | Qualification for a connected (unmanaged) OC2 server = the server lists MCP `oc-ui` as connected. | Flutter only qualifies phone-managed hosts after its own install + `initialize` check; the native app has no phone-Linux manager yet. This keeps "registration alone is never ready" (the server's MCP status is its initialize) and never installs on a remote host. Off by default either way. |
| D4 | Cards sit under the turn's work line, and the card call still counts in the work line's step count. | The transcript engine (not this unit) folds tool parts into the work line; the card slot anchors to that row. |
| D5 | The card page is reached from a read-only card, not from Settings › Agents. | Settings › Agents belongs to the agents unit (`AgentsRoute`); see §6. |
| D6 | Photo asks show the "can't be added from here" line. | No card photo picker in the native chat yet; the engine's photo rules are complete. |
| D7 | The open chat reads its own bounded history (2 × 50) for cards, besides the transcript's read. | Same as Flutter's `GenUiStateController.recoverOne`; the transcript engine's P3 rows drop tool input. |

## 6. For the integrator

1. Put `CardsSettingsRoute` (or the switch) on Settings › Agents / the Agents hub (`AgentsRoute.Home`, agents unit).
2. Place `KitCardListSlot` under Conversations rows from each profile's waiting cards (`CardBoardState.waiting`) — needs
   an app-scoped card state per profile (today one `ChatCardsModel` per open chat).
3. Extend profile deletion to remove `oc.genui.enabled|covered|journal.<profileId>` and call `ChatCards.setup.forget`.
4. Wire `ShowCardPermission` into the Paseo permission path when the Paseo gateway lands; wire the card photo picker into
   `KitCardPhoto` (engine already validates).
5. Persist the dispatch journal (`oc.genui.journal.<profileId>`, routing metadata only) so an uncertain answer blocks a
   resend across restarts (today: in memory for the open chat).
