# FQ3b — protocol diagnosis and repeatable certification

Finish line: explain the OC2 inference and OC1 post-abort failures with app-managed/owned-runtime evidence and failing regressions, fix their actual cause, then run both pinned protocols on app2196 and generate truthful matrix cells with owned-session cleanup.

Non-goal: provider enrollment, moving credentials between engine namespaces, UI changes, BA-owned phone_agents edits, publication, signing, or qualification beyond the recorded emulator runtime.

Base: feat/genui-fe75ac5ae766e06c54227606cc02712ec3aa0439cd; branch sol/bc-fq3b. Root owns device/common transport, live emulator sessions, artifacts and all machine-locked checks. WorkerA owns OC1 adapter/tests, workerB owns OC2 adapter/tests, workerC owns orchestration/evidence/manifest/reconciliation/matrix generation and corresponding tests. App protocol/state changes require explicit ownership transfer before editing if diagnosis finds a real app bug.

Frozen adapter contract: runProtocol1/2(Fq3Wire,ProbeOptions) unchanged. Scenario options.title is run-engine-scenario; each adapter appends a session ordinal. Device contract: PhoneRuntime.inspect(runID); start(bool oc2,{bool appManagedOnly=false,bool forceOwned=false}). App-only comparison refuses a wrong engine at4097 rather than starting an auxiliary process. Metadata contains fixed outcome codes and bounded booleans/counts and validated public provider/model references; passwords, provider configuration, replies and raw exceptions remain in memory.

Device sessions use /home/eslam/Storage/tmp/oc-emulator.lock. Normal installed APK is2196 from /home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk, known local signer1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C. Restore2196 with install-r-d inside the lock after device runs; never uninstall or clear data. Capture actual engine/server provenance, CLI+HTTP versions, appUID/build and exact owned-session IDs. Cleanup every complete/failed run; interrupted-run recovery only admits exact captured ownership.

Focused checks run serially under machine_lock with pinned Flutter3.47.1. Record guard-removal red proofs, focused passes and analyzer; coordinator owns the integration full suite. New local commits carry [skip ci]. Final status must say SEQUENCE COMPLETE: FQ3b and distinguish actual per-capability qualification from implemented harness/backend changes.
