// KitTerminalView and TerminalKeyBar (docs/ux-system/kit-api/KitTerminalView.md,
// "Tests required" 1-15): key size and latch, the new Ctrl-C and Ctrl-D
// keys, no haptics, disabled keys, application cursor mode, live focus and
// read-only input, the palette and the painted colours, the output form's
// tail, cap and redaction, tints, right-to-left, the TerminalView wrapper,
// reduced motion and the overflow matrix.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/kit/kit_terminal_view.dart';
import 'package:opencode_mobile/ui/kit/terminal_key_bar.dart';
import 'package:opencode_mobile/ui/widgets/terminal_view.dart';
import 'package:xterm/xterm.dart' as xterm;

import 'kit_harness.dart' show recordHaptics;
import 'kit_motion_still.dart';

/// Runs [body] as Android (the spec's platform), clearing the override
/// before flutter_test checks it.
void _test(String description, WidgetTesterCallback body) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

/// Pumps [child] as a screen's body at [size] (DPR 1).
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
  Locale locale = const Locale('en'),
  double textScale = 1,
  bool light = false,
  double keyboard = 0,
}) async {
  tester.view
    ..physicalSize = size
    ..devicePixelRatio = 1
    ..viewInsets = FakeViewPadding(bottom: keyboard);
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: light ? AppTheme.light() : AppTheme.dark(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: Scaffold(body: child),
    ),
  );
  await tester.pump();
}

/// A bar at the bottom of the screen, recording what it sends.
Future<List<(TerminalBarKey, bool, bool)>> _bar(
  WidgetTester tester, {
  TerminalKeyBarController? controller,
  Size size = const Size(412, 915),
  bool compact = false,
  bool interruptKeys = false,
  bool enabled = true,
  String? disabledReason,
}) async {
  final pressed = <(TerminalBarKey, bool, bool)>[];
  await _pump(
    tester,
    Align(
      alignment: Alignment.bottomCenter,
      child: TerminalKeyBar(
        controller: controller ?? TerminalKeyBarController(),
        compact: compact,
        interruptKeys: interruptKeys,
        enabled: enabled,
        disabledReason: disabledReason,
        onKey: (key, {required ctrl, required alt}) =>
            pressed.add((key, ctrl, alt)),
      ),
    ),
    size: size,
  );
  return pressed;
}

Finder _key(TerminalBarKey key) =>
    find.byKey(ValueKey('terminal-key-${key.name}'));

/// The keys a bar shows.
List<TerminalBarKey> _shown({bool interruptKeys = false}) => [
  if (interruptKeys) ...TerminalBarKey.extraRow,
  ...TerminalBarKey.rows.expand((row) => row),
];

/// The fixed ANSI sample the live scenes show (TEST-11: no clock, no
/// process): a prompt, `ls --color`, a red error line, a green pass line.
const _sample =
    '\x1b[32mdev@build-server\x1b[0m:\x1b[34m~/app\x1b[0m\$ ls --color\r\n'
    '\x1b[34mlib\x1b[0m  \x1b[34mtest\x1b[0m  \x1b[32mbuild.sh\x1b[0m  '
    'pubspec.yaml\r\n'
    '\x1b[32mdev@build-server\x1b[0m:\x1b[34m~/app\x1b[0m\$ flutter test\r\n'
    '\x1b[31merror: test/app_test.dart:12:3: Expected a value\x1b[0m\r\n'
    '\x1b[32m00:06 +42: All tests passed!\x1b[0m\r\n'
    '\x1b[32mdev@build-server\x1b[0m:\x1b[34m~/app\x1b[0m\$ ';

/// A live view of a fake terminal whose output lands in [sent].
({xterm.Terminal terminal, List<String> sent}) _terminal() {
  final sent = <String>[];
  final terminal = xterm.Terminal(onOutput: sent.add)..write(_sample);
  return (terminal: terminal, sent: sent);
}

String _lines(int count) =>
    [for (var i = 1; i <= count; i++) 'line $i'].join('\n');

/// Every text span the output block draws, with its resolved style.
List<(String, TextStyle?)> _spans(WidgetTester tester) {
  final text = tester.widget<SelectableText>(find.byType(SelectableText));
  final out = <(String, TextStyle?)>[];
  void walk(InlineSpan span, TextStyle? inherited) {
    if (span is! TextSpan) return;
    final style = inherited?.merge(span.style) ?? span.style;
    if (span.text case final text? when text.isNotEmpty) {
      out.add((text, style));
    }
    for (final child in span.children ?? const <InlineSpan>[]) {
      walk(child, style);
    }
  }

  walk(text.textSpan!, null);
  return out;
}

