# Ready-to-paste posts for the next release

> **Coordinator note (2026-10-16):** treat on-phone Claude Code chat (matrix G3) as `[needs proof: G3]` too. Its last PASS is from 2026-10-07/08, and on 2026-10-15 the owner's phone failed the Claude Code check. Post nothing about Claude Code on the phone until G1, G2 and G3 pass on the current build.

Nothing here has been posted. The owner posts from their own accounts after
reading each venue's live rules (see CHANNELS.md). Replace nothing in angle
brackets except where marked. Every claim below is from the repo, its release
notes or the commit log; do not add numbers (users, stars, speed) that are not
in them.

This file is written for "the next release" (no version number yet). Replace
"the next release" with the real version only after the tag exists.

## The proof gate (read before pasting anything)

Every claim that no device record backs yet carries a marker like
`[needs proof: C3]`. The letter and number are a row of
`docs/qa/real-life-matrix-2026-10-16/README.md`. Before posting:

1. Search this file for `[needs proof`.
2. For each marker, open the matrix row. If it now says PASS on an emulator or
   phone for the release build, delete the marker and keep the sentence.
3. If it is not PASS, delete the whole sentence (or the bullet), not just the
   marker. Do not soften it into something vaguer.
4. A row marked PASS only on a PC or in tests does not count for a claim about
   the phone.

The markers in use, with the claim each one guards:

| Marker | Claim guarded |
| --- | --- |
| G1, Q13 | The Claude Code phone check updates and recovers Claude Code (fix exists; PC proof only, no APK run) |
| G2 | Signing in to Claude Code on the phone with the release build |
| C3 | Sending while an agent works steers it (tests and goldens only so far) |
| I5, I6, I7 | Widgets settle after Allow, Reject or Stop and then show the latest reply, with the app closed |
| I8 | Steer from the widget |
| Q1, Q2, Q8 | Scheduled prompts run on time with the app closed, record history, and write a morning summary |
| Q9, Q10 | Tapping a localhost link opens the running app in a preview |
| B3, B4, B5 | Plain-HTTP consent and self-signed certificate pinning work on an Android device |
| M2, M3 | A send that may not have arrived is kept for review and never sent twice |
| Q12 | New users never see "Older OpenCode" |
| SIZE | The arm64 APK on the release page is about 36 MB (no matrix row; check the asset size on the release page) |

Claims already backed by a PASS in the matrix carry no marker: the on-phone
setup (A4), asking before shell commands and file writes (D1), Allow once and
Reject in chat (D2), Claude Code chat on the phone with tools, permissions and
abort (G3, emulator 2026-10-07/08, older build), the terminal (F4).

## Common facts used

- Free, MIT, independent community project, not affiliated with OpenCode or
  Anthropic, built with substantial AI assistance (the README says so).
- Repo: https://github.com/Eslamasabry/opencode-mobile-next
- APK: https://github.com/Eslamasabry/opencode-mobile-next/releases/latest .
  From the next release there is one APK per processor type: about 36 MB for
  arm64 phones instead of 105 MB [needs proof: SIZE]. Android 8 or newer;
  on-phone mode needs a 64-bit phone; about 2 GB of memory works, slowly.
- First setup: about 4 minutes and about 210 MB; installs Ubuntu, Git,
  Python, Node.js and the agent inside the app; resumable.
- You bring your own model provider (API key or sign-in), or use OpenCode's
  free model to try it.
- Codex, Gemini CLI, Qwen Code, Goose and Pi use the same path but are not
  verified on a physical phone. Say so whenever they are mentioned.
- OpenCode 1 support has ended: the app is OpenCode 2 only. A phone with
  managed OpenCode 1 moves to OpenCode 2 with its history; a remote OpenCode 1
  server shows upgrade instructions.
- Widgets need "Stay connected in the background" on to show Allow/Reject with
  the app closed (emulator finding B3 in
  docs/qa/widgets-v2-2026-10-15/emulator-2320.md). Mention it whenever
  widgets are mentioned.
- Android may stop background work (Android 15 limits dataSync services to 6
  hours per 24). Never say "runs all night".
- AndCode (yuga-hashimoto/and-code) does the same basic thing: Android app,
  Linux on the phone, OpenCode and Claude Code, free, MIT. It is older in the
  market, has more stars and downloads, a smaller APK than our old one, scheduled
  prompts, 8 languages and wake word. Never write "first", "only" or "best".

