# Migration 43: Flutter kit parts

Counts: already there **0**; ported now **0**; not needed **10**; blocked **0**. Assessed **10/76**.

Implementation-only review from P12 `f00dbf8e9b0948f0caad4db96c4170f841977401`. No tests written or run. Deferred means deliberately excluded by the binding native plan, not missing by accident. Plan references below are under `docs/design/native-rewrite-2026-11-01/`.

| Flutter source | Disposition | Behavior and evidence |
|---|---|---|
| `lib/ui/kit/live_background/kit_live_clock.dart` | not needed | Shared 12 Hz clock, 30 s idle rest, wake and visibility attachment serve deferred live backgrounds; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/kit_live_style.dart` | not needed | Frame, palette, memory, contrast and repaint contracts serve deferred live backgrounds; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/kit_live_styles.dart` | not needed | 20-look registry, stable ids and aurora fallback serve deferred live backgrounds; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/live_mock_chat.dart` | not needed | Picker/preview miniature chat serves deferred live backgrounds; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/styles/kit_live_aurora.dart` | not needed | Contrast-limited drifting aurora ribbons and cached meshes; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/styles/kit_live_blueprint.dart` | not needed | Drifting measurement grid and running scan line; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/styles/kit_live_bokeh.dart` | not needed | Drifting out-of-focus lights with running green lights; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart` | not needed | Cached grain and gliding running highlight; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/styles/kit_live_caustics.dart` | not needed | Animated pool-light vertex mesh; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |
| `lib/ui/kit/live_background/styles/kit_live_circuit.dart` | not needed | Cached circuit traces and traveling light pulses; deliberately outside the switch under ADR 01 §12 D11 (`plan/01-architecture-decision.md:881`), explicit kit exclusion `plan/06-ui-kit-design-system.md:946`/`:1018`; preferences remain retained. |

## Verification

Batch 1: documentation only; Kotlin/Gradle unchanged in this commit, compile gate skipped as instructed.
