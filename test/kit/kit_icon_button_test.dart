// Gate G9 (docs/ux-system/kit-api/KitIconButton.md; STANDARDS.md §18) for
// KitIconButton v2: the tooltip/semantic-label parity and its shortcut span
// (G14x), the KIT-22 empty-tooltip assert, the 48 dp target (G37), the
// retired `label:` forwarding (KIT-43), the disabled and working states,
// selected toggling, the destructive tint, the copy contract (G9, G12),
// reduced motion (G8x) and keyboard activation (G14).
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/no_red.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(
  Widget child, {
  Locale locale = const Locale('en'),
  bool disableAnimations = false,
  double textScale = 1,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  theme: AppTheme.dark(),
  builder: (context, widget) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      disableAnimations: disableAnimations,
      textScaler: TextScaler.linear(textScale),
    ),
    child: widget!,
  ),
  home: Scaffold(body: Center(child: child)),
);

final _button = find.byType(KitIconButton);

/// Whether keyboard focus is on [target] itself or something inside it, as
/// test/kit/kit_keyboard_test.dart's helper does for the modal parts.
bool _focusIn(WidgetTester tester, Finder target) {
  final focused = FocusManager.instance.primaryFocus?.context;
  if (focused == null) return false;
  final element = tester.element(target);
  if (focused == element) return true;
  var found = false;
  (focused as Element).visitAncestorElements((ancestor) {
    if (ancestor == element) {
      found = true;
      return false;
    }
    return true;
  });
  return found;
}

Color? _glyphColor(WidgetTester tester) => tester
    .widget<Icon>(find.descendant(of: _button, matching: find.byType(Icon)))
    .color;

bool _focusRingPainted(WidgetTester tester) => tester
    .widgetList<DecoratedBox>(
      find.descendant(of: _button, matching: find.byType(DecoratedBox)),
    )
    .any((box) => (box.decoration as BoxDecoration).border != null);

