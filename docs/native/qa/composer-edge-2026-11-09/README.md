# Chat chrome edges, 2026-11-09

Branch `native/composer-edge`. Owner reports on native/next bc46d2aea (phone):

1. Glass with liquid glass, dark, scrolled up: "a rough line just below the Jump to latest".
2. "Same on top also with that dark gradient below the phone notifications bar."
3. Solid, dark, live background: the composer area sat on an opaque near-black slab whose top cut across the background;
   the header did the same under the status bar.
4. The header's line read "new · This phone ·   Git".

## Cause

- (1) The "Jump to latest" fade (`TranscriptList`, E20-S30) was a 28 dp band that ended at its strongest (72 % of the
  ground) exactly where the composer's room starts. It was made to sit on the composer's veil. When the Glass work
  removed every band behind the chips and message box (owner 2026-11-08), nothing lay under the fade's bottom any more,
  so the transcript came back at full brightness in one pixel row.
- (2) Under liquid glass the bar drew `kitLiquidEdgeFade(top = true)`: 96 % ground at the bar's top edge, inside the
  window insets, so it started hard right under the status bar and faded down across the bar.
- (3) In the other styles the chat's bottom block was the E20-S30 veil: an opaque ground first, then the blurred
  transcript and a 76 % (92 % without blur) tint, with a hairline on top. Over a live background that is a slab with an
  edge. The header was an opaque block of ground starting under the status bar (E20-S13).
- (4) `KitTopBarScope` centred a short branch word in its 48 dp target, so the gap after the dot was 16 dp against 4 dp
  before it.

## Fix

- `KitScreen`: a page that records its body (a chat) lays no band of ground behind its bar or bottom block, in any
  style. The body's content fades out at both edges instead (`kitScrollEdges`, a smooth-step alpha mask): cleared under
  the bar and faded in over 12 dp below it; faded out from 8 dp above the bottom block over 24 dp. The page's ground or
  live picture shows on, unbroken, from the status bar to the navigation bar. The glass faces still sample the whole
  recording, so the glass chips and message box still show the transcript through the glass.
- The jump fade now holds on down to the list's edge, so the screen's scroll edge fades it out; it has no edge of its
  own.
- The chips' ground fade (`kitLiquidChipsFade`, a haze behind the chips) is gone: nothing reads behind the chips now.
- A page without a recording keeps its ground; its header ground also runs up through the status bar.
- `KitTopBarScope`: a short word sits at the start of its target, so the dot has 4 dp on each side.

## Guards (fail before, pass after)

- `ChromeEdgeBlendTest` (:ui:kit): a chat frame over a busy picture, transcript under the bar and composer, status and
  navigation bar insets; in every style, light and dark, with and without blur, and liquid Glass, the brightness down
  each side column never jumps more than 24 of 255 between two rows. Before: Solid 243 at the bar, liquid Glass 186 at
  the navigation bar.
- `JumpFadeEdgeTest` (:ui:kit-chat): the chat scrolled away ("Jump to latest" showing). Before: liquid Glass 173 of 255
  in one row under the pill, Solid 50.
- `KitTopBarScopeSpacingTest` (:ui:kit): before 4 dp vs 16 dp around the dot.

## Images

Desktop renders of the kit frame (painted aurora, dark, 28 dp status bar, 20 dp navigation bar; liquid glass is the
desktop stand-in without the lens), `ChromeEdgeRenders` in :ui:kit-chat:

- `before-glass-dark-scrolled-up.png`, `after-glass-dark-scrolled-up.png`
- `before-solid-dark-scrolled-up.png`, `after-solid-dark-scrolled-up.png`

Each image shows both ends (the top band under the status bar and the bottom line under the pill). Not checked on a
device or emulator.
