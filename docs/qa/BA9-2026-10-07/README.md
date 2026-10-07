# BA9 launch ordering and revocation

Base ce2d9223. Backend contract: ../../design/BA9-contract.md.
Default no-op registry, no browser enabled; no native/UI/BE edits, device session,
APK build, signing, push or publication. Fake daemon/native-port tests prove Dart
ordering and lifetime fences only. BE owns native/runtime/network qualification.

Verification results are appended after the unchanged final candidate gate.

## Verification

Pinned Flutter 3.47.1; every Flutter invocation used machine_lock test/analyze.
Format: pinned Dart, --language-version=3.10. Source/test candidate hashes are
in pre-style-candidate-sha256.txt, based on ce2d9223 plus this slice.

Focused new registry/gateway tests: 32 passed. Controller ownership/revocation
check: 1 passed. Revert controls failed for cold create, abort/delete/resume
revocation, pending-create cancellation, cross-gateway command suppression,
folder source binding, and quarantine intent. Restored before final checks;
negative-controls.txt lists the actual failed test names.

Functional candidate affected batch: **193 passed**, all files completed,
no skips, --no-pub --concurrency=1:

```
test/browser_claude_launch_test.dart
test/paseo_browser_launch_test.dart
test/paseo_gateway_test.dart
test/paseo_correlated_prompt_test.dart
test/paseo_images_test.dart
test/paseo_chat_feed_source_test.dart
test/phone_agents_controller_test.dart
test/phone_agent_owner_test.dart
test/gen_ui_controller_test.dart
test/agent_certification_test.dart
```

This is focused coverage, not the full suite. Coordinator owns the full merge
gate and any live native/browser/device proof. Snapshot generation parity and
git diff --check passed. Analyzer result follows below.

Analyzer initially reported eight style infos. Added braces and made the test
helper private; no behavior changed. The final style candidate is separately
hashed in candidate-sha256.txt. Reran every touched test/production-gateway
check: browser_claude_launch_test, paseo_browser_launch_test,
paseo_gateway_test and paseo_correlated_prompt_test: **76 passed**.
The ten-file 193-test result above belongs to the pre-style snapshot; these
results are not combined into a new 193-test gate claim.

Final pinned `flutter analyze --no-pub` under the analyze lock: **No issues
found**. Final source/test hashes match candidate-sha256.txt. Diff check clean.
No full-suite, physical device, native browser/runtime, build or release claim.
