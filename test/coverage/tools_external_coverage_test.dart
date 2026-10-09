// Coverage ratchet for an external (A2A) agent: the card it published as the
// app saved it, and a task result, drawn by the real agent page and task page
// (see paseo_coverage_support.dart for the rules).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/external_agent.dart';
import 'package:opencode_mobile/state/external_agents.dart';
import 'package:opencode_mobile/ui/screens/external_agents_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../external_agent_state_test.dart' show FakeExternalGateway;
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('tools_external', prefix: '');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('external agent · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final secrets = <String, String>{};
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
            (call) async {
              final args = Map<String, dynamic>.from(call.arguments as Map);
              switch (call.method) {
                case 'write':
                  secrets[args['key'] as String] = args['value'] as String;
                  return null;
                case 'read':
                  return secrets[args['key']];
                case 'delete':
                  secrets.remove(args['key']);
                  return null;
                default:
                  return null;
              }
            },
          );
      SharedPreferences.setMockInitialValues({});
      final store = ExternalAgentStore(
        await SharedPreferences.getInstance(),
        const FlutterSecureStorage(),
      );
      addTearDown(store.dispose);
      final payload = Map<String, dynamic>.from(variant['payload'] as Map);
      final card = ExternalAgentCard.fromJson(
        Map<String, dynamic>.from(payload['card'] as Map),
      );
      final t = Map<String, dynamic>.from(payload['task'] as Map);
      final task = ExternalTask(
        id: t['id'] as String,
        contextId: t['contextId'] as String,
        state: ExternalTaskState.values.byName(t['state'] as String),
        statusMessageId: t['statusMessageId'] as String,
        parts: [
          for (final p in (t['parts'] as List).cast<Map>())
            ExternalResultPart(
              text: p['text'] as String?,
              url: p['url'] as String?,
              name: p['name'] as String?,
            ),
        ],
        omittedContent: t['omittedContent'] == true,
      );
      final profile = await store.add(card, 'fixture-token');
      await store.saveTask(
        profile.id,
        ExternalTaskRecord(
          localId: 'saved',
          title: 'Welcome screen colour',
          created: DateTime(2026, 9, 8),
          task: task,
        ),
      );
      final gateway = FakeExternalGateway()..next = task;
      final (sstore, controller) = await serversState();
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        serversApp(
          boundary,
          sstore,
          controller,
          home: ExternalAgentDetailScreen(
            store: store,
            profile: profile,
            gatewayFactory: () => gateway,
          ),
        ),
      );
      await frames(tester, 12);
      final seen = <String>[screenText(tester).join('\n')];
      await writeCasePng(tester, boundary, 'tools_$id');
      // The agent's Details fold, then the saved task's page.
      final fold = find.text('Details');
      if (fold.evaluate().isNotEmpty) {
        await tester.ensureVisible(fold.first);
        await tester.tap(fold.first, warnIfMissed: false);
        await frames(tester, 8);
        seen.add(screenText(tester).join('\n'));
      }
      final row = find.byKey(const ValueKey('external-task-saved'));
      await tester.ensureVisible(row);
      await tester.tap(row);
      await frames(tester, 14);
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, boundary, 'tools_${id}_task');
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      // The agent's key never reaches the screen.
      if (screen.contains('fixture-token')) {
        problems.add('the key is on screen');
      }
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
