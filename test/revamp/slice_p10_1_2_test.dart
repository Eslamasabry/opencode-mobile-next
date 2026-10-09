// slice-P10.1-2: one command sheet for every backend (P10.1) and the
// conversation menu as "Go to" / "Do" (P10.2). What the person sees:
// - the chat's title menu has two headed kinds, and Fork always lands in
//   the copy (menu, "/fork");
// - the command sheet lists commands only, the server's own first in plain
//   words, and the Library's Commands tab is the same sheet;
// - on a server whose agent does not share its commands the sheet names
//   what is missing, and a typed "/compact" is never sent as a message;
// - "!command" in the composer runs in the conversation's shell.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/codex/gateway.dart'
    show codexServerCapabilities;
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/command_sheet.dart';
import 'package:opencode_mobile/ui/widgets/session_menu.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'chat_3_support.dart';

class _Api extends Chat3Api {
  _Api({this.caps = ServerCapabilities.allV1, List<Session>? sessionList})
    : sessionList = sessionList ?? const [];

  final ServerCapabilities caps;
  final List<Session> sessionList;
  final shells = <String>[];
  final prompts = <String>[];
  final slashes = <String>[];

  @override
  ServerCapabilities get capabilities => caps;

  @override
  Future<List<Session>> sessions() async => sessionList;

  @override
  Future<void> shell(
    String sessionID, {
    required String command,
    required String agent,
    ModelRef? model,
    String? variant,
  }) async {
    shells.add(command);
  }

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    slashes.add('$command $args'.trim());
  }

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
    prompts.add(text);
  }
}

class _Repository implements ProductRepository {
  _Repository({this.commands = const []});

  final List<CommandInfo> commands;
  final forks = <String>[];

  @override
  Future<List<CommandInfo>> listCommands() async => commands;

  @override
  Future<String> forkSession(String id, {String? messageID}) async {
    forks.add(id);
    return 'fork-${forks.length}';
  }

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    WorkspaceProject(
      id: 'p1',
      name: 'p1',
      directory: '/tmp/p1',
      worktrees: const [],
      updatedAt: 1,
    ),
  ];

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(
  _Api api, {
  _Repository? repository,
  String backend = 'openCode',
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Laptop',
        'baseUrl': 'http://localhost',
        'username': '',
        'backend': backend,
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final conn = ConnectionController(store)
    ..api = api
    ..repository = repository ?? _Repository()
    ..status = StreamStatus.connected;
  addTearDown(conn.dispose);
  return conn;
}

final _prompt = chat3Prompt('user-1', 'Fix the checkout');

Future<void> _openMenu(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('session-actions-button')));
  await tester.pumpAndSettle();
}

Future<void> _openSheet(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-commands')));
  await tester.pumpAndSettle();
}

Future<void> _send(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
  await tester.tap(find.byKey(const Key('chat-send-button')));
  await tester.pumpAndSettle();
}

