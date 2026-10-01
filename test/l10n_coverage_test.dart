// Ratchet against new hardcoded UI strings while localization is in progress.
//
// docs/localization-todo.md: the app is English-only and ~700 literals in
// lib/ui/**, lib/voice/** and lib/main.dart bypass AppLocalizations. This test
// counts the ones a translator would need, per file, and fails when a file
// grows past its recorded baseline. Shrink the baseline as strings move into
// lib/l10n/app_en.arb; never raise it to make a PR pass.
//
// To refresh the baseline after moving strings out:
//   L10N_BASELINE_PRINT=1 flutter test test/l10n_coverage_test.dart
// and paste the printed map over `_baseline`.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _roots = ['lib/ui', 'lib/voice'];
const _files = ['lib/main.dart'];

// Text('...') / Text("...") with an optional raw prefix; whitespace and line
// breaks between the paren and the literal are allowed.
final _textLiteral = RegExp(r'''\bText\(\s*r?(['"])(.*?)\1''', dotAll: true);

// Named string parameters that always end up on screen.
final _namedLiteral = RegExp(
  r'''\b(?:hintText|labelText|helperText|tooltip|semanticsLabel|semanticLabel|errorText):\s*(?:const\s+)?r?(['"])(.*?)\1''',
  dotAll: true,
);

final _hasLetter = RegExp('[A-Za-z]');
// Identifiers, paths, model ids, shortcuts: one token containing a separator.
final _identifierLike = RegExp(r'^[^\s]*[/._+:][^\s]*$');
// Dart interpolation-only literals like '$count' or '${a.b}'.
final _interpolationOnly = RegExp(r'^\$\{?[A-Za-z_][A-Za-z0-9_.]*\}?$');
// Names inside interpolation are Dart identifiers, not rendered words. Strip
// only simple references; expressions and any surrounding prose still count.
final _interpolation = RegExp(
  r'\$(?:\{[A-Za-z_][A-Za-z0-9_.]*\}|[A-Za-z_][A-Za-z0-9_]*)',
);

bool _translatable(String literal) {
  if (!_hasLetter.hasMatch(literal.replaceAll(_interpolation, ''))) {
    return false;
  }
  if (_identifierLike.hasMatch(literal)) return false;
  if (_interpolationOnly.hasMatch(literal)) return false;
  return true;
}

String _stripComments(String source) => source
    .split('\n')
    .where((line) => !line.trimLeft().startsWith('//'))
    .join('\n');

int countHardcodedStrings(String source) {
  final code = _stripComments(source);
  var count = 0;
  for (final m in _textLiteral.allMatches(code)) {
    if (_translatable(m.group(2)!)) count++;
  }
  for (final m in _namedLiteral.allMatches(code)) {
    if (_translatable(m.group(2)!)) count++;
  }
  return count;
}

Map<String, int> scan() {
  final results = <String, int>{};
  final paths = <String>[
    for (final root in _roots)
      ...Directory(root)
          .listSync(recursive: true)
          .whereType<File>()
          .where((f) => f.path.endsWith('.dart'))
          .map((f) => f.path),
    ..._files,
  ]..sort();
  for (final path in paths) {
    final n = countHardcodedStrings(File(path).readAsStringSync());
    if (n > 0) results[path.replaceAll('\\', '/')] = n;
  }
  return results;
}

// Recorded 2026-09-03. Only decrease these numbers.
const _baseline = <String, int>{};

void main() {
  test('hardcoded string detector', () {
    expect(countHardcodedStrings("Text('Send')"), 1);
    expect(countHardcodedStrings('Text("Send now")'), 1);
    expect(countHardcodedStrings("Text(\n  'Send',\n)"), 1);
    expect(countHardcodedStrings("hintText: 'Ask anything'"), 1);
    expect(countHardcodedStrings("tooltip: const 'Attach'"), 1);
    expect(countHardcodedStrings("Text('·')"), 0, reason: 'no letters');
    expect(countHardcodedStrings("Text('Ctrl+Enter')"), 0, reason: 'shortcut');
    expect(countHardcodedStrings("Text('anthropic/claude')"), 0, reason: 'id');
    expect(countHardcodedStrings(r"Text('$count')"), 0, reason: 'interp');
    expect(countHardcodedStrings(r"Text('$count files')"), 1);
    expect(
      countHardcodedStrings(
        r"Text('+${file.counts.added}  −${file.counts.removed}')",
      ),
      0,
      reason: 'numeric diff counts contain no translatable words',
    );
    expect(countHardcodedStrings(r"Text('${file.counts.added} added')"), 1);
    expect(countHardcodedStrings("// Text('comment')"), 0);
    expect(countHardcodedStrings('Text(l10n.send)'), 0);
  });

  test('no file gained hardcoded UI strings', () {
    final current = scan();

    if (Platform.environment['L10N_BASELINE_PRINT'] == '1') {
      final buffer = StringBuffer();
      for (final entry in current.entries) {
        buffer.writeln("  '${entry.key}': ${entry.value},");
      }
      // ignore: avoid_print
      print(buffer);
    }

    final regressions = <String>[];
    final improvements = <String>[];
    for (final entry in current.entries) {
      final allowed = _baseline[entry.key] ?? 0;
      if (entry.value > allowed) {
        regressions.add(
          '${entry.key}: ${entry.value} hardcoded strings, baseline $allowed',
        );
      } else if (entry.value < allowed) {
        improvements.add('${entry.key}: ${entry.value} (baseline $allowed)');
      }
    }
    for (final path in _baseline.keys) {
      if (!current.containsKey(path)) {
        improvements.add('$path: 0 (baseline ${_baseline[path]})');
      }
    }

    if (improvements.isNotEmpty) {
      // ignore: avoid_print
      print(
        'l10n: these files improved; lower their baseline in '
        'test/l10n_coverage_test.dart:\n  ${improvements.join('\n  ')}',
      );
    }

    expect(
      regressions,
      isEmpty,
      reason:
          'New user-visible literals must go through AppLocalizations '
          '(lib/l10n/app_en.arb). See docs/localization-todo.md.\n'
          '${regressions.join('\n')}',
    );
  });
}
