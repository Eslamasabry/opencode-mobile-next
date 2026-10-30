# KitMotionParts: API freeze (wave 0)

Unit: `kit-KitMotionParts` (kind `kit-part`, tier 1a; cut review C11). Spec for `revamp.workflow.js`.

## Purpose

These are the small motion primitives a screen may use, all on `KitMotion` timings and curves. Each one:
- shows its final state on the first build;
- is instant under `KitMotion.reduced`;
- moves only paint and transforms (MOT-5);
- never blurs, scales or fade-scales (VL §7, MOT-2).

They replace the framework's `Animated*` widgets, transitions, `Opacity`, `Transform` and `TweenAnimationBuilder` outside the kit. The four named parts are `KitSwap`, `KitSpin`, `KitAnimatedBox` and `KitDim`. This freeze adds one more, `KitAnimatedValue`, because the number-easing job of `TweenAnimationBuilder` fits none of the four. Arrivals (`Transform.translate` plus fade) already have a kit part, the existing `KitEntrance` in `lib/ui/kit/motion/kit_reveal.dart`, so this unit adds none.

## Replaces

**G16 baseline:** 59 in 28 files. The largest are `chat/message_view.dart` 8, `chat/empty_chat.dart` 7, `phone_setup_start_screen.dart` 4, `setup_progress_view.dart` 4, `chat/composer.dart` 3, `chat_screen.dart` 3, `review_workspace.dart` 3 and `entrance.dart` 3.

| Widget (count) | Goes to |
|---|---|
| `Opacity` 19 | `KitDim` for images, drawings and marks. Opacity around text is not a KitDim job (LOOK-14): stale or disabled text uses `KitTextTone.tertiary` plus a stale label, for example `team/run_screen.dart:465` and `team/agent_screen.dart:461`. Fade-ins built from Opacity become the existing `KitEntrance` or `KitSwap`. The `AppGlyph` duotone Opacity is kit-KitIcon's. |
| `TweenAnimationBuilder` 8 | The existing `KitEntrance` (`entrance.dart`, `empty_chat.dart` ×2, `phone_setup_start_screen.dart:675`); `KitAnimatedValue` (`setup_progress_view.dart:307`, `composer.dart:1738`); KitImage's own fade (`provider_logo.dart:79`). The fade-scale pill in `message_view.dart:38` becomes `KitSwap` (no scale). |
| `Transform` 7 | `.translate` entrances → the existing `KitEntrance` (`empty_chat` ×2, `phone_setup_start`, `team_moments.dart:230`, `entrance.dart`). `EntranceReveal`'s index stagger is dropped: the existing `KitEntrance` has none, and rows that arrive while the person looks are `KitAnimatedRows`. `.scale` → none: the fade-scale is dropped (MOT-2), and `Transform.scale(.8, KitStatusMark)` in `team_conversation_view.dart:876` becomes a KitStatusMark size (kit-KitStatusMark-v2). |
| `AnimatedContainer` 5 | `KitAnimatedBox` (the `message_view.dart:1863` highlight, `review_workspace.dart:971`). The composer surface (`composer.dart:256`) goes to kit-KitComposer, and the tool card's running edge (`tool_card.dart:872`) to kit-KitToolRow. The streaming tint (`message_view.dart:1647`) is dropped: the working mark says it (LOOK-6). |
| `AnimatedSwitcher` 4 | `KitSwap` |
| `AnimatedRotation` 4 | `KitSpin` / `KitSpin.chevron` |
| `RotatedBox` 2 | `KitSpin.fixed` |
| `AnimatedSize` 4 | Not here. Layout animation exists only inside `KitReveal` (MOT-5). The `form_renderer.dart` ×2, `permission_sheet.dart:507` and `chat_screen.dart:170` sites become `KitReveal` or no animation. |
| `ScaleTransition` 3 | `KitSwap` cross-fade (`team_board_tabs.dart:222`, `setup_progress_view.dart:774`, `team_cycle_strip.dart:653`). No scale. |
| `FadeTransition` 2 | `KitSwap` / the existing `KitEntrance` |
| `ShaderMask` 1 | Not here. The fading last line of `tool_card.dart:1599` becomes KitCodeBlock/KitToolRow's "Show all" cut (crisp, no feathering). |

