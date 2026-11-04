# ANDR native migration, part 2

Base: `27dc0473501f80e9cbba8dddb2cd82699d0cdd4a`, branch `p12x/29-build-ANDR-2`. The assigned 48 rows were selected from the PC parity ledger using the requested second-half calculation.

Finish line: each assigned row has a reachable native implementation or an explicit dependency blocker. Non-goals: tests, APKs, signing, publishing and pushes. Flutter cited tests and exercised sources were read as behavior specifications only.

- B-ANDR-059 → built now — foreground/quiet-hours/opt-in retirement and workspace-scoped check-ins run through `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MonitorWiring.kt:43`, reached from Servers monitoring controls.
- B-ANDR-060 → built now — durable claim/retirement/batch behavior is bound to native shade dispatch and deletion at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MonitorWiring.kt:43`.
- B-ANDR-061 → built now — private pinned-source preflight and opaque action-free notification taps at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MonitorWiring.kt:391`.
- B-ANDR-062 → built now — default-off saved enrollment, fail-closed Wi-Fi, backoff and existing-service background admission at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MonitorWiring.kt:43`.
- B-ANDR-063 → built now — engine fair alert batches, opaque routes and consent/quiet-hour retirement use native dispatch at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MonitorWiring.kt:43`.
- B-ANDR-064 → built now — source/secret identity fencing, bounded poll cancellation and deletion registration at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/MonitorWiring.kt:438`; ProfileMonitor retains its rollback/late-result guards.
- B-ANDR-065 → blocked: native ProviderQuotaMonitor and enrolled background collector are absent; PlansWiring exposes sign-in-backed plans, without a callable native quota collector.
- B-ANDR-066 → blocked: no native quota observation/claim engine is connected to account rotation or alert delivery, so reset deduplication and claim rollback have no runtime owner.
- B-ANDR-067 → blocked: quota monitoring has no deletion participant, late-completion fence or opaque token resolver in the native graph; disabling a displayed plan cannot provide that contract.
- B-ANDR-068 → blocked: the preview manifest still permits cleartext globally, has no native launcher icon lineage, and Privacy policy opens a remote page; full bundled-policy/signing/build-identity release contract is not complete.
- B-ANDR-070 → built now — `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/InteractiveScheduleAdmission.kt:23` checks the durable bounded phone lease before transport and dispatch; all ConnectionDelivery links use it; queue failures retain retryable entries and EN/AR chat copy names the cause.
- B-ANDR-072 → built now — `native/androidApp/src/main/AndroidManifest.xml:35` declares one route-only VIEW filter for opencode-mobile/session and team; no path or MIME widening.
- B-ANDR-074 → built now — shares enter through native MainActivity/SharedTextIntake and ShareSeed after shell startup; Settings report/share-app now uses the self-excluding AndroidShareService at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt:347`.
- B-ANDR-076 → built now — cold delivery is cancelled for a destroyed Activity and history/restored intents are not replayed at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt:73`; live delivery stays app-scoped.
- B-ANDR-077 → built now — text/optional subject and failure Outcome use AndroidShareService at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt:347`, reached by Settings report and Share app.
- B-ANDR-079 → built now — actual phone-profile opened folders pass SharedStoragePath classification in `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/NativeSharedProjectFolders.kt:58`; remote sources never grant local shared-folder access.
- B-ANDR-080 → built now — acknowledged project/deletion/source changes synchronize canonical roots through `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/NativeSharedProjectFolders.kt:58`; copied native alias crash is fixed and relative/volume/Termux roots refused.
- B-ANDR-081 → blocked: SignInTerminalViewModel still lacks a production TerminalHost/foreground binding and native route installer; the foreground cause model alone is not a reachable terminal.
- B-ANDR-086 → blocked: ThermalGuard has no production HeatPausePort/heat-alert adapter; its AI Team target is explicitly deferred under D6. Plan 11 forbids stopping the OpenCode server as a substitute.
- B-ANDR-088 → blocked: thermal recovery policy exists in the engine but has no production pause/resume target adapter under D6.
- B-ANDR-090 → blocked: no production ThermalGuardStore/HeatPausePort restores a persisted target hold; the existing codec alone cannot resume the deferred AI Team.
- B-ANDR-095 → blocked: strict one-shot UsageTarget parsing reaches Plans through `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt`, but provider-specific sign-in/read-from routes are absent; a generic Plans landing is not full target handover.
- B-ANDR-096 → blocked: SubscriptionAlertDelivery has no native implementation/binding; PlansWiring keeps alertsOnRule false.
- B-ANDR-097 → blocked: no subscription notification-delivery owner consumes quiet hours, Wi-Fi, staleness or quota-monitor deduplication; numerical collection is also absent.
- B-ANDR-098 → blocked: subscription reset delivery/retirement has no native runtime owner, so one-shot reset messages cannot be enabled.
- B-ANDR-099 → blocked: UsageWidgetFeed is wired for sign-in rows, but numerical subscription collectors and visible hidden/window preferences are absent; complete feed behavior cannot be claimed.
- B-ANDR-100 → blocked: production plans never call UsageWidgetFeed.record/markAuthNeeded with real quota readings; snapshot helpers alone do not supply remaining/reset/auth-needed rows.
- B-ANDR-101 → built now — actual permission/network probes and consented downloads are bound at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceDownloadNotices.kt:86`; Voice settings rereads network after permission and keeps unknown data consent explicit.
- B-ANDR-102 → built now — fixed EN/AR numeric setup phases use the serialized VoiceSetupNotices sink at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceDownloadNotices.kt:46`; real speech/natural downloads emit progress and terminal phases.
- B-ANDR-103 → blocked: WidgetFeed.backgroundRunner/liveDispatcher are unbound; no native cold action runner negotiates protocol status after a mutation.
- B-ANDR-104 → blocked: cold action preflight/acknowledged-command settlement has no native runner; WidgetActionRuntime completes unavailable rather than inventing a reply.
- B-ANDR-105 → blocked: no native cold action runner owns health-before-permission admission and its deadline/cancellation fence.
- B-ANDR-106 → blocked: copied action lease/admission policies have no live/cold gateway dispatcher or current run/user-message binding.
- B-ANDR-107 → blocked: native widget mutation dispatch is unavailable; no runner can settle mutation uncertainty without replay.
- B-ANDR-108 → blocked: :feature:widget-panels and native WidgetOverlayActivity/WidgetRouteActivity implementations are absent; frozen component names are not app wiring.
- B-ANDR-109 → blocked: widget style/steer/voice overlay screens and their kit entry installers are absent from the native app.
- B-ANDR-110 → blocked: thermal shell notice/status-slot and thermal setting are unbound; ThermalNotices contains only neutral reference data for the deferred target.
- B-ANDR-111 → built now — permission snapshots publish only reviewed ask fields through `native/android/widgets/src/main/kotlin/io/github/eslamasabry/oc/android/widgets/WidgetPresentation.kt:46` and `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:536`; request metadata/tool inputs are not copied into the ask.
- B-ANDR-112 → built now — bounded message lookup correlates exact message/call identity; reviewed command/file targets and pattern-only folder fallbacks use `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:536`.
- B-ANDR-113 → built now — bounded ordinary assistant text only passes reasoning/Markdown/redaction cleanup at `native/android/widgets/src/main/kotlin/io/github/eslamasabry/oc/android/widgets/WidgetPresentation.kt:28`, called from live widget publication `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:536`.
- B-ANDR-114 → built now — real directory labels use WidgetPresentation.project from `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:536`; generic roots yield no project line.
- B-ANDR-115 → already there — `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:645` publishes name/state/since; built now: since bookkeeping uses profile identity so equal display names cannot collide.
- B-ANDR-116 → built now — twelve-row feed cap, needs-you/running/newest priority, localized presented titles, plain folder labels and server names are bound at `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:922`; renderer already caps launcher-visible rows.
- B-ANDR-117 → already there — `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:542` and AndroidWidgetSurface unchanged-byte comparison avoid rewriting/redrawing; Android-only module has no desktop writer. Publication now remembers a model only after accepted persistence.
- B-ANDR-118 → blocked: WidgetStatusWorker reaches the unbound WidgetFeed.backgroundRunner; no one-read status gateway is available in native attention.
- B-ANDR-119 → blocked: native status tickets/CAS exist but no bound WidgetStatusRunner supplies a guarded real result or closes its transport.
- B-ANDR-120 → blocked: widget publication now supplies safe bounded assistant previews, but direct assistant/tool event and friendly model-name publication are still absent; the full row remains incomplete.
- B-ANDR-121 → blocked: native action parsing/claims exist without dispatcher, private-target fingerprint publication, or run/location revocation wiring; launcher mutations remain unavailable.

Compile checkpoint 1: `:androidApp:compilePreviewDebugKotlin compileKotlinDesktop` passed together (`BUILD SUCCESSFUL`, 2m42s, 585 actionable tasks: 326 executed, 159 from cache, 100 up-to-date). Both requested compilation gates passed; executed tests: 0. Command: from `native/`, JDK 21, Android SDK, `nice -n 5`, the shared build lock, workers=2, daemon idle=300000, JVM `-Xmx2g -XX:MaxMetaspaceSize=768m`. Log: `build/native-andr-29/compile-2.log` (workspace artifact).

Initial attempt stopped at the missing pinned sherpa-onnx 2.0.1 AAR. The repository vendor fetch restored the ignored artifact with its committed SHA-256. No tests were written, changed or run. No APK/signing/push commands were run.

Checkpoint dispositions: 22 completed (20 built now, 2 already there with bounded fixes), 26 blocked. Compile success is not runtime or device qualification.
