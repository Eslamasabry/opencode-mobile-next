# OpenCode Mobile Agent Guide

## Vision

OpenCode Mobile is the touch-first Android control surface for an OpenCode
server the user controls. It must let people securely start, steer, inspect,
and approve real coding work from a phone, with honest behavior across both
OpenCode protocols and provider authentication available inside the app.

Android phone UX is the product. Linux and Windows are useful secondary
surfaces, but they do not displace mobile reliability, security, or protocol
parity.

## Branches And Delivery

- Do all normal work on `dev`. Features, fixes, dependencies, docs, and release
  preparation all land there.
- Treat `master` as the reviewed milestone and production-source branch.
- Promote `dev` to `master` only for an explicitly approved major milestone.
- Do not create release, APK, freeze, or parallel development branches.
- Do not stop work on `dev` while a milestone is being prepared. Promotion
  selects a reviewed commit; it does not freeze development.
- Create tags only from a clean, synchronized `master` after explicit approval.
- Prefer Shorebird for public Android baselines and eligible Dart-only patches.
  A raw Flutter APK is a compile/test artifact, not a public release artifact.
- Shorebird does not cover desktop packages; use verified GitHub artifacts for
  Linux and Windows when those are intentionally released.
- Never push, tag, publish, upload, run a Shorebird release/patch, or alter
  signing material unless the user explicitly requests that exact action.

## Collaboration

- For substantial work, the lead should delegate independent research or
  implementation slices to multiple specialists early.
- Give each editing agent exclusive file ownership. Never let two agents edit
  the same file concurrently.
- Keep one coordinator responsible for integration, final diff review, and the
  full quality gate.
- Use research-only agents to audit cross-cutting behavior before broad edits.
- Prefer the smallest correct change. Do not add compatibility code, release
  machinery, or abstractions without a concrete need.
- Preserve unrelated worktree changes. Never reset or discard work you did not
  create.

## Product Principles

- Optimize for one-handed use, keyboard-visible layouts, 360dp width, 2.5x
  text, TalkBack, and 48dp touch targets.
- Give every hidden gesture a visible accessible alternative.
- Keep content, diffs, prompts, and pending decisions ahead of navigation chrome.
- Put provider authentication and credential lifecycle inside the mobile UI.
- Compare protocol behavior by user intent, not endpoint names or endpoint count.
- Gate UI with capabilities, never protocol flavor checks.
- Treat server access like shell access. Protect credentials, paths, prompts,
  diagnostics, external links, notifications, and profile-scoped data.
- Evidence outranks roadmap claims. "Implemented" means verified code or device
  evidence.

## Architecture

- `lib/api/`: OpenCode 1 client and generated-SDK facade.
- `lib/api2/`: handwritten OpenCode 2 transport, models, events, and gateways.
- `lib/domain/`: shared product contracts.
- `lib/state/`: connection lifecycle, profiles, queues, drafts, and handoffs.
- `lib/ui/`: screens and widgets; no direct transport calls.
- `lib/background/`, `lib/termux/`, `lib/voice/`: platform-sensitive runtime.
- `packages/opencode_sdk/`: generated code; never edit it manually.
- `contracts/`: pinned wire evidence and generated-SDK provenance.

UI must use domain/controller operations and capability gates. Server-derived
URLs must use the shared external-link policy. New profile-scoped storage must
be deleted with its profile.

## Quality Gate

Use Flutter 3.47.2. Run focused tests first, then before declaring substantial
work complete run:

```bash
flutter analyze --no-pub
flutter test --no-pub --concurrency=1
```

Run `flutter gen-l10n` after ARB changes. Do not add `flutter_animate`; periodic
timers and animations must be disposed and tests must not wait forever on them.
Tests touching `ProfileStore.load` or `upsert` must mock secure storage.

## Specialist Routing

- `protocol-parity`: load `dual-protocol` for OpenCode 1/2 contracts, gateways,
  events, and capabilities.
- `mobile-product`: load `mobile-design-system` and `flutter-quality` for
  touch-first UI, themes, typography, motion, and adaptive interaction.
- `state-security`: load `secure-state` and `connection-lifecycle` for lifecycle,
  persistence, credentials, privacy, and trust boundaries.
- `platform-runtime`: load `platform-runtime` for Android channels, Termux,
  voice, background work, notifications, and desktop bridges.
- `test-accessibility`: load `quality-review` and `flutter-quality` for test
  design, localization, accessibility, adaptive layouts, and regression review.
- `milestone-delivery`: load `milestone-delivery` for branch promotion,
  Shorebird eligibility, CI, signing, and artifacts.

Load the matching project skill before specialized work. The lead integrates
results and resolves cross-domain decisions.
