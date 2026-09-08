# More → Settings discovery evidence

Synthetic captures of real Flutter widgets, 390×844 logical pixels, light/dark and1×/2× text. `before-*` uses the owned screen sources at0eabc2d; `after-*` uses this branch. All use the same seeded Laptop fixture, catalog model and local health/shell responses. No real profiles, credentials or server operations were used. These are widget images, not new APK or frame-timing evidence.

Finish line: find and change a setting, understand its active value and return scope with less reading. Non-goals: protocol/state, global palette/icon family, model-picker internals. Persona is an inferred developer using Android one-handed and intermittently, not user research.

| Finding | Repair / remaining work |
|---|---|
| MORE-01 duplicate default card | Current new-chat default appears inside Models & agents; header picker/catalog remain available. Scope copy shortened after visual review to avoid orphaned model name. |
| MORE-02 separator offset | Text and separators share76dp page rail; separator ends16dp before card edge. |
| MORE-03 icon-slot changes | Fixed32dp leading slot,24dp glyph,12dp gap. Redundant36dp filled tile removed. Global glyph-family replacement remains separate. |
| MORE-04 heading nesting | Heading16dp page gutter,24dp upper gap and8dp lower gap. |
| MORE-05 surface/icon heaviness | Deferred to shared theme/icon coordinator; no local glass or gradient added. |
| SETTINGS-01 naming | Localized Settings title matches the entry label. |
| SETTINGS-02 inconsistent geometry | Unboxed connection summary and category labels share60dp page text rail; fixed32dp icon slots. |
| SETTINGS-03 truncated categories | Labels/subtitles wrap, row minimum56dp one-line or72dp two-line with intrinsic growth. |
| SETTINGS-04 repeated URL | Readable profile/health summary; full URL remains under Server; refresh/error detail retained. |
| SETTINGS-05 hidden appearance value | Current light/dark/system and theme-pack labels visible on category. |
| SETTINGS-06 raw model ID | Catalog display name with presented-label fallback and normal body typography. |
| SETTINGS-07 wrong agent route | Selected agent requests focusAgent:true; picker dependency90e1308 stages choices until Apply. Direct tap verified with loaded catalog; cold-catalog behavior reported to picker owner. |
| SETTINGS-08 non-setting essay | Removed Experimental/Workspaces note from coding defaults; Workspace tab retains the feature. |
| SETTINGS-09 theme preview | Deferred: current swatches do not preview a real component. |

Mobbin images inspected: [Deepstash](https://mobbin.com/screens/1402efff-5fe3-487c-8137-a3a96cc81f1a) quiet bare icons; [Base](https://mobbin.com/screens/3f573b95-4bbb-4978-957e-56178543e8e7) current appearance/language values; [Meetup](https://mobbin.com/screens/3acc9970-f3ca-43f3-959e-c72d76090d76) grouped separators and visible On values. iOS hierarchy references are not Android dp specifications.

Validation: pinned Shorebird Flutter3.47.2/e16cf749cc.12 baseline capture cases passed.35 focused cases passed across library_screen_test, settings_server_updates_test and settings_discovery capture (includes direct agent entry checks). Final localized scope-copy change:18 affected library/capture cases passed. Initial compact settings test tried tapping immediately after ensureVisible while its center was exactly at viewport bottom; waiting for the scroll frame fixes the test and the exact rerun passed. Analyzer reports two inherited picker warnings (unused optional key/action on _InlineNotice in dependency90e1308); no screen warnings. Coordinator must clear these at integration. No full-suite, native APK, release or FPS claim.

Reproduce: `flutter test --no-pub --concurrency=1 test/library_screen_test.dart test/settings_server_updates_test.dart tool/capture/settings_discovery_test.dart` with pinned SDK. Capture helper mocks secure storage and uses shared setup_capture_preferences; no flutter_animate or added timing framework.
