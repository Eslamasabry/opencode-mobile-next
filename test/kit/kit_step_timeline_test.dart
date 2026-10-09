// KitStepTimeline (docs/ux-system/kit-api/KitStepTimeline.md): the quiet
// line, the rail and its nodes, the preview card of a file write or edit, and
// the parts that draw themselves differently on it (KitToolRow,
// KitMessage.thought). Reduced motion unless a test asks for the live mark.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_step_timeline.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

const _lineKey = ValueKey('timeline-line');
const _stepsKey = ValueKey('timeline-steps');

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  bool reduceMotion = true,
  Size size = const Size(412, 915),
  Locale locale = const Locale('en'),
}) async {
  tester.view.physicalSize = size * 2;
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reduceMotion,
        ),
        child: widget!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  );
}

KitStepTimeline _timeline({
  bool expanded = true,
  List<Widget>? steps,
  VoidCallback? onPressed,
  Widget? mark,
  Widget? head,
  String label = 'Read 3 files · ran 2 commands',
}) => KitStepTimeline(
  label: label,
  icon: AppIconography.fileText,
  expanded: expanded,
  onPressed: onPressed ?? () {},
  mark: mark,
  head: head,
  lineKey: _lineKey,
  stepsKey: _stepsKey,
  steps: steps ?? const [KitText('A plain step')],
);

const _write = '''line one
line two
line three
line four
line five
line six
line seven
line eight''';

KitToolRow _writeRow({
  KitStepPreview? preview,
  bool? expanded,
  ValueChanged<bool>? onExpansionChanged,
  KitToolStatus status = KitToolStatus.done,
}) => KitToolRow(
  kind: KitToolKind.edit,
  title: 'Write',
  path: 'lib/home.dart',
  added: 8,
  status: status,
  preview: preview,
  expanded: expanded,
  onExpansionChanged: onExpansionChanged,
  body: const [KitCodeBlock(text: _write, kind: KitCodeKind.code)],
);