String _drawn(WidgetTester tester) =>
    _spans(tester).map((span) => span.$1).join();

/// Every role colour of [roles], plus the terminal's selection, as ARGB
/// (a painted colour may pass through 8-bit channels on its way).
Set<int> _roleColors(ThemeRoles roles) => {
  for (final color in _roleColorList(roles)) color.toARGB32(),
};

List<Color> _roleColorList(ThemeRoles roles) => [
  roles.ground,
  roles.surface1,
  roles.surface2,
  roles.surface3,
  roles.hairline,
  roles.text1,
  roles.text2,
  roles.text3,
  roles.accent,
  roles.onAccent,
  roles.attention,
  roles.attentionFill,
  roles.onAttentionFill,
  roles.attentionSurface,
  roles.attentionLine,
  roles.danger,
  roles.dangerFill,
  roles.onDangerFill,
  roles.success,
  roles.scrim,
  roles.codeKeyword,
  roles.codeString,
  roles.codeType,
  roles.elevationShadow,
  KitTerminalView.themeOf(roles).selection,
  Colors.transparent,
];

/// The colours the frame paints (as ARGB): every fill and stroke drawn on
/// the canvas, and every text colour laid out.
Set<int> _paintedColors(WidgetTester tester) {
  final colors = <int>{};
  final canvas = TestRecordingCanvas();
  final context = TestRecordingPaintingContext(canvas);
  tester.binding.renderViews.single.paint(context, Offset.zero);
  context.dispose();
  for (final call in canvas.invocations) {
    if (!call.invocation.memberName.toString().contains('draw')) continue;
    for (final argument in call.invocation.positionalArguments) {
      if (argument is Paint) colors.add(argument.color.toARGB32());
    }
  }
  void spanColors(InlineSpan span) => span.visitChildren((child) {
    if (child is TextSpan && child.style?.color != null) {
      colors.add(child.style!.color!.toARGB32());
    }
    return true;
  });
  for (final object in tester.allRenderObjects) {
    if (object is RenderParagraph) spanColors(object.text);
    if (object is RenderEditable) {
      if (object.text case final text?) spanColors(text);
    }
  }
  return colors;
}

