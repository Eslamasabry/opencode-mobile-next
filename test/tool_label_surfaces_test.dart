// Guard: a raw tool id never reaches a person. Every surface that names a
// tool (the transcript's step rows and running line, the timeline sheet and
// its search, Copy transcript, permission titles, the live notification and
// a team agent's last step) words it through one label function
// (`toolLabel`, lib/ui/widgets/tool_card_contract.dart, over
// lib/domain/tool_label.dart). Conversation list previews carry text parts
// only (session tail cache), so no tool id can reach them.
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart'
    show AgentStep, AgentStepKind;
import 'package:opencode_mobile/domain/server_gateway.dart'
    show PromptDelivery, StreamStatus;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/permission_presentation.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart'
    show agentStepWords;
import 'package:opencode_mobile/ui/widgets/tool_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Ids agents really send that the app must never show as they are.
const _rawIds = [
  'oc-ui_show',
  'mcp__oc-ui__show',
  'task_notification',
  'render_mermaid_diagram',
];

ToolState _state(String id, {String status = 'completed'}) =>
    ToolState.fromJson({
      'status': status,
      'title': id,
      'input': {'id': 'plan', 'title': 'Choose a fix', 'status': 'completed'},
      if (status == 'completed') 'output': 'ok',
    }, toolName: id);

Part _tool(String messageID, String id, {String status = 'completed'}) => Part(
  id: '$messageID-$id',
  messageID: messageID,
  callID: '$messageID-$id',
  type: 'tool',
  toolName: id,
  toolState: _state(id, status: status),
);

/// No visible text, plain or rich, holds any raw id.
void _expectNoRawId() {
  for (final id in _rawIds) {
    expect(
      find.textContaining(id, findRichText: true),
      findsNothing,
      reason: '"$id" is on screen',
    );
  }
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      List.of(messagesResult);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {}
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  @override
  Future<void> refreshCatalog() async {}
}

List<MessageWithParts> _transcript() => [
  MessageWithParts(
    info: MessageInfo(
      id: 'u1',
      sessionID: 'session-1',
      role: 'user',
      time: MsgTime(created: 1, completed: 2),
    ),
    parts: [Part(id: 'u1-text', messageID: 'u1', type: 'text', text: 'Fix it')],
  ),
  // A step that only called tools: its timeline row is named by them.
  MessageWithParts(
    info: MessageInfo(
      id: 'a1',
      sessionID: 'session-1',
      role: 'assistant',
      time: MsgTime(created: 10, completed: 11),
    ),
    parts: [for (final id in _rawIds) _tool('a1', id)],
  ),
];

Future<void> _pumpChat(WidgetTester tester) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: prefs))
    ..api = (_Api()..messagesResult = _transcript())
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        home: ChatScreen(
          sessionID: 'session-1',
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final AppLocalizations en = AppLocalizationsEn();

  group('transcript', () {
    for (final id in _rawIds) {
      testWidgets('a step row words "$id"', (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: ToolCard(toolName: id, state: _state(id)),
            ),
          ),
        );
        _expectNoRawId();
      });

      test('the running work line words "$id"', () {
        final line = runningToolTicker(
          id,
          _state(id, status: 'running'),
          l10n: en,
        );
        expect(line, isNot(contains(id)));
      });
    }

    testWidgets('the conversation itself shows no raw id', (tester) async {
      await _pumpChat(tester);
      // Open the work line so every step row is drawn.
      final header = find.byKey(const Key('work-group-header'));
      if (header.evaluate().isNotEmpty) {
        await tester.tap(header.first);
        await tester.pumpAndSettle();
      }
      _expectNoRawId();
    });
  });

  group('timeline sheet', () {
    testWidgets('rows and search hits word the tools', (tester) async {
      await _pumpChat(tester);
      await tester.tap(find.byTooltip('Conversation menu'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const ValueKey('session-menu-timeline')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('session-menu-timeline')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('timeline-row-a1')), findsOneWidget);
      _expectNoRawId();
      expect(
        find.textContaining('Show card', findRichText: true),
        findsWidgets,
      );

      // A search that lands in a tool's data shows the words around it.
      await tester.enterText(
        find.byKey(const Key('timeline-search')),
        'Choose a fix',
      );
      await tester.pump();
      expect(find.byKey(const Key('timeline-row-a1')), findsOneWidget);
      _expectNoRawId();
    });
  });

  group('Copy transcript', () {
    for (final id in _rawIds) {
      test('a "$id" step is headed in words', () {
        expect(transcriptToolHeading(_tool('a1', id), en), isNot(contains(id)));
      });
    }
  });

  group('team agent', () {
    for (final id in _rawIds) {
      test('a last step with "$id" is in words', () {
        final step = AgentStep(
          tool: id,
          command: '{}',
          output: '',
          kind: AgentStepKind.other,
        );
        expect(agentStepWords(en, step), isNot(contains(id)));
      });
    }
  });

  group('permission requests', () {
    for (final id in _rawIds) {
      test('a request for "$id" is titled in words', () {
        expect(permissionRequestTitle(id, l10n: en), isNot(contains(id)));
      });
    }
  });

  group('live notification', () {
    for (final id in _rawIds) {
      test('the running sentence for "$id" is in words', () {
        expect(ConnectionController.toolSentence(id), isNot(contains(id)));
      });
    }
  });
}
