// The kit variants the Files page and its reader use (vision 2026-10-20):
// a row whose change shows on its own name, the reader layout of KitViewer
// and the code block's hanging indent (KitPageHeading has its own test). Each is opt-in; the defaults keep every other screen's look.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_viewer.dart';

import '../support/part_semantics.dart';
import 'kit_harness.dart';

const _code =
    'function move(x) {\n  paddle.x = Math.max(50, Math.min(width - 50, x));\n}\n';

Future<void> _host(WidgetTester tester, Widget child) async {
  final context = await pumpKitHost(tester);
  unawaited(
    Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => Scaffold(body: child))),
  );
  await tester.pumpAndSettle();
}

Future<void> _reader(WidgetTester tester, {KitAction? primary}) async {
  final context = await pumpKitHost(tester);
  unawaited(
    showKitViewer(
      context,
      name: 'game.js',
      path: 'src/game.js',
      source: const KitViewerSource(
        KitViewerContent.code(_code, language: 'js'),
      ),
      primary: primary,
      reader: true,
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a change mark follows the name, which stays plain text, and '
      'says the state in words', (tester) async {
    await _host(
      tester,
      Builder(
        builder: (context) => KitRow(
          key: const ValueKey('row'),
          leading: KitRow.glyph(context, AppIconography.file, accent: true),
          title: 'index.html',
          titleAccent: true,
          titleMark: KitRow.changeMark(context, semanticsLabel: 'Modified'),
          onTap: () {},
        ),
      ),
    );
    expect(find.text('index.html'), findsOneWidget);
    final label = partSemantics(
      tester,
      find.byKey(const ValueKey('row')),
    ).getSemanticsData().label;
    expect(label, contains('index.html'));
    expect(label, contains('Modified'));
    // The mark sits after the words on the same line.
    expect(
      tester
          .getCenter(
            find.byWidgetPredicate(
              (widget) =>
                  widget is Semantics && widget.properties.label == 'Modified',
            ),
          )
          .dx,
      greaterThan(tester.getTopRight(find.text('index.html')).dx),
    );
  });

  testWidgets('the reader pins the one action at the bottom with Copy, and '
      'wraps code on a phone', (tester) async {
    var asked = 0;
    await _reader(
      tester,
      primary: KitAction(
        label: 'Ask about this file',
        icon: AppIconography.chat,
        onPressed: () => asked++,
      ),
    );
    final primary = find.byKey(const ValueKey('kit-viewer-primary'));
    final copy = find.byKey(const ValueKey('kit-viewer-copy'));
    expect(primary, findsOneWidget);
    expect(copy, findsOneWidget);
    // Below the code, beside each other, the main action first.
    final lastLine = find.byKey(const ValueKey('kit-code-line-3'));
    expect(
      tester.getTopLeft(primary).dy,
      greaterThan(tester.getTopLeft(lastLine).dy),
    );
    expect(tester.getCenter(primary).dx, lessThan(tester.getCenter(copy).dx));
    // Wrap is a quiet toggle in the header, on by default on a phone: no
    // sideways scroller.
    expect(find.byKey(const ValueKey('kit-viewer-wrap')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-code-horizontal')), findsNothing);
    await tester.tap(primary);
    expect(asked, 1);
  });

  testWidgets('without the reader layout the action stays in the header', (
    tester,
  ) async {
    final context = await pumpKitHost(tester);
    unawaited(
      showKitViewer(
        context,
        name: 'game.js',
        source: const KitViewerSource(
          KitViewerContent.code(_code, language: 'js'),
        ),
        primary: KitAction(
          label: 'Add as reference',
          icon: AppIconography.link,
          onPressed: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('kit-viewer-copy')), findsNothing);
    expect(find.byKey(const ValueKey('kit-viewer-wrap')), findsNothing);
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('kit-viewer-primary'))).dy,
      lessThan(
        tester.getTopLeft(find.byKey(const ValueKey('kit-code-line-1'))).dy,
      ),
    );
  });

  testWidgets('a hanging indent keeps the indent as real text', (tester) async {
    await _host(
      tester,
      const SizedBox(
        width: 260,
        height: 400,
        child: KitCodeBlock.fill(
          text: _code,
          language: 'js',
          wrap: true,
          hangingIndent: true,
        ),
      ),
    );
    final line = find.byKey(const ValueKey('kit-code-line-2'));
    // The indent is its own run beside the wrapping rest of the line.
    expect(
      find.descendant(of: line, matching: find.text('  ')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });
}
