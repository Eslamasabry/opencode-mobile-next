// Golden renders of agent cards: a choice, a form and a report as they sit in
// a conversation, and a waiting choice under its row in the Conversations
// list (secondary buttons), at 412x915, dark and light, on Material with the
// app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/agent_card_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart' show ChatStatus;
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'package:opencode_mobile/ui/widgets/agent_card_view.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/agent_card_fakes.dart';
import '../support/chats_fakes.dart';

enum _Scene {
  chatChoice('agent_card_chat_choice'),
  chatForm('agent_card_chat_form'),
  chatReport('agent_card_chat_report'),
  listChoice('agent_card_list_choice');

  const _Scene(this.name);
  final String name;
}

GenUiCard _card(_Scene scene) => switch (scene) {
  _Scene.chatChoice || _Scene.listChoice => agentCard(
    title: 'Which database should I use?',
    body: const [
      GenUiText(text: 'Two fit this project. Pick the one you want to keep.'),
    ],
    ask: choiceAsk(),
  ),
  _Scene.chatForm => agentCard(
    title: 'Set up the deploy',
    body: const [GenUiText(text: 'I need three things before I start.')],
    ask: GenUiFormAsk(
      submitLabel: 'Save setup',
      fields: [
        GenUiField(
          id: 'name',
          label: 'Project name',
          type: GenUiFieldType.text,
          required: true,
          placeholder: 'atlas',
        ),
        GenUiField(
          id: 'replicas',
          label: 'Replicas',
          type: GenUiFieldType.number,
          defaultValue: 2,
        ),
        GenUiField(id: 'public', label: 'Public', type: GenUiFieldType.toggle),
      ],
    ),
  ),
  _Scene.chatReport => agentCard(
    title: 'Build finished',
    body: [
      GenUiKeyValue(
        rows: const [
          GenUiKeyValueRow(key: 'Duration', value: '4 min 12 s'),
          GenUiKeyValueRow(key: 'Files changed', value: '7'),
        ],
      ),
      GenUiChart(
        kind: GenUiChartKind.bar,
        unit: 'ms',
        labels: const ['Mon', 'Tue', 'Wed'],
        series: [
          GenUiChartSeries(name: 'Build', values: const [420, 380, 510]),
        ],
      ),
      const GenUiCallout(
        tone: GenUiCalloutTone.success,
        text: 'All tests pass.',
      ),
    ],
  ),
};

Future<void> _mount(
  WidgetTester tester,
  _Scene scene, {
  required bool light,
  required GlobalKey boundary,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final gen = FakeGenUi();
  final card = _card(scene);
  final view = AgentCardView(
    controller: gen,
    parse: GenUiParsed(card),
    agentLabel: 'Claude Code',
    inList: scene == _Scene.listChoice,
  );
  final Widget app;
  if (scene == _Scene.listChoice) {
    final host = FakeChatsHost(
      FakeChatFeedSource(
        items: [
          chat(
            'a',
            'Choose a database',
            at: DateTime(2026, 10, 7, 11, 40),
            status: ChatStatus.needsYou,
            agentId: 'claude',
            agentLabel: 'Claude Code',
            preview: 'Two fit this project.',
          ),
        ],
      ),
    )..requestCards['a'] = view;
    app = chatsApp(host, const ChatsHomeScreen(), light: light);
  } else {
    app = MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: captureTheme(light: light),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Builder(
        builder: (context) => Material(
          color: Theme.of(context).scaffoldBackgroundColor,
          child: ListView(
            children: [
              SizedBox(height: KitTokens.of(context).space6),
              view,
            ],
          ),
        ),
      ),
    );
  }
  await tester.pumpWidget(RepaintBoundary(key: boundary, child: app));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final scene in _Scene.values) {
      testWidgets('${scene.name} · $mode', (tester) async {
        final boundary = GlobalKey();
        debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
        await _mount(tester, scene, light: light, boundary: boundary);
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('${scene.name}_$mode.png'),
        );
        await tester.pumpWidget(const SizedBox.shrink());
        debugDefaultTargetPlatformOverride = null;
      });
    }
  }
}