void main() {
  group('the key bar', () {
    for (final (name, compact, interrupt) in [
      ('two rows', false, false),
      ('compact', true, false),
      ('with Ctrl-C and Ctrl-D', false, true),
    ]) {
      _test('1. $name: every key is 48 x 48, apart, reachable at 320 dp', (
        tester,
      ) async {
        await _bar(
          tester,
          size: const Size(320, 640),
          compact: compact,
          interruptKeys: interrupt,
        );
        // In the bar's own order, so a lazy compact row scrolls forward.
        final keys = compact
            ? const [
                TerminalBarKey.esc,
                TerminalBarKey.ctrl,
                TerminalBarKey.alt,
                TerminalBarKey.tab,
                TerminalBarKey.left,
                TerminalBarKey.up,
                TerminalBarKey.down,
                TerminalBarKey.right,
                TerminalBarKey.slash,
                TerminalBarKey.dash,
                TerminalBarKey.pipe,
                TerminalBarKey.tilde,
                TerminalBarKey.home,
                TerminalBarKey.end,
                TerminalBarKey.pageUp,
                TerminalBarKey.pageDown,
              ]
            : _shown(interruptKeys: interrupt);
        expect(keys.toSet(), _shown(interruptKeys: interrupt).toSet());
        // The keys built now (a compact row builds lazily) never overlap.
        final rects = {
          for (final key in keys)
            if (_key(key).evaluate().isNotEmpty) key: tester.getRect(_key(key)),
        };
        expect(rects.length, greaterThan(4));
        for (final MapEntry(key: key, value: rect) in rects.entries) {
          expect(rect.width, greaterThanOrEqualTo(48), reason: key.name);
          expect(rect.height, greaterThanOrEqualTo(48), reason: key.name);
          for (final MapEntry(key: other, value: next) in rects.entries) {
            if (other == key) continue;
            final overlap = rect.intersect(next);
            expect(
              overlap.width > .01 && overlap.height > .01,
              isFalse,
              reason: '${key.name} overlaps ${other.name}',
            );
          }
        }
        for (final key in keys) {
          if (_key(key).evaluate().isEmpty) {
            await tester.scrollUntilVisible(
              _key(key),
              48,
              scrollable: find.byType(Scrollable).first,
            );
          }
          await tester.ensureVisible(_key(key));
          await tester.pump();
          final size = tester.getSize(_key(key));
          expect(size.width, greaterThanOrEqualTo(48), reason: key.name);
          expect(size.height, greaterThanOrEqualTo(48), reason: key.name);
          expect(_key(key).hitTestable(), findsOneWidget, reason: key.name);
        }
        expect(tester.takeException(), isNull);
      });
    }

    _test('2. Ctrl latches for one key, with toggled semantics', (
      tester,
    ) async {
      final keys = TerminalKeyBarController();
      final pressed = await _bar(tester, controller: keys);
      await tester.tap(_key(TerminalBarKey.ctrl));
      await tester.pump();
      expect(
        tester.getSemantics(_key(TerminalBarKey.ctrl)),
        isSemantics(label: 'Control', isButton: true, isToggled: true),
      );
      expect(keys.apply('c'), '\x03');
      await tester.pump();
      expect(
        tester.getSemantics(_key(TerminalBarKey.ctrl)),
        isSemantics(isToggled: false),
      );

      await tester.tap(_key(TerminalBarKey.ctrl));
      await tester.tap(_key(TerminalBarKey.left));
      await tester.tap(_key(TerminalBarKey.left));
      await tester.pump();
      expect(pressed, [
        (TerminalBarKey.left, true, false),
        (TerminalBarKey.left, false, false),
      ]);
      expect(keys.ctrl, isFalse);
    });

    test('3. Ctrl-C and Ctrl-D reach the terminal as 0x03 and 0x04', () {
      final sent = <String>[];
      final terminal = xterm.Terminal(onOutput: sent.add);
      sendTerminalBarKey(terminal, TerminalBarKey.interrupt);
      sendTerminalBarKey(terminal, TerminalBarKey.endOfInput);
      sendTerminalBarKey(terminal, TerminalBarKey.interrupt, alt: true);
      expect(sent, ['\x03', '\x04', '\x1b\x03']);
    });

    _test('3. a server bar leads with Ctrl-C, which sends 0x03', (
      tester,
    ) async {
      final live = _terminal();
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal · build-server',
          keys: TerminalKeyBarController(),
          interruptKeys: true,
        ),
      );
      final interrupt = tester.getRect(_key(TerminalBarKey.interrupt));
      expect(
        interrupt.top,
        lessThan(tester.getRect(_key(TerminalBarKey.esc)).top),
      );
      expect(
        interrupt.left,
        lessThanOrEqualTo(tester.getRect(_key(TerminalBarKey.esc)).left),
      );
      await tester.tap(_key(TerminalBarKey.interrupt));
      await tester.tap(_key(TerminalBarKey.endOfInput));
      await tester.pump();
      expect(live.sent, ['\x03', '\x04']);
      expect(
        tester.getSemantics(_key(TerminalBarKey.interrupt)),
        isSemantics(label: 'Interrupt, Control C', isButton: true),
      );
    });

    _test('4. no key vibrates', (tester) async {
      final haptics = recordHaptics(tester);
      await _bar(tester, interruptKeys: true);
      for (final key in _shown(interruptKeys: true)) {
        await tester.tap(_key(key));
        await tester.pump();
      }
      expect(haptics, isEmpty);
    });

    _test('5. disabled keys ignore taps and say why', (tester) async {
      final pressed = await _bar(tester, enabled: false);
      await tester.tap(_key(TerminalBarKey.esc), warnIfMissed: false);
      await tester.tap(_key(TerminalBarKey.ctrl), warnIfMissed: false);
      await tester.pump();
      expect(pressed, isEmpty);
      expect(
        tester.getSemantics(_key(TerminalBarKey.esc)),
        isSemantics(
          isButton: true,
          hasEnabledState: true,
          isEnabled: false,
          hint: 'Unavailable while the terminal is disconnected',
        ),
      );

      await _bar(tester, enabled: false, disabledReason: 'Not connected');
      expect(
        tester.getSemantics(_key(TerminalBarKey.up)),
        isSemantics(isEnabled: false, hint: 'Not connected'),
      );
    });

    _test('6. in application cursor mode the up arrow sends ESC O A', (
      tester,
    ) async {
      final live = _terminal();
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal',
          keys: TerminalKeyBarController(),
        ),
      );
      await tester.tap(_key(TerminalBarKey.up));
      live.terminal.write('\x1b[?1h');
      await tester.tap(_key(TerminalBarKey.up));
      await tester.pump();
      expect(live.sent, ['\x1b[A', '\x1bOA']);
    });
  });

  group('the live view', () {
    _test('7. Tab reaches the shell; Ctrl+Tab leaves the terminal', (
      tester,
    ) async {
      debugPlatformCapabilities = PlatformCapabilities(
        platform: TargetPlatform.linux,
      );
      addTearDown(() => debugPlatformCapabilities = null);
      final live = _terminal();
      final focus = FocusNode();
      addTearDown(focus.dispose);
      await _pump(
        tester,
        Column(
          children: [
            TextButton(onPressed: () {}, child: const Text('Before')),
            Expanded(
              child: KitTerminalView.live(
                terminal: live.terminal,
                semanticsLabel: 'Terminal',
                focusNode: focus,
              ),
            ),
            TextButton(onPressed: () {}, child: const Text('After')),
          ],
        ),
        size: const Size(800, 600),
      );
      expect(focus.hasFocus, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      expect(live.sent, ['\t']);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pump();
      expect(focus.hasFocus, isFalse);
      expect(live.sent, ['\t'], reason: 'Ctrl+Tab is not typed');
      String focusedButton() {
        final focused = FocusManager.instance.primaryFocus!.context!;
        final button =
            focused.findAncestorWidgetOfExactType<TextButton>()!.child! as Text;
        return button.data!;
      }

      expect(focusedButton(), 'After');

      // Shift+Tab from the next control comes back to the terminal.
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await tester.pump();
      expect(focus.hasFocus, isTrue);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pump();
      expect(focus.hasFocus, isFalse);
      expect(focusedButton(), 'Before');
      expect(live.sent, ['\t']);
    });

    _test('7. a PC window: hardware keys only, and no key bar', (tester) async {
      debugPlatformCapabilities = PlatformCapabilities(
        platform: TargetPlatform.linux,
      );
      addTearDown(() => debugPlatformCapabilities = null);
      final live = _terminal();
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal',
          keys: TerminalKeyBarController(),
        ),
        size: const Size(1280, 800),
      );
      expect(find.byType(TerminalKeyBar), findsNothing);
      expect(
        tester
            .widget<xterm.TerminalView>(find.byType(xterm.TerminalView))
            .hardwareKeyboardOnly,
        isTrue,
      );
    });

    _test('adaptive: the bar is capped and centred on a medium window', (
      tester,
    ) async {
      final live = _terminal();
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal',
          keys: TerminalKeyBarController(),
        ),
        size: const Size(800, 1280),
      );
      final first = tester.getRect(_key(TerminalBarKey.esc));
      final last = tester.getRect(_key(TerminalBarKey.pageUp));
      // 720 dp of room: eight keys of a whole 86 dp (not 86.5) and seven
      // 4 dp gaps, centred on a whole pixel (LOOK-21).
      expect(first.width, 86);
      expect(last.right - first.left, 716);
      expect(first.left, 42);
      // At DPR 3 each cap edge still lands on a whole physical pixel.
      tester.view
        ..devicePixelRatio = 3
        ..physicalSize = const Size(430 * 3, 915 * 3);
      await tester.pump();
      for (final key in TerminalBarKey.rows.first) {
        final rect = tester.getRect(_key(key));
        for (final edge in [rect.left, rect.right]) {
          expect(edge * 3, moreOrLessEquals((edge * 3).roundToDouble()));
        }
      }
      expect(
        tester
            .widget<xterm.TerminalView>(find.byType(xterm.TerminalView))
            .hardwareKeyboardOnly,
        isFalse,
      );
    });

    _test('adaptive: one row only when the room above the keyboard is short', (
      tester,
    ) async {
      final live = _terminal();
      Widget view() => KitTerminalView.live(
        terminal: live.terminal,
        semanticsLabel: 'Terminal',
        keys: TerminalKeyBarController(),
        keysKey: const ValueKey('local-terminal-keys'),
      );
      bool oneRow() =>
          tester.getRect(_key(TerminalBarKey.esc)).top ==
          tester.getRect(_key(TerminalBarKey.ctrl)).top;

      await _pump(tester, view(), keyboard: 300);
      expect(oneRow(), isFalse, reason: '615 dp left: two rows');
      await _pump(tester, view(), size: const Size(915, 412));
      expect(oneRow(), isTrue);
      await _pump(tester, view(), size: const Size(412, 700), keyboard: 300);
      expect(oneRow(), isTrue, reason: '400 dp left: one row');
      expect(
        tester.widget<TerminalKeyBar>(find.byType(TerminalKeyBar)).key,
        const ValueKey('local-terminal-keys'),
      );
    });

    _test('8. read-only drops typed input and disables the keys', (
      tester,
    ) async {
      final live = _terminal();
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal',
          keys: TerminalKeyBarController(),
          readOnly: true,
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.sendKeyEvent(LogicalKeyboardKey.keyA);
      await tester.tap(_key(TerminalBarKey.esc), warnIfMissed: false);
      await tester.pump();
      expect(live.sent, isEmpty);
      expect(
        tester.getSemantics(_key(TerminalBarKey.esc)),
        isSemantics(isEnabled: false),
      );

      // The same keys reach a writable terminal.
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal',
          keys: TerminalKeyBarController(),
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.tap(_key(TerminalBarKey.esc));
      await tester.pump();
      expect(live.sent, ['\r', '\x1b']);
    });

    _test('the view is one labelled node; the grid is not read', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final live = _terminal();
      await _pump(
        tester,
        KitTerminalView.live(
          terminal: live.terminal,
          semanticsLabel: 'Terminal · build-server',
          keys: TerminalKeyBarController(),
        ),
      );
      expect(find.bySemanticsLabel('Terminal · build-server'), findsOneWidget);
      expect(find.bySemanticsLabel('Terminal keys'), findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('dev@build-server')), findsNothing);
      semantics.dispose();
    });

    test('selectedText returns only the selection', () {
      final terminal = xterm.Terminal()..write('hello world\r\nsecond');
      final controller = xterm.TerminalController();
      expect(KitTerminalView.selectedText(terminal, controller), '');
      controller.setSelection(
        terminal.buffer.createAnchor(0, 0),
        terminal.buffer.createAnchor(5, 0),
      );
      expect(KitTerminalView.selectedText(terminal, controller), 'hello');
    });
  });

  group('9. the palette', () {
    for (final (name, roles) in [
      ('dark', graphiteDark),
      ('light', graphiteLight),
    ]) {
      test('themeOf($name) uses the roles', () {
        final theme = KitTerminalView.themeOf(roles);
        expect(theme.background, roles.ground);
        expect(theme.foreground, roles.text1);
        expect(theme.cursor, roles.accent);
        expect(theme.red, roles.danger);
        expect(theme.green, roles.success);
        expect(theme.black, roles.surface3);
        expect(theme.brightBlack, roles.text3);
        expect(theme.brightWhite, roles.text1);
        expect(theme.brightRed, theme.red);
      });
    }

    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      _test('the live scene paints only role colours ($mode)', (tester) async {
        final live = _terminal();
        final keys = TerminalKeyBarController()..toggle(TerminalBarKey.ctrl);
        await _pump(
          tester,
          KitTerminalView.live(
            terminal: live.terminal,
            semanticsLabel: 'Terminal',
            keys: keys,
            interruptKeys: true,
          ),
          light: light,
        );
        final roles = ThemeRoles.resolve(
          Theme.of(tester.element(find.byType(KitTerminalView))),
        );
        final painted = _paintedColors(tester);
        expect(painted, contains(roles.ground.toARGB32()));
        expect(painted, contains(roles.surface2.toARGB32()));
        expect(painted, contains(roles.accent.toARGB32()));
        expect(painted.difference(_roleColors(roles)), isEmpty);
      });

      _test('the output scene paints only role colours ($mode)', (
        tester,
      ) async {
        await _pump(
          tester,
          SingleChildScrollView(
            child: KitTerminalView.output(
              command: 'FOO=1 /usr/bin/flutter test -j 1 "a b" | tee log',
              output:
                  '${_lines(12)}\n\x1b[31mred\x1b[0m \x1b[2mfaint\x1b[0m\n'
                  'error: boom\nwarning: slow\n00:07 +6 ~1 -2: x\nAll tests passed!',
              tailLines: 8,
            ),
          ),
          light: light,
        );
        final roles = ThemeRoles.resolve(
          Theme.of(tester.element(find.byType(KitTerminalView))),
        );
        final painted = _paintedColors(tester);
        expect(painted, contains(roles.codeType.toARGB32()));
        expect(painted.difference(_roleColors(roles)), isEmpty);
      });
    }
  });

  group('the output form', () {
    _test('10. the tail first, the rest a tap away', (tester) async {
      await _pump(
        tester,
        SingleChildScrollView(
          child: KitTerminalView.output(output: _lines(50), tailLines: 40),
        ),
      );
      expect(_drawn(tester), isNot(contains('line 10\n')));
      expect(_drawn(tester), contains('line 11\n'));
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('terminal-show-earlier')),
          matching: find.text('Show 10 earlier lines'),
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('terminal-show-earlier')));
      await tester.pump();
      expect(_drawn(tester), startsWith('line 1\n'));
      expect(_drawn(tester), endsWith('line 50'));
      expect(find.byKey(const ValueKey('terminal-show-earlier')), findsNothing);
      expect(find.byKey(const ValueKey('terminal-open-full')), findsNothing);
    });

    _test('10. too long: "Open all" hands over the whole, ANSI stripped', (
      tester,
    ) async {
      final opened = <String>[];
      await _pump(
        tester,
        SingleChildScrollView(
          child: KitTerminalView.output(
            output: '\x1b[31mfirst\x1b[0m\n${_lines(2499)}',
            onOpenFull: opened.add,
          ),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('terminal-show-earlier')));
      await tester.pump();
      // Only the last 2,000 lines inline.
      expect(_drawn(tester), startsWith('line 500\n'));
      final open = find.byKey(const ValueKey('terminal-open-full'));
      expect(
        find.descendant(of: open, matching: find.text('Open all 2,500 lines')),
        findsOneWidget,
      );
      await tester.ensureVisible(open);
      await tester.tap(open);
      await tester.pump();
      expect(opened, hasLength(1));
      expect(opened.single, startsWith('first\nline 1\n'));
      expect(opened.single, isNot(contains('\x1b')));
      expect(find.text('Showing the last 2,000 lines'), findsNothing);
    });

    _test('10. too long with nowhere to open: it says what it shows', (
      tester,
    ) async {
      await _pump(
        tester,
        SingleChildScrollView(
          child: KitTerminalView.output(output: _lines(2500), tailLines: 40),
        ),
      );
      // The button counts what a tap reveals: from the last 40 back to the
      // last 2,000, not all 2,460 hidden lines (COPY-17).
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('terminal-show-earlier')),
          matching: find.text('Show 1,960 earlier lines'),
        ),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('terminal-show-earlier')));
      await tester.pump();
      expect(_drawn(tester), startsWith('line 501\n'));
      expect(find.text('Showing the last 2,000 lines'), findsOneWidget);
      expect(find.byKey(const ValueKey('terminal-open-full')), findsNothing);
      expect(find.byType(TextButton), findsNothing);
    });

    _test('empty: only the command, or nothing at all', (tester) async {
      await _pump(
        tester,
        const KitTerminalView.output(output: '', command: 'true'),
      );
      expect(_drawn(tester), r'$ true');
      await _pump(tester, const KitTerminalView.output(output: ''));
      expect(find.byType(SelectableText), findsNothing);
      expect(find.byKey(const ValueKey('terminal-view')), findsNothing);
    });

    _test('11. secrets are masked on screen and in the full text', (
      tester,
    ) async {
      const key = 'sk-ant-api03-Zx9Qw8Er7Ty6Ui5Op4As3Df2';
      const token = 'eyTokenValue0123456789';
      final opened = <String>[];
      await _pump(
        tester,
        SingleChildScrollView(
          child: KitTerminalView.output(
            command: 'curl -H "Authorization: Bearer $token" api',
            output:
                '${_lines(2500)}\n'
                'using $key\n\x1b[33mAuthorization: Bearer $token\x1b[0m\n'
                // jq -C and httpie colour a name apart from its value.
                '\x1b[34;1m"password"\x1b[0m:\x1b[0;32m"hunter2"\x1b[0m\n'
                'curl: Bearer \x1b[1m$token\x1b[0m',
            tailLines: 2600,
            onOpenFull: opened.add,
          ),
        ),
      );
      final drawn = _drawn(tester);
      expect(drawn, isNot(contains('Zx9Qw8Er7Ty6')));
      expect(drawn, isNot(contains(token)));
      expect(drawn, isNot(contains('hunter2')));
      expect(drawn, contains('"password":"${KitRedact.mask}'));
      expect(drawn, contains('Bearer ${KitRedact.mask}'));
      expect(drawn, contains(KitRedact.mask));
      final open = find.byKey(const ValueKey('terminal-open-full'));
      await tester.ensureVisible(open);
      await tester.tap(open);
      expect(opened.single, isNot(contains('Zx9Qw8Er7Ty6')));
      expect(opened.single, isNot(contains(token)));
      expect(opened.single, contains('sk-ant-${KitRedact.mask}'));
      expect(opened.single, contains('Authorization: ${KitRedact.mask}'));
      expect(opened.single, isNot(contains('hunter2')));
    });

    test('11. a value coloured apart from its name is still masked', () {
      String drawn(String line) => KitTerminalText.line(
        line,
        graphiteDark,
      ).whereType<TextSpan>().map((span) => span.text ?? '').join();
      const jq = '\x1b[34;1m"password"\x1b[0m:\x1b[0;32m"hunter2"\x1b[0m';
      expect(drawn(jq), isNot(contains('hunter2')));
      expect(drawn(jq), '"password":"${KitRedact.mask}"');
      expect(
        drawn('Bearer \x1b[1mTOKEN123\x1b[0m'),
        'Bearer ${KitRedact.mask}',
      );
      expect(
        drawn('\x1b[1mBearer\x1b[0m TOKEN123'),
        'Bearer ${KitRedact.mask}',
      );
      // A line with no secret keeps its colours.
      expect(
        (KitTerminalText.line('\x1b[31mred\x1b[0m', graphiteDark).first
                as TextSpan)
            .style
            ?.color,
        graphiteDark.danger,
      );
    });

    test('12. tints: errors text1 semibold, warnings not amber, ANSI red', () {
      for (final roles in [graphiteDark, graphiteLight]) {
        TextStyle? styleOf(String line) =>
            (KitTerminalText.line(line, roles).first as TextSpan).style;
        final error = styleOf('error: something broke');
        expect(error?.color, roles.text1);
        expect(error?.fontWeight, FontWeight.w600);
        expect(error?.color, isNot(roles.danger));
        final warning = styleOf('warning: deprecated call');
        expect(warning?.color, isNot(roles.attention));
        expect(warning?.color, roles.text2);
        expect(styleOf('\x1b[31mred\x1b[0m')?.color, roles.danger);
        expect(styleOf('All tests passed!')?.color, roles.success);
        expect(styleOf('Found 0 errors')?.color, isNull);
        final progress = KitTerminalText.line('00:07 +6 ~1 -2: x', roles);
        TextStyle? part(String text) => progress
            .whereType<TextSpan>()
            .firstWhere((span) => span.text == text)
            .style;
        expect(part('+6')?.color, roles.success);
        expect(part(' ~1')?.color, roles.text2);
        expect(part(' -2')?.color, roles.text1);
        expect(part(' -2')?.fontWeight, FontWeight.w600);
      }
    });

    test('the command: program, folder, flags, strings, operators', () {
      final roles = graphiteDark;
      final spans = KitTerminalText.command(
        'FOO=1 /usr/bin/git log --oneline "a b" | head',
        roles,
      ).whereType<TextSpan>().toList();
      Color? colorOf(String text) =>
          spans.firstWhere((span) => span.text == text).style?.color;
      expect(colorOf(r'$ '), roles.text3);
      expect(colorOf('FOO=1'), roles.text3);
      expect(colorOf('/usr/bin/'), roles.text3);
      expect(colorOf('git'), roles.accent);
      expect(colorOf('--oneline'), roles.codeType);
      expect(colorOf('"a b"'), roles.codeString);
      expect(colorOf('|'), roles.text3);
      expect(colorOf('head'), roles.accent);
      expect(KitTerminalText.strip('\x1b[1;31mx\x1b[0m'), 'x');
    });

    _test('13. in Arabic the block stays left to right, buttons at start', (
      tester,
    ) async {
      await _pump(
        tester,
        SingleChildScrollView(
          child: KitTerminalView.output(output: _lines(50), tailLines: 40),
        ),
        locale: const Locale('ar'),
      );
      final editable = tester.allRenderObjects
          .whereType<RenderEditable>()
          .single;
      expect(editable.textDirection, TextDirection.ltr);
      final block = tester.getRect(find.byKey(const ValueKey('terminal-view')));
      // Left-aligned: the first glyph sits at the block's left padding.
      final caret = editable.localToGlobal(
        editable.getLocalRectForCaret(const TextPosition(offset: 0)).topLeft,
      );
      expect(caret.dx, closeTo(block.left + 8, 1));
      final earlier = tester.getRect(
        find.byKey(const ValueKey('terminal-show-earlier')),
      );
      expect(earlier.right, closeTo(block.right - 8, 1));
    });

    _test('14. the old TerminalView forwards to KitTerminalView.output', (
      tester,
    ) async {
      await _pump(
        tester,
        const SingleChildScrollView(
          child: TerminalView(
            command: 'apt-get install -y proot',
            output: 'Reading package lists...\nDone',
            tailLines: 1,
            framed: false,
            wrap: false,
          ),
        ),
      );
      final kit = tester.widget<KitTerminalView>(find.byType(KitTerminalView));
      expect(kit.command, 'apt-get install -y proot');
      expect(kit.output, 'Reading package lists...\nDone');
      expect(kit.tailLines, 1);
      expect(kit.framed, isFalse);
      expect(kit.wrap, isFalse);
      expect(kit.onOpenFull, isNotNull);
      expect(
        find.byKey(const ValueKey('terminal-view-sideways')),
        findsOneWidget,
      );
      expect(find.text('Show 1 earlier line'), findsOneWidget);
      expect(KitTerminalText.strip('\x1b[32mok\x1b[0m'), 'ok');
      expect(TerminalView.inlineLineCap, 2000);
    });
  });

  group('15. overflow (G6)', () {
    for (final size in const [Size(320, 640), Size(412, 915), Size(915, 412)]) {
      for (final scale in const [1.0, 1.3, 2.0]) {
        for (final locale in const [Locale('en'), Locale('ar')]) {
          final at =
              '${size.width.toInt()}x${size.height.toInt()} '
              '· ${scale}x · ${locale.languageCode}';
          _test('live $at', (tester) async {
            final live = _terminal();
            await _pump(
              tester,
              KitTerminalView.live(
                terminal: live.terminal,
                semanticsLabel: 'Terminal',
                keys: TerminalKeyBarController(),
                interruptKeys: true,
              ),
              size: size,
              textScale: scale,
              locale: locale,
            );
            expect(tester.takeException(), isNull);
          });
          _test('output $at', (tester) async {
            await _pump(
              tester,
              SingleChildScrollView(
                child: KitTerminalView.output(
                  command: 'flutter test --concurrency=1 test/kit/a_test.dart',
                  output:
                      '${_lines(12)}\n'
                      '${List.filled(30, 'a-very-long-unbroken-token').join()}',
                  tailLines: 6,
                ),
              ),
              size: size,
              textScale: scale,
              locale: locale,
            );
            await tester.tap(
              find.byKey(const ValueKey('terminal-show-earlier')),
            );
            await tester.pump();
            expect(tester.takeException(), isNull);
          });
        }
      }
    }
  });

  kitMotionStillTests(
    'KitTerminalView',
    builds: {
      'live': () => KitTerminalView.live(
        terminal: xterm.Terminal()..write(_sample),
        semanticsLabel: 'Terminal',
        keys: TerminalKeyBarController(),
      ),
      'output': () => SingleChildScrollView(
        child: KitTerminalView.output(output: _lines(50)),
      ),
    },
    changes: {
      'show earlier': KitMotionChange(
        build: () => SingleChildScrollView(
          child: KitTerminalView.output(output: _lines(50)),
        ),
        act: (tester, stage) => stage.press(
          find.descendant(
            of: find.byKey(const ValueKey('terminal-show-earlier')),
            matching: find.byType(TextButton),
          ),
        ),
        hides: 'Show 10 earlier lines',
      ),
      'latch': KitMotionChange(
        build: () => KitTerminalView.live(
          terminal: xterm.Terminal(),
          semanticsLabel: 'Terminal',
          keys: TerminalKeyBarController(),
        ),
        act: (tester, stage) async {
          await tester.tap(find.byKey(const ValueKey('terminal-key-ctrl')));
        },
        shows: 'Ctrl',
      ),
    },
  );
}
