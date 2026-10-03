# KitAction, KitActionBlock and KitActionStack v2: API freeze (wave 0, 2026-09-26)

> **Copy (SEC-13, coordinator 2026-09-27):** `.copy` takes `redact` (default true) and passes it to `KitCopy.copy`, so message and diff callers can copy verbatim (KitCodeBlock keeps the redacting default).

Unit: `kit-KitAction-v2` (wave 1, tier 1a, kit-change). Write set: `lib/ui/kit/kit_buttons.dart` and `lib/ui/kit/kit_action_stack.dart`.

- **Spec:** kit-v2.md §2.7, §4.2, §4.9, §8.2 and §8.3; design-standard.md §2; visual language §5 (buttons; the PC row with keyboard hints).
- **Cut review:** C26 (disabledReason is required when `onPressed` is null, carried from P7.6), C41 (P7.6's spinner ban), K2 kit-v2.json `changed` entry.
- **Rules:** KIT-8, KIT-23, KIT-39, KIT-43, LAY-9, LAY-12, LAY-13, LAY-14, STATE-7, STATE-8, LOOK-5, LOOK-23, COPY-8, and Appendix A #18, #20, #42.

## Purpose

`KitAction` is one action a kit part shows, and the caller never styles it. `KitActionBlock` lays actions out in the one hierarchy: primary, then secondary, then up to two tertiary actions, with the rest in "More". `KitActionStack` puts each rare action on its own line.

v2 makes three rules structural:

- a disabled action shows its reason as visible text;
- a destructive tertiary never sits beside a frequent action;
- on medium and wider windows the block is one end-aligned row.

It also adds copy and keyboard-hint actions.

## Replaces

- **Map (kit-v2.json `existingAdoption` KitActionBlock, 79 elements on 60 pages):**
  - `add-agent` (add-agent-inspect, add-agent-save); `appearance-picker-sheet` (appearance-apply); `attention-overview` (attention-overview-open-profile); `bootstrap-gate` (bootstrap-gate-retry); `builtin-server-setup` (remove-ubuntu); `catalog` (choice-bar);
  - `chat` (chat-background-running-work, chat-load-older); `command-auth-sheet` (check-status, start); `connection-help` (connection-help-explain); `context-capsule` (context-capsule-add); `continue-on-computer-sheet` (continue-on-computer-export); `credential-management-sheet` (account-actions, refresh);
  - `demo` (demo-set-up-server); `development-services` (remove-configuration, service-buttons); `embedded-local-agent-onboarding-block` (needs-ubuntu-refresh, try-again); `embedded-mobile-task-list` (embedded-mobile-task-list-copy-all); `embedded-product-states` (embedded-product-states-error-report-bug);
  - `embedded-team-cycle-strip` (team-cycle-action-nudge, team-cycle-action-output, team-cycle-action-stop); `embedded-team-discovery-card` (discovery-turn-on); `embedded-team-phone-section` (phone-delete, phone-refresh, phone-start-again, phone-stop); `embedded-team-planning-card` (planning-dismiss);
  - `external-agent-detail` (external-agent-detail-delete); `external-agents` (external-agents-add); `external-task` (external-task-actions); `files-changes-sheet` (files-changes-sheet-review-all); `host-management` (host-management-docs); `isolated-task-sheet` (isolated-task-sheet-start, isolated-task-sheet-stop-waiting);
  - `legacy-drafts-review-sheet` (legacy-delete); `local-agent-page` (offer-actions); `local-agent-project-sheet` (continue); `managed-workspaces` (managed-workspaces-create); `mcp-setup` (save); `model-picker-sheet` (model-picker-apply-bar); `project-health` (project-health-git-init); `projects` (projects-project-rename);
  - `run-result` (run-result-open); `running-work-sheet` (running-work-sheet-run-in-background); `server-switcher-sheet` (server-switcher-sheet-disconnect); `session-export` (session-export-save); `session-import` (session-import-submit); `session-note` (session-note-remove, session-note-save);
  - `shell-output` (shell-output-stop, shell-output-timeout); `skill-activation-sheet` (add); `staged-revert` (staged-revert-actions); `tailscale-setup` (tailscale-setup-primaries); `team-board` (team-board-add); `team-host-sheet` (host-submit);
  - `team-phone-onboarding-failed` (failed-retry); `team-phone-onboarding-killed` (killed-start-again); `team-phone-onboarding-offer` (offer-set-up, offer-skip); `team-phone-onboarding-success` (success-open-work); `team-plugin-sheet` (team-sheet-add, team-sheet-turn-off);
  - `terminal` (terminal-new-fab); `termux-processes` (stop-all); `termux-processes-details-sheet` (stop); `termux-storage` (clean); `theme-pack-preview-sheet` (theme-apply); `web-sources` (web-refresh-providers, web-search, web-use-selected); `workspace` (workspace-isolated-task, workspace-section-menu); `worktrees` (worktrees-create).
- **Code (G16, outside the kit):** the raw button families these slots replace:
  - `TextButton` 199 uses in 81 files;
  - `FilledButton` 111 in 56;
  - `OutlinedButton` 37 in 26;
  - `OverflowBar` 1 (`review_workspace.dart`).

  `FloatingActionButton` (3) becomes the KitScreen bottom primary (R23, kit-KitScreen-v2), not this unit.
- **The copy half of `Clipboard.setData(`** (47 uses in 37 files, G2), where the control is a text button such as "Copy details" or "Copy all": `KitAction.copy` (KIT-23).
- **The design-standard rule** "a disabled button needs a reason visible near it", which today is only a review item (§2).

## File

- `lib/ui/kit/kit_buttons.dart`: `KitAction`, `KitButton`, `KitActionBlock`, `KitInset`.
- `lib/ui/kit/kit_action_stack.dart`: `KitActionStack`.
- **Tests:** `test/kit/kit_action_test.dart` (one file for KitAction, KitButton, KitActionBlock and KitActionStack: the name `build_units.py` derives for this unit).
- **Gallery:** `test/goldens/kit/kit_action_golden_test.dart`, one file with a KitActionBlock group and a KitActionStack group. The G4 manifest maps both exports (`kit_buttons.dart`, `kit_action_stack.dart`) to it (README.md convention: one gallery file per unit).

## Public API

```dart
/// One action a kit part can show. Its place (primary, secondary,
/// tertiary, menu) is decided by the slot, never by styling (KIT-8).
@immutable
class KitAction {
  const KitAction({
    required this.label,
    required this.onPressed,
    this.icon,
    this.key,
    this.destructive = false,
    this.working = false,
    this.disabledReason,     // new
    this.shortcut,           // new
  }) : copyText = null;

  /// A text action that copies (KIT-23): "Copy details", "Copy all".
  /// Runs KitCopy.copy(context, text()) at tap time; the button shows a
  /// check and "Copied" for KitMotion.copiedHold; announced once; no SnackBar.
  /// redact (default true) masks secrets; the person's own content passes
  /// false to copy verbatim (SEC-13, R5).
  KitAction.copy({
    required this.label,
    required String Function() text,
    this.icon = AppIconography.copy,
    this.key,
    this.shortcut,
    this.redact = true,
  }) : copyText = text,
       onPressed = null,
       destructive = false,
       working = false,
       disabledReason = null;

  /// A verb naming what happens (COPY-8): "Delete conversation".
  final String label;

  /// Null disables it; then [disabledReason] must say why (see below).
  final VoidCallback? onPressed;
  final IconData? icon;
  final Key? key;

  /// Loses data or ends running work (LOOK-5). Never primary outside a
  /// KitConfirmSheet (§4.2). The act confirms first or offers Undo per
  /// DATA-11 (K2 §4.1); this flag decides neither. (Corrects today's doc,
  /// "Confirm before acting", per Appendix A #20.)
  final bool destructive;

  /// This action's own tap is in flight (a second or two); never lasting
  /// status (STATE-7). A disabled action is never working (the ratchet bans
  /// a disabled action with a spinner, C21 f).
  final bool working;

  /// Why it cannot run now, in one short sentence: "Fill in the server
  /// address first." Rendered as one muted line under the button by
  /// KitActionBlock and KitActionStack, and as the button's semantic hint.
  final String? disabledReason;

  /// The key combination that does the same ("Ctrl+Enter"), as the
  /// shortcuts help writes it. Shown after the label on a fine pointer from
  /// expanded up (visual language §5 "keyboard hints on buttons"). Display
  /// only; the shortcut layer binds it.
  final String? shortcut;

  /// Set only by [KitAction.copy].
  final String Function()? copyText;

  /// True when it can be pressed: an [onPressed] or a copy.
  bool get enabled => onPressed != null || copyText != null;
}

enum KitButtonRole { primary, secondary, tertiary }

/// Unchanged public constructors (KitButton, .primary, .secondary,
/// .tertiary, .fromAction) and fields; gains, additively:
class KitButton extends StatelessWidget {
  // new optional named parameter on every constructor:
  //   String? shortcut,            // the hint shown at the end on a fine pointer
  // KitButton.fromAction also carries action.shortcut and action.copyText;
  // a copy action renders the copied check (a private stateful child).
}

/// The one action block (design standard §2). Same constructor as today.
class KitActionBlock extends StatelessWidget {
  const KitActionBlock({
    super.key,
    this.primary,
    this.secondary,
    this.tertiary = const [],
    this.menu,
  });

  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;

  /// The caller's own overflow widget, where "More" goes.
  /// Retired by kit-KitAction-v2: pass the extra actions in [tertiary]
  /// (the block builds "More" itself). Kept working (KIT-43).
  final Widget? menu;

  bool get isEmpty;
}

/// Every rare path on a line of its own. Same constructor as today.
class KitActionStack extends StatelessWidget {
  const KitActionStack({
    super.key,
    this.primary,
    this.secondary,
    this.tertiary = const [],
  }) : assert(tertiary.length <= 2);
}

/// Unchanged.
class KitInset extends StatelessWidget {
  const KitInset({super.key, required this.child});
}
```

- **Layout rules (behaviour, same public API):**
  1. **Compact, or a short window (`KitLayout.isShort`):** stacked, as today. Primary and secondary are full width, then up to two tertiary actions start-aligned in a wrap, then "More".
  2. **Medium and wider, and not short** (`KitLayout.windowOf(context) != KitWindow.compact`): one end-aligned `Wrap`, with tertiary actions and "More" first and then secondary and primary, so the primary is at the end (LAY-13, VL §5, Appendix A #42). This replaces today's `constraints.maxWidth >= 600` literal (LAY-2): the decision comes from the window class, and the wrap still handles narrow panes.
  3. **Destructive stacking (§2.7, LAY-14):** when any `tertiary` action is `destructive`, `KitActionBlock` lays out exactly as `KitActionStack` on every window. It keeps `space2` (8 dp) between the destructive action's 48 dp area and every other target (LAY-9). A destructive action beyond the first two tertiary actions goes into "More", last, after a divider (KitMenu ordering).
  4. **Disabled reasons (§2.7, STATE-8):** each disabled action with a `disabledReason` gets one muted line (`note`) directly under its button, start-aligned. In the medium-and-up row, the reasons collect in one column under the row, end-aligned, in slot order.
  5. **One primary per block:** this is structural, since there is one `primary` slot. The debug "one visible primary per screen" check is kit-KitScreen-v2's (C06), not this unit's.
- **`calm` and `neutral` (2026-10-03, open-project polish):** both additive, default false, carried by `KitButton.fromAction`.
  - `calm: true` on a primary action eases the accent fill toward `surface1` (32 %) in dark mode only, so a sheet's one main action does not glare at night. Light mode and the global accent token are unchanged; no other screen is touched. The Open a project sheet's "Create and open" uses it.
  - `neutral: true` in the primary slot draws a neutral secondary button (`surface3`), never the accent: for a control that is not the main act, such as "Stop" while a search runs.
- **Internal keys (TEST-5):** keep `kit-actions-more` (used by 9 tests) and `kit-button-working`. Add `kit-action-reason` (each reason line; in tests, find it by its text first) and `kit-action-copied`.
- **The "More" tooltip:** it moves from `chatUiMore` to the new key `kitMore`, with the same English "More", so tests that find it by text keep passing.

## States

| State | Look / behaviour |
|---|---|
| default | VL buttons: primary is `accent` with `onAccent`; secondary is `surface3` with `text1`; tertiary is `accent` words (`danger` when destructive), so an enabled inline action never reads as disabled (R5, 2026-09-27) |
| disabled with reason | the button in `surface3`/`text3` (primary and secondary) or `text3` (tertiary), never partial opacity; the reason line in `text2` under it; semantics `enabled: false`, hint = reason |
| disabled without reason | renders as today (KIT-43); strict mode asserts (see Open questions) |
| working | the spinner in the icon slot, as today (primary and secondary only; tertiary has no spinner) |
| destructive (tertiary or secondary) | words in `danger`; a destructive tertiary makes the whole block stack |
| destructive primary | only `KitButton.primary(destructive: true)` inside `KitConfirmSheet` paints `dangerFill`/`onDangerFill`. `KitActionBlock` with a destructive primary asserts in strict mode (§4.2, G37). |
| copied (`KitAction.copy`) | the label is replaced by a check glyph and l10n.kitCopied ("Copied") for `KitMotion.copiedHold` |
| with shortcut (fine pointer, expanded up) | the label, then the shortcut in `mono` `text3` (tertiary) or in the button's own foreground (primary and secondary) |

`KitActionBlock` renders nothing when empty (`isEmpty`), as today. It has no loading, empty or error state of its own: it shows the host's actions (KIT-12).

## Tokens

- **ThemeRoles:** `accent`, `onAccent`, `surface3`, `text1`, `text2`, `text3`, `danger`, `dangerFill`, `onDangerFill`.
- **KitTokens:**
  - `buttonHeight` (50), `buttonRadius` (14), `minTarget` (48);
  - `space1` (4), `space2` (8, between stacked buttons, around a destructive target, between row items);
  - `note` (the reason line, KitText `secondary` in `text2`);
  - `technicalValue` is not used.
- **KitText:** `button` for labels, `secondary` for reasons, `mono` for the shortcut hint.
- **KitMotion:** `quick`, `enter`, `exit`, `reduced(context)`, as today.
- **KitLayout:** `windowOf`, `isShort` and `finePointer` (all on the VL branch).
- **Not yet on the VL branch** (STANDARDS §0.5 step 2): `KitMotion.copiedHold`, `KitCopy.copy`, `KitBidi.ltr` for the shortcut, and `KitTokens.focusRingWidth` for keyboard focus on buttons.
- **New tokens:** none. The existing literals in `kit_buttons.dart` (the 19 dp icon slot, the 18 dp spinner, `tertiaryInset` 8, `Size(…)`) move onto `KitTokens` names under the G21 kit ratchet. The icon slot and the spinner both become `smallIconSize` (20, LOOK-33); `tertiaryInset` becomes `space2`. The pre-wave names above are listed once in `_new-tokens.md`.

## Adaptive

| Window (§8.1) | KitActionBlock | KitActionStack |
|---|---|---|
| compact | stacked (rule 1); buttons 50 dp, full width | full width, one line each |
| medium | one end-aligned row (rule 2), unless a destructive tertiary forces the stack (rule 3) | stacked, full width of its container |
| expanded / large | the same row, inside the part's own width (a 560 dialog, a 720 reading column); the shortcut hints show with a fine pointer | stacked |
| short window (< 480 dp tall) | stacked (LAY-3) | stacked |

- **Keyboard (LAY-10):** Tab follows the visual order, which is reading order in the stacked layout and start-to-end in the row. Enter and Space press the focused button, and the focus ring is always visible. The block binds no Enter-for-primary; that is the modal part's job (§8.2).
- **Pointer:** hover shows the fill change. Targets stay 48 dp or larger (§8.3).

## Accessibility

- **Each button:** `button: true`, named by `label`; `enabled` follows `enabled`; the hint is `disabledReason` when disabled.
- **The reason line:** plain text, read after the button in traversal order (A11Y-4). It is not a live region: a reason appearing is not announced (A11Y-3); the host announces state changes once.
- **Copy:** "Copied" is announced once by `KitCopy`.
- **Targets:** 48 dp minimum; 8 dp clear around a destructive action (LAY-9, G37).
- **200 % text:** labels wrap to two lines (`maxLines` 2, as today) and reasons wrap. The row falls back to wrapping runs. There is no overflow at 320 dp (G6).

## RTL

- Stacked: tertiary actions start-aligned through `KitInset`, whose translation already mirrors.
- Row: end-aligned, so the primary is at the left in RTL.
- Reason lines are start-aligned in the stack and end-aligned under the row.
- The shortcut hint is isolated with `KitBidi.ltr`.
- Padding is directional; there is no `EdgeInsets.only(left:/right:)` (G7).

## Motion and haptics

- **Working:** the spinner cross-fade, as today, on `KitMotion.quick`. `AnimatedSize` is allowed only for the button spinner slot (MOT-5, the G2 allowlist).
- **Copied:** the check and the label cross-fade on `KitMotion.quick`. The hold is a timer, not an animation.
- **No other animation:** the change between the row and the stack follows the window class and is not animated.
- **Reduced motion:** every swap is instant (G8).
- **Haptics:** none. `KitHaptics.commit` belongs to KitConfirmSheet's confirm, and `send` to the part that sends (MOT-11).

## Data safety and honest state

- **Destructive:** the flag only styles and places the action. Whether the act confirms or offers Undo follows DATA-11 and the K2 §4.1 table, the same from every door.
  - A destructive tertiary is never adjacent to a frequent action (the stack).
  - A destructive primary exists only inside a confirmation.
- **Disabled:** a disabled action always says why, as visible text (STATE-8), or it is left out. A disabled action never shows a spinner, and a button never shows lasting status (STATE-7). "Starting the server…" is a `KitStateView` or `KitProgress`.
- **Copy:** `KitAction.copy` goes through `KitCopy`, which redacts (G12) and is never given a secret (SEC-3). Only `redact: false`, for the person's own content, copies verbatim (SEC-13); in "More" the item keeps the action's `redact`.

## Depends on

- **Units:** none (tier 1a, C25). The edge to kit-KitMenu and kit-KitIconButton-v2 is **not** added (README.md, decision D3): it would move this unit to tier 1b and push every part that adds Action-v2 as a tier-1a edge (Field, DetailsFold, Dialog, Nav, TopBar) one tier later. "More" stays the kit-internal overflow here, with destructive items sorted last after a divider by this unit, and kit-hygiene (2d) moves it onto `KitIconButton` + `showKitMenu`.
- **Shared seams (STANDARDS §0.5 step 2):** `KitCopy`, `KitBidi`, `KitMotion.copiedHold`, `KitTokens.focusRingWidth`.
- **Depend on it (C25):** kit-KitDiffView, kit-KitChecklist, kit-KitRequestCard-v2. Every part with a `KitActionBlock` slot (KitSheet, KitStateView, KitNotice, KitRequestCard) inherits the new behaviour without an edit.

## Tests required

These go in `test/kit/kit_action_test.dart` (G9, G37, G14):

1. A disabled action with `disabledReason` shows the reason text under its button in the block and in the stack. The button's semantics say `enabled: false`, with the reason as the hint.
2. A disabled action paints `text3` or `surface3` at full alpha. No `Opacity` wraps a button (LOOK-14).
3. A block with one destructive tertiary lays out as a stack on compact, medium and large windows. The destructive button's 48 dp area is at least 8 dp from every other target.
4. A block with no destructive tertiary is one row with the primary at the end on medium and larger windows (`KitLayout` classes, not a width literal), and stacked on compact or on a window shorter than 480 dp.
5. With three or more tertiary actions, "More" appears (`kit-actions-more`). A destructive overflow action renders last, after a divider, in the opened menu.
6. `KitAction.copy`:
   - it copies the text read at tap time;
   - "Copied" is announced once;
   - `kit-action-copied` shows, then clears after `KitMotion.copiedHold` (fake async);
   - no `SnackBar`;
   - a fake provider key is redacted (G12).
7. With `working: true`, the spinner shows and taps are ignored. A `working` action with `onPressed: null` fails the G2 source pattern (C21 f), which is recorded in the unit's QA record as covered by kit-gates-ratchet.
8. `shortcut` is shown on a fine pointer at 1280×800, is absent at 412×915 on touch, and is isolated LTR in RTL.
9. Strict mode (Open question 1):
   - a disabled action without a reason asserts;
   - a destructive primary in `KitActionBlock` asserts;
   - `KitButton.primary(destructive: true)` outside a block does not assert.
10. KIT-43 compatibility:
    - every existing constructor call shape compiles, including `menu:`;
    - `KitButton.fromAction` keeps `key`;
    - `kit-actions-more` and `kit-button-working` still resolve.
11. G6: no overflow at the LAY-4 widths × text 1.0/1.3/2.0 × LTR/RTL, with two long tertiary labels and two reasons.
12. G8: it settles after one `pump()` under reduced motion in every state.

## Galleries required

All in `test/goldens/kit/kit_action_golden_test.dart`.

**KitActionBlock group**, at DPR 3.0 (TEST-9, TEST-20):

- **Each state at 412×915, dark and light:** `default` (primary, secondary and two tertiary actions), `disabled` (with reasons), `working`, `destructive_stack`, `overflow` (the More menu open), `copied` and `shortcut`, which is 14 PNGs. The `shortcut` scene is rendered at 412 with desktop capabilities, for the state.
- **`default` at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light:** 10 PNGs. These show the row from medium up and the stack on the 915×412 short window.
- **`default` at text 2.0 and Arabic RTL, at 412×915 and 1280×800, dark and light:** 8 PNGs.
- **Total:** 32 PNGs.

**KitActionStack group:**

- **Each state at 412×915, dark and light:** `default`, `disabled` and `destructive`, which is 6 PNGs.
- **`default` at the 5 other sizes:** 10 PNGs.
- **`default` at text 2.0 and in Arabic, at 412 and 1280:** 8 PNGs.
- **Total:** 24 PNGs.

The unit total is 56 PNGs, under the 60 cap. The existing `kit_foundation_*` goldens are integrator-owned (R07).

## Non-goals

- No new button look: the VL look is already on the branch (f5370bd3).
- No "one primary per screen" check: that is kit-KitScreen-v2.
- No migration of `TextButton`/`FilledButton` call sites (wave 2).
- No split button or segmented action: that is KitSegmented.
- No binding of shortcuts.
- No removal of `menu:`: it is removed by the unit that brings its count to zero.

## Open questions

1. **Structural asserts vs existing call sites (coordinator; the `KitAsserts` seam is listed in `_new-tokens.md`).**
   - **The problem:** C26 and G37 require an assert when a `KitAction` has `onPressed == null` and no `disabledReason`, and when `KitActionBlock` gets a destructive primary. A crude scan finds about 54 possibly-disabled `KitAction` sites in 18 files outside this unit's write set, for example `chat/attention_card.dart`, `phone_setup/*`, `team/gate_sheet.dart` and `servers_screen.dart`. A hard debug assert would change their tests' behaviour, and KIT-43 forbids that.
   - **Proposed:**
     - Add one kit seam, `abstract final class KitAsserts { static bool strict = false; }` in `lib/ui/kit/kit_asserts.dart`. It is written once by the coordinator in STANDARDS §0.5 step 2, with the other shared seams.
     - The structural asserts of every kit part fire only when `strict` is true. `test/kit/kit_asserts_test.dart` (G37) and the kit galleries set it to true.
     - kit-hygiene (2d) flips the default to true after wave 2 has given every disabled action a reason. Each wave-2 unit's acceptance adds "no disabled KitAction without a reason in the write set".
   - **Without the seam:** the unit ships the field and its rendering and does not add the assert. G37's KitAction row waits for kit-hygiene.

Decided in the cross-check (README.md):

- **The "More" overflow on KitMenu:** not in wave 1 (decision D3 above); kit-hygiene moves it.
- **The PC layout with a destructive tertiary:** LAY-14 decides it ("switches to the stacked layout" on every window). There is no third behaviour (PROC-20).
