RESULT: APPROVED WITH FIXES

# Final approval — job 27-build-AGENT-2 (AGENT part 2)

Branch `native/p12x/job-27-build-AGENT-2`. Job commits reviewed: 3dfc39c1, 1bd28a84, cd59cf75, efe00ced
(base `27dc0473`, the P12 integration merge of runtime-c). Report: `docs/native/parity/migration/27-build-AGENT-2.md`.
Approval fixes: 687db2d8. No tests, Gradle or APK tasks ran here (owner: coding first, testing afterwards; Google
Maven is blocked in this environment). The build VPS compiles after this approval.

## Step 1 — merge of the P12 integration head: not done

`origin/native/p12-head` does not exist: no branch, tag or PR ref by that name (or close to it) is on the remote.
The newest integration point reachable here is `27dc0473`, which is already this branch's base. Nothing was merged
and nothing was guessed. The coordinator should push `native/p12-head` (or name the right ref) and merge it into
this branch before integration. The usual rules apply: keep both sides, and union the
`androidApp/build.gradle.kts` dependency lists.

## Verdict

The job is substantially right. 44 rows built now, 5 already there and 10 blocked (after reclassifying 241).
Each built row is reachable from the real app (Servers, chat header, Plans, Projects, Dev services, widgets).
Strings are present in English and Arabic: all `Res.string` references resolve and the key sets match. UI comes
from kit parts only. No placeholder, stub or new TODO was added; the two hits in the diff are older comments that
were only moved.

The security-sensitive pieces hold up on review:
- **Connector-search loopback bridge:** bound to 127.0.0.1, ephemeral port, constant-time bearer check with the
  bearer redacted, bounded headers, body and concurrency, 0600 O_EXCL|O_NOFOLLOW descriptor.
- **Quota collector:** `collector.mjs` and `keys.mjs` are byte-identical to Flutter's and pinned by hash; keys
  never reach Kotlin.
- **Logo fetch:** fixed host, validated slug, 64 KiB and 5 s bounds, no redirects; the bundled marks are
  identical to Flutter's.
- **Codex reads:** read-only; the token is redacted and sent only over wss or loopback; the email leaves only as a
  hash.
- **Vendored Termux `TerminalSession.java`:** upstream hash verified, licences present, AAR hashes pinned.
- **Widget activities:** exported=false and reached only through the app's immutable PendingIntents.

The blocked rows are justified:
- **126/127/128:** dropped as NG8 in plan/17 :442/:838, migrate-out only under D7.
- **135:** no qualified backend for positive runtime add.
- **138:** the phone-engine `/v1` client is deferred by D6.
- **230, 233–236:** no atomic apply/undo server contract.

## Findings fixed in 687db2d8

1. **Major: remote OpenCode 2 cards regressed.** The new phone-only `NativeCardsQualifier` replaced the
   `GraphCardsQualifier` fallback for every profile, so a remote server reported no Cards agent and could never
   qualify. It now delegates every non-phone profile to `GraphCardsQualifier`.
2. **Major, privacy (B-AGENT-314): a remote collector could not be turned off.** "Turn off" always ran the in-app
   script, which fails for remote servers, so remote reads continued. A remote collector now revokes its consent.
3. **Major, crash risk: possible `startForegroundService` crash in `BackgroundConnectionService`.** A widget start
   with malformed extras, or one where only the live signal was set, never called `startForeground`. The service
   now always keeps the promise and keys widget foreground on its own `livePromised`.
4. **Major (B-AGENT-173): history reads were blocked while the host refuses work.** Read-only `sub_` children and
   unstarted drafts no longer take the payload lease. Ordinary history keeps it on purpose: a timeline read is the
   in-place resume that loads the agent, like Flutter's `resumeHostAgentChat`.
5. **Major (B-AGENT-165): every history read waited up to 8 s for the sub-agent list.** It now loads once per
   parent in the background (Flutter parity); a later upsert re-links cards already shown.
6. **Major (B-AGENT-151): the background-task book was rebuilt on every reconnect.** It is now kept per server
   (profile + origin) in a bounded LRU of 32.
7. **Major (B-AGENT-239): the shell output viewer never adopted a new connection.** It now rebinds to the live
   connection before each refresh; `ShellOutput.bind` was unused before.
8. **Blocker for the test phase (owner gate later): three existing source-guard tests broke.** Two broke on the
   ktfmt reformat of `BuiltinLinux.kt` (B-LINUX-028, B-LINUX-221) and one on the new activities (B-ANDR-002).
   - The guards now ignore whitespace.
   - The entry contract now lists both widget activities and checks that the route activity is a translucent,
     no-history trampoline.
   - `WidgetRouteActivity` became one; it previously flashed an opaque window.
9. **Minor fixes:**
   - Usage monitors now pause in the background (B-AGENT-191/313).
   - The MCP catalogue is ordered on first load and on load failure (B-AGENT-212).
   - An empty Codex email no longer yields a hash, and the signed-out Codex reading is a plan sign-in
     (B-AGENT-312/315).
   - A Paseo terminal now buffers only its own frame kinds of the current connection before the ack (B-AGENT-159).
   - Diff paging no longer shows an empty last page.
   - `PlatformWsConnections.kt` exists once in `jvmShared` instead of two identical platform copies.
10. **Report corrections:**
    - Stale evidence lines fixed: `Oc2Gateway.kt` for 130/226/227/229, `BuiltinLinux.kt` for 316.
    - B-AGENT-241 reclassified to already there: `CODEX_START` and its test predate the job.
    - B-AGENT-157 no longer claims the patches are redacted; Flutter does not redact them either.
    - B-AGENT-136's timeout-slot claim is corrected.
    - Notes added for 126–128 (dropped, NG8) and for 191 (the monitor needs a live connection).

## Left open for the coordinator (not blocking)

- **Out-of-ownership edits:** the job edited coordinator- and owner-held modules (`:ui:kit` `KitChoiceList` /
  `KitProviderLogo` / `KitRowParts`, `:ui:routes`, `:core:net`, `:androidApp`, `MODULE_OWNERS`). The edits look
  correct, but the owners should accept or re-home them explicitly (native/AGENTS.md "propose, don't edit").
- **Connector-search timeout (B-AGENT-136):** it cancels the search, while Flutter holds the slot until the work
  ends. No security impact.
- **Shell output polling (B-AGENT-240):** it refreshes only on tap; Flutter polls every 2 s while running.
- **Worktree lists after close (B-AGENT-158):** a worktree created or removed after the sheet closes is not
  re-read until the next workspace retry.
- **Widget voice/configure routes:** `WidgetRouteActivity` opens the app without the requested mode (outside row
  317).
- **Compile check needed:** the build VPS must compile this branch. The fixes were checked by reading only
  (imports and names verified against the sources).
