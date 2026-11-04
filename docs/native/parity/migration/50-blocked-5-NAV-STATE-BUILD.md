# Second native migration pass: NAV, STATE and BUILD

Base: `fe2ef02032714f4abb4a4d646944e72dc76920f8`; branch: `p12x/50-blocked-5-NAV-STATE-BUILD`.

Finish line: real native wiring or a precise external/owner-decision blocker for all 40 rows. Non-goals: tests, APKs, pushes, signing and releases.

Status: 13/40 row decisions recorded in this batch chain; remaining decisions are added by subsequent local batches. Full source candidate compile evidence is below.

B-NAV-023 -> built now — native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/header/world/GraphChatHeaderPort.kt:122 binds synchronously, caches branch by profile/project for reopened first frame, and reads/rebinds on project location changes; header/ui/ChatHeaderBar.kt:217 passes branch once to KitTopBarScope and Git action.
B-NAV-027 -> built now — native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt:439 records real running tool command/start and reads real permission asks; native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsScreen.kt:353 shows Signal running timer and command, :362 command-only permission slip with real Allow once/Reject via HomePermissionWiring.kt; native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatTranscript.kt:300 shows Signal run/step timer. Existing kit supplies Signal home typography/filter/button style; English/Arabic strings added.
B-NAV-033 -> built now — native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/world/GraphChatWorld.kt:1263 cycles the production session catalog/recents, skips unavailable references, wraps both directions and leaves the recent ring unchanged; existing selection transaction verifies server readback only for this session. composer/BasicComposerHost.kt offers visible Previous/Next recent-model actions in the model picker.
B-NAV-046 -> built now — native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/queue/OfflineQueue.kt:268 moves only unsent, unclaimed, non-dispatched records through one acknowledged conditional blob write; QueuedPrompt.kt:107 drops unavailable model/agent selection; native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/queuemove/QueueMoveViewModel.kt:156 revalidates real destination graph/session/catalog; QueueMoveSheet.kt:29 supplies a localized kit sheet through the visible chat menu. Existing chat step lists already use KitWorkLine/KitToolRow. Cross-connection queue cache stamps and conditional commits prevent stale owners overwriting the move.
B-NAV-065 -> built now — native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/presentation/ChatsAllViewModel.kt:378 fetches missing pinned sessions once, preserves pins/rows on failure and :474 explicitly retries failed pinned reads; existing AllConversationsRules.kt:133 sorts pinned-first with recency and ProjectsWiring.kt:168 supplies real SessionPinStore/profile-scoped persistence. Ordinary rows now offer visible More for pin/unpin and all other long-press menu actions.
B-STATE-001 -> built now (native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/StateHandoffWiring.kt:43): chat counterpart links use the shared durable owner, explicit Server/PhoneAgent identity and durable parent; phone links recover the real trusted .agents runtime, then recheck link membership, profile admission, project and graph after lookup.
B-STATE-047 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:542): receipt-capable online sends claim metadata before dispatch even without an offline-queue marker; journal survives restart; Check again reads admission without replay.
B-STATE-048 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/receipts/PendingCommandJournal.kt:94): production online sends now use the single per-profile journal owner and serialized duplicate claims; existing id/tab/scope identities cannot be overwritten.
B-STATE-049 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/receipts/PendingCommandJournal.kt:116): real online sends now retain uncertain/terminal tombstones and cannot reclaim those ids for dispatch.
B-STATE-050 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt:545): receipt-capable production sends refuse missing client-id/journal admission; existing malformed registry/journal and unverified-storage checks fence the wire.
B-STATE-051 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/receipts/PendingCommandJournal.kt:166): production admission uses acknowledged store values/readback and independent per-profile journal keys; direct Check again verifies the receipt's captured scope.
B-STATE-052 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/receipts/PendingCommandJournal.kt:192): real online claims now encounter the existing 1000-entry/1-MiB bounds and preserve every tombstone on refusal.
B-STATE-053 -> built now (native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/receipts/PendingCommandJournal.kt:315): production journal admission shares the process owner already drained/fenced by PreviewRuntime profile deletion, including closure before owner construction.

## Verification

Tests: 0 written, 0 changed, 0 run, as explicitly instructed. No APK, push, signing or release.

Compile-only gate: **3/3 targets passed, 0 compiler errors**. Warnings remain; this is not a warnings-clean or behavioral qualification claim. Gradle reported `717 actionable tasks: 142 executed, 575 up-to-date`.

Source candidate: SHA-256 `c933b2f15368e09187c3a5b8b7533055fdbdb2a4879d0d736542bc1a532a0642` over the sorted changed-file manifest and content hashes (156 implementation/resource/build files; documentation excluded). Source hashes were unchanged across the successful gate.

The missing ignored sherpa-onnx AAR was restored from a local checkout only after matching this checkout's committed SHA-256 pin. Integration fixes retained module boundaries: settings persistence lives in the engine, migration inspection exposes a public read-only seam while the concrete protocol gateway stays internal, and the desktop kit uses the graph's redactor and work context.

Command: Command (from `native/`, serialized behind the shared PC build slot):

```sh
JAVA_HOME=/home/eslam/Storage2/tools/jdk21 ANDROID_HOME="$HOME/Android/Sdk" \
  nice -n 5 ./gradlew :androidApp:compilePreviewDebugKotlin compileKotlinDesktop :desktopApp:compileKotlin \
  -Dorg.gradle.workers.max=2 -Dorg.gradle.daemon.idletimeout=300000 \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m' --console=plain --continue
```

Private address handoff remains unavailable: the local binding-store rows are built, but no unqualified discovery, requester authentication or session authority has been introduced. AI Team and A2A/Termux enable controls retain the binding plan's owner limits.
