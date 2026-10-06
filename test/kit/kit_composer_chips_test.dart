// KitComposerChips (docs/ux-system/kit-api/KitComposerChips.md): the frozen
// "Tests required" contract (1–10), the reduced-motion sample (G8x, MOT-7)
// and the keyboard behaviour (G14).
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_chips.dart';
import 'package:opencode_mobile/ui/kit/kit_image.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

/// A valid 1x1 opaque PNG (test/kit/kit_image_test.dart's fixture).
final Uint8List _png = Uint8List.fromList(const [
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, //
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, //
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00,
  0x90, 0x77, 0x53, 0xDE,
  0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, //
  0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00, 0x00, 0x03, 0x01, 0x01, 0x00,
  0x18, 0xDD, 0x8D, 0xB0,
  0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82, //
]);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  Size size = const Size(412, 915),
  bool reduced = false,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: _theme,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reduced,
        ),
        child: Directionality(textDirection: direction, child: widget!),
      ),
      home: Scaffold(
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: Align(alignment: Alignment.topLeft, child: child),
        ),
      ),
    ),
  );
}

/// Room for the chip's words and its context words in the test font, whose
/// glyphs are one em wide.
const _wide = Size(900, 600);

/// One theme instance, so a rebuild never starts MaterialApp's theme
/// animation (which would read as the part still moving).
final _theme = AppTheme.dark();

/// The colours of every paragraph (words and icon glyphs) painted under
/// [root], read from the render tree rather than the widgets that built it.
Set<Color?> _paintedColors(RenderObject root) {
  final colors = <Color?>{};
  void visit(RenderObject node) {
    if (node is RenderParagraph) {
      node.text.visitChildren((span) {
        final color = span.style?.color;
        if (color != null) colors.add(color);
        return true;
      });
      final base = node.text.style?.color;
      if (base != null) colors.add(base);
    }
    node.visitChildren(visit);
  }

  visit(root);
  return colors;
}

const _chipKey = Key('composer-model-context');
const _contextKey = Key('composer-context-percent');

Widget _model({
  String label = 'Sonnet 4.5',
  KitModelChipState state = KitModelChipState.chosen,
  double? contextUsed,
  VoidCallback? onPressed,
  List<KitMenuItem> menu = const [],
}) => KitComposerChips.model(
  label: label,
  onPressed: onPressed ?? () {},
  state: state,
  contextUsed: contextUsed,
  menu: menu,
  chipKey: _chipKey,
  contextKey: _contextKey,
);

KitAttachment _image({VoidCallback? onOpen, String? detail}) => KitAttachment(
  id: 'img',
  label: 'screenshot.png',
  kind: KitAttachmentKind.image,
  thumbnail: KitImageSource.memory(_png),
  thumbnailKey: const Key('attachment-thumbnail'),
  detail: detail,
  onOpen: onOpen,
);

KitAttachment _file({VoidCallback? onOpen}) => KitAttachment(
  id: 'file',
  label: 'notes.txt',
  kind: KitAttachmentKind.file,
  onOpen: onOpen,
);

const _longDescription =
    'Summarise the conversation so far into a shorter history that keeps '
    'the decisions, the open questions and every file that was changed, so '
    'the model has room to keep working';

List<KitSuggestion> _suggestions(int count) => [
  for (var i = 0; i < count; i++)
    KitSuggestion(
      id: i,
      label: '/command$i',
      kind: KitSuggestionKind.command,
      description: i == 0 ? _longDescription : 'Short description $i',
      key: Key('inline-command-$i'),
    ),
];

SemanticsData _semanticsOf(WidgetTester tester, Finder finder) =>
    tester.getSemantics(finder).getSemanticsData();

ThemeRoles _roles(WidgetTester tester) =>
    KitTokens.of(tester.element(find.byType(KitComposerChips).first)).roles;

