# Design schools: one app, five styles (2026-10-21)

**Owner's goal (approved 2026-10-21):** "I want all the major schools of design to be accommodated in our app and dynamic based on the user choice."

Settings › Appearance gets a **Style** choice that applies to every screen:

| Style | School | Picker subtitle | Guide |
|---|---|---|---|
| **Solid** (default) | today's calm matte look, pixel-identical | Calm and matte | [schools/solid.md](schools/solid.md) |
| **Glass** | glassmorphism | Frosted, see-through | [schools/glass.md](schools/glass.md) |
| **Soft** | neumorphism | Raised and pressed | [schools/soft.md](schools/soft.md) |
| **Brutal** | neo-brutalism | Bold borders, hard shadows | [schools/brutal.md](schools/brutal.md) |
| **Paper** | flat print / Swiss style | Flat print, Swiss grid | [schools/paper.md](schools/paper.md) |
| **Signal** (owner, 2026-10-30) | railway signal box with console instruments | Signal | [schools/signal.md](schools/signal.md) |
| **Crystal** (owner, 2026-10-22) | iOS 26 clear Liquid Glass | Crystal | lib/ui/kit/style/kit_style_crystal.dart; engine lib/ui/kit/glass/kit_crystal_engine.dart; evidence docs/qa/design-schools-crystal-2026-10-22/ |

*Clay (claymorphism) was dropped by the owner on 2026-10-21: "It feels so fluffy." Its guide is kept for reference in [dropped/clay.md](dropped/clay.md).*

Under it, **Fine-tune** has four knobs shared by every style: **Corners** (Round · Soft · Sharp), **Depth** (Flat · Raised), **Density** (Comfortable · Compact) and **Edges** (Hairline · None). Each style sets the knobs' defaults, and changing the style resets them. **Saver** (true black, motion off) always forces Solid. Later comes an optional, skippable onboarding tour, **"Make it yours"** (mode → style → theme/accent → motion), with a live preview.

## The documents

| Doc | What it is | Who reads it |
|---|---|---|
| [contract.md](contract.md) | **The baseline.** Token schema (`KitStyle`), knobs and storage, resolver API (`KitSurface.look(role)`), the ten surface families and every kit file in them, fallbacks, invariants, acceptance checklist, performance budget, reconciliation with the Surfaces branch, merge order. | everyone, first |
| [flutter-techniques.md](flutter-techniques.md) | How to build each effect on Flutter 3.47.1 / Impeller: blur-behind, inset and hard shadows, RTL mirroring, cross-fade, typography, accessibility signals, frame timing, golden strategy, pub.dev verdicts. | every school agent, before painting |
| [schools/](schools/) | One guide per school: origins and sources, principles, exact light and dark tokens, per-family treatment, do's and don'ts for this app, knob behaviour, which concept to match. | the agent building that school |
| [agent-brief-template.md](agent-brief-template.md) | A ready-to-fill brief for a per-school agent: owned files, forbidden files, capture list, tests, report. | the coordinator |
| [briefs/](briefs/) | The four filled per-school briefs (Paper, Brutal, Soft, Glass) and the hooks a school may ask the baseline for. | the coordinator, then each school agent |

## The owner's plan, and where we are

1. **Research the guidebooks and how to do each school well in Flutter.** Done (these docs).
2. **Write the baseline** (shared contract + one guide per school). Done: the docs, then the code on `feat/schools-baseline` (contract §0), reconciled with what Surfaces shipped (contract §10).
3. One agent per school builds against the baseline (contract §11 step 3, using [agent-brief-template.md](agent-brief-template.md)).
4. The coordinator merges them all, in the order of contract §11.

## Rules

Every school obeys these. They come from the owner and AGENTS.md:

- **Kit only.** Every UI component comes from `lib/ui/kit/`. Screens never paint a style. They arrange kit parts, and kit parts ask the resolver (contract §4). The G16 ratchet must not rise.
- **Never red.** No red, danger or error colour anywhere (`test/no_red_scan_test.dart`). A destructive act is made safe by its confirmation, never by a colour.
- **Amber only means "needs you".** No amber code boxes, highlights or decorations in any style.
- **One main action per screen, in solid mint** (the theme accent, opaque, in every style).
- **No new UI levels.** A style never adds a bar or a row. It re-skins the same parts.
- **Plain words** in every label, including the style names and subtitles above.
- **Micro-animations 150–250 ms**, still under reduced motion (`KitMotion`). **`flutter_animate` is banned.**
- **Simplify and natural.** Judge each treatment by what it removes. A style that needs explanation is wrong.
- **Sharp and crisp** (visual language §7): integer type, edges on whole device pixels, no text shadows.
- **AA always.** Text ≥ 4.5:1 and non-text cues ≥ 3:1 in every style, light and dark, including glass over its worst-case backdrop.
- **Solid stays pixel-identical.** No existing golden may change.
- **Review one full phone screen per image**, never grids or composites. Before any APK reaches the owner, every screen in the capture list is checked on the emulator.
- **Docs and evidence:** each school records its captures and checks in `docs/qa/design-schools-<style>-<date>/README.md`.

## How a per-school agent uses these docs

