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

## Store listing metadata (FG3)

`fastlane/metadata/android/` holds the listing text in the fastlane layout
that F-Droid, IzzyOnDroid and Google Play read:

- `en-US/` and `ar/`: `title.txt`, `short_description.txt` (under 80
  characters), `full_description.txt` (under 4,000) and
  `changelogs/<versionCode>.txt` (under 500 each) for every published
  version with notes in `CHANGELOG.md` or `docs/releases/` (1.0.32+33 and
  the dev pre-releases have none): 34 (1.0.33), 35 (1.0.34, later marked broken, said so), 49
  (1.0.43), 50 (1.0.44), 51 (1.1.0) and 52 (1.2.0). The version code is the
  build number after `+` (`versionCode = flutter.versionCode`). Sources:
  `CHANGELOG.md` and `docs/releases/`.
- `en-US/images/phoneScreenshots/`: the six real captures listed above. `ar/`
  has no screenshots of its own, so stores fall back to `en-US`. Arabic
  captures would be a follow-up.
- Not included yet: a 512 px icon and a feature graphic
  (`images/icon.png`, `images/featureGraphic.png`). A new release adds
  `changelogs/<its versionCode>.txt` in both languages.
