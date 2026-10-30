# Brutal: bold borders, hard shadows (neo-brutalism)

*Part of [design schools](../README.md). Contract: [contract.md](../contract.md). Techniques: [flutter-techniques.md](../flutter-techniques.md) §4.*

**One line:** thick ink borders, hard offset shadows with no blur, flat confident fills and heavy type, kept orderly. It is a skin, not anti-design.

**Picker subtitle:** "Bold borders, hard shadows".

## 1. Where it comes from

- **Brutalism on the web** is the raw, unadorned look of early-1990s pages, named after 1950s concrete architecture. NN/g separates it from **anti-design** (intentionally ugly, disorienting, hierarchy-free) and advises using brutalism **for visual design only**, keeping hierarchy, navigation and interaction clear. Adult Swim is its example of brutalism that is "skin-deep" ([NN/g, *Brutalism and Antidesign*, Kate Moran, 2017](https://www.nngroup.com/articles/brutalism-antidesign/)). A long-running gallery: [brutalistwebsites.com](https://brutalistwebsites.com/).
- **Neo-brutalism** is the colourful, orderly descendant: high contrast, blocky layouts, bold colours, thick borders, solid single-colour offset shadows instead of soft ones, expressive type balanced by whitespace, and retro UI cues such as monospace. Its risks: bold colours failing contrast, clutter from dense borders, and interactive elements losing recognisability. NN/g's recommendations: limit the palette to 2–3 colours, pair a bold display face with a clean body face, use generous whitespace, signal interaction clearly, keep hierarchy, check contrast. Examples it cites include Gumroad and Figma's brand refresh ([NN/g, *Neobrutalism: Definition and Best Practices*, Hayat Sheikh, 2025](https://www.nngroup.com/articles/neobrutalism/)).
- **Gumroad's 2021 look** popularised the idiom: black borders, pastel and pink fills, and a hard offset shadow (about `4px 4px 0 #000`) on cards, buttons and badges (secondary sources; the current site may differ). The rule repeated by every guide is "no blur on the shadow".

## 2. Principles

1. **Ink and paper.** One ink (`text1`), one paper (the surface), **one** accent for the main action and amber for needs-you. That is 2–3 colours, per NN/g, and it fits the owner's rules exactly.
2. **The border is the structure.** A 2 dp ink edge on every surface that holds content or takes input.
3. **Shadows are hard**: blur 0, a fixed offset toward the bottom-end, in ink. A pressed control moves *into* its shadow.
4. **Type is heavy and blunt**: display weight 800, labels in mono (sentence case; the owner bans uppercase labels).
5. **Order, not chaos.** The same grid, spacing and hierarchy as Solid. Only the skin changes. No rotated elements, no clashing colours, no hidden navigation.

## 3. Tokens

| Token | Dark | Light |
|---|---|---|
| ground (adjusted) | `#111214` | **`#F4F0E8`** (cream paper) |
| surface1 / surface2 | `#1E1F24` / `#1E1F24` | **`#FFFDF8`** / `#FFFDF8` |
| surface3 (secondary, tiles) | `#2A2C32` | `#ECE6DA` |
| ink (edge, shadow source) | `#F3F3F1` (`text1`) | `#111214` (`text1`) |
| edge width | **2 dp** (logical; snapped to whole device px) | **2 dp** |
| shadow: panels, cards, buttons | offset (4, 4), blur 0, `#5A5D66` (ink-grey: black on near-black would vanish) | offset (4, 4), blur 0, ink `#111214` |
| shadow: chips, tiles | (3, 3) | (3, 3) |
| shadow: floating (composer, dock, top pill) | (6, 6) | (6, 6) |
| primary fill / words | accent `#3DDC8A` / `onAccent` `#03140B` | **bright accent `#3DDC8A` / ink `#111214`** (10.5:1; contract §5 F5) |
| needs-you tag | `attentionFill` `#FFB547` / `onAttentionFill` + 2 dp ink edge | the same |
| focus ring | accent, **3 dp**, outside the border (offset 2) | accent, 3 dp, outside the border |
| divider | 2 dp ink at α 0.9 (only between groups) | the same |

Contrast (my check): light ground `text1` 16.5, `text2` 6.8, `text3` 5.1, attention 4.9; on the light surface `text3` 5.7. Light accent *words* 4.48:1, so the accent guard runs. Dark surface: `text1` 14.8, `text3` 5.1. The dark shadow grey is 2.8:1 against the ground. That is fine: the shadow is decorative depth, and the 14.8:1 ink border is the boundary (WCAG 1.4.11 counts the border).

Knob defaults: Corners **Soft** (radius 10–14), Depth Raised, Density Comfortable, Edges Hairline, which here means the 2 dp ink edge (see contract Q3). Type: display Geist 800 at −0.03em; body 500; section labels and captions in Geist Mono 600.

## 4. Per-part treatment

| Family | Brutal treatment |
|---|---|
| F1 panels | paper fill, 2 dp ink edge, (4,4) hard shadow; rows inside separated by a 2 dp ink rule *only* between groups, and by space within one; the pressed row is `surface3`. |
| F2 needs-you | paper card, ink edge, hard shadow; the "Needs you" tag is an amber pill with an ink edge (the only amber fill); the question title is display weight; the command is a neutral code box (**not** yellow). |
| F3 floating | composer: paper, 2 dp ink, (6,6) shadow, square-ish send button (accent fill, ink edge); dock: paper with an ink edge, active tab = accent fill + ink words. |
| F4 modal | sheet: paper, 2 dp ink top edge, no shadow (it sits on the scrim); dialog: ink edge + (6,6) shadow; menu: ink edge + (4,4). |
| F5 controls | primary: bright accent + ink words + ink edge + shadow; **press = translate the face (4,4) and drop the shadow** (instant; reduced motion still applies the state). Secondary: paper + ink edge + shadow. Tertiary: ink words + 2 dp underline. Selected chip or segment: accent fill + ink words, no shadow (it reads as "pressed down"). |
| F6 fields | paper, 2 dp ink edge, no shadow; focus = 3 dp accent ring outside. |
| F7 transcript | prompt bubble: tinted paper (see Q5) + ink edge + (4,4); reply: plain text; code block: paper + ink edge, **no shadow**, mono; diff: + and − glyphs carry meaning (no red). |
| F8 tiles | paper tile + 2 dp ink edge; status marks gain a 1.5 dp ink outline; illustration strokes +0.5. |
| F9 ground | cream paper (light) or ink-black (dark); live background capped at Subtle (Q4). |

## 5. Do's and don'ts for this app

- **Do** keep every border and shadow on whole device pixels. A 2 dp border at DPR 2.625 must snap, or it looks soft (visual language §7).
- **Do** make the composer's running glow a **hard 2 dp accent ring** travelling the border, with no blur halo (contract §3.10).
- **Do** use mono for meta and section labels, in sentence case.
- **Don't** add colours. The concept's lavender prompt bubble and yellow code box are tempting, but amber means "needs you" and only the main action is mint. A second hue appears only if the theme has a partner colour (Q5).
- **Don't** put shadows on everything inside a card. Nested shadows turn into noise; only the outer surface casts.
- **Don't** let "Reject" or any destructive or stop action get a colour. It is a paper button, and the confirmation is the safety.
- **Don't** rotate, overlap or skew elements (that is anti-design, per NN/g).

## 6. Knob combinations

| Knob | Brutal behaviour |
|---|---|
| Depth Flat | hard shadows off; borders carry everything; press = `surface3` fill instead of translate |
| Edges None | the ink border is removed, and surfaces keep only the hard shadow; fields keep the border (the ≥ 3:1 rule) |
| Corners Round / Sharp | radii per contract §3.1; Sharp looks most "poster" |
| Density Compact | shadow offset 4 → 3 |

## 7. Concepts to match

- `appearance-concepts-2/h-brutal-chat/concept.png` (light chat): match the borders, the hard shadows, the "Needs you" tag and the button press look. **Don't** copy its yellow command box or its lavender bubble (owner colour rules).
- `appearance-concepts-2/j-style-picker/concept.png`: the "Brutal · Bold borders, hard shadows" card.
- Related general skill (local): `~/.claude/skills/industrial-brutalist-ui` covers the darker, data-terminal branch of brutalism. Brutal here is the friendlier neo-brutal branch.
