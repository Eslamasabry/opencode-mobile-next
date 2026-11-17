# IzzyOnDroid: inclusion check and request text

Checked 2026-10-15 against https://izzyondroid.org/docs/general/AppInclusionPolicy/
and the Codeberg tracker. Nothing was submitted.

## Verdict

**Do not expect inclusion. The AI policy alone is decisive.**

The policy says (AI policy section, quoted): "We are strongly opposed to apps
which are fully or in part created by generative AI tools." "Vibe-coded apps
will be rejected." "Apps acting as front-end for LLMs as outlined in point 6
above, or integrate with such services, will be rejected." "Readme, Changelogs
and similar documentation files are allowed to include LLM-generated texts,
but the code itself should be free of it." "Any lack of transparency
discovered by us, can lead to the project being degraded to rejected state."

Our README says "Built with substantial AI assistance", and the app is a
front end for coding agents that call LLMs. Both halves of the rule apply.

Evidence from the tracker (https://codeberg.org/IzzyOnDroid/repodata/issues,
read through the Codeberg API on 2026-10-15): the inclusion form has an "AI
Tools Usage" section with an "Assistance Level" dropdown, the tool names and
"What did the tools help with"; requests are labelled `llm/substantial`,
`llm/moderate` or `llm/minimal`. Counts of issues by label and outcome:

| Label | Issues | Accepted | Declined | Other |
| --- | --- | --- | --- | --- |
| `llm/substantial` | 350 | 0 | 324 | 26 (withdrawn 8, closed without status label 18) |
| `llm/moderate` | 37 | 25 | 3 | 9 (open, abandoned, waiting) |
| `llm/minimal` | 31 | 22 | 2 | 7 |

(Counts are for the labels as applied; the sample is the tracker's own
labelling. "Accepted" is the `status/accepted` label.) A request that says the
tools were used substantially has been declined 324 times and accepted never.
Being honest about it (which the policy requires) therefore means declined.
Hiding it is not an option: it would break the policy's transparency rule
and the README already says it.

If the owner wants to try anyway, the request text is below. It states the
assistance level truthfully. I recommend not spending the owner's time on it.

## Policy check, point by point

| Policy point | Our state | Result |
| --- | --- | --- |
| Free and open source, OSI/FSF licence | MIT (`LICENSE`); GitHub reports MIT | pass |
| Source on Codeberg, GitLab or GitHub | GitHub, public | pass |
| No proprietary components (some tolerated if essential) | See next table | partial |
| No trackers (ads, analytics) | none found in `pubspec.yaml` or `android/app/build.gradle.kts`; PRIVACY.md says no analytics; an Aptabase plan exists in `docs/aptabase-telemetry-plan.md` but nothing in `pubspec.yaml` or `lib/` mentions it | pass |
| Cleartext traffic avoided | `android:usesCleartextTraffic="false"` and a `networkSecurityConfig` in `AndroidManifest.xml` | pass |
| Target end users | an app for developers; end users | pass |
| No downloading of executable binaries without explicit opt-in consent ("it must not be harder to decline than to accept") | guided setup downloads an Ubuntu rootfs and installs packages; Shorebird patches (Dart code) are checked and downloaded from the About screen path | needs work |
| Unique packageName | `io.github.eslamasabry.opencode_mobile` | pass |
| Metadata in the repo in Fastlane structure | present: title, short and full description, six phone screenshots, Arabic copy | partial |
| Release APK signed with a release key; not debuggable | signed release key; Shorebird release build | pass (unverified here; I did not run apksigner) |
| Size: "usually reserves up to 30 megabytes per app" | 1.4.0 APK is about 100 MB | **blocker** |
| Tagged releases with APK attached | `v1.4.0+54` on GitHub with `opencode-mobile-1.4.0+54.apk` and `SHA256SUMS` | pass |
| AI policy | substantial AI assistance; LLM front end | **blocker (decisive)** |

## Proprietary and unusual components (what I grepped)

Searched `pubspec.yaml`, `android/app/build.gradle.kts`,
`android/settings.gradle.kts`, `shorebird.yaml`, `AndroidManifest.xml`.

