# Per-school agent brief (template)

*Part of [design schools](README.md). The coordinator copies everything below the line, replaces each `{…}`, and sends it as the agent's first message. Use one agent per school, each in its own worktree. Filled briefs for the four schools are in [briefs/](briefs/).*

---

You own one design school in the OpenCode Mobile Flutter app: **{Style}** ({school name}). Worktree: `{worktree path}` (branch `feat/style-{id}`, from int `{baseline commit}`). Work only there. Read AGENTS.md first, then `docs/ux-system/kit-v2.md` §4, §8 and §9, then all of `docs/design/design-schools/`: README, contract, flutter-techniques, and your guide `schools/{id}.md`.

{One-line machine note: where you run, how many CPU/test processes you may use, whether to write a REPORT.md elsewhere.}

## The owner's goal

"I want all the major schools of design to be accommodated in our app and dynamic based on the user choice." Settings › Appearance › Style offers Solid (default), Glass, Soft, Brutal and Paper. Fine-tune knobs (Corners, Depth, Density, Edges) take each style's defaults. Saver forces Solid. You make **{Style}** real on every screen, through the kit's style resolver, without touching any screen.

## What exists when you start

- The code baseline (contract §0): `KitSurfaceLook.decorate/resolve` asks `kitStyleSpec(style)` in `lib/ui/kit/style/kit_style.dart`. Each style is a `KitStyleSpec`: a `KitStyleTokens` object and `Decoration resolve(KitSurfaceRequest r)`. `KitTokens.of` follows Corners and Density; `KitStyles` reads the style and knobs.
- Your spec: `lib/ui/kit/style/kit_style_{id}.dart` ({placeholder, or the Surfaces values moved verbatim}).
- The harnesses: `test/kit/kit_style_test.dart` (invariants for every style × brightness × knob combination), `test/goldens/kit/kit_surfaces_golden_test.dart` (each style in light and dark), and `tool/capture/surfaces_test.dart` (full-screen captures plus a frame-timing line).
- The hooks a school may need, and who builds them: `briefs/README.md`.
- Concepts (AI-generated; take the look, never the data): {list from your guide §7, absolute paths}.

## Files you own (write only these)

- `lib/ui/kit/style/kit_style_{id}.dart`: your spec (tokens, defaults, resolve).
- `lib/ui/kit/style/kit_style_{id}_painters.dart` (optional): helpers only your style needs.
- `test/kit/style/{id}_test.dart`: your unit tests.
- Your goldens `test/goldens/kit/kit_surfaces_{id}_*.png` and any `tolerances.json` entries for them.
- `docs/qa/design-schools-{id}-{date}/README.md` and your capture outputs (renders stay out of git unless the coordinator asks).

## Do NOT edit

- `kit_style.dart` (registry, tokens, readers), `kit_surface_look.dart`, `kit_tokens.dart`, `KitEffects` and preferences. These are the shared baseline. If you need a hook that isn't there (a role, a state, a paint layer), stop and ask the coordinator. Don't fork it.
- Any kit part outside your own spec files, any screen in `lib/ui/screens/`, the chat library (`chat_screen.dart` + `chat/*.dart`), `main.dart`, `theme_roles.dart`, `theme_packs*.dart`, and the Appearance screen and its kit parts (`kit_option_card.dart`, `kit_appearance_preview.dart`).
- Another school's files.
- `.claude/skills/` (never commit).

## Build

1. Turn `schools/{id}.md` §3 (tokens) and §4 (per-family treatment) into your spec. Map the families to the five surface roles (`content`, `control`, `track`, `floating`, `sheet`); where a family needs something the request doesn't carry, ask for a hook.
2. Run `kit_style_test.dart` until every invariant passes for your style across all knob combinations (contract §7). If a token in your guide fails an invariant, fix the token and note it in your report. The guide's numbers are proposals; the invariants are the law.
3. Render your goldens (TZ=UTC) and **look at every image**.
4. Render the captures (contract §8 capture list: 14 screens, light and dark, RTL, 320 dp at 2×, tablet). Compare each, one full screen at a time, with your concept. Do **at least three polish passes**, recording before and after per pass in the QA README.
5. Measure the frame timing (techniques §9) against Solid on the same run, if the machine allows. Otherwise say so plainly.
6. Check the owner rules on every capture: no red; amber only for needs-you; one main action in solid mint; no new UI level; plain words; motion 150–250 ms and still under reduced motion; Saver → Solid; HC per contract §6.

## Progress images (owner rule 2026-10-21)

After EVERY visual pass, not only at the end, save one full phone screen per changed state as `passN-<screen>-<dark|light>.png`. Put them in `launch-footage/progress/<school>/` on the PC, or in `~/vision-assets/out/<school>/renders/` on the build server. Append one line per pass to `LOG.md` there: what changed and what comes next. The owner watches these to steer.

## Tests

- Your unit tests: the spec's defaults, knob combinations (contract §2.2 rule), RTL mirroring of your shadows, chosen and pressed states, lerp endpoints.
- `kit_style_test.dart` passes (all styles, not only yours).
- Your `kit_surfaces_{id}_*` goldens: light and dark (add RTL, 2× text and HC variants as your guide asks).
- **Every existing golden unchanged**: `git diff --stat -- '*.png'` must list only your new files.
- `flutter analyze` once at the end. Format with `dart format --language-version=3.10`.
- No full suite, no emulator or adb unless the coordinator says so, never kill processes by pattern. At most one `flutter test` process at a time.

## Commit

Commit locally and never push. Format: `feat(style): {Style} style through the kit resolver [skip ci]`, a body, `Change-Class: feature`, then exactly:

```
Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_019USgVuPTzKahG6VxEiKX8g
```

## Report back (short)

- What works (which families honour {Style}, light and dark), and what doesn't yet, and why.
- Tokens you changed from the guide, and the invariant that forced each change.
- Frame timing against Solid (or why it wasn't measured).
- Capture paths (one screen per image) and the QA README path.
- Hooks you needed from the baseline (asked or still missing).
- Commits.