void main() {
  group('the line', () {
    testWidgets('closed it is the glyph, the words and a chevron', (
      tester,
    ) async {
      var taps = 0;
      await _pump(tester, _timeline(expanded: false, onPressed: () => taps++));
      expect(find.text('Read 3 files · ran 2 commands'), findsOneWidget);
      expect(find.byIcon(AppIconography.fileText), findsOneWidget);
      expect(find.text('A plain step'), findsNothing);
      expect(find.byKey(_stepsKey), findsNothing);
      await tester.tap(find.byKey(_lineKey));
      expect(taps, 1);
    });

    testWidgets('is one button node that says whether it is open', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(tester, _timeline(expanded: true));
      final node = tester.getSemantics(
        find.bySemanticsLabel('Read 3 files · ran 2 commands'),
      );
      expect(
        node,
        isSemantics(
          label: 'Read 3 files · ran 2 commands',
          isButton: true,
          hasTapAction: true,
          hasExpandedState: true,
          isExpanded: true,
        ),
      );
      expect(
        tester.getSize(find.byKey(_lineKey)).height,
        greaterThanOrEqualTo(48),
      );
      handle.dispose();
    });

    testWidgets('draws no pill, no frame and no fill around itself', (
      tester,
    ) async {
      await _pump(tester, _timeline(expanded: false));
      final decorated = find.descendant(
        of: find.byKey(_lineKey),
        matching: find.byWidgetPredicate(
          (w) =>
              w is DecoratedBox &&
              w.decoration is BoxDecoration &&
              (w.decoration as BoxDecoration).color != null &&
              (w.decoration as BoxDecoration).color!.a > 0,
        ),
      );
      expect(decorated, findsNothing);
    });

    testWidgets('the mark takes the glyph place', (tester) async {
      await _pump(
        tester,
        _timeline(
          expanded: false,
          mark: const KitStatusMark(state: KitMarkState.working),
        ),
      );
      expect(find.byType(KitStatusMark), findsOneWidget);
      expect(find.byIcon(AppIconography.fileText), findsNothing);
    });

    testWidgets('iconFor picks the glyph of what the work mostly did', (
      tester,
    ) async {
      expect(KitStepTimeline.iconFor(read: 3, ran: 8), AppIconography.terminal);
      expect(KitStepTimeline.iconFor(read: 3, ran: 1), AppIconography.fileText);
      expect(
        KitStepTimeline.iconFor(read: 2, edited: 2),
        AppIconography.editNote,
      );
      expect(KitStepTimeline.iconFor(), AppIconography.tools);
    });
  });

  group('the rail', () {
    testWidgets('steps are drawn only while open', (tester) async {
      await _pump(tester, _timeline(expanded: true));
      expect(find.text('A plain step'), findsOneWidget);
      expect(find.byKey(_stepsKey), findsOneWidget);
    });

    testWidgets('a step knows it sits on a rail; outside, it does not', (
      tester,
    ) async {
      late bool inside;
      late bool outside;
      await _pump(
        tester,
        Column(
          children: [
            Builder(
              builder: (context) {
                outside = KitStepTimeline.inside(context);
                return const SizedBox();
              },
            ),
            _timeline(
              steps: [
                Builder(
                  builder: (context) {
                    inside = KitStepTimeline.inside(context);
                    return const SizedBox();
                  },
                ),
              ],
            ),
          ],
        ),
      );
      expect(inside, isTrue);
      expect(outside, isFalse);
    });

    testWidgets('node leaves a step untouched outside a timeline', (
      tester,
    ) async {
      await _pump(
        tester,
        Builder(
          builder: (context) => KitStepTimeline.node(
            context,
            icon: AppIconography.fileText,
            child: const Text('plain'),
          ),
        ),
      );
      expect(find.text('plain'), findsOneWidget);
      expect(find.byIcon(AppIconography.fileText), findsNothing);
    });

    testWidgets('the steps and the head keep their order under the line', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          head: const Text('Show 3 earlier steps'),
          steps: const [Text('first'), Text('second')],
        ),
      );
      final ys = [
        for (final t in ['Show 3 earlier steps', 'first', 'second'])
          tester.getTopLeft(find.text(t)).dy,
      ];
      expect(ys, orderedEquals([...ys]..sort()));
      expect(tester.getTopLeft(find.byKey(_lineKey)).dy, lessThan(ys.first));
    });

    testWidgets('the steps start where the line\'s words start', (
      tester,
    ) async {
      await _pump(tester, _timeline(steps: const [Text('first')]));
      expect(
        tester.getTopLeft(find.text('first')).dx,
        tester.getTopLeft(find.text('Read 3 files · ran 2 commands')).dx,
      );
    });

    testWidgets('Arabic: the glyph and the steps sit at the right edge', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            _writeRow(
              preview: KitStepPreview.fromText(_write),
              expanded: false,
              onExpansionChanged: (_) {},
            ),
          ],
        ),
        locale: const Locale('ar'),
      );
      final width = tester.view.physicalSize.width / 2;
      final glyph = tester.getCenter(find.byIcon(AppIconography.fileText));
      expect(glyph.dx, greaterThan(width / 2));
      final tile = tester.getCenter(find.byIcon(AppIconography.editNote));
      expect(tile.dx, closeTo(glyph.dx, 1));
      expect(tester.getTopRight(find.text('Write')).dx, lessThan(tile.dx));
      expect(tester.takeException(), isNull);
    });
  });

  group('KitToolRow on the rail', () {
    testWidgets('a done step is a tile with the glyph, title, file under', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          steps: const [
            KitToolRow(
              kind: KitToolKind.read,
              title: 'Read',
              path: 'lib/main.dart',
              status: KitToolStatus.done,
            ),
          ],
        ),
      );
      expect(find.text('Read'), findsOneWidget);
      expect(find.text('lib/main.dart'), findsOneWidget);
      // The step's own glyph, as the node (the line's glyph is the same icon).
      expect(find.byIcon(AppIconography.fileText), findsNWidgets(2));
      expect(
        tester.getTopLeft(find.text('Read')).dy,
        lessThan(tester.getTopLeft(find.text('lib/main.dart')).dy),
      );
      // Out of a timeline the same row has the glyph beside the title, and
      // no tile.
    });

    testWidgets('the step in progress carries the one live mark', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          steps: const [
            KitToolRow(
              kind: KitToolKind.read,
              title: 'Read',
              path: 'a.dart',
              status: KitToolStatus.done,
            ),
            KitToolRow(
              kind: KitToolKind.edit,
              title: 'Edit',
              path: 'b.dart',
              status: KitToolStatus.running,
            ),
          ],
        ),
        reduceMotion: false,
      );
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.byType(KitStatusMark), findsOneWidget);
    });

    testWidgets('failed and waiting steps show their state on the rail', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          steps: const [
            KitToolRow(
              kind: KitToolKind.shell,
              title: 'Run',
              status: KitToolStatus.failed,
            ),
            KitToolRow(
              kind: KitToolKind.shell,
              title: 'Run',
              status: KitToolStatus.waitingForYou,
            ),
          ],
        ),
      );
      expect(find.text('Failed'), findsOneWidget);
      expect(find.text('Waiting for you'), findsOneWidget);
      expect(find.byType(KitStatusMark), findsNWidgets(2));
    });

    testWidgets('the same row outside a timeline is unchanged', (tester) async {
      await _pump(
        tester,
        _writeRow(
          preview: KitStepPreview.fromText(_write),
          expanded: false,
          onExpansionChanged: (_) {},
        ),
      );
      expect(find.byKey(const ValueKey('kit-step-preview')), findsNothing);
      expect(find.text('Write'), findsOneWidget);
    });

    testWidgets('a write shows its preview card: six lines, fading', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            _writeRow(
              preview: KitStepPreview.fromText(_write),
              expanded: false,
              onExpansionChanged: (_) {},
            ),
          ],
        ),
      );
      expect(find.byKey(const ValueKey('kit-step-preview')), findsOneWidget);
      for (var n = 1; n <= 6; n++) {
        expect(find.text('+$n'), findsOneWidget, reason: 'line $n');
      }
      expect(find.text('+7'), findsNothing);
      expect(find.text('line six'), findsOneWidget);
      expect(find.text('line seven'), findsNothing);
      // More follow: the last lines fade.
      expect(find.byType(ShaderMask), findsWidgets);
    });

    testWidgets('a short write has no fade', (tester) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            _writeRow(
              preview: KitStepPreview.fromText('only\ntwo'),
              expanded: false,
              onExpansionChanged: (_) {},
            ),
          ],
        ),
      );
      expect(find.text('+2'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('kit-step-preview')),
          matching: find.byType(ShaderMask),
        ),
        findsNothing,
      );
    });

    testWidgets('a tap on the card opens the step, and the card steps aside', (
      tester,
    ) async {
      final opened = <bool>[];
      await _pump(
        tester,
        _timeline(
          steps: [
            _writeRow(
              preview: KitStepPreview.fromText(_write),
              expanded: false,
              onExpansionChanged: opened.add,
            ),
          ],
        ),
      );
      await tester.tap(find.byKey(const ValueKey('kit-step-preview')));
      expect(opened, [true]);
    });

    testWidgets('open: the full view replaces the card', (tester) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            _writeRow(
              preview: KitStepPreview.fromText(_write),
              expanded: true,
              onExpansionChanged: (_) {},
            ),
          ],
        ),
      );
      expect(find.byKey(const ValueKey('kit-step-preview')), findsNothing);
      expect(find.byType(KitCodeBlock), findsOneWidget);
    });

    testWidgets('no card while the write is still running', (tester) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            _writeRow(
              preview: KitStepPreview.fromText(_write),
              expanded: false,
              onExpansionChanged: (_) {},
              status: KitToolStatus.running,
            ),
          ],
        ),
      );
      expect(find.byKey(const ValueKey('kit-step-preview')), findsNothing);
    });

    testWidgets('a sub-agent is a step too: tile, title, task', (tester) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            KitToolRow.agent(
              title: 'Delegated to explore',
              status: KitToolStatus.done,
              task: 'Find where routes are declared',
              onOpen: () {},
            ),
          ],
        ),
      );
      expect(find.text('Delegated to explore · Done'), findsOneWidget);
      expect(find.text('Find where routes are declared'), findsOneWidget);
      expect(find.byIcon(AppIconography.agent), findsOneWidget);
    });
  });

  group('KitMessage.thought on the rail', () {
    KitMessage thought({bool working = false, bool? expanded}) =>
        KitMessage.thought(
          heading: 'Looking at how the app starts',
          working: working,
          expanded: expanded,
          onExpansionChanged: expanded == null ? null : (_) {},
          body: const KitMarkdown('Because.', role: KitTextRole.secondary),
        );

    testWidgets('is a sentence with a dot, not a glyph', (tester) async {
      await _pump(tester, _timeline(steps: [thought()]));
      expect(find.text('Looking at how the app starts'), findsOneWidget);
      expect(find.byIcon(AppIconography.idea), findsNothing);
    });

    testWidgets('outside a timeline it keeps its glyph', (tester) async {
      await _pump(tester, thought());
      expect(find.byIcon(AppIconography.idea), findsOneWidget);
    });

    testWidgets('while it thinks it carries the live mark', (tester) async {
      await _pump(tester, _timeline(steps: [thought(working: true)]));
      expect(find.byType(KitStatusMark), findsOneWidget);
    });

    testWidgets('opens in place to its words', (tester) async {
      await _pump(tester, _timeline(steps: [thought()]));
      expect(find.text('Because.'), findsNothing);
      await tester.tap(find.text('Looking at how the app starts'));
      await tester.pump();
      expect(find.text('Because.', findRichText: true), findsOneWidget);
    });
  });

  group('KitStepPreview', () {
    test('a new file: every line added, numbered from 1, capped at six', () {
      final preview = KitStepPreview.fromText(_write);
      expect(preview.lines, hasLength(6));
      expect(preview.more, isTrue);
      expect(preview.lines.first.newNo, 1);
      expect(preview.lines.last.newNo, 6);
      expect(preview.lines.every((l) => l.newNo != null), isTrue);
    });

    test('a file that fits has no more to show', () {
      final preview = KitStepPreview.fromText('a\nb\n');
      expect(preview.lines.map((l) => l.text), ['a', 'b']);
      expect(preview.more, isFalse);
    });

    test('a patch starts at its first change with one line of context', () {
      const patch = '''--- a/x.dart
+++ b/x.dart
@@ -10,7 +10,8 @@
 ten
 eleven
 twelve
-thirteen
+thirteen!
+thirteen and a half
 fourteen
 fifteen
 sixteen
''';
      final preview = KitStepPreview.fromPatch(patch);
      expect(preview.lines.map((l) => l.text).toList(), [
        'twelve',
        'thirteen',
        'thirteen!',
        'thirteen and a half',
        'fourteen',
        'fifteen',
      ]);
      expect(preview.more, isTrue);
      expect(preview.lines[1].oldNo, 13);
      expect(preview.lines[2].newNo, 13);
    });

    test('a before and after pair has signs and no numbers', () {
      final preview = KitStepPreview.fromTexts(
        before: 'a\nold\nc',
        after: 'a\nnew\nc',
      );
      expect(preview.lines.map((l) => l.text), ['a', 'old', 'new', 'c']);
      expect(
        preview.lines.every((l) => l.newNo == null && l.oldNo == null),
        isTrue,
      );
    });

    test('an empty patch has nothing to show', () {
      expect(KitStepPreview.fromPatch('').isEmpty, isTrue);
      expect(KitStepPreview.fromText('').isEmpty, isTrue);
    });
  });

  group('large text and long work', () {
    testWidgets('2.0 text at 320 dp does not overflow', (tester) async {
      await _pump(
        tester,
        _timeline(
          label:
              'Read 3 files · edited 1 file · ran 8 commands · 6 other steps',
          steps: [
            const KitToolRow(
              kind: KitToolKind.read,
              title: 'Read',
              path: 'lib/ui/kit/chat/kit_step_timeline.dart',
              status: KitToolStatus.done,
              duration: Duration(seconds: 3),
            ),
            _writeRow(
              preview: KitStepPreview.fromText(_write),
              expanded: false,
              onExpansionChanged: (_) {},
            ),
            KitMessage.thought(
              heading: 'The home route needs its own screen and a test',
              body: const KitMarkdown('x', role: KitTextRole.secondary),
            ),
          ],
        ),
        textScale: 2,
        size: const Size(320, 800),
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('200 steps are laid out as given, with no per-step state', (
      tester,
    ) async {
      await _pump(
        tester,
        _timeline(
          steps: [
            for (var i = 0; i < 200; i++)
              KitToolRow(
                kind: KitToolKind.read,
                title: 'Read $i',
                path: 'lib/f$i.dart',
                status: KitToolStatus.done,
              ),
          ],
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.text('Read 199'), findsOneWidget);
    });
  });
}
