# Soft: raised and pressed (neumorphism)

*Part of [design schools](../README.md). Contract: [contract.md](../contract.md). Techniques: [flutter-techniques.md](../flutter-techniques.md) §2.*

**One line:** the surface *is* the ground, shaped by a pair of soft shadows (a light one at the top-start, a dark one at the bottom-end). Controls rest raised and press in when chosen.

**Picker subtitle:** "Raised and pressed".

## 1. Where it comes from

- **Origin.** Alexander Plyuto's banking-app shot on Dribbble (late 2019) started it. Michał Malewicz wrote it up and the name "neumorphism" (new skeuomorphism) stuck ([Malewicz, *Neumorphism in user interfaces*, Hype4 Academy](https://hype4.academy/articles/design/neumorphism-in-user-interfaces); [Wikipedia, Neumorphism](https://en.wikipedia.org/wiki/Neumorphism)).
- **Recipe.** The element has the same colour as its background. Two `box-shadow`s, a lighter and a darker tone of that colour, offset in opposite directions from one light source, with soft blur and low contrast. The pressed state is the same pair *inset*. Convex and concave gradient variants follow the light. An optional subtle border sharpens edges ([Adrian Bece, *Neumorphism and CSS*, CSS-Tricks, 2020](https://css-tricks.com/neumorphism-and-css/)).
- **The criticism, which shapes every rule below.** The look is built from low contrast, so controls can stop reading as controls. Users with low vision struggle, hierarchy flattens, and the whole app becomes "basically one color". Malewicz himself warned it is not for every element and suggested cards over buttons ([Built In, *Neumorphism shows accessibility is still a UI afterthought*](https://builtin.com/design-ux/neumorphism-accessibility); CSS-Tricks above). WCAG 1.4.11 requires that the visual cue identifying a control has ≥ 3:1 contrast, and treats a subtle shadow as part of the adjacent colour, so a soft shadow alone never satisfies it ([W3C, Understanding SC 1.4.11 Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html)). Apple's Big Sur leaned toward soft depth in 2020 and kept strong contrast in critical areas.
- **Malewicz's own rules** (2021 update of his article): the surface is made of the background's own material and "pretends to extrude from the background"; pure black or white grounds can't show both shadows, so tint the ground; and an inverted (inset) pressed state alone "has too little contrast to signify a difference", so an active state also needs an outline, a filled icon, an underline or a colour fill.
- **Near-white or near-black grounds** make the highlight or the shade invisible (CSS-Tricks above). This is why Soft moves the ground slightly, and why the owner's dark concept barely showed depth.

## 2. Principles

1. **Ground-matched.** Content surfaces have the ground's colour. Depth is only the shadow pair.
2. **One light source**, at the top-start (mirrored in RTL), for every shadow and every sheen.
3. **Raised = resting and available; inset = chosen or pressed.** That pairing is the signifier, and it is used consistently.
4. **Every control is also identified by something that isn't a shadow:** its words, its icon, or (for fields and selected segments) a ≥ 3:1 edge.
5. **Raise groups, not rows.** A grouped panel is raised; rows inside it stay flat and are separated by space.
6. **Dark mode must show depth.** Lift the ground, add a faint top rim, and push the shade.

## 3. Tokens

The owner liked Soft most in light (concept `g-soft-light`). In dark, the depth must still read, so do better than concept `f-neumorphism`.

| Token | Dark | Light |
|---|---|---|
| ground (adjusted, contract §3.3) | **`#1C1E23`** (lifted from `#0B0C0E`) | **`#E6E7E3`** (one step below `#F3F3F1`) |
| surface1 / 2 (content, sheets) | = ground | = ground |
| surface3 (tiles, wells) | `#22252B` | `#DFE0DC` |
| raised highlight | white α **0.06**, offset (−5, −5), blur 12 | white α **0.85**, offset (−6, −6), blur 14 |
| raised shade | black α **0.62**, offset (6, 6), blur 14 | `#A3A69E` α **0.55**, offset (6, 6), blur 14 |
| controls (buttons, chips, segments) | the same pair at distance 4, blur 10 | the same pair at distance 4, blur 10 |
| top rim (the dark-depth fix) | white α **0.05**, 1 physical px, top edge only | none |
| inset shade | black α 0.60, offset (3, 3), σ 4 | `#9EA199` α 0.45, offset (3, 3), σ 4 |
| inset highlight | white α 0.05, offset (−3, −3), σ 4 | white α 0.90, offset (−3, −3), σ 4 |
| field / selected edge (≥ 3:1) | `#6A6D75` (3.2:1 on ground) | `#7E8188` (3.1:1 on ground) |
| floating composer and dock | raised pair at distance 8, blur 20 | raised pair at distance 8, blur 20 |

Contrast on the adjusted grounds (my check): dark `text1` 15.0, `text2` 6.8, `text3` 5.2, accent 9.4, attention 9.5. Light `text1` 15.1, `text2` 6.2, `text3` 4.7, attention 4.5. **Light accent words are 4.1:1, so the accent guard must run** (`withAccent` after the ground change, contract §3.3). For other theme packs, the ground is moved by the same amount in lightness (about −5 L\* light, +6 L\* dark) keeping its hue, and the shade colour is the ground at about −30 % lightness.

Knob defaults: Corners Round, Depth Raised, Density Comfortable, Edges None. Type: display weight 600, body 400, labels 550.

## 4. Per-part treatment

| Family | Soft treatment |
|---|---|
| F1 panels | raised pair on ground; rows flat, separated by space and no dividers; a pressed row turns inset. |
| F2 needs-you | raised card on ground + **4 dp amber edge at the start** + amber caption + the "Needs you" dot. No amber fill (a fill would break ground-matching). |
| F3 floating | composer shell raised (distance 8); its field **inset**; the send and stop circles raised, then inset on press; dock raised; the active tab inset. |
| F4 modal | sheet = ground with a top highlight edge and a raised handle; dialogs raised; the scrim as today. |
| F5 controls | primary = accent fill + raised pair (shade tinted toward the accent's dark), pressed inset; secondary = raised ground; selected chip, segment or tab = **inset** + accent glyph or word; the segmented track inset, its thumb raised. |
| F6 fields | inset well + the ≥ 3:1 edge; focus adds the accent ring. |
| F7 transcript | prompt bubble raised (shadow on a uniform-radius rrect behind the tailed bubble, techniques §2); code block and log inset; diff rows flat. |
| F8 tiles | inset tile (`surface3`); progress tracks inset. |
| F9 ground | the live background capped at Subtle, drawn on the open ground only. |

## 5. Do's and don'ts for this app

- **Do** pick inset vs raised by meaning (chosen or pressed vs available), never for decoration.
- **Do** tune dark separately. The test is one full phone screen in dark: you must be able to point at every control's edge from arm's length.
- **Do** keep the composer's running glow. It travels the raised shell's outline, and the inset field stays still.
- **Don't** raise every row: it turns a list into bubble wrap. Raise the group.
- **Don't** let a disabled control look like an inset (chosen) one. Disabled = flat, no shadows, words in `text3`, with its reason shown.
- **Don't** rely on the shadow to say "this is a button". Words or icons first; fields and selected segments get the ≥ 3:1 edge.
- **Don't** use Soft under high contrast or Saver: it resolves to Solid.

## 6. Knob combinations

| Knob | Soft behaviour |
|---|---|
| Depth Flat | shadows off → Edges forced to Hairline (contract §2.2), surfaces ground-matched with an edge; inset still marks "chosen" via the edge plus a `surface3` fill |
| Edges Hairline | adds a 1 physical px edge (`hairline` role) to raised surfaces; helps low-vision users |
| Corners Sharp | allowed, but soft shadows on sharp corners look harsh; the picker preview shows it honestly |
| Density Compact | shadow distance 4 → 3 for controls |

## 7. Concepts to match

- `appearance-concepts-2/g-soft-light/concept.png` (light): the owner's favourite. Match its raised preview card, its inset composer field and its inset-selected segment ("Light").
- `appearance-concepts-2/f-neumorphism/concept.png` (dark): **beat it**. Its depth is too faint. Our lifted ground and top rim must make edges visible.
- `appearance-concepts-2/j-style-picker/concept.png`: the "Soft · Raised and pressed" card.
