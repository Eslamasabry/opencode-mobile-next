// kit-KitAction-v2 (docs/ux-system/kit-api/KitAction.md): KitAction,
// KitButton, KitActionBlock and KitActionStack in one file, as the frozen
// spec's own "Tests" section names it.
//
// Covers: a disabled action's reason renders as visible text and as the
// button's semantic hint (STATE-8); a destructive tertiary forces the
// block to stack on every window, with clearance around it (LAY-9,
// LAY-14); the block is one end-aligned row from medium up otherwise;
// overflow into "More", with a destructive item last after a divider;
// KitAction.copy; `working` ignores taps; the shortcut hint; and KIT-43
// compatibility (old call shapes, old keys).
import 'dart:math' as math;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _compact = Size(412, 915);
const _medium = Size(700, 900);
const _large = Size(1280, 800);
const _shortWide = Size(915, 412); // wide but under KitLayout.shortHeight.

Future<void> _pumpAt(
  WidgetTester tester,
  Widget child, {
  Size size = _compact,
  Locale locale = const Locale('en'),
  double textScale = 1,
  bool reducedMotion = false,
  Key? appKey,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      key: appKey,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reducedMotion,
        ),
        child: child!,
      ),
      home: Scaffold(body: child),
    ),
  );
}

/// Every kit button's full hit area (its [ButtonStyleButton], which holds
/// the 48 dp target), not the words inside it (LAY-9).
List<Rect> _buttonRects(WidgetTester tester) => [
  for (final element
      in find.byWidgetPredicate((w) => w is ButtonStyleButton).evaluate())
    if (element.renderObject case final RenderBox box)
      box.localToGlobal(Offset.zero) & box.size,
];

/// The hit area of the button labelled [label].
Rect _buttonRect(WidgetTester tester, String label) => tester.getRect(
  find
      .ancestor(
        of: find.text(label),
        matching: find.byWidgetPredicate((w) => w is ButtonStyleButton),
      )
      .first,
);

/// The clear distance between two rectangles (0 when they touch or
/// overlap).
double _gap(Rect a, Rect b) {
  final dx = [b.left - a.right, a.left - b.right, 0.0].reduce(math.max);
  final dy = [b.top - a.bottom, a.top - b.bottom, 0.0].reduce(math.max);
  return math.sqrt(dx * dx + dy * dy);
}

/// Mocks the platform channel (the clipboard) and records what it gets.
List<MethodCall> _mockClipboard() {
  final calls = <MethodCall>[];
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    calls.add(call);
    return null;
  });
  addTearDown(
    () => messenger.setMockMethodCallHandler(SystemChannels.platform, null),
  );
  return calls;
}

String? _clipboardText(List<MethodCall> calls) {
  final call = calls.lastWhere((c) => c.method == 'Clipboard.setData');
  return (call.arguments as Map)['text'] as String?;
}

/// Connects a mouse for the test (a fine pointer, [KitLayout.finePointer]).
Future<void> _connectMouse(WidgetTester tester) async {
  final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
  addTearDown(gesture.removePointer);
  await gesture.addPointer(location: Offset.zero);
  await tester.pump();
}

