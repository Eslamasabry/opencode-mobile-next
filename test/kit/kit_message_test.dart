// KitMessage (docs/ux-system/kit-api/KitMessage.md; STATE-16, KIT-41,
// LOOK-26, LOOK-5, KIT-28, A11Y-5): the words of a transcript. The numbered
// groups follow the spec's "Tests required".
import 'dart:convert';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_chips.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_divider.dart';
import 'package:opencode_mobile/ui/kit/kit_image.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_tappable.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

const _bubbleKey = ValueKey('user-prompt-1');
const _thoughtKey = ValueKey('thought-1');
const _noticeKey = ValueKey('notice-1');
const _markerKey = ValueKey('marker-1');
const _bodyKey = ValueKey('reply-1');

Widget _app(
  Widget child, {
  bool reduced = false,
  double textScale = 1,
  TextDirection? direction,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, inner) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      disableAnimations: reduced,
      textScaler: TextScaler.linear(textScale),
    ),
    child: direction == null
        ? inner!
        : Directionality(textDirection: direction, child: inner!),
  ),
  home: Scaffold(
    body: SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [child],
      ),
    ),
  ),
);

/// A 432 dp window: the host column is 400 dp wide.
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(432, 915),
  bool reduced = false,
  double textScale = 1,
  TextDirection? direction,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    _app(child, reduced: reduced, textScale: textScale, direction: direction),
  );
  await tester.pump();
}

KitMessage _prompt({
  String text = 'Fix the flaky checkout test.',
  List<KitMenuItem> menu = const [],
  List<KitAttachment> attachments = const [],
  DateTime? time,
}) => KitMessage.prompt(
  body: KitMarkdown(text, selectable: false),
  menu: menu,
  attachments: attachments,
  time: time,
  bubbleKey: _bubbleKey,
);

List<KitMenuItem> _menu(List<String> ran) => [
  KitMenuItem(label: 'Copy message', onSelected: () => ran.add('copy')),
  KitMenuItem(label: 'Edit and resend', onSelected: () => ran.add('edit')),
];

/// A 1×1 PNG.
final _onePixelPng = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
);

ThemeRoles _roles(WidgetTester tester) =>
    KitTokens.of(tester.element(find.byType(KitMessage))).roles;

