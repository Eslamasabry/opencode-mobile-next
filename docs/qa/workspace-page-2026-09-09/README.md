# Workspace page repair evidence

Finish line: recognize the current project, resume a readable session, or start once from a stable bottom action. Preserve complete path and review-state details, capability gates and large text.

Non-goal: global theme/navigation changes, protocol/storage changes or session regrouping. Owned files: Workspace screen, return brief card, Workspace hierarchy test and the dedicated capture fixture.

The before images are unmodified synthetic fixtures from integration checkpoint `0eabc2d`. The after images use the same 390 × 844 logical viewport, at 3 pixels per logical pixel. Light and dark themes, 1x and 2x text, and empty/recent-session states are captured. They demonstrate layout, not real-device performance.

| Finding | Repair | Evidence |
| --- | --- | --- |
| WORKSPACE-01 | Project, session and review-status text share a 60dp rail: 16dp page edge + 32dp leading slot + 12dp gap. | Before/after recent states; capture geometry assertions. |
| WORKSPACE-02 | One bottom New session action in empty Workspace; native button semantics without a redundant outer button node. | Empty captures; a narrow 2x-text test opens the created session once. |
| WORKSPACE-03 | Session titles can use two lines before truncation. | Recent 2x captures; test opens the selected existing session without creating another. |
| WORKSPACE-04 | Filled action begins at the 16dp page edge, with a 6dp bottom gap. The shell owner supplies the other 6dp gap above navigation. | Capture edge assertion; final shared shell alignment requires integration evidence. |
| WORKSPACE-05 | Session metadata uses supporting typography while blocker emphasis remains visible. | Recent captures and existing blocker grouping tests. |
| WORKSPACE-06 | Runtime scroll entrance behavior remains a separate open motion finding. | No frame-time or scroll performance claim from fixture captures. |

Verification: pinned Shorebird Flutter 3.47.2 passed 63 tests across `workspace_hierarchy_test.dart`, `return_brief_widget_test.dart`, `projects_screen_test.dart`, `session_needs_you_test.dart` and `tool/capture/quiet_workspace_test.dart`, with `--no-pub --concurrency=1`. All eight after captures were visually inspected. Changed Dart files were formatted and `git diff --check` passed. The [focused test log](focused-tests.txt) records the run. WORKSPACE-01 through WORKSPACE-05 are tested; WORKSPACE-06 remains deferred. The coordinator owns final integration analyzer, full-suite and device evidence. Detailed stable findings are maintained in the external audit packet's `pages/workspace.md` and `pages/workspace.json`.

These captures retain the checkpoint's shared shell, typography and icons. Final page/dock edge agreement and the coordinator's shared token changes require integrated captures; this page-only evidence does not imply those later changes have been verified.
