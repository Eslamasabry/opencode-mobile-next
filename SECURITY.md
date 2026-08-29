# Security policy

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team.

## Why this matters more than for a typical app

Please read this before deciding where to report something.

This app **stores server credentials** — the `OPENCODE_SERVER_PASSWORD` for
each server profile, held in the Android Keystore — and it **authorizes
shell-capable actions**. An OpenCode server runs commands on its host as the
user who started it, and this app is a front end for approving those
commands: it can grant tool permissions, answer agent questions, and resolve
requests from the notification shade without the app being opened.

So a defect here is not only an app defect. Anything that lets an attacker
read a stored password, approve a permission the user did not approve, point
the app at a server the user did not choose, or get a request approved out of
band, is a path to code execution on somebody's development machine.

Report it privately. Do not open a public issue, a discussion, or a pull
request that demonstrates it.

## Supported versions

**Only the current preview is supported.** This project ships a rolling
preview line; there are no maintained release branches and no backports.

| Version | Supported |
|---|---|
| Current preview (see [releases](https://github.com/Eslamasabry/oc_app/releases)) | Yes |
| Any earlier preview | No |
| `v1.0.19+20` and earlier stable cuts | No |

A fix ships in the next preview or, when it is Dart-side only, as a
Shorebird patch to the current preview. If you are running anything older,
the first step of any fix is to update.

## Reporting a vulnerability

Use **GitHub private vulnerability reporting** on this repository:

<https://github.com/Eslamasabry/oc_app/security/advisories/new>

That opens a private advisory visible only to you and the maintainer. It is
the only channel that should be used for a suspected vulnerability.

If private advisories are unavailable to you for any reason, open a public
issue that says only *"requesting a private security contact"* — with no
detail, no reproduction, and no logs — and wait to be contacted.

Please include, as far as you can:

- what an attacker gains, in one sentence;
- the app version (**Settings → About**) and Android version;
- whether the server was reached over adb reverse, an SSH forward, TLS, or
  on-device Termux, and whether it was OpenCode v1 or v2;
- a reproduction, and a proof-of-concept if you have one;
- anything you already know about scope — which stored data, which server
  actions.

**Do not include real credentials, real server addresses, session
transcripts, or unredacted diagnostics.** If a reproduction needs a password,
say so and use a throwaway one.

## What to expect

This is a single-maintainer project, not a company with an on-call rota. The
honest expectation:

- acknowledgement within **7 days**;
- an assessment, with a severity and a rough plan, within **30 days**;
- a fix in the next preview once one is agreed, prioritized over feature
  work.

If you have heard nothing after 7 days, comment on your own advisory — it
means the notification was missed, not ignored.

Coordinated disclosure: please give a fix a reasonable chance to ship before
publishing. Reporters are credited in the advisory and the release notes
unless they ask not to be. There is no bug bounty.

## Out of scope here

These are real security concerns, but they belong somewhere else:

- **The OpenCode server itself** — report to the upstream OpenCode project.
- **Your model provider** — report to that provider.
- **Termux, proot-distro, or Ubuntu in the chroot** — report upstream.
- **Shorebird** — report to Shorebird.
- **"The server can run shell commands"** — that is what an OpenCode server
  is. Documented, not a vulnerability. What *is* in scope is the app doing it
  without the user's authorization.
- **Sideload signing warnings** — these builds are signed with the project's
  own certificate, not a Play Store key, and Android says so. The fingerprint
  is published in the README so you can verify it.

## Things already known and documented

Reporting these again is welcome but will be closed as known:

- The app refuses plain HTTP to anything but the phone's own loopback;
  reaching a remote server needs adb reverse, an SSH forward, or TLS.
- CI (`android-quality.yml`) has never run — a GitHub account-level billing
  block, documented at the top of the workflow and in the README. Release
  gates were run locally.
- Findings from the two published audits are tracked, with their current
  status, in
  [docs/audits/post-remediation-status-2026-08-29.md](docs/audits/post-remediation-status-2026-08-29.md).
  Several remain open and are listed there as such.
