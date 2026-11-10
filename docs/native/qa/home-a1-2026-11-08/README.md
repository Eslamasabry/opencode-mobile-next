# A1 Conversations home: desktop renders, 2026-11-08

Branch `native/home-a1`. Mockups: `/home/owner/Storage2/mockups/2026-11-08/A1-{glass,solid}-RECOMMENDED.png`, used as a
layout guide only (owner: no icons in boxes, no pill badges).

Rendered by `A1HomeRendersTest` (feature/chats desktopTest) on the CI runner (goldens lane, run 42519041931), 412 x 915 dp
at 2.5x, with the app's bundled agent marks. One screen per picture:

| File | Look |
|---|---|
| `home-a1-glass-dark.png` | Glass, dark: running, needs-you with its answers, five Recent of seven, two projects |
| `home-a1-glass-light.png` | Glass, light |
| `home-a1-solid-dark.png` | Solid, dark |
| `home-a1-solid-light.png` | Solid, light |
| `home-a1-solid-dark-project.png` | Solid, dark, the project filter on oc_app: no project dots, Recent is that project's five latest |
| `home-a1-glass-dark-ar.png` | Glass, dark, Arabic (right to left) |

Desktop draws Glass as its frost fallback; the liquid lens is Android 13+ only.

## Changed from the mockup, and why

- The project tile with the agent badge is gone: the agent's mark stands bare (28 dp); the project is a small dot and its
  name on the second line, shown only when the list spans more than one project.
- Status is plain coloured text under the time (Working green with a 6 dp dot, Needs you amber), never a pill.
- A waiting permission keeps Allow once / Reject (and Always allow where the server keeps grants) under its row, so the
  home can still answer it; the mockup showed only the request line.
- All conversations stops short of the floating + so the two share the last line (the full-width row ran under the +).