void main() {
  kitMotionStillTests(
    'KitComposerStatusStrip',
    builds: {
      'facts': () => KitComposerStatusStrip(chips: [_model()]),
      'empty': () => const KitComposerStatusStrip(chips: []),
    },
    changes: {
      'fact appears': KitMotionChange(
        build: () => const KitComposerStatusStrip(chips: []),
        act: (tester, stage) => stage.rebuild(
          KitComposerStatusStrip(
            chips: [
              KitComposerChips.model(label: 'Review model', onPressed: () {}),
            ],
          ),
        ),
        shows: 'Review model',
      ),
    },
  );

  group('1. model chip words', () {
    testWidgets('each state says its words; only chooseNeeded says '
        '"Choose a model"', (tester) async {
      const cases = {
        KitModelChipState.chosen: 'Sonnet 4.5',
        KitModelChipState.serverDefault: 'Sonnet 4.5',
        KitModelChipState.signInNeeded: 'Sign in to a model',
        KitModelChipState.chooseNeeded: 'Choose a model',
      };
      for (final MapEntry(key: state, value: words) in cases.entries) {
        await _pump(tester, _model(state: state));
        expect(find.text(words), findsOneWidget, reason: '$state');
        expect(
          find.text('Choose a model'),
          state == KitModelChipState.chooseNeeded
              ? findsOneWidget
              : findsNothing,
          reason: '$state',
        );
      }
    });

    testWidgets('serverDefault without a name says "Server default"', (
      tester,
    ) async {
      await _pump(
        tester,
        _model(label: '', state: KitModelChipState.serverDefault),
      );
      expect(find.text('Server default'), findsOneWidget);
      expect(find.text('Choose a model'), findsNothing);
    });
  });

  group('tap feedback on the next frame (KitPressTracker)', () {
    /// The model chip's pill fill: surface3 at rest, surface2 pressed.
    Color? pillFill(WidgetTester tester) {
      final boxes = tester.widgetList<DecoratedBox>(
        find.descendant(
          of: find.byType(KitComposerChips),
          matching: find.byType(DecoratedBox),
        ),
      );
      for (final box in boxes) {
        final decoration = box.decoration;
        if (decoration is ShapeDecoration && decoration.color != null) {
          return decoration.color;
        }
      }
      return null;
    }

    testWidgets('pointer-down fills the model chip on the first frame', (
      tester,
    ) async {
      await _pump(tester, _model());
      final roles = _roles(tester);
      expect(pillFill(tester), roles.surface3);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(_chipKey)),
      );
      await tester.pump();
      expect(pillFill(tester), roles.surface2, reason: 'no 100 ms wait');
      await gesture.up();
      await tester.pumpAndSettle();
      expect(pillFill(tester), roles.surface3);
    });

    testWidgets('a quick tap on the model chip is still seen', (tester) async {
      var taps = 0;
      await _pump(tester, _model(onPressed: () => taps++));
      final roles = _roles(tester);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(_chipKey)),
      );
      await gesture.up();
      await tester.pump();
      expect(taps, 1);
      expect(pillFill(tester), roles.surface2, reason: 'held after up');
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
      expect(pillFill(tester), roles.surface3, reason: 'clears after the hold');
    });

    testWidgets('a quick tap on an attachment body is still seen', (
      tester,
    ) async {
      var opened = 0;
      await _pump(
        tester,
        KitComposerChips.attachments(
          items: [_file(onOpen: () => opened++)],
          onRemove: (_) {},
        ),
      );
      final roles = _roles(tester);
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('notes.txt')),
      );
      await gesture.up();
      await tester.pump();
      expect(opened, 1);
      expect(pillFill(tester), roles.surface2);
      await tester.pump(KitMotion.pressHold);
      await tester.pumpAndSettle();
      expect(pillFill(tester), roles.surface3);
    });
  });

  group('2. tap and menu', () {
    List<KitMenuItem> menu(List<String> log) => [
      KitMenuItem(label: 'Next model', onSelected: () => log.add('next')),
      KitMenuItem(
        label: 'Previous model',
        onSelected: () => log.add('previous'),
      ),
    ];

    testWidgets('tap calls onPressed once', (tester) async {
      var taps = 0;
      await _pump(tester, _model(onPressed: () => taps++));
      await tester.tap(find.byKey(_chipKey));
      await tester.pumpAndSettle();
      expect(taps, 1);
      expect(find.text('Next model'), findsNothing);
    });

    testWidgets('long-press opens the menu', (tester) async {
      final log = <String>[];
      var taps = 0;
      await _pump(tester, _model(menu: menu(log), onPressed: () => taps++));
      await tester.longPress(find.byKey(_chipKey));
      await tester.pumpAndSettle();
      expect(find.text('Next model'), findsOneWidget);
      await tester.tap(find.text('Next model'));
      await tester.pumpAndSettle();
      expect(log, ['next']);
      expect(taps, 0);
    });

    testWidgets('right-click opens the menu', (tester) async {
      final log = <String>[];
      await _pump(tester, _model(menu: menu(log)));
      await tester.tap(find.byKey(_chipKey), buttons: kSecondaryButton);
      await tester.pumpAndSettle();
      expect(find.text('Previous model'), findsOneWidget);
    });

    testWidgets('the menu items are semantic custom actions', (tester) async {
      final semantics = tester.ensureSemantics();
      final log = <String>[];
      await _pump(tester, _model(menu: menu(log)));
      final node = tester.getSemantics(find.byKey(_chipKey));
      final ids = node.getSemanticsData().customSemanticsActionIds!;
      final labels = [
        for (final id in ids) CustomSemanticsAction.getAction(id)!.label,
      ];
      expect(labels, ['Next model', 'Previous model']);
      tester.semantics.customAction(
        find.semantics.byLabel('Sonnet 4.5'),
        const CustomSemanticsAction(label: 'Previous model'),
      );
      await tester.pumpAndSettle();
      expect(log, ['previous']);
      semantics.dispose();
    });
  });

  group('3. context', () {
    testWidgets('0.69 shows no context words', (tester) async {
      await _pump(tester, _model(contextUsed: 0.69), size: _wide);
      expect(find.byKey(_contextKey), findsNothing);
      expect(find.textContaining('%'), findsNothing);
    });

    testWidgets('0.7 shows "· 70 %" in tabular figures, text1', (tester) async {
      await _pump(tester, _model(contextUsed: 0.7), size: _wide);
      expect(find.text('· 70 %'), findsOneWidget);
      final text = tester.widget<Text>(find.byKey(_contextKey));
      expect(
        text.style!.fontFeatures,
        contains(const FontFeature.tabularFigures()),
      );
      final roles = _roles(tester);
      expect(text.style!.color, roles.text1);
    });

    testWidgets('0.96 says "Context almost full", never attention or '
        'danger', (tester) async {
      await _pump(tester, _model(contextUsed: 0.96), size: _wide);
      expect(find.text('· Context almost full'), findsOneWidget);
      final roles = _roles(tester);
      // Every colour the chip paints: its words and glyphs, as rendered.
      final colors = _paintedColors(tester.renderObject(find.byKey(_chipKey)));
      expect(colors, isNotEmpty);
      expect(colors, isNot(contains(roles.attention)));
      expect(colors, isNot(contains(roles.danger)));
      expect(_paintedColors(tester.renderObject(find.byKey(_contextKey))), {
        roles.text1,
      });
    });
  });

  testWidgets('4. narrow slot: glyph and chevron only, words in semantics '
      'and the tooltip', (tester) async {
    final semantics = tester.ensureSemantics();
    const long = 'explore · Claude Sonnet 4.5 · High reasoning';
    await _pump(
      tester,
      SizedBox(
        width: 120,
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          child: _model(label: long, contextUsed: 0.8),
        ),
      ),
    );
    expect(find.text(long), findsNothing);
    expect(find.byKey(_contextKey), findsNothing);
    expect(
      find.descendant(of: find.byKey(_chipKey), matching: find.byType(Icon)),
      findsNWidgets(2),
    );
    expect(tester.getSize(find.byKey(_chipKey)).width, 48);
    expect(
      _semanticsOf(tester, find.byKey(_chipKey)).label,
      '$long, Context 80 % full',
    );
    final tooltip = tester.widget<Tooltip>(
      find.descendant(of: find.byKey(_chipKey), matching: find.byType(Tooltip)),
    );
    expect(tooltip.message, '$long · 80 %');
    semantics.dispose();
  });

  group('5. attachments', () {
    testWidgets('an image shows its thumbnail under thumbnailKey', (
      tester,
    ) async {
      await _pump(
        tester,
        KitComposerChips.attachments(items: [_image()], onRemove: (_) {}),
      );
      expect(find.byKey(const Key('attachment-thumbnail')), findsOneWidget);
      expect(find.byType(KitImage), findsOneWidget);
    });

    for (final scale in [1.0, 2.0]) {
      testWidgets('a name and its detail both show whole while the chip has '
          'room · text x$scale', (tester) async {
        const name = 'screenshot-2026-09-27.png';
        await _pump(
          tester,
          KitComposerChips.attachments(
            items: [
              KitAttachment(
                id: 'img',
                label: name,
                kind: KitAttachmentKind.image,
                detail: 'Recovered',
              ),
            ],
            onRemove: (_) {},
          ),
          textScale: scale,
          // Room for both, but less than the name would get as a fixed
          // three-fifths share of the line.
          size: Size(scale == 1 ? 640 : 1120, 915),
        );
        // The label is painted uncut, and the detail beside it is too.
        expect(find.text(name), findsOneWidget);
        final detail = tester.renderObject<RenderParagraph>(
          find.descendant(
            of: find.text('Recovered'),
            matching: find.byType(RichText),
          ),
        );
        expect(detail.didExceedMaxLines, isFalse);
      });
    }

    testWidgets('a name too long for the slot is middle-cut, keeping its '
        'file name and its detail', (tester) async {
      const name = 'lib/ui/screens/chat/composer/attachment_preview.dart';
      await _pump(
        tester,
        SizedBox(
          width: 320,
          child: KitComposerChips.attachments(
            items: [
              KitAttachment(
                id: 'ref',
                label: name,
                kind: KitAttachmentKind.reference,
                detail: 'Recovered',
              ),
            ],
            onRemove: (_) {},
          ),
        ),
      );
      expect(find.text(name), findsNothing);
      // Middle-cut: the path's start and its extension stay.
      expect(find.textContaining(RegExp(r'^l.*….*\.dart$')), findsOneWidget);
      expect(find.text('Recovered'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('the remove target calls onRemove with that attachment', (
      tester,
    ) async {
      final removed = <Object>[];
      await _pump(
        tester,
        KitComposerChips.attachments(
          items: [_image(), _file()],
          onRemove: (a) => removed.add(a.id),
        ),
      );
      await tester.tap(find.bySemanticsLabel('Remove notes.txt'));
      await tester.pump();
      expect(removed, ['file']);
    });

    testWidgets('read-only shows no remove target', (tester) async {
      await _pump(
        tester,
        KitComposerChips.attachments(items: [_image(), _file()]),
      );
      expect(find.bySemanticsLabel(RegExp('^Remove')), findsNothing);
      expect(find.byIcon(Icons.close), findsNothing);
    });

    testWidgets('tapping the body calls onOpen', (tester) async {
      var opened = 0;
      final removed = <Object>[];
      await _pump(
        tester,
        KitComposerChips.attachments(
          items: [_file(onOpen: () => opened++)],
          onRemove: (a) => removed.add(a.id),
        ),
      );
      await tester.tap(find.text('notes.txt'));
      await tester.pump();
      expect(opened, 1);
      expect(removed, isEmpty);
    });

    testWidgets('an empty list renders nothing', (tester) async {
      await _pump(
        tester,
        const KitComposerChips.attachments(
          items: [],
          stripKey: Key('composer-reference-strip'),
        ),
      );
      expect(
        tester.getSize(find.byKey(const Key('composer-reference-strip'))),
        Size.zero,
      );
    });
  });

  group('6. suggestions', () {
    testWidgets('seven items show five rows and "Show all"', (tester) async {
      var showAll = 0;
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(7),
          onSelected: (_) {},
          onShowAll: () => showAll++,
          listKey: const Key('inline-command-suggestions'),
        ),
      );
      for (var i = 0; i < 7; i++) {
        expect(
          find.byKey(Key('inline-command-$i')),
          i < 5 ? findsOneWidget : findsNothing,
        );
      }
      await tester.tap(find.text('Show all'));
      await tester.pump();
      expect(showAll, 1);
    });

    testWidgets('a phone\'s three rows still lead to "Show all"', (
      tester,
    ) async {
      // The host gives fewer rows than it holds; Show all is its way on.
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(3),
          onSelected: (_) {},
          onShowAll: () {},
        ),
      );
      expect(find.text('Show all'), findsOneWidget);
    });

    testWidgets('no "Show all" without onShowAll; empty renders nothing', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(7),
          onSelected: (_) {},
        ),
      );
      expect(find.text('Show all'), findsNothing);
      await _pump(
        tester,
        KitComposerChips.suggestions(suggestions: const [], onSelected: (_) {}),
      );
      expect(find.bySemanticsLabel('Suggestions'), findsNothing);
      expect(find.text('Show all'), findsNothing);
      expect(tester.getSize(find.byType(KitComposerChips)), Size.zero);
      semantics.dispose();
    });

    testWidgets('no rows with a note: one plain line in the suggestion '
        'area, read out, nothing to tap (B12)', (tester) async {
      final semantics = tester.ensureSemantics();
      const note =
          'The demo has no commands — send the sample prompt to see a '
          'change reviewed.';
      await _pump(
        tester,
        KitComposerChips.suggestions(
          listKey: const Key('note-list'),
          suggestions: const [],
          onSelected: (_) {},
          note: note,
        ),
        size: const Size(360, 800),
      );
      expect(find.text(note), findsOneWidget);
      expect(find.byKey(const Key('note-list')), findsOneWidget);
      expect(find.bySemanticsLabel('Suggestions'), findsNothing);
      expect(find.byType(InkWell), findsNothing);
      // Read out when it appears: a live region with the line as its label.
      expect(
        tester.getSemantics(find.text(note)),
        matchesSemantics(label: note, isLiveRegion: true),
      );
      // Rows win over the note.
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(2),
          onSelected: (_) {},
          note: note,
        ),
      );
      expect(find.text(note), findsNothing);
      expect(find.text('/command1'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('tapping a row calls onSelected once', (tester) async {
      final selected = <Object>[];
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(3),
          onSelected: (s) => selected.add(s.id),
        ),
      );
      await tester.tap(find.text('/command2'));
      await tester.pump();
      expect(selected, [2]);
    });

    testWidgets('a long description wraps to two lines and ellipsizes at a '
        'word, with the full text in semantics', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        SizedBox(
          width: 320,
          child: KitComposerChips.suggestions(
            suggestions: _suggestions(1),
            onSelected: (_) {},
          ),
        ),
      );
      final shown = tester
          .widgetList<Text>(find.byType(Text))
          .map((t) => t.data ?? '')
          .firstWhere((d) => d.startsWith('Summarise'));
      expect(shown, isNot(_longDescription));
      expect(shown, endsWith('…'));
      final kept = shown.substring(0, shown.length - 1);
      expect(_longDescription.startsWith(kept), isTrue);
      expect(
        _longDescription[kept.length],
        ' ',
        reason: 'cut at a word boundary: "$shown"',
      );
      final paragraph = tester.renderObject<RenderParagraph>(find.text(shown));
      final lineHeight =
          paragraph.text.style!.fontSize! * paragraph.text.style!.height!;
      expect(
        tester.getSize(find.text(shown)).height,
        moreOrLessEquals(2 * lineHeight, epsilon: 1),
      );
      expect(
        _semanticsOf(tester, find.byKey(const Key('inline-command-0'))).label,
        '/command0, $_longDescription',
      );
      semantics.dispose();
    });
  });

  group('7. keyboard', () {
    testWidgets('Tab order: chip, attachment body, its remove target; Enter '
        'activates each', (tester) async {
      final log = <String>[];
      await _pump(
        tester,
        Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _model(onPressed: () => log.add('model')),
            KitComposerChips.attachments(
              items: [_file(onOpen: () => log.add('open'))],
              onRemove: (_) => log.add('remove'),
            ),
          ],
        ),
      );
      for (final expected in ['model', 'open', 'remove']) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();
        expect(log.last, expected);
      }
      expect(log, ['model', 'open', 'remove']);
    });

    testWidgets('Delete or Backspace on a focused attachment removes it', (
      tester,
    ) async {
      final removed = <Object>[];
      await _pump(
        tester,
        KitComposerChips.attachments(
          items: [_file(onOpen: () {})],
          onRemove: (a) => removed.add(a.id),
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.delete);
      await tester.pump();
      expect(removed, ['file']);
      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await tester.pump();
      expect(removed, ['file', 'file']);
    });

    testWidgets('arrow keys move between suggestion rows; Enter selects', (
      tester,
    ) async {
      final selected = <Object>[];
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(3),
          onSelected: (s) => selected.add(s.id),
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(selected, [1]);
    });
  });

  group('8. semantics and targets', () {
    testWidgets('model chip: words, context, hint', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, _model(contextUsed: 0.85), size: _wide);
      final data = _semanticsOf(tester, find.byKey(_chipKey));
      expect(data.label, 'Sonnet 4.5, Context 85 % full');
      expect(data.hint, 'Change model');
      expect(data.flagsCollection.isButton, isTrue);
      final size = tester.getSize(find.byKey(_chipKey));
      expect(size.height, greaterThanOrEqualTo(48));
      expect(size.width, greaterThanOrEqualTo(48));
      semantics.dispose();
    });

    testWidgets('attachments: preview and remove buttons, 48 dp, 8 dp apart', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        KitComposerChips.attachments(
          items: [_image(onOpen: () {}, detail: 'Recovered')],
          onRemove: (_) {},
        ),
      );
      final body = find.bySemanticsLabel('Preview screenshot.png');
      final remove = find.bySemanticsLabel('Remove screenshot.png');
      expect(body, findsOneWidget);
      expect(remove, findsOneWidget);
      expect(_semanticsOf(tester, body).value, 'Recovered');
      final bodyRect = tester.getRect(body);
      final removeRect = tester.getRect(remove);
      for (final rect in [bodyRect, removeRect]) {
        expect(rect.width, greaterThanOrEqualTo(48));
        expect(rect.height, greaterThanOrEqualTo(48));
      }
      expect(removeRect.left - bodyRect.right, 8);
      semantics.dispose();
    });

    testWidgets('read-only chips are plain text with the kind', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        KitComposerChips.attachments(items: [_image(), _file()]),
      );
      final image = find.bySemanticsLabel('Image, screenshot.png');
      expect(image, findsOneWidget);
      expect(_semanticsOf(tester, image).flagsCollection.isButton, isFalse);
      expect(find.bySemanticsLabel('File, notes.txt'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('suggestions: a list named "Suggestions" of 48 dp buttons', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        KitComposerChips.suggestions(
          suggestions: _suggestions(2),
          onSelected: (_) {},
        ),
      );
      expect(find.bySemanticsLabel('Suggestions'), findsOneWidget);
      final row = find.byKey(const Key('inline-command-1'));
      final data = _semanticsOf(tester, row);
      expect(data.label, '/command1, Short description 1');
      expect(data.flagsCollection.isButton, isTrue);
      expect(tester.getSize(row).height, greaterThanOrEqualTo(48));
      semantics.dispose();
    });
  });

  group('motion', () {
    testWidgets('a removed attachment fades where it was, then leaves', (
      tester,
    ) async {
      Widget strip(List<KitAttachment> items) =>
          KitComposerChips.attachments(items: items, onRemove: (_) {});
      await _pump(tester, strip([_image(), _file()]));
      await _pump(tester, strip([_image()]));
      await tester.pump(const Duration(milliseconds: 50));
      final fading = tester.widget<FadeTransition>(
        find.ancestor(
          of: find.text('notes.txt'),
          matching: find.byType(FadeTransition),
        ),
      );
      expect(fading.opacity.value, inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(find.text('notes.txt'), findsNothing);
      expect(find.text('screenshot.png'), findsOneWidget);
    });

    testWidgets('9. reduced motion: one pump() settles', (tester) async {
      await _pump(tester, _model(), reduced: true, size: _wide);
      await _pump(tester, _model(contextUsed: 0.8), reduced: true, size: _wide);
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.text('· 80 %'), findsOneWidget);
      await _pump(
        tester,
        KitComposerChips.attachments(items: [_file()], onRemove: (_) {}),
        reduced: true,
      );
      await _pump(
        tester,
        KitComposerChips.attachments(items: [_image()], onRemove: (_) {}),
        reduced: true,
      );
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.text('notes.txt'), findsNothing);
    });
  });

  group('10. 200 % text at 320 dp', () {
    for (final direction in TextDirection.values) {
      testWidgets('no overflow · ${direction.name}', (tester) async {
        await _pump(
          tester,
          Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _model(
                label: 'explore · Claude Sonnet 4.5 · High',
                contextUsed: 0.85,
              ),
              KitComposerChips.attachments(
                items: [
                  _image(detail: 'Recovered'),
                  _file(),
                  const KitAttachment(
                    id: 'ref',
                    label: 'lib/ui/screens/chat/composer.dart:1054–1288',
                    kind: KitAttachmentKind.reference,
                    detail: 'Not saved with your draft',
                  ),
                ],
                onRemove: (_) {},
              ),
              KitComposerChips.suggestions(
                suggestions: _suggestions(7),
                onSelected: (_) {},
                onShowAll: () {},
              ),
            ],
          ),
          textScale: 2,
          size: const Size(320, 1400),
          direction: direction,
        );
        expect(tester.takeException(), isNull);
      });
    }
  });

  kitMotionStillTests(
    'KitComposerChips',
    builds: {
      'model': () => _model(contextUsed: 0.8),
      'attachments': () =>
          KitComposerChips.attachments(items: [_file()], onRemove: (_) {}),
      'suggestions': () => KitComposerChips.suggestions(
        suggestions: _suggestions(2),
        onSelected: (_) {},
      ),
    },
    changes: {
      'context appears': KitMotionChange(
        build: () => _model(),
        act: (tester, stage) => stage.rebuild(_model(contextUsed: 0.8)),
        shows: '· 80 %',
      ),
      'attachment added': KitMotionChange(
        build: () =>
            KitComposerChips.attachments(items: [_file()], onRemove: (_) {}),
        act: (tester, stage) => stage.rebuild(
          KitComposerChips.attachments(
            items: [_file(), _image()],
            onRemove: (_) {},
          ),
        ),
        shows: 'screenshot.png',
      ),
    },
  );
}
