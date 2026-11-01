# Unit S3-COMPE: `:engine:composer`

Date: 2026-11-01. Branch: `native/compe-2026-11-01` (from `claude/native-compe-2026-11-01-5t27af` at `6b9a2b21`).
Write set: `native/engine/composer/**` (new module) and this report. Nothing outside it was edited.

Inputs read: `native/AGENTS.md`, `docs/native/units/S1-DATA.md`, plan 15 (row S3-COMPE, conflict C15-1), plan 09 §10
(and §15.2 test map, §17 unit rows), `map/chat-genui.md` CHAT-GENUI-35…38, `map/state-data.md` STATE-DATA-21…23, 26, the
catalog rows below, and the Flutter sources being ported (`lib/state/{session_drafts,draft_attachments,
draft_attachment_storage*,prompt_shelf,saved_prompts_controller,phrase_chips,phrase_moment,review_handoff,prompt_photos,
draft_photo_recovery}.dart`, `lib/state/connection/drafts.dart`, `lib/ui/screens/chat/{empty_chat,chat_send,composer}.dart`,
`lib/ui/widgets/prompt_history_navigation.dart`, `lib/domain/models/models_requests.dart`) with their tests.

**Result.** Drafts (with the §10.8 restore rules and the 600 ms autosave), the attachment vault, saved prompts (stash),
prompt history, Undo, phrase chips, the phrase moment, starters, review references, mentions, the Send decision and the
photo recovery record are implemented in common Kotlin and green on the desktop JVM (102 tests, 0 failures). Every
store reads the Flutter-written format unchanged (JSON fixtures in the tests, and a vault directory in the exact Flutter
file layout read through `FilePayloadStore`).

Shipping state: implemented, verified locally (desktop tests; the Android target compiles), committed and pushed to the
branch. Not wired into any graph or screen, not merged, no PR, no CI run. **Not done against the real preference
store:** `:core:data` does not compile on this branch today (see §5.1), so the engine talks to its own
`ComposerPrefs` port; the `KeyValueStore` adapter is the first follow-up.

---

## 1. What was built

Package root `io.github.eslamasabry.oc.engine.composer`, `explicitApi()` (engine layer). Dependencies: `:core:common`,
`:core:model` (PromptAttachment, transcript types), `:core:security` (Redactor), `kotlinx-io-core` (existing catalog entry).

