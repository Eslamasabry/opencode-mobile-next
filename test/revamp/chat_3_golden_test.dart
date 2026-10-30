// Gallery of unit chat-3 (the composer and its prompt pages; map pages
// embedded-composer, prompt-tools-sheet, prompt-history-sheet and
// prompt-editor): the glass pill with a draft, busy with Stop and Send,
// with nothing signed in, the "+" sheet, the history sheet and the
// full-screen editor, at 412x915 and 1280x800, dark and light, with the
// app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/chat_3_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'chat_3_support.dart';
import '../support/composer_tools.dart';

typedef _Scene =
    Future<void> Function(WidgetTester tester, ConnectionController conn);

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pumpAndSettle();
}

Future<void> _openPrompts(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  await Scrollable.ensureVisible(
    tester.element(find.byKey(const Key('composer-tools-prompts'))),
    alignment: .5,
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tools-prompts')));
  await tester.pumpAndSettle();
}

final _scenes = <String, (_Scene?, _Scene)>{
  'chat_3_composer_text': (
    null,
    (tester, conn) => _type(tester, 'Tidy the parser and add a test'),
  ),
  'chat_3_composer_busy': (
    null,
    (tester, conn) async {
      conn.busySessions.add('session-1');
      conn.notifyListeners();
      await tester.pumpAndSettle();
      await _type(tester, 'Also check the lexer');
    },
  ),
  'chat_3_composer_sign_in': (
    (tester, conn) async {
      conn.catalog = const CatalogSnapshot(
        providers: [],
        models: [],
        agents: [],
      );
    },
    (tester, conn) async {},
  ),
  'chat_3_tools_sheet': (
    null,
    (tester, conn) async {
      await openComposerTools(tester);
      await tester.pumpAndSettle();
    },
  ),
  'chat_3_history_sheet': (
    null,
    (tester, conn) async {
      await _openPrompts(tester);
      await Scrollable.ensureVisible(
        tester.element(find.byKey(const Key('composer-tool-history'))),
        alignment: .5,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-history')));
      await tester.pumpAndSettle();
    },
  ),
  'chat_3_prompt_editor': (
    null,
    (tester, conn) async {
      await _type(tester, 'Refactor the parser.\n\nKeep the public API.');
      await tester.tap(find.byKey(const Key('prompt-editor-button')));
      await tester.pumpAndSettle();
    },
  ),
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(chat3MockSecureStorage);

  for (final size in const [Size(412, 915), Size(1280, 800)]) {
    for (final light in [false, true]) {
      for (final MapEntry(key: name, value: (before, scene))
          in _scenes.entries) {
        final sized = size.width == 412
            ? ''
            : '_${size.width.toInt()}x${size.height.toInt()}';
        final file = '$name${sized}_${light ? 'light' : 'dark'}';
        testWidgets(file, (tester) async {
          final api = Chat3Api()
            ..transcript = [
              chat3Prompt('m1', 'Review the parser for edge cases'),
              chat3Prompt('m2', 'Write the migration notes'),
            ];
          final conn = await chat3Controller(api: api);
          addTearDown(conn.dispose);
          final boundary = GlobalKey();
          debugDefaultTargetPlatformOverride =
              TargetPlatform.android; // ARCH-11
          try {
            if (before != null) await before(tester, conn);
            await pumpChat3(
              tester,
              conn,
              size: size,
              light: light,
              boundary: boundary,
            );
            await scene(tester, conn);
            expect(tester.takeException(), isNull);
            await expectLater(
              find.byKey(boundary),
              matchesGoldenFile('goldens/$file.png'),
            );
          } finally {
            await tester.pumpWidget(const SizedBox.shrink());
            await tester.pump();
            debugDefaultTargetPlatformOverride = null;
          }
        });
      }
    }
  }
}
