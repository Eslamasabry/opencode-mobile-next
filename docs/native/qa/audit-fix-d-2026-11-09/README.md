# Audit fix D: status pills, selected slabs, notice ring, empty slots, menu glyphs (2026-11-09)

Fixes five classes from the [2026-11-09 audit](../audit-2026-11-09/README.md), at the kit part that draws each, so every
screen follows. Branch `native/audit-fix-d`. Renders: the audit's desktop capture harness (`AuditDShotTest`, runs only
with `OC_LOOKS_DIR` set), 412 x 915 dp. *Before* is `native/next` at `1f66ce4a2`; *after* is this branch. One scene per image.

| Class | Fix (kit part) | Guard |
|---|---|---|
| C03 status as a tinted pill | `KitChip` with no action, `KitStatusTag` and `KitStatusPill` are a small dot (only where the tone carries meaning) and a plain word, no fill | `AuditClassGuardDTest` C03 tests sample the strip inside the old capsule: it must be the page colour |
| C04 selected is a slab | choice rows, rows, choice cards and option cards: 6 percent accent wash at most plus a bare accent check at the trailing edge; the radio ring, check disc and accent ring are gone | C04 test: wash at most 8 percent, one `kit-choice-tick` for a single choice, check on a chosen `KitRow` |
| C11 empty ring as notice glyph | neutral status glyph (`KitStatusLine`, `KitStatusIcon`) and the state view's fallback use the info glyph | C11 test + a scan: no `KitIcons.radioEmpty` in kit `commonMain` |
| C14 empty leading slot | `KitProviderLogo` gives its slot back when the source has no mark | C14 test: logo width is 0 |
| C17 menus lose their icon column | `KitMenu` shows glyphs on every item or on none | C17 test: 0 glyphs for a half-iconed menu, 3 for a full one |

## Renders

| Scene | Before | After |
|---|---|---|
| C03 status, light | [before](c03-status-solid-light-before.png) | [after](c03-status-solid-light-after.png) |
| C03 status, dark | [before](c03-status-solid-dark-before.png) | [after](c03-status-solid-dark-after.png) |
| C03 status, Arabic | [before](c03-status-solid-dark-ar-before.png) | [after](c03-status-solid-dark-ar-after.png) |
| C04 selected, light | [before](c04-selected-solid-light-before.png) | [after](c04-selected-solid-light-after.png) |
| C04 selected, dark | [before](c04-selected-solid-dark-before.png) | [after](c04-selected-solid-dark-after.png) |
| C04 selected, Arabic | [before](c04-selected-solid-dark-ar-before.png) | [after](c04-selected-solid-dark-ar-after.png) |

C11: `KitNotice` already drew the info glyph on this base; the ring remained in `KitStatusLine`, `KitStatusIcon` and the
empty state view, covered by the scan. C14 and C17 render in popups or need a logo source, so they have guards only.

## Not done
- The composer's Stop chip, a tappable action, keeps its pill. The Usage tab switcher and theme swatch cards keep
  their selected tab or ring (a tab strip has no trailing edge).
- `KitToolRow`'s pending-step ring (kit-chat) is a step marker, left as is.
- Glass style: only the shared parts changed; its chosen face still draws its own accent tint and rim.
- Pixel goldens of the changed kit parts are re-recorded by the goldens lane (see the commit after this one).
