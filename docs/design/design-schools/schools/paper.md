# Paper: flat print, Swiss grid (International Typographic Style)

*Part of [design schools](../README.md). Contract: [contract.md](../contract.md). Techniques: [flutter-techniques.md](../flutter-techniques.md) §7.*

**One line:** ink on paper. No shadows, no blur, square corners, fine ink rules, strong size contrast, flush-start text on a firm grid, and one accent colour.

**Picker subtitle:** "Flat print, Swiss grid".

## 1. Where it comes from

- **The International Typographic Style ("Swiss style").** Systematic graphic design from Basel and Zürich, 1930s–1950s, building on Bauhaus, De Stijl and Constructivism. A mathematical grid as the base, asymmetric layouts, text flush left and ragged right, sans-serif type (Akzidenz-Grotesk, Helvetica, Univers), objective photography over illustration, and the designer as an "objective conduit" for the content. Key figures include Ernst Keller, Josef Müller-Brockmann, Max Bill, Armin Hofmann and Emil Ruder ([Wikipedia, International Typographic Style](https://en.wikipedia.org/wiki/International_Typographic_Style)).
- **The grid book.** Josef Müller-Brockmann, *Grid Systems in Graphic Design* (1981): modular grids from 8 to 32 fields, structure as a foundation for creativity ([Thames & Hudson listing](https://thamesandhudson.com.au/?p=93182)).
- **Paper and ink on screens.** Material Design (2014) started from the metaphor "inspired by the study of paper and ink", plus "bold, graphic, intentional": print design's typography, grids, space and colour ([Material Design introduction, 2016 archive](https://webarchive.library.unt.edu/web/20160706101945mp_/http://www.google.com/design/spec/material-design/introduction.html)). Paper drops Material's shadows and keeps the print half.
- **Flat design's lesson.** Flat surfaces can hide what is clickable, so keep strong contrast, make buttons look like buttons, and avoid ghost buttons ([NN/g, Flat-Design Best Practices](https://www.nngroup.com/articles/flat-design-best-practices/)). In Paper, the signifier is a ruled box or an accent fill, never a faint outline.

## 2. Principles

1. **Flat.** No shadows and no blur anywhere, including the floating layer (it is separated by a rule instead).
2. **Rules, not boxes.** Content is grouped by fine ink rules and whitespace. A box appears only around something you can act on (a field, the composer, a secondary button) or a card that needs attention.
3. **Type does the hierarchy.** Big size contrast (largeTitle 34/38 at weight 750), flush-start, tight tracking on display, mono for meta.
4. **Square.** Corners at 0. Circles stay circles (avatars, send and stop).
5. **One accent, inverted selection.** The main action is the accent fill. A selected chip, segment or tab inverts to ink on paper.
6. **Print does not move.** The live background is off by default under Paper (Q4).

## 3. Tokens

| Token | Dark | Light |
|---|---|---|
| ground (adjusted) | `#101010` | **`#F6F4EE`** (warm newsprint) |
| surface1 (panels) | = ground (panels are ruled, not filled) | = ground |
| surface2 (sheets, composer, dialog) | `#171717` | `#FFFFFF` |
| surface3 (secondary, tiles) | `#222222` | `#ECEAE3` |
| ink | `#EDEDEA` | `#111214` (`text1`) |
| box rule (actionable, cards) | 1 dp ink at α 0.6 (6.3:1) | 1 dp ink at α 0.9 |
| row divider | 1 physical px ink at α 0.20 | 1 physical px ink at α 0.20 |
| section rule (above a section label) | 1 dp ink at α 0.9 | 1 dp ink at α 0.9 |
| shadows | none | none |
| radii | **0** (Sharp, overridden down from 2; circles stay) | 0 |
| primary | accent fill, square, words `onAccent` | accent fill, square, `onAccent` |
| selected | **inverted**: ink fill, ground words | inverted |
| focus ring | accent, 2 physical px, outside the rule (offset 2) | the same |

Contrast (my check): light ground `text1` 17.0, `text2` 7.0, `text3` 5.3, accent 4.6, attention 5.1. Dark ground `text1` 17.1, `text2` 7.7, `text3` 5.9, accent 10.7. Rules at those alphas meet 3:1 where they identify a control.

Type (on Geist): largeTitle **34/38, 750, −0.03em**; title 24/30, 700; headline 17/22, 650; body 16/24, 400; section labels and meta in **Geist Mono 500** (sentence case); numbers tabular.

Knob defaults: Corners Sharp, Depth Flat, Density Comfortable, Edges Hairline (= the ink rules).

## 4. Per-part treatment

| Family | Paper treatment |
|---|---|
| F1 panels | no fill, no box: a section rule above, physical-px dividers between rows, generous space; the pressed row is `surface3`. |
| F2 needs-you | ground fill, **4 dp amber rule at the start**, amber caption in mono, display-weight title; no fill, no ring. |
| F3 floating | composer: `surface2` box with a 1 dp ink rule, square, no shadow, accent square or circle send; dock and rail: a 1 dp ink rule on the content side, no box; top bar: a rule under the title only when the page has scrolled. |
| F4 modal | sheet: `surface2`, 1 dp ink top rule, square top corners (0); dialog: 1 dp ink box; menu: 1 dp ink box. |
| F5 controls | primary accent fill (square); secondary 1 dp ink box; tertiary ink words with an underline; selected = inverted ink; segmented: ink box with an inverted thumb; switch: square-ended track. |
| F6 fields | 1 dp ink box at α 0.9 (Q6: underline instead?), square; focus = accent ring outside. |
| F7 transcript | prompt: 1 dp ink box, square, `surface2`; reply: plain text set on the grid; code block: ground + a 1 dp ink rule left and right (a printed listing); diff: rules and +/− glyphs. |
| F8 tiles | 1 dp ink-outline tile, square; status marks outlined. |
| F9 ground | newsprint (light) or near-black (dark); live background off by default. |

## 5. Do's and don'ts for this app

- **Do** let type and space carry hierarchy: one size step up for headings is not enough, so go to 34 for page titles.
- **Do** keep the composer's running glow as a **thin 1 dp accent rule** travelling the box, with no halo.
- **Do** align everything to the start edge. Avoid centred text except the empty-state illustration.
- **Don't** draw a box around every row group. That's Solid with a thin border, not Paper.
- **Don't** use ghost buttons (thin grey outlines). An actionable outline is ink at α 0.9.
- **Don't** fake paper texture (noise or grain bitmaps). It costs memory, blurs the crisp rule, and the owner's crispness rule wins.

## 6. Knob combinations

| Knob | Paper behaviour |
|---|---|
| Depth Raised | the floating layer gets today's tight drop (the only shadow Paper will take) |
| Edges None | dividers and section rules removed (space only); actionable boxes and fields keep their rule |
| Corners Round / Soft | radii per contract §3.1; Round Paper reads like a calm print magazine |
| Density Compact | contract §3.7 |

## 7. Concepts to match

- `appearance-concepts-2/j-style-picker/concept.png`: the "Paper · Flat print, Swiss grid" card (bubble box, square primary). **But** its primary is black. Ours is accent (one main action in solid mint).
- There is no full-screen Paper concept yet. Generate one with the `ui-concept-images` skill before building, or build against the style sheet plus this doc and review the first captures with the owner.
- Related general skill (local): `~/.claude/skills/minimalist-ui` (warm monochrome editorial). Paper is stricter: no pastels, square, ruled.
