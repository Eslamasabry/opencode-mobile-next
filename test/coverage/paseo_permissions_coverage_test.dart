// Coverage ratchet for what a Paseo agent asks the person: permission
// requests, plans and questions (see paseo_coverage_support.dart for the
// rules). Each case is a request the way Paseo sends it, pushed through the
// app's real Paseo gateway, which turns it into a permission card or a
// question card; the real conversation screen draws it and Details is opened.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../paseo_gateway_test.dart' show FakeDaemon, agentJson;
import 'paseo_chat_harness.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final family = CoverageFamily('permissions');
  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('paseo request · $id', (tester) async {
      final request = (variant['payload'] as Map).cast<String, dynamic>();
      final daemon = FakeDaemon();
      final agent = agentJson(checkoutSessionID, cwd: '/work/app');
      daemon.handlers['fetch_agents_request'] = (_) => (
        'fetch_agents_response',
        {
          'entries': [
            {'agent': agent},
          ],
          'pageInfo': {
            'nextCursor': null,
            'prevCursor': null,
            'hasMore': false,
          },
        },
      );
      final gateway = PaseoGateway(
        directory: '/work/app',
        transport: PaseoTransport(
          endpoint: 'ws://127.0.0.1:6767',
          socketFactory: (_, _) async => daemon,
        ),
      );
      addTearDown(gateway.close);
      // The agent is Codex for the one case that is Codex's.
      agent['provider'] = request['provider'];
      gateway.openEventChannel(onEvent: (_) {}, onStatus: (_) {});
      await tester.runAsync(() async {
        await gateway.sessions();
        daemon.push('agent_permission_request', {
          'agentId': checkoutSessionID,
          'request': request,
        });
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      final permissions = await tester.runAsync(gateway.pendingPermissions);
      final questions = await tester.runAsync(gateway.pendingQuestionsV2);
      expect(
        permissions!.length + questions!.length,
        1,
        reason: 'the app turns the request into exactly one card',
      );
      final boundary = GlobalKey();
      await pumpCoverageChat(
        tester,
        boundary,
        messages: const [],
        capabilities: gateway.capabilities,
        setUp: (controller) {
          controller.permissions = {for (final p in permissions) p.id: p};
          controller.questions = {
            for (final q in questions.map(PendingQuestion.fromJson)) q.id: q,
          };
        },
      );
      final details = find.byKey(const Key('permission-card-review'));
      if (details.evaluate().isNotEmpty) {
        await tester.tap(details.first, warnIfMissed: false);
        await frames(tester);
      }
      // What is readable before any fold opens, then with Details open.
      final primary = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'perm_$id');
      final fold = find.text('Details');
      if (find.byKey(const Key('permission-sheet')).evaluate().isNotEmpty &&
          fold.evaluate().isNotEmpty) {
        await tester.tap(fold.last, warnIfMissed: false);
        await frames(tester);
      }
      final screen = screenText(tester).join('\n');
      final problems = checkCase(family, variant, screen, primaryText: primary);
      // Paseo keeps a standing rule only when the agent suggested one: with
      // none, offering "Always allow" would be a button that cannot work.
      final suggested =
          (request['suggestions'] as List? ?? const []).isNotEmpty;
      if (!suggested && request['kind'] == 'tool') {
        expect(
          flat(screen),
          isNot(contains('Always allow')),
          reason: 'no suggested rule, so no Always allow',
        );
      }
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}