void main() {
  group('KitAction', () {
    test(
      'enabled is true for a callback or a copy action, false otherwise',
      () {
        expect(KitAction(label: 'Save', onPressed: () {}).enabled, isTrue);
        expect(KitAction(label: 'Save', onPressed: null).enabled, isFalse);
        expect(KitAction.copy(label: 'Copy', text: () => 'x').enabled, isTrue);
      },
    );
  });

  group('disabledReason (STATE-8, P7.6)', () {
    testWidgets(
      'KitActionBlock shows a disabled primary\'s reason under it, as text '
      'and as the semantic hint',
      (tester) async {
        await _pumpAt(
          tester,
          KitActionBlock(
            primary: const KitAction(
              label: 'Save',
              onPressed: null,
              disabledReason: 'Fill in the server address first.',
            ),
          ),
        );

        expect(find.text('Fill in the server address first.'), findsOneWidget);
        expect(
          tester.getSemantics(find.text('Save')),
          matchesSemantics(
            label: 'Save',
            isButton: true,
            hasEnabledState: true,
            isEnabled: false,
            hint: 'Fill in the server address first.',
          ),
        );
      },
    );

    testWidgets('KitActionStack shows a disabled tertiary\'s reason under it', (
      tester,
    ) async {
      await _pumpAt(
        tester,
        KitActionStack(
          tertiary: [
            const KitAction(
              label: 'Stop',
              onPressed: null,
              disabledReason: 'Nothing is running.',
            ),
          ],
        ),
      );

      expect(find.text('Nothing is running.'), findsOneWidget);
      final reason = tester.getTopLeft(find.text('Nothing is running.'));
      final button = tester.getTopLeft(find.text('Stop'));
      expect(reason.dy, greaterThan(button.dy));
    });

    testWidgets('a disabled action with no reason renders with no reason line '
        '(KIT-43: unchanged for callers that pass none)', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(label: 'Save', onPressed: null),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byKey(const ValueKey('kit-action-reason')), findsNothing);
    });

    testWidgets('a row (medium+) collects reasons under it, end-aligned', (
      tester,
    ) async {
      await _pumpAt(
        tester,
        Align(
          alignment: Alignment.topRight,
          child: KitActionBlock(
            primary: const KitAction(
              label: 'Save',
              onPressed: null,
              disabledReason: 'Fill in the server address first.',
            ),
            secondary: const KitAction(label: 'Cancel', onPressed: null),
          ),
        ),
        size: _medium,
      );

      expect(find.text('Fill in the server address first.'), findsOneWidget);
      final reason = tester.getTopLeft(
        find.text('Fill in the server address first.'),
      );
      final save = tester.getTopLeft(find.text('Save'));
      expect(reason.dy, greaterThan(save.dy));
    });
  });

  group('destructive stacking (§2.7, LAY-14)', () {
    Widget block() => KitActionBlock(
      primary: const KitAction(label: 'Save', onPressed: null),
      secondary: const KitAction(label: 'Cancel', onPressed: null),
      tertiary: [
        KitAction(label: 'Duplicate', onPressed: () {}),
        KitAction(label: 'Delete', onPressed: () {}, destructive: true),
      ],
    );

    for (final MapEntry(key: name, value: size) in {
      'compact': _compact,
      'medium': _medium,
      'large': _large,
    }.entries) {
      testWidgets('a destructive tertiary forces the stack on $name', (
        tester,
      ) async {
        await _pumpAt(tester, block(), size: size);

        // Stacked: Save above Cancel above the tertiary actions, each its
        // own line (never the end-aligned row, whatever the window).
        final save = tester.getTopLeft(find.text('Save'));
        final cancel = tester.getTopLeft(find.text('Cancel'));
        final duplicate = tester.getTopLeft(find.text('Duplicate'));
        final delete = tester.getTopLeft(find.text('Delete'));
        expect(cancel.dy, greaterThan(save.dy));
        expect(duplicate.dy, greaterThan(cancel.dy));
        expect(delete.dy, greaterThan(duplicate.dy));

        // LAY-9: the destructive button's 48 dp area keeps 8 dp from every
        // other target's area (the buttons, not their words).
        final targets = _buttonRects(tester);
        final deleteArea = _buttonRect(tester, 'Delete');
        expect(deleteArea.height, greaterThanOrEqualTo(48));
        expect(targets.length, 4);
        for (final other in targets) {
          if (other == deleteArea) continue;
          expect(_gap(deleteArea, other), greaterThanOrEqualTo(8));
        }
      });
    }

    // PROC-20 (QA record, contract problem 4): KitAction.md rule 1 / LAY-3
    // ("a short window stacks") overflows KitRequestCard's 45 % large-text
    // cap at 915x412 (G6), so that one item stays at today's behaviour: the
    // window's width decides, and a short wide window keeps the row.
    testWidgets('a short wide window keeps today\'s row (PROC-20)', (
      tester,
    ) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(label: 'Save', onPressed: null),
          secondary: const KitAction(label: 'Cancel', onPressed: null),
        ),
        size: _shortWide,
      );
      final save = tester.getTopLeft(find.text('Save'));
      final cancel = tester.getTopLeft(find.text('Cancel'));
      expect(save.dy, cancel.dy);
      expect(save.dx, greaterThan(cancel.dx));
    });

    testWidgets('compact stacks with no destructive tertiary', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(label: 'Save', onPressed: null),
          secondary: const KitAction(label: 'Cancel', onPressed: null),
        ),
      );
      final save = tester.getTopLeft(find.text('Save'));
      final cancel = tester.getTopLeft(find.text('Cancel'));
      expect(cancel.dy, greaterThan(save.dy));
    });

    testWidgets(
      'with no destructive tertiary, medium and up is one row with the '
      'primary at the end',
      (tester) async {
        await _pumpAt(
          tester,
          KitActionBlock(
            primary: const KitAction(label: 'Save', onPressed: null),
            secondary: const KitAction(label: 'Cancel', onPressed: null),
          ),
          size: _medium,
        );
        final save = tester.getTopLeft(find.text('Save'));
        final cancel = tester.getTopLeft(find.text('Cancel'));
        // A row: both on the same line, Save (primary) to the right of
        // Cancel (secondary) in LTR (LAY-13: primary at the end).
        expect(save.dy, cancel.dy);
        expect(save.dx, greaterThan(cancel.dx));
      },
    );
  });

  group('overflow ("More", §2.7)', () {
    testWidgets('a third tertiary action moves into More', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          tertiary: [
            KitAction(label: 'One', onPressed: () {}),
            KitAction(label: 'Two', onPressed: () {}),
            KitAction(label: 'Three', onPressed: () {}),
          ],
        ),
      );
      expect(find.text('One'), findsOneWidget);
      expect(find.text('Two'), findsOneWidget);
      expect(find.text('Three'), findsNothing);
      expect(find.byKey(const ValueKey('kit-actions-more')), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      expect(find.text('Three'), findsOneWidget);
    });

    testWidgets('a destructive overflow action renders last, after a divider', (
      tester,
    ) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          tertiary: [
            KitAction(label: 'One', onPressed: () {}),
            KitAction(label: 'Two', onPressed: () {}),
            KitAction(label: 'Three', onPressed: () {}),
            KitAction(label: 'Delete', onPressed: () {}, destructive: true),
          ],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();

      final divider = tester.getTopLeft(find.byType(PopupMenuDivider));
      final three = tester.getTopLeft(find.text('Three'));
      final delete = tester.getTopLeft(find.text('Delete'));
      expect(delete.dy, greaterThan(divider.dy));
      expect(divider.dy, greaterThan(three.dy));
    });

    testWidgets('tapping an overflow item calls its onPressed', (tester) async {
      var tapped = 0;
      await _pumpAt(
        tester,
        KitActionBlock(
          tertiary: [
            KitAction(label: 'One', onPressed: () {}),
            KitAction(label: 'Two', onPressed: () {}),
            KitAction(label: 'Three', onPressed: () => tapped++),
          ],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Three'));
      await tester.pumpAndSettle();
      expect(tapped, 1);
    });
  });

  group('KitAction.copy (KIT-23)', () {
    late List<MethodCall> platform;
    late List<Map<Object?, Object?>> announcements;

    setUp(() {
      platform = [];
      announcements = [];
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        platform.add(call);
        return null;
      });
      messenger.setMockDecodedMessageHandler<dynamic>(
        SystemChannels.accessibility,
        (message) async {
          final map = message as Map<Object?, Object?>;
          if (map['type'] == 'announce') announcements.add(map);
          return null;
        },
      );
    });

    tearDown(() {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(SystemChannels.platform, null);
      messenger.setMockDecodedMessageHandler<dynamic>(
        SystemChannels.accessibility,
        null,
      );
    });

    String? copiedText() {
      final call = platform.lastWhere((c) => c.method == 'Clipboard.setData');
      return (call.arguments as Map)['text'] as String?;
    }

    testWidgets(
      'copies the text read at tap time, announces once, shows the check '
      'then reverts, with no SnackBar',
      (tester) async {
        var reads = 0;
        await _pumpAt(
          tester,
          KitActionBlock(
            primary: KitAction.copy(
              label: 'Copy details',
              text: () {
                reads++;
                return 'details-$reads';
              },
            ),
          ),
        );

        await tester.tap(find.text('Copy details'));
        await tester.pump();

        expect(reads, 1);
        expect(copiedText(), 'details-1');
        expect(announcements, hasLength(1));
        expect((announcements.single['data'] as Map)['message'], 'Copied');
        expect(find.text('Copied'), findsOneWidget);
        expect(find.text('Copy details'), findsNothing);
        expect(find.byType(SnackBar), findsNothing);
        expect(find.byKey(const ValueKey('kit-action-copied')), findsOneWidget);

        await tester.pump(KitMotion.copiedHold);
        expect(find.text('Copy details'), findsOneWidget);
        expect(find.text('Copied'), findsNothing);
      },
    );
  });

  group('working (STATE-7, C21 f)', () {
    testWidgets('shows the spinner and ignores taps', (tester) async {
      var tapped = 0;
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: KitAction(
            label: 'Send',
            onPressed: () => tapped++,
            working: true,
          ),
        ),
      );
      expect(find.byKey(const ValueKey('kit-button-working')), findsOneWidget);
      await tester.tap(find.byType(FilledButton));
      await tester.pump();
      expect(tapped, 0);
    });
  });

  group('shortcut (visual language §5)', () {
    testWidgets('shown on a fine pointer, absent on touch', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(
            label: 'Send',
            onPressed: null,
            shortcut: 'Ctrl+Enter',
          ),
        ),
        size: _large,
      );
      expect(find.textContaining('Ctrl+Enter'), findsNothing);

      await _connectMouse(tester);
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(
            label: 'Send',
            onPressed: null,
            shortcut: 'Ctrl+Enter',
          ),
        ),
        size: _large,
      );
      expect(find.textContaining('Ctrl+Enter'), findsOneWidget);
    });

    testWidgets('isolated left to right in Arabic', (tester) async {
      await _connectMouse(tester);
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(
            label: 'إرسال',
            onPressed: null,
            shortcut: 'Ctrl+Enter',
          ),
        ),
        size: _large,
        locale: const Locale('ar'),
      );
      final shown = tester
          .widgetList<Text>(find.textContaining('Ctrl+Enter'))
          .single;
      expect(shown.data, KitBidi.ltr('Ctrl+Enter'));
    });
  });

  group('KIT-43 compatibility', () {
    testWidgets('the old menu: slot keeps working', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(label: 'Save', onPressed: null),
          menu: TextButton(onPressed: () {}, child: const Text('Legacy menu')),
        ),
      );
      expect(find.text('Legacy menu'), findsOneWidget);
    });

    testWidgets('KitButton.fromAction keeps the caller\'s key', (tester) async {
      await _pumpAt(
        tester,
        KitButton.fromAction(
          KitAction(
            key: const ValueKey('my-save-button'),
            label: 'Save',
            onPressed: () {},
          ),
          role: KitButtonRole.primary,
        ),
      );
      expect(find.byKey(const ValueKey('my-save-button')), findsOneWidget);
    });

    testWidgets('every existing KitButton constructor shape still compiles', (
      tester,
    ) async {
      await _pumpAt(
        tester,
        Column(
          children: [
            KitButton.primary(label: 'A', onPressed: () {}),
            KitButton.secondary(label: 'B', onPressed: () {}),
            KitButton.tertiary(label: 'C', onPressed: () {}),
            KitButton(
              role: KitButtonRole.primary,
              label: 'D',
              onPressed: () {},
            ),
          ],
        ),
      );
      expect(tester.takeException(), isNull);
      for (final label in ['A', 'B', 'C', 'D']) {
        expect(find.text(label), findsOneWidget);
      }
    });
  });

  group('disabled look (LOOK-14, test 2)', () {
    testWidgets('a disabled primary and secondary paint text3 on surface3 at '
        'full alpha, with no Opacity around a button', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(
            label: 'Save',
            onPressed: null,
            disabledReason: 'Fill in the server address first.',
          ),
          secondary: const KitAction(
            label: 'Test',
            onPressed: null,
            disabledReason: 'Nothing to test yet.',
          ),
          tertiary: const [
            KitAction(
              label: 'Duplicate',
              onPressed: null,
              disabledReason: 'Save it first.',
            ),
          ],
        ),
      );
      final roles = KitTokens.of(tester.element(find.text('Save'))).roles;
      for (final label in ['Save', 'Test']) {
        final material = tester.widget<Material>(
          find
              .descendant(
                of: find.ancestor(
                  of: find.text(label),
                  matching: find.byWidgetPredicate(
                    (w) => w is ButtonStyleButton,
                  ),
                ),
                matching: find.byType(Material),
              )
              .first,
        );
        expect(material.color, roles.surface3, reason: label);
        expect(material.color!.a, 1.0, reason: label);
      }
      for (final label in ['Save', 'Test', 'Duplicate']) {
        final paragraph = tester.renderObject<RenderParagraph>(
          find.text(label),
        );
        expect(paragraph.text.style?.color, roles.text3, reason: label);
        expect(paragraph.text.style!.color!.a, 1.0, reason: label);
      }
      expect(
        find.descendant(
          of: find.byType(KitActionBlock),
          matching: find.byType(Opacity),
        ),
        findsNothing,
      );
    });
  });

  group('overflow items (More)', () {
    testWidgets('a copy action in More is enabled and copies', (tester) async {
      final calls = _mockClipboard();
      await _pumpAt(
        tester,
        KitActionBlock(
          tertiary: [
            KitAction.copy(label: 'Copy details', text: () => 'details'),
            KitAction.copy(label: 'Copy all', text: () => 'all'),
            KitAction.copy(label: 'Copy log', text: () => 'the log'),
          ],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      final item = tester.widget<PopupMenuItem<int>>(
        find.ancestor(
          of: find.text('Copy log'),
          matching: find.byType(PopupMenuItem<int>),
        ),
      );
      expect(item.enabled, isTrue);
      await tester.tap(find.text('Copy log'));
      await tester.pumpAndSettle();
      expect(_clipboardText(calls), 'the log');
      expect(find.byType(SnackBar), findsNothing);
    });

    testWidgets('a disabled action in More shows its reason as a line and '
        'as its hint', (tester) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          tertiary: [
            KitAction(label: 'One', onPressed: () {}),
            KitAction(label: 'Two', onPressed: () {}),
            const KitAction(
              label: 'Export',
              onPressed: null,
              disabledReason: 'Nothing to export yet.',
            ),
          ],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      expect(find.text('Nothing to export yet.'), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-action-reason')), findsOneWidget);
      expect(
        tester.getTopLeft(find.text('Nothing to export yet.')).dy,
        greaterThan(tester.getTopLeft(find.text('Export')).dy),
      );
      expect(
        tester.getSemantics(find.text('Export')),
        isSemantics(
          label: 'Export\nNothing to export yet.',
          hint: 'Nothing to export yet.',
          isEnabled: false,
          hasEnabledState: true,
        ),
      );
    });

    testWidgets('two tertiary actions with the same label and reasons build '
        '(no label-derived keys)', (tester) async {
      await _pumpAt(
        tester,
        KitActionStack(
          primary: const KitAction(
            label: 'Save',
            onPressed: null,
            disabledReason: 'Reason A.',
          ),
          tertiary: const [
            KitAction(label: 'Retry', onPressed: null, disabledReason: 'B.'),
            KitAction(label: 'Retry', onPressed: null, disabledReason: 'C.'),
          ],
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byKey(const ValueKey('kit-action-reason')), findsNWidgets(3));
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(
            label: 'Save',
            onPressed: null,
            disabledReason: 'Reason A.',
          ),
          secondary: const KitAction(
            label: 'Test',
            onPressed: null,
            disabledReason: 'Reason D.',
          ),
          tertiary: const [
            KitAction(label: 'Retry', onPressed: null, disabledReason: 'B.'),
            KitAction(label: 'Retry', onPressed: null, disabledReason: 'C.'),
          ],
        ),
        size: _large,
      );
      expect(tester.takeException(), isNull);
      expect(find.byKey(const ValueKey('kit-action-reason')), findsNWidgets(4));
    });
  });

  group('tertiary colours (R5)', () {
    Color? wordsColour(WidgetTester tester, String label) =>
        DefaultTextStyle.of(tester.element(find.text(label))).style.color;

    testWidgets(
      'an enabled tertiary is neutral text1, never the disabled text3',
      (tester) async {
        final roles = ThemeRoles.resolve(AppTheme.dark());
        await _pumpAt(
          tester,
          Column(
            children: [
              KitButton.tertiary(label: 'Edit name', onPressed: () {}),
              const KitButton.tertiary(label: 'Rename', onPressed: null),
              KitButton.tertiary(
                label: 'Delete',
                destructive: true,
                onPressed: () {},
              ),
            ],
          ),
        );
        expect(wordsColour(tester, 'Edit name'), roles.text1);
        expect(wordsColour(tester, 'Rename'), roles.text3);
        expect(wordsColour(tester, 'Edit name'), isNot(roles.text3));
        expect(wordsColour(tester, 'Delete'), roles.danger);
      },
    );
  });

  group('KitAction.copy keys and redaction', () {
    testWidgets('the caller\'s key is on exactly one widget and taps copy', (
      tester,
    ) async {
      final calls = _mockClipboard();
      const key = ValueKey('copy-details');
      await _pumpAt(
        tester,
        KitActionBlock(
          secondary: KitAction.copy(
            key: key,
            label: 'Copy details',
            text: () => 'details',
          ),
        ),
      );
      expect(find.byKey(key), findsOneWidget);
      await tester.tap(find.byKey(key));
      await tester.pump();
      expect(_clipboardText(calls), 'details');
      await tester.pump(KitMotion.copiedHold);
    });

    testWidgets('a GlobalKey on a copy action builds', (tester) async {
      _mockClipboard();
      final key = GlobalKey();
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: KitAction.copy(key: key, label: 'Copy', text: () => 'x'),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byKey(key), findsOneWidget);
    });

    testWidgets('redact: false copies verbatim, in a block and in More '
        '(SEC-13)', (tester) async {
      final calls = _mockClipboard();
      const raw = 'key sk-ant-api03-AbCdEfGhIjKlMnOpQrSt done';
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: KitAction.copy(
            label: 'Copy message',
            text: () => raw,
            redact: false,
          ),
          tertiary: [
            KitAction(label: 'One', onPressed: () {}),
            KitAction(label: 'Two', onPressed: () {}),
            KitAction.copy(label: 'Copy reply', text: () => raw, redact: false),
          ],
        ),
      );
      await tester.tap(find.text('Copy message'));
      await tester.pump();
      expect(_clipboardText(calls), raw);
      await tester.pump(KitMotion.copiedHold);
      await tester.pumpAndSettle();

      calls.clear();
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Copy reply'));
      await tester.pumpAndSettle();
      expect(_clipboardText(calls), raw);
    });

    testWidgets('a provider key is redacted before it reaches the clipboard '
        '(G12)', (tester) async {
      final calls = _mockClipboard();
      const secret = 'sk-ant-api03-AbCdEfGhIjKlMnOpQrSt';
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: KitAction.copy(
            label: 'Copy details',
            text: () => 'key $secret done',
          ),
        ),
      );
      await tester.tap(find.text('Copy details'));
      await tester.pump();
      final copied = _clipboardText(calls)!;
      expect(copied, isNot(contains(secret)));
      expect(copied, 'key sk-ant-${KitRedact.mask} done');
      await tester.pump(KitMotion.copiedHold);
    });
  });

  group('working semantics (STATE-7)', () {
    testWidgets('working with onPressed: null renders disabled, as before', (
      tester,
    ) async {
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: const KitAction(
            label: 'Start',
            onPressed: null,
            working: true,
          ),
        ),
        reducedMotion: true,
      );
      final button = tester.widget<ButtonStyleButton>(
        find.byWidgetPredicate((w) => w is ButtonStyleButton),
      );
      expect(button.onPressed, isNull);
      expect(
        tester.getSemantics(find.text('Start')),
        isSemantics(isEnabled: false, hasEnabledState: true),
      );
    });

    testWidgets('working with a callback keeps the accent fill, ignores taps '
        'and is not announced as an enabled button', (tester) async {
      var tapped = 0;
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: KitAction(
            label: 'Start',
            onPressed: () => tapped++,
            working: true,
          ),
        ),
        reducedMotion: true,
      );
      final roles = KitTokens.of(tester.element(find.text('Start'))).roles;
      final material = tester.widget<Material>(
        find
            .descendant(
              of: find.byWidgetPredicate((w) => w is ButtonStyleButton),
              matching: find.byType(Material),
            )
            .first,
      );
      expect(material.color, roles.accent);
      expect(
        tester.getSemantics(find.byKey(const ValueKey('kit-button-working'))),
        isSemantics(
          label: 'Start',
          isButton: true,
          isEnabled: false,
          hasEnabledState: true,
        ),
      );
      await tester.tap(find.text('Start'));
      await tester.pump();
      expect(tapped, 0);
    });
  });

  group('keyboard focus ring (LOOK-21, LAY-10)', () {
    for (final (role, action) in [
      (
        'secondary',
        KitActionBlock(
          secondary: KitAction(label: 'Test', onPressed: () {}),
        ),
      ),
      (
        'tertiary',
        KitActionBlock(
          tertiary: [KitAction(label: 'Test', onPressed: () {})],
        ),
      ),
      (
        'primary',
        KitActionBlock(
          primary: KitAction(label: 'Test', onPressed: () {}),
        ),
      ),
    ]) {
      testWidgets('a $role button draws a focusRingWidth ring when focused '
          'from the keyboard', (tester) async {
        await _pumpAt(tester, action);
        BorderSide side() {
          final material = tester.widget<Material>(
            find
                .descendant(
                  of: find.byWidgetPredicate((w) => w is ButtonStyleButton),
                  matching: find.byType(Material),
                )
                .first,
          );
          return (material.shape! as OutlinedBorder).side;
        }

        expect(side(), BorderSide.none);
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pumpAndSettle();
        final context = tester.element(find.text('Test'));
        final roles = KitTokens.of(context).roles;
        expect(side().width, KitTokens.focusRingWidth(context));
        expect(side().color, role == 'primary' ? roles.onAccent : roles.accent);
      });
    }
  });

  group('shortcut window rule (test 8)', () {
    Widget block() => KitActionBlock(
      primary: const KitAction(
        label: 'Send',
        onPressed: null,
        shortcut: 'Ctrl+Enter',
      ),
    );

    testWidgets('absent at 412x915 on touch', (tester) async {
      await _pumpAt(tester, block());
      expect(find.textContaining('Ctrl+Enter'), findsNothing);
    });

    testWidgets('absent at 412x915 even with a mouse (compact window)', (
      tester,
    ) async {
      await _connectMouse(tester);
      await _pumpAt(tester, block());
      expect(find.textContaining('Ctrl+Enter'), findsNothing);
    });

    testWidgets('shown at 1280x800 with a mouse', (tester) async {
      await _connectMouse(tester);
      await _pumpAt(tester, block(), size: _large);
      expect(find.textContaining('Ctrl+Enter'), findsOneWidget);
    });
  });

  group('G6: no overflow (test 11)', () {
    const sizes = <Size>[
      Size(320, 640),
      Size(360, 740),
      Size(412, 915),
      Size(600, 960),
      Size(800, 1280),
      Size(840, 1180),
      Size(1280, 800),
      Size(1600, 1000),
      Size(915, 412),
    ];
    Widget scene(bool rtl) => ListView(
      padding: const EdgeInsets.all(16),
      children: [
        KitActionBlock(
          primary: KitAction(
            label: rtl ? 'حفظ الخادم' : 'Save the server',
            onPressed: null,
            disabledReason: rtl
                ? 'املأ عنوان الخادم أولاً قبل الحفظ.'
                : 'Fill in the server address first, then save it.',
          ),
          secondary: KitAction(
            label: rtl ? 'إلغاء' : 'Cancel',
            onPressed: () {},
          ),
          tertiary: [
            KitAction(
              label: rtl
                  ? 'نسخ عنوان الخادم مع كل التفاصيل التقنية'
                  : 'Copy the server address with every technical detail',
              onPressed: () {},
            ),
            KitAction(
              label: rtl
                  ? 'فتح إعدادات الاتصال المتقدمة لهذا الخادم'
                  : 'Open the advanced connection settings for this server',
              onPressed: null,
              disabledReason: rtl
                  ? 'غير متصل الآن.'
                  : 'Not connected right now, so settings are read-only.',
            ),
          ],
        ),
        KitActionStack(
          primary: KitAction(
            label: rtl ? 'تحديث' : 'Update the server now',
            onPressed: null,
            disabledReason: rtl
                ? 'لا يوجد تحديث.'
                : 'There is no update to install right now.',
          ),
          tertiary: [
            KitAction(
              label: rtl
                  ? 'إعادة تشغيل الخادم وكل العمليات الجارية'
                  : 'Restart the server and every running process',
              onPressed: () {},
            ),
            KitAction(
              label: rtl
                  ? 'إيقاف الخادم وكل العمليات الجارية الآن'
                  : 'Stop the server and every running process now',
              onPressed: null,
              destructive: true,
              disabledReason: rtl
                  ? 'لا شيء قيد التشغيل.'
                  : 'Nothing is running on this server.',
            ),
          ],
        ),
      ],
    );

    testWidgets('every LAY-4 size x text 1.0/1.3/2.0 x LTR/RTL', (
      tester,
    ) async {
      final failures = <String>[];
      for (final size in sizes) {
        for (final scale in [1.0, 1.3, 2.0]) {
          for (final rtl in [false, true]) {
            final where =
                '${size.width.toInt()}x${size.height.toInt()} '
                'text $scale ${rtl ? 'rtl' : 'ltr'}';
            await _pumpAt(
              tester,
              scene(rtl),
              size: size,
              textScale: scale,
              locale: Locale(rtl ? 'ar' : 'en'),
              appKey: ValueKey(where),
            );
            final error = tester.takeException();
            if (error != null) {
              failures.add('$where: ${'$error'.split('\n').first}');
            }
          }
        }
      }
      expect(failures, isEmpty);
    });
  });

  group('G8: settles after one pump under reduced motion (test 12)', () {
    final states = <String, Widget Function()>{
      'default': () => KitActionBlock(
        primary: KitAction(label: 'Save', onPressed: () {}),
        secondary: KitAction(label: 'Cancel', onPressed: () {}),
        tertiary: [KitAction(label: 'Rename', onPressed: () {})],
      ),
      'disabled': () => KitActionBlock(
        primary: const KitAction(
          label: 'Save',
          onPressed: null,
          disabledReason: 'Fill in the address first.',
        ),
      ),
      'working': () => KitActionBlock(
        primary: KitAction(label: 'Save', onPressed: () {}, working: true),
        secondary: KitAction(label: 'Test', onPressed: () {}, working: true),
      ),
      'working, no icon': () =>
          KitButton.secondary(label: 'Test', onPressed: () {}, working: true),
      'destructive': () => KitActionBlock(
        secondary: KitAction(label: 'Cancel', onPressed: () {}),
        tertiary: [
          KitAction(label: 'Delete', onPressed: () {}, destructive: true),
        ],
      ),
      'stack': () => KitActionStack(
        primary: KitAction(label: 'Update', onPressed: () {}, working: true),
        tertiary: [KitAction(label: 'Restart', onPressed: () {})],
      ),
      'shortcut': () => KitActionBlock(
        primary: KitAction(
          label: 'Send',
          onPressed: () {},
          shortcut: 'Ctrl+Enter',
        ),
      ),
    };
    for (final MapEntry(key: name, value: build) in states.entries) {
      testWidgets(name, (tester) async {
        await _pumpAt(tester, build(), reducedMotion: true, size: _large);
        await tester.pump();
        expect(tester.hasRunningAnimations, isFalse);
      });
    }

    testWidgets('copied', (tester) async {
      _mockClipboard();
      await _pumpAt(
        tester,
        KitActionBlock(
          primary: KitAction.copy(label: 'Copy details', text: () => 'x'),
        ),
        reducedMotion: true,
      );
      // Pressed without a pointer, so Material's ink ripple (the tap's own
      // feedback, not the kit's swap) is not what is measured.
      tester
          .widget<ButtonStyleButton>(
            find.byWidgetPredicate((w) => w is ButtonStyleButton),
          )
          .onPressed!();
      // The copy's clipboard and announcement futures resolve, then one
      // pump shows the swap.
      await tester.pump();
      await tester.pump();
      expect(find.text('Copied'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
      await tester.pump(KitMotion.copiedHold);
      await tester.pump();
      expect(find.text('Copy details'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  testWidgets('a compact label keeps its full target for a screen reader '
      '(semanticsLabel)', (tester) async {
    var taps = 0;
    await _pumpAt(
      tester,
      KitButton.fromAction(
        KitAction(
          label: 'Resume',
          semanticsLabel: 'Resume background connection',
          onPressed: () => taps++,
        ),
        role: KitButtonRole.tertiary,
      ),
    );
    expect(find.text('Resume'), findsOneWidget);
    final semantics = tester.ensureSemantics();
    expect(
      find.bySemanticsLabel('Resume background connection'),
      findsOneWidget,
    );
    expect(find.bySemanticsLabel('Resume'), findsNothing);
    semantics.dispose();
    await tester.tap(find.text('Resume'));
    expect(taps, 1);
  });
}
