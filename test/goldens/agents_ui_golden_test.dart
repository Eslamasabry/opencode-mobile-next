// Golden renders of the agents UI: the agent sheet (the choice, install
// progress, sign-in), the status lines on Conversations, the resume notice,
// and Settings › This phone › Agents with the phone check, at 412x915 and
// 1280x800, dark and light, on Material with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/agents_ui_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/agents/agents_section.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/agents_fakes.dart';
import '../support/chats_fakes.dart';

final _now = DateTime(2026, 10, 3, 12);

enum _Scene {
  sheetList('agents_sheet_list'),
  sheetInstall('agents_sheet_install'),
  sheetSignIn('agents_sheet_signin'),
  homeStatus('agents_home_status'),
  homeResume('agents_home_resume'),
  settingsAgents('agents_settings_agents'),
  settingsCheck('agents_settings_check');

  const _Scene(this.name);
  final String name;
}

Future<void> _mount(
  WidgetTester tester,
  _Scene scene, {
  required bool light,
  required Size size,
  required GlobalKey boundary,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final agents = FakePhoneAgentsSource(
    rows: [
      agentRowFor('claude', switch (scene) {
        _Scene.sheetList || _Scene.sheetInstall => FakeAgentStage.notInstalled,
        _Scene.sheetSignIn => FakeAgentStage.signedOut,
        _ => FakeAgentStage.ready,
      }),
      agentRowFor('gemini', FakeAgentStage.notInstalled),
    ],
  );
  final items = [
    chat(
      'a',
      'Review the diff',
      at: _now.subtract(const Duration(minutes: 20)),
      agentId: 'claude',
      agentLabel: 'Claude Code',
      preview: 'Two files changed.',
    ),
    chat(
      'b',
      'Explain the build',
      at: _now.subtract(const Duration(hours: 3)),
      preview: 'It runs gradle first.',
    ),
  ];
  agents.noticeFor['a'] = const AgentResumeNotice(
    canReopen: false,
    label: "Can't reopen old chats",
    note: 'Starts a new chat',
    requiresAcknowledgement: true,
  );
  if (scene == _Scene.homeStatus) {
    agents.lines = [
      PhoneAgentStatusLine(
        agentId: 'claude',
        agentName: 'Claude Code',
        kind: PhoneAgentStatusLineKind.limitReached,
        resetAt: DateTime(2026, 10, 3, 15),
      ),
      const PhoneAgentStatusLine(
        agentId: 'claude',
        agentName: 'Claude Code',
        kind: PhoneAgentStatusLineKind.signedOut,
      ),
      const PhoneAgentStatusLine(
        agentId: 'claude',
        agentName: 'Claude Code',
        kind: PhoneAgentStatusLineKind.stopped,
      ),
    ];
  }
  if (scene == _Scene.settingsCheck) {
    agents.nextCheck = AgentPhoneCheckResult(
      agentId: 'claude',
      architecture: AgentArchitecture.arm64,
      passed: false,
      completed: const [
        AgentPhoneCheckStep.install,
        AgentPhoneCheckStep.version,
      ],
      failure: AgentHostFailure.daemon,
    );
  }
  final host = FakeChatsHost(
    FakeChatFeedSource(
      items: items,
      projects: [project('alpha', kind: 'dart')],
      lastUsed: '/root/projects/alpha',
    ),
  )..phoneAgents = agents;
  final Widget home = switch (scene) {
    _Scene.sheetList ||
    _Scene.sheetInstall ||
    _Scene.sheetSignIn => const NewChatScreen(),
    _Scene.homeStatus || _Scene.homeResume => const ChatsHomeScreen(),
    _ => ListView(
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 16),
          child: const AgentsSection(),
        ),
      ],
    ),
  };
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: chatsApp(host, home, light: light),
    ),
  );
  await tester.pumpAndSettle();
  switch (scene) {
    case _Scene.sheetList:
      await tester.tap(find.byKey(const ValueKey('chats-new-agent')));
    case _Scene.sheetInstall:
      await tester.tap(find.byKey(const ValueKey('chats-new-agent')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-install')));
    case _Scene.sheetSignIn:
      await tester.tap(find.byKey(const ValueKey('chats-new-agent')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('agents-choice-claude')));
    case _Scene.homeResume:
      await tester.tap(find.text(KitBidi.auto('Review the diff')));
    case _Scene.settingsCheck:
      await tester.tap(find.byKey(const ValueKey('agents-check-phone')));
    case _Scene.homeStatus || _Scene.settingsAgents:
      break;
  }
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  const sizes = {'': Size(412, 915), '_1280x800': Size(1280, 800)};
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(key: suffix, value: size) in sizes.entries) {
      for (final scene in _Scene.values) {
        final name = '${scene.name}$suffix';
        testWidgets('$name · $mode', (tester) async {
          final boundary = GlobalKey();
          debugDefaultTargetPlatformOverride =
              TargetPlatform.android; // ARCH-11
          await withClock(Clock.fixed(_now), () async {
            await _mount(
              tester,
              scene,
              light: light,
              size: size,
              boundary: boundary,
            );
          });
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('${name}_$mode.png'),
          );
          await tester.pumpWidget(const SizedBox.shrink());
          debugDefaultTargetPlatformOverride = null;
        });
      }
    }
  }
}
