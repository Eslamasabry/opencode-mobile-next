# Voice of users: mobile AI-coding apps vs OpenCode Mobile

Researched 2026-10-15. Text and web research only; nothing run, built or
installed. Numbers come from the GitHub API, the Hacker News Algolia API and
the project pages on that date.

**Method and limits (read first).**
- Quotes are verbatim from the linked page and date. Where I shortened a
  quote I cut at a sentence boundary; I never paraphrase inside quote marks.
- **Reddit was not reachable** (WebFetch and WebSearch both refuse
  reddit.com), and I found no Play Store or App Store review text I could
  read (AndCode's Play URL returns 404; Happy's guessed URL returned 404).
  So this document has **no Reddit voice and no store-review voice**. The
  evidence is GitHub issues (with reaction counts), Hacker News threads, and
  Product Hunt. That biases it toward people who file issues, who are more
  technical than the average phone user. Re-run the Reddit part from a
  browser before quoting "what Reddit says" anywhere.
- Ranking is a judgement: how many distinct sources raise it, how many
  reactions or "+1" comments it draws, and how strongly it is worded. It is
  not a survey. Counts of reactions are GitHub's `reactions.total_count` on
  2026-10-15.
- Our external user voice is thin: our two repos have a handful of outside
  issues (listed in section 1, items 8, 12, 17, 20). I did not invent more.

---

## 1. Top 20 wants and pain points

Status key: **covered** (shipped, evidence cited), **partly**, **missing**.
Evidence for our status is from `README.md`, `CHANGELOG.md`,
`docs/releases/v1.4.0+54.md`, and `lib/` in the checkout
`/home/owner/Storage2/wt/oc_app-batch`. "Not device-verified" is stated where
our own docs say it.