void main() {
  kitMotionStillTests(
    'KitMessage',
    builds: {
      'prompt': () => const KitMessage.prompt(
        body: KitMarkdown('Review checkout', selectable: false),
      ),
      'working thought': () => const KitMessage.thought(
        body: KitMarkdown('Inspecting the change', selectable: false),
        working: true,
      ),
      'notice': () =>
          const KitMessage.notice(text: 'The request failed', failed: true),
    },
    changes: {
      'thought opens': KitMotionChange(
        build: () => const KitMessage.thought(
          body: KitMarkdown('Inspecting the change', selectable: false),
          expanded: false,
        ),
        act: (tester, stage) => stage.rebuild(
          const KitMessage.thought(
            body: KitMarkdown('Inspecting the change', selectable: false),
            expanded: true,
          ),
        ),
        shows: 'Inspecting the change',
      ),
      'thought closes': KitMotionChange(
        build: () => const KitMessage.thought(
          body: KitMarkdown('Inspecting the change', selectable: false),
          expanded: true,
        ),
        act: (tester, stage) => stage.rebuild(
          const KitMessage.thought(
            body: KitMarkdown('Inspecting the change', selectable: false),
            expanded: false,
          ),
        ),
        hides: 'Inspecting the change',
      ),
    },
  );

  group('1. prompt bubble', () {
    for (final direction in TextDirection.values) {
      testWidgets(
        'sits at the end edge, surface2, 20/20/6/20, at most full width minus 48 '
        '(${direction.name})',
        (tester) async {
          await _pump(
            tester,
            _prompt(
              text:
                  'Fix the flaky checkout test. It fails about one run in five '
                  'on CI and I would like to know why before the release.',
            ),
            direction: direction,
          );
          final rect = tester.getRect(find.byKey(_bubbleKey));
          if (direction == TextDirection.ltr) {
            expect(rect.right, 416);
          } else {
            expect(rect.left, 16);
          }
          // Owner decision 04A: at most 85 % of the column.
          expect(
            rect.width,
            lessThanOrEqualTo((400 * KitLayout.bubbleMaxShare).floorToDouble()),
          );

          final box = tester.renderObject<RenderDecoratedBox>(
            find.byKey(_bubbleKey),
          );
          final decoration = box.decoration as BoxDecoration;
          expect(decoration.color, kitPromptBubbleFill(_roles(tester)));
          expect(decoration.border, isNull);
          expect(decoration.boxShadow, isNull);
          final radius = decoration.borderRadius!.resolve(direction);
          const tail = Radius.circular(6);
          const round = Radius.circular(20);
          if (direction == TextDirection.ltr) {
            expect(radius.bottomRight, tail);
            expect(radius.bottomLeft, round);
          } else {
            expect(radius.bottomLeft, tail);
            expect(radius.bottomRight, round);
          }
          expect(radius.topLeft, round);
          expect(radius.topRight, round);
        },
      );
    }

    testWidgets('a short prompt hugs its words', (tester) async {
      await _pump(tester, _prompt(text: 'Hi'));
      expect(tester.getSize(find.byKey(_bubbleKey)).width, lessThan(120));
    });

    testWidgets('time shows as a caption under the bubble', (tester) async {
      await _pump(tester, _prompt(time: DateTime(2026, 9, 27, 14, 5)));
      final time = find.text('2:05 PM');
      expect(time, findsOneWidget);
      expect(
        tester.getTopLeft(time).dy,
        greaterThanOrEqualTo(tester.getBottomLeft(find.byKey(_bubbleKey)).dy),
      );
      expect(
        tester.widget<KitText>(find.byType(KitText).last).role,
        KitTextRole.caption,
      );
    });
  });

  group('2. prompt menu', () {
    testWidgets('no control row; long-press opens the menu; custom actions', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final ran = <String>[];
      await _pump(tester, _prompt(menu: _menu(ran)));
      final inMessage = find.byType(KitMessage);
      for (final type in [KitButton, KitIconButton, KitTappable]) {
        expect(
          find.descendant(of: inMessage, matching: find.byType(type)),
          findsNothing,
        );
      }
      final node = tester.getSemantics(find.byKey(_bubbleKey));
      expect(node.flagsCollection.isButton, isFalse);
      final actions = [
        for (final id in node.getSemanticsData().customSemanticsActionIds!)
          CustomSemanticsAction.getAction(id)?.label,
      ];
      expect(actions, containsAll(['Copy message', 'Edit and resend']));

      await tester.longPress(find.byKey(_bubbleKey));
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.text('Copy message'), findsOneWidget);
      await tester.tap(find.text('Edit and resend'));
      await tester.pumpAndSettle();
      expect(ran, ['edit']);
      semantics.dispose();
    });

    testWidgets('right-click opens the same items (desktop capabilities)', (
      tester,
    ) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      addTearDown(() => debugPlatformCapabilities = null);
      await _pump(tester, _prompt(menu: _menu([])));
      await tester.tap(
        find.byKey(_bubbleKey),
        buttons: kSecondaryMouseButton,
        kind: PointerDeviceKind.mouse,
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(find.text('Copy message'), findsOneWidget);
      expect(find.text('Edit and resend'), findsOneWidget);
    });

    testWidgets('an empty menu opens nothing', (tester) async {
      await _pump(tester, _prompt());
      await tester.longPress(find.byKey(_bubbleKey));
      await tester.pumpAndSettle();
      expect(find.byType(KitMenuPanel), findsNothing);
    });
  });

  testWidgets('3. two attachments show both names, no remove control', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await _pump(
      tester,
      _prompt(
        attachments: const [
          KitAttachment(
            id: 1,
            label: 'checkout_test.dart',
            kind: KitAttachmentKind.file,
          ),
          KitAttachment(
            id: 2,
            label: 'screenshot.png',
            kind: KitAttachmentKind.image,
          ),
        ],
      ),
    );
    expect(find.textContaining('checkout_test.dart'), findsOneWidget);
    expect(find.textContaining('screenshot.png'), findsOneWidget);
    expect(find.byType(KitIconButton), findsNothing);
    expect(find.bySemanticsLabel(RegExp('Remove')), findsNothing);
    expect(
      tester.getSemantics(find.byKey(_bubbleKey)).label,
      allOf(contains('checkout_test.dart'), contains('screenshot.png')),
    );
    semantics.dispose();
  });

  testWidgets('3b. a photo with pixels shows as a thumbnail that opens', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var opened = 0;
    await _pump(
      tester,
      _prompt(
        attachments: [
          KitAttachment(
            id: 1,
            label: 'screenshot.png',
            kind: KitAttachmentKind.image,
            thumbnail: KitImageSource.memory(_onePixelPng),
            onOpen: () => opened++,
            chipKey: const ValueKey('photo-open'),
            thumbnailKey: const ValueKey('photo-thumb'),
          ),
          const KitAttachment(
            id: 2,
            label: 'notes.pdf',
            kind: KitAttachmentKind.file,
          ),
        ],
      ),
    );
    final thumb = find.byKey(const ValueKey('photo-thumb'));
    expect(thumb, findsOneWidget);
    expect(tester.getSize(thumb), const Size.square(KitLayout.promptPhotoSize));
    // The photo is its picture, not its name; other files stay chips.
    expect(find.textContaining('screenshot.png'), findsNothing);
    expect(find.textContaining('notes.pdf'), findsOneWidget);
    expect(find.bySemanticsLabel('Preview screenshot.png'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('photo-open')));
    expect(opened, 1);
    semantics.dispose();
  });

  testWidgets('4. reply: no frame, full width, the markdown is there', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitMessage.reply(
        body: KitMarkdown('The flakiness comes from a race.'),
        bodyKey: _bodyKey,
      ),
    );
    expect(find.byType(KitMarkdown), findsOneWidget);
    expect(tester.getSize(find.byKey(_bodyKey)).width, 400);
    expect(
      find.descendant(
        of: find.byType(KitMessage),
        matching: find.byType(DecoratedBox),
      ),
      findsNothing,
    );
  });

  group('5. thought', () {
    Future<void> title(
      WidgetTester tester,
      KitMessage message,
      String expected,
    ) async {
      await _pump(tester, message);
      expect(find.text(expected), findsOneWidget);
    }

    const body = KitMarkdown(
      'I should read the bloc first.',
      role: KitTextRole.secondary,
    );

    testWidgets('title rules', (tester) async {
      await title(
        tester,
        const KitMessage.thought(body: body, heading: 'Reading the bloc'),
        'Reading the bloc',
      );
      await title(
        tester,
        const KitMessage.thought(body: body, working: true),
        'Thinking…',
      );
      await title(
        tester,
        const KitMessage.thought(body: body, took: Duration(seconds: 12)),
        'Thought for 12 seconds',
      );
      await title(
        tester,
        const KitMessage.thought(body: body, took: Duration(minutes: 3)),
        'Thought for 3 minutes',
      );
      await title(tester, const KitMessage.thought(body: body), 'Thought');
    });

    testWidgets('toggling shows and hides the body', (tester) async {
      final changes = <bool>[];
      await _pump(
        tester,
        KitMessage.thought(
          body: body,
          thoughtKey: _thoughtKey,
          onExpansionChanged: changes.add,
        ),
      );
      expect(find.text('I should read the bloc first.'), findsNothing);
      await tester.tap(find.byKey(_thoughtKey));
      await tester.pumpAndSettle();
      expect(find.text('I should read the bloc first.'), findsOneWidget);
      await tester.tap(find.byKey(_thoughtKey));
      await tester.pumpAndSettle();
      expect(find.text('I should read the bloc first.'), findsNothing);
      expect(changes, [true, false]);
    });

    testWidgets('controlled mode never toggles on its own', (tester) async {
      final changes = <bool>[];
      await _pump(
        tester,
        KitMessage.thought(
          body: body,
          expanded: false,
          thoughtKey: _thoughtKey,
          onExpansionChanged: changes.add,
        ),
      );
      await tester.tap(find.byKey(_thoughtKey));
      await tester.pumpAndSettle();
      expect(changes, [true]);
      expect(find.text('I should read the bloc first.'), findsNothing);
    });
  });

  group('6. notice', () {
    testWidgets('text, technical in mono, the action calls back once', (
      tester,
    ) async {
      var compacted = 0;
      await _pump(
        tester,
        KitMessage.notice(
          text: 'Skill loaded',
          technical: 'flutter-testing',
          action: KitAction(
            label: 'Compact again',
            onPressed: () => compacted++,
          ),
        ),
      );
      expect(find.text('Skill loaded'), findsOneWidget);
      final mono = tester.widget<KitText>(
        find.byWidgetPredicate(
          (w) => w is KitText && w.text.contains('flutter-testing'),
        ),
      );
      expect(mono.role, KitTextRole.mono);
      await tester.tap(find.text('Compact again'));
      await tester.pumpAndSettle();
      expect(compacted, 1);
    });

    testWidgets('failed: "Failed," first, and no danger colour', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        const KitMessage.notice(
          text: 'Compaction did not finish',
          failed: true,
          noticeKey: _noticeKey,
        ),
      );
      expect(
        tester.getSemantics(find.byKey(_noticeKey)).label,
        startsWith('Failed, '),
      );
      final danger = _roles(tester).danger;
      for (final icon in tester.widgetList<Icon>(
        find.descendant(
          of: find.byType(KitMessage),
          matching: find.byType(Icon),
        ),
      )) {
        expect(icon.color, isNot(danger));
      }
      for (final text in tester.widgetList<RichText>(
        find.descendant(
          of: find.byType(KitMessage),
          matching: find.byType(RichText),
        ),
      )) {
        text.text.visitChildren((span) {
          expect(span.style?.color, isNot(danger));
          return true;
        });
      }
      semantics.dispose();
    });

    testWidgets('with detail it is a fold', (tester) async {
      await _pump(
        tester,
        const KitMessage.notice(
          text: 'Conversation compacted',
          detail: KitMarkdown('Kept the last 12 messages.'),
          noticeKey: _noticeKey,
        ),
      );
      expect(find.text('Kept the last 12 messages.'), findsNothing);
      await tester.tap(find.byKey(_noticeKey));
      await tester.pumpAndSettle();
      expect(find.text('Kept the last 12 messages.'), findsOneWidget);
    });
  });

  group('7. marker', () {
    testWidgets('two rules and centred words', (tester) async {
      await _pump(
        tester,
        const KitMessage.marker(text: 'Switched to Sonnet 4'),
      );
      expect(find.byType(KitDivider), findsNWidgets(2));
      final words = tester.getCenter(find.text('Switched to Sonnet 4')).dx;
      final whole = tester.getCenter(find.byType(KitMessage)).dx;
      expect((words - whole).abs(), lessThanOrEqualTo(1));
      expect(find.byType(KitStatusMark), findsNothing);
    });

    testWidgets('working shows a working mark', (tester) async {
      await _pump(
        tester,
        const KitMessage.marker(text: 'Compacting…', working: true),
      );
      final mark = tester.widget<KitStatusMark>(find.byType(KitStatusMark));
      expect(mark.state, KitMarkState.working);
    });
  });

  testWidgets('8. no size animation; reduced motion settles in one pump', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitMessage.thought(
        body: KitMarkdown('Body words'),
        thoughtKey: _thoughtKey,
      ),
      reduced: true,
    );
    await tester.tap(find.byKey(_thoughtKey));
    await tester.pump();
    expect(find.text('Body words'), findsOneWidget);
    expect(find.byType(AnimatedSize), findsNothing);
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('9. semantics: "You said", folds are expanded buttons, no '
      'live regions, 48 dp targets', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pump(
      tester,
      Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _prompt(menu: _menu([])),
          const KitMessage.thought(
            body: KitMarkdown('Body words'),
            took: Duration(seconds: 4),
            thoughtKey: _thoughtKey,
          ),
          const KitMessage.notice(
            text: 'Context added',
            detail: KitMarkdown('AGENTS.md'),
            noticeKey: _noticeKey,
          ),
          const KitMessage.marker(
            text: 'Switched to Sonnet 4',
            markerKey: _markerKey,
          ),
        ],
      ),
    );
    expect(
      tester.getSemantics(find.byKey(_bubbleKey)).label,
      allOf(startsWith('You said, '), contains('Fix the flaky')),
    );
    for (final key in [_thoughtKey, _noticeKey]) {
      expect(
        tester.getSemantics(find.byKey(key)),
        isSemantics(isButton: true, hasExpandedState: true, isExpanded: false),
      );
      final size = tester.getSize(find.byKey(key));
      expect(size.height, greaterThanOrEqualTo(48));
    }
    expect(
      tester.getSize(find.byKey(_bubbleKey)).height,
      greaterThanOrEqualTo(48),
    );
    expect(
      find.byWidgetPredicate(
        (w) => w is Semantics && (w.properties.liveRegion ?? false),
      ),
      findsNothing,
    );
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    semantics.dispose();
  });

  testWidgets('10. desktop: Tab reaches the prompt and each fold; Shift+F10 '
      'opens the prompt menu; the ring shows', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    addTearDown(() => debugPlatformCapabilities = null);
    await _pump(
      tester,
      Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _prompt(menu: _menu([])),
          // Selection belongs to the host's KitSelectable (spec "Adaptive"),
          // so the reply's own Markdown is not selectable and not a stop.
          const KitMessage.reply(
            body: KitMarkdown('Plain reply words.', selectable: false),
          ),
          const KitMessage.thought(
            body: KitMarkdown('Body words', selectable: false),
            thoughtKey: _thoughtKey,
          ),
          const KitMessage.notice(
            text: 'Context added',
            detail: KitMarkdown('AGENTS.md', selectable: false),
            noticeKey: _noticeKey,
          ),
        ],
      ),
    );

    bool focusHolds(Finder target) {
      final focused = FocusManager.instance.primaryFocus?.context;
      if (focused == null) return false;
      return find
          .descendant(
            of: find.byElementPredicate((e) => e == focused),
            matching: target,
          )
          .evaluate()
          .isNotEmpty;
    }

    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(focusHolds(find.byKey(_bubbleKey)), isTrue);
    final ring = tester.widget<CustomPaint>(
      find
          .descendant(
            of: find.byKey(_bubbleKey),
            matching: find.byType(CustomPaint),
          )
          .first,
    );
    expect(ring.foregroundPainter, isNotNull);

    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.f10);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    await tester.pumpAndSettle();
    expect(find.byType(KitMenuPanel), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(find.byType(KitMenuPanel), findsNothing);

    // Focus returns to the bubble; the next stops are the folds, in order.
    expect(
      focusHolds(find.byKey(_bubbleKey)),
      isTrue,
      reason: '${FocusManager.instance.primaryFocus}',
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(
      focusHolds(find.byKey(_thoughtKey)),
      isTrue,
      reason: '${FocusManager.instance.primaryFocus}',
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(find.text('Body words'), findsOneWidget);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(
      focusHolds(find.byKey(_noticeKey)),
      isTrue,
      reason: '${FocusManager.instance.primaryFocus}',
    );
  });

  for (final direction in TextDirection.values) {
    testWidgets('11. 200 % text at 320 dp: no overflow (${direction.name})', (
      tester,
    ) async {
      await _pump(
        tester,
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _prompt(
              text:
                  'Fix the flaky checkout test. It fails about one run in five.',
              time: DateTime(2026, 9, 27, 9, 30),
              attachments: const [
                KitAttachment(
                  id: 1,
                  label: 'checkout_test.dart',
                  kind: KitAttachmentKind.file,
                ),
              ],
              menu: _menu([]),
            ),
            const KitMessage.reply(
              body: KitMarkdown('The flakiness comes from a race.'),
            ),
            const KitMessage.thought(
              body: KitMarkdown('Body words that go on for a while.'),
              heading: 'Reading the checkout bloc and its tests',
              expanded: true,
            ),
            KitMessage.notice(
              text: 'Instructions updated',
              technical: 'packages/checkout/AGENTS.md',
              action: KitAction(label: 'Compact again', onPressed: () {}),
            ),
            const KitMessage.notice(
              text: 'Compaction did not finish',
              failed: true,
              detail: KitMarkdown('The server stopped answering.'),
            ),
            const KitMessage.marker(
              text: 'Switched to Claude Sonnet 4 for this conversation',
              working: true,
            ),
          ],
        ),
        size: const Size(320, 2400),
        textScale: 2,
        direction: direction,
      );
      expect(tester.takeException(), isNull);
    });
  }

  test('the prompt bubble is visible on the page in light and dark', () {
    for (final roles in [graphiteLight, graphiteDark]) {
      final fill = kitPromptBubbleFill(roles);
      for (final page in [roles.ground, if (!roles.isDark) roles.surface1]) {
        expect(
          contrastRatio(fill, page),
          greaterThanOrEqualTo(1.12),
          reason: '${roles.brightness} bubble against $page',
        );
      }
      expect(contrastRatio(roles.text1, fill), greaterThanOrEqualTo(7));
    }
  });
}