- **Classes it makes redundant** (their files are outside this write set, KIT-43): `EntranceReveal` (`lib/ui/widgets/entrance.dart`) → the existing `KitEntrance`.
- **kit-v2.json `assignment`:** no element is assigned.

## File

`lib/ui/kit/motion/kit_motion_parts.dart` (new). The write set is that file, `test/kit/kit_motion_parts_test.dart` and `test/goldens/kit/kit_motion_parts_golden_test.dart`.

## Public API

```dart
/// The two paces a part may move at (MOT-1).
enum KitPace {
  quick,    // KitMotion.quick (150 ms): a control answering a touch
  standard, // KitMotion.standard (250 ms): a part appearing or changing
}

/// Cross-fades between children: AnimatedSwitcher's job, without scale or
/// size animation. The child's key says when the content is new. The old
/// child ignores touches and is excluded from semantics while it leaves.
class KitSwap extends StatelessWidget {
  const KitSwap({
    Key? key,
    required Widget child,
    KitPace pace = KitPace.quick,
    AlignmentDirectional alignment = AlignmentDirectional.center,
  });
}

/// ADDED 2026-10-27 (micro-motion audit, docs/qa/micro-motion-2026-10-27).
/// A count that changes in place: the new number rolls in from below as the
/// old one rolls out above (the other way when it falls), both fading, on
/// KitMotion.quick. Calm: cross-fade only (KitMotion.travels). Off and
/// reduced motion: at once. Clipped to the number's box only while it rolls.
/// Used by KitNeedsYou.badge, KitTabSwitcher and KitSegmented counts.
const KitSwap.count({
  Key? key,
  required int count,
  required Widget child,
  AlignmentDirectional alignment = AlignmentDirectional.center,
});

/// Rotates its child. The rotation is paint only and does not change layout.
class KitSpin extends StatelessWidget {
  /// Animated to [turns] (0.5 = half a turn) on [pace].
  const KitSpin({
    Key? key,
    required double turns,
    required Widget child,
    KitPace pace = KitPace.quick,
  });

  /// A fixed rotation that also rotates the layout (RotatedBox's job), for
  /// example a glyph drawn sideways.
  const KitSpin.fixed({Key? key, required int quarterTurns, required Widget child});

  /// The one disclosure chevron of the kit (README.md decision D10):
  /// `AppIconography.chevronDown` at 20 dp in text2, turning on
  /// KitMotion.standard with KitMotion.emphasized,
  /// pointing down when closed and up when [open]. It uses the vertical
  /// glyph so it never depends on the reading direction.
  const KitSpin.chevron({Key? key, required bool expanded});
}

/// A box whose paint changes between surface steps: the fill level and the
/// hairline edge animate on [pace]. A change of size snaps; layout animation
/// is KitReveal's alone (MOT-5). [level] null is no fill.
class KitAnimatedBox extends StatelessWidget {
  const KitAnimatedBox({
    Key? key,
    required Widget child,
    KitSurfaceLevel? level,            // pre-wave seam enum
    KitShape shape = KitShape.panel,   // pre-wave seam enum
    bool outlined = false,             // exactly one physical pixel (LOOK-21)
    KitPace pace = KitPace.quick,
  });
}

/// How far KitDim dims.
enum KitDimLevel {
  stale,    // KitTokens.staleAlpha: last-known content while a refresh is pending
  disabled, // KitTokens.disabledAlpha: an image or drawing for an unavailable choice
}

/// Dims an image, a drawing or a mark (Opacity's job) for non-text content
/// only. A debug assert fails when the child's render subtree contains a
/// paragraph (LOOK-14: text at rest is opaque; dim text with a tone instead).
class KitDim extends StatelessWidget {
  const KitDim({
    Key? key,
    required Widget child,
    bool dimmed = true,
    KitDimLevel level = KitDimLevel.disabled,
    KitPace pace = KitPace.quick,
  });
}

// Arrivals are the EXISTING KitEntrance (lib/ui/kit/motion/kit_reveal.dart;
// child, trigger, rise, onMount). It is not redefined or changed here.
// Calm: fade only (it rises only under Animations: Full, KitMotion.travels).

/// ADDED BY THIS FREEZE. Eases a number toward [value]
/// (TweenAnimationBuilder's job). The first build shows [value] at once, and
/// later changes animate on [pace]. [jump] shows the new value at once (a new
/// job, a reset). The builder should change paint or a transform: a bar's
/// fill, a count's position. It must not change layout.
class KitAnimatedValue extends StatefulWidget {
  const KitAnimatedValue({
    Key? key,
    required double value,
    required Widget Function(BuildContext context, double value) builder,
    KitPace pace = KitPace.standard,
    bool jump = false,
  });
}
```