### 1. Opening the agent app should "just work" the first time (setup and crashes)
The loudest, most frequent category across every project. Installs that fail,
servers that will not connect, a chat that freezes or crashes on opening.
- Happy: "`$ happy` Error: Request failed with status code 404" - +34
  reactions, 17 comments, [slopus/happy#319](https://github.com/slopus/happy/issues/319) (2026-01-01). "Self-hosting is thoroughly broken"
  [#246](https://github.com/slopus/happy/issues/246) (2025-11-27).
- AndCode: "There are many serious flaws in the application, such as automatic
  exit and the disappearance of replies when entering conversations."
  [and-code#310](https://github.com/yuga-hashimoto/and-code/issues/310) (2026-09-09); "App not working... tried reinstalling"
  [#339](https://github.com/yuga-hashimoto/and-code/issues/339) (2026-09-18).
- Termux route: "[DEVICE] android 10 didnot work ... `Bad system call claude`"
  [claude-code-android#5](https://github.com/ferrumclaudepilgrim/claude-code-android/issues/5) (2026-06-11); "glibc-repo install failed" [#4](https://github.com/ferrumclaudepilgrim/claude-code-android/issues/4) (2026-06-05).
- Ours: "Cant download ai team on phone no matter how many retries"
  [opencode-mobile-next#87](https://github.com/Eslamasabry/opencode-mobile-next/issues/87) (2026-09-23).
- **Status: partly.** Guided, resumable setup with per-step checks and repair
  of half-installed packages (README "What you can do"; 1.4.0 "Setup repairs
  packages left half-installed"). But our own newest-user report (#87) and
  #95 (agent picker stuck on "Checking sign-in...", closed) show the first
  run is still where we lose people. Not device-verified on many phones
  (README, "AI Team has not yet been tried on many physical phones").

### 2. Get told when the agent finishes or needs me (push notifications) and act on it
- "[FEATURE] Mobile push notifications when Claude Code finishes a task or
  requires input... developers must either watch their terminal continuously
  or poll manually, which defeats the purpose of handing off work"
  [anthropics/claude-code#72348](https://github.com/anthropics/claude-code/issues/72348) (2026-06-29).
- On Happy's OpenCode request, a user explains why a phone app beats OpenCode's own web UI: "their web UI doesn't send notifications when OpenCode is done." [happy#344 comment](https://github.com/slopus/happy/issues/344) (Zohair-coder, 2026-01-15).
- Omnara's pitch to HN: "you can select models/harnesses, spin up new
  sessions, create worktrees, view code changes, see tool calls, have live
  previews, have voice agent support" [HN 46991787](https://news.ycombinator.com/item?id=46991787) (kmansm27, 2026-02-12).
- **Status: covered, strongest area.** Inbox across servers, "While you were
  away", permission notifications, and 1.4.0 home-screen widgets (Allow,
  Reject, Stop, Steer, voice). Caveat from COMPETITORS.md: with the app closed,
  Allow needs "Stay connected in the background"; widgets verified on an
  emulator for OpenCode 1 only (`docs/qa/widgets-v2-2026-10-15/emulator-2320.md`).

### 3. Make Codex and other agents work, not only one
Happy's three biggest feature threads are "add OpenCode". AndCode's users ask
for Codex, then for Codex fixes.
- "We're a development team spending approximately **$20,000 USD per day** on
  AI coding API usage... we'd like to request official support for OpenCode"
  [happy#344](https://github.com/slopus/happy/issues/344) (2026-01-07, +36). Also [#265 "OpenCode Support"](https://github.com/slopus/happy/issues/265) +55 (2025-12-05), [#394](https://github.com/slopus/happy/issues/394) +41, [#538](https://github.com/slopus/happy/issues/538) +29.
- "Support pi coding harness" [happy#1213](https://github.com/slopus/happy/issues/1213) +30 (2026-05-03); "feat: Antigravity CLI support" [paseo#1131](https://github.com/getpaseo/paseo/issues/1131) +32 (2026-05-21).
- AndCode: "Please publish a new release with Codex support... I really like
  your app, it's like a dream." [and-code#363](https://github.com/yuga-hashimoto/and-code/issues/363) (2026-10-04); "Codex"
  [#331](https://github.com/yuga-hashimoto/and-code/issues/331) (2026-09-17).
- **Status: covered in breadth, thin in proof.** OpenCode 1+2, Claude Code,
  Pi, Codex via Paseo; Gemini CLI, Qwen Code, Goose on the on-phone path
  (README). README itself says Codex/Gemini/Qwen/Goose/Pi are "not yet
  checked on a physical phone". Antigravity: not supported.

### 4. Permissions and questions that actually work on a phone
Happy's approval UI breaks in public, a lot.
- "Whenever I initiate a codex session... I can neither approve nor decline
  it, which makes the entire session get stuck" [happy#302](https://github.com/slopus/happy/issues/302) (2025-12-27, +18);
  "whenever Codex ... asks me to grant permission, it freezes" [#661](https://github.com/slopus/happy/issues/661) (2026-02-18, +19).
- "When Claude Code triggers `askUserQuestion`, the app incorrectly
  interprets this as a permission request" [happy#375](https://github.com/slopus/happy/issues/375) (2026-01-13, +16);
  "the mobile interface shows raw JSON and only yes/no buttons" [#635](https://github.com/slopus/happy/issues/635) (2026-02-16).
- HN Ask, an Android user: Happy "can't deal with claude's new question
  format" [HN 46742298](https://news.ycombinator.com/item?id=46742298) (2026-01-24).
- AndCode: "fix(chat): cannot type spaces in question tool custom answer field"
  [and-code#319](https://github.com/yuga-hashimoto/and-code/issues/319) (2026-09-15).
- **Status: covered.** Approvals above the message box, "waiting questions
  are answered from the Conversations list" (1.3.0), `lib/ui/screens/chat/attention_card.dart`
  handles question requests. Not independently device-tested by me.

### 5. "Not a terminal": a UI made for thumbs, not a TUI
- "A TUI interface is far from what I would call 'comfortable' on a mobile
  phone... Important would be that I am not just being dropped into a console
  but that the interface is actually optimized for phone use." [HN 46742298](https://news.ycombinator.com/item?id=46742298)
  (Ask HN, 2026-01-24, Android user).
- "People tried reinventing terminals, SSH, and tmux for phones. It's a pretty
  terrible experience using your thumbs. And it takes significant know-how to
  set up." [HN 47152678](https://news.ycombinator.com/item?id=47152678) (ebiester, 2026-02-25).
- Omnara's founders say they chose a GUI over a CLI for mobile ergonomics (my
  summary of a secondary write-up of the [Launch HN](https://news.ycombinator.com/item?id=46991591), 2026-02-12; not a verbatim quote).
- **Status: covered; our core promise.** Chat with live steps, folded tool
  work, diffs that wrap to phone width (README). A real terminal exists too.

### 6. Keep my work running; do not die when I leave
- Remote Control: "The `/remote-control` feature disconnects frequently...
  The session drops every few minutes and needs to be restarted manually."
  [anthropics/claude-code#33041](https://github.com/anthropics/claude-code/issues/33041) (2026-03-11, +29).
- "the session becomes unresponsive after ~5 hours of inactivity... there is
  no way to reconnect to the same session... The core value proposition of
  Remote Control (resume anytime from mobile) is undermined" [claude-code#36401](https://github.com/anthropics/claude-code/issues/36401) (2026-03-19).
- AndCode: "Unable to connect after some time of inactivation... When this is
  happening, I have to first uninstall and then reinstall." [and-code#306](https://github.com/yuga-hashimoto/and-code/issues/306) (2026-09-09).
- Android realities: "Context.startForegroundService() did not then call
  Service.startForeground()" crash in AndCode's wake-word service [and-code#301](https://github.com/yuga-hashimoto/and-code/issues/301) (2026-09-08).
- **Status: partly.** Background monitor, keep-live service, resume of
  AI Team after interruption (1.4.0). Hard limit we state ourselves:
  Android 15 caps `dataSync` foreground services at 6 h per rolling 24 h
  (AGENTS.md, COMPETITORS.md "gaps"). Honest claim: "survives your screen
  turning off", not "runs all night".

### 7. Free, open, and my own keys: no subscription lock-in, no relay
- HN on Remote Control: "Would be great if it supported API keys. I'm getting
  by with slack threads of all things for work." [HN 47150020](https://news.ycombinator.com/item?id=47150020) (squirrellous, 2026-02-25).
- Same thread: "That's what I've been doing with termux, mosh, and tmux...
  you can avoid vendor lock in." [HN 47150069](https://news.ycombinator.com/item?id=47150069) (samusiam, 2026-02-25).
- Omnara HN: "I'm trying my best to only pay for the model these day and avoid
  any other tooling subscription" [HN 46992608](https://news.ycombinator.com/item?id=46992608) (faramarz, 2026-02-12).
- Billing risk for Claude-via-SDK apps: "Is using claude in paseo against
  their ToS? Either way, readme should notice this." [paseo#1029](https://github.com/getpaseo/paseo/issues/1029) (okan-instrumentl, 2026-06-11, +13).
- **Status: covered.** MIT, no ads, no analytics, bring your own provider
  (COMPETITORS.md differentiator 6). Our CHANGELOG keeps Claude subscription
  collection unavailable on purpose (line ~425), which is the right side of
  the paseo#1029 question.

### 8. Connect to my server over plain HTTP / private networks / self-signed
- Ours: "The app should not prevent remote HTTP connections." [opencode-mobile-next#85](https://github.com/Eslamasabry/opencode-mobile-next/issues/85) (sawa-ko, 2026-09-20; open).
- Ours (internal, open): "Self-signed certificate trust flow" [#38](https://github.com/Eslamasabry/opencode-mobile-next/issues/38). P4OC had the same ask: "Self-signed certificate failure" [P4OC#1](https://github.com/theblazehen/P4OC/issues/1) (+2, 2026-03-23).
- doza62: "Cannot connect OpenCode Mobile to VPN-only opencode serve endpoint" [#16](https://github.com/doza62/opencode-mobile/issues/16) (2026-06-09); "Tailscale" [#10](https://github.com/doza62/opencode-mobile/issues/10).
- **Status: partly / missing for plain HTTP.** We refuse remote cleartext HTTP
  unless confirmed (`serverUrlNeedsCleartextConfirmation` in
  `lib/platform/connection_advice.dart`); #85 is still open, #38 open.
  Tailscale route is documented (`docs/tailscale-connection.md`).

### 9. Use the model I want, switch fast, set thinking level
- AndCode: "There is no level of thinking" [#270](https://github.com/yuga-hashimoto/and-code/issues/270) (2026-09-01); "No place found to configure Deepseek" [#337](https://github.com/yuga-hashimoto/and-code/issues/337) (2026-09-18) and "We need you to add a feature to add new providers... If you see my problem, please like this post." [#322](https://github.com/yuga-hashimoto/and-code/issues/322) (2026-09-14).
- Japanese user wants "Max" reasoning above "Extra High", plus temperature
  and Top-P [and-code#294](https://github.com/yuga-hashimoto/and-code/issues/294) (2026-09-04).
- grapeot iOS: "feat(models): derive model picker from /provider instead of
  hardcoded presets" [#99](https://github.com/grapeot/opencode_ios_client/issues/99) (+3, 2026-06-17); P4OC: thinking level selector [#11](https://github.com/theblazehen/P4OC/issues/11) (2026-05-14).
- **Status: covered.** Model picker with Reload providers, favourites/recents
  (issue #60, closed), Providers screen incl. device-code sign-in
  (1.4.0). Per-request temperature/Top-P: missing (not a stated goal).

### 10. Look at diffs, files and changes properly on a phone
- "There is no 'files changed' summary after a turn. The chat only shows a
  list of file *names*, and the workspace Changes tab requires a git repo"
  [and-code#330](https://github.com/yuga-hashimoto/and-code/issues/330) (henryz78, 2026-09-16).
- "The file explorer cannot leave the current workspace root, so projects/files
  created elsewhere inside the Linux environment can never be opened." (same issue).
- P4OC wants file and image attachments from mobile [#69](https://github.com/theblazehen/P4OC/issues/69) (2026-09-08).
- **Status: covered; syntax highlight in diffs open.** Diffs wrap to phone
  width, Files and Changes tabs for Claude Code chats (1.3.0). Open on our
  tracker: "Diff viewer: syntax highlighting" #26, "Per-file accept/reject in
  a multi-file edit" #27.

### 11. A real terminal (PTY), not one command at a time
- "The embedded terminal can only execute one bounded command at a time. It
  is unusable for normal terminal work (running a dev server, following logs,
  answering an interactive prompt, interrupting with Ctrl+C)... force-killed
  after 30s... only the last 4,000 characters" [and-code#329](https://github.com/yuga-hashimoto/and-code/issues/329) (2026-09-16, open).
- Ours-adjacent: our own user asked for the same thing and named AndCode:
  "instead of using termux manually why doesn't it have in built its own
  terminal stuff etc like andcode" [opencode-mobile-next#87](https://github.com/Eslamasabry/opencode-mobile-next/issues/87) (2026-09-23).
- P4OC: "Terminal font size is fixed at 14sp and cannot be increased on
  phones" [P4OC#68](https://github.com/theblazehen/P4OC/issues/68) (2026-09-06).
- **Status: covered by design, not by my verification.** The local terminal
  is an `xterm` view fed by a real PTY in the built-in Ubuntu
  (`docs/design/local-terminal-2026-09-24.md`, `lib/ui/screens/local_terminal_screen.dart`)
  with a key bar. I did not check what the shipped build does with
  scrollback or long runs.

### 12. Typing and layout bugs on Android (keyboard, rotation, fonts)
Small, concrete, repeated. Cheap to fix and loud when wrong.
- "Android: chat input hidden behind keyboard" +11 [dzianisv/opencode-mobile#194](https://github.com/dzianisv/opencode-mobile/issues/194) (2026-08-19); alvarolorentedev: "Text/chat input box is hidden behind (Futo) keyboard" [#63](https://github.com/alvarolorentedev/opencode-mobile/issues/63) (2026-10-04); "Caret is invisible in the text input field" [#46](https://github.com/alvarolorentedev/opencode-mobile/issues/46).
- AndCode: "composer draft lost on device rotation... the in-progress prompt does
  not [survive]" [#355](https://github.com/yuga-hashimoto/and-code/issues/355) (2026-09-27); alvarolorentedev: forced 100% screen brightness
  while foreground [#75](https://github.com/alvarolorentedev/opencode-mobile/issues/75) (2026-10-10).
- CC Pocket: "Long chat history causes severe UI jank on Android" [ccpocket#80](https://github.com/K9i-0/ccpocket/issues/80) (2026-05-08).
- **Status: partly (not verified by me).** CHANGELOG records draft
  preservation for unsent prompts and failed writes (lines ~254-281);
  custom IME and rotation cases were not checked. A sweep on a Futo keyboard
  and rotation is a cheap trust win.

### 13. Clickable links, especially localhost dev servers, and a browser to see the result
- "Markdown links like `**http://localhost:4200**` are not clickable."
  [and-code#374](https://github.com/yuga-hashimoto/and-code/issues/374) (NINI1988, 2026-10-07). Same user: "develop angular web apps locally with this
  app, but... switching to the guest browser takes a lot of clicks" [#362](https://github.com/yuga-hashimoto/and-code/issues/362) (2026-10-04).
- Related market signal: "MobileCode - OpenCode with Built-In iOS and Android
  Previews" [Show HN 49577645](https://news.ycombinator.com/item?id=49577645) (2026-09-05); Omnara "live previews" (see item 2).
- **Status: partly.** External links go through `openExternalLink` with a
  confirmation (security invariant in AGENTS.md; `kit_markdown_image.dart`).
  No in-app browser or preview of a dev server on the phone that I found.

### 14. Voice: talk to the agent, offline, in more languages
- Ours (open): "Non-English transcription" [#36](https://github.com/Eslamasabry/opencode-mobile-next/issues/36), "Push-to-talk mode and auto-send on silence" [#35](https://github.com/Eslamasabry/opencode-mobile-next/issues/35).
- AndCode: voice command crash [#301](https://github.com/yuga-hashimoto/and-code/issues/301); grapeot: "questions about voice input" [#158](https://github.com/grapeot/opencode_ios_client/issues/158) (2026-08-30); Happy "Voice Assistant Not Working" [#402](https://github.com/slopus/happy/issues/402), [#576](https://github.com/slopus/happy/issues/576).
- **Status: partly.** On-device transcription, "audio never leaves the phone"
  (README), voice from the widget (1.4.0). Open issues: non-English, push to
  talk. No wake word, no read-aloud (AndCode has both).

### 15. Local language and RTL
- "enhance arabic support... mixed languages eg. ar-en, rtl & ltr, as it
  appears rotated, start of line at the end" [and-code#341](https://github.com/yuga-hashimoto/and-code/issues/341) (2026-09-19).
- AndCode ships 8 languages; their i18n issues were Japanese-first
  ([#55](https://github.com/yuga-hashimoto/and-code/issues/55), [#56 RTL](https://github.com/yuga-hashimoto/and-code/issues/56)).
- **Status: covered for English and Arabic**; fewer languages than AndCode (8).

### 16. Find it in a store; an installable that updates itself
- "F-Droidへの公開をご検討いただけないでしょうか" (please consider publishing on
  F-Droid), reasons given: discoverability in the "AI Chat" category, privacy-minded users
  [and-code#267](https://github.com/yuga-hashimoto/and-code/issues/267) (2026-09-01, +2); alvarolorentedev: "f-droid release?" [#45](https://github.com/alvarolorentedev/opencode-mobile/issues/45) (2026-09-23).
- Happy's earliest Android request: "Release Android apk" [happy#25](https://github.com/slopus/happy/issues/25) (2025-08-19).
- **Status: missing.** GitHub APK and Obtainium only (README); Play, F-Droid
  and IzzyOnDroid not done (COMPETITORS.md "gaps"; our #41, #42 open). The
  APK is about 100 MB against AndCode's about 62 MB.

### 17. Don't let the project look abandoned; respond to issues
- "Still maintained? It looks like the project has been abandoned (last commit
  > 1 month ago)" [happy#223](https://github.com/slopus/happy/issues/223) (2025-11-04, +29); Ask HN: "seems a bit abandoned" (item 4 above).
- Contrast: AndCode ships a release every few days (79 releases in 3 months;
  see section 2) and its maintainer closes most issues within a day or two.
- **Status: covered on cadence** (1.2.0 to 1.4.0 in 8 days, Shorebird
  patches available). The weak spot is replying: our issue #87 got a reply
  within hours and a follow-up on 2026-10-01; keep that pace for every outside
  issue.

### 18. Safe by default: do not let a phone approve dangerous commands blindly
- AndCode README warns: "PRoot is a compatibility runtime, not a full security
  sandbox. Full-access / bypass-permissions modes... QR codes... can embed a
  plaintext connection password" ([README, Security](https://github.com/yuga-hashimoto/and-code#security), read 2026-10-15).
- HN: "even more reasons to sandbox it to a container or vm" [HN 47149966](https://news.ycombinator.com/item?id=47149966) (weikju, 2026-02-25); Palmier thread: "what's the approval flow look like? Is it per-tool-call, per-session, or do you grant broad scopes up front?" [HN 47846195](https://news.ycombinator.com/item?id=47846195) (2026-04-21).
- **Status: partly.** "Asks first" mode fixed for OpenCode 2 (1.4.0), device
  authentication before notification approvals (CHANGELOG ~607), saved
  permissions screen. Open: our biometric/PIN lock issue #37 and threat model
  #40.

### 19. Reuse my existing sessions; hand off between devices and agents
- "It would be amazing if we could import existing sessions from the CLI tools
  so we can resume work on the go." [paseo#237](https://github.com/getpaseo/paseo/issues/237) (2026-04-10, +10).
- Omnara: "Omnara tries to make switching between being on your computer and
  being on your phone feel seamless" [HN 46992236](https://news.ycombinator.com/item?id=46992236) (kmansm27, 2026-02-12).
- AndCode answers it with "Continue on another runtime" (README, Handoff).
- **Status: covered, and a differentiator.** "Continue with..." between
  agents (1.4.0), "Import from Claude Code" (1.3.0), "Continue on computer"
  copies the resume command. Continue with... has no device pass recorded
  (COMPETITORS.md diff 4).

### 20. Work I can leave running that does something while I am away (scheduled / background tasks)
- AndCode's maker says on Product Hunt that scheduled tasks are one of three
  headline reasons it exists. A user asks to "run startup commands /
  background services when the local runtime starts" [and-code#379](https://github.com/yuga-hashimoto/and-code/issues/379) (2026-10-10, open).
- Omnara founders sell "managed infrastructure around the coding agent
  harness with features like mobile, sandboxes, preview urls" [HN 46991798](https://news.ycombinator.com/item?id=46991798) (cmsparks, 2026-02-12).
- Ours: "Manage OpenCode 2 background shells from a Running work sheet" #62 (closed).
- **Status: partly.** AI Team (planner, workers, checker) and check-in
  reminders for long runs. Missing: scheduled/recurring prompts (AndCode
  ships these) and guest-service autostart.

---

## 2. AndCode vs OpenCode Mobile

Sources: AndCode README, repo, releases and issues via the GitHub API
(2026-10-15); Product Hunt page; its site. Our side from README, CHANGELOG,
1.4.0 notes.

### Facts and numbers (2026-10-15)

| | AndCode | OpenCode Mobile (ours) |
|---|---|---|
| Repo | [yuga-hashimoto/and-code](https://github.com/yuga-hashimoto/and-code) | Eslamasabry/opencode-mobile-next |
| Licence | MIT | MIT |
| Price | Free (Product Hunt, README) | Free |
| Stars / forks | 140 / 17 | 7 / 3 |
| Created | 2026-07-19 | earlier (our releases go back to 1.0.17) |
| Latest release | v1.2.27, 2026-10-05; 79 release tags since 2026-07 | 1.4.0+54, 2026-10-14/15 |
| GitHub release downloads (all assets, all versions) | 2,421 (sum I computed; recent: 457 on v1.2.27, 581 on v1.2.25) | about 150 across versions (COMPETITORS.md: 6, 20, 29, 29, 26, 38) |
| APK size | about 62 MB (v1.2.27 asset) | about 100 MB (1.4.0) |
| Language | Kotlin (native Android, Compose) | Flutter (Dart) + Kotlin |
| Platforms | Android | Android (iOS none, desktop experimental CI) |
| Contributors | 3 | not counted |
| Open issues | 4 | see `gh issue list` (many are planning tickets) |
| Store presence | GitHub APK only that I could verify; users ask for F-Droid | GitHub APK, Obtainium |
| Product Hunt | 1 upvote, 2 followers on the page I fetched (a search snippet said 0 upvotes, rank #86; I report both because they differ); launched about a month earlier. No comments | none |
| HN | no Show HN found for AndCode. The author posted "OpenClaw Assistant" (a different app) on HN | none yet |
| Docs site | yuga-hashimoto.github.io/and-code (English + Japanese README) | eslamasabry.github.io/opencode-mobile-next |

Their downloads are about 15x ours and stars about 20x. They grew from zero
to 140 stars in 12 weeks; both have no store listing. Their issue tracker is
mostly users from Android 16 phones (Samsung, Xiaomi, OPPO, OnePlus).

### Feature by feature

| Area | AndCode | Ours | Ahead |
|---|---|---|---|
| Runs agent on the phone | Alpine via PRoot; OpenCode (stable), Claude Code, Codex, Antigravity (beta) | Ubuntu via proot; OpenCode and Claude Code verified; Codex, Gemini CLI, Qwen Code, Goose, Pi on same path, unverified on a phone | **AndCode** on shipped agent breadth (Antigravity, Codex tested by users); us on Ubuntu (glibc, no second rootfs) and Gemini/Qwen/Goose |
| Connect to a computer | OpenCode only; LAN, Tailscale, QR, mDNS discovery | OpenCode 1 and 2 (QR, address), Claude Code/Codex/Pi through Paseo | **Us** (more agents), AndCode on mDNS discovery |
| Setup time | "~2 min on a good connection" (README) | about 4 min, about 210 MB (COMPETITORS.md) | AndCode (claim; not timed by me) |
| Chat with live steps / folding | Collapsible reasoning, tool calls, command output | One live line, folded steps, Stop on the field | Even; we are more opinionated |
| Tool approvals | Approve/reject; never auto-approves | Approve above field; Always-allow with confirm; widgets | **Us** |
| Approve without opening the app | Home-screen widget fires a prompt only | Allow, Reject, Stop, Steer, voice from widgets | **Us** |
| Steer a running reply | not listed | Yes (OpenCode 2, Codex, Claude Code) | **Us** |
| Diff review | Inline diff viewer; Changes tab needs git; no files-changed list (their #330) | Diffs wrap to phone width; Files and Changes; per-file accept open (#27) | Roughly even |
| Files | File tree, syntax highlight, all-files access to /sdcard | Files tab, project file picker, previews | AndCode on device-wide file access |
| Terminal | one command at a time, 30 s kill, 4 KB cap, no PTY (their #329) | xterm over a real PTY in Ubuntu (design doc; build behaviour not checked by me) | **Us** (if it ships as designed) |
| Git | Status, diff, branch, PR badges pinned above the composer | Changes, worktrees (Claude Code chats), commit prompts in AI Team | AndCode on PR badges |
| Scheduled prompts | Yes, one-time or cron, with run history | Check-in reminders only | **AndCode** |
| Multi-agent project work | none | AI Team: planner, workers, checker, receipts, merge to dev | **Us** (proven on emulators only) |
| Hand a chat to another agent | "Continue on another runtime" (summary prompt, local to remote) | Continue with... (masked record, links both chats, agent to agent) | **Us** |
| Voice | Push-to-talk, wake word, text-to-speech | On-device transcription, voice start from widget | AndCode on wake word and read-aloud; us on privacy |
| Digital assistant (home gesture) | Yes | no | **AndCode** |
| Widgets | One prompt widget | Three sizes, actions, usage meter, styling | **Us** |
| Model / provider UX | Dynamic models, favourites, searchable | Picker, reload providers, device-code sign-in | Even |
| Inbox / many servers | Chat home with recent sessions | Inbox across servers, While you were away | **Us** |
| Look | Dark/light, themes | 20 live backgrounds, text size, light and dark | Subjective; see their #279 "theme light poor" |
| Languages | 8 (en, ja, zh, ru, es, fr, pt-BR, ar) | English and Arabic | **AndCode** |
| Privacy | Keystore for credentials; local runtime | Keystore; voice stays on phone; no analytics; diagnostics only on send | Even; we state more |
| Update path | Release APKs | Shorebird patches for Dart-only fixes; Obtainium | **Us** |
| Codex images | Fixed 2026-10-08 (their #375) | n/a | n/a |

### What AndCode does better
1. **Momentum and trust signals:** a release every 1-3 days, issues closed
   in under a day, 140 stars. (Release list and issue dates, GitHub API.)
2. **Smaller, simpler first run:** about 62 MB APK, "about 2 min" setup,
   four agents tested by users on many phones in the issue tracker.
3. **Scheduled prompts with run history**, pull-request badges, wake word,
   text-to-speech, digital assistant, 8 languages, whole-phone file access.
4. **A developer who writes down his own limits** in `docs/LOCAL_RUNTIME.md`
   (for example, "no JVM works in PRoot", which a user then disproved in
   [#356](https://github.com/yuga-hashimoto/and-code/issues/356), 2026-09-27) and merges user fixes.

### Where we are clearly ahead
- Acting without opening the app (widgets: Allow/Reject/Stop/Steer/voice).
- Steering a running reply; always-allow with confirm.
- Hand a conversation to a different agent with a masked record.
- AI Team (planner, workers, checker) with receipts and a "nothing reaches
  main until you confirm" rule (emulator-proven only).
- Several servers at once with one inbox; Claude Code/Codex/Pi through Paseo.
- A real terminal on a PTY (their own open issue #329 says theirs is not).
- Shorebird over-the-air patches; Arabic RTL work.
- Their crash volume is visible in the tracker (Compose duplicate-key crash
  in an existing chat, [#310](https://github.com/yuga-hashimoto/and-code/issues/310), [#311](https://github.com/yuga-hashimoto/and-code/issues/311)); ours must prove stability too, not just claim it.

**Headline:** AndCode is the same idea (an Android app that installs its
own Linux and runs OpenCode and Claude Code on the phone, MIT, free) with
about 20x our stars and 15x our downloads after 12 weeks. We are ahead on
what you can do without opening the app, on multi-agent work and on hand-off;
they are ahead on adoption, release speed, size, scheduled prompts and
language count. We should not say "first".

---

## 3. The five highest-value gaps to build next

Ranked by evidence strength times cost. Size: S under a day or two, M a
week, L several weeks.

### Gap 1. First-run reliability and a faster "first answer" (S to M, then ongoing)
- **Why:** Category 1 is the most repeated complaint across every project,
  and our newest outside user says it directly: "Cant download ai team on
  phone no matter how many retries... it takes 20 sec to even load a
  setting chat send message" ([#87](https://github.com/Eslamasabry/opencode-mobile-next/issues/87)). AndCode's tracker is dominated by "app not working"
  and crash reports on Android 16 phones. Happy's top bug (+34) was a
  cannot-start error.
- **What:** device-matrix a first run on 3-4 real phones; make setup restart
  in place; a visible "what is it doing" step list with time left; one-tap
  "send report". Our README already says "Report a problem" exists. Prove it
  with `docs/qa/` evidence.

### Gap 2. A store-quality install: Google Play or F-Droid/Izzy, smaller APK (M, partly paperwork)
- **Why:** Users on two competitors ask for F-Droid ([and-code#267](https://github.com/yuga-hashimoto/and-code/issues/267), [opencode-mobile#45](https://github.com/alvarolorentedev/opencode-mobile/issues/45)) and the
  OpenCode-client rivals (dzianisv, P4OC, alvarolorentedev) are on Play.
  "Download an APK from GitHub" is the biggest drop-off for non-developers,
  and we are about 100 MB.
- **What:** follow `IZZYONDROID.md` and our issues #41, #42; drop the APK
  size where models/assets allow. Note IzzyOnDroid's rule on AI-built apps
  (COMPETITORS.md) before promising a date.

### Gap 3. Scheduled and background runs with a morning digest (M)
- **Why:** AndCode's maker leads with scheduled tasks; user asks for startup
  commands and services ([and-code#379](https://github.com/yuga-hashimoto/and-code/issues/379)); HN readers say the point is leaving work
  running ("the natural workflow would be to context-switch ... while Claude
  works", [claude-code#72348](https://github.com/anthropics/claude-code/issues/72348)). We already have AI Team digests ("Since you were
  away") and check-ins, so the piece missing is "run this prompt at 7:00 in
  this project".
- **What:** recurring prompt on a phone-hosted agent, run history, result
  notification and a widget tile. Must respect Android 15 `dataSync` limits;
  say that plainly.

### Gap 4. Dev-server preview and clickable localhost links (M)
- **Why:** The most specific feature request from the only AndCode power user
  who files regularly (three issues: [#362](https://github.com/yuga-hashimoto/and-code/issues/362), [#374](https://github.com/yuga-hashimoto/and-code/issues/374), [#379](https://github.com/yuga-hashimoto/and-code/issues/379)), and MobileCode's
  Show HN ("OpenCode with Built-In iOS and Android Previews") shows the
  market wants it. For a phone-only developer, "see the result" is the loop.
- **What:** `localhost:PORT` links in chat open an in-app preview of the
  phone's own server; a floating "Preview" button; split-screen friendly.
  Every non-app URL must still go through `openExternalLink` (AGENTS.md);
  the in-app preview of the phone's own loopback is a deliberate, local
  exception and needs a security note.

### Gap 5. Plain HTTP / self-signed server trust, done safely (S to M)
- **Why:** Two outside issues in our own tracker ([#85](https://github.com/Eslamasabry/opencode-mobile-next/issues/85) HTTP; #38 TLS), the
  same ask in P4OC#1 and doza62#16. It blocks the "connect to my home
  server" path, the only path for people who do not want Tailscale.
- **What:** a per-server "I trust this address" choice with a fingerprint or
  LAN-only rule, named warnings in plain words, and Tailscale as the
  recommended path. Do not allow silent cleartext on public addresses.

Honourable mentions (not in the five): per-file accept/reject (#27), diff
highlight (#26), push-to-talk and non-English transcription (#35, #36),
PR badges, wake word, Android keyboard/rotation sweep (item 12), more UI
languages.

---

## 4. Messaging implications

### Words users actually use (copy these)
- **"from my phone"**, "on the go", "from anywhere", "while I do chores"
  ("building a sizable feature while doing chores", [HN 44912638](https://news.ycombinator.com/item?id=44912638), 2025-08-15).
- **"no terminal"**, "not just being dropped into a console", "a native
  experience tailored for coding agents" (Omnara user).
- **"no PC"**, "without SSH-ing into a PC or carrying a laptop" (AndCode's
  Product Hunt maker comment).
- **"approve"**, "grant permission", "ask me" - not "authorise" or "HITL".
  Happy's users: "can neither approve nor decline".
- **"it just works"**, **"reliable"**. Happy was criticised for "rarely
  works reliably" (HN 46742298). Reliability is the word to win.
- **"my own keys"**, "free", "no subscription", "no vendor lock-in".
- **"Claude Code"**, **"Codex"**, **"OpenCode"** by name; people ask for
  each one by name in the titles of their issues.
- **"notifications"**, **"when it's done"**, **"page my phone"**.
- Words to avoid: "AI Team" as a lead (they never ask for it), "multi-agent
  orchestration", "ACP", "gateway", "protocol", "proot" in headlines.

### Claims that are credible against AndCode (all checkable)
1. "Allow, Reject, Stop and Steer from the home screen" (1.4.0 notes;
   emulator-verified for OpenCode 1). AndCode's widget only sends a prompt.
2. "Hand a chat to a different agent" (Continue with...). Say "built and
   tested", not "proven on phones" until the device pass exists.
3. "Steer an agent while it works" (1.4.0). Nobody else in this set lists it.
4. "One list of chats across your phone and your computers" (README).
5. "Free, MIT, no ads, no analytics, your own keys" - true for both; do not
   present it as unique versus AndCode, only versus Omnara, Cosyra and
   Remote Control (which need a plan or a key-less login).
6. "Works with Claude Code and OpenCode on the phone itself" - true for both,
   so do not say "only".

### Claims to avoid (not supportable on 2026-10-15)
- "First", "only", "best" (COMPETITORS.md already says so). AndCode shipped
  PRoot-on-phone in July.
- "Smaller", "faster setup", "more agents on-device": AndCode's APK is
  smaller and it ships Antigravity and Codex support that users have tried.
- "Stable", "works on every phone": our README says public alpha, and our
  newest outside user reports stuck downloads.
- "Real terminal / full PTY" until the shipped build is verified by someone
  other than a design doc (item 11).
- "AI Team works on your phone": it is emulator-proven only; README says so.
- "Runs all night": the Android 15 6-hour `dataSync` cap.

### Suggested one-liners (for POSTS.md / site)
- "Approve your coding agent from the home screen. Allow, Reject, Stop,
  Steer. Free, MIT, runs on your phone."
- "Start it on the phone, finish it on the laptop, or hand it to a
  different agent. One list of chats." (hand-off + inbox)
- When contrasting: "AndCode is a good Android app for running agents on a
  phone, with scheduled prompts and 8 languages. If you want to act on the
  agent without opening the app, steer it mid-run, or move a chat between
  agents, that is what we added in 1.4.0."

### Where the audience is (from this research)
The most active conversations were Hacker News "Ask HN: How do you AI code
from your phone?" (2026-01-24; the OP was an Android user) and Launch HN /
Remote Control threads. COMPETITORS.md notes Show HN rarely clears 10 points
in this category; an Ask HN reply with a real answer to "what do you use?"
may do more than a launch post. Reddit could not be checked; do that by hand.

### Sources not reachable or not found
- reddit.com (blocked for this tool), Play Store review text (404 URLs),
  App Store reviews (not mined), Cosyra reviews, Happy's Android store page.
- Sources used and linked above: GitHub issues and releases for
  yuga-hashimoto/and-code, slopus/happy, getpaseo/paseo,
  dzianisv/opencode-mobile, doza62/opencode-mobile, theblazehen/P4OC,
  alvarolorentedev/opencode-mobile, grapeot/opencode_ios_client,
  K9i-0/ccpocket, ferrumclaudepilgrim/claude-code-android,
  anthropics/claude-code; Hacker News items 47148454, 46991591, 44878650,
  46742298, 47843841, 44904039; Product Hunt page
  https://www.producthunt.com/posts/1243053; AndCode README and site.
