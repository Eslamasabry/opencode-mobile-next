> **Dropped by the owner on 2026-10-21: "It feels so fluffy."** Kept for reference only; Clay is not part of the style set and no agent builds it.

# Clay: puffy and playful (claymorphism)

*Formerly part of [design schools](../README.md). Contract: [contract.md](../contract.md) (Clay removed). Techniques: [flutter-techniques.md](../flutter-techniques.md) §2 (the inset painter Clay would have reused).*

**One line:** soft, inflated surfaces that float above the ground. An outer drop, an inner highlight at the top-start and an inner shade at the bottom-end give each surface a moulded, toy-like volume. Unlike Soft, it works on any colour.

**Picker subtitle:** "Puffy and playful".

## 1. Where it comes from

- **Origin.** Michał Malewicz coined "claymorphism" in 2021 (Hype4), as a follow-up to neumorphism: rounded, floating 3D surfaces that pair well with 3D illustration ([Malewicz, *Claymorphism in user interfaces*, Hype4 Academy](https://hype4.academy/articles/design/claymorphism-in-user-interfaces)).
- **Recipe.** One outer shadow plus **two inner shadows** (a dark one and a light one). Because the surface doesn't need to match the background (Soft's limitation), any fill works. Bece's reference values: outer `8px 8px 16px rgba(0,0,0,.25)`, inner dark `inset -8px -8px 12px rgba(0,0,0,.25)`, inner light `inset 8px 8px 12px rgba(255,255,255,.4)`, radius 32–48 for big boxes. Malewicz advises "minimal typography, strong accent colors, good contrast" to avoid a childish feel. Accessibility is better than Soft's, but hierarchy can suffer because everything bulges, and press and scale animations must respect reduced motion ([Adrian Bece, *Claymorphism: Will It Stick Around?*, Smashing Magazine, 2022](https://www.smashingmagazine.com/2022/03/claymorphism-css-ui-design-trend/)). Bece's utility library: [clay.css](https://github.com/codeAdrian/clay.css).
- **Malewicz's own recipe** (Hype4): one larger outer shadow (it may even sit slightly off the x-axis), two inner shadows (top-left a little lighter, bottom-right a little darker), corners rounded "usually beyond 50% roundness", paired with fluffy 3D imagery. In dark mode it works only while the shape "isn't completely black", so the inner shadows stay visible.
- **Where it lives.** Onboarding, consumer fintech and health, games, and kids' products: friendly, approachable products.

## 2. Principles

1. **Volume, not flatness.** Every content surface has an outer float and an inner light and shade. One light source at the top-start, mirrored in RTL.
2. **Friendly but grown-up.** Generous radii and soft volumes, but restrained type, strong contrast and one accent.
3. **Hierarchy by float height.** Floating layer > cards > panels > chips. A bigger, softer outer drop means higher.
4. **Pressed squishes.** The outer float shrinks and the inner shade deepens. Selected takes an accent tint.
5. **Budget the inner shadows.** On groups, cards, buttons, chips and the composer only. Transcript bubbles get the outer float and a gradient sheen (technique notes below).

## 3. Tokens

| Token | Dark | Light |
|---|---|---|
| ground (adjusted) | `#17181D` | **`#EEF0F6`** (cool lavender-grey) |
| surface1 / 2 | `#262830` / `#2A2C35` | **`#F8F9FC`** / `#FFFFFF` |
| surface3 (chips, tiles) | `#30333C` | `#E9ECF4` |
| outer float (panels, cards) | black α 0.50, (0, 10), blur 24 + black α 0.30, (0, 2), blur 6 | `#1B2240` α 0.14, (0, 10), blur 24 + `#1B2240` α 0.08, (0, 2), blur 6 |
| outer float (floating layer) | (0, 12), blur 28, α 0.55 | (0, 12), blur 28, α 0.16 |
| inner highlight (visible top-start) | white α 0.10, offset (4, 4), σ 6 | white α 0.90, offset (4, 4), σ 6 |
| inner shade (visible bottom-end) | black α 0.35, offset (−4, −4), σ 8 | `#1B2240` α 0.10, offset (−4, −4), σ 8 |
| sheen (fill gradient, top-start → bottom-end) | +5 % → −5 % lightness | +6 % → −4 % lightness |
| primary | accent `#3DDC8A` clay, words `onAccent` | **bright accent `#3DDC8A` clay, ink words** (10.5:1) |
| primary outer float | accent α 0.30, (0, 8), blur 18 | accent α 0.30, (0, 8), blur 18 |
| needs-you card | amber 16 % over surface1 + clay shadows | **`#F9EEDF`** (amber 16 % over `#F8F9FC`) + clay shadows |
| radii | Round × **1.25**: card 28, panel 22, button 18, bubble 24 | the same |

Contrast (my check): light ground `text1` 16.5, `text3` 5.1; surface `text3` 5.5; needs-you card: attention words 4.9, `text1` 16.4. Light accent words 4.47:1, so the accent guard runs. Dark surface `text1` 13.2, `text3` 4.5 (borderline; keep the remap option open if a real-palette check fails).

**Contract note:** the Round × 1.25 radius scale is a proposed amendment to [contract.md](../contract.md) §3.1 (Clay is puffier than Round). If the owner wants "Round" identical across styles, Clay uses plain Round.

Knob defaults: Corners Round, Depth Raised, Density Comfortable, Edges None. Type: display Geist 700 at −0.02em, body 450, labels 600 (optional Nunito: techniques §7).

## 4. Per-part treatment

| Family | Clay treatment |
|---|---|
| F1 panels | clay panel: outer float + inner light and shade + sheen; rows inside flat, no dividers. |
| F2 needs-you | amber-tinted clay card; an amber circle tile with the warning glyph; amber caption; buttons clay inside it. |
| F3 floating | composer: a big clay pill (floating float); its field a soft **inset** well; send and stop as clay circles (accent); top-bar back and menu buttons as clay circles (as in the concept); dock clay. |
| F4 modal | clay sheet with the inner highlight along the top edge; dialog clay with the floating float. |
| F5 controls | primary accent clay; secondary neutral clay; **press = outer float (0,10) → (0,4) and the inner shade +50 %**, 150 ms (instant under reduced motion); selected chip or segment = accent 22 % tint clay. |
| F6 fields | inset well inside the clay surface + a ≥ 3:1 edge. |
| F7 transcript | prompt bubble: clay **without inner shadows** (outer float + sheen), radius 24, tail 8; reply plain; code block flat inset (no float); diff flat. |
| F8 tiles | clay chip tile (small float (0,3) blur 8, no inner shadows); avatars round with a small float. |
| F9 ground | cool ground; live background capped at Subtle. |

## 5. Do's and don'ts for this app

- **Do** keep type grown-up (Geist, strong weights, AA contrast). Clay plus bubbly type plus pastels reads childish (Malewicz's own warning).
- **Do** let the composer's running glow wrap the clay pill. The glow sits outside the outer float.
- **Do** measure first. Clay is the heaviest style, and if the reference scroll goes over +2 ms, switch the inner shadows to the SDF shader before cutting the look.
- **Don't** add pastel colours per element (lavender bubbles, mint tiles, as in the concept) unless the theme provides a partner colour (Q5). The accent stays the one main action; amber stays needs-you.
- **Don't** put inner shadows on transcript bubbles or rows (budget).
- **Don't** use scale-bounce press animations. The squish is a shadow change, not a scale (no layout motion in lists).

## 6. Knob combinations

| Knob | Clay behaviour |
|---|---|
| Depth Flat | floats and inner shadows off → Edges forced on (contract §2.2); the sheen stays, so it's still visibly "Clay" |
| Edges Hairline | a light inner edge (white α 0.5 light, α 0.06 dark) along the surface |
| Corners Soft / Sharp | allowed; Sharp clay looks like moulded tiles |
| Density Compact | float offsets × 0.75 |

## 7. Concepts to match

- `appearance-concepts-2/i-clay-chat/concept.png` (light chat): match the floating clay bubbles, the round clay header buttons, the amber clay needs-you card and the clay composer pill. Treat its lavender prompt bubble as Q5.
- `appearance-concepts-2/j-style-picker/concept.png`: the "Clay · Puffy and playful" card.

## 8. Technique notes (moved from flutter-techniques.md §3)

Clay is the recipe popularised by Adrian Bece's clay.css ([Smashing, Claymorphism](https://www.smashingmagazine.com/2022/03/claymorphism-css-ui-design-trend/)): an outer drop (`8 8 16 rgba(0,0,0,.25)`) plus an inner shade at the bottom-end and an inner highlight at the top-start. In Flutter:

- the outer drop: a `BoxShadow` (fast path), usually `Offset(0, 10)`, blur 24, plus a tight contact shadow `Offset(0, 2)`, blur 6;
- the two inner shadows: the same `KitInsetShadow` painter as Soft (techniques §2), drawn above the fill and below the content;
- the budget binds: inner shadows only on group panels, cards, the composer, buttons and chips (at most 14 visible at once); transcript bubbles get the outer float and a `LinearGradient` sheen (+6 % to −4 % lightness) instead; if over budget, switch the inner shadows to the SDF shader (techniques §2.2).
- Optional face: [Nunito](https://fonts.google.com/specimen/Nunito) (OFL, rounded terminals).
