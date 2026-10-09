# Static product page

`index.html` is a standalone page with local styles, fonts, logo and screenshots.
It needs no JavaScript, package install or build step. Open `index.html` directly
with a `file://` URL for an offline preview, or serve this directory locally:

```bash
python3 -m http.server 8000 --bind 127.0.0.1 --directory pages
```

Open `http://127.0.0.1:8000` after starting the server from the repository root.
Stop that server with Ctrl+C in the same terminal. The Download and source links
navigate to GitHub and need a network connection; the page itself uses local
rendering assets. An Arabic section declares its own language and RTL direction.

Run the focused structural check from the repository root:

```bash
python3 tool/qa/test_site.py
```

The standard-library check covers asset paths and existence, no-JavaScript
render dependencies, screenshot use, alternative text, anchors, language,
viewport, keyboard-focus styling and reduced-motion styling. Passing these
checks does not establish browser rendering, accessibility audit results or
physical-device behavior. Check the page visually at narrow and wide widths,
with keyboard navigation and reduced motion, before claiming those results.

## Screenshot provenance

The six page screenshots are local copies from
`fastlane/metadata/android/en-US/images/phoneScreenshots/`:

- `1_welcome.jpg`
- `2_setup_on_phone.jpg`
- `3_chat.jpg`
- `4_approve.jpg`
- `5_models.jpg`
- `6_ai_team.jpg`

The page uses `4_approve.png`, a privacy-redacted derivative of `4_approve.jpg`.
The two personal filesystem paths are masked; the original source is unchanged.
The other five screenshots are exact copies. No account values are shown. The
screenshot set documents the app's existing screens; it does not certify every
pictured runtime or workflow.
The site uses the project's own logo; the bundled font license accompanies the
local font assets.

Do not produce an animated demonstration from interim device runs. Any future
GIF should come from the final successful, approved device verification run and
receive a privacy review before inclusion.

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

The 8 static-page checks and 12 mocked recorder checks pass:

```bash
python3 -m unittest tool.qa.test_site tool.qa.test_record_demo_gif
```

No adb, ffmpeg or recording was run during this slice. The source screenshot
set and redacted image were visually reviewed. Browser rendering was deferred
because the shared host had under 500 MB available memory and full swap.
The full application test pass and final device capture belong to integration.
