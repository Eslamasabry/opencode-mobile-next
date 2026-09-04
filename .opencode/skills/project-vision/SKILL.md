---
name: project-vision
description: Use for OpenCode Mobile roadmap, architecture, prioritization, UX, or cross-cutting implementation decisions.
---

# Project Vision

OpenCode Mobile is a touch-first Android control surface for an OpenCode server
the user controls. Optimize for securely starting, steering, inspecting, and
approving coding work from a phone.

## Decisions

1. Android phone UX comes first; desktop remains secondary.
2. Provider authentication must be completable in the mobile UI.
3. Protocol parity is measured by user intent and truthful behavior.
4. UI uses domain/controller operations and capability gates, never flavor checks.
5. Content and pending decisions outrank navigation chrome and dashboards.
6. Hidden gestures need visible accessible alternatives.
7. Security and privacy are product behavior, not cleanup work.
8. Evidence from tests, contracts, or devices outranks plans and stale audits.
9. Prefer the smallest correct implementation and reuse established patterns.
10. Develop on `dev`; promote major milestones to `master`; prefer Shorebird.

Before changing behavior, read `AGENTS.md`, `CONTRIBUTING.md`, and the relevant
current source. Treat dated audit documents as historical until reverified.
