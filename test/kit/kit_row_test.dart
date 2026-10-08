// Behaviour tests for KitRow v2 and KitSwipeAction
// (docs/ux-system/kit-api/KitRow.md, KitSwipeAction.md "Tests required").
// Arabic/RTL cases are dropped by the owner decision of 2026-09-27.
import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitConfirmSheet;
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_swipe_action.dart';
import 'package:opencode_mobile/ui/kit/kit_tappable.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  bool light = false,
  double textScale = 1,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: light ? AppTheme.light() : AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [child],
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void _desktop() {
  debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
  addTearDown(() => debugPlatformCapabilities = null);
}

KitTokens _tokens(WidgetTester tester) =>
    KitTokens.of(tester.element(find.byType(Scaffold)));

Color? _titleColor(WidgetTester tester, String title) =>
    tester.widget<Text>(find.text(title)).style?.color;

/// The labels of the custom semantic actions on the row's node.
List<String> _customActionLabels(WidgetTester tester, Finder row) {
  final data = tester.getSemantics(row).getSemanticsData();
  return [
    for (final id in data.customSemanticsActionIds ?? const <int>[])
      ?CustomSemanticsAction.getAction(id)!.label,
  ];
}

void _performCustomAction(WidgetTester tester, Finder row, String label) {
  final node = tester.getSemantics(row);
  final id = node.getSemanticsData().customSemanticsActionIds!.firstWhere(
    (id) => CustomSemanticsAction.getAction(id)!.label == label,
  );
  node.owner!.performAction(node.id, SemanticsAction.customAction, id);
}

Finder _richTextContaining(String text) => find.byWidgetPredicate(
  (w) => w is RichText && w.text.toPlainText().contains(text),
);