double _top(WidgetTester tester, Finder finder) => tester.getTopLeft(finder).dy;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(chat3MockSecureStorage);

  group('P10.2 conversation menu', () {
    test('Go to and Do, from the server capabilities', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      final items = sessionMenuItems(
        l10n,
        SessionMenuOffer.of(
          ServerCapabilities.allV1,
          shared: false,
          savedServer: true,
          hasPrompt: false,
        ),
        onSelected: (_) {},
      );
      final keys = [for (final item in items) (item.key! as ValueKey).value];
      expect(keys, [
        'session-menu-changes',
        'session-menu-timeline',
        'session-menu-find',
        'session-menu-subagents',
        'session-menu-details',
        'session-menu-share',
        'session-menu-compact',
        'session-menu-fork',
        'session-menu-rename',
        'session-menu-continue-computer',
        'session-menu-continue-phone',
        'session-menu-archive',
        'session-menu-delete',
      ]);
      expect(items.take(5).map((item) => item.group).toSet(), {
        const KitMenuGroup('Go to'),
      });
      expect(
        items
            .skip(5)
            .where((item) => !item.destructive)
            .map((i) => i.group)
            .toSet(),
        {const KitMenuGroup('Do')},
      );
      // Before the first prompt, Compact and Fork wait with the reason.
      final fork = items.firstWhere(
        (item) => item.key == const ValueKey('session-menu-fork'),
      );
      expect(fork.enabled, isFalse);
      expect(fork.disabledReason, 'Available after the first prompt');

      // A server without the flags offers what it can, nothing dead.
      final codex = sessionMenuItems(
        l10n,
        SessionMenuOffer.of(
          codexServerCapabilities,
          shared: false,
          savedServer: true,
        ),
        onSelected: (_) {},
      );
      expect(
        [for (final item in codex) (item.key! as ValueKey).value],
        [
          'session-menu-timeline',
          'session-menu-find',
          'session-menu-details',
          'session-menu-rename',
          'session-menu-continue-phone',
        ],
      );
    });

    testWidgets('the title menu shows Go to above Do', (tester) async {
      final api = _Api()..transcript = [_prompt];
      final conn = await _controller(api);
      await pumpChat3(tester, conn);

      await _openMenu(tester);
      final goTo = find.byKey(const ValueKey('kit-menu-heading-Go to'));
      final act = find.byKey(const ValueKey('kit-menu-heading-Do'));
      expect(goTo, findsOneWidget);
      expect(act, findsOneWidget);
      final changes = find.byKey(const ValueKey('session-menu-changes'));
      final share = find.byKey(const ValueKey('session-menu-share'));
      expect(_top(tester, goTo), lessThan(_top(tester, changes)));
      expect(_top(tester, changes), lessThan(_top(tester, act)));
      expect(_top(tester, act), lessThan(_top(tester, share)));
      // The old display and utility rows live elsewhere now.
      expect(find.text('Display and context'), findsNothing);
      expect(find.text('Retry last prompt'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('fork lands in the copy, from the menu and from /fork', (
      tester,
    ) async {
      final api = _Api()..transcript = [_prompt];
      final repository = _Repository();
      final conn = await _controller(api, repository: repository);
      await pumpChat3(tester, conn);

      await _openMenu(tester);
      await tester.tap(find.byKey(const ValueKey('session-menu-fork')));
      await tester.pumpAndSettle();
      expect(repository.forks, ['session-1']);
      expect(
        tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
        'fork-1',
      );
      // No second stop in a timeline picker.
      expect(find.text('Fork from a prompt'), findsNothing);

      // "/fork" in the copy lands the same way: in the next copy.
      await _send(tester, '/fork');
      expect(repository.forks, ['session-1', 'fork-1']);
      expect(
        tester.widget<ChatScreen>(find.byType(ChatScreen)).sessionID,
        'fork-2',
      );
    });
  });

  group('P10.1 command sheet', () {
    const commands = [
      CommandInfo(
        name: 'review',
        description: 'Review the changes on this branch',
        agent: 'plan',
        subtask: false,
      ),
      CommandInfo(name: 'init', subtask: false),
    ];

    testWidgets('lists commands only, the server first in plain words', (
      tester,
    ) async {
      final api = _Api()..transcript = [_prompt];
      final conn = await _controller(
        api,
        repository: _Repository(commands: commands),
      );
      await pumpChat3(tester, conn);
      await _openSheet(tester);

      expect(find.byType(CommandSheet), findsOneWidget);
      expect(find.text('Commands from Laptop'), findsOneWidget);
      final review = find.byKey(const Key('command-server-review'));
      expect(review, findsOneWidget);
      expect(
        find.descendant(
          of: review,
          matching: find.text('Review the changes on this branch'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: review,
          matching: find.textContaining('/review', findRichText: true),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: review,
          matching: find.textContaining('Runs with plan', findRichText: true),
        ),
        findsOneWidget,
      );
      // The conversation menu's acts are not repeated here.
      for (final slash in [
        'fork',
        'compact',
        'rename',
        'timeline',
        'share',
        'diff',
        'context',
      ]) {
        expect(find.byKey(Key('command-mobile-$slash')), findsNothing);
      }
      // What left the old menu is a command now.
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        'retry',
      );
      await tester.pump();
      expect(find.byKey(const Key('command-mobile-retry')), findsOneWidget);

      // Picking a server command readies it in this conversation.
      await tester.enterText(
        find.byKey(const Key('command-launcher-search')),
        'review',
      );
      await tester.pump();
      await tester.tap(find.byKey(const Key('command-server-review')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pumpAndSettle();
      expect(api.slashes, ['review']);
      expect(api.prompts, isEmpty);
    });

    testWidgets('the Library Commands tab is the same sheet', (tester) async {
      final api = _Api();
      final conn = await _controller(
        api,
        repository: _Repository(commands: commands),
      );
      await tester.pumpWidget(
        ProviderScope(
          overrides: [connProvider.overrideWithValue(conn)],
          child: MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: CapabilitiesScreen(
              controller: conn,
              initialSection: ToolsSection.commands,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(CommandSheet), findsOneWidget);
      expect(find.byKey(const Key('command-server-review')), findsOneWidget);
      expect(find.text('Commands from Laptop'), findsOneWidget);
    });

    testWidgets(
      'an agent without commands is named and /compact is never sent',
      (tester) async {
        final api = _Api(caps: codexServerCapabilities)..transcript = [_prompt];
        final conn = await _controller(api, backend: 'codex');
        await pumpChat3(tester, conn);
        await _openSheet(tester);

        final notice = find.byKey(
          const Key('command-launcher-agent-commands-unavailable'),
        );
        expect(notice, findsOneWidget);
        expect(
          find.descendant(
            of: notice,
            matching: find.text('Codex commands unavailable'),
          ),
          findsOneWidget,
        );
        expect(
          find.descendant(
            of: notice,
            matching: find.textContaining('run ! shell commands'),
          ),
          findsOneWidget,
        );
        expect(find.byKey(const Key('command-server-review')), findsNothing);
        await tester.tap(find.byTooltip('Close'));
        await tester.pumpAndSettle();

        await _send(tester, '/compact');
        expect(api.prompts, isEmpty);
        expect(api.slashes, isEmpty);
        expect(
          find.textContaining("/compact wasn't sent: Codex doesn't share"),
          findsOneWidget,
        );

        await _send(tester, '!ls');
        expect(api.shells, isEmpty);
        expect(api.prompts, isEmpty);
        expect(
          find.textContaining("!ls wasn't sent: shell commands can't run"),
          findsOneWidget,
        );
      },
    );

    testWidgets('!command runs in the conversation shell', (tester) async {
      final api = _Api()..transcript = [_prompt];
      final conn = await _controller(api);
      await pumpChat3(tester, conn);

      await _send(tester, '!ls -la');
      expect(api.shells, ['ls -la']);
      expect(api.prompts, isEmpty);
      expect(
        tester
            .widget<EditableText>(
              find.descendant(
                of: find.byKey(const Key('chat-composer-field')),
                matching: find.byType(EditableText),
              ),
            )
            .controller
            .text,
        isEmpty,
      );
    });
  });
}
