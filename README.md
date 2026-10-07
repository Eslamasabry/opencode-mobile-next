# OpenCode Mobile

[![Android quality gate](https://github.com/Eslamasabry/opencode-mobile-next/actions/workflows/android-quality.yml/badge.svg?branch=master)](https://github.com/Eslamasabry/opencode-mobile-next/actions/workflows/android-quality.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-55D187.svg)](LICENSE)

**Your coding agent, in your pocket. On your computer, or on the phone itself.**

Ask an agent for a change, watch it work, approve what it wants to do, and
review the result — from an Android phone. OpenCode Mobile connects to
[OpenCode](https://opencode.ai) on your computer, or runs OpenCode on the phone
with a built-in Linux. No Termux needed.

**[Download 1.2.0 for Android](https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v1.2.0%2B52/opencode-mobile-1.2.0%2B52.apk)**
· [What's new](docs/releases/v1.2.0+52.md)
· [Get started](#get-started)
· [Get help](SUPPORT.md)

> OpenCode Mobile is an independent community project. It is not built, maintained,
> endorsed by, or affiliated with the official OpenCode team. It is built with
> substantial AI assistance.
> Report problems with **Report a problem** in the app.

| Welcome | Work | This phone ready | Models |
| --- | --- | --- | --- |
| ![Welcome: choose where your agent runs](docs/qa/journeys-2066-2026-09-29/J1-01-welcome.png) | ![Work: conversations and other projects](docs/qa/journeys-2066-2026-09-29/J2-05-work-home.png) | ![OpenCode is ready on this phone](docs/qa/journeys-2066-2026-09-29/J6-03-done.png) | ![Choose a model](docs/qa/journeys-2066-2026-09-29/J3-03-model-picker.png) |

## What you can do

- **Run OpenCode on this phone.** Guided setup installs Ubuntu, Git, Node and
  OpenCode inside the app, checks each step, and can stop and resume. Already
  on Termux? **Move from Termux** copies your projects over and leaves Termux
  untouched.
- **Or use your computer.** Pair with a QR code, or type an address. Use
  [Tailscale](https://tailscale.com) to reach it from anywhere.
- **Chat that keeps up.** Chats open instantly where you left off, replies stream
  as they are written, and one live line on the message box says what the agent
  is doing, with **Stop** right there. The agent's file reads, edits and commands
  fold into one line you can open.
- **Approve before it acts.** Permission requests appear above the message box;
  one tap to allow, another to look closer. Diffs wrap to the phone's width.
- **Inbox across servers.** Everything that needs you, from every server, in one
  list. **While you were away** shows what happened in the meantime.
- **AI Team for whole projects.** Give a goal, a spec and milestones. A planner,
  workers and a checker — each with the model you choose — plan the work, build
  it in one lane or several, check every task, and merge to `dev` with a receipt.
  Nothing reaches `main` until you confirm. Budgets, resume after interruptions,
  and a **Since you were away** digest are built in.
- **Talk instead of type.** Voice is transcribed on the phone; audio never leaves it.
- **Several kinds of server.** OpenCode 1 and 2, plus Claude Code, Pi and Codex
  through a [Paseo](https://github.com/getpaseo/paseo) helper, all behind the same screens.
- **English and Arabic**, light and dark, large text up to 200%.

### AI Team

| Ready on this phone | Review the plan | Merged with receipts | Promote to `main` |
| --- | --- | --- | --- |
| ![AI Team is ready, protected by the phone's Linux sandbox](docs/qa/aiteam-e2e-2026-09-30/run5/a1_ready.png) | ![Review plan with tasks, criteria and roles](docs/qa/aiteam-e2e-2026-09-30/run5/b1_plan.png) | ![Tasks merged into dev, with before and after commits](docs/qa/aiteam-e2e-2026-09-30/run5/g1_merged_receipts.png) | ![Promote dev to main asks for confirmation](docs/qa/aiteam-e2e-2026-09-30/run5/g2_promote_confirm.png) |

Captured from the release candidate on an Android 15 emulator, running a real
project with GLM-5.3. AI Team has not yet been tried on many physical phones —
[tell us how it goes](https://github.com/Eslamasabry/opencode-mobile-next/issues).

## Get started

You need an Android phone running **Android 8 or newer** and a model provider
(an API key, or OpenCode's free model to try it out). Running OpenCode on the
phone itself needs a 64-bit phone.

1. **[Download the APK](https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v1.2.0%2B52/opencode-mobile-1.2.0%2B52.apk)**
   and install it. Android warns about installs from outside the Play Store.
2. **Choose where your agent runs** on the welcome screen:
   - **On this phone** — tap it and follow the setup. The first setup downloads
     Ubuntu and the tools, so Wi-Fi is best. Phones with about 2 GB of memory
     work, slowly.
   - **On my computer** — with OpenCode 2, run `opencode2 pair` and scan the
     QR code. For OpenCode 1 or an address, follow the steps in the app.
   - **Just show me** — a simulated conversation, no server needed.
3. **Add a provider key** in Settings → Providers if you want models beyond the
   free one. Then start a conversation.

To turn on AI Team: open a project, choose **AI Team**, then **Turn on AI Team on
this phone**, and pick a model for each role.

### If something doesn't work

- **`localhost` on the phone means the phone itself.** For a computer, use its
  Tailscale or network address.
- **No models:** add a provider on the server, then **Reload providers** in the
  model sheet.
- **The update won't install:** the new APK must be signed with the same key as
  the one installed (see [Updating](#updating)). Don't uninstall to fix this —
  that deletes the phone's projects.
- More in [SUPPORT.md](SUPPORT.md) and the in-app guide.

## Compatibility

| | Status |
|---|---|
| Android 8+ | Supported — 1.2.0 |
| OpenCode 1 (1.18.x) | Supported |
| OpenCode 2 (beta and stable 2.0) | Supported |
| Claude Code, Pi, Codex (via Paseo) | Supported |
| AI Team | New; proven on Android 14/15 emulators, physical phones still being tried |
| Linux and Windows desktop | Experimental CI builds |
| Web | In development |
| iOS / macOS | Not available |

The app connects only to servers you choose. It is not a hosted service and
does not include a model subscription.

## Updating

Android only updates an app in place when the new APK has the same signature.
Public releases from this page are signed with
`842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C`, so 1.2.0
installs over 1.1.0 and earlier releases and keeps your saved servers, sign-ins and settings. You
can check the installed signer in **Settings → About**.

Projects on the phone's built-in server live inside the app: **Clear storage**
or uninstalling deletes them. Android's Clear storage opens an app page first
that lets you export them.

## Your data

The app talks to the servers you choose and to nobody else about your work.
Passwords stay in Android's keystore, voice stays on the phone, and diagnostics
are only sent when you send a report yourself, with secrets removed. Provider
logos come from Google's public favicon service by domain name. Details in
[PRIVACY.md](PRIVACY.md) and in the app under Settings → Privacy.

## For developers

- [docs/technical-overview.md](docs/technical-overview.md) — architecture, the
  server protocols, building and releasing
- [CONTRIBUTING.md](CONTRIBUTING.md) — the pinned toolchain, checks and code boundaries
- [AGENTS.md](AGENTS.md) — working notes for coding agents in this repo
- [SECURITY.md](SECURITY.md) — report vulnerabilities privately, never in a public issue

Quick checks (with the pinned Flutter from CONTRIBUTING.md):

```bash
flutter pub get
flutter analyze
tool/qa/run_tests_fast.sh    # full suite in parallel shards
```

CI runs analysis, Android lint, the test suite in six parallel shards and a
release build on every push to `dev` and `master`, in about ten minutes.

## Licence and thanks

[MIT](LICENSE). The app bundles Geist and Geist Mono (OFL-1.1), sherpa-onnx
(Apache-2.0), ONNX Runtime and the Whisper models (MIT), and the packages in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Thanks to the OpenCode team,
who made the agent this app is a window onto.
