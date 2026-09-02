# OpenCode Mobile — product demo video

Remotion 4 project that renders `public/opencode-mobile-demo.mp4` (1920x1080, 30 fps, ~51 s)
and `public/opencode-mobile-demo-vertical.mp4` (1080x1920). No music; sparse sfx from `public/sfx`.

## Re-render

    cd video && npm install
    npm run render            # landscape -> public/opencode-mobile-demo.mp4
    npm run render:vertical   # vertical  -> public/opencode-mobile-demo-vertical.mp4
    npm run studio            # live preview

`remotion.config.ts` points at the sandbox headless Chromium (`/opt/pw-browsers/chromium_headless_shell-1194/...`);
change or remove `Config.setBrowserExecutable` on another machine (Remotion downloads its own otherwise).

## Footage wiring (`src/config.ts`)

- `public/phone-frames/%04d.png` — 756 PNG frames, 585x1266 @30 fps (symlinked from the capture dir; the
  bottom 60 px caption strip is cropped and a fake status bar composited). `PHONE_SCRIPT` holds/plays ranges,
  `PHONE_BEATS` are the captions, `PHONE_FOCUS` the zoom pushes.
- `public/desktop-frames/%04d.png` — 1440x900 Linux capture at a measured 4.12 fps; `DESKTOP.segments`
  picks the excerpts, `DESKTOP.enabled=false` swaps in the placeholder card.
- Fonts in `public/fonts` (Space Grotesk, JetBrains Mono), loaded in `src/fonts.ts`.

Scenes: `Intro` → `PhoneAct` → `Inbox` → `Desktop` → `Outro`, cross-faded by `XFADE` frames (see `src/Demo.tsx`).
