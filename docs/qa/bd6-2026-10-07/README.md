# BD6 evidence

Finish line: an authorized Android quality build with one test APK posts/updates its download link on every associated current PR, including forks. Non-goal: sending a GitHub comment now, running CI or making a test APK a release.

The upload remains in android-quality.yml. The separate workflow_run listener runs trusted default-branch inline code and uses only the GitHub API; it never checks out PR code or downloads/executes an artifact. This permits fork comments without granting a fork build a write token. It refuses absent/expired/ambiguous artifacts and stale PR revisions, updates only this bot's marked comment, and names a failed quality run honestly. Normal 7-day artifact expiration is stated.

`node --test tool/qa/test_apk_comment.mjs`: 6 passed against actual inline workflow code with fake APIs (normal/fork, stale head, expired/ambiguous/absent artifacts, idempotent update). Temporarily removed head-SHA guard: stale regression failed; restored and passed. GitHub-script v8 SHA verified with read-only git ls-remote. Python YAML parse and every Android workflow bash block's `bash -n` passed; actionlint unavailable. No network writes or comments were sent.

Operational limitation: no APK link exists when a build never runs (mandatory `[skip ci]` requires owner-triggered full gate), fails to build, or artifacts expire. Listener must first reach the default branch; repository Actions write-token policy must permit bot comments. No claim of deployed automation.

Integration edge: use the workflow event's recorded PR head when present, so merge-run identity can differ from the actual PR head without losing its APK link. A sixth actual-inline-script fixture passes for this case and rejects a stale PR; removing recorded-head selection makes it fail. Manual runs without PR metadata use their run head.
