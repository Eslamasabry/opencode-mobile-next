# BD2 evidence and skip policy

Finish line: a full Android quality run rejects malformed commit subjects and unformatted changed Dart files. Non-goal: triggering CI, modifying history, installing shared git hooks, or weakening the mandated skip marker.

The existing G33 body/trailer rules and `[skip ci]` remain required. Conventional subjects use `type(scope): plain sentence`, optional scope and breaking `!`. Merge commits retain their exemption from subject/body/trailer rules. Full checkout history permits actual PR base/head ranges. The named commit check is inside required `checks`; formatting uses CI's selected Dart and language version 3.10. A manual full run must supply `commit_base` explicitly.

Policy: keep `[skip ci]` on swarm commits. GitHub skips push/pull_request workflows for that marker, so such PRs require an explicitly authorized full `workflow_dispatch` run on the exact candidate branch with its base revision before merge. Do not claim the marker can be ignored by a pull_request workflow or that adding a step makes skipped workflows run. Branch protection must require the full gate; APK-only runs are not merge evidence (existing release preflight also rejects them). A gate triggered by an unskipped PR checks the same mandatory rules and fails commits lacking the marker.

`python3 -m unittest tool/qa/test_check_commits.py`: 4 passed, covering valid range, malformed subject, missing marker, real pinned Dart format failure. Removed new subject validation: malformed-subject regression failed; restored. `bash -n` passed. Workflow Python YAML parse and required-check dependency read passed. actionlint is unavailable. No CI was triggered.

Reference: [GitHub skipping workflow runs](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-workflow-runs/skipping-workflow-runs). Skip instructions affect push/pull_request, not workflow_dispatch.
