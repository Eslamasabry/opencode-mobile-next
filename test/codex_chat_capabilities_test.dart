import 'support/complete_message_history.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _CodexApi extends OpenCodeApi with CompleteMessageHistory {
  _CodexApi() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> history = const [];

  @override
  Future<List<MessageWithParts>> messages(String id) async => history;

  @override
  ServerCapabilities get capabilities => codexServerCapabilities;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Session> session(String id) async => Session(id: id);

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
  }) async {
    throw StateError('Codex prompt should be kept local while offline');
  }
}

Future<ConnectionController> _controller(_CodexApi api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.disconnected;
  addTearDown(controller.dispose);
  return controller;
}

Future<void> _pumpChat(
  WidgetTester tester,
  _CodexApi api, {
  String initialText = '',
  List<PromptAttachment> initialAttachments = const [],
}) async {
  final controller = await _controller(api);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        home: ChatScreen(
          sessionID: 'thread-1',
          initialText: initialText,
          initialAttachments: initialAttachments,
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Codex prompt tools hide attachment transports', (tester) async {
    await _pumpChat(tester, _CodexApi());

    final tooltip = tester.widget<Tooltip>(
      find.byKey(const Key('prompt-tools-tooltip')),
    );
    expect(tooltip.message, 'Prompt tools');

    await tester.tap(find.byKey(const Key('composer-tools-button')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('composer-tool-attach')), findsNothing);
    expect(find.byKey(const Key('composer-tool-gallery')), findsNothing);
    expect(find.byKey(const Key('composer-tool-camera')), findsNothing);
    expect(find.text('Add web source'), findsNothing);
  });

  testWidgets('restored attachments stay visible and explain the send guard', (
    tester,
  ) async {
    const attachment = PromptAttachment(
      mime: 'text/plain',
      filename: 'draft.txt',
      url: 'data:text/plain;base64,ZA==',
    );
    await _pumpChat(
      tester,
      _CodexApi(),
      initialText: 'keep this draft',
      initialAttachments: [attachment],
    );

    expect(find.text('draft.txt'), findsOneWidget);
    expect(
      find.text(
        'This connection supports text only. Remove attachments before sending.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('offline Codex send keeps draft and does not enqueue a turn', (
    tester,
  ) async {
    final api = _CodexApi();
    await _pumpChat(tester, api, initialText: 'keep this offline draft');
    final controller = ProviderScope.containerOf(
      tester.element(find.byType(ChatScreen)),
      listen: false,
    ).read(connProvider);

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pumpAndSettle();

    final field = tester.widget<TextField>(
      find.byKey(const Key('chat-composer-field')),
    );
    expect(field.controller!.text, 'keep this offline draft');
    expect(
      find.text('Reconnect before sending. Your draft is kept on this device.'),
      findsOneWidget,
    );
    expect(controller.queuedPromptsFor('thread-1'), isEmpty);
  });

  testWidgets('Codex session menu hides unsupported session actions', (
    tester,
  ) async {
    await _pumpChat(tester, _CodexApi());

    await tester.tap(find.byKey(const Key('session-actions-button')));
    await tester.pumpAndSettle();

    expect(find.text('Changes'), findsNothing);
    expect(find.text('Fork session'), findsNothing);
    expect(find.text('Revert last prompt'), findsNothing);
    expect(find.text('Compact context'), findsNothing);
    expect(find.text('Run shell command'), findsNothing);
    expect(find.text('Subagent sessions'), findsNothing);
    expect(find.text('Share session'), findsNothing);
    expect(find.text('Reload messages'), findsOneWidget);
  });

  testWidgets('Codex command launcher hides unsupported server catalogs', (
    tester,
  ) async {
    await _pumpChat(tester, _CodexApi());
    await tester.tap(find.byKey(const Key('composer-tools-button')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('composer-tool-commands')));
    await tester.pumpAndSettle();

    for (final query in ['mcp', 'connect', 'skills']) {
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        query,
      );
      await tester.pump();
      expect(find.byKey(const Key('command-mobile-mcps')), findsNothing);
      expect(find.byKey(const Key('command-mobile-connect')), findsNothing);
      expect(find.byKey(const Key('command-mobile-skills')), findsNothing);
    }
    await tester.enterText(
      find.byKey(const Key('command-launcher-search')),
      'editor',
    );
    await tester.pump();
    expect(find.byKey(const Key('command-mobile-editor')), findsOneWidget);
  });

  testWidgets('Codex timeline keeps message navigation without fork actions', (
    tester,
  ) async {
    final api = _CodexApi()
      ..history = [
        MessageWithParts(
          info: MessageInfo(
            id: 'saved-user',
            sessionID: 'thread-1',
            role: 'user',
            time: MsgTime(created: 1),
          ),
          parts: [Part(type: 'text', text: 'Saved prompt')],
        ),
      ];
    await _pumpChat(tester, api);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Session menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Timeline'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('timeline-row-saved-user')), findsOneWidget);
    expect(find.byTooltip('Fork from this prompt'), findsNothing);
    expect(find.textContaining('Fork restores'), findsNothing);
    await tester.tap(find.byKey(const Key('timeline-row-saved-user')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('timeline-search')), findsNothing);
    expect(find.text('Saved prompt'), findsOneWidget);
  });

  testWidgets('Codex prompt editor can edit text and remove restored files', (
    tester,
  ) async {
    await _pumpChat(
      tester,
      _CodexApi(),
      initialText: 'Original draft',
      initialAttachments: const [
        PromptAttachment(
          mime: 'text/plain',
          filename: 'draft.txt',
          url: 'data:text/plain;base64,ZA==',
        ),
      ],
    );
    await tester.tap(find.byKey(const Key('prompt-editor-button')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('prompt-editor-attach')), findsNothing);
    expect(find.text('draft.txt'), findsOneWidget);
    await tester.tap(find.byTooltip('Remove attachment draft.txt'));
    await tester.enterText(
      find.byKey(const Key('prompt-editor-field')),
      'Edited text-only draft',
    );
    await tester.tap(find.byKey(const Key('prompt-editor-done')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('prompt-editor-screen')), findsNothing);
    expect(find.text('draft.txt'), findsNothing);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-composer-field')))
          .controller!
          .text,
      'Edited text-only draft',
    );
  });

  testWidgets('Codex approval sheet has no persistent grant action', (
    tester,
  ) async {
    final replies = <String>[];
    final permission = PermissionRequest(
      id: 'approval-1',
      sessionID: 'thread-1',
      permission: 'shell',
      patterns: const ['git status'],
      always: const ['git *'],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: PermissionSheet(
            permission: permission,
            supportsRejectMessage: false,
            allowPersistentPermission:
                codexServerCapabilities.persistentPermissionGrants,
            onReply: (reply, {message}) async => replies.add(reply),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('permission-allow-once')), findsOneWidget);
    expect(find.byKey(const Key('permission-reject')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-always')), findsNothing);
    expect(find.text('Always allow would also cover'), findsNothing);

    await tester.tap(find.byKey(const Key('permission-allow-once')));
    await tester.pumpAndSettle();
    expect(replies, ['once']);
  });
}
