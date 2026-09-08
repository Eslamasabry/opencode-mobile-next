# Model choice: coherent drafts and a readable catalog

Finish line: model, thinking mode and agent share one explicit Apply action. Dismissal before Apply leaves prior choices intact. Separately saved model/agent operations remain honest on partial failure. Provider status stays actionable without dominating the list.

Actual production widgets, synthetic four-model catalog and agents. 390×844 logical pixels, rendered at3×, dark/light and1×/2× text. These are Flutter widget captures, not screenshots from a newly built APK. Provider logos use the widget's deterministic monogram fallback. Baseline is0eabc2d; after is this commit's source.

| State | Before | After |
| --- | --- | --- |
| Dark normal | [Before](before-dark-1x.png) | [After](after-dark-1x.png) |
| Light normal | [Before](before-light-1x.png) | [After](after-light-1x.png) |
| Dark 2× text | [Before](before-dark-2x.png) | [After](after-dark-2x.png) |
| Light 2× text | [Before](before-light-2x.png) | [After](after-light-2x.png) |

Inspected all8 PNGs. At normal text the first selected row moves80dp upward in the identical fixture, preserving its row height and touch controls. At2× text the old warning leaves only a partial first row before the pinned Apply action; the compact notice exposes two complete rows and the start of the next. Tabs remain horizontally scrollable and controls/list scroll together at large text. This is content visibility evidence, not measured usability or frame pacing.

Verified with Shorebird Flutter3.47.2 e16cf749ccaa38d7050335ff305def49b1c7c84c:

- `flutter test --no-pub --concurrency=1 test/model_picker_test.dart test/session_selection_sync_test.dart test/session_model_scope_test.dart tool/capture/simple_model_test.dart`:42 behavioral cases and4 after captures passed on the final source. Four controlled baseline captures were previously captured and inspected.
- Regression coverage includes dismiss-before-apply, successful agent/mode apply, per-session agent dispatch without changing defaults, direct agent entry before/after catalog loading, opening that entry only once, partial-save error/retry, provider details/reload and completion of already-authorized Apply after dismissing its sheet.
- New journey copy uses8 unique `modelChoice` localization keys; generated with the pinned Flutter toolchain. Unused private notice key/action plumbing removed.
- Format and `git diff --check` clean. Analyzer/full suite belongs to coordinator's integration gate. No native build, signing, deployment or live provider mutation performed.

The direct `focusAgent:true` entry is available for Settings integration. It opens agent selection, stages that choice, then returns to the existing scoped Apply action. Favorites remain immediate library preferences; they are not part of model/agent Apply.

Durable current-run audit with MODEL-01..06 and inspected Claude/GitHub/Alta Mobbin references is in the coordinator's external audit folder `pages/model.md` and `pages/model.json`. The external original APK capture includes existing user state and is intentionally not copied into Git.