| Area | Files | What it does |
|---|---|---|
| Storage ports | `store/ComposerPrefs.kt`, `store/ComposerProfiles.kt`, `store/JsonRead.kt` | `ComposerPrefs` (sync reads of acknowledged values, atomic `commit(PrefsEdit)` → Boolean), `MemoryComposerPrefs` (previews/tests, `refuse` switch); `ComposerProfiles` (exists, count); Dart-equivalent JSON readers (`as String`, `as num?`, `toString()`). |
| Vault | `vault/AttachmentVault.kt`, `vault/PayloadStore.kt`, `vault/FilePayloadStore.kt`, `vault/Sha256.kt`, `vault/AttachmentRefJson.kt` | Port of `DraftAttachmentVault`: identity = SHA-256 of the whole `data:` URL, owner dir = SHA-256(profile id), ≤ 5 per prompt, 32 MiB per draft, 256 MiB per root, `file:`/`http(s):` kept as metadata, other schemes refused, restore checks size and hash, `file:` only at the same place, collection deletes only unreferenced and uncommitted payloads. Three roots (`draft-attachments-v1`, `prompt-stash-attachments-v1`, `prompt-photo-recovery-v1`). `FilePayloadStore` = Flutter's `<root>/<owner>/<id>.blob` + `.tmp` + atomic rename over kotlinx-io, on the injected I/O dispatcher. |
| Drafts | `drafts/SessionDraft.kt`, `drafts/Drafts.kt`, `drafts/DraftAutosave.kt` | `oc.sessionDrafts` (JSON array, newest first, Flutter field order), keys `[profile,session]` / legacy session id, the `oc.draftAttachmentVault` marker before any payload; one Mutex lane; payloads → metadata → collection; 50 max, a full store refuses; unreadable index never overwritten; `closeProfile` fences late writes at once and removes the profile's and the unattributed drafts and only their payloads; `restoreFor` = the §10.8 rules (unconfirmed send not restored, share replaces, async attachment recovery with named misses, watched/demo none); `DraftAutosave` (600 ms debounce, `flush`, `settled`, `failure`, a refused save stays pending for "Try saving again"). |
| Saved prompts | `stash/StashedPrompt.kt`, `stash/PromptShelf.kt` | `oc.promptStash.<profile>.<id>` (legacy inline attachments and `attachmentFormat: 1` refs both read), `oc.promptHistory.<profile>` (50, dedup, newest first), owner marker `oc.stashAttachmentVault.<profile>` before publishing; 50 saved prompts never evicted; corrupt shelf refused (`Storage(Unreadable)`), collection suppressed on unknown versions or corrupt refs; restore never consumes the source; `deleteWithUndo` (exact text, metadata and bytes; one use; refused after the profile is gone); `clearForProfile`; secret-like identities refused. |
| Phrases | `phrases/PhraseChips.kt`, `phrases/PhraseMomentRules.kt`, `phrases/Starters.kt` | `PhraseRules` (normalize with Dart/JS Unicode whitespace, recordable, secret-like via `Redactor` or 20+ char run, redo phrases), `PhraseChipStore` over `oc.phraseChips.<profile>` (`{v:1,hidden,entries}` byte-compatible), ranking with the 14-day half-life, starters first, hide / show hidden, bounds 50/100, `close()` before the sweep; `PhraseChipStores` (one per profile, `changes` flow, device switch `oc.phraseChips.enabled` default on); `PhraseMomentRules.derive` over `:core:model` `MessageWithParts` (with a local port of `MessageErrorKind.fromName/refineFromText`); `Starters` (kinds, facts line, project name, ellipsis → trailing space). |
| References | `references/ReviewReferences.kt` | `ReviewReference` (label, signature, `toPromptText` with whole-line truncation stated in the text and fences longer than any backtick run), `format` with the max-min fair 24k budget, `ReviewReferences` per-session staging (10 max, duplicate/full outcomes, silent unknown removal, `take`). |
| Input | `input/Mentions.kt`, `input/ComposerInput.kt`, `input/PromptHistory.kt` | `resolveFileMentions` (boundary rules, UTF-16 ranges), `projectFile` (Dart `Uri.file` encoding), sub-agent mentions (longest first); `/` and `@` caret triggers, `/command args`, `!shell`; `SendDecision` (empty → mic, read-only, in flight, shelf busy, offline → "send when back online", commands/shell, **steer whenever a turn runs and steering exists**, else the server's mid-turn effect, else "cannot send yet"); `PromptHistoryKeys` + `PromptHistoryNavigation` over a Compose-free `ComposerValue`. |
| Photo recovery | `photo/PhotoRecovery.kt` | `PendingPhoto` (`oc.pendingPromptPhoto`, Flutter keys), `PhotoRecoveryStore` (`begin` commits the origin before launch and refuses while one is pending, `complete` sniffs PNG/JPEG/GIF/WebP and ≤ 10 MiB then stores in the photo vault, `markFailed`, `readPending`, `discard` payloads-then-record, `clearForProfile`), `DraftPhotoRecovery.recover()` in the drafts lane (joins its own conversation's draft with text and place, dedupes after a failed discard, profile removal wins). |

Tests: `commonTest` 10 classes / 100 tests (run on the desktop JVM), `desktopTest` 1 class / 2 tests (real files).

## 2. Catalog rows covered

Test names start with the row id. "Engine part" means the row also needs UI, the platform picker or the app graph.

| Row | Status | Test class |
|---|---|---|
| B-CHAT-017, 018 | covered (kinds; the words are `:ui:words`') | `StartersTest` |
| B-CHAT-077…084 | covered | `PhraseChipsTest` |
| B-CHAT-085, 086 | covered | `PhraseMomentTest` |
| B-CHAT-087 | covered (contract level: which file parts a send carries) | `ComposerInputTest` |
| B-CHAT-088 | covered (engine) | `StashTest` |
| B-CHAT-089 | covered (key filter + navigation; the real key wiring is S3-COMPUI) | `ComposerInputTest` |
| B-CHAT-098, 099 | covered (the `description` copy moves to `:ui:words`) | `ReviewReferencesTest` |
| B-STATE-016 | covered | `AttachmentVaultTest`, `FilePayloadStoreTest` |
| B-STATE-059 | covered (the sweep itself is `:core:data` ProfileScope) | `PhraseChipsTest` |
| B-STATE-085 | covered (engine; "restore preserves source and Undo refuses later edits" of the composer swap is S3-COMPUI) | `StashTest` |
| B-STATE-086 | covered (engine) | `StashTest` |
| B-STATE-088 | engine part (record, join, dedupe, deletion race) | `PhotoRecoveryTest` |
| B-STATE-100, 101 | covered (engine) | `DraftsTest`, `AttachmentVaultTest` |
| B-STATE-102 | engine part: full store refuses, failure kept and retried; the leave sheet is UI | `DraftsTest` |
| B-STATE-136 | covered | `StashTest`, `AttachmentVaultTest`, `FilePayloadStoreTest` |
| B-STATE-138 | covered (snapshot before the queued write; clear waits via the one lane) | `StashTest` |
| B-STATE-140 | covered (engine) | `StashTest` |
| B-STATE-141 | **part**: empty/closed scope and stale removal; the chat-level "stash and switch" location token is not ported | `StashTest` |
| B-MEDIA-008 | engine part (record before launch, failed record = no launch, cancel, bytes kept, late result, pending blocks, fake extension, oversize) | `PhotoRecoveryTest` |
| DraftRestoreTest rules 1, 2, 3, 5 | covered (rule 4, voice partials, is a caller contract stated on `DraftAutosave`) | `DraftsTest` |
| Steer-never-queue / send guards (brief) | covered as a pure decision | `ComposerInputTest` |
| **B-STATE-087** | **not done** (older drafts → Saved prompts migration) | — |
| `ComposerAppliesOutcomesTest` (plan 09 §15.2) | **not done**: `PromptDelivery.outcomes` (S2-DLV) does not exist on this branch | — |

## 3. Commands and results

Environment: Android command-line tools `commandlinetools-linux-16111833_latest.zip` into
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `sdkmanager "platform-tools" "platforms;android-37.1"
"build-tools;37.0.0"`; `native/local.properties` (gitignored) `sdk.dir=/opt/android-sdk`; JDK 21.0.12 runs Gradle (JDK 17
toolchain provisioned by foojay). Maven Central answered HTTP 429 through the proxy, so a container-only init script
`~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo) puts Google Maven and Google's Central mirror first for
plugins and dependencies.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :core:data:compileKotlinDesktop :core:testing:compileKotlinDesktop` | **FAILED** in `:core:data` (`Failure.Storage` now takes a `StorageProblem`; 3 call sites); `:core:common` compiled |
| 2 | `./gradlew :core:testing:compileKotlinDesktop`, `./gradlew :core:security:compileKotlinDesktop` | BUILD SUCCESSFUL |
| 3 | `./gradlew :engine:composer:desktopTest` (per slice while building) | green after each slice; two of my own test mistakes fixed, and one real bug found (§5.2 item 3) |
| 4 | `./gradlew :engine:composer:compileAndroidMain` | BUILD SUCCESSFUL |
| 5 | `./gradlew :quality:test` | **could not run**: `:engine:connection` fails to compile (`GatewayFactory.backend` unresolved), outside this write set. K3/K4/K5/K10/K11/K16 checked by hand over the module (one KDoc mention of `Dispatchers.` reworded) |
| 6 | `./gradlew :engine:composer:desktopTest :engine:composer:compileAndroidMain :engine:composer:compileKotlinDesktop --rerun-tasks` (final) | BUILD SUCCESSFUL; **102 tests, 0 failed, 0 skipped** |
| 7 | `git diff 6b9a2b21 \| grep` for RFC 1918 / CGNAT addresses and email addresses | no match |

Per class: AttachmentVaultTest 7, ComposerInputTest 10, DraftsTest 16, FilePayloadStoreTest 2, PhotoRecoveryTest 8,
PhraseChipsTest 19, PhraseMomentTest 10, ReviewReferencesTest 9, StartersTest 7, StashTest 14.

Not run: `build` (rule 12), Android host tests (the module has no platform code), iOS, any device.

## 4. Library and versions

No new catalog entries. Used: `kotlinx-io-core` 0.9.2 (catalog entry existed, first use), plus the convention set
(coroutines 1.11.0, serialization-json 1.12.0, collections-immutable 0.5.2, coroutines-test, Turbine via `:core:testing`).

## 5. Deviations and decisions

### 5.1 Storage ports instead of `:core:data`

`:core:data` does not compile on this branch (row 1 above), and the brief says to depend only on modules that compile.
The engine therefore defines `ComposerPrefs` (exactly the `KeyValueStore` shape it needs: synchronous acknowledged
reads, one all-or-nothing `commit`) and `PayloadStore`. Because `KeyValueStore.commit` publishes only acknowledged
values, the Flutter "optimistic cache after a refused write, reload prefs" code paths were not ported; a refused commit
simply changes nothing. **Follow-up:** a 20-line `KeyValueStore` → `ComposerPrefs` adapter (lane per profile, Flutter
key names already logical) once `:core:data` compiles, and the ProfileScope registrations in §6.

### 5.2 Behaviour

1. **Restore rule 2 follows Flutter, not the plan's wording.** Share-intent *text* replaces the saved draft; shared
   *files alone* keep the saved words and replace only the saved files (`chat_screen.dart:486-503`). Plan 09 §10.8 says
   "text or attachments replace the saved draft"; flagged for the chat lead.
2. **Photo recovery is split at the platform line.** The engine owns the record and the draft join; reading the
   platform's lost result (`retrieveLostData`) is `:android:platform`'s: it calls `complete`/`markFailed`/`discard`.
   `recover()` answers `RetryRequired` while the record has no bytes yet.
3. **Bug fixed relative to Flutter:** `FilePayloadStore.delete` also removes `<id>.tmp`. Flutter listed dead `.tmp`
   files as uncommitted and asked to delete them, but its delete only targeted `.blob`, so they counted against the
   256 MiB budget forever. Found by `FilePayloadStoreTest`.
4. **No copy in the engine.** Starters are `StarterKind`s, the facts line is `StartFact`s, refusals are `Failure` values
   or small enums (`VaultProblem`, `PhotoProblem`, `SendBlock`); `ReviewReference.description` was not ported (copy).
   The reference *prompt text* stays here: it is content sent to the agent, not UI copy.
5. **Send decision included.** The brief names steer-never-queue, send guards and slash commands; plan 15 C15-1 keeps
   the steer *execution* in S2-DLV. `SendDecision` only chooses the action (Steer whenever a turn runs and
   `promptSteering` exists); delivery performs it.
6. **Failure mapping.** No new `Failure` leaves: vault limits/quota → `Storage(Full)`, bad scheme/too many/duplicate id/
   secret identity → `Storage(InvalidValue)`, refused commit → `Storage(WriteRefused)`, corrupt shelf →
   `Storage(Unreadable)`, closed profile → `Refused(ProfileDeleted)`, empty scope → `Refused(StaleScope)`, a used Undo →
   `Refused(AlreadyAnswered)`. The words layer may want distinct sentences for "draft attachment limit" vs "storage full".
7. **Duplicated helpers (TODO(move-to-core))**: pure-Kotlin SHA-256 (same as `:core:data` `DartJson.sha256Hex`) and
   `MessageErrorKind.fromName/refineFromText` (plan 09 assigns them to `:core:model`).
8. **Older-draft migration (B-STATE-087) and legacy inline stash migration (`migrateAttachments`) not ported**: both
   are one-time migrations that belong with the migrator's ordering (`MigrationRunner`); the stash reads legacy inline
   records unchanged meanwhile.
9. **Test-first was per slice, not per test**: each slice's tests were written from the catalog rows and the Flutter
   tests before running, in the same sitting as the code; the red evidence is the `.tmp` bug and two wrong test
   expectations corrected against the Flutter behaviour (explicit profile deletion may erase corrupt stash metadata).
10. **No formatter run**: `tool/format-changed.sh` (AGENTS rule 13) does not exist in the tree; code follows the
    surrounding style by hand.

## 6. Catalog requests and requests to other owners

No library requests.

- **Coordinator:** add `:engine:composer	S	chat-lead	slice-3` (or the owner the coordinator names) to
  `native/MODULE_OWNERS`; `ModuleOwnersTest` will be red until then.
- **`:core:data` owner (ProfileScope registrations, when wiring):** per-profile keys `oc.phraseChips.<id>`,
  `oc.promptStash.<id>.<item>`, `oc.promptHistory.<id>`, `oc.stashAttachmentVault.<id>`; app-wide keys holding
  per-profile rows `oc.sessionDrafts` (swept through `Drafts.closeProfile`) and `oc.pendingPromptPhoto` (through
  `PhotoRecoveryStore.clearForProfile`); app-wide `oc.draftAttachmentVault`, `oc.phraseChips.enabled` (never swept);
  files: owner directories `sha256(profileId)` under `draft-attachments-v1`, `prompt-stash-attachments-v1`,
  `prompt-photo-recovery-v1` (deletion order: close the stores, then sweep; each store's `close*/clear*` drains its lane).
- **S2-DLV:** `unconfirmedTexts(ref)` for `Drafts.restoreFor`, and `PromptDelivery.outcomes` so the composer can apply
  them (`ComposerAppliesOutcomesTest`).
- **`:core:model` owner:** take `MessageErrorKind.fromName/refineFromText` (ported here in `PhraseMomentRules`).
- **S0-CORE:** take the SHA-256 into `:core:common` (one copy with `DartJson`).
- **S3-COMPUI / S3-MEDIAATT:** the engine entry points are `Drafts`, `DraftAutosave`, `PromptShelf`,
  `PhraseChipStores`, `PhraseMomentRules`, `Starters`, `ReviewReferences`, `Mentions`, `ComposerInput`,
  `SendDecision`, `PromptHistoryNavigation`, `PhotoRecoveryStore`, `DraftPhotoRecovery`.

## 7. Open problems

1. Not wired to the real preference file or the app graph (Metro) until `:core:data` compiles (§5.1).
2. `:quality:test` not run on this branch (row 5); `ModuleOwnersTest` needs the coordinator's row.
3. B-STATE-087, the stash inline-attachment migration and the chat-level B-STATE-141 location token remain.
4. A server re-added under a removed id must be reopened in each store (`Drafts.reopenProfile`,
   `PromptShelf.reopenProfile`, `PhraseChipStores.reopenProfile`); the profiles engine has to call them.