Every part follows these rules:
- **First build is final.** The part shows its final state on the first build, with no mount animation.
- **Reduced motion.** Under `KitMotion.reduced(context)` every change is instant.
- **Controllers.** Each part owns at most one `AnimationController`, idle at rest.
- **Curves.** Curves are `KitMotion.enter` and `exit`, plus `emphasized` for the chevron's turn (MOT-1 names all three).
- **Settling.** Nothing loops, so `pumpAndSettle` always settles.

## States

- **At rest and changing:** the only two phases of every part.
- **KitDim:** `dimmed` true or false × stale or disabled.
- **KitSpin.chevron:** expanded or folded.
- **KitAnimatedValue:** settled, easing, or jumped.
- Loading, empty, error, working and answered belong to the hosts. For example, a working mark is KitStatusMark, and a progress bar is KitProgress, which may use `KitAnimatedValue`.

## Tokens

- **KitMotion:** `quick`, `standard`, `enter`, `exit`, `reduced(context)`.
- **ThemeRoles** (via tokens): `surface1`–`surface3`, `ground`, `hairline`, `text2` (chevron).
- **KitTokens:** `fillOf(level)` and `shapeOf(shape)` (pre-wave seam), `hairlineWidth(context)` (pre-wave §0.5 step 2), `smallIconSize` (the chevron).
- **New (pre-wave, `_new-tokens.md`):** `KitTokens.staleAlpha` (0.6, today's stale dim) and `KitTokens.disabledAlpha` (0.38), in `kit_tokens.dart`, which is outside this unit's write set.

## Adaptive

- The same on compact, medium, expanded and large. Motion does not change with the window.
- **Pointer and keyboard:** none of these parts is interactive or focusable.
- **KitSwap:** focus inside the outgoing child is not carried over. A host that swaps a focused control restores focus itself (G14 checks the hosts).

## Accessibility

- **KitSwap:** the outgoing child is excluded from semantics and ignores pointers while it leaves (as `KitReveal` does), so nothing reads or acts on content that is going away.
- **KitDim:** it changes no semantics. The host says why something is dimmed (STATE-8).
- **KitSpin.chevron:** it is decorative. The host carries the `expanded` semantics state.
- **KitAnimatedValue:** it adds no semantics. The host states the value in words (KitProgress's caption).
- **200 % text:** unaffected, because nothing clamps or scales text.
- Status changes are announced by the host's live region, never by a motion part (A11Y-3).

## RTL

- `KitSwap.alignment` is directional.
- `KitSpin` rotates clockwise in both directions. `KitSpin.chevron` uses the vertical chevron, so it is direction-free: a mirrored right chevron turned a quarter would point the wrong way.
- No left or right literals (G7).

## Motion and haptics

- **Motion:** `KitMotion` durations and curves only (MOT-1). Paint and transforms only (MOT-5). No blur, scale or fade-scale (MOT-2). Instant under reduced motion and Effects › Animations: Off (MOT-7).
- **Leaving:** `KitReveal` and `KitAnimatedRows` unfold in over `KitMotion.standard` and fold out on `KitMotion.quick` (leaving is quicker than arriving), on `KitMotion.exit` in the reverse slot. `KitSwap` is `quick` both ways.
- **Calm:** these parts behave as under Full: they are finite and nothing loops. The exceptions are `KitSwap.count`, whose roll becomes a plain cross-fade, and `KitEntrance`, which fades in place without its rise (Calm: fade only; `KitMotion.travels`).
- **Haptics:** none (MOT-11).

## Data safety and honest state

- Motion never hides a state change. Each part shows the final state immediately under reduced motion, and in all cases at the end of one `KitMotion.standard` at most.
- `KitDim(level: stale)` is paired by its host with a stale word ("Updated 4 min ago"), so dimming never stands alone for "old data".
- `KitSwap` never keeps an outgoing child interactive, so a tap cannot land on a control that is going away.

## Depends on

- `KitMotion`, `KitEffects` (existing).
- The pre-wave seams: `KitSurfaceLevel`, `KitShape`, `fillOf`, `shapeOf` and `hairlineWidth`; the new `staleAlpha` and `disabledAlpha`.
- The existing `KitEntrance` is used by screens for arrivals and is not touched here.
- No wave-1 part. The chevron is drawn with the kit-internal `Icon` at 20 until kit-KitIcon merges; `kit-hygiene` swaps it.

## Tests required

`test/kit/kit_motion_parts_test.dart`:

1. Each part shows its final state on the first `pump()` (no mount animation).
2. Under `MediaQuery(disableAnimations: true)` and under Effects › Animations: Off, every change settles after one `pump()` with no active ticker (G8, MOT-7).
3. `KitSwap`:
   - a keyed change cross-fades over exactly `KitMotion.quick`, or `standard`;
   - no `ScaleTransition`, `Transform.scale` or size animation is in the tree during the swap;
   - the outgoing child ignores taps and has no semantics.
4. `KitSpin(turns: .5)` animates. `KitSpin.fixed(quarterTurns: 1)` swaps width and height. `KitSpin.chevron(expanded: true)` points up under both LTR and RTL and turns over `KitMotion.standard` with `emphasized`.
5. `KitAnimatedBox`:
   - a level change animates only the paint (the `RenderBox` size is constant during it);
   - a child size change snaps in one frame;
   - `outlined` is `1 / dpr` thick.
6. `KitDim` over a `KitText` child fails its debug assert. Over an image it paints at `disabledAlpha` or `staleAlpha`.
7. `KitAnimatedValue`:
   - the first value shows at once;
   - 0.2 → 0.8 eases over `standard`;
   - `jump: true` shows 0.8 at once.
8. `pumpAndSettle` completes for every part (nothing loops).
9. No `Duration(` literal and no `Curves.` in `kit_motion_parts.dart` (a source scan, MOT-1).

## Galleries required

`test/goldens/kit/kit_motion_parts_golden_test.dart`, at DPR 3, Android, showing settled frames:

- **States** in dark and light at 412×915:
  - `swap` (before and after);
  - `spin` (chevron closed and open, a fixed quarter turn);
  - `box` (none, surface1, surface2, surface3, outlined);
  - `dim` (stale and disabled over an image and a drawing);
  - `value` (a bar at 0.3 and 0.8).
- **Default state (`box`):** at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **`_text2` and `_ar`:** `spin` and `swap` at 412×915 and 1280×800.
- **G6 overflow matrix** without images.

## Non-goals

- No page transitions (`KitPageTransitions`), come-and-go parts (`KitReveal`), arrivals (`KitEntrance`), animated lists (`KitAnimatedRows`), pull to refresh (`KitRefresh`), scenes (`KitIllustration`) or haptics (`KitHaptics`). Those exist already and are not changed here.
- No staggered list entrance.
- No layout or size animation.
- No springs or custom curves.
- No looping or ambient motion.
- No gradient masks.
- No call-site migration.

## Open questions

None. `KitTokens.staleAlpha` and `KitTokens.disabledAlpha` are pre-wave (`_new-tokens.md`). Without them, `KitDim` is a PROC-20 contract problem (`blocks: true` for that class only). `KitSwap`, `KitSpin`, `KitAnimatedBox` and `KitAnimatedValue` go ahead.
