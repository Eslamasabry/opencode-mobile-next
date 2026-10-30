# Brief: Glass (glassmorphism)

*Ready to send. Generated from [agent-brief-template.md](../agent-brief-template.md) for the code baseline `feat/schools-baseline` (contract §0), with hooks H1–H11 built: you edit only your own spec file, goldens and renders.*

---

You own one design school in the OpenCode Mobile Flutter app: **Glass** (glassmorphism). Make a worktree first: `git -C /home/eslam/Storage2/wt/oc_app-schools-base worktree add /home/eslam/Storage2/wt/oc_app-style-glass -b feat/style-glass feat/schools-baseline`. Work only there. Read AGENTS.md first, then `docs/ux-system/kit-v2.md` §4, §8 and §9, then `docs/design/design-schools/`: README, contract (§0 first: it says what is built), flutter-techniques, your guide `schools/glass.md` and `briefs/README.md` (the hooks, all built, and the spec field that drives each).

You run on the PC (8 CPU, 16 GB, shared with other agents): at most one `flutter test` process at a time with `-j 2`; between 00:00 and 02:00 no local Flutter at all. Run `flutter pub get` (and in `widgetbook/`) once at the start, with the pinned Flutter `~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.

## The owner's goal

"I want all the major schools of design to be accommodated in our app and dynamic based on the user choice." Settings › Appearance › Style offers Solid (default), Glass, Soft, Brutal and Paper (Clay was dropped: "It feels so fluffy"). Fine-tune knobs (Corners, Depth, Density, Edges) take each style's defaults; changing the style resets them. Saver forces Solid. You make **Glass** real on every screen, through the kit's style resolver, without touching any screen.

## What exists when you start

- The baseline (contract §0): `KitSurfaceLook.decorate/resolve` asks `kitStyleSpec(style)` (in `lib/ui/kit/style/kit_style.dart`). Each style is a `KitStyleSpec`: a `KitStyleTokens` object (Fine-tune defaults, sharp radius, blur, high-contrast behaviour, type tweaks) and `Decoration resolve(KitSurfaceRequest r)`. The request carries the theme roles, the surface role (`content`, `control`, `track`, `floating`, `sheet`), the part's own solid decoration, the knobs in force, the level, radius, circle, a part's own colour, and selected/pressed. You return a `KitSurfaceDecoration` (fill, lit start/end edge, paired soft shadows, drop shadows, inset, and the hard shadow layer).
- `KitTokens.of` already follows Corners and Density; `KitStyles.knobsFor` applies the §2.4 rules (high contrast, flat Soft keeps an edge).
- Your starting point: Surfaces' Glass, moved **verbatim** into the spec file (pane opacities in `KitStyleGlass.opacity`, dark panes tinted 45 % towards the ground, rims, one bounded blur sigma 18 per floating piece or sheet). Its goldens are the Surfaces ones.
- Harnesses: `test/kit/kit_style_test.dart` (invariants for every style × brightness × knob combination), `test/goldens/kit/kit_surfaces_golden_test.dart` (your style in light and dark), `tool/capture/surfaces_test.dart` (home, chat, settings and a sheet per style, plus a frame-timing line).
- Concepts (AI-generated; take the look, never the data):
  - /home/eslam/Storage2/launch-footage/appearance-concepts-2/e-glass/concept.png (dark; the frosted preview card, the pill tabs, the rims)
  - /home/eslam/Storage2/launch-footage/glass-inspiration/owner-glass-home.png and owner-glass-music.png (the owner's picks: restrained panes, colour clearly showing through)
  - /home/eslam/Storage2/launch-footage/appearance-concepts-2/k-make-it-yours/concept.png (Glass selected in the tour)

## Files you own (write only these)

- `lib/ui/kit/style/kit_style_glass.dart`: your spec.
- `lib/ui/kit/style/kit_style_glass_painters.dart` (optional): helpers only your style needs.
- `test/kit/style/glass_test.dart`: your unit tests.
- `test/goldens/kit/kit_surfaces_glass_*.png` and any `tolerances.json` entries for them.
- `docs/qa/design-schools-glass-<date>/README.md` and your renders (renders stay out of git).

## Do NOT edit

- `kit_style.dart` (registry, tokens, readers), `kit_surface_look.dart`, `kit_tokens.dart`, `lib/state/effects.dart`, `app_preferences.dart`, another school's spec. **The hooks you use (H1, H3, H10, H11) are built**; you drive them from your spec only (see the table in `briefs/README.md`: which spec field drives which hook). If something is still missing, ask the coordinator; don't work around it in a part.
- Any kit part, any screen, the chat library, `main.dart`, `theme_roles.dart`, `theme_packs*.dart`, the Appearance screen and its kit parts (`kit_option_card.dart`, `kit_appearance_preview.dart`).
- `.claude/skills/` (never commit).

## Build

1. Targets for Glass (from `schools/glass.md`; the guide's numbers are proposals, the invariants are the law):
   - **Known issue (coordinator, 2026-10-21): on the emulator Glass over the live background is far too opaque.** The dark tint leans to the ground for AA, so the aurora barely shows through. Evidence: `/home/eslam/Storage2/launch-footage/dryrun-2572/10-home-glass-live.png`. Target: the inspiration images above and `e-glass`.
   - **Keep AA by raising text weight or adding a scrim only behind the words, not by filling the whole pane.** So: lower the pane opacities and the ground lean (start near α 0.30–0.40 content, 0.45–0.55 floating and sheet, lean ≤ 0.15), then restore contrast with H1 (one weight step up for words on glass) and H10 (a soft plate behind text). The faintest words go to `text2` on panes (H3). Note: H1's `weightBoost` is style-wide (every word in Glass); words on panes only are reached through the plate (H10, `wordsPlate` per role) and `onColor` (H3).
   - Re-derive the contrast expectations in `test/kit/kit_surface_look_test.dart` (glass groups only) against the worst-case backdrop: the brightest colour of each live-style palette at Bold intensity (contract §7.1). The words, not the whole pane, must reach 4.5:1.
   - Blur only floating pieces and one sheet (≤ 3 live on screen); one `BackdropGroup` per route (H11). Accent-coloured words never sit on glass; code surfaces stay opaque.
   - Rims stay crisp (1 physical px light top, dark bottom); the running glow sits on the composer's rim.
2. Keep `kit_style_test.dart` green for every style and knob combination. If a guide token fails an invariant, change the token and say so in your report.
3. Render your goldens (`TZ=UTC flutter test test/goldens/kit/kit_surfaces_golden_test.dart --plain-name "surfaces · glass" --update-goldens`) and **look at every image**. No other golden may change (`git diff --stat -- '*.png'`).
4. Render the captures: `SURFACES_SHOTS_DIR=/home/eslam/Storage2/launch-footage/progress/glass/passN flutter test tool/capture/surfaces_test.dart --plain-name " glass "`. Compare each, one full screen at a time, with your concept. Do **at least three polish passes**. Extend the capture list to the contract §8 screens as the hooks land.
5. Check the owner rules on every render: kit only; never red; amber only for "needs you"; one main action in solid mint; no new UI level; plain words; motion 150–250 ms and still under reduced motion; `flutter_animate` banned; Saver → Solid; high contrast per contract §6.

## Progress images (owner rule 2026-10-21)

After EVERY visual pass, not only at the end, save one full phone screen per changed state as `passN-<screen>-<dark|light>.png` in `/home/eslam/Storage2/launch-footage/progress/glass/`. Append one line per pass to `LOG.md` there: what changed and what comes next. The owner watches these to steer.

## Tests

- Your unit tests: defaults, knob combinations, RTL mirroring of anything directional, chosen and pressed states, lerp to and from Solid.
- `test/kit/kit_style_test.dart` and `test/kit/kit_surface_look_test.dart` pass.
- `test/kit_ratchet_test.dart` (G17: no colour literals; derive from the roles) and `test/no_red_scan_test.dart` pass.
- Glass groups in `test/kit/kit_surface_look_test.dart` (you own those groups, not the file's other tests).
- Your `kit_surfaces_glass_*` goldens; every other golden unchanged.
- `flutter analyze` once at the end. Format with `dart format --language-version=3.10`.
- No full suite, no emulator or adb unless the coordinator says so; never kill processes by pattern.

## Commit

Commit locally and never push. Format: `feat(style): Glass tuned through its spec [skip ci]`, a body, `Change-Class: feature`, then exactly:

```
Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_019USgVuPTzKahG6VxEiKX8g
```

## Report back (short)

- What now looks right in Glass (light and dark), and what still doesn't, and why.
- Tokens you changed from the guide, and the invariant that forced each.
- The hooks you asked for, and which you are still waiting on.
- Frame-timing lines against Solid.
- Render paths (one screen per image) and the QA README path.
- Commits.
