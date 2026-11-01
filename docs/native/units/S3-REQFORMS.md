# S3-REQFORMS — forms, question sheets, approval modes and the always-allow invitation

Date: 2026-11-01. Branch: `native/reqforms-2026-11-01`, created from `claude/native-reqforms-2026-11-01-0qhpdz` at
`7db496b2` (the M0 tip). Write set: form files of `native/engine/requests/**` (new files only) and form files of
`native/ui/kit-chat/**` (new files only); this report.

Shipping state: implemented and verified locally with module tests (commands in §3), committed, pushed to the branch.
**Not** integrated into `:feature:chat` (outside the write set), not enabled in the app, no PR, no CI, no release.

Finish line (plan 15 row): *one `FormSpec` engine (OC2 `hidden` and `when`, MCP/ACP elicitation, AskUserQuestion), the
question sheet, approval modes and the always-allow invitation work; forms come back after a forced reconnect.* What
works now, as engine + kit parts with tests: the OC2 form engine (hidden, when, every field kind, MCP elicitation as a
connection-level owner, the OC2 `question` tool flagged), the question-sheet rules and lane, approval-mode resolution and
the auto-approve sweep, the always-allow invitation, and forms rehydrating after a forced reconnect with a held answer
reaching the server once. ACP / Codex elicitation adapters and the screen wiring are **not** done (§5).

---

## 1. What was built

### `:engine:requests` (new module directory; all files new)

