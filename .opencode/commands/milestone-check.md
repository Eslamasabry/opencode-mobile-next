---
description: Audit whether dev is ready for an explicitly approved promotion to master without publishing anything.
agent: mobile-lead
---

Assess milestone readiness for: $ARGUMENTS

Load `milestone-delivery`. Delegate independent checks to the delivery,
quality/accessibility, protocol, state/security, and platform specialists.
Inspect all commits and changes that would move from `dev` to `master`, verify
tests and artifact eligibility, and return blockers plus a concise promotion
checklist. Do not modify branches, push, tag, publish, upload, or run Shorebird
publishing commands.
