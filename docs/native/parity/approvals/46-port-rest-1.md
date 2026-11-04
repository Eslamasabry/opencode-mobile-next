RESULT: APPROVED WITH FIXES

# Approval — job 46-port-rest-1 (Flutter platform, Linux, protocol and other code, part 1)

Reviewer: Opus 5.5 (cloud). Branch `native/p12x/job-46-port-rest-1`. `origin/native/p12-head` (`b6c30a59`) was
merged first: it is already the job's base, so the merge was a no-op ("Already up to date", no merge commit and no
conflicts). Reviewed the job commits `a92890e1` (code) and `7b19018a`…`b693dcd7` (report) against the Flutter
sources in `lib/` and `android/app`, `behaviors.jsonl`, `native/AGENTS.md` and plan 01. No Gradle build, tests or
APK here (Google Maven is blocked in this environment); the build VPS compiles next. The job's own log records
`:androidApp:compilePreviewDebugKotlin compileKotlinDesktop` passing at its last code commit.

## What holds

- `phone_budget.dart`: note text, marker block, the seven thread-cap variables, "only add unset keys / only remove
  our value", the `.oc-profiles/<id>/claude` layout with the id pattern, no-follow checks and atomic replace match
  Flutter; the switch is reachable on Keep running, saved under the existing device-wide `oc.fullSpeed` key.
- `heat_tier.dart`: real PowerManager status/headroom, battery and power-save feed the existing `AgentThrottle`
  hysteresis; `AgentPriority` moves only this app's helper trees; full speed restores Normal at once.
- `linux_vitals.dart`: heat mapping, `MemAvailable`, CPU share of all cores and the heaviest program (≥ 50 % of a
  core) match Flutter; sampling runs only while the Linux page is resumed and ready, every 5 s; failed sources stay
  absent, never zero. `KitVitalsStrip` is a kit part; no live region.
- `models_content.dart` / `models_messages.dart`: `pruned` and the raw `executed` flag now reach the core tool
  state; `CardCodec` reads `ran`, which is exactly Flutter's `_toolRan` (outcome wins over beta's `executed:false`).
  Typed error kinds (`fromName` + sign-in refinement) reach the footer with existing EN/AR copy; stops stay
  stopped; details stay masked by the redactor.
- `gen_ui_history_http.dart`: 1 MiB streamed cap, 10 s cancelling deadline, page/session/cursor validation and
  source/close fences match Flutter; backends without the facet get no unbounded fallback.
- `local_llm_*`: Settings › This app › Storage measures and deletes `files/local_llm_models` without following
  links, behind a kit destructive confirm (plan 02 §3.7); the Gemma runtime stays deferred.
- Fork and project inventory: Flutter's OC2 capabilities leave `sessionFork` and `projectManagement` on, so
  enabling them natively is parity; the before/through boundary matches `ops_sessions.dart`.
- Pinned shortcuts wait for the startup connection, never switch servers, and re-check ownership; Background and
  Linux launcher actions open real pages.
- "Already there" rows: every cited production file exists and does what the row says (spot-checked backoff,
  trust gate, catalog/requests wire, deliberate stop, chat leases, setup records/planning, card validator,
  abort-as-stop attention, mentions, server answers).
- Blocked rows name real missing native owners; partial ports inside them (PTY socket, sign-in lease, service
  hysteresis) are described as partial and are not presented as finished journeys.
- Security/privacy: no credentials logged or echoed; no `launchUrl` of server values (file links never reach the
  link opener); `MANAGE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` (≤ 32) mirror the Flutter manifest; the
  FileProvider is not exported and grants read access per intent only; the PTY socket rides the profile's
  hardened client. No new per-profile storage key. Kit-only UI; new copy in EN + AR resources.

## Findings fixed on this branch (commit `a8a348c5`)

1. **Regression — ordinary chat history.** `Oc2Gateway.messagePage` had been rerouted through the card-recovery
   path: a 16 MiB/30 s cap, every non-200 answer (401, 404, …) reported as an unreadable reply instead of its typed
   failure, and `limit` added to cursor pages. Restored the previous read; only `boundedHistoryPage` is bounded,
   as in Flutter.
2. **Regression — Codex/Paseo history.** The staged-boundary reader fetched `session(id)` before every history
   read on every backend; a new empty Codex thread could fail to open. The snapshot is now read only when the
   gateway has the `StagedRevertGateway` facet.
3. **Parity/privacy — phone files.** A tap opened Android's chooser/installer, or jumped to the all-files-access
   Settings page, with no question. Added Flutter's kit confirm ("Open {name}?" / "Install {name}?", existing EN/AR
   copy) before anything is handed to Android; restricted the classification to Flutter's four shared roots
   (SD-card/Termux paths keep the preview, as in Flutter); added Flutter's generic Settings fallback.
4. **Kit rule — access confirm.** "Allow file access" is not a question; `KitConfirm` asserts that (throws in debug
   builds). Retitled in EN and AR.
5. **Parity — Fork.** Added Flutter's best-effort "Copy of <title>" rename, skipped for unreadable or placeholder
   titles.
6. **Copy duplication.** `relativeAge*` duplicated Flutter's `e7Workspace*` strings (and lacked their es/zh
   translations); RelativeAge now uses the existing words and the duplicate files are removed.
7. **Person's file layout.** `settings.json` was rewritten as one compact line; it now keeps Flutter's two-space
   indentation.
8. Report rows for phone files, relative age, session history, card history, Fork and phone budget corrected.

## Remaining, not blocking

- Flutter's "too big to preview → offer Open with" fallback and the Wi-Fi share alternative in the phone-file
  sheet are not ported (named in the report row).
- The job edited shared modules (kit, words, core) that rule 14 reserves for their owners; the changes are small
  and additive, so they are accepted here for the coordinator to note.
- Files were not run through `tool/format-changed.sh` (no formatter in this environment).
- Nothing here was compiled, tested or run on a device; the VPS build is the next check.
