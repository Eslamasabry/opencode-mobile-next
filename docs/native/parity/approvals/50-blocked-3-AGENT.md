RESULT: APPROVED WITH FIXES

# Approval — job 50-blocked-3-AGENT (native migration second pass, AGENT rows)

Branch `native/p12x/job-50-blocked-3-AGENT`. Job commits reviewed: `669b2d01`, `2fd2705b`, `999df029`
(base `518d4340`). Report: `docs/native/parity/migration/50-blocked-3-AGENT.md` (27 rows: 3 built now,
5 already there, 19 blocked). Rows were checked against `catalog/behaviors.jsonl`, the Flutter sources
in `lib/`, `test/` and the Flutter app's Kotlin in `android/`, the binding plan (plan/01 D6/D7, plan/17
NG8/N17-D2) and `native/AGENTS.md`. Per the owner, no tests were written or run and no APK was built.
Android was not compiled here (Google Maven is blocked), so the build VPS compiles next. Every
reference touched below was checked by name, import and signature against the sources.

## Merge of `native/p12-head` (`fe2ef020`)

Clean merge, no conflicts: P12 head only added one line to `docs/native/units/INTEGRATE-P12.md`. No
dependency list changed.

## Findings and fixes

1. **B-AGENT-050: Claude sign-in sharing became a launch blocker (fixed).** In Flutter,
   `BuiltinLinux.shareClaudeSignIn` is explicitly best effort: a failure or a foreign `~/.claude` link
   is logged and never blocks the launch. The job replaced it, in both `android/linux` `BuiltinLinux`
   and `android/agenthost` `AgentProcesses`, with `PhoneAgentClaudeHome.requireShared`, which threw
   `agent_sign_in_home_unavailable` and refused every Claude launch (sign-in terminal, helper, auth
   and sign-in checks) for someone whose `~/.claude` points elsewhere. No copy or failure mapping
   existed for that code either, so it would have surfaced as a generic error. The Flutter behavior is
   restored in both copies (with `AppLog`/`Log` as before the job) and the now-unused `requireShared`
   is removed from both `PhoneAgentClaudeHome` copies. What remains "built now" for this row is real:
   `TESTED_CLAUDE_VERSIONS` now matches Flutter's `claudeCodeTestedVersions` (adds 2.1.316). The
   shared-folder calls on every launch path were already there.
2. **B-AGENT-030: "Undo unavailable" on every Activity row (fixed).** `AttentionScreen` labeled every
   non-undoable act, including reconnect, update, permission and queued-send acts, as "Undo
   unavailable", although only restarts ever carry an inverse. The label now appears only on restart
   rows (`AttentionScreen.kt:140`).
3. **B-AGENT-030: beyond-Flutter addition (accepted, documented).** Flutter never passes an `undo`
   callback for any production act, so no Flutter screen offers Undo. The job's restart inverse
   ("Stop restarted server") is a native addition. It is kept because it is reachable, explicit and
   localized; it re-proves process, generation, boot and owner identity under the runtime monitor
   before it stops anything; it never revives after a restart; and it uses the engine's existing
   once-only, attempt-marked Undo. Removing it would leave the Undo UI dead. The report now says this
   explicitly. The owner may decide to drop it later. The job's main structural fix is good: before,
   `NativeRuntimeActs` and `AppAttentionState` each wrote `oc.automaticActivity.<id>` (two writers
   for one key). Now one `@SingleIn` `AppAutomaticActivities` registry is shared.
4. **Report corrected** for B-AGENT-030 and B-AGENT-050 (claims, line references).

## Verified without change

- **B-AGENT-057:** reachable from the always-present ⋯ › agent handoff entry (`HeaderRules.menu`). For
  a direct Codex chat, `open()` refuses (no filesystem attestation) and `review()` builds a masked,
  editable Claude draft from the read-only full-thread snapshot (`handoffSnapshot`, never
  `messagePage`). It offers no Send, writes no file or link, and the title and prompt are redacted.
  The Codex destination stays `agentHandoffFiles = false`. Copy is in English and Arabic.
- **B-AGENT-014/015/016/017/029 (already there):** the cited engine and wiring lines exist and do what
  the report says.
- **Blocked rows:** B-AGENT-044…049 cite the empty `appVersions` in `scripts/byo-host/release-pins.json`
  and `byoHostBundleForAppVersion => null` (true). B-AGENT-121…128 cite D7 migrate-out-only and NG8
  (plan/17:838, :850; true). B-AGENT-135 cites `mcpChatToolRefresh = false` plus the OpenCode 2
  card hold (true). B-AGENT-138 cites D6 (true). B-AGENT-230/233…236 cite the lack of a production
  atomic config transaction, with legacy PATCH refused (`Oc2SetupConfigGateway.kt:112`; true).
- **Prerequisites for blocked rows:** `ByoHostService` matches `ByoHostController`'s constructor and
  result types. It is unreferenced groundwork, default off, and never reports availability without a
  bundle. It is acceptable because its rows stay honestly blocked. The `SetupConfigRevision` freeze
  turns a data class into a class; nothing uses `copy` or destructuring on it.
- **Kit-only UI:** `KitRow`, `KitRowTrailing.Action`/`KitLineAction` and `KitNotice`. No new interface
  implementers outside the edited modules were broken (`AttentionActions.undo`,
  `AgentHandoffHost.review`).
- **Security and privacy:** no credential, prompt or URL logging was added. Handoff review content is
  memory-only and has a content-free `toString`. The setup snapshot and commit now also have
  content-free `toString`.

## Not done here

No compile (VPS next), no tests, no APK, per the owner.
