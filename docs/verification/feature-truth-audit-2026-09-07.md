# Feature usefulness and truth audit — 2026-09-07

Finish line: verify the previewed features against complete user tasks, correct
misleading behavior/evidence, and distinguish useful working source from
conditional integrations and unfinished groundwork. Non-goal: add features to
fill the backlog or declare a new APK ready from synthetic screenshots.

The coordinator runs checks and reviews rendered output; three agents own
quota monitoring, setup/native evidence, and search/session recovery. This
audit starts at pushed `5a4a868` plus the second-wave working diff. Checks during
repairs are focused evidence, not a full-suite pass for an unchanged candidate.

## Product verdict

| User task | What is real | Practical limit / judgment |
|---|---|---|
| Reopen a previous conversation | Workspace has a global session finder, archive option and scoped reopening without an open project | High priority. Audit found project-list failure still hid recovery; this must be repaired independently of new features. |
| Start an existing phone installation | Inventory supplies a version and a no-download start action; progress changes before awaited bridge work | High priority. Inventory is a version probe, not authenticated server health. Fresh native installation remains separate evidence. |
| Understand local server state | Explicit status/storage reads and bounded opt-in foreground recovery | Useful. Audit found an older recovery observation could override a newer manual failure; stale green state must not survive that check. |
| Find work awaiting attention | Profile monitoring reads pending requests and opens a revalidated destination | Useful for multiple servers. Counts cover each profile's selected location, not every project; background delivery needs actual Android verification. |
| Track spending | Saved personal budgets compare server-reported cost/tokens for the selected report scope | Useful as personal tracking; neither provider billing nor subscription allowance. Missing measurements are not invented. |
| Get provider-limit attention | Optional collector, account/source-bound enrollment, rules and fixed-copy native routing | Conditional advanced integration. APK installation alone does not deploy the collector. Claude remains unavailable; no live account result is claimed. |
| Add web context to a prompt | Supported search transport, explicit query, review and editable unsent composer text | Useful when the server has a configured search provider. Search results are untrusted context, not verified page content. |
| Organize plugin commands | Personal links persist and revalidate command/destination before reviewed execution | Modest convenience. The server does not report plugin-command ownership; linking does not discover, install or enable a plugin. |
| Read task progress | Bundled todo renderer consumes actual tool data and filters it locally | Modest readability improvement. Completion is reported by the server, not independently verified; this is a chat tool result, not a separate task-manager page. |
| Use Codex as a mobile backend | Isolated real CLI protocol proof and focused transport/gateway tests | Still unexposed groundwork until profile, controller, approval and reconnect flows are integrated. It must not be counted as an enabled user feature. |

## Audit findings

1. **Project discovery could block old-session recovery.** A whole-screen project
   error hid the global session finder and usable session rows. Keep recovery
   available when the independent project request fails.
2. **Manual health could display old success.** Prefer the latest completed
   observation; hide the old version/status after a fresh failed check.
3. **Setup overstated validation and offered competing primary actions.** Remove
   unsupported “tested release” wording. Make starting the existing installation
   primary and replacement secondary, with explicit installed/target versions.
4. **Quota alerts could be missed.** Expiring an alert with a short-lived reading
   while retaining its duplicate marker can suppress the only notice. Retain a
   clearly historical threshold event; current measurements still expire.
5. **A transient collector failure blocked notification review.** A valid saved
   source route should open review/retry without presenting stale values. Deleted,
   disabled or replaced sources must still be rejected.
6. **Some previews implied pages that do not exist.** Task, activity and managed
   health captures used isolated test scaffolds. Recapture their production
   parents and label synthetic data explicitly. No new page should be built just
   to match a misleading screenshot.
7. **Search review was buried below an empty manual-entry form.** Put the next
   step where a user who selected a search result can find it.

The first failed search tap was a fixture mistake: it tapped the previous
disabled button without pumping the query-dependent frame. The corrected check
asserts Search is enabled, then exercises the real composer return. Quota capture
deadlocks are fixture/scheduling findings, not proof of an Android runtime hang.

## Evidence limits

Captures render production Flutter screens with synthetic data and mocked native
responses. They are not screenshots of the installed APK or live accounts.
They can establish visible layout and simulated interactions; they cannot prove
TalkBack, native callback timing, Android foreground-service delivery, real
provider access, phone upgrade preservation or fresh Termux installation.

The available Flutter reports 3.47.2 but is upstream. The documented Shorebird
fork's Android AOT compiler for a Linux ARM64 host is unavailable in the verified
artifact table. Do not call this the pinned native gate or a signed release.

## Repair boundary and checks

All seven findings above have source/evidence corrections. Project discovery
failure no longer blocks existing sessions or global search. Setup favors reuse;
manual health supersedes older recovery observations. Historical quota alerts
remain reviewable through outages, quiet hours and ordinary foreground use;
fresh confirmed recovery dismisses them. Unenrolled monitoring does not schedule
work. Search review precedes optional manual entry. Captures now show actual
Chat, Activity and Servers parents.

- The broad focused command completed 106 checks with one optional preview
  skipped and eight failures. Those failures were in newly added fixtures:
  immutable project-list fixtures, missing post-scroll frames, and a chat
  capture supplying the legacy message method instead of `messagePage()`.
  The four affected files were rerun after repair: **35 passed, zero failed**.
  These commands overlap and are not a full-suite total.
- Quota monitor state/screen, setup, Codex gateway and the remaining production
  captures passed in that broader command. Earlier focused health, profile,
  command and task behavior checks also passed; actual device proof is separate.
- Final `flutter analyze --no-pub`: **No issues found**.
- Collector checks: **49 passed**, no failures.
- Isolated real npm installation on Linux ARM64/glibc completed successfully
  with required `opencode-linux-arm64@1.18.29` and `opencode-ai@1.18.29`;
  postinstall succeeded and the CLI reported `1.18.29`. The guest used Node
  18.19.1/npm 9.2.0, differing from the reported npm 11 environment. No server,
  model turn, live credential or Android bridge was involved.
- Desktop packaging checks previously passed: six Python packaging cases and
  ten Flutter packaging-contract cases. No desktop/native app build is claimed.
- The complete recursive suite, pinned native build, signed APK and on-device
  upgrade/notification checks remain pending. This development batch is not a
  release or a claim that the full backlog is finished.

Accepted captures are in `docs/qa/session-recovery/`, `setup-progress/`,
`managed-server/`, `profile-monitor/`, `provider-quota/`, `plugins/` and
`web-search/`. Their README files distinguish synthetic data from device proof.
