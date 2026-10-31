# Static product page

`index.html` is a standalone page with local styles, fonts, logo and screenshots.
It works without JavaScript (one tiny inline script only adds scroll-reveal animation, content is visible by default; a second inline script sends a cookie-free visit count to GoatCounter at `opencode-mobile.goatcounter.com`, only on the published `github.io` host and never with Do Not Track) and needs no package install or build step. Open `index.html` directly
with a `file://` URL for an offline preview, or serve this directory locally:

```bash
python3 -m http.server 8000 --bind 127.0.0.1 --directory docs/site
```

Open `http://127.0.0.1:8000` after starting the server from the repository root.
Stop that server with Ctrl+C in the same terminal. The Download and source links
navigate to GitHub and need a network connection; the page itself uses local
rendering assets. An Arabic section declares its own language and RTL direction.

Run the focused structural check from the repository root:

```bash
python3 tool/qa/test_site.py
```

The standard-library check covers asset paths and existence, that the page works
without JavaScript, that every screen is used once, the video's controls, site weight, alternative text, anchors, language,
viewport, keyboard-focus styling and reduced-motion styling. Passing these
checks does not establish browser rendering, accessibility audit results or
physical-device behavior. Check the page visually at narrow and wide widths,
with keyboard navigation and reduced motion, before claiming those results.

## Media provenance

- `assets/video/launch-720.mp4`: the 64-second launch video
  (`launch-footage/motion/out/v4-vertical-m2.mp4`, 1080x1920) re-encoded with
  ffmpeg to 720x1280, H.264 High, yuv420p, `+faststart`, CRF 23 capped at 1 Mbit/s,
  AAC 128k (about 4.9 MB). `poster.jpg` is the frame at 31 s. The video never
  autoplays; it has controls and plays with sound only when pressed.
- `assets/screens/*.webp` (2.1.0, 2026-10-31), 480x1010 unless noted:
  `build-chat`, `approve`, `preview`, `diff` and `terminal` are the real screens,
  dark, rendered with demo data by the `tool/capture` harness at 412x915 dp, 2x
  (`build-chat` by a one-off scene with the same fixtures),
  scaled to 1010 px high and padded at the sides with the app's background;
  `models` is a dark emulator capture of 2.1.0 (status bar cropped off). The three
  `widget-*` images are rounded crops of the 2.1.0 home-screen widgets (light) on
  the emulator (`widget-list` 480x296, `widget-small` 346x447, `widget-ready`
  560x346). All use demo data from the synthetic `neon-breaker` and
  `weather-app` projects; no account values, errors or status bars are shown.
- `assets/og-image.jpg` is composed from the preview capture, the logo and the
  bundled Geist font.
- The site uses the project's own logo; the bundled font license accompanies the
  local font assets.

Deployment: `.github/workflows/pages.yml` publishes `docs/site` to GitHub Pages
on pushes to `master` that touch `docs/site/**`, or by manual dispatch. The site
URL is https://eslamasabry.github.io/opencode-mobile-next/ (Pages must be set to
"GitHub Actions" as its source in the repository settings).

## Final-pass demo recorder

`tool/qa/record_demo_gif.py` captures a 60–90 second, operator-driven demo on
`emulator-5554` with adb screenrecord, then makes a cropped GIF using ffmpeg.
It prints a plan unless `--record --attest-synthetic-demo` is supplied. Use only
synthetic/demo data; keep sign-in, account values and notifications off screen.
The operator shows guided installation/setup, picks an agent, and approves a
harmless tool at the printed cues. It does not tap UI coordinates or use a real
Claude account.

For the coordinator's final device pass only, from the repository root:

```bash
python3 tool/qa/record_demo_gif.py --record --attest-synthetic-demo --duration 75
```

An optional `--candidate-apk /absolute/path/to/approved.apk` installs that APK
before capture and restores normal APK 2199 afterward, including on failure.
Without it, the existing installed app is used. The shared emulator flock is
held through capture, conversion and cleanup. No app data is cleared.
Recordings stay under `~/.local/share/opencode/private-demo-recordings/`, with
a manual review marker; inspect both MP4 and GIF before any separate export.
The script intentionally does not copy recordings into this website.

## Slice verification

The 10 static-page checks and the mocked recorder checks pass:

```bash
python3 -m unittest tool.qa.test_site tool.qa.test_record_demo_gif
```

The 2.0 page was also rendered in headless Chromium at 390 px and 1440 px
wide (renders kept outside the repository).

## Publishing and search (2026-10-15)

The page uses only relative paths, so it works under
`https://eslamasabry.github.io/opencode-mobile-next/` when this folder is the
published root. GitHub Pages can serve only `/` or `/docs`, so publishing needs
a Pages workflow that uploads `docs/site` (not set up here; repository settings
are the owner's). `robots.txt` and `sitemap.xml` are in this folder; search
engines read `robots.txt` only at a host root, so submit `sitemap.xml` in Search
Console instead.

Additional images:

- `assets/og-image.jpg` (1200x630 social card) is composed from the logo and
  `1_welcome.jpg`.

The page also carries JSON-LD (`SoftwareApplication`, data only, no script
execution) and light and dark colour schemes. `tool/qa/test_site.py` allows the
JSON-LD block.
