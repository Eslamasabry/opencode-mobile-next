# Signal: a railway signal box with a console's instruments (2026-10-30)

*Part of [design schools](../README.md). Owner-approved concept CD (renders kept locally in `/home/eslam/Storage2/wt/concepts-2026-10-30/cd/`). Code: `lib/ui/kit/style/kit_style_signal.dart` (the spec), `kit_signal.dart` and `kit_signal_slip.dart` (the instruments), palette `lib/ui/theme_packs_signal.dart`.*

## Idea

One track line down the start edge of lists and of the conversation. Signal lights are the only status colour: red means the work needs you, green means it runs, a hollow ring on a dashed siding means done. The console gives only the instruments you read or press: engraved small-caps labels, a segment timer for running time, a printed slip for a request, raised hardware keys for actions.

## Tokens

| | Light | Dark (night signal box, not inverted) |
|---|---|---|
| Ground (enamel) | `#EFE9DA` | `#12110E` |
| Slip | `#FBF9F2`, edge `#CDC3A9` | `#221F19`, edge `#3F3A30` |
| Rule between rows | `#C9BFA6` | `#2E2B24` |
| Live track (3 dp solid) | `#191815` | brass `#8E8673` |
| Siding (2 dp, 6 on 4 off) | `#6F6858` | `#6A6353` |
| Lamps | red `#FF3B2F`, green `#3BE070`, halo 25 % | the same, glowing (blur 4, 55 %) |
| Segment timer | window `#1B1D1A`, digits `#9FE870`, ghost `#2C302A` | window `#070806` with a `#34302A` bezel |

Words keep the theme's own `text1`/`text2` and accent; `text3` and the attention words move only as far as they must toward the ink to keep 4.5:1 on the enamel. Knob defaults: Corners Sharp (4 dp, the machined radius of a key), Depth Flat, Edges On, Comfortable. Keeps itself under high contrast. The live background turns off when Signal is chosen (still the person's switch); the composer's running glow stays.

## Parts

- **Conversations list** (`KitFeedItem`, `KitSliverRowGroup`): the dashed siding runs down each group, the solid track over a conversation that needs you or runs (its request included); the signal sits on the track at the title line; title and time on one line; the state word in ink, never coloured.
- **Section labels** (`KitSectionLabel`): engraved, 12 dp capitals tracked 0.12 em; screen readers hear the words as written; Arabic has no capitals.
- **Step timeline**: the rail is the track (solid while a step runs or waits, else the siding); nodes are signals (green running, red waiting, unlit queued, ring done); a sentence is a sleeper; the folded work line has sleepers, its words in mono between dashed rules and an engraved Show/Hide.
- **Running time** (`KitToolRow`): the segment timer, ghost digits the same length so the window never jumps.
- **Request** (`kitRequestFrame`): a printed slip out of a printer slot, perforated bottom, no ring or tint; Reject and Allow are both raised keys; "Needs you" is engraved in the signal red with a red lamp.
- **Keys**: an action with a fill of its own (the primary, Send, the + button) is a raised key with a 3 dp lip (1 dp held), straight down in both directions; by night it dims halfway toward the ground. Filters, segments and fields are flat 1.5 dp ink outlines; a chosen one is set in reverse.

## Red

The no-red rule stands everywhere else. Signal's lamps, the engraved "Needs you" and the Stop key are the one red, in this chosen style only, and live in `theme_packs_signal.dart` (allowed by `test/no_red_scan_test.dart`).

## Left to the slices that own them

The transcript-wide track, the start tick, the live line's lamp and timer, and the red Stop key are drawn by `kit_turn.dart` and the composer (the STOP slice) with `KitTrack`, `kitSignalHead`, `kitSegmentTimer`, `kitSignalStartTick` and `kitSignalStopKey`. Frost on the header and composer is FROST's (`KitFrost` allows Signal by name).
