import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Guards the public boundary of the repository itself: what a first-time
/// reader meets at the root, and what third-party material the tree carries.
void main() {
  test('the internal engineering log is out of the public tree', () {
    // The append-only working log carried machine paths, device names, and
    // release claims that were stale the week they were written. It is gone
    // from the tree entirely; git history keeps it for archaeology.
    for (final path in const [
      'HANDOFF.md',
      'docs/internal/handoff.md',
    ]) {
      expect(
        File(path).existsSync(),
        isFalse,
        reason: '$path is internal scratch, not a document a public reader '
            'should be handed',
      );
    }
  });

  test('the root points contributors at CONTRIBUTING, not the log', () {
    final contributing = File('CONTRIBUTING.md');
    expect(contributing.existsSync(), isTrue);
    final text = contributing.readAsStringSync();
    // The facts a contributor cannot guess and cannot work without.
    expect(text, contains('3.47.2'));
    expect(text, contains('flutter analyze'));
    expect(text, contains('--concurrency=1'));
    expect(text, contains('flutter_secure_storage'));

    final readme = File('README.md').readAsStringSync();
    expect(readme, contains('CONTRIBUTING.md'));
    expect(
      readme,
      isNot(contains('handoff.md')),
      reason: 'the docs index still links the removed engineering log',
    );
    expect(
      readme,
      isNot(contains('[HANDOFF.md](HANDOFF.md)')),
      reason: 'the docs index still links the removed root log',
    );

    // The facts the log used to be the only home for now live where a
    // contributor will actually look for them.
    expect(text, contains('flutter_animate'));
    expect(text, contains('validateSigningRelease'));
  });

  test('third-party agent skill packs are not carried in the tree', () {
    final ignore = File('.gitignore').readAsStringSync();
    expect(
      ignore.split('\n').map((line) => line.trim()),
      contains('.claude/skills/'),
      reason: 'skill packs are third-party prompt content with their own '
          'licensing; committing them makes this repo responsible for it',
    );

    // Their provenance stays recorded so they can be reinstalled and so a
    // future pack has a bar to clear.
    final provenance = File('docs/internal/developer-skills.md');
    expect(provenance.existsSync(), isTrue);
    final text = provenance.readAsStringSync();
    expect(text, contains('motion-design'));
    expect(text, contains('remotion-motion-graphics'));
    expect(text, contains('SPDX'));
  });

  test('every bundled third-party component has a license text', () {
    final notices = File('THIRD_PARTY_NOTICES.md').readAsStringSync();
    final referenced = RegExp(r'LICENSES/([A-Za-z0-9._-]+\.txt)')
        .allMatches(notices)
        .map((match) => match.group(1)!)
        .toSet();
    expect(referenced, isNotEmpty);
    for (final name in referenced) {
      expect(
        File('LICENSES/$name').existsSync(),
        isTrue,
        reason: 'THIRD_PARTY_NOTICES.md references a missing license text',
      );
    }

    final present = Directory('LICENSES')
        .listSync()
        .whereType<File>()
        .map((file) => file.uri.pathSegments.last)
        .toSet();
    expect(
      present.difference(referenced),
      isEmpty,
      reason: 'a license text sits in LICENSES/ that no notice accounts for',
    );
  });

  test('a public repository carries its governance files', () {
    for (final path in const [
      'SECURITY.md',
      'SUPPORT.md',
      'CODE_OF_CONDUCT.md',
      'CONTRIBUTING.md',
      'LICENSE',
      '.github/CODEOWNERS',
      '.github/dependabot.yml',
      '.github/PULL_REQUEST_TEMPLATE.md',
      '.github/ISSUE_TEMPLATE/config.yml',
      '.github/ISSUE_TEMPLATE/bug_report.yml',
      '.github/ISSUE_TEMPLATE/feature_request.yml',
    ]) {
      expect(File(path).existsSync(), isTrue, reason: '$path is missing');
    }

    // The two facts that make this app's reporting rules different from a
    // typical app's, and the one supported-version rule.
    final security = File('SECURITY.md').readAsStringSync();
    expect(security, contains('security/advisories/new'));
    expect(security, contains('shell-capable'));
    expect(security, contains('current preview'));

    // Security reports must not be routed into public issues.
    final config = File('.github/ISSUE_TEMPLATE/config.yml').readAsStringSync();
    expect(config, contains('blank_issues_enabled: false'));
    expect(config, contains('security/advisories/new'));
  });

  test('the non-affiliation statement reaches every public entry point', () {
    // Upstream asks third-party projects using the OpenCode name to say so.
    // It is asserted in-app by release_blockers_test; these are the repository
    // surfaces a reader or reporter meets first.
    for (final path in const [
      'README.md',
      'SECURITY.md',
      'SUPPORT.md',
      '.github/ISSUE_TEMPLATE/bug_report.yml',
      '.github/ISSUE_TEMPLATE/feature_request.yml',
      'THIRD_PARTY_NOTICES.md',
    ]) {
      final text = File(path).readAsStringSync().replaceAll(
        RegExp(r'[>*\s]+'),
        ' ',
      );
      expect(
        text,
        contains(
          'not built, maintained, endorsed by, or affiliated with the '
          'official OpenCode team',
        ),
        reason: '$path does not state the project is unaffiliated',
      );
    }
  });

  test('the notice inventory matches the resolved dependency versions', () {
    // The notices shipped `record` 6.2.1 for a build that carried 7.1.1, and
    // reproduced the 6.2.1 text with it. Attribution that names the wrong
    // version is worse than no table, so pin the two together.
    final notices = File('THIRD_PARTY_NOTICES.md').readAsStringSync();
    final documented = <String, String>{
      for (final row in RegExp(
        r'^\| `([a-z0-9_]+)` \| ([^|]+?) \|',
        multiLine: true,
      ).allMatches(notices))
        row.group(1)!: row.group(2)!.trim(),
    };
    expect(documented, isNotEmpty);

    final lock = File('pubspec.lock').readAsStringSync().split('\n');
    final resolved = <String, String>{};
    String? current;
    var hosted = false;
    for (final line in lock) {
      final entry = RegExp(r'^  ([a-z0-9_]+):$').firstMatch(line);
      if (entry != null) {
        current = entry.group(1);
        hosted = false;
        continue;
      }
      if (current == null) continue;
      if (line == '    source: hosted') hosted = true;
      final version = RegExp(r'^    version: "(.+)"$').firstMatch(line);
      if (version != null && hosted) resolved[current] = version.group(1)!;
    }
    expect(resolved.length, greaterThan(100));

    for (final entry in resolved.entries) {
      expect(
        documented[entry.key],
        entry.value,
        reason: 'THIRD_PARTY_NOTICES.md is out of date for ${entry.key}: '
            'pubspec.lock resolves ${entry.value}, the notice says '
            '${documented[entry.key] ?? "nothing"}',
      );
    }
    expect(
      documented.keys.toSet().difference(resolved.keys.toSet()),
      isEmpty,
      reason: 'the notice inventory lists a package that is no longer '
          'resolved in pubspec.lock',
    );
  });
}
