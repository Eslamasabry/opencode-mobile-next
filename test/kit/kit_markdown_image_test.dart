// Pictures in an agent's Markdown (docs/ux-system/kit-api/KitMarkdown.md,
// "Pictures"): `![alt](target)` never shows as syntax. The scanner is tested
// as plain Dart; the tiles through KitMarkdown, with the host's file links
// stubbed the way the chat screen supplies them.
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_image.dart';

/// The words of a chip: the description (the server's, so isolated) or the
/// app's own word, then the file name or host (technical, so left to right).
String _words(String label, [String? detail, bool described = false]) {
  final head = described ? KitBidi.auto(label) : label;
  return detail == null ? head : '$head · ${KitBidi.ltr(detail)}';
}

KitMarkdownImage _one(String source) {
  final parts = KitMarkdownImage.scan(source);
  final images = [
    for (final part in parts)
      if (part.image != null) part.image!,
  ];
  expect(images, hasLength(1), reason: 'pictures in "$source"');
  return images.single;
}

void main() {
  group('scanner', () {
    test('a picture is a picture, a link is still a link', () {
      final image = _one('![Image](/tmp/shot.png)');
      expect(image.alt, 'Image');
      expect(image.target, '/tmp/shot.png');
      expect(image.kind, KitMarkdownImageKind.file);
      expect(image.path, '/tmp/shot.png');
      expect(image.hasDescription, isFalse);

      // The same words with no `!` are a link, not a picture.
      expect(KitMarkdownImage.scan('[Image](/tmp/shot.png)'), [
        (text: '[Image](/tmp/shot.png)', image: null),
      ]);
    });

    test('a ! before something that is not a picture stays text', () {
      for (final text in [
        'Wow! [link](https://example.com)',
        'Done!',
        '!important',
        'a ! [b](c)',
        '! [b](c)',
        '![ not closed',
        '![alt] (/tmp/a.png)',
        '![alt]',
        '![alt](/tmp/a.png',
        'Use ![ as the prefix',
      ]) {
        final parts = KitMarkdownImage.scan(text);
        expect(
          parts.every((part) => part.image == null),
          isTrue,
          reason: '"$text" is text',
        );
        expect(parts.map((part) => part.text).join(), text);
      }
    });

    test('brackets inside the description do not end it early', () {
      final image = _one('![a [b] c](/tmp/x.png)');
      expect(image.alt, 'a [b] c');
      expect(image.target, '/tmp/x.png');

      // Escaped brackets and parentheses in the destination.
      expect(_one('![x](/tmp/shot_(1).png)').target, '/tmp/shot_(1).png');
      expect(_one(r'![a \] b](/tmp/x.png)').alt, r'a \] b');
    });

    test('several pictures on a line, with the words between them', () {
      final parts = KitMarkdownImage.scan(
        'One ![a](/tmp/a.png) two ![b](https://example.com/b.png) three',
      );
      expect(parts.map((part) => part.text ?? '<img>').toList(), [
        'One ',
        '<img>',
        ' two ',
        '<img>',
        ' three',
      ]);
      expect(parts[1].image!.alt, 'a');
      expect(parts[3].image!.kind, KitMarkdownImageKind.web);
    });

    test('the description: trimmed, empty, and the bare word Image', () {
      expect(_one('![  Home screen  ](/tmp/a.png)').alt, 'Home screen');
      expect(_one('![  Home screen  ](/tmp/a.png)').hasDescription, isTrue);
      expect(_one('![](/tmp/a.png)').hasDescription, isFalse);
      expect(_one('![Image](/tmp/a.png)').hasDescription, isFalse);
      expect(_one('![image](/tmp/a.png)').hasDescription, isFalse);
      expect(_one('![Images of it](/tmp/a.png)').hasDescription, isTrue);
    });

    test('a path on the server, a web address, or neither', () {
      // Paths: anchored, relative, bare name, file: URI, <angle> form,
      // percent-encoded, with a title.
      expect(_one('![a](/tmp/shots/a.png)').path, '/tmp/shots/a.png');
      expect(_one('![a](shots/a.png)').path, 'shots/a.png');
      expect(_one('![a](a.png)').path, './a.png');
      expect(_one('![a](file:///tmp/a%20b.png)').path, '/tmp/a b.png');
      expect(_one('![a](</tmp/my shot.png>)').path, '/tmp/my shot.png');
      expect(_one('![a](/tmp/my%20shot.png)').path, '/tmp/my shot.png');
      expect(_one('![a](/tmp/a.png "A title")').path, '/tmp/a.png');
      expect(_one('![a](/tmp/a.PNG)').fileName, 'a.PNG');

      // Web: http and https only, with a host. Never a path.
      final https = _one('![a](https://cdn.example.com:8443/a.png)');
      expect(https.kind, KitMarkdownImageKind.web);
      expect(https.url, 'https://cdn.example.com:8443/a.png');
      expect(https.host, 'cdn.example.com:8443');
      expect(https.path, isNull);
      expect(
        _one('![a](http://example.com/a.png)').kind,
        KitMarkdownImageKind.web,
      );

      // Neither: nothing the app may open.
      for (final target in [
        'data:image/png;base64,iVBORw0KGgo=',
        'javascript:alert(1)',
        'intent://scan/#Intent;scheme=zxing;end',
        'content://media/external/images/1',
        'file://evil.example.com/share/a.png',
        '//cdn.example.com/a.png',
        r'C:\Users\me\a.png',
        '/tmp/a.png?raw=1',
        'notes/readme',
        '',
      ]) {
        final image = _one('![alt]($target)');
        expect(image.kind, KitMarkdownImageKind.inert, reason: target);
        expect(image.path, isNull, reason: target);
        expect(image.url, isNull, reason: target);
      }
    });

    test('a picture inside a link opens the link, never the file twice', () {
      final badge = _one(
        '[![build](https://img.example.com/b.svg)](https://example.com/ci)',
      );
      expect(badge.alt, 'build');
      expect(badge.kind, KitMarkdownImageKind.web);
      expect(badge.url, 'https://example.com/ci');
      expect(badge.host, 'example.com');

      // A file in a link stays a file; the line is one picture, not three
      // pieces of syntax.
      final local = _one('[![shot](/tmp/s.png)](https://example.com/pr/1)');
      expect(local.kind, KitMarkdownImageKind.file);
    });

    test('inline code is code, not a picture', () {
      final parts = KitMarkdownImage.scan(
        'Write `![alt](/tmp/a.png)` to embed it.',
      );
      expect(parts.single.image, isNull);
    });

    test('only: a line that holds nothing but pictures', () {
      expect(
        KitMarkdownImage.only('  ![a](/tmp/a.png)  ![b](/tmp/b.png) ')!,
        hasLength(2),
      );
      expect(KitMarkdownImage.only('see ![a](/tmp/a.png)'), isNull);
      expect(KitMarkdownImage.only('![a](/tmp/a.png) see'), isNull);
      expect(KitMarkdownImage.only('plain'), isNull);
      expect(KitMarkdownImage.only('[a](/tmp/a.png)'), isNull);
    });

    test('speech, measuring and copy read the description, not the target', () {
      expect(
        KitMarkdownImage.withAltText('Look ![Chart](/tmp/c.png) here'),
        'Look Chart here',
      );
      expect(
        KitMarkdown.proseForSpeech(
          'The chart:\n![Revenue by month](https://example.com/r.png)\nDone.',
        ),
        'The chart:\nRevenue by month\nDone.',
      );
      final three = KitMarkdown.proseForSpeech(
        '![Image](/tmp/a.png)\n![Image](/tmp/b.png)\n![](/tmp/c.png)',
      );
      expect(three, 'Image\nImage');
      for (final spoken in [three]) {
        expect(spoken, isNot(contains('/tmp')));
        expect(spoken, isNot(contains('!')));
        expect(spoken, isNot(contains('](')));
      }
      // A link keeps its old reading.
      expect(
        KitMarkdown.proseForSpeech('See [the docs](https://example.com).'),
        'See the docs.',
      );
    });

    test('a picture still being written is held back', () {
      for (final partial in [
        'Here:\n![',
        '![Ima',
        '![Image]',
        '![Image](',
        '![Image](/tmp/sho',
        '![Image](/tmp/a.png "Ti',
      ]) {
        final last = partial.split('\n').last;
        expect(
          KitMarkdownImage.withoutUnfinished(last),
          '',
          reason: '"$last" is unfinished',
        );
      }
      expect(
        KitMarkdownImage.withoutUnfinished('![a](/tmp/a.png) and ![b](/tm'),
        '![a](/tmp/a.png) and ',
      );
      // Finished pictures and plain words stay.
      for (final done in [
        '![a](/tmp/a.png)',
        'Great!',
        'x ![a](/tmp/a.png) y',
        'no picture',
      ]) {
        expect(KitMarkdownImage.withoutUnfinished(done), done);
      }
    });
  });

  group('tiles', () {
    Future<void> pump(
      WidgetTester tester,
      Widget child, {
      Locale locale = const Locale('en'),
    }) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: AppTheme.dark(),
          locale: locale,
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
      await tester.pump();
    }

    /// The host's file links: every path in [readable] validates, [read]
    /// serves its thumbnail bytes.
    KitMarkdownFileLinks links({
      Set<String> readable = const {},
      Future<Uint8List?> Function(String path)? read,
      List<String>? opened,
      List<String>? validated,
    }) => KitMarkdownFileLinks(
      validate: (path) async {
        validated?.add(path);
        return readable.contains(path);
      },
      open: (path) => opened?.add(path),
      readImage: read,
    );

    /// The chip that carries [words]: a tap lands on its tap zone, which
    /// sits over the text.
    Finder chip(String words) =>
        find.ancestor(of: find.text(words), matching: find.byType(KitChip));

    /// A picture inside a sentence: an openable run paints as rich text.
    Finder inline(String words) => find.text(words, findRichText: true);

    Finder tile(int index) => find.byKey(ValueKey('kit-md-image-$index'));

    /// Nothing of the syntax is on screen.
    void expectNoSyntax() {
      for (final fragment in ['![', '](', '(/tmp', '(https', 'data:']) {
        expect(
          find.textContaining(fragment, findRichText: true),
          findsNothing,
          reason: 'raw "$fragment" shown',
        );
      }
    }

    const reply = '''
Here are the screens:
![Image](/tmp/shots/home.png)
![Image](/tmp/shots/settings.png)

![Image](/tmp/shots/chat.png)
Done.''';

    testWidgets('consecutive pictures are one group, never raw syntax', (
      tester,
    ) async {
      await pump(
        tester,
        KitMarkdown(
          reply,
          fileLinks: links(
            readable: {
              '/tmp/shots/home.png',
              '/tmp/shots/settings.png',
              '/tmp/shots/chat.png',
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      expectNoSyntax();
      expect(find.text('Here are the screens:'), findsOneWidget);
      expect(find.text('Done.'), findsOneWidget);
      // Three pictures, one wrapping group (blank line between them or not).
      expect(tile(0), findsOneWidget);
      expect(tile(1), findsOneWidget);
      expect(tile(2), findsOneWidget);
      expect(tile(3), findsNothing);
      final group = find.ancestor(of: tile(0), matching: find.byType(Wrap));
      expect(group, findsOneWidget);
      expect(
        find.descendant(of: group, matching: find.byType(KitChip)),
        findsNWidgets(3),
      );
      // The words are the app's own for an empty description, with the name.
      expect(find.text(_words('Image', 'home.png')), findsOneWidget);
      expect(find.text(_words('Image', 'chat.png')), findsOneWidget);
    });

    testWidgets('a file the host cannot confirm is a chip that does nothing', (
      tester,
    ) async {
      final opened = <String>[];
      await pump(
        tester,
        KitMarkdown(
          '![Image](/tmp/gone.png)',
          fileLinks: links(opened: opened, read: (_) async => null),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(_words('Image', 'gone.png')), findsOneWidget);
      expect(find.byType(KitImage), findsNothing);
      expect(
        tester.widget<KitChip>(find.byType(KitChip)).kind,
        KitChipKind.plain,
      );
      await tester.tap(chip(_words('Image', 'gone.png')));
      expect(opened, isEmpty);
      // Flush the bounded re-validation timers.
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(seconds: 23));
      }
    });

    testWidgets('a file with no way to read it opens as a named chip', (
      tester,
    ) async {
      final opened = <String>[];
      await pump(
        tester,
        KitMarkdown(
          '![Home screen](/tmp/shots/home.png)',
          fileLinks: links(readable: {'/tmp/shots/home.png'}, opened: opened),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitImage), findsNothing);
      expect(
        find.text(_words('Home screen', 'home.png', true)),
        findsOneWidget,
      );
      expect(
        tester.widget<KitChip>(find.byType(KitChip)).kind,
        KitChipKind.action,
      );
      await tester.tap(chip(_words('Home screen', 'home.png', true)));
      expect(opened, ['/tmp/shots/home.png']);
    });

    testWidgets('a file the host reads shows a thumbnail that opens it', (
      tester,
    ) async {
      final opened = <String>[];
      final reads = <String>[];
      await pump(
        tester,
        KitMarkdown(
          '![Image](/tmp/shots/home.png)\n![Image](/tmp/shots/chat.svg)',
          fileLinks: links(
            readable: {'/tmp/shots/home.png', '/tmp/shots/chat.svg'},
            opened: opened,
            read: (path) async {
              reads.add(path);
              return null;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      // The PNG is a thumbnail named by its file; the SVG, which Flutter
      // cannot draw, stays a chip that still opens.
      final thumbnail = find.descendant(
        of: tile(0),
        matching: find.byType(KitImage),
      );
      expect(thumbnail, findsOneWidget);
      expect(find.text(KitBidi.ltr('home.png')), findsOneWidget);
      expect(find.text(_words('Image', 'chat.svg')), findsOneWidget);
      expect(
        find.descendant(of: tile(1), matching: find.byType(KitImage)),
        findsNothing,
      );
      expect(reads, ['/tmp/shots/home.png']);

      await tester.tap(thumbnail);
      expect(opened, ['/tmp/shots/home.png']);
      await tester.tap(chip(_words('Image', 'chat.svg')));
      expect(opened, ['/tmp/shots/home.png', '/tmp/shots/chat.svg']);
    });

    testWidgets('the thumbnail is named for screen readers', (tester) async {
      final handle = tester.ensureSemantics();
      await pump(
        tester,
        KitMarkdown(
          '![Home screen](/tmp/shots/home.png)',
          fileLinks: links(
            readable: {'/tmp/shots/home.png'},
            read: (_) async => null,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester.getSemantics(
          find.descendant(of: tile(0), matching: find.byType(KitImage)),
        ),
        isSemantics(
          label: 'Preview Home screen',
          isButton: true,
          hasTapAction: true,
        ),
      );
      handle.dispose();
    });

    testWidgets('a web picture is never loaded; a tap asks before it opens', (
      tester,
    ) async {
      await pump(
        tester,
        KitMarkdown(
          '![Image](https://cdn.example.com/a.png)\n'
          '![Architecture](https://docs.example.org/arch.png)',
          fileLinks: links(read: (_) async => fail('must not read a URL')),
        ),
      );
      await tester.pumpAndSettle();

      expectNoSyntax();
      expect(find.byType(KitImage), findsNothing);
      expect(find.byType(Image), findsNothing);
      expect(find.text(_words('Image', 'cdn.example.com')), findsOneWidget);
      expect(
        find.text(_words('Architecture', 'docs.example.org', true)),
        findsOneWidget,
      );

      await tester.tap(chip(_words('Image', 'cdn.example.com')));
      await tester.pumpAndSettle();
      // The existing link gate (SEC-1): it names the host and waits.
      final sheet = find.byKey(const ValueKey('external-link-confirm'));
      expect(sheet, findsOneWidget);
      expect(
        find.descendant(
          of: sheet,
          matching: find.textContaining('cdn.example.com'),
        ),
        findsWidgets,
      );
    });

    testWidgets('a data picture and an odd scheme are named, not openable', (
      tester,
    ) async {
      await pump(
        tester,
        KitMarkdown(
          '![Pixel](data:image/png;base64,iVBORw0KGgo=)\n'
          '![](javascript:alert(1))',
          fileLinks: links(),
        ),
      );
      await tester.pumpAndSettle();
      expectNoSyntax();
      expect(find.text(_words('Pixel', null, true)), findsOneWidget);
      expect(find.text('Image'), findsOneWidget);
      for (final chip in tester.widgetList<KitChip>(find.byType(KitChip))) {
        expect(chip.kind, KitChipKind.plain);
      }
      expect(find.byType(KitImage), findsNothing);
    });

    testWidgets('a picture inside a sentence is a link-like run', (
      tester,
    ) async {
      final opened = <String>[];
      await pump(
        tester,
        KitMarkdown(
          'See ![the chart](/tmp/c.png) above, or ![](https://example.com/x.png).',
          fileLinks: links(readable: {'/tmp/c.png'}, opened: opened),
        ),
      );
      await tester.pumpAndSettle();
      expectNoSyntax();
      expect(find.byType(KitChip), findsNothing);
      expect(find.byType(KitImage), findsNothing);
      expect(inline(_words('the chart', 'c.png', true)), findsOneWidget);
      expect(inline(_words('Image', 'example.com')), findsOneWidget);
      expect(find.textContaining('See ', findRichText: true), findsOneWidget);

      await tester.tap(inline(_words('the chart', 'c.png', true)));
      expect(opened, ['/tmp/c.png']);
    });

    testWidgets('a picture in a list item or a table cell has no syntax', (
      tester,
    ) async {
      await pump(
        tester,
        KitMarkdown(
          '- first ![a](/tmp/a.png)\n\n'
          '| Name | Shot |\n| --- | --- |\n| home | ![home](/tmp/h.png) |',
          fileLinks: links(readable: {'/tmp/a.png', '/tmp/h.png'}),
        ),
      );
      await tester.pumpAndSettle();
      expectNoSyntax();
      expect(inline(_words('a', 'a.png', true)), findsOneWidget);
      expect(inline(_words('home', 'h.png', true)), findsOneWidget);
    });

    testWidgets('a half-written picture on the last line shows nothing', (
      tester,
    ) async {
      await pump(
        tester,
        KitMarkdown(
          'Here it is:\n![Image](/tmp/a.png)\n![Image](/tmp/sho',
          fileLinks: links(readable: {'/tmp/a.png', '/tmp/shot.png'}),
        ),
      );
      await tester.pumpAndSettle();
      expectNoSyntax();
      expect(find.text(_words('Image', 'a.png')), findsOneWidget);
      expect(find.byType(KitChip), findsOneWidget);

      // The rest arrives: the second picture joins the group.
      await pump(
        tester,
        KitMarkdown(
          'Here it is:\n![Image](/tmp/a.png)\n![Image](/tmp/shot.png)',
          fileLinks: links(readable: {'/tmp/a.png', '/tmp/shot.png'}),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(KitChip), findsNWidgets(2));
    });

    testWidgets('an isolated preview draws the tiles and takes no tap', (
      tester,
    ) async {
      final opened = <String>[];
      await pump(
        tester,
        KitMarkdown(
          '![Image](https://example.com/a.png)\n![Image](/tmp/a.png)',
          interactive: false,
          fileLinks: links(readable: {'/tmp/a.png'}, opened: opened),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(_words('Image', 'example.com')), findsOneWidget);
      for (final chip in tester.widgetList<KitChip>(find.byType(KitChip))) {
        expect(chip.kind, KitChipKind.plain);
      }
      await tester.tap(
        find.text(_words('Image', 'example.com')),
        warnIfMissed: false,
      );
      expect(find.byKey(const ValueKey('external-link-confirm')), findsNothing);
      expect(opened, isEmpty);
    });

    testWidgets('selecting and copying a reply gives the description', (
      tester,
    ) async {
      final copied = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied.add((call.arguments as Map)['text'] as String);
          }
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await pump(
        tester,
        KitMarkdown(
          'Before.\n\n![Home screen](/tmp/shots/home.png)\n\nAfter.',
          fileLinks: links(readable: {'/tmp/shots/home.png'}),
        ),
      );
      await tester.pumpAndSettle();

      final region = tester.state<SelectableRegionState>(
        find.byType(SelectableRegion),
      );
      region.selectAll();
      await tester.pump();
      // The region's own Copy action, from inside it.
      Actions.invoke(
        tester.element(find.textContaining('Before.', findRichText: true)),
        CopySelectionTextIntent.copy,
      );
      await tester.pump();

      expect(copied, isNotEmpty);
      final text = copied.last;
      expect(text, contains('Before.'));
      expect(text, contains('Home screen'));
      expect(text, contains('After.'));
      expect(text, isNot(contains('/tmp')));
      expect(text, isNot(contains('![')));
    });

    testWidgets('a thumbnail decodes from the host bytes', (tester) async {
      final png = await tester.runAsync(() async {
        final recorder = ui.PictureRecorder();
        Canvas(recorder).drawRect(
          const Rect.fromLTWH(0, 0, 8, 8),
          Paint()..color = const Color(0xFF3D6BFF),
        );
        final image = await recorder.endRecording().toImage(8, 8);
        final data = await image.toByteData(format: ui.ImageByteFormat.png);
        image.dispose();
        return data!.buffer.asUint8List();
      });
      await pump(
        tester,
        KitMarkdown(
          '![Image](/tmp/a.png)',
          fileLinks: links(readable: {'/tmp/a.png'}, read: (_) async => png),
        ),
      );
      final raw = find.descendant(
        of: find.byType(KitImage),
        matching: find.byType(RawImage),
      );
      ui.Image? decoded;
      for (var i = 0; i < 50 && decoded == null; i++) {
        await tester.runAsync(
          () => Future<void>.delayed(const Duration(milliseconds: 20)),
        );
        await tester.pump();
        if (raw.evaluate().isNotEmpty) {
          decoded = tester.widget<RawImage>(raw).image;
        }
      }
      expect(decoded, isNotNull, reason: 'the thumbnail never decoded');
    });
  });
}