The words users use (VOICE-OF-USERS.md section 4), and the posts use them:
"from my phone", "no terminal", "approve", "my own keys", "when it's done".
Avoid "AI Team" as a lead, "orchestration", "protocol", and "proot" in
headlines.

Screenshot files (all paths relative to the repo root):

- S1 `fastlane/metadata/android/en-US/images/phoneScreenshots/1_welcome.jpg`
- S2 `.../2_setup_on_phone.jpg`
- S3 `.../3_chat.jpg` (a reply streaming, with Stop)
- S4 `.../4_approve.jpg` (an edit waiting for approval; 360x800, small)
- S5 `.../5_models.jpg`
- S6 `.../6_ai_team.jpg`
- W1 `docs/qa/widgets-v2-2026-10-15/img/w-12-permission-widget.png` (widget with Allow once / Reject; row title is a raw session name, so crop or prefer W2)
- W2 `docs/qa/widgets-v2-2026-10-15/img/w-10-timer-later.png`
- W3 `docs/qa/widgets-v2-2026-10-15/img/w-13-after-allow.png` (after Allow once, app never opened)
- C1 `docs/releases/v1.3.0+53/img/after/02-claude-files.jpg` (Claude Code: Files)
- C2 `docs/releases/v1.3.0+53/img/after/04-claude-terminal.jpg` (Claude Code: terminal)
- New captures for this release (schedule list, history, morning summary,
  preview) are in `docs/qa/scheduled-prompts-2026-10-15/` on this branch;
  pick them only after the matching proof marker is cleared, and use one
  full phone screen per image.

Do not use goldens (`*-dark.png`, `*-light.png`, `preview-layouts-dark.png`,
`docs/qa/agent-handoff-ui-2026-10-14/*.png`) as if they were device captures.
The video (VIDEO.md) is the best attachment wherever a venue allows one; it
must be re-cut so it opens on Claude Code on the phone.

---

## 1. r/opencodeCLI (text post)

Flair: whichever matches "project" or "built"; read the form.

Title (94 characters; Reddit allows 300):

    I built an Android app that runs Claude Code and OpenCode from my phone, no computer or Termux

