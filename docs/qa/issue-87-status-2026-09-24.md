# Issue #87: where the proof stands (2026-09-24, evening)

Issue: https://github.com/Eslamasabry/opencode-mobile-next/issues/87. The reporter could not download AI Team, found the animations choppy, wanted a built-in terminal instead of Termux, and waited about 20 s for settings, a chat or sending a message.

**What people have today:** release 1.0.44+50 (2026-09-21), which contains **none** of this.
- The work below is on the local branch `feat/phone-setup-v2`: 79 commits ahead of `mobile-next/dev`, not pushed, not released.
- Every device proof so far ran on x86_64 emulators (Android 14 and Android 15). **Nothing has been proven on a real arm64 phone.**

| # | The reporter's problem | What changed | Proven | Not proven |
|---|---|---|---|---|
| 1 | "Can't download AI Team no matter how many retries" | Cause: the Termux path's manifests pointed at the owner's private Tailscale server. **Termux path (`9045386b`):** it now points at a GitHub pre-release `aiteam-assets-1`, which **does not exist**; the owner chose not to publish it. **Built-in path (new):** AI Team is an optional part of the in-app setup. It downloads gc 1.4.1, bd 1.2.2 and dolt 2.3.3 from the upstream projects' own GitHub releases, pinned by SHA-256. | Built-in path on the Android 15 emulator, twice (`aiteam-builtin-2026-09-24`, first proof and Run 2 on video): download, install, turn on, a real task merged (`86759b9`). | **Termux path: still fails** until `aiteam-assets-1` is published or Termux users are sent to the built-in Ubuntu. The arm64 pins have never been executed. Agent A's pull-to-project and process-peak fixes are still in progress. |
| 2 | "Animations look choppy" | `fix/smooth-drawing` (`ff592df2`): the transcript redraws less while a reply streams (repaint boundary, per-block markdown cache). Earlier: first-run motion (`22b169cb`, `9724d5d9`). The design standard (`docs/design/design-standard.md`) started today for consistency. | Unit tests only (`markdown_streaming_test`, `entrance_test`). | **No frame-time measurement on any device** (no `gfxinfo` or frame-timing numbers before and after). The design standard has not been applied yet (the Work tab and connection card are in progress). |
| 3 | "Why not a built-in terminal like andcode/anyclaw instead of Termux" | Built-in Ubuntu 24.04.5 inside the APK: proot as native libraries, a resumable setup (Linux, Git and SSH, Python, Node.js, OpenCode, optional AI Team), an in-app server with a foreground service, an in-app terminal view, and a phone-context note for the agent (`295106f5`). | Android 14 emulator: R1–R5 including resume after a force-stop and a real coding task (`phone-setup-v2-2026-09-24`). Android 15 emulator: fresh setup in 364.7 s (R7 / Run 2, video). | Real arm64 phone. Behaviour on slow or metered networks. Long background runs past 10 min with the server alone. |
| 4 | "20 s to load settings, a chat, sending a message" | Load waits (`fix/load-waits`). A folder no longer waits for the 6 MB catalog (`3b16f306`). A new folder no longer waits for the one-time provider refresh (`5afcfaee`), and neither does the first connect (`787fa137`). A setup screen that froze on one bad status read is fixed (`b17c67c7`). `OCTRACE` timing everywhere. | Measured on the emulator: the catalog took 7.7 s and delayed permissions by 6.2 s (R6), then fixed with a test. Create took 7.6 s (6.8 s of it the provider refresh), then fixed with a test. | **None of the fixes is re-measured on a device.** Opening settings and sending a message to the first reply are **not measured at all**. On the owner's phone (Termux, 2026-09-24) the slowness was the server itself: memory and swap full, no answer to `/global/health` within 30 s. The app cannot fix that, but the Work tab work in progress makes it say "isn't answering" after 8 s. |

## To close the issue with proof

