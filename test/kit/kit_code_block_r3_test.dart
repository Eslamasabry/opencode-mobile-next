// KitCodeBlock R3 (docs/ux-system/revamp/leftover-units.json, slice-R3): no
// empty header band, Copy on the command's own line, Wrap only when a line
// overflows (accent glyph, never a filled circle), wrap: true honoured for
// commands with hanging continuation lines, the edge fade whenever a
// command overflows, and a copy label read in the header.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

const _copyKey = ValueKey('kit-code-copy');
const _wrapKey = ValueKey('kit-code-wrap');
const _blockKey = ValueKey('kit-code-block');
const _horizontalKey = ValueKey('kit-code-horizontal');

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
}) async {
  tester.view
    ..physicalSize = size
    ..devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
  // The sideways scroller reports its extent after the first layout.
  await tester.pump();
  await tester.pump();
}

KitTokens _tokens(WidgetTester tester) =>
    KitTokens.of(tester.element(find.byType(KitCodeBlock)));

/// The block's outer box (its code surface).
Rect _blockRect(WidgetTester tester) => tester.getRect(
  find
      .descendant(
        of: find.byType(KitCodeBlock),
        matching: find.byType(DecoratedBox),
      )
      .first,
);

bool _fadeShown(WidgetTester tester) => find
    .descendant(
      of: find.byKey(_horizontalKey),
      matching: find.byWidgetPredicate(
        (w) =>
            w is Container &&
            w.decoration is BoxDecoration &&
            (w.decoration! as BoxDecoration).gradient is LinearGradient,
      ),
    )
    .evaluate()
    .isNotEmpty;

