# OpenCode Mobile audit reports

> Historical record. Statements below about CI never having run were accurate
> when these audits were written. Current Android, Linux, Windows, and SDK
> workflows run on GitHub Actions; use the README and open issues for live status.

OpenCode Mobile is an independent community project. It is not built,
maintained, endorsed by, or affiliated with the official OpenCode team.

Two independent audits reviewed the `oc_app` repository at commit
`9e7d5ce5431f8d20b8da028c20e8c71787d29384` on 29 August 2026. **Read the
status file with them** — much of what they describe has since been fixed,
and a good deal has not.

## Start here

- [**Post-remediation status**](post-remediation-status-2026-08-29.md) — every
  finding from both audits, marked Closed / Partially closed / Open /
  Accepted risk, with the commit that closed it or the reason it is still
  open. Established from `git log`, not from intent.

## The audits

- [Public launch readiness audit](public-launch-audit-2026-08-29.md) — security, privacy, release engineering, repository hygiene, architecture, quality gates, governance, launch readiness, and prioritized remediation.
- [Dedicated UI/UX audit](ui-ux-audit-2026-08-29.md) — information architecture, navigation, onboarding, chat, files, review, terminal, requests, settings, visual system, accessibility, responsive behavior, and usability validation.
- [Public launch HTML reader](public-launch-audit-2026-08-29.html)
- [UI/UX HTML reader](ui-ux-audit-2026-08-29.html)

The dated audits are not edited. Their text and their verdicts stand as
written; a dated audit that gets quietly amended stops being evidence.
Remediation is recorded in the status file instead.

## Headline verdicts, as issued

- **Public launch:** conditional no-go until the critical deployment/security, CI, data-deletion, provenance, and public-history issues are closed.
- **UI/UX:** suitable for a controlled public preview after a focused convergence pass; not yet ready to be described as polished or stable.

## Where that stands now

Both verdicts still hold. The UI/UX convergence pass happened — all six of
its launch blockers are closed — but the public-launch conditions have not
all been met. CI has never run (a GitHub account-level billing block), no
artifact has been cut and verified from a remediated commit, `PRIVACY.md` is
still wrong about drafts and queued prompts, and the history and secret-scan
work was never done. Of the seven hard launch blockers, four are closed, one
is partially closed, and two are open.

The status file has the detail, including five things that are deliberately
not being fixed and the short list of what has to happen before "public
launch" is an honest phrase.
