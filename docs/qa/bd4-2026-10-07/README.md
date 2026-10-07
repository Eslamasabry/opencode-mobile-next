# BD4 — Release metadata gate

Date: 2026-10-07

Finish line: the full `pubspec.yaml` release version, including build number,
matches the first `CHANGELOG.md` entry and the exact first-line header in
`docs/releases/v<version>.md`; this check runs in the PR quality gate.
Non-goal: bumping the app version or rewriting existing release notes.

## Implementation

`test/release_metadata_test.dart` reads the three real repository files.
`tool/release/release_metadata.dart` validates the release identity and returns
specific discrepancies naming the file and expected version/header. Existing
metadata agrees at **1.2.0+52**, so no version or documentation bump was needed.

The focused file also covers missing notes, absent/malformed versions,
build-number-only discrepancies, version-prefix mistakes, a matching older
changelog entry underneath an incorrect first entry, missing/unversioned
changelog headings, incorrect first-line release headers, and CRLF input.

The coordinator owns `.github/workflows/android-quality.yml`; it should run
this file as a named checks step. The recursive PR suite also discovers it.

## Regression proof

Temporarily disabled only the release-note header comparison in the validator
(`if (firstLine != '# OpenCode Mobile $version')` changed to `if (false)`).
Ran the focused regression:

```sh
tool/qa/machine_lock.sh test -- \
  /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --concurrency=1 test/release_metadata_test.dart \
  --plain-name 'reports a build-number-only mismatch in the release header'
```

**Expected failure, exit 1:** a fixture with header `1.2.0+51` produced `[]`
instead of the required discrepancy for `1.2.0+52`. Restored the comparison
before running the complete affected test file.

## Validation

```sh
/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart \
  format --language-version=3.10 \
  test/release_metadata_test.dart tool/release/release_metadata.dart

tool/qa/machine_lock.sh test -- \
  /home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter \
  test --no-pub --concurrency=1 test/release_metadata_test.dart
```

**Passed: 11 tests, exit 0.** No full suite, APK build, device session, signing,
publication, or CI run was performed for this script/test change. Analyzer and
workflow validation belong to the coordinator's integration checkpoint.
