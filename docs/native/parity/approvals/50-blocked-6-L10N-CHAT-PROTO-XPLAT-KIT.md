RESULT: APPROVED WITH FIXES

# Approval: 50-blocked-6-L10N-CHAT-PROTO-XPLAT-KIT

Branch `native/p12x/job-50-blocked-6-L10N-CHAT-PROTO-XPLAT-KIT`. Job commits reviewed: `ac79d831` and `663f9325`, against the catalog rows, the Flutter reference, `native/AGENTS.md` and plan 01. The review environment cannot compile Android, so nothing was compiled, tested or built into an APK. The build server compiles after this approval.

## Merge

`origin/native/p12-head` was merged in (`3a082857`). Six files conflicted, and each was resolved keeping both sides:

- `TerminalSession.java`: the job's OSC 52 filter now feeds P12's SGR normalizer (`appendFiltered` → `appendNormalized`).
- `AndroidRemoteTerminalRenderer.kt`: keeps the job's `TerminalSurfaceHost` and P12's `applicationCursor`.
- `feature/terminal/build.gradle.kts`: union of `:engine:settings` and `:engine:connection`.
- `LocalTerminalViewModel.kt`: keeps the link tip and teaching (job) and the native keys and server sources (P12). Back now dismisses the tip and discards pending input.
- `LocalTerminalScreen.kt`: keeps the tip anchor (job) and the full key bar (P12).
- `KitTerminalKeyBar.kt`: both APIs kept, the `keys` list and the default `onSend` control bar. `KitTerminalView` gained `defaultKeyBar` so screens with their own bar do not show the keys twice.

## Findings fixed (`8d033b4a`)

1. **Privacy: silent clipboard writes.** A background sign-in or unseen terminal session could change the clipboard silently through OSC 52, a behaviour the base had deliberately refused. Writes now need the terminal's view to be on screen, as in Dart.
2. **Security: multi-line secrets unmasked.** Command output was masked line by line, so a private key's body lines were shown. The whole output is now masked first.
3. **Parity: command box.** It showed the last lines; Dart's `_CommandBox` shows the first lines, then Show all, then the full output. Restored.
4. **Merge regression: missing control keys.** The server and native-local terminals lost one-tap Ctrl-C/Ctrl-D. They now lead the key bar.
5. **B-CHAT-124: staged sources wiped.** A provider refresh or capability change discarded the person's staged web sources. They are now kept.
6. **B-PROTO-088 / B-PROTO-045: hidden waiting sends.** Waiting sends could only be found in the overflow menu, and an unsent offline prompt had no action. Added a visible strip above the composer, plus "Bring back to edit" for undispatched device sends. A cancel-restore now survives a registry emission.
7. **B-CHAT-108: re-armed confirmation.** Reopening the sheet re-armed a skill confirmation that might already have reached the server, which could create a duplicate conversation. It is now remembered per skill.
8. **B-KIT-013: stale capability flows.** Flows were registered from a stale `facts.value` snapshot. They are now collected.
9. **B-L10N-017: wrong Material You preview.** The Material You preview showed fallback colours. It now applies directly. The preview starts in the current light/dark mode.

The new copy is the `nativePendingCount` plural, in English and Arabic.

## Accepted as is / noted for later

- **B-XPLAT-010:** detection and the policy-checked open are correct. The sheet lacks Flutter's full address and Copy link, and a refused link shows no notice.
- **B-XPLAT-011:** `TerminalSurface.selectedText()` has no caller yet.
- **B-KIT-013:** the claim is overstated. Only the "server.any" explainer is used, so only Add server is reachable.
- **Capabilities page:** Usage is shown as available for any connected server (`usageStatistics = live != null`), and every flag reads false while capabilities load.
- **Dead code:** the `protocol/oc2` copy of `WebSourcesReview` is unused by the app, and `KitFactRow` has no screen caller yet.
- **Shared modules:** the job edited the shared `ui/kit` and `ui/words` modules (rule 14). Accepted for this integration.
- **Blocked rows:** B-PROTO-083 is legitimately blocked (no remote-upgrade API). B-L10N-007 and B-KIT-060 are owner-deferred test gates.
- **"Already there":** the claims for B-L10N-003, B-CHAT-046 and B-CHAT-048 were confirmed in code.
- **Security:** no credential logging found. Every terminal URL goes through `ExternalLinkPolicy`, and OSC 52 queries are never answered.
