# Kit v2 (2026-09-26)

> **2026-10-02 (owner, final):** liquid and frosted glass are removed. Wherever this document or a part spec says glass, read a plain solid surface: opaque `surface2`, hairline, token radius, at most the one elevation shadow. `KitGlass` keeps its name as that thin solid part (`docs/ux-system/kit-api/KitGlass.md`).

*Specification for the next version of `lib/ui/kit/`. It is built from the Phase 1 map (`docs/ux-system/map/all.json`), the Phase 2 patterns (`journeys.json` › `patterns`), the verticals (`personas-verticals.json`), the design standard, the principles, and the ui-ux-pro-max checklist. Machine-readable twin: `kit-v2.json`. Nothing here is built yet.*

## What the map says

The map lists 817 elements that are hand-built (`kit: "none"`) or have a `kitGap`. Of these, 294 carried a proposed component, using 61 different names. Many names mean the same thing: `KitEntryDialog`, `KitInputDialog`, `KitTextField` and `KitField` all describe one labelled field. The other elements had no proposal because an existing part already fits (a `ListTile` that should be a `KitRow`).

Kit v2 assigns every one of the 817 elements (`kit-v2.json` › `assignment`):

| Where it goes | Elements |
|---|---|
| **21 new kit parts** (§1) | 452 |
| **Existing kit parts**, some with the changes in §2 | 315 |
| **Module parts, not kit** (§5): chat transcript and composer, platform layers, one-off surfaces | 50 |

### New parts at a glance

| Part | Replaces | Merges these proposed names |
|---|---|---|
| `KitConfirmSheet` | 116 | `KitConfirmSheet`, `KitDiscardGuard` |
| `KitSheet` | 52 | `KitDiscardGuard`, `KitSheet` |
| `KitChoiceList` | 37 | `KitChoiceList`, `KitChoiceRow`, `KitSegmented` |
| `KitField` | 26 | `KitComposerField`, `KitField`, `KitSecretField`, `KitTextField` |
| `KitSegmented` | 25 | `KitChipRow`, `KitFilterBar`, `KitSegmented`, `KitSplitButton` |
| `KitDetailsFold` | 25 | `KitCopyValue`, `KitDetailsFold`, `KitDetailsRow`, `KitTechnicalDetails` |
| `KitIconButton` | 22 | (no proposal; from the verticals and patterns) |
| `KitDialog` | 21 | `KitEntryDialog`, `KitInputDialog` |
| `KitTopBar` | 20 | `KitBrandHeader`, `KitProjectHeader`, `KitShellHeader`, `KitTopBar` |
| `KitLogPanel` | 18 | `KitLogFold`, `KitLogPanel`, `KitLogView` |
| `KitChecklist` | 18 | `KitActionStep`, `KitChecklist`, `KitStageStrip`, `KitStepList`, `KitStepRow` |
| `KitSearchField` | 15 | `KitField`, `KitSearchBar`, `KitSearchField` |
| `KitCodeBlock` | 14 | `KitCodeBlock`, `KitCommandBlock`, `KitCopyValue` |
| `KitViewer` | 11 | `KitDocumentView`, `KitFileViewer` |
| `KitNeedsYou` | 9 | `KitBadge` |
| `KitProgressRow` | 6 | `KitMetric`, `KitProgressRow` |
| `KitDiffView` | 6 | `KitDiffView` |
| `KitJumpPill` | 5 | `KitJumpPill` |
| `KitReceipt` | 3 | `KitReceipt` |
| `KitTerm` | 2 | `KitTerm` |
| `KitUndo` | 1 | `KitUndoSnackbar` |

### How the kit grows (applies to every part below)

- **Entry rule.** A part joins the kit when two or more pages need it. A widget used once stays in its module, built from kit tokens and parts (see §5).
- **Idioms.** Actions are `KitAction`s placed in slots, never styled buttons. Tones come from `AppStatusTone`. Tests get `…Key` parameters. A modal part has a `showKit…` function that returns a `Future`, like `showConfirmSheet` does today. Durations and curves come only from `KitMotion`, and every part is still under `KitMotion.reduced`. Vibration goes only through `KitHaptics`.
- **Every part is checked for:** loading, empty, error and disabled states (a disabled control always shows its reason); motion; haptics; a11y (a semantic label on every control, 48 dp targets with 8 dp between them, a status change announced once, reflow at 200 % text); RTL (directional insets only, technical values isolated LTR); data safety; and honest state. The sections below say only where a part differs from these defaults.

---

## 1. New components

### 1.1 KitSheet: the one sheet frame (52)

**Purpose.** The single frame for every modal bottom sheet: a handle, a header (title, optional one-line subtitle, close), a scrolling body, and a pinned `KitActionBlock`. It replaces the 30+ `showModalBottomSheet` frames, which all differ: different header sizes, some with a handle and some with an X, bands of black between the body and the actions, and sheets that open at 95 % height to show two lines.

**Use it for** a choice, a short task, a request's details, a list of actions on one thing, or a picker. **Don't use it for** a place the person navigates within (use a screen), a blocking alert (use `showKitAlert`), or a done-with-undo (use `showKitUndo`).

```dart
enum KitSheetHeight { content, half, full } // content = as tall as it needs, up to 90 %

Future<T?> showKitSheet<T>(
  BuildContext context, {
  required String title,              // the place in the person's words, ≤ 4 words
  required WidgetBuilder body,        // scrolls inside the frame; never its own Scaffold
  String? subtitle,                   // one muted line: what it is about
  KitSheetHeight height = KitSheetHeight.content,
  KitAction? primary,                 // pinned KitActionBlock (§2 order)
  KitAction? secondary,
  List<KitAction> tertiary = const [],
  ValueListenable<bool>? dirty,       // unsaved input that cannot be kept as a draft
  KitDraft? draft,                    // typed input kept across dismiss (preferred over [dirty])
  RequestRoutes? routes,              // closes itself when its request is answered elsewhere
  bool dismissible = true,            // false only while an irreversible step runs
  Key? sheetKey,
});

/// Typed input kept per target and profile: key `oc.draft.<target>.<profileId>`,
/// so ProfileStore.profileScopedPreferenceKeys sweeps it on profile deletion.
class KitDraft {
  const KitDraft({required this.target, required this.profileId});
}

/// The frame itself, for goldens and a full-screen variant on tablets.
class KitSheet extends StatelessWidget { /* title, subtitle, child, actions, onClose */ }
```

