RESULT: APPROVED WITH FIXES

# Approval — job 40-port-screens-2 (Flutter screens migration, part 2 of 3)

Branch `native/p12x/job-40-port-screens-2`. Job commits reviewed: `02aa4a7d`, `2085d27b`, `c9777f13`,
`2b4262b6`, `5ee11960` (base `cde0afb4`). Report: `docs/native/parity/migration/40-port-screens-2.md`
(67 rows: 3 already there, 47 ported, 17 not needed). The rows were checked against the Flutter
sources in `lib/`, the binding plan (plan/01 §12 D6 for the AI Team / A2A / FactsBook "not needed"
rows) and `native/AGENTS.md`. Per the owner, no tests were run or written and no APK was built;
Android was not compiled here (Google Maven is blocked), so the build VPS compiles next. Every fix
below was checked by name, import and signature against the sources.

## Merge of `native/p12-head` (`f6cb9557`)

Twelve files conflicted. All were resolved keeping both intents, and the dependency lists in
`androidApp/build.gradle.kts` are a union. Five places needed real reconciliation, because both sides
had built the same thing:

1. **Paseo provider import was implemented twice.** The job's version is the core.domain
   `ProviderConversationImportGateway`. P12's version is a set of extension functions in the same
   file. The job's implementation is kept: it is limited to the exact project, and an import must
   match the last list shown. It now serves both entry points: New conversation and P12's computer
   page (`AppComputerTools` in `ProtocolsWiring.kt`, adapted to `lastUsed: Instant`). The imported
   agent is read under its app id (`gateway.app(id)`). The P12 Paseo test now lists before
   importing.
2. **Two saved-server monitor engines.** The job's `AppChatsMonitor` ran its own `ProfileMonitor`
   alongside P12's `AndroidProfileMonitoring`, on the same `oc.notifyRules.*`/`oc.monitor*` keys.
   That meant double polling and double alerts. `AppChatsMonitor` is now an adapter over P12's one
   engine (`awaitEngine`, `enabled`, a new `openWatched` with the same private preflight as a
   notification tap). The job's fence, gateway and second token route are removed. P12's
   `monitoring.route(intent)` handles alert taps, and its `registerKeys` covers deletion.
3. **Linux storage cleanup.** P12's receipt-bound `measureStorage`/`cleanableBytes`/`cleanup` are
   kept, together with the job's `scanProcesses`/`stopIdentities`. The job's storage model already
   calls them in a compatible order.
4. **Two methods defined twice in `Oc2Gateway`.** P12's `createWorktree`/`addMcpServer` overrides
   and the job's ones were both kept by the merge, which would not compile. The job's versions are
   kept (fix `3bafaa92`): they register MCP header and environment secrets with the redactor. Web
   search is added to the gateway's list of interfaces.
5. **Two writers of the phone runtime's shared-project roots.** The job's Files grant
   (`SharedFolderWiring.syncRoots`) and P12's `NativeSharedProjectFolders` used different unions, so
   a grant could drop the opened-project roots. `syncRoots` now goes through the P12 class's new
   `syncNow()` (fix `474bbaca`).

Smaller resolutions:
- **Launch routing:** P12's `MainActivity` launch hand-off is kept, plus the job's export
  attach/detach.
- **Settings:** P12's share service is kept, and the job's Tips/Tools/Providers rows are wired.
- **Message delivery:** the job's `sendInsteadOfDraft` is kept.
- **Imports:** the chat and Linux imports are the union of both sides.
- **Key registration:** the job's profile-key registrations are kept, with P12's monitor keys from
  `registerKeys`.

## Findings and fixes in the job

- **Security — child conversations (`b048c10d`).** `ChatViewModel.openSession` opened any session id
  the server wrote. The parent check (`GraphChatWorld.isChildSession`) existed but nothing called it.
  It is now required before opening, as Flutter's `requireChild` does.
- **Security — backup restore (`95bb5ffe`).** `LinuxBackupTar` checked each symlink target only on
  its own text, so a chain of links could climb out of the restore root. A link target may no longer
  pass through another archived link, and a link may not sit on a path an earlier target passed
  through.
- **New conversation (`c561003a`).**
  - Repair was offered and run after any failure in a phone project folder. It now runs only for
    `Failure.ProjectGit`.
  - A typed folder must be an absolute path with no `.`/`..` parts and no control characters.
  - Import rows show a relative age instead of a raw timestamp, and a failed list load has its own
    state.
  - A failed open of a watched request reopens the Inbox, so the failure is visible.
- **Preview (`c561003a`).** After one confirmed "Stop and send", later sends restarted the agent's
  turn without asking. Each restart now needs its own confirmation.
- **Library (`3bafaa92`).** MCP add refused when the location changed during its duplicate-name
  check. It now adds at the location captured at the start, like Flutter and P12's test.
- **Chat details (`b048c10d`).** The background-agent card has its "Background result" caption back,
  and the compaction token count uses locale digits.
- **Linux ports (`95bb5ffe`).** The process scan ran first and its retries hid a busy or failed port
  read. It now runs only after a successful port read. The fakes in the Linux and phone screen tests
  now implement the job's new actions, so the test sources compile; no tests were run.
- **Report.** Eleven rows were corrected or annotated in place: timeline, transcript rows and turns,
  Claude import, Git repair, inbox, MCP, backups, ports, storage, and local agent (whose line
  citations were wrong).

## Checked and accepted

- Every "built now" row is reachable from the real app; I found no placeholder, stub, fake or TODO
  in the job's sources.
- The "already there" rows (packages, storage, local agent) are true.
- All 17 "not needed" rows are AI Team, A2A or FactsBook (deferred or dropped by plan/01 §12 D6) or
  Flutter plumbing.
- UI is kit-only.
- Every `Res.string` the code uses exists. The English and Arabic key sets match for this job's copy;
  the only gaps are the pre-existing team-goal plurals.
- **Library secrets:** provider keys, OAuth codes and MCP secrets are registered with the redactor
  and entered in secret fields. Pending sign-in recovery stores no link or code, under
  `oc.pendingAuth.<profileId>`.
- **Links:** server URLs go through the checked link policy and opener.
- **Per-profile keys:** the new keys follow `oc.<what>.<profileId>` and are registered for deletion.
- **Linux safety:** kept-service scripts quote every variable, and file access refuses guest
  symlinks.
- No new Metro duplicate bindings or duplicate route entries.

## Remaining differences (not blocking, follow-up)

- **Timeline:** row times are UTC ISO text, because there is no shared local date formatter yet.
  Fork does not restore the prompt for editing.
- **Inbox:** rows only open the conversation; there is no inline answering or question/form text.
- **Git repair:** notices only; Flutter has a staged sheet with the in-place Termux step.
- **Project filter:** "Open a project…" opens a path dialog instead of the full sheet, and project
  rows have no Git chip.
- **Ports:** the Port actions sheet replaces Flutter's long-press menu (a deliberate change).
- **Monitor adapter:** builds its route with flavor "v2" and does not skip profiles that cannot be
  read yet, as before.
- **Pre-existing on both branches:** `strings_phone.xml` and `strings_linux_services.xml` define the
  same `linuxSvc*` names with different text. This needs one owner.
- **Now unused:** P12's `Oc2Operations.worktreeCreate`/`mcpAdd`. A warning only, while `oc.werror`
  is false.
