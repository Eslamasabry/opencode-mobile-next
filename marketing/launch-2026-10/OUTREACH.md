# Outreach: exact entries, diffs and messages

Researched 2026-10-15. Nothing here has been submitted. Each item says who
submits it. All lists were read from their repos on that date.

Facts to reuse (from README.md and the release notes): "OpenCode Mobile",
repo https://github.com/Eslamasabry/opencode-mobile-next, MIT, Flutter (Dart)
plus Kotlin, Android 8 or newer, APK on GitHub releases, created 2026-09-02,
7 stars on 2026-10-15.

Eligibility summary (stars and age are our current numbers):

| List | Rule that matters | Eligible now |
| --- | --- | --- |
| OpenCode docs ecosystem page | Submit a PR | yes |
| awesome-opencode | Public, relevant, commits in the last 6 months, unique | yes (maintainer last pushed 2026-07-03) |
| awesome-claude-code | 14 days old with active development, or 100 stars; one recommendation at a time; web form only | yes |
| open-source-android-apps (pcqpcq) | Public repo, open source | yes |
| Open App Scout (tortuvshin/open-apps) | A usable app with a public repo and verifiable license; not a library or demo | yes |
| awesome-ai-devtools (jamesmurdza) | Developer-focused tool that uses AI; style match | yes |
| awesome-flutter (Solido) | "35 stars minimum are required to apply" | no (7 stars) |
| awesome-cli-coding-agents (bradAGI) | "Must have a CLI or terminal interface" | no (we are not a CLI) |
| awesome-coding-agent (jqueryscript) | every project has 500+ stars (as described by a search snippet) | no |
| codepick.dev | Suggestion by GitHub issue | yes |
| Console.dev | Pre-1.0 or beta tool | borderline |
| Android Weekly | Link suggestion form; editors choose | only with an article |

---

## 1. OpenCode ecosystem page (official docs)

Repo: https://github.com/anomalyco/opencode
File: `packages/web/src/content/docs/ecosystem.mdx`, section "## Projects".
Page text: "Want to add your OpenCode related project to this list? Submit a PR."
CONTRIBUTING.md: documentation PRs are exempt from the linked-issue check;
"Keep PRs small and focused"; commit style `type(scope): summary`; "Long,
AI-generated PR descriptions and issues may be ignored. Write a short
explanation in your own words." So the PR description should be two lines,
written by the owner.

Diff to propose (one row, placed after the CodeNomad row; the table has
aligned columns, so pad to match):

```diff
 | [CodeNomad](https://github.com/NeuralNomadsAI/CodeNomad)                                   | Desktop, Web, Mobile and Remote Client App for OpenCode          |
+| [OpenCode Mobile](https://github.com/Eslamasabry/opencode-mobile-next)                     | Android app that runs OpenCode on the phone itself, or connects to your server |
```

Commit message: `docs: add OpenCode Mobile to ecosystem projects`

PR description (owner writes it in their own words; suggested content):

    Adds OpenCode Mobile, an independent Android app. It runs OpenCode in an
    Ubuntu sandbox on the phone, or connects to an OpenCode server. MIT.

Note: the name must not imply it is official. Our README states it is not
affiliated; keep "independent" visible in the repo description.

## 2. awesome-opencode

Repo: https://github.com/awesome-opencode/awesome-opencode (10.6k stars; last
push 2026-07-03). Instructions from its contributing.md: add a YAML file in
the category folder; "Filename: kebab-case"; open a PR; automated YAML
validation, then maintainer review; README regenerates.
Entry requirements it lists: Relevant, Public, "Maintained - Active commits
within the last 6 months", Unique, Complete.

New file `data/projects/opencode-mobile-next.yaml`:

