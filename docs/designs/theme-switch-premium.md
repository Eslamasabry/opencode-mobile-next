# Premium theme switching on the existing mode control

**Status:** v2, APPROVED by the owner 2026-11-08, after two goldfish reviews (zoom-out, design critic). The v1 draft and the reviews are in git history.

**The owner's choices:**
- Keep the Light / Dark / Auto / Saver control and add premium motion to it (option C). This is not a new two-state switch.
- The rim light uses the theme accent: a cool tint in dark, a warm tint in light. Styles without glow get their own version.

**Reference:** the owner's 14 s video (a glowing rim light on the knob; the icon dissolves to pixels and re-forms; the surface fades in step).

**Goal:** changing the theme feels crafted and smooth on every style, at the phone's full refresh rate, with no new controls and no layout change.

## Non-goals

- A new two-state switch.
- Moving Saver or Auto.
- A Quick Settings tile.
- A quick-menu switch: overlay windows are outside the theme cover's capture.
- Any change to the guided Appearance flow.

## Step 0 (gate): fix the theme-change stall first

`KitThemeFade` (P-116) measured about 95 ms for the first frame in the new colours, because every part recomposes on the main thread at once. Any motion started at the tap would hitch.

**Spike, done first:**
- measure on the owner's phone (Perfetto or JankStats);
- find what recomposes, and reduce it, for example by reading colours in the draw phase through locals, or splitting expensive subtrees;
- report the before and after numbers.

**The motion below is built only if the theme-change frame is under 16 ms**, or the commit can be placed so it doesn't land on a motion frame.

## Choreography (one beat table)

| t (ms) | Event |
|---|---|
| 0 | Tap. The haptic tick (the existing kit haptic, respecting the Haptics setting). The selected-segment indicator starts its slide: the `release` spring spec, allowed slight overshoot. |
| 0–120 | The rim light starts on the target segment's edge. The source icon starts dissolving. |
| ~120 | **Theme commit**, after the motion is visibly underway. The motion parts animate in draw-phase or layer-only state, so a recompose does not stall them. The existing ThemeFade cover (screenshot fade, 200 ms) runs from here. |
| 120–450 | The dots re-form into the target icon. The rim light travels round the segment edge and settles into a soft glow. |
| ~700 | Idle. Every shader or draw loop has stopped, so the idle cost is zero. |

**Interruption.** A second tap mid-animation retargets the slide and the dots from their current positions. The rim restarts on the new target. The theme commits again with the normal cover.

**No replay.**
- A programmatic change (Auto flipping with the system, rotation, first composition, process restore) shows the end state with no rim, dust or haptic.
- The animation state is not saved: it only replays on a person's tap.

## Parts

**1. Selected-segment indicator, rim light** (in `KitSegmented` or a mode-specific variant in `:ui:kit`)
- A segment of light runs along the indicator's rounded edge, as a head plus a fading tail, with a soft halo.
- It's drawn with a `PathMeasure` stroke plus a gradient and a small blur layer, so it works on every Android version: no AGSL needed, and no new shader seam.
- It's low-resolution and runs for about 600 ms only.

**2. Pixel icon morph**
- The Light / Dark / Auto / Saver icons get pixel-art versions authored as small dot grids (new assets: sun, moon with sparkle, auto, leaf).
- On change, the dots of the old icon move to the slots of the new one, with a precomputed mapping, jitter and a stagger. Extra dots fade out and missing ones fade in.
- One Canvas pass.
- The icons also look right still, as pixel icons in the control at rest.

**3. Per-style rim**

| Style | Rim |
|---|---|
| Solid, Signal | Accent glow, as above |
| Glass | Accent glow, plus its existing frost and fallback path |
| Soft | A soft inner glow, no hard edge |
| Brutal | No glow: a hard outline sweep plus a 2 px offset shift |
| Paper | No glow: an ink outline sweep |

**4. Colour**
- The rim's hue is derived from the theme accent: in HCT, the hue is pulled toward a fixed night anchor (cool) in dark and a day anchor (warm) in light, with a tone clamp.
- A test checks every theme × light/dark, so the rim is visible against its ground, and the selected segment keeps 3:1 contrast through its edge or icon.

**5. Motion levels**
- **Full:** everything.
- **Calm:** the slide plus a plain icon crossfade; no rim, no dust.
- **Off, reduced motion or Saver:** an instant change and the existing cover fade only.
- Driven by the kit's `MotionLevel` (from the app's Motion setting and the system animator scale).

**6. RTL**
- The control follows the layout direction, as KitSegmented already does. Travel directions mirror, and so do Brutal's offset shadows.
- RTL goldens.

## Accessibility

Unchanged semantics: the segmented control's roles and labels stay. Also:
- rapid toggling is debounced so the full-screen luminance flip cannot exceed 3 a second;
- focus ring and keyboard or D-pad activation work as today;
- 1.3× frame text is supported;
- haptics are off when the setting is off.

## Verification

- **Gate 0:** theme-change frame time before and after, on the owner's phone.
- **Kit tests:**
  - state at rest for each mode, style and RTL;
  - the motion level matrix;
  - interruption retargeting;
  - no replay on programmatic changes;
  - the colour and contrast test across all themes.
- **Motion review:** a recorded video from the emulator or phone, of each style in light and dark, put side by side with the owner's reference for the owner's verdict. Desktop renders don't prove smoothness.
- **On the phone:** JankStats during repeated mode changes. Janky frames under 5 % and p99 under 20 ms during the animation (the RC1 G4 targets).

## Plan

1. Step 0 spike: measure the theme-change stall and fix it (`:ui:kit` theme plus whatever recomposes).
2. Kit parts: the rim light, the pixel icons, the morph, per-style rims, colour, motion levels and RTL, in `:ui:kit`, with tests and the recorded video.
3. Use the pixel icons and the motion in `AppearancePage`'s mode control. That is a small hook in `:feature:settings`, done after s31-app frees the module.