void main() {
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
    debugPlatformCapabilities = null;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, null);
    messenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      null,
    );
  });

  String? copied() {
    final call = platform.lastWhere((c) => c.method == 'Clipboard.setData');
    return (call.arguments as Map)['text'] as String?;
  }

  testWidgets(
    'the semantic label equals the tooltip; a fine pointer adds the shortcut',
    (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.retry,
            tooltip: 'Retry connection',
            shortcut: 'Ctrl+R',
            onPressed: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();

      final semantics = tester.getSemantics(_button);
      expect(semantics.label, 'Retry connection');
      expect(
        semantics,
        matchesSemantics(
          label: 'Retry connection',
          isButton: true,
          hasEnabledState: true,
          isEnabled: true,
          hasTapAction: true,
        ),
      );

      final tooltip = tester.widget<Tooltip>(find.byType(Tooltip));
      final rich = tooltip.richMessage! as TextSpan;
      final spans = rich.children!;
      expect((spans.first as TextSpan).text, 'Retry connection');
      expect((spans.first as TextSpan).text, semantics.label);
      expect((spans.last as TextSpan).text, contains('Ctrl+R'));

      // Hovering with a mouse (a fine pointer) actually shows the overlay.
      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await tester.pump();
      await gesture.moveTo(tester.getCenter(_button));
      await tester.pump(const Duration(seconds: 1));
      await tester.pump();
      expect(find.textContaining('Retry connection'), findsWidgets);
    },
  );

  testWidgets('with no touch device, the tooltip has no shortcut span', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitIconButton(
          icon: AppIconography.retry,
          tooltip: 'Retry connection',
          shortcut: 'Ctrl+R',
          onPressed: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    final tooltip = tester.widget<Tooltip>(find.byType(Tooltip));
    final rich = tooltip.richMessage! as TextSpan;
    expect(rich.children, hasLength(1));
  });

  test('an empty tooltip and no label asserts (KIT-22)', () {
    expect(
      () => KitIconButton(
        icon: AppIconography.close,
        tooltip: '',
        onPressed: () {},
      ),
      throwsAssertionError,
    );
  });

  testWidgets('the target is 48x48 at text 1.0 and 2.0, compact and large', (
    tester,
  ) async {
    for (final size in [const Size(360, 800), const Size(1600, 1000)]) {
      for (final textScale in [1.0, 2.0]) {
        tester.view
          ..physicalSize = size * tester.view.devicePixelRatio
          ..devicePixelRatio = tester.view.devicePixelRatio;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          _host(
            KitIconButton(
              icon: AppIconography.close,
              tooltip: 'Close',
              onPressed: () {},
            ),
            textScale: textScale,
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.getSize(_button), const Size(48, 48));
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      }
    }
  });

  testWidgets('label: alone still renders, and the semantic label equals it', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitIconButton(
          icon: AppIconography.close,
          label: 'Close',
          onPressed: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.getSemantics(_button).label, 'Close');
    expect(find.byTooltip('Close'), findsOneWidget);
  });

  testWidgets(
    'onPressed: null disables the button: no tap, enabled: false, the hint '
    'is the reason, the glyph is text3 at full alpha',
    (tester) async {
      // The same button, first enabled with a counter, then disabled: a tap
      // counts only while it is enabled.
      var taps = 0;
      var enabled = true;
      late StateSetter setHost;
      await tester.pumpWidget(
        _host(
          StatefulBuilder(
            builder: (context, setState) {
              setHost = setState;
              return KitIconButton(
                icon: AppIconography.delete,
                tooltip: 'Remove header',
                onPressed: enabled ? () => taps++ : null,
                disabledReason: 'Read-only connection',
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(_button);
      await tester.pump();
      expect(taps, 1);

      setHost(() => enabled = false);
      await tester.pumpAndSettle();
      await tester.tap(_button, warnIfMissed: false);
      await tester.pump();
      expect(taps, 1);
      expect(
        tester
            .widget<InkWell>(
              find.descendant(of: _button, matching: find.byType(InkWell)),
            )
            .onTap,
        isNull,
      );

      expect(
        tester.getSemantics(_button),
        matchesSemantics(
          label: 'Remove header',
          hint: 'Read-only connection',
          isButton: true,
          hasEnabledState: true,
          isEnabled: false,
        ),
      );
      final roles = ThemeRoles.resolve(AppTheme.dark());
      final color = _glyphColor(tester)!;
      expect(color, roles.text3);
      expect(color.a, 1.0);
    },
  );

  testWidgets(
    'a disabled button tooltip says why: the label span, then the reason',
    (tester) async {
      await tester.pumpWidget(
        _host(
          const KitIconButton(
            icon: AppIconography.delete,
            tooltip: 'Remove header',
            shortcut: 'Del',
            onPressed: null,
            disabledReason: 'Read-only connection',
          ),
        ),
      );
      await tester.pumpAndSettle();
      final spans =
          (tester.widget<Tooltip>(find.byType(Tooltip)).richMessage!
                  as TextSpan)
              .children!
              .cast<TextSpan>();
      expect(spans.first.text, tester.getSemantics(_button).label);
      expect(spans.last.text, 'Read-only connection');
      expect(spans.map((s) => s.text).join(), isNot(contains('Del')));

      // A long-press (the compact-window path) shows both lines.
      await tester.longPress(_button);
      await tester.pump();
      expect(find.textContaining('Read-only connection'), findsOneWidget);
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
    },
  );

  testWidgets('an enabled or working button tooltip has no reason span', (
    tester,
  ) async {
    for (final working in [false, true]) {
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.send,
            tooltip: 'Send',
            working: working,
            onPressed: () {},
            disabledReason: 'Nothing to send',
          ),
        ),
      );
      await tester.pump();
      final rich =
          tester.widget<Tooltip>(find.byType(Tooltip)).richMessage! as TextSpan;
      expect(rich.toPlainText(), 'Send', reason: 'working: $working');
    }
  });

  testWidgets(
    'at 412x915 and text 2.0 the long-press tooltip wraps to at most two '
    'lines and is not cut',
    (tester) async {
      tester.view
        ..physicalSize = const Size(412, 915) * 3
        ..devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.retry,
            tooltip: 'Retry this connection',
            onPressed: () {},
          ),
          textScale: 2,
        ),
      );
      await tester.pumpAndSettle();
      await tester.longPress(_button);
      await tester.pumpAndSettle();

      final text = find.textContaining('Retry this connection');
      expect(text, findsOneWidget);
      final paragraph = tester.renderObject<RenderParagraph>(
        find.descendant(of: text, matching: find.byType(RichText)).first,
      );
      final plain = paragraph.text.toPlainText();
      final lines = paragraph
          .getBoxesForSelection(
            TextSelection(baseOffset: 0, extentOffset: plain.length),
          )
          .map((box) => box.top.round())
          .toSet()
          .length;
      expect(lines, inInclusiveRange(1, 2));
      expect(paragraph.didExceedMaxLines, isFalse);
      final box = tester.getRect(text);
      expect(box.left, greaterThanOrEqualTo(0));
      expect(box.right, lessThanOrEqualTo(412));
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();
    },
  );

  testWidgets(
    'working: true shows the spinner, ignores taps and settles after one '
    'pump under reduced motion',
    (tester) async {
      var taps = 0;
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.send,
            tooltip: 'Send',
            working: true,
            onPressed: () => taps++,
          ),
          disableAnimations: true,
        ),
      );
      await tester.pump();
      expect(
        find.byKey(const ValueKey('kit-icon-button-working')),
        findsOneWidget,
      );
      await tester.tap(_button, warnIfMissed: false);
      await tester.pump();
      expect(taps, 0);
      expect(tester.getSemantics(_button).hint, 'Working');
      expect(tester.binding.hasScheduledFrame, isFalse);
    },
  );

  testWidgets(
    'selected true/false exposes toggled semantics; null exposes none',
    (tester) async {
      Future<void> pump(bool? selected) => tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.wrapText,
            tooltip: 'Wrap lines',
            selected: selected,
            onPressed: () {},
          ),
        ),
      );

      await pump(true);
      await tester.pumpAndSettle();
      expect(
        tester.getSemantics(_button),
        matchesSemantics(
          label: 'Wrap lines',
          isButton: true,
          hasEnabledState: true,
          isEnabled: true,
          hasTapAction: true,
          hasToggledState: true,
          isToggled: true,
        ),
      );

      await pump(false);
      await tester.pumpAndSettle();
      expect(
        tester.getSemantics(_button),
        matchesSemantics(
          label: 'Wrap lines',
          isButton: true,
          hasEnabledState: true,
          isEnabled: true,
          hasTapAction: true,
          hasToggledState: true,
          isToggled: false,
        ),
      );

      await pump(null);
      await tester.pumpAndSettle();
      expect(
        tester.getSemantics(_button),
        matchesSemantics(
          label: 'Wrap lines',
          isButton: true,
          hasEnabledState: true,
          isEnabled: true,
          hasTapAction: true,
        ),
      );
    },
  );

  testWidgets(
    'destructive keeps the glyph neutral, never danger (owner rule)',
    (tester) async {
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.delete,
            tooltip: 'Delete',
            destructive: true,
            onPressed: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(_glyphColor(tester), isNotRed);
    },
  );

  testWidgets('selected shows an accent glyph with no filled circle', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitIconButton(
          icon: AppIconography.wrapText,
          tooltip: 'Wrap lines',
          selected: true,
          onPressed: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    final roles = ThemeRoles.resolve(AppTheme.dark());
    expect(_glyphColor(tester), roles.accent);
    final fills = tester
        .widgetList<DecoratedBox>(
          find.descendant(of: _button, matching: find.byType(DecoratedBox)),
        )
        .map((box) => box.decoration)
        .whereType<BoxDecoration>()
        .map((decoration) => decoration.color)
        .whereType<Color>();
    expect(fills, isEmpty);
  });

  group('the copy contract (G9)', () {
    testWidgets(
      'copies at tap time, announces Copied once, shows no SnackBar, and '
      'the check reverts after the hold',
      (tester) async {
        var value = 'first render';
        await tester.pumpWidget(_host(KitIconButton.copy(text: () => value)));
        await tester.pumpAndSettle();
        expect(tester.getSemantics(_button).label, 'Copy');

        // The text is read at tap time, not at build time.
        value = 'second render';
        await tester.tap(_button);
        await tester.pump();

        expect(copied(), 'second render');
        expect(announcements, hasLength(1));
        expect((announcements.single['data'] as Map)['message'], 'Copied');
        expect(find.byType(SnackBar), findsNothing);
        expect(
          find.byKey(const ValueKey('kit-icon-button-copied')),
          findsOneWidget,
        );

        await tester.pump(
          KitMotion.copiedHold - const Duration(milliseconds: 1),
        );
        expect(
          find.byKey(const ValueKey('kit-icon-button-copied')),
          findsOneWidget,
        );
        await tester.pump(const Duration(milliseconds: 2));
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('kit-icon-button-copied')),
          findsNothing,
        );

        // A repeat tap copies again and restarts the hold.
        await tester.tap(_button);
        await tester.pump();
        expect(announcements, hasLength(2));
        expect(
          find.byKey(const ValueKey('kit-icon-button-copied')),
          findsOneWidget,
        );
        await tester.pump(KitMotion.copiedHold);
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('kit-icon-button-copied')),
          findsNothing,
        );
      },
    );

    testWidgets(
      'a fake provider key is redacted before it reaches the clipboard',
      (tester) async {
        const raw = 'export KEY=sk-ant-api03-AbCdEfGhIjKlMnOp';
        await tester.pumpWidget(_host(KitIconButton.copy(text: () => raw)));
        await tester.pumpAndSettle();
        await tester.tap(_button);
        await tester.pump();
        expect(copied(), 'export KEY=sk-ant-${KitRedact.mask}');
        expect(copied(), isNot(contains('AbCdEfGhIjKlMnOp')));
      },
    );

    testWidgets(
      'redact: false copies the text verbatim, fake key and all (SEC-13)',
      (tester) async {
        const raw = 'export KEY=sk-ant-api03-AbCdEfGhIjKlMnOp';
        await tester.pumpWidget(
          _host(KitIconButton.copy(text: () => raw, redact: false)),
        );
        await tester.pumpAndSettle();
        await tester.tap(_button);
        await tester.pump();
        expect(copied(), raw);
        // The copied check shows exactly as on a redacting copy.
        expect(
          find.byKey(const ValueKey('kit-icon-button-copied')),
          findsOneWidget,
        );
        await tester.pump(KitMotion.copiedHold);
        await tester.pumpAndSettle();
      },
    );
  });

  testWidgets('a working spinner draws the kit spinner stroke at any density', (
    tester,
  ) async {
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _host(
        KitIconButton(
          icon: AppIconography.retry,
          tooltip: 'Refresh',
          working: true,
          onPressed: () {},
        ),
      ),
    );
    await tester.pump();
    final spinner = tester.widget<CircularProgressIndicator>(
      find.byType(CircularProgressIndicator),
    );
    expect(spinner.strokeWidth, KitTokens.spinnerStroke);
  });

  testWidgets(
    'a long tooltip keeps a side margin at 412 dp and 2.0 text (A11Y)',
    (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      const label =
          'Retry connecting to the server on the computer in the other room';
      await tester.pumpWidget(
        _host(
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitIconButton(
              icon: AppIconography.retry,
              tooltip: label,
              onPressed: () {},
            ),
          ),
          textScale: 2,
        ),
      );
      await tester.pumpAndSettle();
      await tester.longPress(_button);
      await tester.pumpAndSettle();
      final bubble = tester.getRect(
        find
            .ancestor(
              of: find.textContaining('Retry connecting'),
              matching: find.byType(DecoratedBox),
            )
            .first,
      );
      const gutter = 16.0;
      expect(bubble.left, greaterThanOrEqualTo(gutter));
      expect(bubble.right, lessThanOrEqualTo(412 - gutter));
      // Dismiss the tooltip so no timer is left pending.
      await tester.pump(const Duration(seconds: 3));
      await tester.pumpAndSettle();
    },
  );

  group('a toggled glyph cross-fades', () {
    Widget eye(bool shown, {bool still = false}) => _host(
      KitIconButton(
        icon: shown ? AppIconography.hidden : AppIconography.visible,
        tooltip: 'Show',
        selected: shown,
        onPressed: () {},
      ),
      disableAnimations: still,
    );
    Finder glyph(IconData icon) =>
        find.descendant(of: _button, matching: find.byIcon(icon));

    testWidgets('the old glyph fades out as the new one fades in', (
      tester,
    ) async {
      await tester.pumpWidget(eye(false));
      await tester.pumpWidget(eye(true));
      await tester.pump(KitMotion.quick ~/ 2);
      expect(glyph(AppIconography.visible), findsOneWidget);
      expect(glyph(AppIconography.hidden), findsOneWidget);
      final fading = tester
          .widgetList<FadeTransition>(
            find.ancestor(
              of: glyph(AppIconography.hidden),
              matching: find.byType(FadeTransition),
            ),
          )
          .map((t) => t.opacity.value);
      expect(fading.any((o) => o > 0 && o < 1), isTrue);
      await tester.pumpAndSettle();
      expect(glyph(AppIconography.visible), findsNothing);
      expect(glyph(AppIconography.hidden), findsOneWidget);
    });

    testWidgets('under reduced motion the glyph changes at once', (
      tester,
    ) async {
      await tester.pumpWidget(eye(false, still: true));
      await tester.pumpWidget(eye(true, still: true));
      await tester.pump();
      expect(glyph(AppIconography.visible), findsNothing);
      expect(glyph(AppIconography.hidden), findsOneWidget);
    });

    testWidgets('a colour change alone eases in place, with one glyph', (
      tester,
    ) async {
      Widget pin(bool on) => _host(
        KitIconButton(
          icon: AppIconography.retry,
          tooltip: 'Pin',
          selected: on,
          onPressed: () {},
        ),
      );
      await tester.pumpWidget(pin(false));
      await tester.pumpAndSettle();
      final roles = ThemeRoles.resolve(AppTheme.dark());
      expect(_glyphColor(tester), roles.text1);
      await tester.pumpWidget(pin(true));
      await tester.pump(KitMotion.quick ~/ 2);
      expect(glyph(AppIconography.retry), findsOneWidget);
      final midway = _glyphColor(tester);
      expect(midway, isNot(roles.text1));
      expect(midway, isNot(roles.accent));
      await tester.pumpAndSettle();
      expect(_glyphColor(tester), roles.accent);
    });
  });

  group('reduced motion settles after one pump (G8x)', () {
    const iconKey = ValueKey('kit-icon-button-icon');
    const workingKey = ValueKey('kit-icon-button-working');
    const copiedKey = ValueKey('kit-icon-button-copied');

    // Each state is reached from the default look, so the glyph swap itself
    // must be instant: after one pump only the new glyph is in the tree and
    // nothing is scheduled.
    final cases = <String, (Widget, ValueKey<String>)>{
      'default': (
        KitIconButton(
          icon: AppIconography.close,
          tooltip: 'Close',
          onPressed: () {},
        ),
        iconKey,
      ),
      'disabled': (
        const KitIconButton(
          icon: AppIconography.close,
          tooltip: 'Close',
          onPressed: null,
          disabledReason: 'Not available',
        ),
        iconKey,
      ),
      'working': (
        KitIconButton(
          icon: AppIconography.close,
          tooltip: 'Close',
          working: true,
          onPressed: () {},
        ),
        workingKey,
      ),
      'selected': (
        KitIconButton(
          icon: AppIconography.close,
          tooltip: 'Close',
          selected: true,
          onPressed: () {},
        ),
        iconKey,
      ),
      'destructive': (
        KitIconButton(
          icon: AppIconography.close,
          tooltip: 'Close',
          destructive: true,
          onPressed: () {},
        ),
        iconKey,
      ),
    };

    for (final MapEntry(key: name, value: (widget, glyph)) in cases.entries) {
      testWidgets(name, (tester) async {
        // One host throughout, so only the button changes (a fresh host
        // would animate its theme).
        final shown = ValueNotifier<Widget>(cases['default']!.$1);
        addTearDown(shown.dispose);
        await tester.pumpWidget(
          _host(
            ValueListenableBuilder<Widget>(
              valueListenable: shown,
              builder: (_, child, _) => child,
            ),
            disableAnimations: true,
          ),
        );
        await tester.pump();
        expect(tester.binding.hasScheduledFrame, isFalse, reason: name);
        shown.value = widget;
        await tester.pump();
        expect(find.byKey(glyph), findsOneWidget, reason: name);
        for (final other in [iconKey, workingKey, copiedKey]) {
          if (other != glyph) {
            expect(find.byKey(other), findsNothing, reason: '$name: $other');
          }
        }
        // No animation is left running. (Turning disabled or working makes
        // the InkWell unfocusable, and the framework's Focus widget rebuilds
        // once for that: one frame of bookkeeping, not motion, so the check
        // is on tickers rather than on any scheduled frame.)
        expect(tester.binding.transientCallbackCount, 0, reason: name);
        await tester.pump();
        expect(tester.binding.hasScheduledFrame, isFalse, reason: name);
      });
    }

    testWidgets('without reduced motion the same swap cross-fades', (
      tester,
    ) async {
      final shown = ValueNotifier<Widget>(cases['default']!.$1);
      addTearDown(shown.dispose);
      await tester.pumpWidget(
        _host(
          ValueListenableBuilder<Widget>(
            valueListenable: shown,
            builder: (_, child, _) => child,
          ),
        ),
      );
      await tester.pump();
      shown.value = cases['working']!.$1;
      await tester.pump();
      expect(find.byKey(iconKey), findsOneWidget);
      expect(find.byKey(workingKey), findsOneWidget);
      expect(tester.binding.hasScheduledFrame, isTrue);
      // The spinner never settles; drop it so the test can end.
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('copied, under reduced motion', (tester) async {
      await tester.pumpWidget(
        _host(KitIconButton.copy(text: () => 'x'), disableAnimations: true),
      );
      await tester.pump();
      await tester.tap(_button);
      await tester.pump();
      expect(find.byKey(copiedKey), findsOneWidget);
      expect(find.byKey(iconKey), findsNothing);
      expect(tester.binding.hasScheduledFrame, isFalse);
      // The hold is a state timer, not an animation: it still runs.
      await tester.pump(KitMotion.copiedHold);
      await tester.pump();
      expect(find.byKey(iconKey), findsOneWidget);
      expect(find.byKey(copiedKey), findsNothing);
      expect(tester.binding.hasScheduledFrame, isFalse);
    });
  });

  testWidgets(
    'one Tab reaches it and it keeps focus: the ring shows, Enter and Space '
    'activate it',
    (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      var taps = 0;
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.check,
            tooltip: 'Approve',
            onPressed: () => taps++,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(_focusRingPainted(tester), isFalse);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, _button), isTrue);
      expect(_focusRingPainted(tester), isTrue);

      // Focus survives the rebuild that paints the ring.
      for (var i = 0; i < 3; i++) {
        await tester.pump(const Duration(milliseconds: 16));
      }
      await tester.pumpAndSettle();
      expect(_focusIn(tester, _button), isTrue);
      expect(_focusRingPainted(tester), isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(taps, 1);

      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(taps, 2);
    },
  );

  testWidgets(
    'after a focusable neighbour, exactly two Tabs reach it, and the ring '
    'goes when focus moves on',
    (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      var taps = 0;
      await tester.pumpWidget(
        _host(
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Focus(child: const SizedBox.square(dimension: 48)),
              KitIconButton(
                icon: AppIconography.check,
                tooltip: 'Approve',
                onPressed: () => taps++,
              ),
              Focus(child: const SizedBox.square(dimension: 48)),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_focusIn(tester, _button), isFalse);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.pumpAndSettle();
      expect(_focusIn(tester, _button), isTrue);
      expect(_focusRingPainted(tester), isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(taps, 1);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      expect(_focusIn(tester, _button), isFalse);
      expect(_focusRingPainted(tester), isFalse);
    },
  );

  testWidgets(
    'focused and hovered, the ring paints above the hover fill (§8.3)',
    (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: AppIconography.check,
            tooltip: 'Approve',
            onPressed: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();

      final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await gesture.addPointer(location: Offset.zero);
      addTearDown(gesture.removePointer);
      await gesture.moveTo(tester.getCenter(_button));
      await tester.pumpAndSettle();
      expect(_focusIn(tester, _button), isTrue);

      // The ring is a foreground decoration over the Material the hover ink
      // is drawn on, so it paints after (above) that ink.
      final ring = find.descendant(
        of: _button,
        matching: find.byWidgetPredicate(
          (w) =>
              w is DecoratedBox &&
              (w.decoration as BoxDecoration).border != null,
        ),
      );
      expect(ring, findsOneWidget);
      expect(
        tester.widget<DecoratedBox>(ring).position,
        DecorationPosition.foreground,
      );
      expect(
        // The press layer's clip (the ripple surface inside it has no
        // shape of its own).
        find.descendant(
          of: ring,
          matching: find.byWidgetPredicate(
            (w) => w is Material && w.shape is CircleBorder,
          ),
        ),
        findsOneWidget,
      );
    },
  );

  group('the turn motion (the Settings gear)', () {
    double turns(WidgetTester tester) => tester
        .widget<RotationTransition>(
          find.byKey(
            const ValueKey('kit-icon-button-turn'),
            skipOffstage: false,
          ),
        )
        .turns
        .value;

    Widget opener({bool reduce = false}) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: AppTheme.dark(),
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reduce),
        child: widget!,
      ),
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: KitIconButton(
              icon: Icons.settings,
              tooltip: 'Settings',
              motion: KitIconMotion.turn,
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const Scaffold(body: Text('Settings page')),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    testWidgets('turns a quarter in 200 ms as the page opens, holds while it '
        'is open, and turns back on return', (tester) async {
      await tester.pumpWidget(opener());
      expect(turns(tester), 0);
      await tester.tap(_button);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      final mid = turns(tester);
      expect(mid, greaterThan(0));
      expect(mid, lessThan(.25));
      await tester.pumpAndSettle();
      expect(find.text('Settings page'), findsOneWidget);
      expect(turns(tester), closeTo(.25, 1e-9));
      tester.state<NavigatorState>(find.byType(Navigator)).pop();
      await tester.pumpAndSettle();
      expect(turns(tester), 0);
    });

    testWidgets('with nothing opened it turns back by itself', (tester) async {
      await tester.pumpWidget(
        _host(
          KitIconButton(
            icon: Icons.settings,
            tooltip: 'Settings',
            motion: KitIconMotion.turn,
            onPressed: () {},
          ),
        ),
      );
      await tester.tap(_button);
      await tester.pumpAndSettle();
      expect(turns(tester), 0);
    });

    testWidgets('under reduced motion it does not turn', (tester) async {
      await tester.pumpWidget(opener(reduce: true));
      await tester.tap(_button);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(turns(tester), 0);
      await tester.pumpAndSettle();
      expect(find.text('Settings page'), findsOneWidget);
    });

    testWidgets('at rest nothing moves (no idle or looping animation)', (
      tester,
    ) async {
      await tester.pumpWidget(opener());
      await tester.pump(const Duration(seconds: 3));
      expect(tester.binding.hasScheduledFrame, isFalse);
      expect(turns(tester), 0);
    });

    testWidgets('the shell bar gear is 24 and search 22, each on a round '
        '48 dp surface', (tester) async {
      await tester.pumpWidget(
        _host(
          SizedBox(
            width: 400,
            child: KitShellControls(
              server: 'Laptop',
              serverStatus: 'Connected',
              onServer: () {},
              onSearch: () {},
              onSettings: () {},
              searchKey: const ValueKey('search'),
              settingsKey: const ValueKey('gear'),
            ),
          ),
        ),
      );
      final gear = tester.widget<KitIconButton>(
        find.byKey(const ValueKey('gear')),
      );
      final search = tester.widget<KitIconButton>(
        find.byKey(const ValueKey('search')),
      );
      expect(
        (gear.size, gear.round, gear.motion),
        (24, true, KitIconMotion.turn),
      );
      expect((search.size, search.round), (22, true));
      for (final key in ['gear', 'search']) {
        final round = find.descendant(
          of: find.byKey(ValueKey(key)),
          matching: find.byKey(const ValueKey('kit-icon-button-round')),
        );
        expect(round, findsOneWidget);
        expect(tester.getSize(round), const Size(48, 48));
      }
    });
  });

  group('round (the shell bar\'s search and gear)', () {
    Widget round(KitSurfaceStyle style, {bool isRound = true}) => _host(
      KitEffectsScope(
        effects: const KitEffects().withSurfaceStyle(style),
        child: KitIconButton(
          icon: Icons.search,
          tooltip: 'Search',
          round: isRound,
          onPressed: () {},
        ),
      ),
    );

    Iterable<BoxDecoration> circles(WidgetTester tester) => tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byKey(const ValueKey('kit-icon-button-round')),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map((box) => box.decoration)
        .whereType<BoxDecoration>()
        .where((d) => d.shape == BoxShape.circle && d.color != null);

    testWidgets('Solid light: the one quiet drop, black .10, y 4, blur 12', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Center(
              child: KitIconButton(
                icon: Icons.search,
                tooltip: 'Search',
                round: true,
                onPressed: () {},
              ),
            ),
          ),
        ),
      );
      final shadow = circles(tester).single.boxShadow!.single;
      expect(shadow.color.a, closeTo(.10, .005));
      expect(shadow.offset, const Offset(0, 4));
      expect(shadow.blurRadius, 12);
      expect(shadow.spreadRadius, -4);
    });

    testWidgets('a plain button draws no circle of its own', (tester) async {
      await tester.pumpWidget(round(KitSurfaceStyle.solid, isRound: false));
      expect(find.byKey(const ValueKey('kit-icon-button-round')), findsNothing);
    });

    testWidgets('Solid: a surface2 circle with the hairline, no shadow in '
        'dark, outside the press clip, the full 48', (tester) async {
      await tester.pumpWidget(round(KitSurfaceStyle.solid));
      final surface = find.byKey(const ValueKey('kit-icon-button-round'));
      expect(tester.getSize(surface), const Size(48, 48));
      final roles = KitTokens.of(
        tester.element(find.byType(KitIconButton)),
      ).roles;
      final circle = circles(tester).single;
      expect(circle.color, roles.surface2);
      expect(circle.border, isNotNull);
      // Unslop 2026-10-30: in dark surface2 and the hairline lift it; the
      // quiet drop (black .10) is light mode's only.
      expect(circle.boxShadow ?? const <BoxShadow>[], isEmpty);
      // The press layer's clip is inside the circle, so a shadow is whole.
      expect(
        // The press layer's clip (the ripple surface inside it has no
        // shape of its own).
        find.descendant(
          of: surface,
          matching: find.byWidgetPredicate(
            (w) => w is Material && w.shape is CircleBorder,
          ),
        ),
        findsOneWidget,
      );
    });

    for (final style in KitSurfaceStyle.values) {
      testWidgets('${style.name}: the style draws a resting circle', (
        tester,
      ) async {
        await tester.pumpWidget(round(style));
        expect(tester.takeException(), isNull);
        expect(
          find.byKey(const ValueKey('kit-icon-button-round')),
          findsOneWidget,
        );
        await tester.tap(find.byType(KitIconButton));
        await tester.pump();
      });
    }
  });
}
