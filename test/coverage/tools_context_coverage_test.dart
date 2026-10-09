// Coverage ratchet for the conversation's active context (what the model
// reads on its next turn): the message cases of the chat ratchet, read by the
// app's real OpenCode 2 message parser and context mapper, and drawn by the
// real Active context page with each message opened (see
// paseo_coverage_support.dart for the rules).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/active_context_mapper.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/active_context_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';
import 'tools_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    mockNoTermux();
  });
  final family = CoverageFamily('tools_context', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('active context · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final raw = (variant['payload'] as List).cast<Map>();
      final repo = ToolsRepo()
        ..context = [
          for (final message in raw)
            mapActiveContext(
              Api2Message.fromJson(Map<String, dynamic>.from(message))!,
            ),
        ];
      final (store, controller) = await toolsConnection(repo);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        serversApp(
          boundary,
          store,
          controller,
          home: ActiveContextScreen(controller: controller, sessionID: 'ses_1'),
        ),
      );
      await frames(tester, 14);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'tools_ctx_$id');
      // Open each message, one at a time.
      final count = find
          .byWidgetPredicate(
            (w) =>
                w.key is ValueKey &&
                '${(w.key! as ValueKey).value}'.startsWith(
                  'active-context-msg',
                ),
          )
          .evaluate()
          .length;
      final rows = find.descendant(
        of: find.byKey(const ValueKey('active-context-list')),
        matching: find.byType(KitRow),
      );
      final total = rows.evaluate().length;
      for (var i = 0; i < total + count; i++) {
        final current = find.descendant(
          of: find.byKey(const ValueKey('active-context-list')),
          matching: find.byType(KitRow),
        );
        if (i >= current.evaluate().length) break;
        await tester.ensureVisible(current.at(i));
        await tester.tap(current.at(i), warnIfMissed: false);
        await frames(tester, 10);
        seen.add(screenText(tester).join('\n'));
        final more = find.text('Details');
        if (more.evaluate().isNotEmpty) {
          await tester.tap(more.last, warnIfMissed: false);
          await frames(tester, 6);
          seen.add(screenText(tester).join('\n'));
        }
        final navigator = tester.state<NavigatorState>(
          find.byType(Navigator).first,
        );
        await navigator.maybePop();
        await frames(tester, 8);
      }
      final screen = seen.join('\n');
      // The page draws what the model reads, which differs by message kind:
      // a "shown" field must be on screen wherever it is drawn at all, and
      // somewhere (the last test of this file checks that).
      final onScreen = flat(screen);
      final judged = {
        ...variant,
        'fields': [
          for (final field in (variant['fields'] as List).cast<Map>())
            {
              ...field,
              if (family.ledger[field['key']] == 'shown')
                'probes': [
                  for (final probe in (field['probes'] as List).cast<String>())
                    if (onScreen.contains(flat(probe))) probe,
                ],
            },
        ],
      };
      final problems = checkCase(family, judged, screen, primaryText: screen);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }

  test('every "shown" field is on screen in at least one case', () {
    final seenAnywhere = <String>{
      for (final row in family.report)
        if ((row['visible'] as List).any((v) => v == true))
          row['field'] as String,
    };
    final never = [
      for (final entry in family.ledger.entries)
        if (entry.value == 'shown' && !seenAnywhere.contains(entry.key))
          entry.key,
    ];
    expect(never, isEmpty, reason: '"shown" fields no case ever drew');
  });
}
