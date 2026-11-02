# Native composer verification — in progress

Candidate branch: native/full-composer-2026-11-02, based on diagnostics 7f23ae13. Preview target: 10003, side-by-side package. Nothing in this note establishes APK, parity or release acceptance yet.

Actual GLM route: `zai-coding-plan/glm-5.3` in OpenCode CLI on the VPS. Six isolated workers: draft-files, android-files, protocol-models, delivery-rich, world-rich, ui-rich. Coordinator owns all Gradle/test/emulator processes and reviews all patches before adoption.

## Feasibility proved before enabling

The running isolated PC OC2 2.2.5 backend exposes eight enabled models at /api/model. A newly-created, task-owned session accepted two POST model choices (204), and each GET session readback matched provider/id. No owner session changed. Local private receipts: model-contract-proof.json. App integration is still pending.

## Required checks

* New tests must fail against the previous implementation, then pass on the final unchanged candidate.
* engine/composer: restart exact Unicode text/file recovery, missing/corrupt payload preserved, file removal, older ack/newer file/text survival, profile-delete late writer fence, refused disk commit leaves accepted chip unchanged.
* protocol/oc2: strict model body + readback/default/variant, safe catalog mapping and unsupported shape refusal.
* engine/delivery: rich immutable snapshot, model apply/readback before prompt, refusal/stale/Stop causes zero prompt dispatch, steering remains direct with no queue fallback.
* feature/chat: actual kit-based model sheet and file controls, attachment-only send, picker cancellation/errors, draft restoration/IME/selection, no local-Ok clear, EN/AR + RTL.
* API35 emulator: real system picker, attach/remove/restart/restore, select different model/restart/readback, real prompt using attached fixture word, exactly one canonical prompt, next unsent edits retained, only own profile cleanup removes files.
* Final affected module suites + architecture gates; APK package/version/source identity; signer must match previous accepted native preview. Full Flutter parity, all native modules, iOS and production release remain separate gates.

## Adapted Flutter acceptance sources

* test/model_picker_test.dart: target-session selection isolation; failed save keeps the picker open; selection persists. Native tests and device receipts to be added after running.
* test/draft_attachments_test.dart: exact Unicode bytes after restart, metadata contains references only, owner isolation, shared-blob retention, missing/corrupt payload refusal.
* test/session_draft_test.dart: acknowledged durability, independent profiles, restart and deletion.
* test/chat_attachment_reader_test.dart: bounded intake and unsupported/oversized file refusal.

The previous text-only facade's test refusing every saved attachment is changed to reject a corrupt stored attachment. Valid attachments now have an enabled restoration path; corruption must continue to preserve the original metadata and blob bytes.

## Failing-first receipt

CoordinatorComposerModelTest ran against the previous OC2 implementation before adopting worker code: **2 tests, 2 assertion failures**. Session selection was null, and catalog returned unavailable. Command: `:protocol:oc2:desktopTest --tests '*CoordinatorComposerModelTest'` under the PC Gradle lock. Build completed the test process in 41 seconds; failures were assertions, not compilation or harness errors. The JUnit receipt and candidate source identity are retained outside the repository.