Body:

    I wanted to use my coding agents away from my desk without carrying a
    laptop or leaving one on at home. So I made an Android app that installs
    its own Ubuntu inside the app and runs Claude Code and OpenCode there. No
    terminal setup, no Termux, no server of your own. If your agent already
    runs on a computer, it connects to that too (QR code or address;
    Tailscale works for reaching it from anywhere).

    The part I care about most is that you stay in charge from the phone:

    - It asks before it runs shell commands or writes files, and you approve
      from the chat. Allow once, Reject, or look closer at the diff first.
    - Home-screen widgets: Allow, Reject or Stop a running agent from the
      launcher, with the app closed, and the widget then shows the agent's
      latest reply instead of a stale "working" [needs proof: I5, I6, I7].
      Allow with the app closed needs "Stay connected in the background" on.
    - Sending a message while the agent works steers it instead of queuing
      [needs proof: C3].
    - Scheduled prompts: run a prompt at a set time (once or repeating), see
      each run's history, get a morning summary [needs proof: Q1, Q2, Q8].
    - Tap a localhost link in a reply and the running dev server opens in an
      in-app preview [needs proof: Q9].
    - The arm64 download is about 36 MB from the next release, down from
      about 105 MB [needs proof: SIZE].

    Other things that changed since the last release: a message that may not
    have reached the server is kept for you to review and is never sent twice
    [needs proof: M2, M3]; you can connect to a plain-HTTP server after an
    explicit yes, and pin a self-signed certificate [needs proof: B3, B4, B5];
    the Claude Code check on the phone can update and recover Claude Code
    [needs proof: G1, Q13]. OpenCode 1 support is gone: it is OpenCode 2 only.

    Honest limits: it is a public alpha. On the phone, OpenCode and Claude
    Code are the two I have checked; Codex, Gemini CLI, Qwen Code, Goose and Pi
    use the same mechanism but I have not checked them on a physical phone. You
    bring your own provider key (or use OpenCode's free model to try it). A
    phone with about 2 GB of memory works, slowly. Android can stop background
    work after a few hours, so I would not leave a long job unattended.

    There are other Android apps that do the same basic thing, AndCode for one.
    I did not invent this idea; what I added is acting on an agent without
    opening the app, steering it, and handing a chat to a different agent.

    It is free and MIT, an independent community project, not made by or
    affiliated with the OpenCode team or Anthropic. A lot of it was written
    with AI assistance, which the README says.

    Repo and APK: https://github.com/Eslamasabry/opencode-mobile-next

    I would most like to hear what breaks on your phone model.

Attach: the video if the form allows, else S3, S4, then the widget capture W3
as a comment. First comment from the owner: Android versions and phone memory
actually tested on (fill in; do not guess).

---

## 2. r/ClaudeCode: Weekly Showcase thread comment

The thread asks for what you built, how you used Claude Code, a link, and one
thing you learned. Keep it short ("quick project drops").

    What I built: an Android app that runs Claude Code on the phone itself. It
    installs its own Ubuntu, so there is no computer and no Termux, and you
    approve what it wants to do (shell commands, file writes) from the chat.
    It also connects to agents on your computer.

    How I used Claude Code: most of the app was written with AI assistance
    (the README says so), and Claude Code is one of the agents it runs. On the
    phone it gets its own sign-in, Files, Changes and a terminal in the project.

    Interesting bit: Allow, Reject or Stop from a home-screen widget without
    opening the app, then the widget shows the latest reply [needs proof: I5,
    I6, I7]. (With the app closed, Allow needs "Stay connected in the
    background".) It also has scheduled prompts with a morning summary
    [needs proof: Q1, Q2, Q8].

    Free, MIT, independent, not affiliated with Anthropic. Bring your own
    sign-in or key. Public alpha.
    https://github.com/Eslamasabry/opencode-mobile-next

Attach: S3. The standalone "Built with Claude Code" write-up goes a week later
(see CHANNELS.md); it is not written here.

---

## 3. r/ClaudeAI: Built with Claude Project Showcase megathread comment

(Rule 7 text not retrieved; read it first.)

    Claude Code from my phone, with no computer and no terminal setup: an
    Android app that installs its own Ubuntu and runs Claude Code and OpenCode
    there (about 4 minutes and about 210 MB the first time). If your agent is
    on a computer, it connects to that instead.

    How Claude was involved: the app was built with substantial AI assistance
    (the README says so), and Claude Code is one of the agents it runs.

    What works: chat with streaming replies and Stop, permission requests you
    approve with Allow or Reject, Files / Changes / terminal for Claude Code
    chats. New in the next release: the Claude Code check on the phone can
    update Claude Code and recover from a bad state [needs proof: G1, Q13],
    and widgets that Allow or Stop without opening the app
    [needs proof: I5, I6, I7]. Public alpha. Free, MIT, independent, not
    affiliated with Anthropic.

    https://github.com/Eslamasabry/opencode-mobile-next

Attach: one image, S3 or C1.

---

## 4. r/androiddev: monthly "Interesting Android Apps" showcase comment

(Next monthly thread, around Nov 9. It asks you to say whether the app is
free, paid or subscription.)

    OpenCode Mobile (free, MIT, no ads; usage and crash reports are off until you turn them on; Flutter plus a lot of
    Kotlin). It runs coding agents (OpenCode, Claude Code) on the phone by
    installing an Ubuntu rootfs inside the app and running it under proot.

    Android-side things that might interest this sub:
    - proot and its loader ship as native libraries, because that is the one
      place the app may run programs from.
    - Foreground-service limits: Android 15 caps dataSync services at 6 hours
      in the background per rolling 24 hours, so nothing assumes unbounded
      lifetime. Scheduled prompts use bounded WorkManager and alarm leases for
      the same reason [needs proof: Q1, Q2].
    - Home-screen widgets (1x1, 2x2, 4x2) that act on a running agent
      (Allow, Reject, Stop) through a short-lived connection
      [needs proof: I5, I6, I7].
    - One APK per ABI from the next release: the arm64 download drops from
      about 105 MB to about 36 MB [needs proof: SIZE].
    - Emulator-tested on Android 14 and 15; limited physical-phone coverage,
      so reports are welcome.

    APK from GitHub releases; it is not on the Play Store.
    https://github.com/Eslamasabry/opencode-mobile-next

Attach: W2 and S2. Do not claim Play availability.

---

## 5. r/selfhosted: New Project Megathread comment (hold until stable)

Only the megathread is allowed for projects under 3 months (repo from
2026-09-02, so until about 2026-12-02), and the wiki wants "production ready"
apps with docs. We are a public alpha, so this may be removed. If posted,
expect to answer the AI-involvement bot: "built with substantial AI
assistance".

    OpenCode Mobile: an Android companion for a self-hosted coding agent.
    Point it at an OpenCode 2 server you run (QR code or address; a plain-HTTP
    address after an explicit yes, or a self-signed certificate you pin
    yourself [needs proof: B3, B4, B5]; works over Tailscale), or run the agent
    on the phone in an Ubuntu sandbox the app installs. Free, MIT, no ads or accounts; usage and crash reports are off until you turn them on. Passwords stay in the Android keystore. Your conversations
    and code go only to the servers you choose. Built with substantial AI assistance;
    independent, not affiliated with OpenCode or Anthropic. Public alpha.
    https://github.com/Eslamasabry/opencode-mobile-next

---

## 6. Show HN

Title (limit 80 characters; this one is 69):

    Show HN: Claude Code from my Android phone, no computer and no Termux

First comment (post it right away, as the author):

    Hi HN. I wanted to run Claude Code from my phone without a laptop running
    at home and without a terminal to set up. This Android app installs its own
    Ubuntu (proot) inside the app and runs Claude Code and OpenCode there. If
    your agent is already on a computer, the app connects to that instead (QR
    code or address; Tailscale to reach it from anywhere).

    How it works: the app ships proot and its loader as native libraries,
    downloads an Ubuntu 24.04 base, and a guided, resumable setup installs
    Git, Python, Node.js and the agents inside it (about 4 minutes and about
    210 MB the first time). It is a Flutter app with a lot of Kotlin around the
    sandbox, foreground services and the widgets.

    What I would point at, since you cannot try it without a phone:
    - You approve from the phone. It asks before shell commands and file
      writes; Allow once or Reject, or look closer at the diff first.
    - Home-screen widgets for Allow, Reject and Stop, with the app closed
      [needs proof: I5, I6, I7].
    - Sending a message while the agent works steers it [needs proof: C3].
    - Scheduled prompts with run history and a morning summary
      [needs proof: Q1, Q2, Q8].
    - A localhost link in a reply opens the dev server in a preview
      [needs proof: Q9].
    - The arm64 APK is about 36 MB from the next release (it was 105 MB)
      [needs proof: SIZE].

    Limits, plainly: public alpha. Claude Code and OpenCode are the two checked
    on the phone; Codex, Gemini CLI, Qwen Code, Goose and Pi use the same path
    and are not checked on a physical phone. You bring your own model provider
    (an API key or sign-in, or OpenCode's free model to try it). A phone with
    about 2 GB of memory works, slowly. Android 15 limits dataSync services to 6
    hours per 24, so a long run on the phone is not a guarantee. It is on
    GitHub releases, not Play or F-Droid. OpenCode 1 support is gone; it is
    OpenCode 2 only.

    AndCode does the same basic job and is further along in some ways (more
    stars, 8 languages, wake word). I do not claim to be first. It is MIT,
    free and independent: not affiliated with the OpenCode team or Anthropic.
    Much of it was written with AI assistance and the README says so. I would
    like to know what fails on your phone.

    https://github.com/Eslamasabry/opencode-mobile-next

URL to submit: the repo link (the repo page has the download).
Attach: nothing on HN; keep any video link in the first comment.

---

## 7. X thread (6 posts; each at most 280 characters)

Post 1 (attach the 15 s vertical video, or W3):

    Claude Code from my phone. No computer, no terminal setup, no Termux. This Android app installs its own Ubuntu and runs Claude Code and OpenCode there. You approve what it wants to do. Your own keys. Free, MIT. Thread:

Post 2 (attach S2):

    First run: guided setup installs Ubuntu, Git, Python, Node and the agent inside the app. About 4 minutes and about 210 MB, resumable if you stop. Claude Code runs beside OpenCode with its own sign-in.

Post 3 (attach S4):

    Approve before it acts. It asks before shell commands and file writes. Allow once, Reject, or look closer at the diff first. Widgets do the same from the home screen with the app closed [needs proof: I5, I6, I7].

Post 4 (attach S3):

    Send a message while it works and it steers instead of queuing [needs proof: C3]. A localhost link opens your dev server in an in-app preview [needs proof: Q9].

Post 5 (attach a schedule capture only after the proof is cleared):

    Scheduled prompts: set a time, see every run's history, read a morning summary [needs proof: Q1, Q2, Q8]. The arm64 download is about 36 MB from the next release, down from 105 MB [needs proof: SIZE].

Post 6:

    Honest limits: public alpha. Claude Code and OpenCode are checked on a phone; Codex, Gemini, Qwen, Goose, Pi are not. AndCode does similar work; I am not first. Android may stop long jobs. Bring your own key.

    https://github.com/Eslamasabry/opencode-mobile-next

Not affiliated with OpenCode or Anthropic; say so again in the profile bio.
No tags or hashtags on purpose.

---

## 8. Product Hunt (hold until a landing page or store listing exists)

The landing page now exists (docs/site), but there is still no store listing
and Product Hunt wants a one-click try. Hold until that changes.

Name: OpenCode Mobile

Tagline (60 characters max; this is 50):

    Run Claude Code and OpenCode on your Android phone

Description (260 characters max; this is 255):

    An Android app that installs its own Ubuntu and runs Claude Code and OpenCode from your phone: no computer, no Termux. You approve what it does, from the chat or a widget. Or connect to agents on your computer. Free, MIT, independent. Bring your own keys.

First comment (maker comment):

    Hi, I am <owner name>. I built this because I wanted Claude Code on my
    phone without leaving a laptop on at home. The app installs Ubuntu inside
    itself and runs Claude Code and OpenCode there. It also connects to agents
    on your computer. You approve what it wants to do from the chat, and from
    home-screen widgets [needs proof: I5, I6, I7].

    Public alpha: Claude Code and OpenCode are checked on the phone; Codex,
    Gemini CLI, Qwen, Goose and Pi share the mechanism but are unchecked on a
    physical phone. Bring your own model provider. Free and MIT; independent,
    not affiliated with OpenCode or Anthropic; built with substantial AI
    assistance. AndCode is a similar app; I am not the first. Feedback on what
    breaks is the most useful thing you can give me.

Topics: Android, Developer Tools, Artificial Intelligence, Open Source.
Gallery order: the video, S3, S2, S4, W3, S6.

---

## 9. OpenCode Discord (short message)

Post only in a showcase or community-projects channel if one exists; never in
support channels.

    Hi all. I made an independent Android app that runs OpenCode and Claude Code
    from the phone itself (it installs its own Ubuntu, no Termux) or connects
    to your OpenCode 2 server. You approve from the chat or a widget. Free, MIT,
    not affiliated with the OpenCode team. Public alpha, OpenCode 2 only.
    https://github.com/Eslamasabry/opencode-mobile-next
    Would love to hear what breaks on your phone.

Attach: S3 or the video.

---

## Replies to expect (prepared, honest)

- "Why not Termux?" Termux needs manual setup per agent and its own package
  state; this installs and checks each step and can resume. It also has a
  "Move from Termux" path that copies projects (README).
- "How is this different from AndCode?" Same idea, and they are further along
  in some ways: more stars and downloads, faster releases, 8 languages, wake
  word, scheduled prompts (we added ours in the next release). What we do
  differently: act on an agent from a home-screen widget without opening the
  app [needs proof: I5, I6, I7], steer a running reply [needs proof: C3],
  hand a chat to another agent ("Continue with..."), one inbox across phone
  and computers. Do not say "first" or "only".
- "How is this different from Happy / Paseo / CC Pocket?" Those drive agents
  that run on a computer you keep on. This one can run the agent on the phone,
  and also connects to computers. They are mature, have far more users, and
  have iOS apps; we are Android only.
- "Is it smaller than X?" Do not compare. Say the arm64 APK is about 36 MB
  [needs proof: SIZE].
- "Is it on Play / F-Droid?" No. GitHub releases and Obtainium only. Do not
  promise either (CHANNELS.md).
- "Does it send my code anywhere?" Your conversations and code go only to the servers you
  choose; no ads; usage and crash reports are off until you turn them on, and when on they go to Google and our own Umami server hosted with Railway (PRIVACY.md). The model
  provider you configure receives your prompts.
- "Can I use my Claude subscription?" The app does not collect a Claude
  subscription on your behalf; you sign in to Claude Code yourself on the
  phone [needs proof: G2].
- "Does it work over plain HTTP?" Yes after an explicit yes for that server;
  self-signed certificates can be pinned, and a changed certificate is blocked
  [needs proof: B3, B4, B5].
- "Was this vibe coded?" Substantial AI assistance, stated in the README; the
  repo has a test suite and CI. Do not argue; link the README sentence.
