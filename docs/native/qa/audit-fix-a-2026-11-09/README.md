# Audit fix A: icon plates, doubled validation, green deletes (2026-11-09)

Fixes three classes from the [2026-11-09 native visual audit](../audit-2026-11-09/README.md) in the kit, with a guard
per class. Branch `native/audit-fix-a`.

**Renders:** desktop capture tests (the audit's `AuditShot` harness, Solid light, 412 × 915 dp, density 2.625), run
on the build VPS. *Before* is `native/next` at `f6ca78138`. *After* is `a02ae4b6c`, plus the glyph follow-ups
`f2621e669` and `6eaf14f79` for the restart and disconnect screens. One screen per image.

## C01: icons on a plate

**Fix:**
- These parts now draw their glyph bare, with no tile, box, circle or wash behind it, at the same glyph sizes:
  - `KitConfirm`'s header mark;
  - `KitSheet`'s header icon and its discard question;
  - `KitAlert`'s icon;
  - `KitStateView`, `KitPageState`, `KitInlineState` (both layouts), `KitInlineStateView` and `KitStartState`.
- `IconTile` is gone. Its replacements are `HeaderMark` and `HeaderMarkSmall`, both tagged `kit-header-mark`.

| Screen | Before | After |
|---|---|---|
| Trust this certificate (sheet header) | [before](c01-trust-certificate-sheet-before.png) | [after](c01-trust-certificate-sheet-after.png) |
| Usage › Limits, empty (state view) | [before](c01-usage-limits-empty-before.png) | [after](c01-usage-limits-empty-after.png) |
| Open project, folder can't be shown (error state) | [before](c01-open-project-folder-error-before.png) | [after](c01-open-project-folder-error-after.png) |

**Guard:** `AuditClassGuardTest` (ui/kit).
- It draws the following, then requires the 6 dp strips on each side of the glyph to be one flat colour:
  - every `KitConfirmKind` and both sheet tones;
  - an alert;
  - every `KitStateView` kind, at page and inline size;
  - the page, inline and start states.
- A source scan fails if `IconTile(` or `KitSurfaceTile(` appears in the kit's `modal/` or `state/` parts.

## C06: the same text twice

**Fix:**
- `KitInputDialog` says its validation line once, under the field.
- The line appears only after the person has typed or tried to submit.
- The confirm stays tappable and checks the text again. `KitInputOperationDialog` and `KitInputFormDialog` already
  worked this way.

| Screen | Before | After |
|---|---|---|
| Send back with a note | [before](c06-send-back-with-a-note-before.png) | [after](c06-send-back-with-a-note-after.png) |
| Rename terminal | [before](c06-rename-terminal-before.png) | [after](c06-rename-terminal-after.png) |
| New named worktree | [before](c06-new-worktree-name-before.png) | [after](c06-new-worktree-name-after.png) |

**Guard:**
- `AuditClassGuardTest` opens `KitInputDialog`, `KitInputOperationDialog` and `KitInputFormDialog`. Before anything
  is typed, the reason shows zero times; after an invalid submit, it shows exactly once.
- `Kita2InputReviewTest` (KITA2-7) also checks that no `kit-action-reason` line appears under the button.

## C10: delete confirms drawn as the green primary

**Fix:**
- `KitConfirmSpec.kind` has no default any more, so the compiler makes every confirm state its kind.
- All call sites now pass one: about 60 had none.
- Destructive acts now use the kit's destructive style, the strong neutral fill:
  - Delete schedule;
  - MCP remove and disconnect, Forget sign-in;
  - Server disconnect;
  - Decline a form;
  - local data deletes.
- Some acts get a kind closer to what they do:
  - reload over a draft is Discard;
  - restart while a reply runs and the chat's server restart are Stop;
  - insecure links (web search, providers, terminal links) are Destructive, as the other insecure links already were.
- Disconnect and decline confirms show the unlink and close glyphs, and restarts show the restart glyph, instead of
  the delete bin or the stop square.
- Linux backup restore stays Neutral: nothing is lost, because projects stay and replaced settings files are kept
  aside.

| Screen | Before | After |
|---|---|---|
| Delete schedule | [before](c10-delete-schedule-before.png) | [after](c10-delete-schedule-after.png) |
| This phone › Restart the server, while a reply runs | [before](c10-restart-during-a-reply-before.png) | [after](c10-restart-during-a-reply-after.png) |
| Server settings › Disconnect | [before](c10-disconnect-server-before.png) | [after](c10-disconnect-server-after.png) |

**Guard:**
- The required `kind` is checked at compile time.
- `AuditClassGuardTest` scans `ui/`, `feature/`, `androidApp/` and `desktopApp/`. It fails on any `KitConfirmSpec`
  whose title or confirm label names a destructive verb when the kind cannot be Destructive, Stop or Discard. The verbs
  are: delete, remove, clear, discard, forget, revoke, reset, disconnect, sign out, uninstall, erase, wipe, stop,
  undo, revert and decline.
- The scan skips comments. A planted sample proves it catches a Neutral delete.

## Checks

On the build VPS (JDK 21):
- `:desktopApp:compileKotlin`, `:ui:kit-content:compileKotlinDesktop` and `:ui:gate:compileTestKotlinDesktop` compile.
- `:ui:kit:desktopTest` passes for `AuditClassGuardTest`, `ConfirmLayoutTest` and `Kita2InputReviewTest`.

Known noise, listed in `known_failures.txt`:
- `KitConfirmSheetGateTest`, the frozen Flutter picture gate, now differs by design: the tile is gone.
- `E20S09StateActionsTest` was already noise.

## Left for others

- **C11 (audit-fix-b):** `KitStateView`'s empty fallback glyph is still the ring. Without its plate, it reads even
  more like an unchecked radio (see the Usage › Limits *after*).
- **Not in scope:** row leading tiles (`KitRowLeading.Glyph(tile = true)`) and the composer tray circles (glass-panes).
