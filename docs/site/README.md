# Static product page

`index.html` is a standalone page with local styles, fonts, logo and screenshots.
It works without JavaScript (one small inline script makes the hero notification answerable: Allow once and Reject switch the notification and the phone screen; without it the request and its result simply show one after the other; a second inline script sends a cookie-free visit count to GoatCounter at `opencode-mobile.goatcounter.com`, only on the published `github.io` host and never with Do Not Track) and needs no package install or build step. Open `index.html` directly
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
- `assets/og-image.jpg` (1200x630) is rendered in headless Chromium from the
  page's own look: the logo, the hero headline, the `npm install` notification
  and the top of the `approve` capture, in the bundled Roboto Flex.
- The site uses the project's own logo. Fonts are self-hosted, so the page makes
  no font requests: `assets/fonts/RobotoFlex-latin.woff2` (Roboto Flex 3.200,
  Latin subset) and `assets/fonts/NotoSansArabic-arabic.woff2` (Noto Sans
  Arabic 2.012, Arabic subset), both the Google Fonts woff2 files, SIL OFL 1.1;
  copyrights and the license are in `assets/fonts/OFL.txt`. Commands use the
  system monospace font.

## Design (direction A, "Needs you", 2026-10-31)

The page is built around what makes the app different: the phone tells you
when the agent needs you, and you answer there. It uses the light grey of
Android's notification shade (`#E7EAEF`), white cards, the app's own "Allow
once" green (`#167A46`) and an amber dot only where something waits. Type is
Roboto Flex, wide and heavy for headlines and normal width for text, and
Noto Sans Arabic for the Arabic section.

The one memorable element is the hero notification, "Run npm install in
neon-breaker?". Allow once turns it into "Ready" and the phone beside it
switches from the conversation to the game in the preview. On wide screens the
card sits over the empty part of the chat, never over its title or messages;
the hero phone is cropped above the chat's own request card and chips. The
`approve` and `preview` captures are used there, so the demo section shows the
other four screens. Everything else stays quiet: a plain feature list instead
of cards, no stats strip, no eyebrows, and no scroll-reveal animation. The
design record and renders are kept outside the repository
(`/home/eslam/Storage2/wt/site-redesign-2026-10-31/`).

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
