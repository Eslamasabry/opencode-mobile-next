# Competitors and alternatives

All numbers read from the GitHub API or the project's own page on **2026-10-15**
(stars change daily). "Release downloads" is the sum of `download_count` over
the release assets GitHub reports (all platforms, all assets, all versions), so
it is an upper bound on installs from GitHub and says nothing about store
installs. Where a project has no GitHub releases it says so. No install counts
for Google Play or the App Store were available to me; none are quoted.

Our own numbers on the same date: 7 stars, release downloads per version 6
(1.4.0, 1 day old), 20, 29, 29, 26, 38 (1.0.43). About 34 unique visitors per 2
weeks (owner's traffic data).

## A. Mobile clients for OpenCode

| Project | What it does | Stars | License | Platforms and distribution | Release downloads | Last push |
| --- | --- | --- | --- | --- | --- | --- |
| dzianisv/opencode-mobile | Android client for a self-hosted OpenCode server (Tailscale); stream sessions, review diffs, approve tool calls; "Try a Demo" mode needs no server | 228 | MIT | Android; Google Play, a self-hosted F-Droid repo, direct APK | 5,289 across 25 releases | 2026-08-20 |
| doza62/opencode-mobile | React Native client for iOS, Android and web; installs an OpenCode plugin (`npx opencode-mobile install`) and uses a relay with push notifications | 304 | MIT | iOS, Android, web (Expo); no GitHub releases | none | 2026-02-25 (stale) |
| theblazehen/P4OC ("Pocket for OpenCode") | Native Android client: chat, files with highlighting, diffs, embedded terminal (Termux-based) | 88 | GPL-3.0 | Android; Google Play and GitHub releases | 2,316 across 15 releases | 2026-10-11 |
| alvarolorentedev/opencode-mobile | Android and iOS app: tasks, review changes, terminal, voice follow-ups, multiple servers | 259 | Apache-2.0 | Google Play, TestFlight beta, APK | 1,218 across 30 releases | 2026-10-14 |
| grapeot/opencode_ios_client | Native iOS, iPadOS and visionOS client for an OpenCode server | 266 | MIT | TestFlight beta | none | 2026-10-14 |
| bmpenuelas/opencode-mobile-client | Wraps OpenCode Web as native Android and iOS apps; server profiles, Basic Auth | 66 | MIT | Google Play and App Store | none | 2026-09-04 |
| grinev/opencode-telegram-bot | Telegram bot to run and monitor OpenCode tasks from a phone (OpenCode V2 supported) | 1,254 | not checked | Telegram; no app | not checked | 2026-10-15 |
| hosenur/portal | Mobile-first web UI for OpenCode over Tailscale/VPN (listed on OpenCode's ecosystem page) | not checked | not checked | web | not checked | not checked |

Common limit of this group: they connect to an OpenCode server that you run on
a computer (or VPS). None of those I read runs the server on the phone.

## B. Agent apps and platforms that include a phone client

| Project | What it does | Stars | License | Phone platforms | Release downloads |
| --- | --- | --- | --- | --- | --- |
| getpaseo/paseo | Open-source daemon plus desktop, mobile, web and CLI clients that orchestrate many agent CLIs (Claude Code, Codex, OpenCode, Pi and more); plugins | 20,989 | not identified by the GitHub API (NOASSERTION); an awesome list calls it AGPL-3.0, verify | iOS, Android (Expo) | 8,176,144 across 30 releases (all platforms, includes desktop) |
| slopus/happy | Mobile and web client for Claude Code and Codex with end-to-end encryption; run `happy` instead of `claude` on the computer; relay server | 24,161 | MIT | iOS App Store, Android, web | no GitHub releases |
| K9i-0/ccpocket | Mobile client for Codex and Claude via a bridge server on the computer, scan a QR code | 1,091 | MIT | iOS App Store, Google Play | 473 across 18 releases |
| NeuralNomadsAI/CodeNomad | Desktop, web, and remote client for OpenCode with a "Remote Control" phone pairing | 2,624 | MIT | desktop; phone via remote control | 852 across 29 releases |
| openchamber/openchamber | OpenCode-based workspace for desktop, web, VS Code; phone via PWA (a roundup calls its mobile less complete than desktop) | 11,650 | MIT | PWA, iOS/Android per its README | not checked |
| nimbalyst/nimbalyst | Visual workspace for Claude Code, Codex and OpenCode with a mobile companion | 1,906 | MIT | desktop; mobile companion (one 2026 roundup says no native Android app) | not checked |

All need an agent running on a computer you keep reachable (Paseo and Happy
run a daemon or CLI there; Remote Control is the same idea, below).

## C. Official and cloud options

| Option | What it does | Cost and requirements (as published) | Note |
| --- | --- | --- | --- |
| Anthropic Remote Control (Claude app for iOS and Android, or claude.ai/code) | Continue a Claude Code session running on your machine from the phone; "your code execution and filesystem access stay on your machine"; sessions sync across devices (https://code.claude.com/docs/en/remote-control) | Pro, Max, Team or Enterprise plan; "API keys are not supported"; not available with Bedrock, other cloud gateways or a custom `ANTHROPIC_BASE_URL` | Official, polished. Claude Code only; needs a computer on |
| Cosyra | Cloud VM per user running Claude Code, Codex, OpenCode, managed from a phone app (https://cosyra.com/) | $30/month for the 8 GB plan per its comparison table; also BYO keys | Claude Code runs "in the cloud workspace, not on the phone" (its own guide). iOS and Android |
| Termux guides (for example ferrumclaudepilgrim/claude-code-android, 273 stars; also iAmAjayTeli/claude-code-android, 10 stars) | Install Claude Code by hand inside Termux, proot-Ubuntu or the Android Virtualization Framework | Free; manual setup | The DIY version of what our setup automates; the first is listed in awesome-claude-code |
| PhoneCode (dttdrv/phonecode) | Native Android coding agent that runs its own agent loop on the phone with a bundled Alpine Linux, no backend; Apache-2.0 | 11 stars, last push 2026-10-14 | The closest on-device rival we found. It ships its own agent; ours runs Claude Code and OpenCode themselves |
| Tailscale plus SSH, mosh, tmux | Reach your computer's terminal from a phone terminal app | free | Needs a computer on; no approval UI |

## D. Where this category has been tried on Hacker News

From the Algolia HN API, `Show HN` posts, 2026-10-15 (points, comments):
Happy Coder 30 and 8 (2025-08); Paseo 14, 6 and 5 (three posts, 2026-03);
MobileCode 7 and 3 (2026-09); CC Pocket 2 and 0 (2026-03); Claude Code Remote
3 and 2 (2026-01); Nimbalyst 8 and 4 (2026-04); PhoneCode 1 (2026-06). A
Show HN for this category rarely reaches double digits even for projects
that now have 20k stars. Plan accordingly (see CHANNELS.md).

## Our real differentiators (each is checkable in the repo)

1. **The agent can run on the phone.** The app installs its own Ubuntu (proot)
   and runs OpenCode and Claude Code inside it; no computer, no Termux
   (README; first setup about 4 minutes and about 210 MB). Of the OpenCode
   clients above, none that I read does this. Cosyra does it in the cloud, and
   PhoneCode on the phone but with its own agent.
2. **Same app for computer-hosted agents.** OpenCode 1 and 2 by QR code or
   address; Claude Code, Codex and Pi through a Paseo helper. One conversation
   list across connections.
3. **Acting without opening the app (1.4.0).** Home-screen widgets: Allow,
   Reject, Stop, Steer, voice. Emulator-verified for OpenCode 1 (see
   `docs/qa/widgets-v2-2026-10-15/emulator-2320.md`), with a caveat: with the
   app closed the Allow buttons need "Stay connected in the background" on.
   I did not find another client advertising widgets of this kind; I did not
   search store listings exhaustively.
4. **Continue with... (1.4.0).** Hand a conversation to another agent (for
   example Codex to Claude Code) with a masked record. Built and tested with
   goldens and unit tests; no device pass is recorded in
   `docs/qa/agent-handoff-ui-2026-10-14/README.md`.
5. **Claude Code chat tools.** Files, Changes, terminal, worktrees and Fast
   mode (1.3.0 release notes).
6. **Free, MIT, no ads, usage and crash reports off until you turn them on, bring your own provider; English and
   Arabic.**
7. AI Team (planner, workers, checker; merges to dev with receipts). Proven
   only on emulators; the README says so. Do not lead with it.

## Our real gaps (say these before others do)

- Android only. Most competitors in B have iOS; Remote Control and Happy are
  more mature for the "continue my laptop session" case.
- 7 stars and a few dozen downloads; competitors have thousands of stars or
  GitHub downloads (Paseo 20,989 stars; Happy 24,161).
- Public alpha. The on-phone path is verified for OpenCode and Claude Code.
  Codex, Gemini CLI, Qwen Code, Goose and Pi use the same path but are not
  checked on a physical phone (README).
- Not on Google Play or F-Droid; the APK is about 100 MB (1.4.0) from GitHub
  releases only. dzianisv/opencode-mobile, P4OC and alvarolorentedev's app are
  on Play. See CHANNELS.md and IZZYONDROID.md for why.
- A phone is a poor place for long runs: Android 15 caps `dataSync`
  foreground services at 6 background hours per rolling 24 hours (AGENTS.md);
  a phone with about 2 GB memory is slow.
- OpenCode 1 support is being retired; new phones start on OpenCode 2.
- The app is built with substantial AI assistance (README), which some lists
  and communities refuse outright (IzzyOnDroid) or scrutinise (r/selfhosted
  asks about it).
- No web or desktop companion for the phone; no cloud option if the phone
  sleeps.

## How to talk about the others

Describe them by what they do; they are mature projects. "Paseo, Happy and CC
Pocket are good if your agent is on a computer you keep on. This is for when it
is not, or when you want it on the phone." Never claim "first" or "only";
PhoneCode and the Termux guides prove otherwise.
