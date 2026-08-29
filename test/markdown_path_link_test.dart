import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';

void main() {
  group('looksLikeFilePath', () {
    test('accepts anchored and extensioned paths', () {
      expect(looksLikeFilePath('/tmp/opencode/shots/home-desktop.png'), isTrue);
      expect(looksLikeFilePath('lib/state/connection.dart'), isTrue);
      expect(looksLikeFilePath('lib/state/connection.dart:1146'), isTrue);
      expect(looksLikeFilePath('~/notes/todo.md'), isTrue);
      expect(looksLikeFilePath('./scripts/run.sh'), isTrue);
      expect(looksLikeFilePath('/tmp/rec-stop'), isTrue);
    });

    test('rejects non-paths', () {
      expect(looksLikeFilePath('and/or'), isFalse);
      expect(looksLikeFilePath('https://example.com/a.png'), isFalse);
      expect(looksLikeFilePath('flutter test'), isFalse);
      expect(looksLikeFilePath('a/b c/d.txt'), isFalse);
      expect(looksLikeFilePath('foo.dart'), isFalse);
      expect(looksLikeFilePath('x/y'), isFalse);
    });
  });

  test('stripPathLineSuffix trims only trailing line numbers', () {
    expect(stripPathLineSuffix('lib/a.dart:120'), 'lib/a.dart');
    expect(stripPathLineSuffix('lib/a.dart'), 'lib/a.dart');
    expect(stripPathLineSuffix('/tmp/c:120/x.png'), '/tmp/c:120/x.png');
  });

  Widget host({
    required String data,
    required Future<bool> Function(String) validate,
    required void Function(String) open,
  }) {
    return MaterialApp(
      home: Scaffold(
        body: MarkdownFileLinks(
          validate: validate,
          open: open,
          child: MarkdownText(data),
        ),
      ),
    );
  }

  testWidgets('validated path becomes tappable and reports the raw span', (
    tester,
  ) async {
    final validated = <String>[];
    final opened = <String>[];
    await tester.pumpWidget(
      host(
        data: 'See `lib/a/b.dart:12` for details.',
        validate: (path) async {
          validated.add(path);
          return true;
        },
        open: opened.add,
      ),
    );
    await tester.pumpAndSettle();

    expect(validated, ['lib/a/b.dart']);
    final link = find.byKey(const Key('path-link-lib/a/b.dart:12'));
    expect(link, findsOneWidget);
    await tester.tap(link);
    expect(opened, ['lib/a/b.dart:12']);
  });

  testWidgets('unreadable path stays a plain code chip', (tester) async {
    await tester.pumpWidget(
      host(
        data: 'Missing `/tmp/gone.txt` file.',
        validate: (_) async => false,
        open: (_) => fail('must not open unvalidated paths'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('/tmp/gone.txt'), findsOneWidget);
    expect(find.byKey(const Key('path-link-/tmp/gone.txt')), findsNothing);

    // Exhaust the bounded revalidation timers so none stay pending.
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(seconds: 23));
    }
    expect(find.byKey(const Key('path-link-/tmp/gone.txt')), findsNothing);
  });

  testWidgets('a path that appears later lights up on retry', (tester) async {
    var exists = false;
    await tester.pumpWidget(
      host(
        data: 'Saved to `/tmp/new-file.png` just now.',
        validate: (_) async => exists,
        open: (_) {},
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('path-link-/tmp/new-file.png')), findsNothing);

    exists = true;
    await tester.pump(const Duration(seconds: 23));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const Key('path-link-/tmp/new-file.png')),
      findsOneWidget,
    );
  });

  testWidgets('pending validation renders plain until it resolves', (
    tester,
  ) async {
    final gate = Completer<bool>();
    await tester.pumpWidget(
      host(
        data: 'Open `/tmp/slow.png` now.',
        validate: (_) => gate.future,
        open: (_) {},
      ),
    );
    await tester.pump();
    expect(find.byKey(const Key('path-link-/tmp/slow.png')), findsNothing);

    gate.complete(true);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('path-link-/tmp/slow.png')), findsOneWidget);
  });

  testWidgets('non-path code spans never consult the scope', (tester) async {
    final validated = <String>[];
    await tester.pumpWidget(
      host(
        data: 'Run `flutter test` and read `foo.dart`.',
        validate: (path) async {
          validated.add(path);
          return true;
        },
        open: (_) {},
      ),
    );
    await tester.pumpAndSettle();
    expect(validated, isEmpty);
  });

  testWidgets('without a scope, path-like spans render as plain chips', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: MarkdownText('See `lib/a/b.dart` here.')),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('lib/a/b.dart'), findsOneWidget);
    expect(find.byKey(const Key('path-link-lib/a/b.dart')), findsNothing);
  });
}
