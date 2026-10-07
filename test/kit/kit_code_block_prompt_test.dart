// A command block draws its `$` prompt only where a command starts, and on
// a touch screen no scroll thumb sits on the code at rest (owner report,
// 2026-10-07: a heredoc script in a Claude Code chat had `$` on every line
// and the sideways indicator over the text).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';

List<bool> _starts(String text) => kitCommandLineStarts(text.split('\n'));

Future<void> _pump(WidgetTester tester, String text) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark().copyWith(platform: TargetPlatform.android),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            KitCodeBlock(
              text: text,
              kind: KitCodeKind.command,
              copyLabel: 'Copy command',
            ),
          ],
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  group('kitCommandLineStarts', () {
    test('separate commands each start a line', () {
      expect(_starts('cd app\nflutter pub get\nflutter test'), [
        true,
        true,
        true,
      ]);
    });

    test('a heredoc body and its terminator continue the command', () {
      const script =
          "cat > /tmp/a.py <<'EOF'\n"
          'import os\n'
          'def f():\n'
          '    return 1\n'
          'EOF\n'
          'python3 /tmp/a.py';
      expect(_starts(script), [true, false, false, false, false, true]);
    });

    test('<<- strips tabs; <<< is a here-string, not a heredoc', () {
      expect(_starts('cat <<-END\n\tbody\n\tEND\necho done'), [
        true,
        false,
        false,
        true,
      ]);
      expect(_starts('read -r A B <<< "x y"\necho \$A'), [true, true]);
    });

    test('a trailing backslash, pipe or && continues the command', () {
      expect(_starts('docker run \\\n  --rm image\nls'), [true, false, true]);
      expect(_starts('cat log |\n  grep error\nls'), [true, false, true]);
      expect(_starts('make &&\n  make install\nls'), [true, false, true]);
    });

    test('an open quote or bracket continues the command', () {
      expect(_starts('echo "one\ntwo"\nls'), [true, false, true]);
      expect(_starts('python3 -c \'\nprint(1)\n\'\nls'), [
        true,
        false,
        false,
        true,
      ]);
      expect(_starts('x=\$(\n  date\n)\nls'), [true, false, false, true]);
    });

    test('the body of a for or if block continues it', () {
      expect(_starts('for f in *.txt; do\n  wc -l "\$f"\ndone\nls'), [
        true,
        false,
        false,
        true,
      ]);
      expect(_starts('if [ -f a ]; then\n  rm a\nfi\nls'), [
        true,
        false,
        false,
        true,
      ]);
    });

    test('a # comment or a quoted < does not open anything', () {
      expect(_starts('echo "a <<EOF"\nls # (\nls'), [true, true, true]);
    });
  });

  testWidgets('a heredoc command shows one prompt per command', (tester) async {
    await _pump(
      tester,
      "cat > /tmp/a.py <<'EOF'\nimport os\nprint(os.getcwd())\nEOF\n"
      'python3 /tmp/a.py',
    );
    expect(find.text(r'$'), findsNWidgets(2));
  });

  testWidgets('on touch, no scroll thumb sits on an overflowing command at '
      'rest', (tester) async {
    await _pump(
      tester,
      'echo ${'very-long-argument ' * 20}\nls -la /a/rather/long/path',
    );
    final scrollbar = tester.widget<Scrollbar>(
      find.descendant(
        of: find.byKey(const ValueKey('kit-code-horizontal')),
        matching: find.byType(Scrollbar),
      ),
    );
    expect(scrollbar.thumbVisibility, isFalse);
  });
}