| File | What |
|---|---|
| `build.gradle.kts` | Stand-in build file (`oc.kmp.library`, deps `:core:common/model/domain`). **S2-REQ owns the module**: if its build file lands first, keep S2-REQ's (it only needs these three deps for my files). |
| `forms/FormModel.kt` | Plan 09 §5.5 `FormSpec` / `FieldSpec` / `FieldKind` / `FormValue` / `FieldCondition` / `FormOwner` (`Session(id)` or `Connection` for MCP elicitation, `sessionID: "global"`). `isQuestion` for the OC2 `question` tool (`metadata.kind = "question"`). |
| `forms/Oc2FormAdapter.kt` | Core's wire `FormSpec` (fields keep their raw JSON) → `FormSpec`. Port of Dart `Api2FormField.fromJson` + the kind choice in `form_renderer.dart` (string+options → Select; `date`/`date-time`; multi-line unless `maxLength ≤ 120`). Tolerant: keyless fields and unknown types dropped, wrong-typed limits absent. |
| `forms/FormRevision.kt` | Port of `form_request.dart`: schema revision (sorted keys at every depth, list order and JSON types kept) and the identity key (parts hashed as one JSON list → no delimiter collisions). |
| `forms/FormRules.kt` | `evaluate` (one forward pass; unanswered reference = false; `eq` on multi-select = includes; hidden fields active with their default), validation problems (`FieldProblem`, one sentence each for `:ui:words`), `payload` (active only; whole numbers without fraction), `isSensitive` (OpenCode's credential regex), `prefersFullHeight` (≥ 5 declared visible fields or a description > 140; ignores `when`), `initialDraft`. |
| `forms/FormDrafts.kt` | Per-form draft keeping (by identity, so a new revision never inherits) and the `oc.draft.<target>.<profileId>` key for multi-line answers. |
| `forms/HeldAnswers.kt` | The hold-then-send lane shared by forms and question sheets: 3 s hold with Undo, one write slot, exactly one write, second answer ignored, `flush` (leaving the chat / `BackgroundIdle`) never double-sends, 8 s "Not confirmed yet", a late failure after the request left shows nothing. |
| `forms/FormRequests.kt` | OC2 forms of one connection: `FormCreated`/`FormSettled`/`SessionDeleted`, `refresh()` hydration (in-flight answers survive it), `answer(id, draft)` re-validates (invalid drafts never send), `decline`, `undo`, `flushHeld(session?)`; 400 (any failure but below) keeps the form with the reason, 409 closes with one "answered elsewhere" notice, 404 closes silently. |
| `questions/QuestionSheet.kt` | Question-sheet rules: presentation (in place / request sheet / full sheet), single-select replaces and clears own words, multi-select keeps them, answers in offered order then own words, `block` (unanswered required prompt, value not offered). |
| `questions/QuestionRequests.kt` | Sheet questions on the session-scoped question endpoints: refresh hydration, wrong session never sends, invalid never sends, hold/Undo/once, `QuestionNotFound` (404) resolves locally. |
| `consent/AlwaysAllowInvitation.kt` | Port of `repeated_permission_consent.dart`: third distinct identical ask offers once per scope; gated on `persistentPermissionGrants` and never while auto-approving; durable claim first; digests only; Flutter storage format (`oc.permissionConsent.<profileId>`, version 1, 256 entries / 128 KiB); corrupt/refused storage suppresses. Storage via a small `ConsentStorage` port. |
| `consent/ApprovalModes.kt` | `ApprovalModes.effective` (port of `effectiveFor`: own setting, inheriting ancestor, an explicit non-inheriting ancestor stops the walk, `"*"` approve-everything, else ask; cycles end) and `AutoApprovalSweep` (answers "once" exactly once where automatic, failures stay visible with the reason, history latest 5 per conversation, cleared with the connection). |

### `:ui:kit-chat` (new files in `form/`)

| File | What |
|---|---|
| `form/KitForm.kt` | The one form renderer on kit-local models (`KitFormField`, `KitFormValue`; plan 09 C5): `KitField` for words/numbers/dates (`Secret` when sensitive), `KitSwitchRow`, radio `KitChoiceRow`s with "Something else" + own-words field, check rows for multi-select (offered order, own words last), a secondary `KitButton` for external links (tap → `onOpenLink`, the caller routes through the link policy). Inactive fields fold away; the caller owns the draft. |
| `form/KitFormSheet.kt` | `KitFormSheetBody` (title, description, scrolling fields, pinned Decline + Send; says why it cannot send yet; refused-answer notice; Send working while sending) and `KitFormCard` (needs-you card in the chat: title, "N questions", Answer, or the status line while an answer is on its way). |

## 2. Catalog rows covered (tests named `ROW what it proves`)

| Row | Test(s) |
|---|---|
| B-CHAT-028 | `FormSchemaTest` ×4 (key order, every constraint/nested/type change, field order, identity key collisions) |
| B-PROTO-022 | `FormSchemaTest` (OpenCode 2.1.1 Console form: hidden default sent, never shown, no "Select an option") |
| B-PROTO-044 | `FormSchemaTest` ×3 (kinds, owners, conditions); `FormRequestsTest` (events add/settle/delete, hold → post → settle, 400 keeps, 409 resolves locally with notice); `QuestionsTest` (deleted conversation drops questions); `KitFormTest` (refused answer shown, retry) |
| B-PROTO-048 | `FormRequestsTest` (late failure after remote resolution shows no error; failed refetch keeps provenance); `QuestionsTest` (`QuestionNotFound` resolves locally) |
| WP-04.11 acceptance | `FormRequestsTest` › forms come back after a forced reconnect and a held answer reaches the server once (refresh ×2 inside the hold, then `flushHeld`) |
| B-CHAT-072 | `FormRulesTest` ×2 (required, not offered); `FormRequestsTest` (invalid draft never sends); `QuestionsTest` ×2 (exact typed answers, wrong session, not offered, required) |
| B-CHAT-074 | `FormRequestsTest` (second answer while held/sending ignored, first decision wins) |
| B-STATE-024 | `FormRulesTest` (drafts per identity; `oc.draft.<target>.<profileId>` shape) |
| B-SEC-204 | `FormRulesTest` (credential-like fields are sensitive) |
| B-SEC-147 | `AlwaysAllowInvitationTest` ×5 |
| B-AGENT-221 | `FormRequestsTest` (forms never answered without a person); `AutoApprovalTest` (nothing auto-answered without a setting) |
| B-AGENT-222…225 | `AutoApprovalTest` ×5 |
| CHAT-GENUI-43/45–48 (map rows) | Undo/flush (`FormRequestsTest`), conditions/limits/payload/sheet height (`FormRulesTest`), presentation and select rules (`QuestionsTest`), renderer/sheet/card (`KitFormTest` ×5), STATE-8/STATE-10 receipts |

Plan 15's row also lists B-PROTO-029, 063, 069, 102, 103 (copied from WP-04.11). 063 (migration status), 069 (pairing QR),
102 (selection sync) and 103 (skill activation) are not about requests: **not covered**, they belong to the OC2 protocol
work. 029 (phone chat dispatch admission) belongs to the delivery engine; only its "settle once, never replay" idea is
exercised here through the answer lane.

Red first: each test file was written before its code ran; the first red state was a compile failure (the classes did
not exist), the second was two test typing errors. No assertion failed once the code compiled, so no test was seen
red on a behavioral assertion.

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk` (cmdline-tools
`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`). JDK 17
was provisioned by foojay. Container-only (not committed): `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's
Maven Central mirror first and `google()` before the plugin portal (Central answers 429 through the proxy);
`~/.gradle/gradle.properties` `-Xmx6g`, 4 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :ui:kit-chat:compileKotlinDesktop` (baseline) | success |
| 2 | `./gradlew :engine:requests:desktopTest` (first runs) | compile errors in two tests (typing) → fixed → 36 tests, 0 failures |
| 3 | `./gradlew :ui:kit-chat:desktopTest --tests '*KitFormTest*'` | 5 tests, 0 failures |
| 4 | `./gradlew :engine:requests:desktopTest` (with consent files) | 46 tests, 0 failures |
| 5 | **Final:** `./gradlew :engine:requests:desktopTest :ui:kit-chat:desktopTest :engine:requests:compileAndroidMain :ui:kit-chat:compileAndroidMain --continue` | **BUILD SUCCESSFUL**; engine/requests 46/46, ui/kit-chat 72/72 (67 existing + 5 new) |
| 6 | `native/tool/check_commits.sh 7db496b2 HEAD` | OK |

Not run (out of scope for this unit or needs other owners): `:quality:test` (`ModuleOwnersTest` will want a
`:engine:requests` row in `MODULE_OWNERS` — coordinator file), the kit-chat Roborazzi goldens (no new goldens recorded),
`allTests`, any app build.

## 4. Deviations

1. **`FormSpec` lives in `:engine:requests` (`…engine.requests.forms`), not `:core:model` `…core.model.forms`.** `:core:model`
   is the core owner's and already has the wire-shaped `FormSpec` (raw fields "for the forms engine"). Moving it is one
   import change; requested below. The engine imports the core one as `WireForm`.
2. **`FieldSpec` carries four more fields than plan 09's sketch**: `custom`, `minItems`, `maxItems`, `url` (OpenCode 2
   sends them; Dart validates them).
3. **Approval modes**: plan 17 §1.10 homes `AutoApproval` (store, mode-change flow, server resync) in `:engine:agents`,
   consumed through `AutoApprovalPolicy` in `:core:domain` (neither exists yet). Built here: the pure effective-mode walk
   and the request-side sweep, behind plain function parameters, so the coordinator can move the walk next to the store
   without touching the sweep. The store (`oc.autoApprove.<profileId>`), the mode menu, the server resync
   (`SessionPermissionSync`) and B-STATE-099 are **not** built.
4. **`FormDrafts.storageKey`** names the field by a 16-hex digest of its key (keys may hold dots). Only the key function
   and in-memory keeping are here; the persisted store and `ProfileScope` registration belong to the drafts owner.
5. **`QuestionRequests` serves every `PendingQuestion`**; S2-REQ's one-question card covers the in-place case. The
   coordinator should route `RequestAnswer.Question` for sheet presentations (`QuestionSheet.presentation != InPlace`)
   here, or let S2-REQ's `RequestsEngine` delegate both.
6. **Answer holds are per lane** (`HeldAnswers`), not S2-REQ's `DelayedAnswers` (not visible on this base). When both
   land, one of them should go (same rules: 3 s, Undo, flush, one write slot).
7. **Kit secondary answer**: `KitFormSheetBody` uses `KitButtonRole.Tertiary` for Decline (quiet, never accent).
8. **No formatter run**: `tool/format-changed.sh` does not exist on this base; files follow the surrounding style.

## 5. Open problems / not done

1. Integration into `:feature:chat` (card in the request slot, sheet presentation, words in `:ui:words`, Inbox/attention
   presentation of connection-level MCP forms) — outside the write set.
2. `AcpElicitationAdapter` and `CodexElicitationAdapter` (plan 09 §5.5 table: "with the backend" / slice 5) and
   `CardFormAdapter` (S5-CARDS) — not built; the model takes them without change.
3. Paseo plan approval ("never restores bypass mode", "keep planning denies without interrupt") — needs the Paseo
   gateway; not built.
4. A `FormCreated` with a new revision while an answer for the old revision is held keeps the held answer (it was
   validated against the old schema). Dart retires nested decisions on invalidation; the coordinator should decide
   whether a revision change cancels the hold.
5. The age ledger ("Needs you · 20 s") and stall-clock holding by global forms are S2-REQ / transcript work.
6. Kit-chat goldens for `KitForm` / `KitFormCard` (light/dark × en/ar) are not recorded.
7. Live OpenCode 2 checks not run (no server in this container; AGENTS forbids the owner's servers).

## 6. Catalog requests

None (no new libraries).

## 7. Requests to other owners

- **Core owner:** move `forms/FormModel.kt` into `:core:model` `…core.model.forms` (plan 09 §5.5) and rename the wire
  `FormSpec` (e.g. `WireFormInfo`), or confirm the engine home.
- **Coordinator:** add `:engine:requests` to `MODULE_OWNERS` (S2-REQ's row); keep S2-REQ's `build.gradle.kts` on merge;
  decide deviation 3 (where the effective-mode walk lives) and 5/6 (one question lane, one hold).
- **`:ui:words`:** one sentence per `FieldProblem`, `QuestionBlock`, `FormNotice.AnsweredElsewhere`, the sheet labels
  (Send answers, Decline this request, Something else, Answer, N questions, Sending your answers, Not confirmed yet), and
  the invitation and approval-mode words.
- **Drafts owner (`:engine:composer`):** persist multi-line form answers under `FormDrafts.storageKey` and register the
  prefix with `ProfileScope`.
- **S2-REQ (`RequestsEngine`):** bind `AlwaysAllowInvitation`'s `ConsentStorage` to `oc.permissionConsent.<profileId>` and register it.
