# Suggested GitHub repository settings (for the owner)

The owner changes repository settings; nothing here has been applied. Read
from `gh repo view` on 2026-10-07.

## Description (About)

Current:

> Unofficial Android client for OpenCode: follow live sessions, approve tools,
> review diffs, and optionally run on-device with Termux.

Suggested (under 350 characters, leads with the searchable promise, no
Termux, since setup now uses the app's own Ubuntu):

> Run Claude Code and OpenCode on your Android phone. No computer, no Termux.
> Or follow, approve and review the agents running on your computer.

## Topics

Current: ai-agent, android, coding-agent, dart, developer-tools, flutter,
linux, opencode, self-hosted, termux.

Suggested (20 maximum):

- Keep: `ai-agent`, `android`, `coding-agent`, `developer-tools`, `flutter`,
  `opencode`, `self-hosted`
- Add: `claude-code`, `codex`, `gemini-cli`, `ai-coding`, `coding-assistant`,
  `android-app`, `proot`, `ubuntu`, `mobile-ide`
- Remove: `termux` (no longer how the app runs agents), `dart` and `linux`
  (generic; optional)

## Homepage

Empty today. Set it to the releases page until a landing page exists:
`https://github.com/Eslamasabry/opencode-mobile-next/releases/latest`.

## README first screen (done in the repo)

The README now leads with the one-line promise, install badges (Download APK,
latest release, total downloads, CI, licence) and six screenshots. The
screenshots live in `fastlane/metadata/android/en-US/images/phoneScreenshots/`
so the README and a store listing share one set. Each is a real capture from
an Android emulator during QA, resized to JPG:

| File | Source capture |
|---|---|
| `1_welcome.jpg` | `docs/qa/journeys-2066-2026-09-29/J1-01-welcome.png` |
| `2_setup_on_phone.jpg` | `docs/qa/emulator-qa-claude-2026-09-28/73-this-phone-setup-progress.png` |
| `3_chat.jpg` | `docs/qa/emulator-qa-claude-2026-09-28/49-chat-in-flight.png` |
| `4_approve.jpg` | `docs/qa/paseo/03-permission-review.png` |
| `5_models.jpg` | `docs/qa/journeys-2066-2026-09-29/J3-03-model-picker.png` |
| `6_ai_team.jpg` | `docs/qa/aiteam-e2e-2026-09-30/run5/g1_merged_receipts.png` |

The welcome capture predates FB3 (2026-10-07), which moved "On this phone"
first with "Recommended". Replace `1_welcome.jpg` with a device capture of
the new welcome after the next release.
