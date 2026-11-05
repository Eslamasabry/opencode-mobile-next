// Fake-only screenshots of Antigravity sign-in and the Gemini way forward.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sheet.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import 'kit/kit_gallery.dart' show loadKitGalleryFonts;

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final locale in const [Locale('en'), Locale('ar')]) {
    for (final id in ['antigravity', 'gemini']) {
      testWidgets('$id guidance ${locale.languageCode}', (tester) async {
        tester.view.physicalSize = const Size(412, 915);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        final boundary = GlobalKey();
        final host =
            FakeChatsHost(
                FakeChatFeedSource(
                  items: const [],
                  projects: [project('alpha')],
                  lastUsed: '/root/projects/alpha',
                ),
              )
              ..phoneAgents = FakePhoneAgentsSource(
                rows: [
                  agentRowFor(
                    id,
                    id == 'antigravity'
                        ? FakeAgentStage.signedOut
                        : FakeAgentStage.notInstalled,
                  ),
                ],
              );
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: chatsApp(host, const NewChatScreen(), locale: locale),
          ),
        );
        await tester.pumpAndSettle();
        unawaited(
          showAgentSheet(
            tester.element(find.byType(NewChatScreen)),
            agentId: id,
            step: id == 'antigravity'
                ? AgentSheetStep.signIn
                : AgentSheetStep.setup,
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('${id}_guidance_${locale.languageCode}.png'),
        );
        await tester.pumpWidget(const SizedBox.shrink());
      });
    }
  }
}
