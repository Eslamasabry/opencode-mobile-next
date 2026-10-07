import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/release/release_metadata.dart';

void main() {
  const version = '1.2.0+52';
  const pubspec = 'name: opencode_mobile\nversion: $version\n';
  const changelog = '# Changelog\n\n## $version — 2026-10-07\n\nNotes.\n';
  const releaseNotes = '# OpenCode Mobile $version\n\nRelease notes.\n';

  List<String> validate({
    String manifest = pubspec,
    String changes = changelog,
    String? notes = releaseNotes,
  }) => validateReleaseMetadata(
    pubspec: manifest,
    changelog: changes,
    releaseNotes: notes,
  );

  test('current pubspec, top changelog entry and release header agree', () {
    final manifest = File('pubspec.yaml').readAsStringSync();
    final currentVersion = releaseVersionFromPubspec(manifest);
    final notes = File('docs/releases/v$currentVersion.md');
    final issues = validateReleaseMetadata(
      pubspec: manifest,
      changelog: File('CHANGELOG.md').readAsStringSync(),
      releaseNotes: notes.existsSync() ? notes.readAsStringSync() : null,
    );
    expect(issues, isEmpty, reason: issues.join('\n'));
  });

  group('release metadata discrepancies', () {
    test('accepts matching metadata and old releases below the first', () {
      expect(
        validate(changes: '$changelog\n## 1.1.0+51 — 2026-09-01\n'),
        isEmpty,
      );
    });

    test('reports a build-number-only mismatch in the changelog', () {
      expect(
        validate(changes: changelog.replaceFirst(version, '1.2.0+51')),
        contains(contains('CHANGELOG.md first release heading')),
      );
    });

    test('requires the first entry rather than a matching older entry', () {
      expect(
        validate(changes: '## 1.1.0+51\n\n$changelog'),
        contains(contains('CHANGELOG.md first release heading')),
      );
    });

    test('rejects a version prefix match', () {
      expect(
        validate(changes: changelog.replaceFirst(version, '1.2.0+521')),
        isNotEmpty,
      );
    });

    test('reports a missing or unversioned first changelog heading', () {
      for (final changes in ['# Changelog\n', '## Unreleased\n$changelog']) {
        expect(validate(changes: changes), isNotEmpty);
      }
    });

    test('reports the expected path when release notes are missing', () {
      expect(
        validate(notes: null),
        contains(contains('docs/releases/v$version.md')),
      );
    });

    test('reports a build-number-only mismatch in the release header', () {
      expect(
        validate(notes: releaseNotes.replaceFirst(version, '1.2.0+51')),
        contains(contains('must start with # OpenCode Mobile $version')),
      );
    });

    test('requires the exact first-line release header', () {
      for (final notes in [
        '',
        '\n$releaseNotes',
        'Introduction\n$releaseNotes',
        '# OpenCode Mobile $version - Preview\n',
        '# OpenCode Mobile ${version}0\n',
      ]) {
        expect(validate(notes: notes), isNotEmpty);
      }
    });

    test('reports malformed or missing pubspec release identity', () {
      for (final manifest in [
        'name: app\n',
        'version: 1.2.0\n',
        'version: 1.2.0+invalid\n',
        'version: 1.2.0+52suffix\n',
      ]) {
        expect(
          validate(manifest: manifest),
          contains('pubspec.yaml must contain version: X.Y.Z+BUILD.'),
        );
      }
    });

    test('supports CRLF metadata and a trailing pubspec comment', () {
      expect(
        validate(
          manifest: 'version: $version # current release\r\n',
          changes: changelog.replaceAll('\n', '\r\n'),
          notes: releaseNotes.replaceAll('\n', '\r\n'),
        ),
        isEmpty,
      );
    });
  });
}
