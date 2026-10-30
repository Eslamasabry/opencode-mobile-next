import 'support/complete_message_history.dart';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/composer_tools.dart';

class _CodexApi extends FakeServerGateway with CompleteMessageHistory {
  _CodexApi() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> history = const [];
  final prompts = <({String text, PromptDelivery? delivery})>[];
  bool acceptPrompts = false;

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
    if (!acceptPrompts) {
      throw StateError('Codex prompt should be kept local while offline');
    }
    prompts.add((text: text, delivery: delivery));
  }
}

Future<ConnectionController> _controller(
  _CodexApi api, {
  bool connected = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = connected ? StreamStatus.connected : StreamStatus.disconnected;
  addTearDown(controller.dispose);
  return controller;
}

Future<ConnectionController> _pumpChat(
  WidgetTester tester,
  _CodexApi api, {
  String initialText = '',
  List<PromptAttachment> initialAttachments = const [],
  bool connected = false,
}) async {
  final controller = await _controller(api, connected: connected);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,

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
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Codex prompt tools explain unavailable attachment transports', (
    tester,
  ) async {
    await _pumpChat(tester, _CodexApi());

    expect(find.byTooltip('Attach and more'), findsOneWidget);

    await openComposerTray(tester);
    await tester.pumpAndSettle();

    final attach = tester.widget<KitRow>(
      find.byKey(const Key('composer-tool-attach')),
    );
    expect(attach.enabled, isFalse);
    expect(attach.onTap, isNull);
    expect(find.text('This server takes text only'), findsOneWidget);
    expect(find.byKey(const Key('composer-tool-gallery')), findsNothing);
    expect(find.byKey(const Key('composer-tool-camera')), findsNothing);
    await tester.tap(find.byKey(const Key('composer-tools-advanced')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('composer-tool-web-sources')), findsNothing);
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
        'This server supports text only. Remove attachments before sending.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('a Codex message sent while it works adds to the turn: one '
      'send, no queue wording', (tester) async {
    final api = _CodexApi()..acceptPrompts = true;
    final controller = await _pumpChat(tester, api, connected: true);
    controller.busySessions.add('thread-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'also check the tests',
    );
    await tester.pump();

    expect(find.byTooltip('Add to this turn'), findsOneWidget);
    expect(find.text('Adds to this reply'), findsOneWidget);
    expect(find.byTooltip('Send after this reply'), findsNothing);
    expect(find.text('Sends after this reply'), findsNothing);
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);

    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(api.prompts.single.text, 'also check the tests');
    expect(api.prompts.single.delivery, PromptDelivery.steer);
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

    final field = tester.widget<TextFormField>(
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

    // Go to / Do (slice-P10.2) lists only what Codex can do.
    for (final missing in [
      'changes',
      'subagents',
      'share',
      'compact',
      'fork',
      // No `opencode --session` CLI, so no resume command is offered.
      'continue-computer',
    ]) {
      expect(
        find.byKey(ValueKey('session-menu-$missing')),
        findsNothing,
        reason: missing,
      );
    }
    for (final kept in ['find', 'details', 'rename']) {
      expect(
        find.byKey(ValueKey('session-menu-$kept')),
        findsOneWidget,
        reason: kept,
      );
    }
  });

  testWidgets('Codex command launcher hides unsupported server catalogs', (
    tester,
  ) async {
    await _pumpChat(tester, _CodexApi());
    await openComposerTools(tester);
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

  testWidgets('the command launcher also searches the rest of the app', (
    tester,
  ) async {
    await _pumpChat(tester, _CodexApi());
    await openComposerTools(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('composer-tool-commands')));
    await tester.pumpAndSettle();
    final search = find.byKey(const Key('command-launcher-search'));

    // Nothing extra until the person types.
    expect(
      find.byKey(
        const ValueKey('command-launcher-result-settings-category-appearance'),
      ),
      findsNothing,
    );
    // The same gates as the Settings search: Codex has no MCP screen and no
    // terminal, so neither is offered from inside a conversation either.
    for (final query in ['mcp', 'terminal', 'skills']) {
      await tester.enterText(search, query);
      await tester.pump();
      expect(
        find.byKey(const ValueKey('command-launcher-result-settings-mcp')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('command-launcher-result-project-terminal')),
        findsNothing,
      );
      expect(
        find.byKey(
          const ValueKey('command-launcher-result-inside-capabilities-skills'),
        ),
        findsNothing,
      );
    }

    await tester.enterText(search, 'theme');
    await tester.pump();
    final result = find.byKey(
      const ValueKey('command-launcher-result-inside-appearance-theme'),
    );
    expect(result, findsOneWidget);
    await tester.ensureVisible(result);
    await tester.tap(result);
    await tester.pumpAndSettle();
    // The sheet is gone and the setting is open over the conversation.
    expect(search, findsNothing);
    expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
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
    await tester.tap(find.byTooltip('Conversation menu'));
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
    await tester.tap(find.bySemanticsLabel('Remove draft.txt'));
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
          .widget<TextFormField>(find.byKey(const Key('chat-composer-field')))
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
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,

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

    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
    expect(find.byKey(const Key('permission-card-reject')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-always')), findsNothing);

    // The details sheet offers no "Always allow" switch either.
    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    final sheet = find.byKey(const Key('permission-sheet'));
    expect(sheet, findsOneWidget);
    expect(find.byKey(const Key('permission-allow-always')), findsNothing);
    expect(find.text('Always allow would also cover'), findsNothing);

    final allowOnce = find.descendant(
      of: sheet,
      matching: find.text('Allow once'),
    );
    await tester.ensureVisible(allowOnce.last);
    await tester.pumpAndSettle();
    await tester.tap(allowOnce.last);
    await tester.pumpAndSettle();
    expect(replies, ['once']);
  });
}
