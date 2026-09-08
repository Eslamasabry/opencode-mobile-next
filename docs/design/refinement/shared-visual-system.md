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

Combined icon/theme/navigation/model checks passed (50 cases); actual-font page captures passed (52 cases), and eight theme goldens were refreshed and default light/dark inspected. Candidate analyzer is clean at 5fc0c57. Final full-suite and native verification remain pending; historical 0eabc2d coverage does not apply to the changed candidate.

## Material and upgrade contract

The application explicitly enables Flutter Material 3 (`useMaterial3: true`) on the repository-pinned Flutter 3.47.2. Google Material 3 Expressive is a design direction; this is not a claim that Flutter implements every latest Compose Expressive component. Core controls remain Flutter Material widgets, with centralized visual tokens and framework animations. No third-party animation framework or glass package is added.

The new navigation glass uses Flutter rendering on Android, not Android cross-window blur or a Compose view. Blur is limited to navigation, clipped to its bounds, with a fully opaque accessibility fallback. Native performance still requires candidate APK evidence.

Sources checked 2026-09-09: https://docs.flutter.dev/ui/design/material ; https://design.google/library/expressive-material-design-google-research ; https://api.flutter.dev/flutter/widgets/BackdropFilter-class.html . Dependency versions remain pinned; available updates are evaluated through compatibility checks rather than upgraded wholesale during a visual change.
