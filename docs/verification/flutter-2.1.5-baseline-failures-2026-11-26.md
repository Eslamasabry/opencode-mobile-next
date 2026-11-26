# 2.1.5 test baseline on the phone-hosted container (2026-11-26)

## Environment

- Flutter 3.47.1, Shorebird fork revision `91f8bd75076e9c740aa13cf67eb9ec1a093f68f5`.
- Working branch: `flutter-2.1.5` at `b7bad32c3` (update notices removed).
- Baseline: tag `v2.1.5+63` at `c7ebb72e1`, checked out in a separate worktree.

## Commands

```sh
flutter analyze
flutter test --concurrency=1
flutter test --concurrency=1 \
  test/agent_removal_scripts_test.dart \
  test/chat_project_chip_test.dart
```

The last command was run twice: once on the working branch and once on the
untouched baseline worktree. Both runs used the same pinned Flutter and the
same package resolution.

## Results

- `flutter analyze`: `No issues found!` on the working branch.
- Full suite on the working branch: `+1624 ~13 -3`. The three failures below
  also reproduced when the two affected files were run in isolation.
- Finalization of the full run reported
  `PathNotFoundException: Deletion failed, path = '/tmp/flutter_tools.IUPRDS/...'`;
  this is Flutter's temporary listener cleanup, not a test assertion.

| Test | Failure | Working branch | Baseline `v2.1.5+63` |
| --- | --- | --- | --- |
| `agent_removal_scripts_test.dart: allocated bytes deduplicate payload hardlinks and exclude retained inode` | `Expected: <0>`, `Actual: <18>` | fails | fails identically |
| `chat_project_chip_test.dart: two servers: ...` | golden `chat_one_bar_two_servers_dark.png`, 0.03%, 92px | fails | fails identically |
| `chat_project_chip_test.dart: at text size 2.0 ...` | golden `chat_one_bar_title_text2_dark.png`, 0.04%, 107px | fails | fails identically |

## Conclusion

All three failures predate the update-notice removal and are caused by this
container's filesystem/renderer, not by the branch changes. The touched files
are limited to the update notices, their tests, the `main.dart` import, the
Widgetbook lockfile and `tool/qa/preflight.sh`; none of the failing tests or
goldens were modified. Do not regenerate the goldens on this host: that would
encode container-specific pixels into the repository.
