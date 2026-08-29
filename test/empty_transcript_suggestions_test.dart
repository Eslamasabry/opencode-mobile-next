import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

void main() {
  test('a selected project seeds the first suggestion chip', () {
    expect(emptyTranscriptSuggestions(directory: '/work/oc_app'), [
      'Explain the oc_app project',
      'What changed recently?',
      'Find and fix a bug',
    ]);
  });

  test('trailing slashes and Windows separators still yield the name', () {
    expect(
      emptyTranscriptSuggestions(directory: '/work/oc_app/').first,
      'Explain the oc_app project',
    );
    expect(
      emptyTranscriptSuggestions(directory: r'C:\work\oc_app').first,
      'Explain the oc_app project',
    );
  });

  test('no project replaces the git-dependent chips', () {
    // A fresh server with zero projects serves its own default directory,
    // which usually has no git repository — "what changed recently" and
    // "explain this project" would be dead ends there.
    for (final directory in [null, '', '   ']) {
      expect(emptyTranscriptSuggestions(directory: directory), [
        "List what's in this directory",
        'Find and fix a bug',
      ]);
    }
  });
}