1. Read AGENTS.md, then `docs/ux-system/kit-v2.md` §4, §8 and §9.
2. Read [contract.md](contract.md) end to end. Its §4.4 seam is the only code surface you write.
3. Read your school's guide and [flutter-techniques.md](flutter-techniques.md) §0 plus your technique section.
4. Open your concept image(s) (local paths in your guide §7). Take the look, never the data.
5. Fill your `KitStyleSpec`, run the invariant test, render the style sheet golden and the captures, and polish against the concept at least three times.
6. Report per the brief template.

## Owner decisions (2026-10-21: "All yes")

The owner accepted every recommendation below (Q1–Q9). Q8 was later dropped with Clay (2026-10-21). The recommended column is now the decision; school agents build to it.

## Questions as asked

Each has a recommended answer. The baseline is built on the recommendation unless the owner says otherwise.

| # | Question | Recommendation |
|---|---|---|
| Q1 | **Fonts.** Should all the styles use the bundled Geist and Geist Mono (weight, tracking and size do the work), or add one open-licence face for Brutal (Archivo) or Paper (Inter Tight)? Each new face is a release (not a patch) and about 100–400 KB. | Geist only first. Revisit after seeing the captures. |
| Q2 | **High contrast.** When the phone asks for high contrast, should Brutal and Paper keep their look (they are already high contrast) while Glass and Soft fall back to Solid? Or should all of them fall back to Solid? | Brutal and Paper keep their look. |
| Q3 | **Edges wording.** "Hairline · None" reads oddly for Brutal (a bold 2 dp ink edge) and Paper (ink rules). Keep the words and let "Hairline" mean "the style's own edge"? Or rename the knob to "Edges: On · Off"? | Rename to **On · Off**. Same knob, clearer word. |
| Q4 | **Live background under print styles.** Should Paper start with the live background off ("print does not move"), and should Brutal cap it at Subtle? Or keep the person's choice in every style? | Paper off by default, Brutal and Soft capped at Subtle, still the person's switch. |
| Q5 | **A second hue.** The Brutal concept tints the person's message bubble lavender. Allow the theme's partner colour as a tint on the prompt bubble only? Otherwise it stays neutral. | Yes, on the prompt bubble only, at 35 %, never on actions. |
| Q6 | **Paper fields:** a full ink box, or a single underline (classic print form)? | Box (a clearer target and boundary). |
| Q7 | **The High-contrast theme pack:** should choosing it also act like the phone's high-contrast switch for styles? | Yes. |
| Q8 | **Clay corners:** Clay looks puffier with radii 25 % larger than Round (cards 28 instead of 22). Allow that one exception, so that "Round" differs slightly in Clay? | **Dropped with Clay** (2026-10-21). |
| Q9 | **Onboarding "Make it yours":** shown once after first setup, or only from Settings? | After the first successful connection, once, with Skip; also reachable from Appearance. |

## Sources (all schools)

Each guide lists its own. The core set:

- NN/g: [Glassmorphism](https://www.nngroup.com/articles/glassmorphism/) (2024), [Neobrutalism](https://www.nngroup.com/articles/neobrutalism/) (2025), [Brutalism and Antidesign](https://www.nngroup.com/articles/brutalism-antidesign/) (2017), [Flat-Design Best Practices](https://www.nngroup.com/articles/flat-design-best-practices/) (2017), [Liquid Glass Is Cracked](https://www.nngroup.com/articles/liquid-glass/) (2025)
- Malewicz (Hype4 Academy; first published on UX Collective): [Neumorphism](https://hype4.academy/articles/design/neumorphism-in-user-interfaces), [Glassmorphism](https://hype4.academy/articles/design/glassmorphism-in-user-interfaces)
- Adrian Bece: [Neumorphism and CSS](https://css-tricks.com/neumorphism-and-css/) (CSS-Tricks, 2020)
- Platform materials: [Microsoft Acrylic](https://learn.microsoft.com/en-us/windows/apps/design/style/acrylic), [Apple HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials), [Material 3 Elevation](https://m3.material.io/styles/elevation/overview)
- Accessibility: [WCAG 2.2 SC 1.4.11 Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html), [Built In on neumorphism](https://builtin.com/design-ux/neumorphism-accessibility), [Axess Lab on glassmorphism](https://axesslab.com/glassmorphism-meets-accessibility-can-frosted-glass-be-inclusive/)
- Swiss style: [International Typographic Style](https://en.wikipedia.org/wiki/International_Typographic_Style); Müller-Brockmann, *Grid Systems in Graphic Design* (1981)
- Flutter: [BackdropFilter](https://api.flutter.dev/flutter/widgets/BackdropFilter-class.html), [BackdropGroup](https://api.flutter.dev/flutter/widgets/BackdropGroup-class.html), [Performance best practices](https://docs.flutter.dev/perf/best-practices), [Impeller `SolidRRectBlurContents`](https://api.flutter.dev/impeller/classimpeller_1_1_solid_r_rect_blur_contents.html)

Copyright: these docs summarise in our own words, quote at most a short line, and link every source. No third-party pages or images are stored in the repo. Concept images stay in `/home/eslam/Storage2/launch-footage/` (local).
