# FQ3 protocol certification contract

Finish line: a repeatable locked run against build 2195's in-app Ubuntu records observed versions and individual protocol assertions for both engines, and generates the certification matrix from bounded sanitized evidence.

Non-goal: UI rendering, account enrollment, installation, runtime readiness promotion, app replacement, or changing existing user chats/configuration. Protocol results are namespaced separately from UI/install evidence and do not grant UI capabilities.

The Dart harness uses `Fq3Wire.request(method, path, {body, query})`, `openEvents(path, {query})`, `closeEvents()`, `events`, and `waitFor(predicate, {timeout})`. Paths include `/api` for OC2. Credentials remain in memory and HTTP Authorization headers only. Request/response bodies and exception messages are never emitted. `ProbeRun.check(key, action)` records fixed assertion codes plus bounded numeric/bool facts. Each adapter returns `ProbeRun` with `sessionIDs`, `historyCounts`, `observedVersion`, and `results`.

Capability keys: version, create, models, modelSwitch, stream, abort, reconnect, permissionAllow, permissionDeny, image, cards. Root records protocolSwitch by refetching both owned histories through fresh clients in OC1 → OC2 → OC1 → OC2 order. This is connection switching, not proof of app UI selection or server process restart. No volatile OC2 replay or Last-Event-ID headers.

Adapters own disposable sessions only. Permissions apply only to these sessions; no global PATCH or restart to force MCP reload. Missing runtime/model/tool/permission prerequisites produce explicit failed assertions. Image proof requires an image-capable model and a content answer, not successful admission alone. Cards proof requires an actual assistant tool call and a subsequent matching answer receipt. Script-generated matrix entries retain the narrower protocol scope.

Device driver owns emulator lock, ephemeral adb forwards, UID/version attestation, runtime password access and exact cleanup. Root adb is available but release `run-as` is unavailable. The approved existing in-app runtime password file `/root/.oc-builtin/server.password` (the app's own launch configuration) is read under the app UID, without reading secure-storage blobs. Guest version probes execute under the Android app UID and existing PRoot; no real-UID-0 guest writes.
