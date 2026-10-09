// Coverage ratchet for what the Codex app-server tells the Account page
// (account/read, rate limits, usage and the device sign-in). The answers are
// served to the app's real Codex account session and the real page is drawn
// from what it parsed (see paseo_coverage_support.dart for the rules).
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/codex/account.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/state/agent_account.dart';
import 'package:opencode_mobile/ui/screens/agent_account_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/account_fakes.dart';
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final family = CoverageFamily('codex_account', prefix: '');

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('codex account · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final payload = variant['payload'] as Map;
      final socket = AccountSocket();
      socket.onSend = (frame) {
        switch (frame['method']) {
          case 'initialize':
            socket.result(frame['id'], {
              'userAgent': 'codex_cli_rs/0.153.4 (fixture)',
            });
          case 'account/read':
            socket.result(
              frame['id'],
              Map<String, dynamic>.from(payload['read'] as Map),
            );
          case 'account/rateLimits/read':
            socket.result(frame['id'], {
              'rateLimitsByLimitId': {
                'codex': Map<String, dynamic>.from(
                  (payload['limits'] ?? <String, dynamic>{}) as Map,
                ),
              },
            });
          case 'account/usage/read':
            socket.result(
              frame['id'],
              Map<String, dynamic>.from(
                (payload['usage'] ?? {'summary': {}}) as Map,
              ),
            );
          case 'account/login/start':
            socket.result(
              frame['id'],
              Map<String, dynamic>.from(payload['login'] as Map),
            );
        }
      };
      final transport = CodexTransport(
        endpoint: 'wss://fixture.invalid',
        token: 'fixture',
        socketFactory: (_, _) async => socket,
        requestTimeout: const Duration(seconds: 2),
      );
      await tester.runAsync(transport.connect);
      final session = CodexAccountSession(transport, () => true);
      final controller = AgentAccountController(session);
      await tester.runAsync(controller.refresh);
      final boundary = GlobalKey();
      final screens = ListsScreens(
        tester,
        await listsController(),
        boundary,
        'codex-account-$id',
      );
      await screens.show(
        AgentAccountPanel(controller: controller, profileName: 'My Codex host'),
        then: () async {
          final signIn = find.text('Sign in with ChatGPT');
          if (signIn.evaluate().isNotEmpty) {
            await tester.ensureVisible(signIn);
            await tester.tap(signIn);
            await tester.runAsync(
              () => Future<void>.delayed(const Duration(milliseconds: 100)),
            );
          }
        },
      );
      File(
        'build/coverage/codexaccount_$id.txt',
      ).writeAsStringSync(flat(screens.text));
      final problems = checkCase(family, variant, screens.text);
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
      await tester.runAsync(transport.close);
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screens.text)}');
    });
  }
}