```yaml
name: OpenCode Mobile
repo: https://github.com/Eslamasabry/opencode-mobile-next
tagline: Android app that runs OpenCode on the phone itself or connects to your server.
description: Independent, MIT-licensed Android app. Guided setup installs an Ubuntu sandbox inside the app and runs OpenCode (1 or 2) and Claude Code there, with no computer or Termux. It can also connect to an OpenCode server by QR code or address. Streaming chat, permission approvals, diffs, home-screen widgets and an AI Team mode.
```

(The tagline is 78 characters, under the 120 limit.)

Commit message: `docs: add opencode-mobile-next to projects`
Uniqueness note for the PR body: the list already has dzianisv/opencode-mobile
and P4OC (Android clients that connect to a server). Ours also runs OpenCode on
the phone; say that, not "better".

## 3. awesome-claude-code (hesreallyhim)

Submit through the web issue form only (CONTRIBUTING.md: "Do not open a PR";
"It is **not** possible to submit a resource recommendation using the `gh`
CLI"; "resource recommendations must be created by human beings"):
https://github.com/hesreallyhim/awesome-claude-code/issues/new?template=recommend-resource.yml

Form fields and what to enter (the form's own field names):

- Display Name: `OpenCode Mobile`
- Category: `Alternative Clients` (the other candidate is `Remote Control,
  Notifications & Voice I/O`; the first fits an app with its own chat UI)
- Link: `https://github.com/Eslamasabry/opencode-mobile-next`
- Author Name: `Eslamasabry`
- Author Link: `https://github.com/Eslamasabry`
- Description (1 to 3 sentences, 10 to 500 characters, "Descriptive, not
  promotional. Don't address the reader"; per CONTRIBUTING one line, no emoji):

      An Android app that installs an Ubuntu sandbox inside itself and runs Claude Code and OpenCode on the phone, or connects to agents running on a computer. Provides streaming chat, tool approvals, diffs, Files, Changes and a terminal for Claude Code conversations, and home-screen widgets to allow, stop or steer a running agent. MIT licensed and independent.

  (Length: 357 characters.)
- Checklist: the owner ticks the boxes truthfully; "This resource is specific
  to Claude Code" is only partly true (the app also runs OpenCode), so note in
  the form's free text, if any, that Claude Code is one of two supported
  agents. Leave the final box unchecked, as the form instructs.

Timing note from the maintainer: the list prefers projects that already have
users. Submit after the Reddit posts, and submit only one resource.

## 4. open-source-android-apps (pcqpcq, 10.5k stars)

Repo: https://github.com/pcqpcq/open-source-android-apps . Three ways in per
CONTRIBUTING.md: a GitHub Actions workflow ("Actions -> Add New App -> Run
workflow", which needs the category, app name and repo URL), a helper script
`scripts/add_app.py`, or a manual PR. Rules: "Open-source only", one app, one
primary category, "One change per commit", alphabetical order by name.
Run `python scripts/check_repo.py --skip-counts` before the PR.

Category file: `categories/tools.md` (Termux, ConnectBot and a similar AI agent
app, Verdroid, are listed there). Row to add in alphabetical position (after
the "O..." rows that precede "OpenCode"):

```
| [**OpenCode Mobile**](https://github.com/Eslamasabry/opencode-mobile-next) | Runs OpenCode and Claude Code on the phone inside an Ubuntu sandbox, or connects to agents on a computer. | `Dart` | `MIT` | 7 | [Releases](https://github.com/Eslamasabry/opencode-mobile-next/releases/latest) |
```

(The stars column is refreshed by their daily action. Language: GitHub reports
Dart as the largest language (about 43 MB, Kotlin about 1.6 MB). Download column: per the rules a releases link is allowed.)

Commit message: `Add OpenCode Mobile to Tools`

## 5. Open App Scout (tortuvshin/open-apps)

Site: https://openappscout.com . Repo: https://github.com/tortuvshin/open-apps
(4.4k stars). Route per CONTRIBUTING.md: open `/submit` on the site, paste the
GitHub URL, answer the questions, then "Open pull request"; explain in the PR
why the app is useful. Inclusion: "a usable application, not a library, tutorial,
boilerplate, one-screen demo, or marketing-only page. It needs a public source
repository, a verifiable open-source license, and enough documentation".

If hand-writing the record, file `content/records/opencode-mobile.md`:

```markdown
---
name: OpenCode Mobile
repoUrl: https://github.com/Eslamasabry/opencode-mobile-next
category: developer-tools
stack: flutter
description: Android app that runs OpenCode and Claude Code on the phone in an Ubuntu sandbox, or connects to agents on a computer.
platforms:
  - android
tags:
  - ai-agents
  - coding-agents
addedAt: <date of submission>
submittedBy: Eslamasabry
---

## Why it's listed

Runs coding agents on the phone itself rather than only remote-controlling a
computer; MIT licensed; public alpha, APK from GitHub releases.
```

The `/submit` page checks the fields against `data/taxonomy/`; categories and
stacks above (`developer-tools`, `flutter`) exist there. The distribution
channel id for us is `github-releases` (not `play-store` or `fdroid`).

## 6. awesome-ai-devtools (jamesmurdza)

Repo: https://github.com/jamesmurdza/awesome-ai-devtools (3.9k stars). The PR
template says "This list is for developer tools exclusively" and the checklist
asks: a tool that uses AI, developer-focused, unambiguous description, style
matching other entries. Section: "Desktop & Mobile Applications" (contains
IM.codes and Onepilot, which are our closest neighbours).

Line to add at the end of that section:

```
- [OpenCode Mobile](https://github.com/Eslamasabry/opencode-mobile-next) — Android app that runs OpenCode and Claude Code on the phone in an Ubuntu sandbox, or connects to agents on a computer. Streaming chat, tool approvals, diffs and home-screen widgets. MIT, independent.
```

## 7. codepick.dev "Mobile AI Coding Tools 2026"

Page: https://codepick.dev/en/guides/mobile-ai-coding-tools-2026/ (research
dated 2026-06-05; "Personal project - Non-commercial"). Contact route: GitHub
issues at https://github.com/WhiteWorld/codepick/issues . The repo's open issues
show the pattern ("Suggest VoiceVibe for the voice tools comparison"; another
suggestion begins with a conflict-of-interest disclosure "(I am the
maintainer)"). So disclose that we are the author. The page's category 2 is
"Open a development runtime from the phone", which lists Cosyra, Codem, Open
Minis and Termux + Tailscale/SSH; we belong there.

Issue title: `Suggest OpenCode Mobile for "Open a development runtime from the phone"`

Issue body (the owner posts it; a human, with the disclosure kept):

    Disclosure: I am the author of this project.

    OpenCode Mobile is an independent, MIT-licensed Android app that installs
    an Ubuntu (proot) sandbox inside the app and runs OpenCode and Claude Code on
    the phone, with no computer and no Termux. It can also connect to
    OpenCode on a computer.

    - Repo: https://github.com/Eslamasabry/opencode-mobile-next
    - Latest release: https://github.com/Eslamasabry/opencode-mobile-next/releases/latest
    - Distribution: GitHub releases APK only (not on Play or F-Droid)
    - Status: public alpha; on-phone path verified for OpenCode and Claude
      Code, other agents (Codex, Gemini CLI, Qwen Code, Goose, Pi) share the
      path but are not verified on a physical phone
    - Data: no account or ads; usage and crash reports are off until you turn them on; the user brings a model provider

    Happy to correct anything in the entry. The page is dated 2026-06-05, so
    some details above may be newer than your data.

## 8. Other roundups: contact routes

No email addresses were published on these pages; the routes below are the
public ones I found. The guides mostly come from companies that sell
something, so expect low response; a short factual note is enough. Do not
contact competitors' own guides (Nimbalyst, Cosyra), since they are vendor
marketing.

| Page | Route | Note |
| --- | --- | --- |
| https://sealos.io/blog/claude-code-on-phone/ (dated 2025-11-27) | Contact link on the site; X @Sealos_io | Vendor blog, old; low odds |
| https://www.explainx.ai/blog/claude-code-mobile-remote-control-phone-guide-2026 | X @explainx_ai; LinkedIn company page | Guide lists Termux and remote tools |
| https://felloai.com/how-to-use-claude-code-on-your-phone/ | X @fello_ai | General AI site |
| https://sotaaz.com/post/mobile-claude-code-en (2026-07-07) | X @sotaaz_lab | Compares three approaches |
| https://zilliz.com/blog/3-easiest-ways-to-use-claude-code-on-your-mobile-phone | X @zilliz_universe | Vector DB vendor; poor fit |

Message (X reply or contact form), short and factual:

    Hi, I read your guide on running Claude Code from a phone. In case it is
    useful for an update: OpenCode Mobile is a free, MIT-licensed, independent
    Android app that runs Claude Code and OpenCode on the phone itself (it
    installs an Ubuntu sandbox inside the app, no computer or Termux), or
    connects to agents on a computer. Repo and APK: 
    https://github.com/Eslamasabry/opencode-mobile-next . I am the author; it
    is a public alpha and the on-phone path is verified for OpenCode and
    Claude Code only. No action needed if it does not fit.

## 9. Newsletters

### Console.dev

Route: email hello@console.dev (published on
https://console.dev/selection-criteria). Their criteria: "Each week we feature
and review 2-3 interesting developer tools"; "Is the primary user a
developer?"; self-service signup; "Is it being actively maintained"; and for
betas, "the release must be pre 1.0 and/or have an appropriate label ... the
word 'beta', 'preview' or similar should appear somewhere easily discoverable
on the website, release notes, and/or documentation. Any GA or stable releases
are not eligible." Our version is 1.4.0, and the README says "public alpha",
so it may count as alpha; that is the owner's decision on how the project
labels itself.

Email text:

    Subject: OpenCode Mobile: coding agents that run on an Android phone (public alpha, MIT)

    Hi Console team,

    I am the author of OpenCode Mobile, a free, MIT-licensed, independent
    Android app (public alpha) that installs an Ubuntu sandbox inside the app
    and runs coding agents (OpenCode, Claude Code) on the phone, with no
    computer and no Termux. It also connects to agents running on a computer.
    1.4.0 added home-screen widgets that Allow, Reject, Stop or Steer a
    running agent without opening the app.

    Repo: https://github.com/Eslamasabry/opencode-mobile-next
    APK (about 100 MB, no signup, no account): 
    https://github.com/Eslamasabry/opencode-mobile-next/releases/latest

    It is built with substantial AI assistance (stated in the README) and is
    not affiliated with OpenCode or Anthropic. Limits: on-phone path verified
    for OpenCode and Claude Code only; you bring your own model provider.

    Thanks for considering it.

### Android Weekly

Route: the "Submit" link at https://androidweekly.net/ (#submit-stuff); the
form text is "Suggest a link for an upcoming Android Weekly Issue." and
"Links in each issue will be chosen by us. Please be aware that we can't pick
every link." Recent issues are articles about Kotlin and Compose; an app
launch is unlikely to be chosen. Better: first publish an engineering article
(for example, "Running a Linux userland on Android 15 with proot and
foreground-service limits") on a blog or dev.to, then submit that link. The
article does not exist yet.

Form entry (for the article, when it exists): the article URL and a one-line
description; fill what the form asks for.

### TLDR

No link-submission route found. The only route found is paid sponsorship
(https://sponsorships.tldr.tech/). Do not pay without the owner's decision.

## 10. Not recommended

- Contacting Cosyra, Nimbalyst, Paseo or Happy: they are competitors; a
  courtesy note is possible but gains nothing.
- Paid lists and "submit your app" directories that charge or ask for a
  backlink: not researched; do not use without the owner's decision.
