# Glass: frosted and see-through (glassmorphism)

*Part of [design schools](../README.md). Contract: [contract.md](../contract.md). Techniques: [flutter-techniques.md](../flutter-techniques.md) §1.*

**One line:** frosted panes the live background shows through, with restraint. The floating layer has a real blur; content has translucency without blur; and every word stays at AA over the brightest backdrop.

**Picker subtitle:** "Frosted, see-through".

## 1. Where it comes from

- **The name and recipe.** Michał Malewicz named "glassmorphism" in November 2020: background blur that gives frosted transparency, floating layers stacked in depth, vivid colour behind that shows through, and a subtle light border on the pane ([Malewicz, *Glassmorphism in user interfaces*, Hype4 Academy](https://hype4.academy/articles/design/glassmorphism-in-user-interfaces); first published on UX Collective, Nov 2020). His own caveat: it "can work if used scarcely", and objects must keep their structure and readability without the decoration.
- **Ancestors in design systems.** Windows Aero (2006) and Apple's iOS 7 (2013) used translucency long before the name. Microsoft's **Acrylic** is the most complete written recipe: background → blur → exclusion blend → tint colour at a tint opacity → noise. Its rules matter here: use it on transient and supporting surfaces, **don't** place several acrylic panes edge to edge (a visible seam), **don't** put accent-coloured text on acrylic, and fall back to a solid colour in high contrast, with Transparency off, in **Battery Saver**, and on low-end hardware ([Microsoft Learn, Acrylic material](https://learn.microsoft.com/en-us/windows/apps/design/style/acrylic)). Apple's materials come in thicknesses from ultra-thin to thick, with vibrant foreground colours and a Reduce Transparency fallback ([Apple HIG, Materials](https://developer.apple.com/design/human-interface-guidelines/materials)).
- **Usability research.** NN/g: translucency creates depth and separation; contrast varies across a pane as the backdrop changes; "more background blur is better, especially with intricate backgrounds"; meet contrast against every backdrop; let users reduce transparency ([NN/g, Glassmorphism: Definition and Best Practices](https://www.nngroup.com/articles/glassmorphism/), Megan Brown, 2024). NN/g's critique of iOS 26's Liquid Glass shows the failure mode: controls over noisy content, reduced legibility, and spectacle over content ([NN/g, Liquid Glass Is Cracked](https://www.nngroup.com/articles/liquid-glass/), Raluca Budiu, 2025).
- **Accessibility write-up.** [Axess Lab, Glassmorphism meets accessibility](https://axesslab.com/glassmorphism-meets-accessibility-can-frosted-glass-be-inclusive/) covers contrast over moving backgrounds and the need for an opaque fallback.
- **The owner's picks.** `owner-glass-home.png` and `owner-glass-music.png` (local): restrained frosted panes over a photo or gradient, thin light rims, and opaque text and controls. The owner rejected *liquid* glass (refraction, `feat/true-liquid-glass`). This is plain frosted glass.

## 2. Principles

1. **Glass on the floating layer, translucency on content.** Real blur only where sharp content moves behind (bars, composer, dock, one sheet). Content panels are translucent tints with no blur ([techniques §1](../flutter-techniques.md#1-glass-blur-behind)).
2. **Never glass on glass.** A sheet over a blurred bar gets its own backdrop group, and a pane never sits inside another pane's blur.
3. **Words never sit on raw translucency.** Each pane's tint is opaque enough for AA over the **worst-case backdrop**, and `text3` is remapped to `text2` on translucent fills.
4. **Accent stays opaque.** The primary action is an opaque accent fill. Accent-coloured *words* (links) don't go on glass; they use `text1` + underline there.
5. **Edges are light, not lines.** A 1-physical-pixel light rim on top and a darker one at the bottom (visual language §7), never a soft glow.
6. **It needs something behind it.** With the live background off, Glass drops the blur and becomes a translucent solid.

## 3. Tokens

Composited contrast was checked with the WCAG formula against an assumed worst-case backdrop (accent at 55 % over the ground in dark, light accent at 35 % over the ground in light). The contract test must re-run this with the real live-style palettes ([contract.md](../contract.md) §7.1).

| Token | Dark | Light |
|---|---|---|
| ground | theme ground (`#0B0C0E`) under the live background | theme ground (`#F3F3F1`) under the live background |
| content tint (F1, F7 bubbles) | `surface2` `#1B1C20` at **α 0.72**, no blur | `#FFFFFF` at **α 0.70**, no blur |
| floating (F3) | `#1B1C20` at **α 0.76**, **blur σ 16**, bounded | `#FFFFFF` at **α 0.74**, **blur σ 16**, bounded |
| modal (F4) | `#1B1C20` at **α 0.82**, **blur σ 20**, own group | `#FFFFFF` at **α 0.82**, **blur σ 20** |
| top rim / bottom rim (1 physical px) | white α 0.10 / black α 0.35 | white α 0.85 / black α 0.06 |
| edge (Edges = Hairline) | white α 0.08 | black α 0.06 |
| scrim under a glass sheet | black α 0.40 (the blur separates; today 0.58) | black α 0.32 |
| floating shadow | today's tight drop | today's tight drop |
| text remap on translucent fills | `text3` → `text2` | none needed (`text3` 4.94:1 at α 0.70) |
| code, log, terminal, diff (F7) | **opaque** `surface1` | **opaque** `surface1` |

Worst-case contrast at those values (my check): dark content α 0.72 gives `text1` 11.2:1, `text2` 5.2:1, accent 7.0:1, attention 7.1:1, and `text3` only 3.9:1, hence the remap. Light content α 0.70 gives `text1` 15.9:1, `text2` 6.5:1, `text3` 4.9:1, and accent words 4.3:1, hence "no accent words on glass".

Knob defaults: Corners Round, Depth Flat, Density Comfortable, Edges Hairline (the rims). Type: Geist as Solid, with body weight never below 400.

## 4. Per-part treatment

| Family | Glass treatment |
|---|---|
| F1 panels | content tint, rims, no blur, no shadow. Rows inside are transparent; the pressed row is tint α +0.08. |
| F2 needs-you | amber at α 0.12 over the content tint, `attentionLine` as the rim colour, today's 4 dp ring. The caption keeps `attention`. |
| F3 floating | the only blurred surfaces: top pill, composer shell, dock and rail, jump pill (≤ 3 live at once). The composer's field sits inside the shell's blur with no second blur. |
| F4 modal | one blurred pane per modal, in its own `BackdropGroup`. A confirmation's consequences panel inside the sheet is an opaque `inset` (no glass on glass). |
| F5 controls | primary opaque accent; secondary tint α 0.16 + rim; selected = brighter tint + 1 physical px accent rim; chips are content tints. |
| F6 fields | tint α 0.5 + rim; focus = accent rim + the focus ring. |
| F7 transcript | prompt bubble = content tint (no blur); reply plain text; code surfaces opaque. |
| F8 tiles | tint α 0.2; dividers are hairlines. |
| F9 ground | the live background as chosen (Glass needs it); without it, no blur anywhere and floating α +0.1. |

## 5. Do's and don'ts for this app

- **Do** keep the blur count at 3 or fewer on screen. The chat's transcript scrolls under the top bar and the composer, and those two blurs are the whole effect.
- **Do** make the running composer glow sit *on* the glass rim (the rim becomes the glow path). Don't blur the glow.
- **Do** frost the dock and the composer even when the live background is subtle. Scrolling transcript text behind them is the real backdrop.
- **Don't** blur list rows, chips, bubbles or cards. It costs per row and looks the same as a tint.
- **Don't** put amber on a large glass area. The needs-you tint stays at α 0.12, and the card's words carry the meaning.
- **Don't** put the code block, diff or terminal on translucency. Technical text needs an opaque ground.
- **Don't** let a glass pane sit over a platform view (the web preview).
- **Don't** use Glass under Saver or high contrast: it resolves to Solid ([contract.md](../contract.md) §6).

## 6. Knob combinations

| Knob | Glass behaviour |
|---|---|
| Depth Raised | content panels gain the tight floating drop (they "float" over the wash) |
| Edges None | rims removed on content (kept on floating and modal panes, which must separate from what scrolls under them) |
| Corners Sharp | allowed; rims follow the shape |
| Density Compact | contract §3.7 |

## 7. Concepts to match

- `appearance-concepts-2/e-glass/concept.png` (dark): the reference for the floating pill tabs, the frosted preview card, and the rims.
- `appearance-concepts-2/k-make-it-yours/concept.png`: Glass selected in the tour; the frosted card and step list.
- `glass-inspiration/owner-glass-home.png`, `owner-glass-music.png`: the restraint level (light and colourful backdrops).
- Watch the concept's weak spot: its secondary words sit too faint over the aurora. Ours must not (the `text3` → `text2` remap).