| Component | Where | Issue |
| --- | --- | --- |
| Shorebird code push (`shorebird_code_push: 2.0.7`, `shorebird.yaml`, release builds with Shorebird's Flutter fork 3.47.1) | `pubspec.yaml` line 87; `docs/technical-overview.md` | A network service run by a company; patches are Dart code downloaded after install. The updater library is open source (Apache-2.0 and MIT per a search result; verify), the patch service is not self-hostable by us. Needs opt-in consent per the policy, and an IzzyOnDroid build would have to turn it off. |
| ML Kit barcode model, bundled (`mobile_scanner` wraps `com.google.mlkit:barcode-scanning`) | comment block in `pubspec.yaml` lines 55 to 72 | Proprietary Google library bundled in the APK. Not Play Services and not a download, but proprietary. Used for QR pairing only. |
| Firebase, Google Play Services, Crashlytics, analytics SDKs | none found | clean |
| proot, its loader, the Rust phone engine (`libaiteam_*.so`) as prebuilt native libraries | `android/app/build.gradle.kts` comments near `jniLibs` | Prebuilt binaries inside the APK. IzzyOnDroid prefers builds that can be reproduced; these must be built from source in their pipeline or documented. |
| `com.github.termux.termux-app:terminal-emulator` v0.118.3 via JitPack | `android/app/build.gradle.kts` line 167 | Open source, Apache-2.0 (the one Apache exception inside GPLv3 termux-app, documented in `THIRD_PARTY_NOTICES.md`). No issue; a JitPack-built artifact would have to be built from source by a reviewer's pipeline. |
| On-device speech models (`sherpa_onnx`, Whisper, MIT) | `pubspec.yaml` | open licences; models are downloaded on demand |

## Reproducibility

The policy page has no separate reproducibility rule; it says it prefers
tagged releases because they make reproducible builds possible. IzzyOnDroid
runs its own verification builders (https://izzyondroid.org/docs/reproducible-builds/).
We cannot offer that today: releases are built with Shorebird's Flutter fork
on the maintainer's setup, and the Shorebird release process signs and
registers the build with Shorebird's service. A stock-Flutter build would not be
byte-identical to the released APK. This is a blocker for the preferred
path, not the stated policy.

## Small gaps in the repo (fixable, not decisive)

- No Fastlane changelog files for versionCode 53 and 54:
  `fastlane/metadata/android/en-US/changelogs/` ends at `52.txt` (the Arabic
  folder also ends at 52).
- No `icon.png` under `fastlane/metadata/android/en-US/images/` (only
  `phoneScreenshots/`).
- Tag name `v1.4.0+54` contains both the versionName and the versionCode; the
  reviewer checklist asks whether tags "match either versionName or
  versionCode", which this probably satisfies, but IzzyOnDroid tooling may want
  a plain `1.4.0` or `54`.
- Short description is 67 characters (limit 80); title 15 characters.

## What the request would need to say (honest draft)

Where: the Codeberg tracker https://codeberg.org/IzzyOnDroid/repodata/issues,
issue template "App Inclusion Request" (title `[AppRequest] <App Name>`;
labels `app-request`, `needs/apk-scan`). The form requires a Codeberg account;
the owner must file it. Fields below are the form's own.

Title: `[AppRequest] OpenCode Mobile`

Guidelines checkboxes: tick "I am the developer of the app." Tick "The app
complies with the App Inclusion Policy" **only if** the owner accepts that
this is false for the AI policy; the honest option is to leave it unticked and
say why under Further Notices. Tick "The app is not already listed" (it is
not). Tick "The Fastlane folder is available".

- Link to the source code: https://github.com/Eslamasabry/opencode-mobile-next
- Link to app in another app store: none (GitHub releases only; not on Play or F-Droid)
- License used: MIT
- Categories: Development (and Connectivity)
- Summary: Runs coding agents (OpenCode, Claude Code) on the phone in an
  Ubuntu sandbox inside the app, or connects to agents on a computer.
- Description:

      OpenCode Mobile is an independent, MIT-licensed Android app for working with coding agents. Guided setup installs an Ubuntu sandbox (proot) inside the app and runs OpenCode and Claude Code there, with no computer and no Termux. It can also connect to OpenCode on a computer by QR code or address. Features: streaming chat with Stop, per-request permission approvals, diffs, Files, Changes and a terminal for Claude Code conversations, home-screen widgets (Allow, Reject, Stop, Steer, voice), a live background, and an AI Team mode. No ads; usage and crash reports are off until you turn them on; passwords stay in the Android keystore; the user brings their own model provider. Not affiliated with the OpenCode team or Anthropic.

- Build instructions (from README and `docs/technical-overview.md`; the owner
  must confirm they work from a clean checkout before sending): Flutter 3.47.1
  (pinned), JDK 17, Android SDK API 37; `flutter pub get`; the release build is
  `flutter build apk --release`. Say plainly that the official releases are
  Shorebird releases built with Shorebird's Flutter fork, and that the
  Shorebird updater and the bundled ML Kit barcode model would need to be
  removed or replaced for a build that satisfies the policy. This is not yet
  done; do not claim it is.
- AI Tools Usage, Assistance Level: **Substantial** (the README states
  "Built with substantial AI assistance"; AGENTS.md describes coding agents
  working in worktrees). AI tool(s): Claude Code, OpenAI Codex (per
  AGENTS.md and the repo's workflow notes; the owner should confirm the
  exact list before sending). What the tools helped with: architecture, UI,
  implementation, tests and documentation (the owner should describe their own
  role and review process accurately).
- AI Accountability checkboxes: the owner ticks only those that are true.
- Further Notices: The app is a front end for coding agents that call LLMs, and
  was built with substantial AI assistance, so I expect this to fall under the
  AI policy. I am filing it so the decision is explicit. The release APK is
  about 100 MB, above the usual 30 MB. If either is a firm no, please decline
  and I will distribute through GitHub releases.

## Better routes for Android-native distribution without these blockers

- A self-hosted F-Droid repository (what dzianisv/opencode-mobile does:
  https://dzianisv.github.io/opencode-mobile/fdroid/repo). No policy gate,
  no size cap, but users must add the repo; it adds a signed index and
  an `fdroid` toolchain to the release process.
- Obtainium users can already add the GitHub repo URL; the releases attach
  a signed APK and SHA256SUMS. A one-line "Install with Obtainium" in the README
  costs nothing. (Obtainium works from GitHub releases; I did not test our tag
  format against it.)
