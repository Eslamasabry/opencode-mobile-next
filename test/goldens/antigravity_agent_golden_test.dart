// Golden renders of the phone-agent sheet for Antigravity (sign-in) and Gemini
// CLI (the way forward to Antigravity), over New conversation, in English and
// Arabic, dark and light, at 412x915 with the app's real fonts and Android
// platform behaviour. Fake-only: no account or network is involved.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/antigravity_agent_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/agents/agent_sheet.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';
import 'kit/kit_gallery.dart' show loadKitGalleryFonts;

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final locale in const [Locale('en'), Locale('ar')]) {
      for (final id in ['antigravity', 'gemini']) {
        testWidgets('$id guidance ${locale.languageCode} · $mode', (
          tester,
        ) async {
          debugDefaultTargetPlatformOverride = TargetPlatform.android;
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
          try {
            await tester.pumpWidget(
              RepaintBoundary(
                key: boundary,
                child: chatsApp(
                  host,
                  const NewChatScreen(),
                  light: light,
                  locale: locale,
                  theme: AppTheme.forLocale(captureTheme(light: light), locale),
                ),
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
              matchesGoldenFile(
                'agents_${id}_guidance_${locale.languageCode}_$mode.png',
              ),
            );
          } finally {
            await tester.pumpWidget(const SizedBox.shrink());
            debugDefaultTargetPlatformOverride = null;
          }
        });
      }
    }
  }
}
