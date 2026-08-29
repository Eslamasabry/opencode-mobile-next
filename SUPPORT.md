# Support

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team. It is
maintained by one person in their own time, in a preview line that is not yet
stable.

**Security problems do not belong here.** See [SECURITY.md](SECURITY.md) and
report privately.

## Is it this app?

Five different projects are in the room when you use this. Sending a report
to the wrong one costs everybody a week.

| Symptom | Where it belongs |
|---|---|
| The Android app crashes, renders wrong, loses state, or a control does nothing | **Here** — open a bug report |
| A tool ran, was refused, or produced the wrong result on the server | The **OpenCode server** project — reproduce with the `opencode` CLI first |
| The model is slow, refuses, hallucinates, or bills you oddly | Your **model provider** |
| `proot-distro`, `pkg`, or the Ubuntu chroot fails on its own | **Termux** / proot-distro |
| A code-push update fails to download or apply | **Shorebird**, unless the app's own update UI is what is broken |
| An APK will not install, or Android warns about the signer | Read the signing section of the [README](README.md) first — sideload builds are signed with the project's own certificate |

**The one test that sorts most of it:** run the same thing against the same
server with the `opencode` CLI on the machine. If the CLI does it too, it is
not this app.

## Before opening an issue

1. **Update.** Only the current preview is supported. The bug may already be
   gone.
2. **Search** [existing issues](https://github.com/Eslamasabry/oc_app/issues?q=is%3Aissue).
3. **Collect versions** — app version and build from **Settings → About**,
   Android version and device, OpenCode server version, and whether the
   server is v1 or v2.
4. **Note how you connect** — adb reverse, SSH forward, TLS/Tailscale, or
   on-device Termux. A surprising share of connection reports come down to
   this.
5. **Redact.** Never paste a server password, a provider API key, a real
   server hostname, or an unredacted session transcript. The app's
   diagnostics are redacted and explicit-only for this reason; check what you
   are about to paste anyway.

## Where to go

- **Bug in the app** — [open a bug report](https://github.com/Eslamasabry/oc_app/issues/new?template=bug_report.yml)
- **Feature or change** — [open a feature request](https://github.com/Eslamasabry/oc_app/issues/new?template=feature_request.yml)
- **Question, idea, or "is this supposed to work?"** —
  [Discussions](https://github.com/Eslamasabry/oc_app/discussions)
- **Vulnerability** — [private advisory](https://github.com/Eslamasabry/oc_app/security/advisories/new),
  never a public issue
- **Want to fix it yourself** — [CONTRIBUTING.md](CONTRIBUTING.md)

## What support actually means here

There is no SLA and no support contract. Issues are read; they are not
guaranteed a reply, a fix, or a timeline. Security reports are prioritized
over everything else. Well-scoped bug reports with a reproduction get
attention far sooner than "it doesn't work".

Known gaps are not a secret: the two audits under
[docs/audits/](docs/audits/) and the
[post-remediation status](docs/audits/post-remediation-status-2026-08-29.md)
list what is still open. Checking there before filing may save you the write-up.
