# Solid: calm and matte (the default)

*Part of [design schools](../README.md). Contract: [contract.md](../contract.md). Techniques: [flutter-techniques.md](../flutter-techniques.md).*

**One line:** today's look, pixel for pixel. Matte panels in surface steps, a hairline edge, and one tight shadow under the floating layer only.

**Picker subtitle:** "Calm and matte".

## 1. Where it comes from

- **Flat design → "flat 2.0".** Flat design took off around 2012. Its known failure is weak signifiers: flattened controls don't look clickable. NN/g recommends a *semiflat* "flat 2.0" with subtle depth for complex products, plus clear contrast and buttons that look like buttons ([NN/g, Flat-Design Best Practices](https://www.nngroup.com/articles/flat-design-best-practices/); [NN/g, Flat UI elements attract less attention](https://www.nngroup.com/articles/flat-ui-less-attention-cause-uncertainty/)).
- **Tonal surfaces instead of shadows.** Material 3 shows elevation through surface tone rather than drop shadows in most places ([Material 3, Elevation](https://m3.material.io/styles/elevation/overview)). The app's visual language does the same with four surface steps (visual language §1, §4).
- **The owner's own bar.** Visual language v1 (approved 2026-09-26) and its "sharp and crisp" rule (§7): integer type, one-physical-pixel hairlines, no shadow on content, and one tight drop (y 6, blur 16, −6 spread, 30 % black) under floating surfaces. The owner's Dribbble pick `owner-dark-dashboard.jpg` (local) is a good dark Solid target: near-black ground, panels one step lighter, quiet hairlines, a single accent.

## 2. Principles

1. Depth comes from **surface steps** (ground → surface1 → surface2 → surface3), never from shadows on content.
2. **One** shadow exists, and only under the floating layer (dock, rail, top pill, composer).
3. Edges are **one physical pixel** (`KitTokens.hairlineWidth`), snapped to the pixel grid.
4. **One accent** for the main action and "working"; amber only for "needs you"; no red.
5. Text is opaque tokens, never text at partial opacity.

## 3. Tokens

Solid **is** the current theme. `kit_style_solid.dart` returns the theme's roles unchanged and today's `KitTokens` values (Corners Round, Depth Flat, Density Comfortable, Edges Hairline). Graphite, for reference:

| Role | Dark | Light |
|---|---|---|
| ground | `#0B0C0E` | `#F3F3F1` |
| surface1 / 2 / 3 | `#141518` / `#1B1C20` / `#26282D` | `#FAFAF8` / `#FFFFFF` / `#E9E9E6` |
| hairline | white α 0.07 | black α 0.08 |
| text1 / 2 / 3 | `#F3F3F1` / `#A3A5AB` / `#8C8F96` | `#111214` / `#50535A` / `#62656C` |
| accent / onAccent | `#3DDC8A` / `#03140B` | `#087F43` / `#FFFFFF` |
| attention | `#FFB547` | `#9A5700` |
| elevationShadow | black α 0.30 | black α 0.30 |

Radii, density and type: today's tables ([contract.md](../contract.md) §3.1 Round, §3.7 Comfortable; visual language §2, §7).

## 4. Per-part treatment

Today's kit, exactly. The [contract §5](../contract.md#5-kit-parts-that-must-honour-the-style) "Solid" column is a description of the current code, not a change.

## 5. Do's and don'ts for this app

- **Do** keep Solid byte-identical. The proof is that every one of the ~4,644 existing goldens is unchanged after the baseline and after every school merge.
- **Do** keep Solid the Saver style and the high-contrast fallback for Glass and Soft.
- **Don't** let a Solid knob change sneak in as a default: Solid's defaults are exactly today.
- **Don't** add shadows to content cards "to match the other styles". Solid + Depth Raised is the person's choice (it lifts content panels with the floating shadow), never the default.

## 6. Knob combinations

| Knob | What changes in Solid |
|---|---|
| Corners Soft / Sharp | radii per the contract §3.1 table |
| Depth Raised | content panels and cards gain the floating layer's tight drop (y 6, blur 16, −6) |
| Density Compact | contract §3.7 |
| Edges None | panels lose the hairline (today's panels already have none); fields keep their edge |

## 7. Concepts to match

- `appearance-concepts-2/final-dark/concept.png`, `final-light/concept.png`, `final-saver/concept.png` (local, `/home/eslam/Storage2/launch-footage/`)
- `glass-inspiration/owner-dark-dashboard.jpg` (the owner's pick, local)
- `appearance-concepts-2/j-style-picker/concept.png`: the "Solid · Calm and matte" card