void main() {
  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async => null,
    );
    messenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      (message) async => null,
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

  testWidgets('a one-line command has no header: Copy sits on its line', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitCodeBlock(text: 'flutter run', kind: KitCodeKind.command),
    );
    final tokens = _tokens(tester);
    final block = _blockRect(tester);
    final copy = tester.getRect(find.byKey(_copyKey));
    final line = tester.getRect(find.byKey(_blockKey));

    // One line tall: the Copy target plus the space1 inset above and below,
    // with no header row and no space2 gap.
    expect(block.height, tokens.minTarget + tokens.space1 * 2);
    // Copy is trailing on the same line and vertically centred on it.
    expect(copy.left, greaterThan(line.left));
    expect((copy.center.dy - line.center.dy).abs(), lessThanOrEqualTo(1));
    // Its glyph's ink ends space4 inside the block (kit_rails_test); the
    // target stays inside the block.
    expect(copy.right, lessThanOrEqualTo(block.right + .5));
    expect(find.byKey(_wrapKey), findsNothing);
  });

  testWidgets('Copy on a block of several lines sits beside the first line', (
    tester,
  ) async {
    await _pump(tester, const KitCodeBlock(text: 'a = 1\nb = 2\nc = 3'));
    final tokens = _tokens(tester);
    final block = _blockRect(tester);
    final copy = tester.getRect(find.byKey(_copyKey));
    expect(copy.top, block.top + tokens.space1);
    expect(find.text('Copy code'), findsNothing, reason: 'icon, no label');
  });

  testWidgets('Wrap is hidden until a line overflows; on is an accent glyph', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitCodeBlock(text: 'final a = 1;'),
      size: const Size(1280, 800),
    );
    expect(find.byKey(_wrapKey), findsNothing);

    await _pump(
      tester,
      KitCodeBlock(text: 'final a = "${'x' * 120}";', kind: KitCodeKind.output),
    );
    expect(find.byKey(_wrapKey), findsOneWidget);
    final handle = tester.ensureSemantics();
    // Compact wraps output by default (code scrolls, polish2): the toggle
    // reads on, as an accent glyph.
    expect(
      tester.getSemantics(find.byKey(_wrapKey)),
      isSemantics(
        hasToggledState: true,
        isToggled: true,
        label: 'Wrap lines',
        isButton: true,
      ),
    );
    final roles = _tokens(tester).roles;
    final icon = tester.widget<Icon>(
      find.descendant(of: find.byKey(_wrapKey), matching: find.byType(Icon)),
    );
    expect(icon.color, roles.accent);
    // No filled circle behind the glyph while it is on.
    final fills = tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byKey(_wrapKey),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map((d) => d.decoration)
        .whereType<BoxDecoration>()
        .where((d) => d.color == roles.surface3);
    expect(fills, isEmpty);

    await tester.tap(find.byKey(_wrapKey));
    await tester.pump();
    expect(
      tester
          .widget<Icon>(
            find.descendant(
              of: find.byKey(_wrapKey),
              matching: find.byType(Icon),
            ),
          )
          .color,
      roles.text2,
    );
    expect(find.byKey(_horizontalKey), findsOneWidget);
    handle.dispose();
  });

  testWidgets('a command with wrap: true wraps, continuation hangs past \$', (
    tester,
  ) async {
    final long = 'adb shell device_config put ${'x' * 60} 2147483647';
    await _pump(
      tester,
      KitCodeBlock(text: long, kind: KitCodeKind.command, wrap: true),
    );
    expect(find.byKey(_horizontalKey), findsNothing);
    final prompt = tester.getRect(find.text(r'$'));
    final text = tester.getRect(
      find.byWidgetPredicate(
        (w) => w is RichText && w.text.toPlainText() == long,
      ),
    );
    expect(text.left, greaterThan(prompt.right));
    expect(
      text.height,
      greaterThan(prompt.height * 1.5),
      reason: 'the command wraps onto more lines, all indented past \$',
    );
  });

  testWidgets('an overflowing command shows the edge fade; a short one not', (
    tester,
  ) async {
    await _pump(
      tester,
      KitCodeBlock(
        text: 'adb shell cmd appops set ${'x' * 80} allow',
        kind: KitCodeKind.command,
      ),
    );
    expect(find.byKey(_horizontalKey), findsOneWidget);
    expect(_fadeShown(tester), isTrue);

    await _pump(
      tester,
      const KitCodeBlock(text: 'flutter run', kind: KitCodeKind.command),
    );
    expect(_fadeShown(tester), isFalse);
  });

  testWidgets('a copy label on several lines is read in the header', (
    tester,
  ) async {
    final platform = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(SystemChannels.platform, (call) async {
          platform.add(call);
          return null;
        });
    const text = 'adb shell one\nadb shell two';
    await _pump(
      tester,
      const KitCodeBlock(
        text: text,
        kind: KitCodeKind.command,
        copyLabel: 'Copy commands',
      ),
    );
    expect(find.text('Copy commands'), findsOneWidget);
    // The header holds only the labelled button: no bare icon floats there.
    expect(
      find.descendant(
        of: find.byType(KitCodeBlock),
        matching: find.byType(KitIconButton),
      ),
      findsNothing,
    );
    await tester.tap(find.byKey(_copyKey));
    await tester.pump();
    final call = platform.lastWhere((c) => c.method == 'Clipboard.setData');
    expect((call.arguments as Map)['text'], text);
    await tester.pump(const Duration(seconds: 2));

    // One line with a label keeps Copy on its line; the label names it.
    final handle = tester.ensureSemantics();
    await _pump(
      tester,
      const KitCodeBlock(
        text: 'flutter run',
        kind: KitCodeKind.command,
        copyLabel: 'Copy the run command',
      ),
    );
    expect(find.text('Copy the run command'), findsNothing);
    expect(
      tester.getSemantics(find.byKey(_copyKey)).label,
      'Copy the run command',
    );
    handle.dispose();

    // With a caption the header already exists and reads the label too.
    await _pump(
      tester,
      const KitCodeBlock(
        text: 'flutter run',
        kind: KitCodeKind.command,
        caption: 'Run this on your computer',
        copyLabel: 'Copy command',
      ),
    );
    expect(find.text('Copy command'), findsOneWidget);
  });

  // 98c064bd let a labelled copy flex beside the caption: both took half the
  // header and the unused half of the copy's share was put before the
  // caption, so "Terminal command" sat ~70 dp in on a 1280 sheet.
  for (final size in const [Size(412, 915), Size(1280, 800)]) {
    for (final dir in TextDirection.values) {
      testWidgets('a caption beside a labelled copy starts at the block\'s '
          'start edge (${size.width.toInt()}, ${dir.name})', (tester) async {
        await _pump(
          tester,
          Directionality(
            textDirection: dir,
            child: const KitCodeBlock(
              text: "cd '/home/dev/My Projects/acme' && opencode2 --session x",
              kind: KitCodeKind.command,
              caption: 'Terminal command',
              copyLabel: 'Copy command',
            ),
          ),
          size: size,
        );
        final block = _blockRect(tester);
        final inset = _tokens(tester).space4;
        final caption = tester.getRect(find.text('Terminal command'));
        final copy = tester.getRect(find.text('Copy command'));
        if (dir == TextDirection.ltr) {
          expect(
            caption.left,
            moreOrLessEquals(block.left + inset, epsilon: 1),
          );
          expect(copy.right, lessThanOrEqualTo(block.right - inset + 1));
          expect(copy.left, greaterThan(caption.right));
        } else {
          expect(
            caption.right,
            moreOrLessEquals(block.right - inset, epsilon: 1),
          );
          expect(copy.left, greaterThanOrEqualTo(block.left + inset - 1));
          expect(copy.right, lessThan(caption.left));
        }
      });
    }
  }

  testWidgets('a long copy label beside a caption wraps and leaves the '
      'caption room', (tester) async {
    await _pump(
      tester,
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(2)),
        child: const KitCodeBlock(
          text: 'flutter run',
          kind: KitCodeKind.command,
          caption: 'Terminal command',
          copyLabel: 'Copy the command for the computer',
        ),
      ),
      size: const Size(320, 800),
    );
    expect(tester.takeException(), isNull);
    final block = _blockRect(tester);
    final caption = tester.getRect(find.text('Terminal command'));
    expect(
      caption.left,
      moreOrLessEquals(block.left + _tokens(tester).space4, epsilon: 1),
    );
    expect(caption.width, greaterThan(0));
  });
}
