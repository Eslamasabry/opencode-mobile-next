# FQ3 — repeatable in-app Ubuntu protocol checks

Candidate branch: `sol/bc-fq3`, based on integration `8644d15d`.
Target: emulator-5554, installed app build **2195**, OC1 **1.18.32**, OC2 **2.0.10**. This is emulator protocol evidence; existing matrix UI/install evidence stays separate. No app installation, data clearing, credential enrollment, readiness promotion or network upload is part of this runner.

Run from the repository root with the pinned Dart:

```sh
/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart \
  tool/qa/fq3_certify.dart --run-id fq3-20261008-qualified \
  --oc1-model zai-coding-plan/glm-5.3 --oc2-model zai-coding-plan/glm-5.3
```

The runner acquires `/home/eslam/Storage/tmp/oc-emulator.lock` for each scenario and releases it between scenarios. It uses ephemeral `adb forward tcp:0`, attests the installed build and Android application UID, and reads only the runtime's launch password configuration under that UID. The release app has no `run-as`; no Keystore/secure-storage extraction is attempted. Passwords travel in memory and Authorization headers, never in command arguments, URLs, committed artifacts or output. Errors export fixed codes and numeric/boolean facts only; responses, transcripts, logs and provider configuration are not saved.

If the app-managed port 4097 already serves the requested exact engine, the runner reuses it. Otherwise it starts an owned loopback server using the installed native PRoot, Android app UID, app-private Ubuntu, project bind and installed CLI. OC2 keeps the app's isolated XDG/config/database paths. These auxiliary processes exercise the same installed runtime and histories; they do not certify Android service lifecycle/confinement, foreground-service restarts or UI selection. All prompts/permissions use owned disposable sessions and a separate project. Cleanup signals only captured owned PIDs whose kernel start times still match, then removes only its own forwards and empty scratch/project directories. No shared service is stopped or reloaded.

OC2 stable detection follows the app's pure `Api2Dialect` mapping: shaped `/api/info` after a clean `/api/health` 404; stable permission HTTP replies use `decision`, while events still carry `reply`. Reconnect opens a new event/HTTP connection and refetches sessions, history, permissions and forms. No Last-Event-ID or volatile replay is used.

Assertions cover observed CLI plus HTTP versions; session retention; usable model discovery and a distinct model selection with inference; completed reply plus fresh assistant deltas; interruption after a live text delta and a usable next prompt; Allow and Deny against a genuine own pending harmless `printf` tool; an answer about an unlabeled two-color PNG; a completed executed assistant `oc-ui_show` call followed by its exact call-ID answer receipt and acknowledgement. Model availability, successful prompt admission, a connected MCP server and unit fixtures alone cannot pass these checks. Ask rules are scoped to the disposable permission sessions. Missing prerequisites and timeouts are failed cells, not assumed capability support.

The final history check connects OC1 → OC2 → OC1 → OC2 through fresh clients and verifies both sets of owned session/message identifiers remain unchanged, then deletes only the runner's exact session IDs. This checks protocol connection switching and durable dialect isolation, not UI navigation, replay or killing the installed app. Prior user chats remain untouched.

Each scenario writes a bounded sanitized JSON file. The final report identifies the harness source revision, app build, actual versions, attestation and individual assertions. `tool/qa/fq3/update_matrix.py --run <report>` validates it and generates both matrix JSON and Markdown. Its `protocolCertification` namespace cannot grant existing BA4 UI capabilities or override previous UI/install cells. After a reviewed matrix update, refresh the bundled snapshot with `python3 tool/agents/generate_certification.py`.

Local verification: the focused adapters, real loopback HTTP/SSE/auth/dialect/redirect tests, process identity tests, existing certification parser tests and Python evidence-validation tests run separately from the phone proof. Guard-removal red logs demonstrate that stale deltas and mismatched observed pins fail their regressions; guards are restored afterward. The full repository suite, APK build, instrumentation and physical-device qualification are outside this tooling slice.

Exploratory findings: stock catalog selection picked a different model from the app's configured GLM use; the reproduction command selects the observed connected `zai-coding-plan/glm-5.3`. On this shared emulator a completed owned reply appeared after the initial 65-second deadline. The final runner allows 100 seconds for a reply and keeps a scenario budget; such waits remain failed if the deadline expires. Native launch uses exported loader variables, matching the app's ProcessBuilder environment; Android's extra outer `env` layer failed before PRoot.

Final run and outcomes are appended after execution. None of the exploratory or controlled fixture results are imported as live certification.

Exploratory cleanup correction: TERM alone left owned PRoot descendants running. The earlier Android shell UID could not reliably inspect their process metadata, and the guest cwd view did not identify them. Root-side kernel metadata plus the exact generated `--rootfs`/`--cwd` argv identified 18 owned processes, which were removed by exact PID/start-time checks; no process-name pattern kill was used. Android restarted during this memory-pressure episode; app build 2195 and its private data remained installed. The final driver captures descendants, uses TERM then KILL only for matching owned identities, verifies they are gone, and never stops the app-managed port. Root adb was restored after Android's restart. These exploratory attempts are not certification results.

Local gate before the recorded candidate: 81 serial focused Flutter checks passed (including existing certification parsing), 19 Python validation checks passed, both guard-removal red proofs failed at the expected assertion and were restored, full pinned Flutter analyzer clean. No full suite or APK build was run for this QA-only change.

Evidence freshness: every phase carries a unique attempt token, candidate revision, run ID and attested build/UID/both version witnesses. Nonzero child exit, stale files, mixed identities or incomplete cleanup cannot contribute passes. Lock acquisition waits without owning the emulator; each completed scenario releases it. History equality uses ordered durable content digests only in memory, never transcript or digest artifacts. The interrupted `fq3-20261008-cert` attempt is provisional evidence only and is not imported into the matrix.