void main() {
  group('KitRow', () {
    testWidgets('1: tap, Enter and Space call onTap; disabled ignores all', (
      tester,
    ) async {
      _desktop();
      var taps = 0;
      await _pump(tester, KitRow(title: 'Open', onTap: () => taps++));
      await tester.tap(find.text('Open'));
      expect(taps, 1);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      expect(taps, 3);

      var disabledTaps = 0;
      await _pump(
        tester,
        KitRow(
          title: 'Open',
          onTap: () => disabledTaps++,
          enabled: false,
          disabledReason: 'Needs a connection',
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      expect(disabledTaps, 0);
    });

    testWidgets('2: disabled reason replaces supporting, is the hint, text3, '
        'no Opacity', (tester) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitRow(
          title: 'Run tests',
          supporting: const TextSpan(text: 'Hidden line'),
          onTap: () {},
          enabled: false,
          disabledReason: 'Needs a connection',
        ),
      );
      expect(find.text('Needs a connection'), findsOneWidget);
      expect(find.text('Hidden line'), findsNothing);
      expect(
        find.descendant(
          of: find.byType(KitRow),
          matching: find.byType(Opacity),
        ),
        findsNothing,
      );
      expect(_titleColor(tester, 'Run tests'), _tokens(tester).roles.text3);
      expect(
        tester.getSemantics(find.byType(KitRow)),
        matchesSemantics(
          label: 'Run tests\nNeeds a connection',
          hint: 'Needs a connection',
          isButton: true,
          hasEnabledState: true,
        ),
      );
      handle.dispose();
    });

    testWidgets('3: unavailable with enable: reason, 48 dp tertiary, row tap '
        'runs it', (tester) async {
      var runs = 0;
      await _pump(
        tester,
        KitRow.unavailable(
          title: 'Voice input',
          reason: 'Voice needs a model on this phone.',
          capability: 'voice.model',
          enable: KitAction(
            label: 'Download voice model',
            onPressed: () => runs++,
          ),
        ),
      );
      expect(find.text('Voice needs a model on this phone.'), findsOneWidget);
      final button = tester.widget<KitButton>(find.byType(KitButton));
      expect(button.role, KitButtonRole.tertiary);
      expect(
        tester.getSize(find.byType(KitButton)).height,
        greaterThanOrEqualTo(48),
      );
      await tester.tap(find.text('Download voice model'));
      expect(runs, 1);
      await tester.tap(find.text('Voice input'));
      expect(runs, 2);
      expect(
        tester.widget<KitRow>(find.byType(KitRow)).capability,
        'voice.model',
      );
      expect(_titleColor(tester, 'Voice input'), _tokens(tester).roles.text3);
    });

    testWidgets('3: unavailable at 2.0 text: the reason wraps in full and '
        'the enable action moves under it', (tester) async {
      const reason =
          'Needs a voice model on this phone. It downloads once and then '
          'works without a connection.';
      await _pump(
        tester,
        KitRow.unavailable(
          title: 'Voice typing',
          reason: reason,
          enable: KitAction(label: 'Download voice model', onPressed: () {}),
        ),
        textScale: 2,
      );
      final text = find.text(reason);
      final paragraph = tester.renderObject<RenderParagraph>(text);
      expect(paragraph.didExceedMaxLines, isFalse);
      expect(paragraph.maxLines, isNull);
      final button = find.byType(KitButton);
      // Under the reason, starting on its line: not squeezed beside it.
      expect(
        tester.getTopLeft(button).dy,
        greaterThanOrEqualTo(tester.getBottomLeft(text).dy),
      );
      expect(
        tester.getSize(text).width,
        greaterThan(tester.view.physicalSize.width / 2),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('3: unavailable at 1.0 text keeps enable trailing', (
      tester,
    ) async {
      await _pump(
        tester,
        KitRow.unavailable(
          title: 'Voice typing',
          reason: 'Needs a voice model.',
          enable: KitAction(label: 'Download', onPressed: () {}),
        ),
      );
      final text = find.text('Needs a voice model.');
      final button = find.byType(KitButton);
      expect(
        tester.getTopLeft(button).dx,
        greaterThanOrEqualTo(tester.getTopRight(text).dx),
      );
      expect(
        tester.getTopLeft(button).dy,
        lessThan(tester.getBottomLeft(text).dy),
      );
    });

    testWidgets("3: an enabled row's own action is a trailing tertiary at "
        '1.0 text and moves under the text from 1.3x; the row still opens', (
      tester,
    ) async {
      var opened = 0;
      var acted = 0;
      Widget row() => KitRow(
        title: 'AI Team',
        supporting: const TextSpan(text: 'Found on Laptop'),
        onTap: () => opened++,
        action: KitAction(
          key: const ValueKey('turn-on'),
          label: 'Turn on',
          onPressed: () => acted++,
        ),
      );
      await _pump(tester, row());
      final text = find.text('Found on Laptop');
      final button = find.byKey(const ValueKey('turn-on'));
      expect(
        tester.getTopLeft(button).dx,
        greaterThanOrEqualTo(tester.getTopRight(text).dx),
      );
      await tester.tap(button);
      await tester.pumpAndSettle();
      expect((acted, opened), (1, 0));
      await tester.tap(find.text('AI Team'));
      await tester.pumpAndSettle();
      expect((acted, opened), (1, 1));

      await _pump(tester, row(), textScale: 2);
      expect(
        tester.getTopLeft(button).dy,
        greaterThanOrEqualTo(tester.getBottomLeft(text).dy),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('3: unavailable without enable has no button and no action', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        const KitRow.unavailable(
          title: 'Sub-agents',
          reason: 'Available on OpenCode 2 servers.',
        ),
      );
      expect(find.byType(KitButton), findsNothing);
      expect(find.byType(KitTappable), findsNothing);
      final data = tester.getSemantics(find.byType(KitRow)).getSemanticsData();
      expect(data.hasAction(SemanticsAction.tap), isFalse);
      expect(data.hint, 'Available on OpenCode 2 servers.');
      handle.dispose();
    });

    testWidgets('4: long-press, right-click and Shift+F10 open the menu; '
        'items are custom actions', (tester) async {
      final handle = tester.ensureSemantics();
      _desktop();
      final picked = <String>[];
      final menu = [
        KitMenuItem(label: 'Delete', destructive: true, onSelected: () {}),
        KitMenuItem(label: 'Rename', onSelected: () => picked.add('Rename')),
      ];
      await _pump(tester, KitRow(title: 'Fix login', onTap: () {}, menu: menu));
      Future<void> expectOpenThenClose() async {
        await tester.pumpAndSettle();
        expect(find.text('Rename'), findsOneWidget);
        // Destructive items render last (KitMenu ordering).
        expect(
          tester.getTopLeft(find.text('Delete')).dy,
          greaterThan(tester.getTopLeft(find.text('Rename')).dy),
        );
        await tester.tap(find.text('Rename'));
        await tester.pumpAndSettle();
      }

      await tester.longPress(find.text('Fix login'));
      await expectOpenThenClose();
      await tester.tap(find.text('Fix login'), buttons: kSecondaryButton);
      await expectOpenThenClose();
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.f10);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await expectOpenThenClose();
      expect(picked, ['Rename', 'Rename', 'Rename']);

      expect(
        _customActionLabels(tester, find.byType(KitRow)),
        containsAll(['Rename', 'Delete']),
      );
      _performCustomAction(tester, find.byType(KitRow), 'Rename');
      expect(picked.length, 4);
      handle.dispose();
    });

    testWidgets('5: onLongPress with a menu asserts; alone it still fires', (
      tester,
    ) async {
      await _pump(
        tester,
        KitRow(
          title: 'Both',
          onLongPress: () {},
          menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
        ),
      );
      expect(tester.takeException(), isAssertionError);

      var presses = 0;
      await _pump(
        tester,
        KitRow(title: 'Old', onTap: () {}, onLongPress: () => presses++),
      );
      await tester.longPress(find.text('Old'));
      expect(presses, 1);
      await _pump(tester, KitRow(title: 'Only', onLongPress: () => presses++));
      await tester.longPress(find.text('Only'));
      expect(presses, 2);
    });

    testWidgets('7: server label leads the supporting line, isolated', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitRow(
          title: 'Fix login',
          server: 'laptop',
          supporting: TextSpan(text: 'Finished 5h ago'),
        ),
      );
      expect(
        _richTextContaining('${KitBidi.auto('laptop')} · Finished 5h ago'),
        findsOneWidget,
      );
      expect(KitBidi.auto('laptop'), '${KitBidi.fsi}laptop${KitBidi.pdi}');
    });

    testWidgets('8: selected exposes selected semantics and paints surface3', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitRow(title: 'Chosen', selected: true, onTap: () {}),
      );
      expect(
        tester.getSemantics(find.byType(KitTappable)),
        matchesSemantics(
          label: 'Chosen',
          isButton: true,
          hasSelectedState: true,
          isSelected: true,
          hasEnabledState: true,
          isEnabled: true,
          hasTapAction: true,
          hasFocusAction: true,
          isFocusable: true,
        ),
      );
      final fill = tester.widget<ColoredBox>(
        find
            .descendant(
              of: find.byType(KitRow),
              matching: find.byType(ColoredBox),
            )
            .first,
      );
      expect(fill.color, _tokens(tester).roles.surface3);
      handle.dispose();
    });

    testWidgets('9: KitRowGroup puts destructive rows last behind a full '
        'divider', (tester) async {
      await _pump(
        tester,
        KitRowGroup(
          label: 'Actions',
          children: [
            KitRow(title: 'Delete', destructive: true, onTap: () {}),
            KitRow(title: 'Rename', onTap: () {}),
            KitRow(title: 'Share', onTap: () {}),
          ],
        ),
      );
      final dy = {
        for (final t in ['Delete', 'Rename', 'Share'])
          t: tester.getTopLeft(find.text(t)).dy,
      };
      expect(dy['Rename']! < dy['Share']!, isTrue);
      expect(dy['Share']! < dy['Delete']!, isTrue);
      final divider = find.byKey(
        const ValueKey('kit-row-group-destructive-divider'),
      );
      expect(divider, findsOneWidget);
      expect(
        tester.getSize(divider).width,
        tester.getSize(find.byType(Material).last).width,
      );
      expect(_titleColor(tester, 'Delete'), _tokens(tester).roles.danger);
    });

    testWidgets('10: hover paints the next surface step; focus paints the '
        'ring', (tester) async {
      _desktop();
      await _pump(
        tester,
        KitRowGroup(
          children: [KitRow(title: 'Hover me', onTap: () {})],
        ),
      );
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      addTearDown(mouse.removePointer);
      await mouse.moveTo(tester.getCenter(find.text('Hover me')));
      await tester.pumpAndSettle();
      final decoration =
          tester
                  .widget<AnimatedContainer>(
                    find.descendant(
                      of: find.byType(KitTappable),
                      matching: find.byType(AnimatedContainer),
                    ),
                  )
                  .decoration!
              as ShapeDecoration;
      expect(decoration.color, _tokens(tester).roles.surface2);
      await mouse.moveTo(Offset.zero);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byType(KitTappable),
          matching: find.byType(CustomPaint),
        ),
        findsWidgets,
      );
    });

    testWidgets('11: 48 dp target and no overflow at 2.0 text and 320 dp', (
      tester,
    ) async {
      await _pump(tester, KitRow(title: 'Short', onTap: () {}));
      expect(
        tester.getSize(find.byType(KitRow)).height,
        greaterThanOrEqualTo(48),
      );
      await _pump(
        tester,
        KitRowGroup(
          children: [
            Builder(
              builder: (context) => KitRow(
                title:
                    'A very long conversation title about fixing the login '
                    'flow on every server',
                leading: KitRow.icon(context, AppIconography.chat),
                server: 'my-laptop-at-home',
                supporting: const TextSpan(text: 'Finished 5h ago'),
                trailing: const KitRowValue('Claude Sonnet 4'),
                onTap: () {},
              ),
            ),
          ],
        ),
        textScale: 2,
        size: const Size(320, 800),
      );
      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(KitRow)).height,
        greaterThanOrEqualTo(48),
      );
    });
  });

  // Owner polish 2026-09-28: at 2.0 text Review broke file names mid-word
  // ("checkout_page.da" / "rt"). A file-name title wraps only between its
  // words: after `_` or `-`, or before the extension's dot.
  group('KitRow supporting line at large text (polish2)', () {
    const supportingKey = ValueKey('row-supporting');
    Widget row({int supportingMaxLines = 1}) => KitRow(
      leading: const Icon(Icons.terminal),
      title: 'Speed up the CI pipeline',
      supporting: const TextSpan(text: 'Editing workflow files · 4 min'),
      supportingKey: supportingKey,
      supportingMaxLines: supportingMaxLines,
      trailing: const Icon(Icons.chevron_right),
      onTap: () {},
    );
    RenderParagraph supporting(WidgetTester tester) =>
        tester.renderObject<RenderParagraph>(find.byKey(supportingKey));

    for (final (scale, lines) in [(1.0, 1), (1.3, 2), (2.0, 3), (3.0, 3)]) {
      testWidgets('at ${scale}x text the supporting line may take $lines '
          'line(s) before its ellipsis', (tester) async {
        await _pump(tester, row(), textScale: scale);
        expect(supporting(tester).maxLines, lines);
      });
    }

    testWidgets('at 2.0 text the line wraps whole instead of ending '
        '"Editing workflow fil…"', (tester) async {
      // The test font's glyphs are one em wide, so this width gives the
      // line the two to three lines a phone's real font needs at 2.0.
      await _pump(tester, row(), textScale: 2, size: const Size(700, 915));
      final paragraph = supporting(tester);
      expect(paragraph.didExceedMaxLines, isFalse);
      expect(
        paragraph
            .getBoxesForSelection(
              const TextSelection(baseOffset: 0, extentOffset: 30),
            )
            .map((b) => b.top)
            .toSet()
            .length,
        greaterThan(1),
        reason: 'wrapped onto a second line',
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('a caller that asks for more keeps its own count', (
      tester,
    ) async {
      await _pump(tester, row(supportingMaxLines: 4), textScale: 2);
      expect(supporting(tester).maxLines, 4);
      await _pump(tester, row(supportingMaxLines: 2));
      expect(supporting(tester).maxLines, 2);
    });
  });

  group('KitRow file-name title', () {
    const titleKey = ValueKey('file-title');

    List<String> lines(WidgetTester tester) {
      final paragraph = tester.renderObject<RenderParagraph>(
        find.descendant(
          of: find.byKey(titleKey),
          matching: find.byType(RichText),
        ),
      );
      final text = paragraph.text.toPlainText();
      // Each character's line, by the top of its box.
      final byTop = <double, StringBuffer>{};
      for (var i = 0; i < text.length; i++) {
        final boxes = paragraph.getBoxesForSelection(
          TextSelection(baseOffset: i, extentOffset: i + 1),
        );
        if (boxes.isEmpty) continue;
        byTop.putIfAbsent(boxes.first.top, StringBuffer.new).write(text[i]);
      }
      final tops = byTop.keys.toList()..sort();
      return [
        for (final top in tops) byTop[top].toString().replaceAll('\u200B', ''),
      ]..removeWhere((line) => line.isEmpty);
    }

    for (final name in [
      'checkout_page.dart',
      'settings_screen.dart',
      'theme-picker-sheet.dart',
    ]) {
      testWidgets('$name at 2.0 text breaks between words only', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        await _pump(
          tester,
          KitRow(
            leading: const Icon(Icons.description),
            title: name,
            titleKey: titleKey,
            titleIsFileName: true,
            supporting: const TextSpan(text: 'lib/checkout'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
          textScale: 2,
        );
        final shown = lines(tester);
        expect(shown.join(), name, reason: 'the whole name is shown');
        expect(shown.length, greaterThan(1), reason: 'the name wraps');
        for (var i = 0; i + 1 < shown.length; i++) {
          final (line, next) = (shown[i], shown[i + 1]);
          expect(
            line.endsWith('_') || line.endsWith('-') || next.startsWith('.'),
            isTrue,
            reason: 'no mid-word break: $shown',
          );
        }
        // Read as the name, without the break chances.
        final label = tester.getSemantics(find.byKey(titleKey)).label;
        expect(label, contains(name));
        expect(label, isNot(contains('\u200B')));
        semantics.dispose();
      });
    }

    testWidgets('at 1.0 text the title is the plain name', (tester) async {
      await _pump(
        tester,
        const KitRow(
          title: 'checkout_page.dart',
          titleKey: titleKey,
          titleIsFileName: true,
        ),
      );
      expect(find.text('checkout_page.dart'), findsOneWidget);
    });

    test('kitBreakableFileName keeps every character in order', () {
      const name = 'a_b-c.d.dart';
      expect(kitBreakableFileName(name).replaceAll('\u200B', ''), name);
      expect(kitBreakableFileName('README'), 'README');
    });
  });

  group('KitSwipeAction', () {
    KitSwipeAction swipe({
      required Future<bool> Function() onAct,
      required void Function() onUndo,
      FutureOr<void> Function()? onCommit,
    }) => KitSwipeAction(
      id: const ValueKey('session-dismiss-1'),
      label: 'Archive',
      icon: AppIconography.archive,
      onAct: onAct,
      undoMessage: "Archived 'Fix login'",
      onUndo: onUndo,
      onCommit: onCommit,
    );

    testWidgets('1, 3, 10: a full swipe acts once, shows Undo, never '
        'confirms, no haptic', (tester) async {
      final haptics = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'HapticFeedback.vibrate') haptics.add('buzz');
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      var acts = 0;
      var undos = 0;
      await _pump(
        tester,
        KitRow(
          title: 'Fix login',
          onTap: () {},
          swipe: swipe(
            onAct: () async {
              acts++;
              return true;
            },
            onUndo: () => undos++,
          ),
        ),
      );
      await tester.drag(find.text('Fix login'), const Offset(-400, 0));
      await tester.pumpAndSettle();
      expect(acts, 1);
      expect(find.text('Fix login'), findsOneWidget); // snapped back
      expect(find.text("Archived 'Fix login'"), findsOneWidget);
      expect(find.byType(KitConfirmSheet), findsNothing);
      await tester.tap(find.text('Undo'));
      await tester.pumpAndSettle();
      expect(undos, 1);
      expect(haptics, isEmpty);
    });

    testWidgets('2: a failed or throwing act shows no undo line', (
      tester,
    ) async {
      for (final act in <Future<bool> Function()>[
        () async => false,
        () async => throw StateError('offline'),
      ]) {
        await _pump(
          tester,
          KitRow(
            title: 'Fix login',
            onTap: () {},
            swipe: swipe(onAct: act, onUndo: () {}),
          ),
        );
        await tester.drag(find.text('Fix login'), const Offset(-400, 0));
        await tester.pumpAndSettle();
        expect(find.text("Archived 'Fix login'"), findsNothing);
        expect(find.text('Fix login'), findsOneWidget);
      }
    });

    testWidgets('4: the twin is in the menu and a custom action; both run '
        'the undo path', (tester) async {
      final handle = tester.ensureSemantics();
      var acts = 0;
      await _pump(
        tester,
        KitRow(
          title: 'Fix login',
          onTap: () {},
          menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
          swipe: swipe(
            onAct: () async {
              acts++;
              return true;
            },
            onUndo: () {},
          ),
        ),
      );
      expect(
        _customActionLabels(tester, find.byType(KitRow)),
        containsAll(['Archive', 'Rename']),
      );
      await tester.longPress(find.text('Fix login'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Archive'));
      await tester.pumpAndSettle();
      expect(acts, 1);
      expect(find.text("Archived 'Fix login'"), findsOneWidget);
      _performCustomAction(tester, find.byType(KitRow), 'Archive');
      await tester.pumpAndSettle();
      expect(acts, 2);
      KitUndo.commitPending();
      await tester.pumpAndSettle();
      handle.dispose();
    });

    testWidgets('5: a menu item with the swipe label asserts', (tester) async {
      await _pump(
        tester,
        KitRow(
          title: 'Fix login',
          onTap: () {},
          menu: [KitMenuItem(label: 'Archive', onSelected: () {})],
          swipe: swipe(onAct: () async => true, onUndo: () {}),
        ),
      );
      expect(tester.takeException(), isAssertionError);
    });

    testWidgets('6: a deferred act commits once when the window closes, '
        'never after Undo', (tester) async {
      var commits = 0;
      var undos = 0;
      Future<void> pumpRow() => _pump(
        tester,
        KitRow(
          title: 'Fix login',
          onTap: () {},
          swipe: swipe(
            onAct: () async => true,
            onUndo: () => undos++,
            onCommit: () => commits++,
          ),
        ),
      );
      await pumpRow();
      await tester.drag(find.text('Fix login'), const Offset(-400, 0));
      await tester.pumpAndSettle();
      await tester.pump(KitUndo.window + const Duration(seconds: 1));
      await tester.pumpAndSettle();
      expect(commits, 1);

      await tester.drag(find.text('Fix login'), const Offset(-400, 0));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Undo'));
      await tester.pumpAndSettle();
      await tester.pump(KitUndo.window + const Duration(seconds: 1));
      await tester.pumpAndSettle();
      expect(commits, 1);
      expect(undos, 1);
    });

    testWidgets('8, 9: the revealed panel is surface3/text1, never danger, '
        'and settles at once under reduced motion', (tester) async {
      await _pump(
        tester,
        MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: KitRow(
            title: 'Fix login',
            onTap: () {},
            swipe: swipe(onAct: () async => false, onUndo: () {}),
          ),
        ),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('Fix login')),
      );
      await gesture.moveBy(const Offset(-40, 0));
      await gesture.moveBy(const Offset(-120, 0));
      await tester.pump();
      final panel = tester.widget<ColoredBox>(
        find.byKey(const ValueKey('kit-swipe-background')),
      );
      final roles = _tokens(tester).roles;
      expect(panel.color, roles.surface3);
      expect(panel.color, isNot(roles.danger));
      expect(
        tester.widget<Icon>(find.byIcon(AppIconography.archive)).color,
        roles.text1,
      );
      await gesture.up();
      await tester.pump();
      await tester.pump();
      expect(tester.binding.hasScheduledFrame, isFalse);
    });
  });

  group('chip', () {
    List<Widget> rows(List<String> taps) => [
      for (final (title, act) in [
        ('Codex', 'Install'),
        ('Gemini CLI', 'Install'),
        ('fx', 'Sign in'),
      ])
        KitRow(
          title: title,
          supporting: const TextSpan(text: 'Not installed'),
          chip: KitRowChip(
            key: ValueKey('chip-$title'),
            label: act,
            semanticsLabel: '$act $title',
            onPressed: () => taps.add(title),
          ),
        ),
      KitRow.unavailable(
        key: const ValueKey('unavailable'),
        title: 'Goose',
        reason: 'Not installed',
        chip: KitRowChip(
          key: const ValueKey('chip-Goose'),
          label: 'Install',
          semanticsLabel: 'Install Goose',
          onPressed: () => taps.add('Goose'),
        ),
      ),
    ];

    testWidgets('chips start at one edge and name their target', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final taps = <String>[];
      await _pump(tester, KitRowGroup(children: rows(taps)));
      final starts = {
        for (final title in ['Codex', 'Gemini CLI', 'fx', 'Goose'])
          tester.getTopLeft(find.byKey(ValueKey('chip-$title'))).dx,
      };
      expect(starts, hasLength(1), reason: '$starts');
      expect(find.text('Install'), findsNWidgets(3));
      expect(find.bySemanticsLabel('Install Gemini CLI'), findsOneWidget);
      expect(find.bySemanticsLabel('Sign in fx'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('chip-fx')));
      // A dimmed row's chip is its way forward; the whole row runs it.
      await tester.tap(find.text('Goose'));
      expect(taps, ['fx', 'Goose']);
      semantics.dispose();
    });

    testWidgets('from 1.3x text the chip moves under the words', (
      tester,
    ) async {
      await _pump(tester, KitRowGroup(children: rows([])), textScale: 1.3);
      final chip = tester.getRect(find.byKey(const ValueKey('chip-Codex')));
      final words = tester.getRect(find.text('Not installed').first);
      expect(chip.top, greaterThanOrEqualTo(words.bottom));
      expect(tester.takeException(), isNull);
    });
  });
}