- **States.** Loading: the one 2 dp `KitLoadingBar` sits under the header, with skeleton rows in the body. Empty and error: an inline `KitStateView` in the body. Disabled primary: the `KitAction.disabledReason` line shows under it (§2.7).
- **Motion.** The route's slide on `KitMotion.standard` with `enter`/`exit`. Height changes use a `KitReveal` inside the body, never an animated sheet height. Reduced motion: it appears at once.
- **Haptics.** None.
- **A11y.** The route is named by the title (`namesRoute`, `scopesRoute`), and focus starts on the title. The close button is a `KitIconButton` with the label "Close". The drag handle has a semantic "Dismiss" action. At 200 % text the header title wraps to two lines, the subtitle to two, and the pinned actions stay pinned while the body scrolls. When the keyboard opens, the actions ride above it and the body keeps the focused field visible.
- **RTL.** The close button sits at the end, and all padding is directional.
- **Data safety** (X2, the vertical's first mechanism):
  - With a `draft`, swipe-down, back and close keep the text silently, and reopening restores it. The person is not asked.
  - With only `dirty`, the frame owns the drag (`enableDrag: false` plus its own vertical drag on the handle and header). Back is caught by `PopScope`. Both show the `KitConfirmKind.discard` question *inside* the sheet (see §4.7: no sheet on a sheet).
  - `dismissible: false` is allowed only while an irreversible step runs, and the body says so.
- **Honest state.** A sheet whose request was answered elsewhere closes through `RequestRoutes` and leaves a `KitReceipt` where it was opened ("Answered on the laptop"), so it never disappears without a word.

**Replaces** (gaps merged: `KitDiscardGuard`, `KitSheet`):
<details><summary>52 elements on 41 pages</summary>

`appearance-picker-sheet` (appearance-sheet); `builtin-server-log-sheet` (sheet-frame); `chat-message-actions-sheet`; `chat-read-aloud-voice-sheet`; `coding-settings-shell-sheet` (shell-sheet, shell-sheet-header); `command-launcher-sheet`; `connection-status-details-sheet` (connection-status-details-sheet-frame); `development-services-editor-sheet` (save, sheet); `development-services-logs-sheet` (sheet); `files-row-actions-sheet` (files-row-actions-sheet-header); `form-sheet` (form-sheet, form-sheet-submit); `isolated-task-sheet` (isolated-task-sheet-frame); `language-sheet`; `legacy-drafts-review-sheet` (legacy-insert, legacy-review-sheet); `model-picker-sheet`; `model-picker-sheet-options-dialog` (options-dialog); `model-picker-sheet-unloaded-providers-dialog` (unloaded-dialog); `permission-sheet`; `plugins-mapping-dialog` (mapping-dialog); `plugins-settings` (plugin-details); `project-folder-browser` (project-folder-browser-frame); `prompt-history-sheet`; `prompt-stash-sheet`; `prompt-tools-sheet`; `question-sheet` (question-sheet-frame); `review-comment-sheet` (review-comment-sheet-actions, review-comment-sheet-guard); `run-result-output-sheet`; `running-work-sheet` (running-work-sheet-frame); `server-switcher-sheet` (server-switcher-sheet-frame); `session-approvals-sheet` (session-approvals-sheet, session-approvals-sheet-done); `session-menu-sheet` (session-menu-sheet, session-menu-sheet-header); `settings-transcript-display-sheet` (transcript-sheet); `shortcuts-help-dialog` (shortcuts-help-dialog-close, shortcuts-help-dialog-list); `team-cycle-how-sheet` (cycle-how-body); `team-plugin-sheet` (team-sheet, team-sheet-title); `theme-pack-preview-sheet` (theme-sheet); `timeline-sheet`; `voice-composer-sheet` (voice-composer-sheet, voice-composer-sheet-actions); `voice-model-setup-sheet` (voice-model-setup-sheet, voice-model-setup-sheet-primary); `workspace-archived-sheet` (workspace-archived-sheet-frame); `workspace-context-sheet` (workspace-context-sheet-frame).

</details>

### 1.2 KitConfirmSheet: the one confirmation (116)

**Purpose.** Asking before an act that cannot be undone (see the rules in §4.1). It becomes the kit's own version of `showConfirmSheet`, which today is used in 37 files, lives outside the kit, and has these problems:
- It centres everything.
- Its default icon is "?", even for warnings.
- It fires a raw `HapticFeedback.mediumImpact` that ignores the person's Vibration choice.
- 32 more files still build `AlertDialog`s, many of them confirmations, with right-aligned buttons.

**Use it for** irreversible or server-destructive acts, and for leaving input that cannot be kept. **Don't use it for** anything reversible (use Undo), harmless starts, or a choice between two good options (use a `KitSheet` with a `KitChoiceList`).

```dart
enum KitConfirmKind {
  neutral,     // accent; e.g. "Share conversation?" (says who can see it)
  stop,        // error tone; ends running work: cancel word "Keep running"
  destructive, // error tone; deletes: cancel word "Cancel"
  discard,     // error tone; drops unsaved input: cancel word "Keep editing"
}

Future<bool> showKitConfirm(
  BuildContext context, {
  required String title,               // a question naming the thing: "Stop fox?"
  required String body,                // what happens, and whether it can be undone; ≤ 2 sentences
  required String confirmLabel,        // a verb naming the act: "Delete conversation"
  KitConfirmKind kind = KitConfirmKind.neutral,
  String? cancelLabel,                 // defaults by kind (above)
  IconData? icon,                      // defaults by kind (stop, delete, edit-off, info), never '?'
  List<String> consequences = const [],// counted facts: "3 queued prompts will be deleted"
  String? typedName,                   // heavy deletes: type this exact name to enable confirm
  KitAction? alternative,              // a safer path: "Export first"
  List<KitTechnicalValue> details = const [], // host, path: in a KitDetailsFold, LTR
  RequestRoutes? routes,
  Key? sheetKey,
  Key? confirmKey,
}); // true only when the confirm action was chosen; back, swipe and cancel are false
```

- **Layout.** Start-aligned on the rails, like a `KitStateView` inline: the icon in a tonal circle, then title, body, consequences (attention-tone `KitNotice` lines), the typed-name `KitField`, then a `KitActionStack` with the confirm first and the cancel under it (full width, secondary). The alternative is a tertiary on its own line. For `destructive`, `stop` and `discard` the confirm is error-filled, which is allowed because the whole sheet is that one act (§2).
- **States.** While the act runs, the confirm shows `working`. On failure the sheet stays open with a `KitNotice` failure and Try again, so it never closes on an error.
- **Motion.** As `KitSheet`. When raised from inside a sheet, the confirm replaces that sheet's content in place with a `KitReveal` instead of stacking a second sheet (§4.7).
- **Haptics.** `KitHaptics.commit` (new, §2.13) when a `stop`, `destructive` or `discard` confirm is chosen. This replaces the raw `HapticFeedback` call.
- **A11y.** The title is announced as the route name. The typed-name field is labelled "Type fox to confirm". The confirm stays disabled with a visible reason until the name matches exactly. Both buttons wrap to two lines at 200 %, and the title wraps without an ellipsis.
- **RTL.** The typed name and the details are isolated LTR, because a branch or path mixed into Arabic must not reorder.
- **Data safety.** `consequences` must list anything else that goes with the act ("Queued prompts for this server: 3"). The map found `servers-remove-server-sheet` deleting queued prompts silently.
- **Honest state.** The body says whether the act can be undone. "Revert" names the files it rolls back. Error tone is never used for a harmless act (the map found error tone on `team-agent-restart` and accent on `chat-revert`).

**Replaces** (gaps merged: `KitConfirmSheet`, `KitDiscardGuard`; also the action buttons and icons inside those sheets, and the confirming `AlertDialog`s):
<details><summary>116 elements on 85 pages</summary>

`app-diagnostics-clear-sheet` (clear-diagnostics-sheet); `builtin-server-remove-confirm-sheet` (confirm); `chat-cancel-inbox-send-sheet` (chat-cancel-inbox-send-sheet, chat-cancel-inbox-send-sheet-cancel, chat-cancel-inbox-send-sheet-confirm); `chat-delete-message-sheet` (chat-delete-message-sheet, chat-delete-message-sheet-cancel, chat-delete-message-sheet-confirm); `chat-discard-queued-draft-sheet` (chat-discard-queued-draft-sheet, chat-discard-queued-draft-sheet-cancel, chat-discard-queued-draft-sheet-confirm); `chat-draft-attachment-recovery-sheet` (chat-draft-attachment-recovery-sheet, chat-draft-attachment-recovery-sheet-cancel, chat-draft-attachment-recovery-sheet-confirm, icon); `chat-leave-unsaved-draft-sheet` (chat-leave-unsaved-draft-sheet, chat-leave-unsaved-draft-sheet-keep-editing, chat-leave-unsaved-draft-sheet-leave); `chat-pending-photo-sheet` (chat-pending-photo-sheet, chat-pending-photo-sheet-cancel, chat-pending-photo-sheet-confirm, icon); `chat-read-aloud-consent-sheet`; `chat-resend-queued-draft-sheet` (chat-resend-queued-draft-sheet, chat-resend-queued-draft-sheet-cancel, chat-resend-queued-draft-sheet-confirm); `chat-revert-confirm-sheet` (chat-revert-confirm-sheet, chat-revert-confirm-sheet-cancel, chat-revert-confirm-sheet-confirm); `chat-share-confirm-sheet` (chat-share-confirm-sheet, chat-share-confirm-sheet-cancel, chat-share-confirm-sheet-confirm); `chat-stash-attachments-unavailable-sheet` (chat-stash-attachments-unavailable-sheet, chat-stash-attachments-unavailable-sheet-cancel, chat-stash-attachments-unavailable-sheet-confirm); `chat-stash-restore-confirm-sheet` (chat-stash-restore-confirm-sheet, chat-stash-restore-confirm-sheet-cancel, chat-stash-restore-confirm-sheet-confirm); `command-auth-sheet-confirm-sheet` (confirm); `confirm-sheet` (confirm-sheet-cancel, confirm-sheet-confirm, confirm-sheet-frame); `console-organization-switch-dialog` (console-organization-switch-dialog-confirm); `credential-management-sheet-remove-sheet` (confirm); `development-services-confirm-sheet` (confirm); `external-agent-detail-delete-sheet`; `external-agents-delete-sheet`; `external-link-dialog` (external-link-dialog-cancel, external-link-dialog-host, external-link-dialog-open); `external-task-cancel-sheet`; `external-task-forget-sheet`; `form-sheet-dismiss-confirm-sheet`; `gate-sheet-confirm-sheet` (gate-confirm); `global-sessions-continue-here-sheet` (global-sessions-continue-here-sheet-confirm); `integrations-disconnect-provider-sheet` (confirm); `integrations-forget-pending-auth-sheet` (confirm); `integrations-forget-uncertain-auth-sheet` (confirm); `integrations-remove-mcp-sheet` (confirm); `legacy-drafts-delete-sheet` (legacy-delete-sheet); `managed-workspaces-remove-dialog` (managed-workspaces-remove-dialog-confirm, managed-workspaces-remove-dialog-confirm-text); `permission-sheet-always-dialog`; `phone-setup-progress-stop-sheet` (confirm); `plugins-clear-mappings-sheet` (clear-mappings-sheet); `privacy-settings-clear-drafts-sheet` (clear-drafts-sheet); `privacy-settings-clear-queued-sheet` (clear-queued-sheet); `profile-editor-discard-sheet`; `profile-monitor-switch-server-dialog`; `project-health-git-init-dialog` (project-health-git-init-dialog-confirm); `prompt-editor-discard-sheet`; `prompt-stash-delete-sheet`; `provider-quota-clear-dialog`; `provider-quota-enroll-dialog`; `question-sheet-dismiss-dialog` (question-sheet-dismiss-dialog-confirm); `remove-local-agents-confirm-sheet` (confirm); `restart-local-agents-sheet` (confirm); `saved-permissions-revoke-dialog` (revoke-dialog); `server-settings-upgrade-sheet`; `servers-remove-server-sheet`; `session-destination-confirm-dialog` (session-destination-confirm-dialog-confirm); `session-note-discard-dialog`; `settings-disconnect-sheet` (disconnect-body, disconnect-sheet); `shell-output-stop-dialog` (shell-output-stop-dialog-confirm); `stage-revert-sheet`; `staged-revert-confirm-sheet`; `stop-local-agents-confirm-sheet` (confirm); `team-agent-restart-confirm-sheet` (team-agent-restart-confirm); `team-agent-stop-confirm-sheet` (team-agent-stop-confirm); `team-board-cancel-confirm-sheet` (team-board-cancel-confirm); `team-cycle-stop-confirm-sheet` (cycle-stop-confirm); `team-merge-approve-sheet` (approve-confirm); `team-merge-confirm-sheet` (merge-confirm); `team-phone-remove-sheet` (phone-remove-confirm); `team-phone-stop-sheet` (phone-stop-confirm); `team-run-cancel-confirm-sheet` (team-run-cancel-confirm); `team-turn-off-sheet` (turn-off-confirm); `terminal-remove-sheet`; `termux-processes-stop-group-sheet` (confirm); `termux-processes-stop-one-sheet` (confirm); `termux-setup-replace-installed-sheet` (confirm); `termux-setup-restart-sheet` (confirm); `termux-setup-start-installed-sheet` (confirm); `termux-setup-switch-runtime-sheet` (confirm); `termux-setup-unchecked-install-sheet` (confirm); `termux-setup-update-sheet` (confirm); `termux-storage-clean-sheet` (confirm); `usage-budget-clear-dialog`; `voice-model-setup-sheet-delete-dialog`; `workspace-archive-session-sheet` (workspace-archive-session-sheet-confirm); `workspace-delete-session-sheet` (workspace-delete-session-sheet-confirm); `workspace-share-session-sheet` (workspace-share-session-sheet-confirm); `worktrees-remove-dialog` (worktrees-remove-dialog-confirm-text, worktrees-remove-dialog-warning); `worktrees-reset-dialog` (worktrees-reset-dialog-confirm).

</details>

### 1.3 KitDialog: short input and blocking alerts (21)

**Purpose.** The only two uses of a dialog left by principle 7:
- one short text entry: rename, a folder name, a code, a budget;
- an alert that has to block.

It replaces rename, entry and input `AlertDialog`s. Those have right-aligned clusters, counters that are always visible, and labels that exist only as placeholders.

**Don't use it for** a confirmation (use `showKitConfirm`), several fields (use a `KitSheet` or a screen), or a detail view with a lone "Done" (use a `KitSheet` or `KitDetailsFold`).

```dart
Future<String?> showKitInputDialog(
  BuildContext context, {
  required String title,                  // "Rename conversation"
  required String label,                  // visible field label, never placeholder-only
  required String confirmLabel,           // a verb: "Rename"
  String? initial,                        // prefilled and selected
  String? helper,                         // wraps to 2 lines, never cut
  KitFieldKind kind = KitFieldKind.text,  // text | mono | path | number | secret
  int? maxLength,                         // the counter appears only from 80 %
  String? Function(String value)? validate,          // error under the field
  Future<String?> Function(String value)? onSubmit,  // async: the error stays in the dialog
  KitAction? alternative,                 // e.g. "Remove budget" (destructive: on its own line)
  Key? dialogKey,
}); // null on cancel

Future<void> showKitAlert(
  BuildContext context, {
  required String title,
  required String body,
  List<KitTechnicalValue> details = const [],
  KitAction? action,           // at most one, e.g. "Open Files"
  String? closeLabel,          // default "Close"
});
```

- **Layout.** Title, `KitField`, then a stacked `KitActionBlock`: the verb as primary, Cancel as tertiary. From 600 dp wide the block may sit in one row with the primary rightmost (§2).
- **States.** Submit is disabled with its reason (for example "Name is empty") until valid. While `onSubmit` runs the primary shows `working`. A server error shows under the field and the typed text is kept.
- **Motion.** The framework's dialog fade and scale on `KitMotion.standard`; instant under reduced motion.
- **Haptics.** None.
- **A11y.** The field is focused and its text selected on open. The IME action submits. The helper is not truncated at 200 %: this was the critical defect in `integrations-mcp-oauth-code-dialog`.
- **RTL.** Mono, path and secret kinds are LTR.
- **Data safety.** Rotating the phone or leaving the app keeps the text (restorable state).

**Replaces** (gaps merged: `KitEntryDialog`, `KitInputDialog`):
<details><summary>21 elements on 16 pages</summary>

`chat-rename-session-dialog` (chat-rename-session-dialog, chat-rename-session-dialog-rename, chat-rename-session-dialog-title-field); `chat-run-shell-dialog` (chat-run-shell-dialog, chat-run-shell-dialog-command-field, chat-run-shell-dialog-run); `credential-management-sheet-rename-dialog` (dialog); `external-agent-detail-input-dialog`; `file-drop-failed-dialog` (file-drop-failed-dialog-close, file-drop-failed-dialog-dialog); `integrations-authorization-launch-dialog` (dialog); `integrations-oauth-code-dialog` (dialog); `integrations-oauth-inputs-dialog` (dialog); `project-folder-new-dialog` (project-folder-new-dialog-name); `project-folder-open-dialog` (project-folder-open-dialog-path); `projects-rename-dialog` (projects-rename-dialog-name); `run-command-dialog`; `terminal-rename-dialog`; `usage-budget-dialog`; `workspace-rename-session-dialog` (workspace-rename-session-dialog-title); `worktrees-create-dialog` (worktrees-create-dialog-name).

</details>

### 1.4 KitField: one labelled field (26)

**Purpose.** Every text input outside search. Today these are raw `TextField`/`TextFormField`s (92 sites), with placeholder-only labels, counters that are always on, helpers that get cut off, and secrets shown in clear.

```dart
enum KitFieldKind { text, multiline, mono, path, url, number, secret }

class KitField extends StatefulWidget {
  const KitField({
    super.key,
    required this.label,                 // shown above the field; the semantic label
    this.controller,
    this.kind = KitFieldKind.text,
    this.helper,                         // ≤ 2 lines, muted, wraps
    this.error,                          // replaces the helper; error tone + icon, a live region
    this.maxLength,                      // the counter appears from 80 % of the limit
    this.draft,                          // multiline input the person wrote: kept across dismiss
    this.enabled = true,
    this.disabledReason,                 // required when !enabled
    this.onChanged,
    this.onSubmitted,
    this.textInputAction,
    this.autofocus = false,
    this.fieldKey,
  });

  /// Obscured, with a reveal toggle and a Paste button. Never prefilled
  /// (asserts an empty controller). No suggestions or IME learning. Its
  /// value is excluded from diagnostics and the report service.
  const KitField.secret({super.key, required this.label, this.controller, this.helper, this.error, ...});
}
```

- **States.**
  - Validating: the helper line says "Checking…". There is no spinner inside the field, and after 8 s it says so and offers a way out (§4.9).
  - Error: shown under the field, never only at the top of the form.
  - Disabled: dimmed, with its reason shown in the helper.
- **Motion.** The error line folds in with a `KitReveal`. No `AnimatedSize` (the map found one at 240 ms in `permission-sheet-reject-message`).
- **Haptics.** None.
- **A11y.** The label is above the field, not floating, so it stays readable at 200 %. Targets are 48 dp. The reveal and paste buttons are `KitIconButton`s with labels. The counter is announced only when it appears.
- **RTL.** `mono`, `path`, `url` and `secret` are LTR with bidi isolation. Text fields follow the locale.
- **Data safety.** The `multiline` kind requires a `draft` when it sits in a sheet. Team message, board add and start-run all lose their text today.
- **Security.** The secret kind is the only way to enter an API key, bearer token or OAuth code. `mcp-setup`'s headers field (a critical finding: tokens in clear) becomes a list of name + `KitField.secret` pairs. A saved value is never shown again, only "Saved · Replace".

**Replaces** (gaps merged: `KitComposerField`, `KitField`, `KitSecretField`, `KitTextField`):
<details><summary>26 elements on 25 pages</summary>

`add-agent` (add-agent-bearer); `development-services-editor-sheet` (fields); `embedded-question-options` (embedded-question-options-custom-answer); `form-sheet` (form-sheet-fields); `integrations-connect-key-dialog` (key-field); `integrations-mcp-oauth-code-dialog` (code-field); `integrations-oauth-inputs-dialog` (prompt-field); `isolated-task-sheet` (isolated-task-sheet-name); `local-agent-project-sheet` (path-field); `managed-workspaces-create-dialog` (managed-workspaces-create-dialog-branch); `mcp-setup` (fields, mcp-headers); `permission-sheet` (permission-sheet-reject-message); `phone-setup-ready` (name-field); `project-folder-browser` (project-folder-browser-name); `prompt-editor` (prompt-editor-field); `question-sheet` (question-sheet-custom-answer); `review-comment-sheet` (review-comment-sheet-field); `session-note` (session-note-editor); `start-run-sheet` (start-objective); `tailscale-setup` (tailscale-setup-address); `team-agent-message-sheet` (team-agent-message-field); `team-board-add-sheet` (team-board-add-field); `team-conversation` (team-conversation-composer); `team-host-sheet` (host-address); `voice-composer-sheet` (voice-composer-sheet-draft-field).

</details>

### 1.5 KitSearchField: find in a list (15)

**Purpose.** One search field for lists, sheets and viewers. It replaces 10+ raw `TextField`s with a magnifier, which use different rails, have unlabelled filter carets, and appear on lists of two items.

```dart
class KitSearchField extends StatefulWidget {
  const KitSearchField({
    super.key,
    required this.hint,                 // "Search models"; also the semantic label
    required this.onChanged,            // debounced
    this.controller,
    this.resultCount,                   // announced politely once the typing settles: "12 results"
    this.filters = const [],            // List<KitMenuItem>: a labelled filter menu at the end
    this.activeFilter,                  // shown as words: "Symbols", never an unlabelled caret
    this.autofocus = false,
    this.fieldKey,
  });
}
```

- **Where.** In `KitScreen.header`, or the header of a `KitSheet`, on the 16 dp rails. Shown only when the list can exceed about 8 items.
- **States.** No match: an inline `KitStateView` ("No models match 'opus'") with **Clear search**. Loading remote results: the screen's `KitLoadingBar`.
- **Motion.** The clear button fades on `KitMotion.quick`.
- **A11y.** The clear button is a 48 dp `KitIconButton` labelled "Clear search". The result count is a polite live region. Esc and the back gesture clear first, then leave.
- **RTL.** The magnifier sits at the start, clear at the end, both directional.
- **Honest state.** When a list is only partly loaded, the count says so: "12 loaded · searching the server…".

**Replaces** (gaps merged: `KitField`, `KitSearchBar`, `KitSearchField`):
<details><summary>15 elements on 15 pages</summary>

`active-context` (active-context-search); `capabilities` (capabilities-search); `command-launcher-sheet` (command-launcher-sheet-search); `command-palette-dialog` (command-palette-dialog-query); `commands` (search); `embedded-transcript-find-bar`; `files` (files-search-field); `global-sessions` (global-sessions-search); `legacy-drafts` (legacy-search); `model-picker-sheet` (model-search); `projects` (projects-search); `session-destination-sheet` (session-destination-sheet-filter); `settings` (library-search); `team-home-runs-tab` (team-home-search); `web-sources` (web-query).

</details>

### 1.6 KitSegmented: one choice among 2–4 (25)

**Purpose.** A single choice among two to four short, always-visible options: a scope, a mode, a range, a filter. It replaces raw `SegmentedButton`s, `ChoiceChip` rows posing as one choice, and centred pill toggles. The map found inverted accents ("Raw" highlighted while unselected) and a runtime choice that greys out with no reason.

**Don't use it for** more than 4 options or long labels (use `KitPickerRow`), switching between views that each keep scroll and state (use `KitTabSwitcher` with its strip, §2.10), or on/off (use `KitSwitchRow`).

```dart
class KitSegment<T> {
  const KitSegment({required this.value, required this.label, this.icon, this.count, this.enabled = true, this.key});
  final T value;
  final String label;      // words; an icon alone is never enough
  final IconData? icon;
  final int? count;        // "Needs you 2"
  final bool enabled;
  final Key? key;
}

class KitSegmented<T> extends StatelessWidget {
  const KitSegmented({
    super.key,
    required this.segments,            // 2..4
    required this.selected,
    required this.onChanged,
    this.semanticsLabel,               // the group's name: "Time range"
    this.disabledReason,               // shown under the control when a segment is disabled
  });
}
```

- **Layout.** Full width on the rails and start-aligned, never centred. When the labels do not fit on one line (long words, 200 % text), it becomes a wrapping row of choice chips with the same semantics. It measures this the same way `KitAskLine` does.
- **Motion.** The selection indicator moves on `KitMotion.quick`/`enter`; instant under reduced motion.
- **Haptics.** None, so feedback keeps its meaning.
- **A11y.** 48 dp tall. Each segment carries `selected`, `inMutuallyExclusiveGroup` and `button`. The selected segment also shows a check, so selection is never colour-only.
- **RTL.** The order follows the text direction.
- **Honest state.** A disabled segment shows why ("Chosen at install · reinstall to change").

**Replaces** (gaps merged: `KitChipRow`, `KitFilterBar`, `KitSegmented`, `KitSplitButton`):
<details><summary>25 elements on 22 pages</summary>

`active-context` (active-context-chips); `appearance-settings` (effects-animations); `builtin-server-setup` (runtime-choice); `catalog` (tabs); `embedded-composer` (embedded-composer-delivery-toggle); `embedded-file-preview-body` (embedded-file-preview-body-mode); `embedded-mobile-task-list` (embedded-mobile-task-list-filter); `global-sessions` (global-sessions-folder-chip); `mcp-setup` (kind-segmented, scope-segmented); `model-picker-sheet` (model-filters); `model-picker-sheet-options-dialog` (options-variant); `provider-quota` (provider-quota-chips); `review-workspace` (review-workspace-mode, review-workspace-scope-picker); `skill-activation-sheet` (view-toggle); `skills-preview-sheet` (view-toggle); `team-host-sheet` (host-kind); `team-run-timeline-tab` (team-run-timeline-filter); `terminal` (terminal-source); `theme-pack-preview-sheet` (theme-mode-chips); `usage` (usage-range); `usage-hub` (usage-range, usage-scope); `workspace` (workspace-new-mode).

</details>

### 1.7 KitChoiceList, KitChoiceRow, KitPickerRow: picking from a list (37)

**Purpose.** One way to pick. It covers:
- question options, form radios and `choices` blocks;
- server, destination, organisation, language, voice and shell choices;
- every `DropdownButton` (a row that shows the current value and opens a sheet).

Today these are radio `ListTile`s, bordered cards, trailing checks set about 40 dp in, and a sheet that opens a dialog that opens a dropdown, three layers for one word.

```dart
class KitChoice<T> {
  const KitChoice({required this.value, required this.title, this.supporting, this.leading, this.enabled = true, this.disabledReason, this.key});
}

class KitChoiceList<T> extends StatelessWidget {
  /// One answer. [actsOnTap]: a tap chooses and closes the host sheet, or
  /// sends; there is no separate Apply. When it sends, the host shows a
  /// KitReceipt, with Undo where the server allows it.
  const KitChoiceList.single({
    super.key,
    required this.choices,
    required T? selected,
    required ValueChanged<T> onSelected,
    this.actsOnTap = true,
    this.other,                         // KitChoiceOther: "Something else" + a KitField
  });

  /// Several answers. The host's primary applies them ("Use 3 sources").
  const KitChoiceList.multi({super.key, required this.choices, required Set<T> selected, required ValueChanged<Set<T>> onChanged});
}

/// A KitRow with a leading selection mark (radio or check) and the
/// "Current" word for the value in use now.
class KitChoiceRow<T> extends StatelessWidget { /* ... */ }

/// A setting row that shows its value and opens a KitSheet with a
/// KitChoiceList.single: "Language · English ›". Replaces DropdownButton.
class KitPickerRow<T> extends StatelessWidget {
  const KitPickerRow({super.key, required this.title, required this.choices, required this.selected, required this.onSelected, this.sheetTitle, this.leading});
}

Future<T?> showKitChoiceSheet<T>(BuildContext context, {required String title, required List<KitChoice<T>> choices, T? selected});
```

- **States.** Loading choices: skeleton rows. None available: an inline `KitStateView` that says why. A disabled choice is dimmed with its reason on its supporting line.
- **Motion.** The mark moves on `KitMotion.quick`. A list that changes while open uses `KitAnimatedRows`.
- **Haptics.** `KitHaptics.send` only when the tap sends an answer to the agent (the person's words leave). Nothing on a local choice.
- **A11y.** Rows are at least 56 dp with the whole row tappable. They carry `selected`, `checked` and `inMutuallyExclusiveGroup` semantics. The mark is a shape (a filled radio or a check), not just colour. Titles and supporting lines may take 2 lines each.
- **RTL.** The mark sits at the start and the chevron at the end, mirrored.
- **Data safety.** The "Something else" text is kept as a draft of the request it answers.
- **Honest state.** Once sent, the row shows the `KitReceipt` state ("Sent", then "Answered"), not only a filled radio.

**Replaces** (gaps merged: `KitChoiceList`, `KitChoiceRow`, `KitSegmented`; also dropdowns and radio lists without a proposal):
<details><summary>37 elements on 31 pages</summary>

`agent-choice` (agent-choice-row); `appearance-picker-sheet` (appearance-choice); `chat-read-aloud-voice-sheet` (chat-read-aloud-voice-sheet-voice-row); `coding-settings-shell-sheet` (shell-choice); `console-organization-sheet` (console-organization-sheet-org); `embedded-markdown-text` (embedded-markdown-text-agent-choice); `embedded-message-view` (embedded-message-view-choice-option); `embedded-question-options` (embedded-question-options-option); `gate-sheet` (gate-options); `language-sheet` (language-choice); `local-agent-project-sheet` (folder-radios); `managed-workspaces-create-dialog` (managed-workspaces-create-dialog-adapter); `model-picker-sheet-agent-dialog` (agent-dialog, agent-dropdown); `model-picker-sheet-options-dialog` (options-agent); `notifications-settings` (notifications-check-in-minutes); `plugins-mapping-dialog` (mapping-checkbox); `profile-editor` (profile-editor-backend); `question-sheet` (question-sheet-option, question-sheet-send); `server-switcher-sheet` (server-switcher-sheet-current, server-switcher-sheet-profile); `servers-welcome` (servers-welcome-choice); `session-approvals-sheet` (session-approvals-sheet-mode); `session-destination-sheet` (session-destination-sheet-destination); `session-export` (session-export-format); `session-import-destination-sheet` (session-import-destination-sheet-choice); `shell-output-timeout-sheet` (shell-output-timeout-sheet-option); `start-run-sheet` (start-project, start-supervision); `team-board-priority-sheet` (team-board-priority-row); `team-phone-onboarding-project-sheet` (project-radio); `termux-setup-choose` (runtime-radios); `voice-model-setup-sheet` (voice-model-setup-sheet-language, voice-model-setup-sheet-pack); `web-sources` (web-provider, web-selected).

</details>

### 1.8 KitDetailsFold and KitTechnicalValue: the one technical fold (25)

**Purpose.** Where the technical truth lives (principle 4): addresses, paths, ids, raw errors and engine words. It is collapsed by default and sits at the end. It replaces five team detail folds and sheets with different title styles, `ExpansionTile`s, a blocking dialog that holds only a path, and error dialogs whose text cannot be copied.

```dart
class KitTechnicalValue {
  const KitTechnicalValue(this.label, this.value, {this.copyable = true, this.key});
  final String label;   // "Address", "Branch", "Interaction id"
  final String value;   // shown mono, LTR, breaking anywhere, selectable
}

class KitDetailsFold extends StatefulWidget {
  const KitDetailsFold({
    super.key,
    this.values = const [],   // deduplicated by value: a value shows once
    this.notes = const [],    // plain lines above the values: what to check
    this.child,               // richer content: a KitLogPanel or KitCodeBlock
    this.label,               // default "Details"
    this.initiallyOpen = false,
    this.foldKey,
  });
}

/// A standalone raw error (a chat error's details): a KitSheet with the
/// fold already open, the text in a KitCodeBlock, and "Copy all".
Future<void> showKitTechnicalDetails(BuildContext context, {required String title, required String text, List<KitTechnicalValue> values = const []});
```

- **Layout.** A muted tertiary "Details ⌄" row (48 dp) that unfolds a surface-container block of rows: label (muted, bodySmall), value (mono), and a trailing `KitIconButton.copy`. It shows "Copy all" when there are 2 or more values.
- **Motion.** `KitReveal` unfold on `KitMotion.standard`; the chevron turns on `emphasized`.
- **Haptics.** None.
- **A11y.** Semantics `expanded`. Each value reads "Address: 100.64.0.3, port 4096" and carries a copy action. Values wrap at 200 % and are never cut.
- **RTL.** Values are isolated LTR inside RTL layouts (the rtl-l10n vertical's first mechanism).
- **Security.** A value that is a secret is refused. Credentials never reach the fold, and a debug assert checks values against the redactor.
- **Honest state.** Engine words (convoy, formula, city, PTY, SSE, `127.0.0.1`) may appear only here. `ui_glossary_test` passes because of that.

**Replaces** (gaps merged: `KitCopyValue`, `KitDetailsFold`, `KitDetailsRow`, `KitTechnicalDetails`; also technical lines shown above the fold today):
<details><summary>25 elements on 22 pages</summary>

`builtin-server-setup` (address-and-checksum); `chat-message-error-details-dialog` (chat-error-details-dialog); `chat-prompt-error-details-dialog` (chat-error-details-dialog); `connection-status-details-sheet` (connection-status-details-sheet-error); `development-services-confirm-sheet` (path-command); `embedded-team-technical-value` (technical-value); `gate-sheet` (gate-details, gate-kicker); `isolated-task-sheet` (isolated-task-sheet-path); `session-import` (session-import-preview); `skills-preview-sheet` (path); `team-agent` (team-agent-technical); `team-agent-details-sheet` (agent-details-values); `team-host-details-sheet` (host-details-values); `team-plugin-sheet` (team-sheet-details); `team-run-details-sheet` (run-details-values); `team-run-overview-tab` (team-run-summary); `termux-processes-details-sheet` (command-folder, pid-subtitle); `voice-composer-sheet` (voice-composer-sheet-technical-details); `voice-model-setup-sheet` (voice-model-setup-sheet-technical-details); `work-sheet` (work-code, work-details); `workspace-directory-details-dialog` (workspace-directory-details-dialog-close); `workspace-session-details-sheet` (workspace-session-details-sheet-lines).

</details>

### 1.9 KitCodeBlock: code, commands and output (14)

**Purpose.** A block of code, a command the person copies to run elsewhere, or tool output. It replaces command cards that clip (a Codex command cut at the edge, a curl URL wrapping over 7 lines), code cards with their own ⋯ inside sheets that also have Copy, and uncapped tool output on the scrolling chat list (a perf finding).

```dart
enum KitCodeKind { code, command, output }

class KitCodeBlock extends StatelessWidget {
  const KitCodeBlock({
    super.key,
    required this.text,
    this.kind = KitCodeKind.code,
    this.language,            // syntax hint only
    this.caption,             // "Run this on your computer"
    this.maxLines = 12,       // capped: "Show all 240 lines" unfolds in place,
    this.onOpenFull,          //   or opens a KitViewer when given
    this.wrap = false,        // otherwise it scrolls sideways, with an edge fade; never clips
    this.copyText,            // what Copy copies, when it differs (no prompt glyph)
  });
}
```

- **Layout.** Edge to edge within its host's rails, surface-container, radius control. A header row holds the caption and a single `KitIconButton.copy`. The `command` kind draws a `$` outside the copied text.
- **Motion.** "Show all" unfolds with `KitReveal`. Never `AnimatedSize` on a scrolling list.
- **Haptics.** None.
- **A11y.** Selectable. The copy button is labelled "Copy command". The horizontal scroll has a visible scrollbar and a semantic scroll action. At 200 % the mono size follows the text scale.
- **RTL.** Always LTR, isolated.
- **Security.** Commands never embed a real token. Placeholders like `<your key>` are used, and a test feeds fake keys through every `KitCodeBlock` caller that builds command strings.

**Replaces** (gaps merged: `KitCodeBlock`, `KitCommandBlock`, `KitCopyValue`):
<details><summary>14 elements on 14 pages</summary>

`connection-help` (connection-help-examples); `continue-on-computer-sheet` (continue-on-computer-command); `embedded-markdown-text` (embedded-markdown-text-code); `embedded-tool-card` (embedded-tool-card-output); `guide` (guide-pair-command); `host-management` (host-management-command); `markdown-code-reader` (markdown-code-reader-body); `permission-sheet` (permission-sheet-command-preview); `profile-editor` (profile-editor-command); `server-settings-restart-dialog`; `session-handoff-dialog`; `team-host-guide-sheet` (guide-body); `team-phone-tips-sheet` (tips-commands); `tools-detail-sheet` (schema).

</details>

### 1.10 KitIconButton and KitIconButton.copy (22)

**Purpose.** The one icon-only control. Its label is required, it has a 48 dp hit area, and it can be tinted as destructive. The copy variant replaces 47 `Clipboard.setData` sites and about 40 "Copied" snackbars. Snackbars are reserved for done-with-undo, and Android 13+ already shows its own clipboard overlay. The a11y vertical's first mechanism, and the map found unlabelled copy, run and stop squares and duplicate refresh icons.

```dart
class KitIconButton extends StatelessWidget {
  const KitIconButton({
    super.key,
    required this.icon,
    required this.tooltip,       // also the semantic label; required, never empty
    required this.onPressed,     // null: dimmed; the tooltip says why
    this.destructive = false,
    this.working = false,        // this tap in flight (≤ a second or two)
    this.selected,               // a toggle (follow output, wrap)
  });

  /// Copies [text] (read at tap time). The icon turns into a check for
  /// KitIconButton.copiedHold, and "Copied" is announced once. No SnackBar.
  const KitIconButton.copy({super.key, required String Function() text, String? tooltip});
}
```

- **Motion.** The icon and the check crossfade on `KitMotion.quick`; they swap instantly under reduced motion. The hold is a state timer, not a motion.
- **Haptics.** None.
- **A11y.** A 48×48 target with 8 dp to its neighbours. Tooltips show on long-press. `selected` maps to toggled semantics.
- **Rule.** A screen shows at most one refresh control: pull (`KitRefresh`) *or* one icon, never both. `saved-permissions` and `managed-workspaces` show two today.

**Replaces:**
<details><summary>22 elements on 21 pages</summary>

`about` (about-report-bug-icon); `attention-overview` (attention-overview-monitor-settings); `capabilities` (capabilities-run); `chat` (chat-appbar-stop-reading); `embedded-composer` (embedded-composer-open-editor, embedded-composer-tools-button); `embedded-message-view` (embedded-message-view-actions-button); `embedded-pending-sends-strip` (pending-actions); `embedded-team-cycle-strip` (team-cycle-action-refresh); `files-changes-sheet` (files-changes-sheet-stage); `integrations` (add-mcp); `legacy-drafts-review-sheet` (legacy-copy); `managed-workspaces` (managed-workspaces-sync); `project-health` (project-health-refresh); `saved-permissions` (saved-permissions-refresh); `team-agent` (team-agent-refresh); `team-home` (team-home-board); `team-run` (team-run-refresh); `terminal-surface` (terminal-surface-header); `termux-processes` (stop-orphan); `timeline-sheet` (timeline-sheet-row-fork); `usage-hub` (usage-refresh).

</details>

### 1.11 KitLogPanel: the one log view (18)

**Purpose.** Live or finished output from a process: server logs, setup output, dev services, shell output, the phone team's steps. It replaces `SetupTerminal`'s "LAST OUTPUT"/"LIVE OUTPUT" boxes, a `TerminalView` inside Details that clips its right edge, two sheets for one server log, and a log that does not follow new lines.

```dart
class KitLogLine {
  const KitLogLine(this.text, {this.tone = AppStatusTone.neutral, this.at});
}

class KitLogPanel extends StatefulWidget {
  const KitLogPanel({
    super.key,
    required this.lines,           // ValueListenable<List<KitLogLine>>; bounded ring buffer
    this.live = false,             // the source is still writing
    this.ended,                    // "Ended · exit 1": the source finished
    this.onRefresh,                // polled while the panel is visible, never off screen
    this.follow = true,            // sticks to the newest line until the person scrolls up
    this.maxLines = 2000,
    this.emptyText,                // "No output yet"
    this.height,                   // folded use: about 12 lines; full use: fills its host
    this.panelKey,
  });
}
```

- **Where.** Folded inside a `KitDetailsFold` ("Show output") by default. It is main content only on a page whose job is the log (shell output, the local agent's live output), or full-height in a `KitSheet`.
- **States.**
  - Empty: `emptyText`.
  - Live: a small "Live" word and dot, plus "Last line 12 s ago" when the output goes quiet for more than 8 s.
  - Ended: the exit words.
  - Failed to read: an inline `KitNotice` with Try again.
- **Look.** Mono, LTR. Muted for ordinary lines, the failure tone for errors, attention for warnings. The accent is never used for ordinary `[oc]` lines. Lines wrap at word boundaries when Wrap is on (a `KitIconButton` toggle); otherwise they scroll sideways.
- **Motion.** When the person has scrolled up, new lines show a `KitJumpPill` ("12 new lines"). There is no auto-scroll animation under reduced motion.
- **Haptics.** None.
- **A11y.** The panel is not a live region (it would flood a screen reader). The header's state word is. Copy all and Wrap are labelled.
- **Security.** Lines pass through the app's redactor before display, so provider keys and bearer tokens never reach the screen.
- **Perf.** Virtualised (`ListView.builder`), with a fixed line height from the text scale. Polling is paused when the panel is off screen (`TickerMode` or route not current).

**Replaces** (gaps merged: `KitLogFold`, `KitLogPanel`, `KitLogView`):
<details><summary>18 elements on 15 pages</summary>

`builtin-server-log-sheet` (inner-header, log-body); `builtin-server-setup` (stop-and-log); `development-services-logs-sheet` (log, refresh-status); `embedded-local-agent-onboarding-block` (log); `embedded-setup-terminal` (log-lines, panel-header); `local-agent-page` (live-output); `phone-setup-progress` (details); `shell-output` (shell-output-log); `team-phone-onboarding-failed` (failed-log); `team-phone-onboarding-steps` (steps-log); `termux-setup` (last-output); `termux-setup-failed` (log); `termux-setup-installed` (last-output); `termux-setup-installing` (live-output); `termux-storage` (scan-log).

</details>

### 1.12 KitChecklist: staged work with person steps (18)

**Purpose.** A job made of steps: phone setup, Termux, Claude Code, AI Team turn-on, voice model, worktree creation, and a team task's cycle. It is the "v2 checklist" in the install-progress pattern and promotes `SetupProgressView` (`lib/ui/widgets/`) into the kit. It replaces numbered step tiles, half-width per-step buttons, spinners mixed with numbers, and a failure mark on the wrong row.

```dart
class KitStep {
  const KitStep({
    required this.title,
    required this.state,            // KitMarkState: waiting, working, done, failed, paused (§2.9)
    this.supporting,                // "29 of 30 MB · about 1 min left"; the failure's reason
    this.personAction,              // a step only the person can do: "Allow", "Get Termux"
    this.retry,                     // on the failed row only
    this.key,
  });
}

class KitChecklist extends StatelessWidget {
  const KitChecklist({
    super.key,
    required this.steps,
    this.progress,                  // KitProgress.staged (§2.8): one bar for the whole job
    this.estimate,                  // shown before it starts: "about 8 minutes the first time"
    this.stop,                      // KitAction; confirms only when work would be lost
    this.resume,                    // after a force stop or a heat pause
    this.log,                       // a KitLogPanel, folded under Details
    this.compact = false,           // one line "Step 3 of 7 · Reviewing · next: merge" that
  });                               //   unfolds to the list (the team stage strip)
}
```

- **States.** Before the start: the estimate and the cost line (`KitNotice.cost`, §2.4). Working: one row working, the bar determinate where the size is known. Person step: a needs-you mark and its action on that row. Failed: the failed row's reason and Try again on that row; the log unfolds on request. Paused for heat: a paused mark and "Resumes when the phone cools". Done: every mark done and `KitHaptics.done` once.
- **Motion.** Marks cross-fade on `KitMotion.quick`, and a new row unfolds with `KitAnimatedRows`. At most one ambient loop per screen, and only while waiting (the host's illustration, not the list).
- **A11y.** Each row reads "Step 2 of 5, Download, working, 29 of 30 MB". A state change is announced once (the job's header is the live region, not each row). Titles wrap at 200 %.
- **Honest state.** A mark comes from the engine's state and is never inferred: "Waiting" is never ticked as done (a map defect). Steps from an existing install show done and "Already installed". A resumed job starts at its step.

**Replaces** (gaps merged: `KitActionStep`, `KitChecklist`, `KitStageStrip`, `KitStepList`, `KitStepRow`):
<details><summary>18 elements on 16 pages</summary>

`builtin-server-setup` (four-step-list, step-primaries); `embedded-local-agent-onboarding-block` (steps); `embedded-mobile-task-list` (embedded-mobile-task-list-progress); `embedded-team-cycle-strip` (cycle-stages); `embedded-team-merge-section` (merge-checks); `guide` (guide-steps); `isolated-task-sheet` (isolated-task-sheet-progress); `local-agent-page` (install-steps, sign-in); `phone-setup-progress` (checklist); `tailscale-setup` (tailscale-setup-step1); `team-phone-onboarding-steps` (steps-marks); `team-run-overview-tab` (team-run-stage-line); `termux-setup-checking` (progress-line); `termux-setup-connected` (steps-done); `termux-setup-get-termux` (steps); `work-sheet` (team-work-sheet-cycle).

</details>

### 1.13 KitProgressRow: a measured amount in a row (6)

**Purpose.** Design standard §4 already names `KitProgressRow` ("inside `KitStateView` or `KitProgressRow`"), but it does not exist. It is a row with a determinate bar: a quota window, a download of one item, context used, a budget. It replaces a `Card` with a `LinearProgressIndicator`, and hero cards below the fold.

```dart
class KitProgressRow extends StatelessWidget {
  const KitProgressRow({
    super.key,
    required this.title,             // "Claude · 5-hour window"
    required this.value,             // 0..1; null while unknown (a skeleton bar, not a spinner)
    this.valueLabel,                 // "62 % · resets in 3 h"
    this.leading,
    this.tone,                       // null: automatic, attention from 80 %, failure at 100 %
    this.asOf,                       // offline or stale: "as of 10:42"
    this.onTap,
  });
}
```

- **Rules.** Built from `KitProgressView` (the kit's one bar). Only `KitScreen`'s loading bar and this row's bar appear on a screen, because the §4 "one loading bar" is about loading, not about measured amounts. A tone always has words with it: "Near limit".
- **A11y.** The bar's semantics carry "62 percent, resets in 3 hours".
- **Honest state.** Last-known values carry their age (the reliability vertical).

**Replaces** (gaps merged: `KitMetric`, `KitProgressRow`):
<details><summary>6 elements on 6 pages</summary>

`agent-account` (agent-account-limits); `provider-quota` (provider-quota-window); `session-context` (session-context-hero); `usage` (usage-total); `usage-hub` (usage-totals); `voice-model-setup-sheet` (voice-model-setup-sheet-progress).

</details>

### 1.14 KitViewer: files and documents (11)

**Purpose.** One viewer for a file, a code block opened full, or a document (About, Privacy, notices, voice notices). It replaces two file viewers with different headers and two copy buttons, four unlabelled icon buttons over a MIME subtitle, Markdown that keeps hard wraps and literal backticks, and a raw-Markdown mono view.

```dart
enum KitViewerKind { code, markdown, text, image, binary }

class KitViewerSource {
  const KitViewerSource.text(String text, {KitViewerKind kind = KitViewerKind.text, String? language});
  const KitViewerSource.load(Future<KitViewerContent> Function() load); // loading / error states built in
}

Future<void> showKitViewer(
  BuildContext context, {
  required String name,                 // "README.md"
  required KitViewerSource source,
  String? path,                         // muted subtitle, LTR, ellipsised in the middle
  KitAction? primary,                   // one labelled action: "Add to prompt"
  List<KitMenuItem> more = const [],    // the rest: Copy path, Share, Open in Files
  bool asPage = false,                  // a page (long documents) instead of a full-height sheet
});
```

- **Layout.** The header is the name plus a muted path, then one labelled primary and an overflow `KitRowMenu`. The body is a renderer: code in a `KitCodeBlock` without its own copy (the header has it), Markdown reflowed as prose (links through `openExternalLink`), images zoomable, binary as a `KitStateView` ("Can't show this file · Share").
- **States.** Loading: skeleton lines. Too large: "Showing the first 2,000 lines · Open all". Error: an inline `KitStateView` with Try again. Empty: "This file is empty".
- **Motion.** The page or sheet transition only.
- **A11y.** Find (`KitSearchField`) and Wrap are labelled. The document reflows at 200 %.
- **RTL.** Code and paths are LTR. Prose follows the locale.
- **Security.** Every link goes through `openExternalLink`.

**Replaces** (gaps merged: `KitDocumentView`, `KitFileViewer`):
<details><summary>11 elements on 8 pages</summary>

`about` (about-document); `about-open-source-tab` (open-source-notices); `about-privacy-tab` (privacy-policy); `embedded-file-preview-body` (embedded-file-preview-body-code); `file-preview-sheet` (file-preview-sheet-body, file-preview-sheet-header); `files-file-viewer-sheet` (files-file-viewer-sheet-body, files-file-viewer-sheet-header); `markdown-code-reader`; `voice-notices` (voice-notices, voice-notices-body).

</details>

### 1.15 KitDiffView: one diff renderer (6)

**Purpose.** Replaces the two renderers (`review_workspace.dart`'s `_UnifiedDiffRow`/`_SplitDiffRow`, and `widgets/diff_view.dart`) and their two hunk navigators. It is used in the review workspace, revert previews, run results and permission previews (the full diff behind a request's Details).

```dart
enum KitDiffMode { unified, split } // split only from 600 dp

class KitDiffView extends StatefulWidget {
  const KitDiffView({
    super.key,
    required this.files,              // List<KitDiffFile>(path, hunks, added, removed)
    this.mode = KitDiffMode.unified,
    this.initialFile,
    this.selectable = false,          // line selection for Comment / Add to prompt
    this.onComment,
    this.onAddToPrompt,
    this.readOnly = true,
  });
}
```

- **Layout.**
  - One file header: a `KitPickerRow` holding "3 files · README.md +4 −1", instead of a chip strip that repeats every name.
  - One navigator: "Change 1 of N ↑ ↓", with 48 dp `KitIconButton`s.
  - Continuation lines are indented.
  - The selection bar is a `KitActionBlock`: Comment as primary, Add to prompt and Copy as tertiary.
- **States.** Loading: skeleton lines. No changes: an inline `KitStateView`. Too big: "Showing 400 of 3,200 lines · Open all".
- **A11y.** Added and removed lines are marked with a + or − glyph and semantics ("line 12 added"), never only green and red. Navigating to a change moves focus there.
- **RTL.** Always LTR.
- **Perf.** Virtualised rows. No layout animation while scrolling.

**Replaces** (gaps merged: `KitDiffView`):
<details><summary>6 elements on 2 pages</summary>

`diff-view` (diff-view-body); `review-workspace` (review-workspace-diff, review-workspace-file-header, review-workspace-file-strip, review-workspace-hunk-nav, review-workspace-selection-bar).

</details>

### 1.16 KitReceipt: what happened to a write (3)

**Purpose.** Every write the phone sends (an answer, message, move, stop, merge, or an automatic action) shows its fate in place. It replaces two classes both named `TeamReceiptChip` with different contracts, the gate sheet's `_Receipt`, the team conversation's sent line, and chat answers that have no receipt at all. It also carries the automation-first vertical's `KitAutoLine`.

```dart
enum KitReceiptState { sending, sent, confirmed, notConfirmed, refused }

class KitReceipt extends StatelessWidget {
  const KitReceipt({
    super.key,
    required this.state,
    this.reason,          // refused: the server's reason, in plain words
    this.at,              // "10:42"
    this.onRetry,         // notConfirmed
    this.onUndo,          // where the server allows it, within the undo window
    this.automatic = false, // "Restarted the phone's server at 10:42 · Undo"
  });

  /// The same as a row's supporting line: "Sent · " / "Not confirmed · Retry".
  static InlineSpan span(BuildContext context, KitReceiptState state, {String? reason});
}
```

- **Look.** A mark and a word, with no chip or card: sending (a small working mark), sent (a check), confirmed (a check in the ok tone and "Done"), not confirmed (attention, with Retry), refused (failure, with the reason).
- **Motion.** The mark crossfades on `KitMotion.quick`.
- **Haptics.** None. The send tick fired when the person sent.
- **A11y.** One live region: each transition is announced once. Retry and Undo are 48 dp tertiary actions.
- **Honest state.** "Sent" is never shown as "Done". Confirmed needs the server's echo. After 8 s without an echo the receipt says "Not confirmed yet" and offers Retry.

**Replaces** (gaps merged: `KitReceipt`):
<details><summary>3 elements on 3 pages</summary>

`chat` (chat-note-receipt-dismiss); `embedded-team-receipt-chip` (receipt); `team-home-needs-you-tab` (receipt).

</details>

### 1.17 KitUndo: the one snackbar (1)

**Purpose.** Done-with-undo, the only job a snackbar has (principle 7). Today the app calls `showSnackBar` 127 times and 6 of those carry an action. Most report a copy (replaced by `KitIconButton.copy`), a failure (belongs in a `KitNotice` or `KitStatusLine`), or an update (belongs in `KitStatusLine`).

```dart
void showKitUndo(
  BuildContext context, {
  required String message,        // names the thing: "Archived 'Fix login'"
  required VoidCallback onUndo,   // runs the inverse; or cancels a deferred commit
  VoidCallback? onCommit,         // deferred commits: runs when the window closes unused
  String? undoLabel,              // default "Undo"
  Key? key,
});
```

- **Behaviour.** One at a time: a new undo commits the previous one. It stays for `KitUndo.window` (8 s), and never auto-dismisses under accessible navigation (the framework keeps action snackbars open when `accessibleNavigation` is on). It floats above the dock, the composer and a pinned primary, using the padding `KitScreen` publishes, so it no longer covers them as the update notices do today.
- **Motion.** The framework's snackbar entrance, timed from `KitMotion.standard`; instant under reduced motion.
- **Haptics.** None.
- **A11y.** Announced politely without taking focus. Undo is a 48 dp action.
- **Data safety.** If the inverse fails, a `KitNotice` failure with Try again appears where the thing was, so nothing is lost silently.

**Replaces:**
<details><summary>1 elements on 1 pages</summary>

`workspace` (workspace-archive-undo).

</details>

Code reach is larger than the map count: it is the destination for every `showSnackBar` call that carries an undo. The others move to `KitIconButton.copy`, `KitNotice` or `KitStatusLine` (gate G1).

### 1.18 KitTopBar: the screen's header (20)

**Purpose.** Design standard §1 describes the top bar (title, back, at most one icon action and overflow), but the kit has no such part. Screens use plain `AppBar`s, brand headers, a shell server switcher, second "← Files" bars and centred titles. `KitTopBar` adds the one-line state subtitle the map asks for on the chat.

```dart
class KitTopBar extends StatelessWidget implements PreferredSizeWidget {
  const KitTopBar({
    super.key,
    required this.title,             // the place, in the person's words, ≤ 4 words
    this.subtitle,                   // one line of state: "Working · 2 min", or the server
    this.subtitleTone,
    this.onTitleTap,                 // a switcher (server, project): adds a chevron and "Switch" semantics
    this.action,                     // at most one KitIconButton
    this.menu = const [],            // List<KitMenuItem> in an overflow
    this.needsYou = 0,               // a KitNeedsYou.badge on the title's switcher
    this.brand = false,              // the logo in place of the title (root pages only)
    this.leading,                    // back or close, inferred from the route
  });
}
```

- **Rules.** A screen inside a tab uses the shell's bar and never adds a second one (the "← Files" bars). Sheets use `KitSheet`'s header.
- **A11y.** The title is a header in semantics. The subtitle's state is part of the title's label ("Fix login, working, 2 minutes"). At 200 % the bar grows instead of cutting the title: it has no fixed toolbar height, and the subtitle drops under the title. The current server's dot is paired with a word.
- **RTL.** Back is mirrored and the actions sit at the end.
- **Honest state.** The subtitle comes from the one status source per entity, never a hand-built string.

**Replaces** (gaps merged: `KitBrandHeader`, `KitProjectHeader`, `KitShellHeader`, `KitTopBar`):
<details><summary>20 elements on 19 pages</summary>

`about` (about-title); `activity` (activity-header); `builtin-server-setup` (title-and-duplicate-heading); `chat` (chat-appbar-title); `demo` (demo-header); `diff-view` (diff-view-header); `files` (files-back-bar); `home-shell` (home-shell-server-switcher); `project-hub` (project-hub-files-back); `prompt-editor` (prompt-editor, prompt-editor-done); `servers` (brand-header); `servers-welcome` (brand-header); `settings` (settings-header); `shell-output` (shell-output-title); `team-board` (team-board-project); `team-conversation` (team-conversation-title); `team-run` (team-run-appbar); `team-run-overview-tab` (overview-objective); `workspace` (workspace-project-header).

</details>

### 1.19 KitJumpPill: back to the newest (5)

**Purpose.** The floating "3 new · Jump to latest" pill on transcripts, team output and logs. It replaces pills with a literal 200 ms `easeOutBack` (not `KitMotion`).

```dart
class KitJumpPill extends StatelessWidget {
  const KitJumpPill({super.key, required this.label, required this.onPressed, required this.visible, this.icon = AppIconography.arrowDown});
}
```

- **Look.** A bounded `KitGlass` surface centred above the composer or the bottom edge. It is 48 dp tall with a label; an arrow alone is never enough.
- **Motion.** Fade and rise on `KitMotion.standard`/`enter`, out on `exit`; instant under reduced motion. It never scales.
- **A11y.** A button labelled with its words. It is not a live region: new lines are not announced one by one.

**Replaces** (gaps merged: `KitJumpPill`):
<details><summary>5 elements on 4 pages</summary>

`chat` (chat-earlier-messages-pill, chat-jump-to-latest); `embedded-message-view` (embedded-message-view-jump-to-latest); `team-agent-output` (team-agent-output-jump); `team-run-timeline-tab` (team-run-timeline-jump).

</details>

### 1.20 KitTerm: a word with an explanation (2)

**Purpose.** An inline term the person may not know ("worktree", "MCP") that explains itself in place. It replaces `embedded-info-label`, a hit area about 24 dp tall (critical X1) that opens a whole page. The owner's verdict was "Kill if not needed or show tool tip?".

```dart
class KitTerm extends StatelessWidget {
  const KitTerm(this.term, {super.key, required this.explanation, this.learnMore, this.style});
  final String term;
  final String explanation;   // ≤ 2 short sentences
  final KitAction? learnMore; // opens the Guide at the right place
}
```

- **Behaviour.** Tapping or long-pressing shows a tooltip bubble anchored to the term. The term keeps a dotted underline and gets a 48 dp hit area without changing the text's line height (semantics padding).
- **Motion.** The tooltip fades on `KitMotion.quick`.
- **A11y.** The term reads "worktree, explanation available", and the explanation is read on activation.
- **RTL.** The bubble mirrors its anchor.

**Replaces** (gaps merged: `KitTerm`):
<details><summary>2 elements on 2 pages</summary>

`embedded-info-label` (embedded-info-label-term); `info-label-sheet`.

</details>

### 1.21 KitNeedsYou: the one "needs you" marker (9)

**Purpose.** One marker, fed by one attention source, counted once, in the same words everywhere (the needs-you-markers pattern). Today the Inbox badge, the Work section count, team rows, server rows and the profile monitor all count on their own. The monitor counts the same requests twice, and a green dot marks a request.

```dart
abstract final class KitNeedsYou {
  /// A row's leading mark: KitTaskMark(needsYou), a question mark in the attention tone.
  static Widget mark();

  /// The start of a row's supporting line, like kitCurrentSpan: "Needs you · ".
  static TextSpan span(BuildContext context, {int count = 1});

  /// A count on a tab, a header's switcher or a server row. Semantics: "3 need you".
  static Widget badge({required int count, required Widget child});
}
```

- **Rules.** The count comes from the attention source (one entry per request across servers, cancelled when it is answered anywhere). Every surface uses these three builders: the tab badge, list rows, server rows, the conversation header and the notification copy. A mark always has its word, never colour alone.
- **A11y.** The badge's count is part of its host's label. A count change is announced once, through the host's live region.

**Replaces:**
<details><summary>9 elements on 5 pages</summary>

`activity` (activity-permission-row, activity-question-row, activity-saved-servers-row, activity-team-gate-row); `chat` (chat-appbar-running-work); `embedded-profile-monitor-inbox` (embedded-profile-monitor-inbox-request-row, embedded-profile-monitor-inbox-summary); `home-shell` (home-shell-badge); `profile-monitor` (profile-monitor-request-row).

</details>

---

## 2. Changes to existing parts

### 2.1 KitRequestCard becomes the one answer card

It becomes the one card for OC1 permissions and questions, OC2 forms, `choices` blocks, AI Team gates and external-task replies (the answer-the-agent pattern). Today there are three card classes (`_PermissionAttentionCard`, `_QuestionAttentionCard`, `_FormRequestCard`), `TeamNeedsYouCard`, a third choice-row implementation in Markdown, and four separate sheets.

```dart
enum KitRequestKind { permission, question, form, choice, gate, reply }
enum KitRequestPhase { waiting, sending, answered, answeredElsewhere, expired }

class KitRequestCard extends StatelessWidget {
  const KitRequestCard({
    super.key,
    required this.kind,
    required this.who,                // "fox · on the laptop": who asks, on which server
    required this.title,              // the ask in one line
    required this.announcement,
    this.phase = KitRequestPhase.waiting,
    this.summary,                     // mono command or path, 2 lines
    this.detail,
    this.answers,                     // the common answer in place:
                                      //   permission: Allow once (primary) / Reject (secondary),
                                      //   single choice: KitChoiceList.single (sends, with Undo),
                                      //   "Something else": KitField with a draft
    this.onDetails,                   // opens showKitRequestSheet: full diff, long form, gate details
    this.receipt,                     // KitReceipt once answered
    this.since,                       // "waiting 4 min" (§2.2 escalation)
    this.tertiary = const [],         // "Always allow" lives in the sheet, not here (risk)
  });
}

/// A KitSheet whose header is the card's header and which owns a
/// RequestRoutes, so answering on another device closes it and leaves the
/// receipt "Answered on the laptop".
Future<void> showKitRequestSheet(BuildContext context, {required KitRequestCard card, required WidgetBuilder body});
```

- **Answered state (new).** The card collapses to a one-line row, "Allowed once · 10:42" (or "Answered on the laptop"), with its `KitReceipt`. It folds away with `KitReveal` after the undo window. `KitHaptics.send` fires on the answer.
- **Honest.** The work line says "Waiting for you" while any request waits, never "Running tools". An expired request says so and offers nothing to press.
- **Risk.** "Always allow" and the server-wide auto-approve move into the sheet as a `KitSwitchRow` with a risk scope (§2.6), never as the card's primary.

### 2.2 KitStateView gains the whenMissing modes, 8 s escalation, and error defaults

The map gates 285 capabilities as `hidden` and 82 as `explains`, but only 18 as `offers-enable`. The discoverability vertical asks that `hidden` be used only where no enable flow exists.

```dart
/// A capability the page needs is missing (map `whenMissing`).
const KitStateView.missing({
  required String capability,           // for tests and the explainer registry: "voice.model"
  required String title,                // "Voice needs a model"
  required String why,                  // one sentence
  KitAction? enable,                    // offers-enable:<flow>: "Download voice model"
  KitStateSize size = KitStateSize.inline,
  String? cost,                         // "About 80 MB" (KitNotice.cost, §2.4)
});

// New on every KitStateView:
final DateTime? since;       // the wait started here; after 8 s the kit changes the body to
                             //   "Still waiting after 8 s" and shows [onSlow] (Retry / Restart / Leave it running)
final KitAction? onSlow;
```

- **Error defaults.** An error-tone state adds **Copy details** and **Report a problem** (prefilled, redacted diagnostics, with a preview) automatically. An error classifier picks Fix instead of Report for network errors (Retry, Switch server). This comes from the help-feedback vertical.
- **Details slot.** It is now `KitDetailsFold` (the same fold everywhere). `details`, `detailNotes` and `detailsChild` map onto it. The fold labels stop borrowing `e7SetupDetails`/`e7SetupHideDetails` and get kit-level keys.
- **The old shared states.** `ProductErrorState`, `ProductEmptyState` and `ProductInlineEmpty` become thin wrappers over `KitStateView` (step B), then are removed along with their re-export from `kit.dart`.

### 2.3 KitStatusLine gains the Now line and the escalation, and replaces banners

- `since` + `onSlow`: the same 8 s escalation as §2.2, with the timer inside the kit.
- `next`: an optional second line for team work, "Working on the login fix · a reviewer checks it next · about 6 min". This is the `KitNowLine` both team pages ask for, and it unfolds in place with `KitReveal`.
- It is fed by one status source per entity. A screen passes the status object, not words, through a `KitStatusLine.of(status)` factory that maps states to words in one place.
- It replaces every `MaterialBanner` (4 files) and the update and release snackbars. The "at most one per screen" rule is enforced by a `KitStatusLineSlot` in `KitScreen` that shows the highest-priority status.

### 2.4 KitNotice gains the cost line

- `KitNotice.cost(List<String> items)`: "About 550 MB memory per worker · uses battery while it works" (the battery-heat vertical's `KitCostLine`). It is required before any install or turn-on primary.
- It absorbs `NudgeCard`, the model picker's `_Notice`, `_FileStatusNotice` and the first-run tips.
- A notice that offers a turn-on ("{server} also runs an AI team. Turn it on?") is a `KitNotice` with one tertiary action and a dismiss, not a `Card`.

### 2.5 KitRow variants

- `KitRow.unavailable(title, reason, {KitAction? enable})`: a dimmed row that says why and offers the enable flow. This is the honest-state and discoverability `KitCapabilityRow`. `hidden` becomes the exception.
- `server:`: a small server label in the supporting line ("laptop · …") on any row, card or notification that comes from another server (multi-server vertical).
- `swipe: KitSwipeAction(...)`: the row's swipe. `SwipeDeleteBackground` moves into the kit. A swipe is only an accelerator: the same act must be in the row's `KitRowMenu` (the gesture's visible alternative), and a swipe always uses Undo, never a confirmation (§4.1).
- Destructive rows sit last in their list, after a divider, and never between frequent ones (`session-menu-sheet` puts Revert between Retry and Fork).
- The kit gains no `ListTile` look-alike. The 193 `ListTile`-family sites (147 plain, plus the switch, radio and checkbox kinds) migrate to `KitRow`, `KitChoiceRow`, `KitSwitchRow` or `KitExpandRow` (gate G2).

### 2.6 KitSwitchRow: risk scope and "always on"

- `risk: KitRisk(scope: 'Every conversation on this server', until: [KitUntil.off, KitUntil.conversation, KitUntil.hour])`: the risky-switch rule (security vertical `KitRiskSwitch`). Turning it on asks for its scope inline, and while it is on, the screen's `KitStatusLine` says so ("Auto-approve is on · Turn off").
- `locked: 'Always included'`: replaces a disabled switch that is on but grey and reads as off (`phone-setup-customize-sheet`).

### 2.7 KitAction, KitActionBlock and KitActionStack

- `KitAction.disabledReason`: when `onPressed` is null, the block shows the reason as one muted line under the button. Design standard §2's "a disabled button needs a visible reason" becomes structural instead of a review item.
- `KitActionBlock` switches to the `KitActionStack` layout by itself when any tertiary action is `destructive`, so a destructive act never sits next to a frequent one (X3).
- It asserts at most one primary per block. A debug-only check in `KitScreen` asserts one visible primary per screen.

### 2.8 KitProgress: stages and estimate

- `KitProgress.staged({required int step, required int of, required String label, Duration? eta})` renders "Step 3 of 5 · Installing · about 2 min left".
- A job that may take over 30 s must use `staged` or `known` with a caption (honest-state vertical). `waiting` shows its 8 s escalation through its host `KitStateView`.

### 2.9 KitStatusMark and KitTaskMark

- `KitStatusMark` reads `KitMotion.reduced(context)` instead of `MediaQuery.disableAnimationsOf`. Today it ignores Animations: Off in Settings, the only kit part that does.
- A new `KitMarkState.paused` (a heat pause, a force stop the app can resume): a muted pause mark.
- `KitTaskMark.needsYou` is what `KitNeedsYou.mark()` returns, so the two cannot drift.

### 2.10 KitTabSwitcher gains its strip

`KitTabSwitcher.tabs({required List<KitTab> tabs, ...})` adds the tab strip the switcher lacks: labels, an optional count, and a needs-you dot paired with the count. It replaces the raw `TabBar`s (usage, about, team run, command launcher, model picker, capabilities) and `TeamBoardTabs`. The body keeps the existing cross-fade and settle.

### 2.11 KitExpandRow

It can now be controlled (`expanded`, `onExpansionChanged`), so every `ExpansionTile` (18 sites) can move to it, including the reasoning toggle and the tool group line in the chat module.

### 2.12 KitScreen

- `topBar: KitTopBar`.
- `search: KitSearchField` (pinned under the bar).
- `status:` the one `KitStatusLine` slot.
- It publishes the bottom padding that `showKitUndo` and `KitJumpPill` use, and it lifts `bottom` above the keyboard.

### 2.13 KitHaptics.commit

A firm tick when the person confirms a `stop`, `destructive` or `discard` act. Like `send`, it obeys Settings › Vibration. It replaces the two raw `HapticFeedback.mediumImpact` calls (`confirm_sheet.dart`, `chat_screen.dart:3749`), which ignore that setting today.

### 2.14 Library hygiene

- `kit.dart`'s doc table lists every part, and a gate keeps it complete (G4).
- `showConfirmSheet` becomes a deprecated wrapper over `showKitConfirm` in step A (so all 37 files get the new look at once), then is deleted once every call site passes a kind.
- `SetupProgressView`, `TeamTechnicalValue`, both `TeamReceiptChip`s, `AgentCommandBlock`, `FilePreviewBody`'s frame, `DiffView`, `QuestionOptionRow`, `InfoLabel`, `ProductRefreshBody`, `NudgeCard` and `TeamComposerField` are absorbed and deleted.

---

## 3. Coverage of the patterns and verticals

| Phase 2 pattern | Kit v2 answer |
|---|---|
| answer-the-agent | `KitRequestCard` v2 + `showKitRequestSheet` + `KitChoiceList` + `KitReceipt` + `KitNeedsYou` |
| confirmations | `KitConfirmSheet` (kinds, typed name, alternative, in-place in a sheet) |
| undo-vs-confirm | §4.1 and `showKitUndo` |
| technical-details | `KitDetailsFold` / `showKitTechnicalDetails` |
| logs | `KitLogPanel` |
| needs-you-markers | `KitNeedsYou` |
| receipts | `KitReceipt` (and the automatic line) |
| status-and-waiting | `KitStatusLine` v2 (`since`, `next`, `.of(status)`) |
| install-progress | `KitChecklist` + `KitProgress.staged` + `KitNotice.cost` |
| viewers | `KitViewer`, `KitDiffView` |
| choice-rows | `KitChoiceList` / `KitChoiceRow` / `KitPickerRow` |
| discard-guard | `KitSheet` `draft` / `dirty` + `KitConfirmKind.discard` |
| error-states | `KitStateView` error defaults; `ProductErrorState` removed |
| short-input | `KitDialog` (`showKitInputDialog`) |

| Vertical | Kit v2 mechanism |
|---|---|
| a11y | `KitIconButton` (required label, 48 dp), `KitTerm`, word + mark in every state part, 200 % goldens (G4) |
| rtl-l10n | LTR isolation inside `KitTechnicalValue`, `KitCodeBlock`, `KitField` mono/path/secret, `KitDiffView`, `KitLogPanel`; directional lint (G7); ar goldens |
| perf | virtualised `KitLogPanel`/`KitDiffView`, capped `KitCodeBlock`, no `AnimatedSize` on lists (G2) |
| battery-heat | `KitNotice.cost`, `KitMarkState.paused` |
| reliability | `KitProgressRow.asOf`, `KitChecklist.resume`, `KitUndo` failure path |
| security-privacy | `KitField.secret`, `KitSwitchRow.risk`, redaction in `KitLogPanel`/`KitDetailsFold`, `launchUrl` gate |
| honest-state | `since` escalation inside the kit, `disabledReason`, `KitStatusLine.of(status)`, `KitRow.unavailable`, `KitStateView.missing` |
| data-safety | `KitDraft`, the `KitSheet` guard, the typed-name confirm, `consequences` |
| automation-first | `KitReceipt(automatic: true)` with Undo |
| help-feedback | the `KitStateView` error defaults (Copy details, Report a problem) |
| discoverability | `KitStateView.missing` + `KitRow.unavailable` with `enable` |
| consistency-kit | this document plus gates G1–G3 |

---

## 4. Rules

### 4.1 Undo, confirm, or neither

The same act gets the same treatment from every entry point: a swipe, a menu and a button all behave alike.

| Treatment | When | Acts (from the map) |
|---|---|---|
| **Act, then Undo** (`showKitUndo`, or `KitReceipt.onUndo` in place) | The act is reversible, or the app holds a copy it can restore | Archive a conversation (swipe *and* menu), restore a saved prompt, delete a local saved prompt, withdraw a queued message to draft, approve (while the host allows withdrawal), a board cancel the host can reopen, move a board item, dismiss a notice that matters |
| **Confirm first** (`showKitConfirm`) | Irreversible, destructive on the server, or losing work that cannot be kept | Delete a conversation, a one-step revert, remove a server (listing its queued prompts), delete the phone's Ubuntu (typed name), remove a worktree or workspace with changes (typed name), stop a task or worker mid-work (`stop`), clear diagnostics or saved drafts, revoke a saved permission, sign out of or disconnect a provider, share a conversation publicly (`neutral`: names who can see it) |
| **Neither** (act, show the result in place) | Harmless, idempotent, or instantly reversible by the same control | Start a service, restart something that loses nothing, switch a setting, pick a choice (the choice itself is the undo), copy, refresh, open, follow output |

- **The test.** "If the person taps it by mistake, what do they lose?" Nothing: *neither*. Something the app can put back: *Undo*. Something nobody can put back: *confirm*.
- **Forbidden.** Never confirm *and* offer Undo for the same act. Never confirm a harmless act: the map found confirmations on restoring a prompt, starting a dev service and starting installed Termux. Never put a destructive act behind a swipe without Undo.

### 4.2 Destructive styling

- The error colour means "this loses data or ends running work". It is never used for restart, cancel-a-pending-send, withdraw-to-draft, or a non-permanent removal (a sign-in that can be redone is `neutral`).
- A destructive action is never primary on a screen. It is primary only inside a `KitConfirmSheet` of kind `destructive`, `stop` or `discard`, where the whole sheet is that act.
- It never sits beside a frequent action (`KitActionBlock` enforces this, §2.7). In rows it sits last, after a divider, in the `KitRowMenu`.
- Its label is a verb naming the thing: "Delete conversation", never "Delete", "OK" or "Yes". Cancel words by kind: "Keep running", "Keep editing", "Cancel".
- Its icon is a trash, stop or edit-off mark in the error tone, never "?".

### 4.3 One details fold

All technical values on a page or sheet go into one `KitDetailsFold`, placed last and collapsed. A value appears once, is copyable, and is LTR. Engine words, ids, paths and ports appear only there. A standalone raw error uses `showKitTechnicalDetails`, never an `AlertDialog`.

### 4.4 One log panel

Every process output is a `KitLogPanel`. It is folded under Details unless the page exists to show that log. There is one panel per source: two sheets for one log is a defect. It follows new lines, polls only while visible, is redacted, and uses the error tone for errors only.

### 4.5 One "needs you" marker

One attention source, counted once per request across servers. It is shown only through `KitNeedsYou`'s mark, span and badge, and always with its word. The same words go on the tab, row, server row, header and notification. A request answered anywhere clears everywhere.

### 4.6 One container per task

A screen is a place. A `KitSheet` is a choice, a short task or a confirmation. A `KitDialog` is a short text entry or a blocking alert. `showKitUndo` is done-with-undo. `KitStatusLine` is a condition on a working screen. `KitNotice` is a message about one part. Nothing else opens modally.

### 4.7 No sheet on a sheet

A confirmation or a choice raised from inside a `KitSheet` replaces the sheet's content in place (a `KitReveal` swap with its own back step). It never stacks a second modal: the map found a confirm on a sheet on a sheet in `gate-sheet-confirm-sheet`, `command-auth-sheet` and `prompt-stash-delete-sheet`.

### 4.8 Feedback in place

A copy shows its check on the button. A write shows its `KitReceipt` where it was made. A failure shows a `KitNotice` on the part that failed. The snackbar is only for Undo.

### 4.9 Honest waiting is in the kit

A part that waits takes `since` and escalates after 8 s by itself: `KitStateView`, `KitStatusLine`, `KitReceipt`, `KitField` validation, `KitChecklist`. A disabled control always has a visible reason (`disabledReason`). A button never shows lasting status.

### 4.10 Technical text and secrets

Every value the app did not write (paths, commands, hosts, ids, code) is rendered by a kit part that isolates it LTR and makes it copyable. Secrets are entered only through `KitField.secret`, never displayed again, never prefilled, and never passed to a copy, log, details or code part.

### 4.11 Motion and haptics

Only the `KitMotion` durations and curves are used, and nothing moves under `KitMotion.reduced`. There is no `AnimatedSize` or layout animation in scrolling lists. Haptics are only `send` (words leave), `done` (a waited-for finish) and `commit` (a confirmed destructive act), all through `KitHaptics`.

### 4.12 Entry rule

A part joins the kit when two or more pages need it. Single-use widgets stay in their module (§5), built from kit parts and tokens, and are checked by the same gates for their raw parts.

---

## 5. Not in the kit (module parts)

These 50 elements stay module widgets, for the reason given, and are still covered by the raw-widget gates:

| Group | Why not kit | Elements |
|---|---|---|
| chat transcript (14) | Single-owner chat library; the team conversation reuses it | `active-context-message` (active-context-message-parts); `chat` (embedded-message-view); `demo` (demo-chat); `embedded-completion-digest-card`; `embedded-markdown-text` (embedded-markdown-text-link, embedded-markdown-text-table); `embedded-message-view` (embedded-message-view-prompt, embedded-message-view-reasoning-toggle, embedded-message-view-tool-group-toggle); `embedded-tool-card` (embedded-tool-card-header, embedded-tool-card-subagent); `embedded-voice-conversation-controls`; `team-agent-output` (team-agent-output-text); `team-conversation` (team-conversation-prompt) |
| chat composer (12) | Single-owner chat library; rebuilt from KitGlass, KitIconButton, KitReceipt | `chat` (chat-pending-photo-row, embedded-composer); `embedded-composer` (embedded-composer-activity, embedded-composer-attachment-chip-preview, embedded-composer-context-badge, embedded-composer-field, embedded-composer-inline-command-row, embedded-composer-model-chip, embedded-composer-model-cycle-menu, embedded-composer-send, embedded-composer-stop); `embedded-pending-sends-strip` (pending-bubble) |
| special surface (10) | One-off surfaces (terminal, camera, graph, rail, meter, stock pickers) | `embedded-work-graph` (work-graph); `form-sheet-date-picker`; `home-shell` (home-shell-rail); `notifications-settings-quiet-time-dialog` (quiet-time-picker); `pairing-scanner` (pairing-scanner-camera); `session-context` (session-context-makeup); `terminal-surface` (terminal-surface-keys, terminal-surface-view); `termux-setup-connect-termux` (guide-illustrations); `voice-composer-sheet` (voice-composer-sheet-meter) |
| platform (no UI) (7) | Shortcuts, intents and routing: no visible part | `chat` (chat-shortcuts); `embedded-desktop-file-drop-target` (embedded-desktop-file-drop-target-drop); `embedded-model-shortcuts`; `global-shortcuts` (global-shortcuts-layer); `system` (launcher-shortcuts, notification-routing, share-intake) |
| appearance (2) | Used once (theme swatches, preview) | `appearance-settings` (theme-pack-grid); `theme-pack-preview-sheet` (theme-preview-card) |
| team board (2) | Used once (paged lanes, task card) | `team-board` (team-board-card, team-board-pages) |
| files (1) | Used once (breadcrumb) | `files` (files-breadcrumb) |
| handoff (1) | Used once (QR code) | `continue-on-phone-sheet` (continue-on-phone-qr) |
| team conversation (1) | Used once (agent strip) | `team-conversation` (team-conversation-family) |

- **Chat transcript and composer.** The proposed `KitTurn`, `KitComposer`, `KitWorkLine`, `KitToolRow` and `KitQueuedMessage` belong to the chat library, which has a single owner (AGENTS.md). The team conversation should reuse the chat's own composer and message view rather than get a kit copy. They are rebuilt from kit parts: the tool group line is a `KitExpandRow`, pending-send actions use `KitIconButton`, the composer surface is `KitGlass`, and queued messages carry a `KitReceipt`. The turn model in `docs/design/` governs them.
- **One-off surfaces.** The terminal's xterm view and its key strip (the key strip should move to the kit's existing `TerminalKeyBar`, per the map), the QR scanner camera, the work graph, the navigation rail, the voice level meter, stock date and time pickers (allowed by G1), the team board's paged lanes and task card, theme swatches and preview, the handoff QR, and the files breadcrumb.

---

## 6. Migration order

Each step's score is the count it replaces × its user impact (3 = critical findings: data loss, destructive, secret, a11y blockers; 2 = high: honest state, input and forms, technical truth; 1 = craft). Rows and buttons (the sweep) are not a separate project: every step migrates the other raw rows and buttons on each page it touches, and step G mops up the rest.

| Step | Parts | Elements | Impact | Score | Pages | Why at this point |
|---|---|---|---|---|---|---|
| **A. One sheet, one confirm** | `KitSheet`, `KitConfirmSheet` | 168 | 3 | 504 | 126 | Critical X2/X3: lost drafts, silent deletes, destructive styling on the wrong acts. `showConfirmSheet` becomes a wrapper first, so 37 files change look in one edit. |
| **B. Honest states and technical truth** | `KitStateView` (v2), `KitStatusLine` (v2), `KitNotice` (v2), `KitDetailsFold`, `KitCodeBlock`, `KitIconButton`, `KitLogPanel`, `KitLoadingBar`, `KitSkeletonRows`, `KitProgress` (v2) | 165 | 2 | 330 | 116 | Honest-state, help-feedback and principle 4; KitStateView v2 needs KitDetailsFold; the copy button retires ~40 snackbars. |
| **C. Input and choice** | `KitField`, `KitDialog`, `KitSearchField`, `KitSegmented`, `KitChoiceList`, `KitTabSwitcher` (v2) | 131 | 2 | 262 | 98 | Contains two critical findings (bearer tokens in clear, a truncated instruction); needs KitSheet from A. |
| **D. Answer the agent** | `KitRequestCard` (v2), `KitReceipt`, `KitNeedsYou`, `KitUndo` | 15 | 3 | 45 | 10 | The top job for remote-lead and team-delegator; built on A (request sheets) and C (choices, fields). Pulled ahead of E-F, see notes. |
| **E. Setup and progress** | `KitChecklist`, `KitProgressRow` | 24 | 2 | 48 | 22 | Install journey; promotes SetupProgressView; needs KitLogPanel (B). |
| **F. Viewers and chrome** | `KitViewer`, `KitDiffView`, `KitTopBar`, `KitJumpPill`, `KitTerm` | 44 | 1 | 44 | 32 | Review and inspect journeys; KitTerm fixes a critical 24 dp target. |
| **G. Row and action sweep (also rides along with every step)** | `KitRow` (v2), `KitActionBlock`, `KitSwitchRow` (v2), `KitRowMenu`, `SectionLabel`, `KitPanel` | 220 | 1 | 220 | 118 | No new part needed; each step migrates these on the pages it touches, G mops up the rest and empties the G2 baseline. |

Notes:

- **Step D is pulled forward** past E and F. Its map count understates its reach: the request card family covers 9 existing `KitRequestCard` uses, the 4 request sheets reframed in step A, and every chat, Inbox and team answer. It depends on A (the sheet) and C (fields and choices), so it cannot come earlier.
- **Single ownership (AGENTS.md).** A kit step's worker owns `lib/ui/kit/` for that step. Chat pages (`chat_screen.dart` and every `chat/*.dart`) migrate in one worktree per step. Protocol clusters are untouched: this is UI only.
- **Each step ships with** its kit part galleries (G4), the pages it migrated joining `design_standard_test`'s migrated list, and those pages' goldens re-rendered and reviewed.

---

## 7. Gates that keep it

| # | Gate | Where | What it checks |
|---|---|---|---|
| G1 | Modal and toast entry points live in the kit | `test/kit_ratchet_test.dart` (new) | Outside `lib/ui/kit/`: `showDialog(`, `showModalBottomSheet(`, `showGeneralDialog(`, `AlertDialog(`, `SimpleDialog(`, `DraggableScrollableSheet(`, `showSnackBar(`, `SnackBar(`, `MaterialBanner(`, and (after step A) `showConfirmSheet(`. Per-file baseline that only shrinks; new files start at zero. `showDatePicker`/`showTimePicker` allowed. |
| G2 | Raw widgets and bypasses ratchet | `test/kit_ratchet_test.dart` | `ListTile`, `ExpansionTile`, `SwitchListTile`, `RadioListTile`, `CheckboxListTile`, `SegmentedButton`, `ChoiceChip`, `FilterChip`, `ActionChip`, `DropdownButton`/`DropdownButtonFormField`, `TabBar(`, `TextField(`/`TextFormField(`, `IconButton(`, `PopupMenuButton`, `Clipboard.setData` (only `KitIconButton.copy`), `HapticFeedback.` (only `KitHaptics`), `launchUrl` (only `external_link.dart` and launchers passed into `openExternalLink`, allowlisted with reasons), literal `Duration(milliseconds:` and `AnimatedSize` in `lib/ui` outside the kit. |
| G3 | Migrated pages are clean, not ratcheted | `test/design_standard_test.dart` | `_forbidden` gains every G1/G2 pattern for files in `_migrated` (zero allowed), and each kit v2 step adds its pages to `_migrated` with dark and light goldens at 412x915. |
| G4 | Every kit part has a gallery | `test/goldens/kit/*_golden_test.dart` + a manifest test | For every new or changed part: each state (default, loading/working, empty, error, disabled, answered where it exists) x light and dark, plus a 200 % text variant (`TextScaler.linear(2)`) and an Arabic RTL variant at 1.3x. The manifest test reads `kit.dart`'s exports and fails when a public widget has no gallery or no row in the doc table. |
| G5 | Accessibility guidelines on every gallery | `test/accessibility_guidelines_test.dart` (extended) | `androidTapTargetGuideline`, `labeledTapTargetGuideline` and `textContrastGuideline` pass on every G4 gallery in both themes. |
| G6 | No overflow at any size | `test/text_scale_overflow_test.dart` (extended) | Each part pumped at 320 dp and 412 dp wide x text scale 1.0 / 1.3 / 2.0 x LTR / RTL: no RenderFlex overflow or clipped-text exception; KitSegmented and KitAskLine-style parts must reach their stacked layouts. |
| G7 | Directional layout | `test/kit_ratchet_test.dart` | In `lib/ui/kit/` (zero) and `lib/ui/` (ratchet): no `EdgeInsets.only(left:`/`right:`, asymmetric `EdgeInsets.fromLTRB`, `Alignment.centerLeft`/`centerRight`, `TextAlign.left`/`right`, `Positioned(left:`/`right:`. |
| G8 | Still under reduced motion | `test/kit_motion_test.dart` (new) | Every part under `MediaQuery(disableAnimations: true)` and under Effects Animations: Off settles after one `pump()` with no running ticker; KitStatusMark shows its still dot under Effects Off (fails today). |
| G9 | Behaviour contracts per part | `test/kit/*_test.dart` (new) | KitConfirmSheet: false on back, swipe and cancel; typed name enables confirm only on exact match; commit haptic only for stop/destructive/discard and never with Vibration off; confirm raised from a KitSheet adds no route. KitUndo: one at a time, persists under accessibleNavigation, failure shows a notice. KitRequestCard: answered state announced once; answering elsewhere closes its sheet via RequestRoutes and leaves the receipt. KitStateView/KitStatusLine/KitReceipt: escalate after 8 s with fake async. KitIconButton.copy: clipboard set, "Copied" announced, no SnackBar. KitLogPanel: follows unless scrolled up; no polling off screen. KitChoiceList.single: one tap, one callback. KitField.secret: asserts empty controller, obscured, no suggestions. |
| G10 | Drafts survive and are swept | `test/kit/kit_draft_test.dart` + `test/profile_store_*` | Every showKitSheet with a KitDraft: type, swipe down, reopen, text kept; back and force-restart keep it; keys are `oc.draft.<target>.<profileId>` and ProfileStore.profileScopedPreferenceKeys removes them on profile deletion (mock the secure-storage channel per AGENTS.md). |
| G11 | Copy rules | `test/ui_glossary_test.dart` (extended) | showKitConfirm titles end with "?"; confirm labels are never OK, Yes, Continue or Confirm; cancel labels match the kind; contradiction pairs (Working + stopped, Paused + idle, Connected + reconnecting) never co-occur on a golden fixture; engine words only inside KitDetailsFold/KitLogPanel/KitCodeBlock. |
| G12 | Redaction through every technical part | `test/redaction_test.dart` (new) | Fake provider keys and bearer tokens fed through KitLogPanel, KitDetailsFold, KitCodeBlock, KitIconButton.copy, diagnostics and the report preview never appear in rendered text, clipboard or test output. |
| G13 | Map stays true | docs check (`tool/ux/` script, no Flutter) | Every kit-v2.json `replaces` entry exists in `map/all.json`; when a page is re-mapped after a step, its elements' `kit` names the v2 part and the count of `kit: "none"` only goes down; `whenMissing: hidden` is allowed only where the capability has no enable flow. |

Ratchet mechanics (G1, G2, G7): a committed `test/kit_ratchet_baseline.json` maps each file to its current count per pattern. The test fails when a count rises, or when a file not in the baseline uses a pattern. When a count drops, the test prints the new baseline to commit, so the numbers only go down. Allowlisted files carry a reason of more than 10 characters, as `_allowed` does today. After the last step the baseline is empty, and the patterns join `design_standard_test`'s `_forbidden` for every file.

---

## Appendix: method

- **Source.** `docs/ux-system/map/all.json` (356 pages). An element is counted when `kit == "none"` or it has a `kitGap`.
- **Assignment.** A `kitGap` name maps to its consolidated part. Elements without a proposal map by `kind` to the existing part that fits. 218 elements are assigned individually where the kind default is wrong: a confirm sheet's buttons go with the sheet, dropdowns go to `KitPickerRow`, sheet headers go to `KitSheet`. Every element's target is recorded in `kit-v2.json` › `assignment` (`"<pageId>#<elementId>": "<target>"`), so the counts can be re-derived when the map changes.
- **Code counts** (2026-09-26, `lib/` outside `lib/ui/kit/`): `showModalBottomSheet` 80 in 53 files, `showDialog` 46 in 31, `AlertDialog` 46 in 32, `showConfirmSheet` 70 in 37, `showSnackBar` 127 (6 with an action), `ListTile` family 193 in 72 (147 plain), `ExpansionTile` 18, `SwitchListTile` 24, `TextField`/`TextFormField` 92, `IconButton(` 152, `PopupMenuButton` 23, `Clipboard.setData` 47, `Duration(milliseconds` 51 in `lib/ui`, `AnimatedSize` 8 in `lib/ui`, raw `HapticFeedback` 2, `launchUrl` 4 outside `external_link.dart` (all app-authored, or the launcher passed into `openExternalLink`).

---

## 8. Every screen size and input (owner, 2026-09-26)

The owner approved P9 with this note: "Ideally I would build this first and it should support tablet, PC, phones etc." The kit is therefore built first, and every part in §1–§2 is adaptive from its first commit. A later retrofit is not allowed. Today only the shell adapts: `home_screen.dart` shows a `NavigationRail` from 760 dp and extends it from 1040 dp. Desktop input already has one seam, `desktopInteractions` in `lib/ui/desktop/desktop_interaction.dart`, which delegates to `platformCapabilities.isDesktop`. The kit has no breakpoints of its own.

### 8.1 Window classes: one answer, in the kit

```dart
/// Material 3 window size classes, measured on the window, never the device.
enum KitWindow { compact, medium, expanded, large }
// compact < 600 dp · medium 600–839 · expanded 840–1199 · large ≥ 1200

class KitLayout {
  static KitWindow windowOf(BuildContext context);      // MediaQuery.sizeOf(context).width
  static bool finePointer(BuildContext context);        // desktopInteractions, or a mouse seen by MouseTracker
  static double readingWidth = 720;                     // forms, settings, sheets' content
  static double listWidth = 960;                        // lists on their own
  static double paneListWidth = 360;                    // the list pane in two panes
}
```

- A part reads only `KitLayout`. Nothing in `lib/ui/` compares a width to a literal. The ratchet (G2) adds `MediaQuery.sizeOf(context).width <` and `constraints.maxWidth <` literals outside the kit.
- The shell's rail moves onto the same classes: a rail from `medium`, extended from `expanded`. This happens in P9.2, not in step A.
- Phones in landscape are usually `medium` by width but short. A part that stacks vertically also checks the height (below 480 dp it keeps the compact layout).

### 8.2 How each part adapts

| Part | compact (phone) | medium (small tablet, phone landscape) | expanded / large (tablet landscape, PC, web) |
|---|---|---|---|
| `KitScreen` | full width, 16 dp gutter | content centred at `readingWidth` or `listWidth` | the same, or `KitScreen.twoPane(list:, detail:)`: the list at `paneListWidth`, the detail in the rest; selecting a row fills the detail instead of pushing a route |
| `showKitSheet` | a bottom sheet (§1.1) | a bottom sheet, capped at 640 dp wide and centred | a dialog panel of up to 560 dp; `KitSheetHeight.full` becomes an end-side sheet of 400–480 dp. Esc closes it and obeys `draft`/`dirty` as swipe-down does |
| `showKitConfirm` | a bottom sheet (§1.2) | the same, capped at 560 dp | a centred dialog of 480 dp. Esc cancels. Enter confirms only for `neutral`; `destructive`, `stop` and `discard` need a click or Tab to the button |
| `showKitDialog` / `showKitAlert` | a dialog | a dialog | a dialog, 480 dp, with Enter as the primary for input |
| `KitUndo` | above the dock, full width minus the gutters | 480 dp, bottom-start | 480 dp, bottom-start, clear of the rail |
| `KitTopBar` | title and at most 2 icons; the rest go in the menu | up to 3 icons | actions with labels (`KitAction` text) instead of an overflow menu, where room allows |
| `KitRow` | 48 dp or more, tap | the same | a hover highlight, a focus ring, right-click or long-press opens `KitRowMenu`, and Enter activates |
| `KitIconButton` | 48 dp target, a semantic label | the same | a tooltip on hover with the label and the shortcut, if there is one |
| `KitChoiceList` / `KitSegmented` | stacked from 2.0 text | the same | arrow keys move within the group, and Space selects |
| `KitLogPanel` / `KitCodeBlock` / `KitViewer` / `KitDiffView` | wraps; horizontal scroll inside its own box | the same | mouse text selection; diffs side by side from `expanded` |
| `KitRequestCard` | in the transcript | the same | the same, plus shortcuts once it has focus: A for allow, D for deny, 1–9 for choices |
| `KitStatusLine` / `KitNeedsYou` | under the header | the same | the same; also a count badge on the rail destination |

### 8.3 Input rules

- **Touch targets stay 48 dp everywhere.** Desktop density is not a reason to shrink them. A fine pointer adds hover, never smaller targets.
- **Everything works from the keyboard on a PC:** Tab order follows the reading order, the focus ring is always visible (`FocusableActionDetector` with kit tokens), and Esc closes the top modal. The existing global shortcuts layer is kept and documented in its help sheet.
- **Hover never carries the only copy of information.** Tooltips repeat a label that the semantics already carry.
- **Right-click and long-press open the same `KitRowMenu`.**
- **Scrollbars** follow `desktop_interaction.dart` (always visible on desktop where a controller exists).

### 8.4 Gates added to §7

- **G4 galleries** render each part at 360×800 (phone), 412×915 (the existing census size), 800×1280 (tablet portrait), 1280×800 (tablet landscape or PC) and 1600×1000 (large PC), in light and dark. Text scale 2.0 and Arabic RTL are rendered at 412 and 1280.
- **G6 overflow** adds widths 600, 840 and 1280 dp, and a landscape phone of 915×412.
- **G14 (new): keyboard and pointer.** For each modal part: Esc closes it, and obeys the draft and dirty rules; Enter confirms only where §8.2 says so; Tab reaches every action; hover shows the tooltip on a `KitIconButton`; right-click on a `KitRow` opens its menu. These run with `debugPlatformCapabilities` set to desktop.
- **G15 (new): no width literals.** Part of the G2 ratchet, as in §8.1.

### 8.5 Order within P9

Step A (P9.1) ships with `KitLayout`, and the sheet and confirmation adapt from the start. P9.2 moves the shell's rail and the Work/Inbox/Settings lists onto `KitScreen.twoPane`. Every later step's part meets §8.2 in the same commit as the part itself.

---

## 9. Kit only (owner rule, 2026-09-26)

> "No UI component to be used should remain outside our kit. All is coming from our library only."

This rule overrides the entry rule in "How the kit grows" and all of §5. No UI component lives outside `lib/ui/kit/`. A screen or module widget outside the kit only arranges kit parts, so a part used once is still a kit part. §5's module parts move into the kit (§9.2). Shortcuts, intents and routing draw nothing, so they can stay where they are.

### 9.1 What a file outside the kit may construct

The gate is an allowlist of Flutter framework widgets (G16). App-defined widgets are allowed if they are built from kit parts.

| Allowed outside `lib/ui/kit/` | Widgets |
|---|---|
| Layout | `Column`, `Row`, `Flex`, `Expanded`, `Flexible`, `Spacer`, `Padding`, `SizedBox`, `Center`, `Align`, `Stack`, `PositionedDirectional`, `Wrap`, `ConstrainedBox`, `LimitedBox`, `AspectRatio`, `FittedBox`, `SafeArea`, `Offstage`, `Visibility`, `KeyedSubtree`, `RepaintBoundary`, `IgnorePointer`, `AbsorbPointer` |
| Scrolling | `ListView`, `CustomScrollView`, `SliverList`, `SliverToBoxAdapter`, `SliverPadding`, `SliverFillRemaining`, `NotificationListener` (it only hears notifications; P9.10) |
| Builders | `Builder`, `StatefulBuilder`, `LayoutBuilder`, `ValueListenableBuilder`, `ListenableBuilder`, `AnimatedBuilder` (with `KitMotion`), `StreamBuilder`, `FutureBuilder` |
| Semantics, focus and input plumbing | `Semantics`, `MergeSemantics`, `ExcludeSemantics`, `Focus`, `FocusScope`, `FocusTraversalGroup`, `Shortcuts`, `Actions`, `CallbackShortcuts`, `PopScope`, `Hero` |
| Routes | none: `KitPageRoute`, `pushKitPage` and `replaceWithKitPage` (KIT-7); `MaterialPageRoute` and `PageRouteBuilder` left the allowlist |

Everything else that draws or takes input comes from the kit. That includes `Text`, `RichText`, `Icon`, `Image`, every button, the `ListTile` family, `TextField`, `Card`, `Container`, `DecoratedBox`, `Material`, `InkWell`, `GestureDetector`, the chips, `Divider`, progress indicators, `Tooltip`, `Scaffold`, `AppBar`, dialogs, sheets, snackbars, menus, `Switch`, `Checkbox`, `Radio`, and `Theme` or `DefaultTextStyle` overrides.

Counted on 2026-09-26 in `lib/ui` outside the kit (224 files):

| Widget | Uses | Widget | Uses | Widget | Uses |
|---|---|---|---|---|---|
| `Text(` | 2,539 | `SnackBar(` | 213 | `Divider(` | 72 |
| `Icon(` | 762 | `ListTile(` | 167 | `Chip(` | 70 |
| `TextButton` | 259 | `IconButton(` | 152 | `CircularProgressIndicator` | 64 |
| `FilledButton` | 138 | `Container(` | 137 | `InkWell(` | 47 |
| `Scaffold(` | 98 | `AppBar(` | 95 | `AlertDialog(` | 44 |
| `Card(` | 83 | `TextField(` | 81 | | |

### 9.2 New kit parts this adds (to §1)

| Part | Replaces | Notes |
|---|---|---|
| `KitText` (roles: title, heading, body, label, caption, mono, number) | `Text`, `RichText`, `DefaultTextStyle` | Roles map to the design standard's type scale. Numbers get tabular figures, and mono text is isolated LTR. |
| `KitIcon` (a named kit icon set, sizes s/m/l, tone) | `Icon` | One icon vocabulary. A decorative icon is excluded from semantics by default. |
| `KitSurface` (levels: plain, raised, tonal, glass; radius tokens) | `Container`, `Card`, `DecoratedBox`, `Material` | `KitPanel` and `KitGlass` become variants of it. |
| `KitTappable` | `InkWell`, `GestureDetector` | 48 dp minimum, focus ring, hover, and the same `KitRowMenu` on right-click or long-press. |
| `KitDivider` | `Divider` | |
| `KitScaffold` | `Scaffold`, `AppBar` | `KitScreen` with `KitTopBar`, adaptive per §8. |
| `KitPageRoute` | `MaterialPageRoute` | Uses `KitPageTransitions`; the allowlist entry for routes is then dropped. |
| `KitDateTimePicker` | `showDatePicker`, `showTimePicker` | Replaces the stock allowance in G1. |
| Chat parts (`lib/ui/kit/chat/`): `KitTurn`, `KitMessage`, `KitMarkdown`, `KitToolRow`, `KitWorkLine`, `KitComposer`, `KitQueuedMessage`, `KitAgentStrip` | the §5 transcript and composer groups | The chat library and the team conversation both arrange these. When a step edits `lib/ui/kit/chat/`, that step's worker owns both it and the chat library. |
| Surfaces: `KitTerminalView` (with `TerminalKeyBar`), `KitScanner`, `KitWorkGraph`, `KitNavRail`, `KitLevelMeter`, `KitBoardLane`, `KitTaskCard`, `KitSwatch`, `KitQr`, `KitBreadcrumb` | the §5 one-off surfaces | Each gets a gallery at the §8.4 sizes like any other part. |

### 9.3 Gate G16: kit only

- `test/kit_ratchet_test.dart` resolves constructor calls in `lib/ui/**` outside `lib/ui/kit/` against the framework's widget class names, minus the §9.1 allowlist.
- It kept a per-file, per-widget baseline in `test/kit_ratchet_baseline.json` that could only shrink while screens moved to the kit.
- The baseline emptied in slice-P9.10 (2026-09-28) and the gate is now absolute, together with G1 (modal and toast entry points) and G15 (width literals): any framework widget outside the allowlist fails, with no per-file allowance and no baseline to regenerate. The failure names the kit part to use instead and, when none fits, how to add one to `lib/ui/kit/` (manifest, spec, test, gallery).
- **Done** for P9 means the G16 baseline is empty and every kit part has its gallery.