1. Merge agent A (team runtime) and the Work tab cleanup, then build one APK.
2. **Integrated emulator run, Android 15, fresh install, recorded.** `OCTRACE` timings for cold start to the Work tab, opening a project, opening Settings, and sending a message to its first token. `adb shell dumpsys gfxinfo` jank figures while a reply streams, compared with 1.0.44+50 on the same emulator. Ask the agent "where are you running?".
3. **A real arm64 phone.** On the owner's phone this needs about 5 GB free, wireless debugging paired, and his OK. His Termux projects are backed up (`/home/owner/phone-backups/termux-2026-09-24/`). Setup, AI Team, one task, and the same timings.
4. **Decide the Termux AI Team path:** publish `aiteam-assets-1`, or point Termux users to the built-in setup.
5. Owner's call: push, release, and reply on #87 with the numbers.

## Update (2026-09-24, late)

- **Still not fixed for the reporter.** Nothing is pushed or released: latest release 1.0.44+50, branch 105 commits ahead of `mobile-next/dev`. `aiteam-assets-1` still does not exist.
- **Item 2, the design standard:** now applied to every screen group, step by step, each with goldens and before/after renders in `docs/qa/design-standard-*-2026-09-24/`:
  - the connection screens;
  - the Work tab;
  - phone setup;
  - the AI Team;
  - chat states;
  - Settings.

  The owner found the AI Team screens "very ugly" after the component pass. A content and words redesign is in progress (`docs/design/aiteam-redesign-2026-09-24.md`). **Not viewed on a device; no frame-time numbers yet.**
- **Item 3, the built-in terminal:** a local terminal that needs no server is in progress (`docs/design/local-terminal-2026-09-24.md`, branch `feat/local-terminal`), with its device checks pending.
- **Item 4, slow loading:** the first-connect wait was also removed (`787fa137`). Nothing is re-measured on a device.
- **Infrastructure:** the PC ran out of memory at 21:27 with two emulators and six agents running. Nothing was lost. Heavy jobs now run one at a time.
- **Unchanged to close:** the integrated device run with the timings, a real arm64 phone, the Termux AI Team decision, and the release plus reply.

## Closing plan (2026-09-25, owner's decisions)

The owner: "let's super focus on closing ticket 87". Decisions: on his phone he installs the candidate APK himself and pastes Settings › App diagnostics › Performance › Copy report (no adb on his phone); once the proof passes, push, release and reply on #87 (reply text shown to him first).

| # | Complaint | State on `feat/phone-setup-v2` | Proof still to take |
|---|---|---|---|
| 1 | Can't download AI Team | Built-in path proven (Android 15 emulator, task merged, merged work in the project folder). Termux path being moved off the unpublished `aiteam-assets-1` to the upstream releases inside Termux's Ubuntu (`fix/termux-aiteam-upstream`). AI Team made discoverable (`ds/team-discover`). | Termux AI Team end to end on an emulator with Termux installed; the built-in path again on the candidate; the owner's phone report. |
| 2 | Choppy, ugly UI | Design standard on every screen group; AI Team redesign; motion and drawings (§10); page/tab/part motion; liquid glass with an Appearance switch (in progress). | `gfxinfo` janky %/p50/p90/p99 while a reply streams and while scrolling, 1.0.44+50 vs the candidate, same emulator; the owner's phone report. |
| 3 | Built-in terminal | Local terminal in the built-in Ubuntu, proven on Android 14 emulator (ten checks). | Re-run on the candidate; the owner's phone. |
| 4 | 20 s loads | Catalog, provider refresh and first-connect waits removed; OCTRACE everywhere. | Cold start → Work tab, open project, open Settings, send → first token: 1.0.44+50 vs candidate on the same emulator; the owner's phone report. |

Order: baseline 1.0.44+50 and a Termux emulator (`docs/qa/issue-87-proof-2026-09-25/`, running) → merge the four open branches → candidate APKs (x86_64 for emulators, arm64 local-key for the owner) → integrated runs with video → owner's phone report → push `dev`, CI quality gate, fast-forward `master`, tag, CI release build (public key `842284B2`), `scripts/release.sh github --publish` → reply on #87 with numbers and video.
