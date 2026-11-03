Verdict: FAIL — 0 BLOCKER, 7 MAJOR, 0 MINOR.

1. **MAJOR — Duplicate sends before acknowledgement.** [BasicComposerViewModel.kt:389](/home/eslam/Storage2/wt/codex-review-p12/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/composer/presentation/BasicComposerViewModel.kt:389): `sending` resets after local admission, but the draft clears only after server acceptance. Delay the first POST response, then tap Send again: unchanged text/files receive another message ID and dispatch again. Only Unconfirmed pending items disable Send. **B-STATE-041.**

2. **MAJOR — Successful commands remain ready to execute again.** [GraphChatWorld.kt:1040](/home/eslam/Storage2/wt/codex-review-p12/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/world/GraphChatWorld.kt:1040): the `cmd-` success branch removes the bubble without acknowledging/clearing its draft. Successfully send `!some-command` or `/review`; the words remain, survive restart, and another Send repeats the operation. Flutter clears them on success.

3. **MAJOR — Picker changes bypass the send-selection lock.** [GraphChatWorld.kt:842](/home/eslam/Storage2/wt/codex-review-p12/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/world/GraphChatWorld.kt:842): picker mutations call the gateway outside delivery’s mutex. Send with model A, pause its prompt preflight after selection verification, then choose B. Resume: OC2 admits the prompt using B because its prompt body carries no model override. **B-STATE-118/119.**

4. **MAJOR — Remote model changes are silently overwritten.** [GraphChatWorld.kt:511](/home/eslam/Storage2/wt/codex-review-p12/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/world/GraphChatWorld.kt:511): `SessionSelection` events never update composer selection. Change A→B through another client while chat remains open; native still displays A, and its next send reapplies A. Flutter adopts these events. **B-STATE-118.**

5. **MAJOR — App commands execute through the server-command path.** [PromptDelivery.kt:643](/home/eslam/Storage2/wt/codex-review-p12/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:643): `/new` and `/sessions` are posted as server commands instead of invoking Flutter’s app actions. A server without those commands refuses them; a same-named template executes instead. **B-NAV-051.**

6. **MAJOR — Subagent mentions never reach the wire.** [ConnectionDelivery.kt:157](/home/eslam/Storage2/wt/codex-review-p12/native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/ConnectionDelivery.kt:157): requests always use the default empty mentions list. Typing a valid `@helper` sends ordinary text without OC2’s `agents` entry; the implemented mention resolver is unreachable.

7. **MAJOR — Camera attachment is unreachable.** [AndroidComposerFiles.kt:425](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AndroidComposerFiles.kt:425): Android contributes only Photos and Clipboard; Camera explicitly refuses. Flutter’s Take photo journey cannot be completed. **B-MEDIA-008.**

Read-only source review; no builds or tests run. Checkout remains clean.

