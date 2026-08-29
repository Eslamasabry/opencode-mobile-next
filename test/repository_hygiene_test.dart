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
    expect(text, contains('3.47.1'));
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
}
