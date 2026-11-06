# Native screens integration — 2026-11-06

Branch: `native/screens-merge`; starting revision: `d6be4381362152b4af6a0299a2af9e1550d66dd5`.
Finish line: all eight upgrade branches merged in order with their designs and all existing preview fixes retained.
Non-goals: compilation, tests, app execution, pushes, CI, signing and releases. The coordinator owns compilation and runtime acceptance.

Source/history review and whitespace/conflict-marker checks only; no build or test qualification is claimed.

## Sources (fetched before merging)

- `origin/native/screens-upgrade`: `b5cb3b2944b2c40bcf1fcbf83f860ce823f0b936`
- `origin/native/upg-newchat`: `679c3d1b51e8b21355ca9ee6c6745ec63c42c047`
- `origin/native/upg-liveline`: `973c668567cad34017ba2bfcc9032301444226fa`
- `origin/native/upg-voice`: `80b12f6fe7d857bc6956a5a0cb37df6be97dd0db`
- `origin/native/upg-rows`: `36aeabfe18ba95243a397abd40931ab719fceadf`
- `origin/native/upg-approvals`: `5e08be40ab025f8a549be71c1187cc7232fadb2a`
- `origin/native/upg-connect`: `7b6a6b54009518210ac2cc97f9416f2833f4e2af`
- `origin/native/upg-discovery`: `3dc828d02518119a4b4fc522c6481f31257ac0be`

## 1. screens-upgrade

Read both conflict sides and their commit history (`git log -p`). Resolutions:

- `NewConversationWiring.kt`: keep instant-start creation journals and model/agent choices; retain explicit receipt codecs and extend them to every new journal and choice envelope, avoiding the Android app's missing serialization plugin. Preserve model/project writes before delivery, session selection/readback, scoped uncertainty fences, authoritative hidden-agent filtering, server chat-default model and real-project fallback. Build/Plan remain separate runtime modes.
- `NewConversation.kt`: keep upgrade mode validation/choice persistence and instant opening, while fresh entries remain blank and only unresolved-send receipts restore old words (N-16).
- `NewConversationScreen.kt`: compact upgrade chips and two-line prompt replace agent cards; keep compact choices beside Start above the keyboard and the explicit blocked reason (N-03). The sheet remains its only vertical scroller.
- `BasicComposerHost.kt`: use upgrade frameless composer, mic/Send action, Free model chip, agent placeholder and history refresh; retain the visible voice option in + and its close-on-selection callback. ViewModel-owned send admission is unchanged (N-14).
- `ChatEntries.kt`: retain notification opt-in in the single status slot and review sheet, hidden while typing; add upgrade teaching and navigation motion (N-06).
- `ChatHeaderBar.kt`: keep the short primary/Go to/More menus and capability gates (N-09); free-model information moves to the upgraded model chip.
- `ChatStartArea.kt`, `ChatScreen.kt`: upgrade project-aware empty state and overlay spacing win; KitScreen retains identical movable slots across layouts, preserving send lifetime (N-14).

Automatic merges also preserve Settings in the home header, server transport review, voice active controls and other fixes. NotificationText regex, isolated phone ports/current-start bind classification, original-error crash logging and N-18 server recovery are outside this upgrade's replaced code and remain intact.

## 2. upg-newchat

One conflict: `NewConversationScreen.kt`. Adopt the upgrade's prompt bubble with Try again/Edit, starters and one growing agent/model sheet; keep its three agent/model/project chips in the keyboard-safe footer from N-03. Remove the obsolete fourth Modes chip and use the shared sheet's Build/Plan control. Preserve the blocked reason, fresh blank entry rule, explicit codecs and send lifetime protections from merge 1. Reviewed both histories, formatted the resolved file, checked the diff; no compilation or tests.

## 3. upg-liveline

No textual conflicts. Retain current-step counts, gentle line transitions, accessibility announcements and plain error states. Move the new plural-resource helper to a composable top-level function so its callers use the resource API from a composable context. No existing preview-fix path is removed. History/source and formatter/diff review only; no compilation or tests.

## 4. upg-voice

One textual conflict: `SCREENS-UPGRADE.md`; retain both the new-chat and voice sections, giving voice its own heading. Automatic composer/voice merges reviewed for semantic conflicts: retain the upgrade in-field mic, stable hold position and dictation-without-send behavior, and retain N-05's + close callback and active conversation controls. `Actions` delegates to the same `Options` entry; that entry now uses the upgrade row design, preventing duplicate voice choices. Inline mic also installs the existing hold teaching tip. Old `startVoiceConversation` callers are removed together with the API. History/source, formatting and diff review only; no compilation or tests.

## 5. upg-rows

One conflict: `SCREENS-UPGRADE.md`; retain all new-chat, voice, rows and predictive-back notes under distinct headings. Source merges keep the agent mark/live dot/server metadata, archive capability gates, swipe/Undo and app-scope completion/failure restoration. N-15's Search/Settings/optional Update header order remains unchanged. New action methods retain defaults for existing callers. History/source and diff review only; no compilation or tests.
