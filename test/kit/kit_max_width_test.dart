// KitMaxWidth (docs/ux-system/kit-v2.md §8.6): a part never paints wider
// than its role's cap, whatever its caller gives it. The conversation parts
// share one 700 dp column and one start edge on a tablet or a PC.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_max_width.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';

final _prose = List.filled(60, 'A long sentence in the agent reply.').join(' ');

Future<void> _pump(
  WidgetTester tester,
  List<Widget> children, {
  double width = 1600,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = Size(width, 1000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) =>
          Directionality(textDirection: direction, child: child!),
      home: Scaffold(
        body: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: children,
          ),
        ),
      ),
    ),
  );
  await tester.pump();
}

/// What a part's [KitMaxWidth] gave its content: the box the content has.
Rect _content(WidgetTester tester, Finder part) {
  final box = tester.renderObject<RenderBox>(part);
  if (box.runtimeType.toString() != '_RenderKitMaxWidth') {
    return box.localToGlobal(Offset.zero) & box.size;
  }
  final child = (box as RenderShiftedBox).child!;
  return child.localToGlobal(Offset.zero) & child.size;
}

final _boxes = find.byWidgetPredicate(
  (w) => w.runtimeType.toString() == '_KitMaxWidthBox',
);

void main() {
  testWidgets('KitMaxWidth caps its child and centres it by default', (
    tester,
  ) async {
    await _pump(tester, [
      KitMaxWidth.cap(maxWidth: 300, child: const SizedBox(height: 10)),
    ]);
    final rect = _content(tester, _boxes);
    expect(rect.width, 300);
    expect(rect.left, 650);
  });

  testWidgets('an inline part keeps the start edge, in either direction', (
    tester,
  ) async {
    await _pump(tester, [
      KitMaxWidth.chip(child: const SizedBox(height: 10)),
    ], direction: TextDirection.rtl);
    final rect = _content(tester, _boxes);
    expect(rect.width, KitLayout.chipMaxWidth);
    expect(rect.right, 1600);
  });

  testWidgets('a phone is untouched: the child gets the whole width', (
    tester,
  ) async {
    await _pump(tester, [
      KitMaxWidth.conversation(child: const SizedBox(height: 10)),
    ], width: 412);
    final rect = _content(tester, _boxes);
    expect(rect.width, 412);
    expect(rect.left, 0);
  });

  testWidgets('at 1,600 dp the conversation parts share one centred column', (
    tester,
  ) async {
    final controller = TextEditingController(text: _prose);
    final focus = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focus.dispose);
    await _pump(tester, [
      KitMessage.prompt(body: KitMarkdown(_prose, selectable: false)),
      KitMarkdown(_prose, selectable: false),
      KitComposer(
        controller: controller,
        focusNode: focus,
        hint: 'Ask',
        onSend: () {},
      ),
    ]);
    const left = (1600 - KitLayout.paneDetailMaxWidth) / 2;
    for (final part in [KitMessage, KitMarkdown, KitComposer]) {
      // The last KitMarkdown is the one the column holds directly.
      final rect = _content(tester, find.byType(part).last);
      expect(rect.width, KitLayout.paneDetailMaxWidth, reason: '$part');
      expect(rect.left, left, reason: '$part sits in the middle, on one edge');
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('a screen-level status line spans the window, not a column', (
    tester,
  ) async {
    await _pump(tester, [
      const KitStatusLine(
        icon: AppIconography.info,
        message: 'A leftover node process has been busy for 12 min',
        tone: AppStatusTone.neutral,
      ),
    ]);
    expect(_content(tester, find.byType(KitStatusLine)).width, 1600);
  });

  testWidgets('a chip ends where the chevron of the rows around it ends', (
    tester,
  ) async {
    await _pump(tester, [
      Builder(
        builder: (context) => KitRowGroup(
          children: [
            KitRow(
              title: 'Claude Code',
              trailing: KitRow.icon(context, AppIconography.chevronRight),
              onTap: () {},
            ),
            KitRow(
              title: 'Gemini CLI',
              chip: KitRowChip(
                label: 'Go',
                semanticsLabel: 'Go Gemini CLI',
                onPressed: () {},
              ),
            ),
            KitRow(
              title: 'Check this phone',
              trailing: KitRow.icon(context, AppIconography.chevronRight),
              onTap: () {},
            ),
          ],
        ),
      ),
    ], width: 800);
    final chevrons = find.byWidgetPredicate(
      (w) => w is Icon && w.icon == AppIconography.chevronRight,
    );
    expect(chevrons, findsNWidgets(2));
    final chip = tester.getRect(find.byType(KitChip)).right;
    for (var i = 0; i < 2; i++) {
      final edge = tester.getRect(chevrons.at(i)).right;
      expect(
        (chip - edge).abs(),
        lessThanOrEqualTo(8),
        reason: 'the chip ends at $chip, the chevron at $edge',
      );
    }
  });
}
