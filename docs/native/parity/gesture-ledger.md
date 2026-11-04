# Native gesture ledger

B-NAV-019, second pass. This records the native production gesture call sites and their visible paths. Evidence is implementation inspection; no UI or automated test was run in this implementation phase. System scrolling, text selection, predictive Back, and unused kit primitives do not create a separate app feature.

| Surface | Gesture | Visible path | Evidence |
|---|---|---|---|
| conversations | Long press a conversation | Tap the visible More action on the same row | `native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsAllScreen.kt:429` |
| chat | Long press a sent prompt | Tap the same prompt to open its actions | `native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatTranscript.kt:525` |
| composer | Hold Stop | Tap the visible Stop after this step action beside Stop | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/composer/KitComposerStop.kt:154` |
| voice | Hold the microphone then release | Tap the microphone, then tap Done | `native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/ComposerVoiceSlot.kt:35` |
| voice | Slide away while holding the microphone | Use tap-to-talk and the visible Cancel action | `native/feature/voice/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/voice/ui/ComposerVoiceSlot.kt:35` |
| chat-picture | Pinch or double tap a reply picture | Use Zoom in, Zoom out and Fit below the picture | `native/ui/kit-chat/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kitchat/image/KitPictureView.kt:117` |
| viewer | Pinch or double tap an image or SVG | Use the always-visible KitZoomControls in KitImageView or KitViewerSvgBody | `native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/image/KitZoomControls.kt:39` |

The machine-readable companion is [gesture-ledger.json](gesture-ledger.json). Help › Tips is the real `TeachingRoute.Tips`: `VoiceEntries.kt` installs `TeachingScreen`, `TeachingViewModel` reads `TeachingRegistry.defaults`, and `AppVoiceTeaching` opens the durable `TipController` after the migration gate. Stop-after-step and microphone gestures retain their registered teaching IDs. Make it yours is the existing four-step replayable tour; Show tips again resets the shared controller.

The new visible conversation More control, Stop-after-step action, and reply-picture zoom controls close the previously hidden-only paths. No hidden gesture was added in this pass.
