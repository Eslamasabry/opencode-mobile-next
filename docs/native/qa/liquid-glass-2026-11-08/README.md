# Liquid glass for the native Glass style — emulator proof, 2026-11-08

Branch `native/liquid-glass` (from `native/screens-merge40` 123df1a33). Design: `docs/designs/liquid-glass.md` v2 plus the
owner's decisions ("go full blown glass", Apple's Liquid Glass rules).

## What is drawn

Under the Glass style the floating parts are liquid glass over the content behind them, through Kyant0/AndroidLiquidGlass
(`io.github.kyant0:backdrop` 2.0.1, Apache-2.0, Android only; its desktop jar targets Java 21):

| Part | Glass over |
|---|---|
| Chat header (capsule), page top bar (conversations) | the transcript / the page body run up under the bar |
| Composer (message box) | the transcript |
| Floating + , Jump to latest pill | the list / the transcript |
| Bottom sheets, menus (popup window, shared by screen position) | the page under them |

- The panes are clear (Glass's own tint at 0.18–0.34). The content is pressed lightly (contrast 0.86, vibrancy 1.45) and
  bent hard in a wide rim (up to 22–30 dp, 40 % of the shorter side). The + and the pills are drops of glass (depth).
- Rim light tinted from the accent (cool in dark, warm in light); no white hairline; dispersion off.
- Legibility: every text on a pane lies on a words plate (GlassSpec.plateFor) whose alpha keeps text1/2/3 at
  min(4.5, Solid) over the worst-case backdrop (`LiquidEdge.wordsPlate`, tested over every pack, light and dark).
- Adaptive ink (per pane): each pane samples its backdrop (1/8 scale, at most 10 Hz, only when the source re-recorded) and
  flips its words to the other theme's ink and plate over bright/dark content, with hysteresis and a 180 ms cross-fade.
- Scroll edge effect: content fades out 20 dp above the composer block; the bar's top gap holds the ground.

## Falls back where

| Condition | Draws |
|---|---|
| Any style but Glass | exactly today (pixel-equal test) |
| Glass, Android 12 and below (no RuntimeShader), desktop | today's frost or solid |
| Saver, reduced motion, high contrast, frost strength Off | today's hold (FrostPolicy.edgesHeldBy) |
| Settings › Appearance › Glass edges: Frosted | today's frost (the kill switch; "Needs Android 13" shown below 13) |
| More than 4 liquid surfaces in a window | the lowest drop to frost (menu > sheet > composer > bar > + > pill > status) |
| A sheet or menu open | the chrome under it drops to frost (no glass on glass) |
| A menu inside a sheet or dialog | frost |
| End side sheet (square shape) | frost |
| Predictive back on a sheet, a menu's grow-in | the lens eases into frost while it runs |

## Emulator notes

- AVD OC_API35 (API 35, google_apis x86_64), `-gpu swiftshader_indirect`; the PC has no host GPU (the render node belongs to
  the gdm seat). The API 37 image aborts system_server on every task snapshot
  (`mapper.ranchu: Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`), under swiftshader and guest GPU alike.
- The app reached the host server only through `adb reverse` (10.0.2.2 timed out from the app, though `nc` from the shell
  worked).
- Proof server: OpenCode 2.2.6, isolated data dir, throwaway project; the code chat was generated with GLM-5.3-Flash, the
  sunset chat was imported (a generated image plus a reply with coloured code).

## Renders in this folder (one phone screen each)

| Screen | Today's Glass | Liquid | Solid |
|---|---|---|---|
| Chat over the sunset image and coloured code, dark | chat-today-glass-dark | chat-liquid-dark (build 5da4b5d4c, before the 18:55 fixes) | chat-solid-dark |
| Same, light | chat-today-glass-light | chat-liquid-light (build 5da4b5d4c) | chat-solid-light |
| Conversations list scrolled under the bar | list-today-glass-{dark,light} | list-liquid-dark, list-liquid-light (b5c31190c: shows the light-ink sampling bug, fixed in 44fd4873a) | list-solid-{dark,light} |
| Model sheet | sheet-today-glass-{dark,light} | sheet-liquid-dark | sheet-solid-{dark,light} |
| Chat menu | menu-today-glass-{dark,light} | menu-liquid-dark | menu-solid-{dark,light} |
| Settings › Appearance › Glass edges | | settings-glass-edges-dark | |
| Library calibration (Backdrop Catalog 2.0.1, same emulator) | calibration-catalog-{bottom-tabs,buttons,dialog} | | |

"Today's Glass" is the same APK with Glass edges: Frosted, which draws today's frost path exactly (pixel-equal test).

Still to render on the latest build (44fd4873a / ba77ad857): liquid chat/list/sheet/menu in dark and light, the ink
flip (a pane over the white band of the sunset image versus over dark code), the scrolling video, and the frame-time
comparison. Idle check on the first build: 0 frames rendered over 5 s with the liquid list on screen.
