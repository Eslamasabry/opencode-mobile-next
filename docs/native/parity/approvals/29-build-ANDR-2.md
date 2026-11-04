RESULT: APPROVED WITH FIXES

# Approval — job 29-build-ANDR-2 (ANDR native migration, part 2)

Reviewer: Opus 5.5 (cloud). Branch `native/p12x/job-29-build-ANDR-2`. P12 head `cde0afb4` merged first
(no conflicts; one integration-record line). Reviewed the job commits `de2cbcb9` and `85c2d1bf` against
`behaviors.jsonl`, the Flutter sources in `lib/` and `test/`, `native/AGENTS.md` and plan 01. No Gradle build,
tests or APK here (Google Maven is blocked in this environment); the build VPS compiles next.

## What holds

- B-ANDR-070: `InteractiveScheduleAdmission` matches Flutter `_ensureScheduledPhoneSendAllowed` — in-app
  OpenCode only (loopback :4097, not an agent socket), a failed read fails closed with the Flutter
  `schedulePhoneCheckFailed` copy, a profile change during the read refuses as stale. Every prompt, slash
  command, shell line and queue flush passes the check; Stop ignores the result, so a lease never blocks stopping.
- B-ANDR-059…064: real saved-server enrollment is reachable from Servers; consent goes through the
  `oc.automation.<id>` policy; reads use a private bounded gateway (never the selected connection) fenced by
  source and secret-hash identity; taps carry only an opaque token and are preflighted before navigation; the
  per-profile keys are registered for the deletion sweep and a `ProfileCloser` stops/drains/reopens.
- B-ANDR-074/076/077: launches wait for the migration gate and the shell's first frame, history and restored
  intents are not replayed; Settings report/share-app use `AndroidShareService`.
- B-ANDR-101/102: download consent is explicit for metered and unknown networks, rechecked after the
  permission dialog, and a network change cancels; shade notices use fixed EN/AR copy and numeric progress.
- B-ANDR-111…117: widget rows carry only reviewed display fields; foreign-server rows have no request ids or
  actions; detail reads are bounded, read-only and discarded if profile, location, requests or feed changed.
- The 26 blocked rows name real missing native owners (quota collectors, widget action runners/overlays,
  thermal adapters under D6, terminal host, release contract); none is papered over with a stub.
- Security/privacy: no credential reaches logs or notifications (only a SHA-256 of the secret is kept in
  memory), no `launchUrl` of server values, kit-only UI, copy in string resources (EN + AR).

## Findings fixed (commit `f81f0fcc`)

1. **Dead deep link (B-ANDR-072).** The VIEW filter was declared but no code read `intent.data`, so a
   session link only opened the app. `PlatformHub.route` now consumes it once via the existing
   `EntryLinkParser`: a saved server connects and opens that exact session; an unknown server, one needing
   its secret again, or a failed connection opens Servers; team links land (AI Team deferred under D6).
2. **Widget ask copy drifted from Flutter (B-ANDR-111/112).** "Run outside", "Use this folder?" and
   "Allow this action?" replaced by Flutter's `chatRequestAskOutsideRun`, `chatRequestAskDirectory` and
   `e7PermissionAction7` ("Use {permission}") in EN and AR; the AR outside-write/read copy now matches too.
   A target containing a space is again not split into path parts (Flutter `widgetAskFor`).
3. **Migrated shared-folder grants dropped (B-ANDR-079/080).** Only the owner's recent-folder list was
   pushed, so folders a Flutter build had bound (`oc.sharedProjects.<id>`, migrated as is) vanished. They
   are now in the union, for saved phone profiles only; the list is sorted like Flutter's push.
4. **Alerts delayed by widget reads.** The feed collector ran up to four 2-second message reads for widget
   details before posting alerts. Widgets now publish last.
5. **Idle secret reads.** The monitoring loop decrypted every saved secret every 30 s even with monitoring
   off (the default). It now refreshes identities only while a server is enrolled, and once on enrolling.
6. **Invented monitoring copy.** The Servers opt-in now uses the ported Flutter `monitorOptIn` /
   `monitorOptInDetail`; the two invented strings were removed (EN, AR).
7. AR voice "verifying" notice aligned with Flutter (`native_voice_preparing`); build-script comment
   fragments left by the formatter tidied; the job report corrected.

## Left open (not blocking)

- A queued prompt held back by a scheduled lease stays unsent and retryable, but the entry carries no
  "scheduled prompt" reason as Flutter's does (`OfflineQueue` is a shared engine; propose to its owner).
- Session-link outcomes open Servers without Flutter's specific notices (server not saved / sign in again /
  connection failed); the shell has no launch-notice port yet.
- Monitoring Wi-Fi and check-in are per-server rows on Servers rather than Flutter's Notifications screen;
  behavior is equivalent, placement differs.

Shipping state: implemented and committed; not compiled here, not tested, not released.
