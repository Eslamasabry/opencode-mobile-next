Verdict: FAIL — 1 BLOCKER, 5 MAJOR, 0 MINOR.

1. **BLOCKER — Dictation can send audio off-device.** [VoiceWiring.kt:325](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceWiring.kt:325)  
   Without an on-device recognizer—or after an unsupported-language error—the app uses the default speech service. `EXTRA_PREFER_OFFLINE` does not enforce local processing, and no consent precedes this fallback. Flutter uses local Whisper. The Settings disclosure does not protect the mic path. **Binding invariant:** plan/10 §17 S1; no dedicated catalog row.

2. **MAJOR — A rejected transcript can auto-send an existing draft.** [ComposerVoicePresenter.kt:359](/home/eslam/Storage2/wt/codex-review-p12/native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/presentation/ComposerVoicePresenter.kt:359)  
   With auto-send enabled, leave an unsent draft and dictate the single letter “a.” Native validates the **merged draft**, starts the countdown, and sends everything. Flutter first validates `voice.draft`, rejecting that transcript independently of existing text. **Row:** B-MEDIA-016.

3. **MAJOR — Microphone permission cannot survive Activity recreation.** [VoiceWiring.kt:201](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceWiring.kt:201)  
   Rotate while the first microphone permission dialog is open. The suspended request’s callback belongs to the old Activity registry; nothing registers its key with the replacement registry. The retained composer remains Starting after the permission result. Cancel only records an early-end flag, so it cannot release this wait. **Row:** B-MEDIA-023.

4. **MAJOR — Backgrounding during TTS initialization permanently breaks subsequent readings.** [AndroidSystemTts.kt:156](/home/eslam/Storage2/wt/codex-review-p12/native/android/voice/src/main/kotlin/io/github/eslamasabry/oc/android/voice/tts/AndroidSystemTts.kt:156)  
   Start the first reading, then background before the initialization callback. Cancellation bypasses both `initializing = false` and engine shutdown. Every later reading encounters the permanently true initialization flag and fails until process restart. **Row:** B-MEDIA-009.

5. **MAJOR — Voice settings is missing from normal navigation.** [SettingsWiring.kt:221](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt:221)  
   Settings publishes voice capabilities as false, supplies no voice adapter, and neither admits nor handles `VoiceSetup`. A user whose dictation works cannot reach language, model, or auto-send controls through Settings. The composer exposes the route only through particular error notes. **Affected rows:** B-MEDIA-012, B-MEDIA-017, B-MEDIA-022.

6. **MAJOR — Nested Markdown fences expose code to read-aloud.** [ReplyReaderPresenter.kt:181](/home/eslam/Storage2/wt/codex-review-p12/native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/presentation/ReplyReaderPresenter.kt:181)  
   A valid four-backtick fenced example containing an inner three-backtick block prematurely exits native’s fence state, causing the inner code—including potentially sensitive content—to be spoken. Flutter tracks fence character and length and skips the whole outer block. **Row:** B-CHAT-060.

Static review of `c6d14e8d6`; no files changed or tests run.