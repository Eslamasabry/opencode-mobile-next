# Shared visual refinement

Finish line: content, supporting metadata and code have distinct readable roles in both themes; section headings and surfaces group related controls without unnecessary visual frames. Existing theme-pack identity and semantic status colors remain coherent.

Non-goal: arbitrary font replacement, recoloring every theme pack, blur on forms, or claiming native frame-time performance from widget captures.

Evidence: exact dev395f374 screenshots and measured color/type/material review in the sibling oc_app-ui-audit-20260909 folder. Original OpenCode secondary text #BCC5BF dark competes with #E3E8E4 content. Code12/17.4 is small for the main review material; globally uppercased tracked11px captions add visual noise.

Candidate contract:
- Page title: bundled Space Grotesk24/30 semibold, -0.25 tracking. Section title remains native16/22 semibold. Body uses native16/23 (large),14/20 (medium); support13/18.
- Code: bundled JetBrains Mono13/19. Inline code follows nearby text rather than shrinking a second time.
- Default OpenCode supporting text: dark #929E97; light #5B6760. Validate >=4.5 against all ordinary surface tiers. Other pack palettes retained.
- Section labels: sentence case13/18 semibold, neutral supporting ink, no forced uppercase or wide tracking, symmetric16dp outer rails.
- Cards: tonal separation and14dp existing radius, no automatic outline on every card. Interactive fields keep their focus/error borders; explicit intentional separators remain.
- Iconography: Phosphor regular24 principal,20 inline; selected navigation uses same-silhouette duotone. Integration is a separate checked dependency/module.

Verification pending candidate integration and actual-font captures. Historical0eabc2d full-suite result does not cover these changes.
