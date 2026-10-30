import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show MethodChannel;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/git_link_repair.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProductException, WorkspaceProject;
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/paseo/transport.dart'
    show PaseoFailure, PaseoFailureKind;
import 'package:opencode_mobile/state/profiles.dart' show ProfileStore;
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'support/agents_fakes.dart';
import 'support/chats_fakes.dart';

FakeChatFeedSource _source({String? lastUsed}) => FakeChatFeedSource(
  projects: [
    project('alpha', kind: 'dart'),
    project('beta', git: false),
  ],
  lastUsed: lastUsed,
);

/// Hosts the start screen the way Home does: pushed over a first page.
Future<void> _open(
  WidgetTester tester,
  FakeChatsHost host, {
  String? directory,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    chatsApp(
      host,
      Builder(
        builder: (context) => TextButton(
          onPressed: () => showNewChat(context, directory: directory),
          child: const Text('go'),
        ),
      ),
    ),
  );
  await tester.tap(find.text('go'));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(loadCaptureFonts);

  testWidgets('shows the question and the last used project on its chip', (
    tester,
  ) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
    );
    expect(find.text('What would you like to build?'), findsOneWidget);
    // One agent here: no lonely card; the rows are on the screen, not
    // behind a sheet.
    expect(find.byType(KitAgentChoice), findsNothing);
    // The button names the agent; no quiet line repeats the Project row.
    expect(find.byType(KitAgentLine), findsNothing);
    expect(find.text('Start with OpenCode'), findsOneWidget);
    expect(find.text('Project'), findsOneWidget);
    expect(find.text(KitBidi.auto('alpha')), findsOneWidget);
    expect(find.text('Model'), findsOneWidget);
    expect(find.text('Server default'), findsOneWidget);
    expect(find.text('Git'), findsNothing);
  });

  // Audit P2-7 (2026-10-28): at 1280 the field and Start ran 1248 dp and
  // "Server default" sat 1100 dp from "Model".
  testWidgets('a wide window keeps the page at a readable width, centred', (
    tester,
  ) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
      size: const Size(1280, 800),
    );
    for (final key in [
      'chats-new-field',
      'chats-new-model',
      'chats-new-send',
    ]) {
      final box = tester.getRect(find.byKey(ValueKey(key)));
      expect(box.width, lessThanOrEqualTo(720), reason: key);
      expect(box.center.dx, closeTo(1280 / 2, 1), reason: key);
    }
  });

  // Audit P3-13 (2026-10-28): the field sat at 16, the rows' icons at 36
  // and the "Choose a project" line at 24. One gutter for all three.
  testWidgets('the field, the rows and the reason line share one edge', (
    tester,
  ) async {
    await _open(tester, FakeChatsHost(_source()));
    final edge = tester.getTopLeft(find.byType(KitStartBox)).dx;
    expect(edge, 16);
    for (final (key, icon) in [
      ('chats-new-model', AppIconography.model),
      ('chats-new-project', AppIconography.files),
    ]) {
      final glyph = find.descendant(
        of: find.byKey(ValueKey(key)),
        matching: find.byIcon(icon),
      );
      // The icon's tile (the glyph centred in it) starts on the edge.
      final tile = KitTokens.of(
        tester.element(find.byType(NewChatScreen)),
      ).iconTileSize;
      expect(
        tester.getCenter(glyph).dx - tile / 2,
        closeTo(edge, 0.5),
        reason: key,
      );
    }
    final reason = find.text('Choose a project to start a conversation.');
    expect(tester.getTopLeft(reason).dx, closeTo(edge, 0.5));
  });

  testWidgets('opens as a page that closes, its title centred', (tester) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
    );
    final title = find.text('New conversation');
    expect(title, findsOneWidget);
    expect(tester.getCenter(title).dx, closeTo(412 / 2, 1));
    expect(find.byTooltip('Back'), findsNothing);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.byType(NewChatScreen), findsNothing);
    expect(find.text('go'), findsOneWidget);
  });

  testWidgets('Start waits for words: off while the field is empty', (
    tester,
  ) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
    );
    KitButton start() =>
        tester.widget<KitButton>(find.byKey(const ValueKey('chats-new-send')));
    expect(start().onPressed, isNull);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix it',
    );
    await tester.pump();
    expect(start().onPressed, isNotNull);
  });

  testWidgets('a directory given wins over the last used one', (tester) async {
    await _open(
      tester,
      FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
      directory: '/root/projects/beta',
    );
    expect(find.text(KitBidi.auto('beta')), findsOneWidget);
    expect(find.text('Git'), findsNothing);
  });

  testWidgets('a temporary last project is not offered', (tester) async {
    await _open(tester, FakeChatsHost(_source(lastUsed: '/tmp/scratch')));
    expect(find.textContaining('Choose a project'), findsWidgets);
  });

  testWidgets('without a project sending is off and says why', (tester) async {
    final host = FakeChatsHost(_source());
    await _open(tester, host);
    expect(find.textContaining('Choose a project'), findsWidgets);
    expect(
      find.text('Choose a project to start a conversation.'),
      findsOneWidget,
    );
    expect(host.fake.started, isEmpty);
  });

  testWidgets('the chip opens Open a project and its choice updates it', (
    tester,
  ) async {
    final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
      ..openProjectResult = '/root/projects/beta';
    await _open(tester, host);
    await tester.tap(find.byKey(const ValueKey('chats-new-project')));
    await tester.pumpAndSettle();
    expect(host.projectOpens, 1);
    expect(find.textContaining(KitBidi.auto('beta')), findsWidgets);
    expect(find.textContaining(KitBidi.auto('alpha')), findsNothing);
  });

  testWidgets('choosing a project enables sending', (tester) async {
    final host = FakeChatsHost(_source())
      ..openProjectResult = '/root/projects/beta';
    await _open(tester, host);
    await tester.tap(find.byKey(const ValueKey('chats-new-project')));
    await tester.pumpAndSettle();
    expect(
      find.text('Choose a project to start a conversation.'),
      findsNothing,
    );
  });

  testWidgets('send starts the chat in the project and opens it', (
    tester,
  ) async {
    final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'));
    await _open(tester, host);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix the build',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('chats-new-send')));
    await tester.pumpAndSettle();
    expect(host.fake.started, [
      (directory: '/root/projects/alpha', prompt: 'Fix the build'),
    ]);
    expect(host.shown, ['ses_started']);
  });

  testWidgets('an error is said in plain words and keeps the draft', (
    tester,
  ) async {
    final source = _source(lastUsed: '/root/projects/alpha')
      ..startError = StateError('SocketException: connection refused');
    final host = FakeChatsHost(source);
    await _open(tester, host);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix the build',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('chats-new-send')));
    await tester.pumpAndSettle();
    expect(host.shown, isEmpty);
    expect(find.text('Fix the build'), findsOneWidget);
    expect(find.textContaining('SocketException'), findsNothing);
    expect(find.textContaining('StateError'), findsNothing);
  });

  testWidgets('a failed start offers its technical cause under Details', (
    tester,
  ) async {
    // Owner's phone, 2026-10-17: the banner said only "Could not start this
    // chat" and nothing anywhere said why.
    final source = _source(lastUsed: '/root/projects/alpha')
      ..startError = const ProductException(
        'Could not start this chat. Refresh before trying again.',
        cause: 'Working directory does not exist: /root/projects/alpha',
      );
    final host = FakeChatsHost(source);
    await _open(tester, host);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix the build',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('chats-new-send')));
    await tester.pumpAndSettle();
    // Plain words first; the cause is not copy.
    expect(
      find.text('Could not start this chat. Refresh before trying again.'),
      findsOneWidget,
    );
    expect(find.textContaining('Working directory'), findsNothing);
    await tester.tap(find.bySemanticsLabel('Details'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('chats-new-failure-details')),
      findsOneWidget,
    );
    expect(find.text("The conversation didn't start"), findsOneWidget);
    expect(
      find.textContaining('Report a problem includes these details.'),
      findsOneWidget,
    );
    // The technical text is folded inside the alert until asked for.
    await tester.tap(
      find.descendant(
        of: find.byKey(const ValueKey('chats-new-failure-details')),
        matching: find.text('Details'),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Working directory does not exist'),
      findsOneWidget,
    );
  });

  testWidgets('a failure with no technical cause offers no Details', (
    tester,
  ) async {
    final source = _source(lastUsed: '/root/projects/alpha')
      ..startError = const ProductException('This agent is not ready yet.');
    final host = FakeChatsHost(source);
    await _open(tester, host);
    await tester.enterText(
      find.byKey(const ValueKey('chats-new-field')),
      'Fix the build',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('chats-new-send')));
    await tester.pumpAndSettle();
    expect(find.text('This agent is not ready yet.'), findsOneWidget);
    expect(find.bySemanticsLabel('Details'), findsNothing);
  });

  testWidgets('large text does not overflow', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      chatsApp(
        FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
        const NewChatScreen(),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  for (final (label, locale) in [
    ('English', const Locale('en')),
    ('Arabic, right to left', const Locale('ar')),
  ]) {
    testWidgets('320 wide at 2x text with cards: scrolls, Start stays pinned '
        '($label)', (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
        ..phoneAgents = FakePhoneAgentsSource(
          rows: [
            agentRowFor('claude', FakeAgentStage.ready),
            agentRowFor('codex', FakeAgentStage.notInstalled),
          ],
          selected: 'claude',
        );
      await tester.pumpWidget(
        chatsApp(host, const NewChatScreen(), textScale: 2, locale: locale),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final start = find.byKey(const ValueKey('chats-new-send'));
      expect(tester.getBottomLeft(start).dy, lessThanOrEqualTo(640));
      // The last row is reachable by scrolling; Start does not move.
      final before = tester.getTopLeft(start);
      await tester.drag(find.byType(ListView).first, const Offset(0, -600));
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(start), before);
      expect(tester.takeException(), isNull);
    });
  }

  // Issue #95: Codex, Kilo and Cline can now sign in on the phone, and
  // their sheet's "Use …" chooses them before they are certified for chat.
  // New conversation then said "Start with OpenCode" while it started the
  // chosen agent; the chosen agent's card shows, chosen, and Start names it.
  testWidgets('an agent chosen from its sheet shows chosen before it is '
      'certified', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
      ..phoneAgents = FakePhoneAgentsSource(
        rows: [
          agentRowFor('claude', FakeAgentStage.ready),
          agentRowFor('cline', FakeAgentStage.ready),
        ],
        selected: 'cline',
      );
    await tester.pumpWidget(chatsApp(host, const NewChatScreen()));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('chats-new-agent-cline')), findsOneWidget);
    expect(find.text('Start with Cline'), findsOneWidget);
    expect(find.text('Start with OpenCode'), findsNothing);
  });

  group('In a separate copy', () {
    const copy = WorkspaceProject(
      id: 'p-alpha',
      name: 'alpha',
      directory: '/root/projects/alpha',
      worktrees: [],
      updatedAt: 1,
    );

    testWidgets('is not offered where the server cannot make one', (
      tester,
    ) async {
      await _open(
        tester,
        FakeChatsHost(_source(lastUsed: '/root/projects/alpha')),
      );
      expect(
        find.byKey(const ValueKey('chats-new-separate-copy')),
        findsNothing,
      );
    });

    testWidgets('an agent on this phone outside /root/projects says why', (
      tester,
    ) async {
      final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
        ..phoneAgents = FakePhoneAgentsSource(
          rows: [agentRowFor('claude', FakeAgentStage.ready)],
          selected: 'claude',
        );
      await _open(tester, host, directory: '/storage/emulated/0/notes');
      expect(
        find.textContaining('works only in the projects on this phone'),
        findsWidgets,
      );
    });

    testWidgets('OpenCode works in any folder', (tester) async {
      final open = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'));
      await _open(tester, open, directory: '/storage/emulated/0/notes');
      expect(
        find.textContaining('works only in the projects on this phone'),
        findsNothing,
      );
    });

    testWidgets('is OpenCode\'s: not offered while another agent is chosen', (
      tester,
    ) async {
      final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
        ..copyProject = copy
        ..phoneAgents = FakePhoneAgentsSource(
          rows: [agentRowFor('claude', FakeAgentStage.ready)],
          selected: 'claude',
        );
      await _open(tester, host);
      expect(
        find.byKey(const ValueKey('chats-new-separate-copy')),
        findsNothing,
      );
    });

    testWidgets(
      'is an option in the choices sheet, under the project, where it can be',
      (tester) async {
        final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
          ..copyProject = copy;
        await _open(tester, host);
        final option = find.byKey(const ValueKey('chats-new-separate-copy'));
        expect(option, findsOneWidget);
        expect(find.text('In a separate copy'), findsOneWidget);
        expect(
          tester.getTopLeft(option).dy,
          greaterThan(
            tester
                    .getBottomLeft(
                      find.byKey(const ValueKey('chats-new-project')),
                    )
                    .dy -
                1,
          ),
        );
        expect(host.copies, isEmpty);
      },
    );

    testWidgets('choosing it starts the task in the copy and opens the '
        'conversation', (tester) async {
      final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
        ..copyProject = copy
        ..copyResult = 'ses_copy';
      await _open(tester, host);
      await tester.tap(find.byKey(const ValueKey('chats-new-separate-copy')));
      await tester.pumpAndSettle();
      // Switching it on starts nothing; Start does, with the words typed.
      expect(host.copies, isEmpty);
      await tester.enterText(
        find.byKey(const ValueKey('chats-new-field')),
        'Try a refactor',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await tester.pumpAndSettle();
      expect(host.copyTasks, ['Try a refactor']);
      expect(host.copies, ['/root/projects/alpha']);
      expect(host.shown, ['ses_copy']);
    });

    testWidgets('closing the step opens nothing', (tester) async {
      final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
        ..copyProject = copy;
      await _open(tester, host);
      await tester.tap(find.byKey(const ValueKey('chats-new-separate-copy')));
      await tester.pumpAndSettle();
      // Switching it on starts nothing; Start does, with the words typed.
      expect(host.copies, isEmpty);
      await tester.enterText(
        find.byKey(const ValueKey('chats-new-field')),
        'Try a refactor',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await tester.pumpAndSettle();
      expect(host.copyTasks, ['Try a refactor']);
      expect(host.copies, hasLength(1));
      expect(host.shown, isEmpty);
    });

    testWidgets('gone with the project: no folder, no option', (tester) async {
      final host = FakeChatsHost(_source(lastUsed: '/tmp/scratch'))
        ..copyProject = copy;
      await _open(tester, host);
      expect(
        find.byKey(const ValueKey('chats-new-separate-copy')),
        findsNothing,
      );
    });
  });

  testWidgets('the quiet line names the agent the server starts with', (
    tester,
  ) async {
    final host = FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
      ..startAgentName = 'Claude Code';
    await _open(tester, host);
    expect(find.textContaining('Claude Code'), findsWidgets);
    expect(find.textContaining('OpenCode'), findsNothing);
  });

  group('a project whose Git cannot be read', () {
    PaseoFailure gitFailure() => PaseoFailure(
      PaseoFailureKind.unavailable,
      reason:
          'Git command failed: git status --porcelain fatal: bad object HEAD',
    );

    Future<FakeChatsHost> failOnce(
      WidgetTester tester,
      GitRepairActions? repair,
    ) async {
      final source = _source(lastUsed: '/root/projects/alpha')
        ..startError = gitFailure();
      final host = FakeChatsHost(source)..gitRepair = repair;
      await _open(tester, host);
      await tester.enterText(
        find.byKey(const ValueKey('chats-new-field')),
        'Fix the build',
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await tester.pumpAndSettle();
      return host;
    }

    testWidgets('Repair takes Retry\'s place, repairs, then starts', (
      tester,
    ) async {
      final stages = <GitRepairStage>[];
      late FakeChatsHost host;
      final repair = GitRepairActions(
        run: (progress) async {
          progress(
            const GitRepairProgress(
              GitRepairStage.askingTermux,
              done: 3,
              total: 9,
            ),
          );
          stages.add(GitRepairStage.askingTermux);
          // The project can be read now: the next start goes through.
          host.fake.startError = null;
          return const GitRepairOutcome(GitRepairResult.repaired, restored: 9);
        },
        allowTermux: () async => true,
        openTermux: () async {},
      );
      host = await failOnce(tester, repair);

      expect(find.text("Git history can't be read."), findsOneWidget);
      expect(find.text('· Retry'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('chats-new-git-repair')));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('git-repair-sheet')), findsOneWidget);
      expect(find.text('alpha is repaired'), findsOneWidget);
      expect(
        find.text('9 Git files are back, and Git can read the project again.'),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('git-repair-start')));
      await tester.pumpAndSettle();

      expect(stages, [GitRepairStage.askingTermux]);
      expect(host.fake.started, hasLength(2));
      expect(host.fake.started.last.prompt, 'Fix the build');
      expect(host.shown, ['ses_started']);
    });

    testWidgets('files gone for good are said plainly, with the way on', (
      tester,
    ) async {
      final repair = GitRepairActions(
        run: (progress) async => const GitRepairOutcome(
          GitRepairResult.cannotRepair,
          restored: 2,
          missing: ['objects/ab/cdef', 'objects/12/3456', 'objects/9a/bc'],
          details: 'Missing (3)',
        ),
        allowTermux: () async => true,
        openTermux: () async {},
      );
      final host = await failOnce(tester, repair);
      await tester.tap(find.byKey(const ValueKey('chats-new-git-repair')));
      await tester.pumpAndSettle();

      expect(find.text("Can't repair alpha"), findsOneWidget);
      expect(
        find.text(
          "3 Git files aren't on this phone any more. Clone the project "
          "again from where it's hosted, or restore a copy.",
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Close'));
      await tester.pumpAndSettle();
      expect(host.fake.started, hasLength(1));
      expect(find.text('Fix the build'), findsOneWidget);
    });

    testWidgets('Termux not allowed yet: Allow and repair asks, then repairs', (
      tester,
    ) async {
      var runs = 0;
      var asked = 0;
      final repair = GitRepairActions(
        run: (progress) async => ++runs == 1
            ? const GitRepairOutcome(
                GitRepairResult.termux,
                termux: GitRepairTermux.notAllowed,
              )
            : const GitRepairOutcome(GitRepairResult.repaired, restored: 1),
        allowTermux: () async {
          asked++;
          return true;
        },
        openTermux: () async {},
      );
      await failOnce(tester, repair);
      await tester.tap(find.byKey(const ValueKey('chats-new-git-repair')));
      await tester.pumpAndSettle();
      expect(find.text('Files are in Termux'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('git-repair-allow')));
      await tester.pumpAndSettle();
      expect(asked, 1);
      expect(runs, 2);
      expect(find.text('alpha is repaired'), findsOneWidget);
    });

    testWidgets('where nothing can repair it, the words say what to do', (
      tester,
    ) async {
      await failOnce(tester, null);
      expect(
        find.text(
          "This project's Git history can't be read, so the conversation "
          "couldn't start. Restore the project from a copy, or clone it again.",
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('chats-new-git-repair')), findsNothing);
    });
  });

  // Audit P1-4 (2026-10-28): one Back press or an edge swipe threw away a
  // long prompt without a word. The words stay on the device, per project
  // and server profile, until they are sent.
  group('the unsent words', () {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    setUp(() {
      SharedPreferences.setMockInitialValues({});
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            secure,
            (call) async =>
                call.method == 'readAll' ? <String, String>{} : null,
          );
    });
    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(secure, null);
    });

    FakeChatsHost host() =>
        FakeChatsHost(_source(lastUsed: '/root/projects/alpha'))
          ..profileId = 'prof-a';

    String field(WidgetTester tester) => tester
        .widget<EditableText>(
          find.descendant(
            of: find.byKey(const ValueKey('chats-new-field')),
            matching: find.byType(EditableText),
          ),
        )
        .controller
        .text;

    Future<void> type(WidgetTester tester, String text) async {
      await tester.enterText(
        find.byKey(const ValueKey('chats-new-field')),
        text,
      );
      await tester.pumpAndSettle();
    }

    Future<void> back(WidgetTester tester) async {
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(NewChatScreen), findsNothing);
    }

    Future<void> reopen(WidgetTester tester) async {
      await tester.tap(find.text('go'));
      await tester.pumpAndSettle();
    }

    testWidgets('survive Back and come back on reopen', (tester) async {
      await _open(tester, host());
      await type(tester, 'Add a dark mode to the settings page');
      await back(tester);
      await reopen(tester);
      expect(field(tester), 'Add a dark mode to the settings page');
      // Close keeps them too.
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      await reopen(tester);
      expect(field(tester), 'Add a dark mode to the settings page');
    });

    testWidgets('belong to their project', (tester) async {
      final chats = host();
      await _open(tester, chats);
      await type(tester, 'Fix the alpha build');
      await back(tester);
      await _open(tester, chats, directory: '/root/projects/beta');
      expect(field(tester), isEmpty);
      await type(tester, 'Tidy beta');
      await back(tester);
      await _open(tester, chats, directory: '/root/projects/alpha');
      expect(field(tester), 'Fix the alpha build');
    });

    testWidgets('move with the project when it is changed', (tester) async {
      final chats = host()..openProjectResult = '/root/projects/beta';
      await _open(tester, chats);
      await type(tester, 'Start over in beta');
      await tester.tap(find.byKey(const ValueKey('chats-new-project')));
      await tester.pumpAndSettle();
      await back(tester);
      await _open(tester, chats, directory: '/root/projects/beta');
      expect(field(tester), 'Start over in beta');
      await back(tester);
      await _open(tester, chats, directory: '/root/projects/alpha');
      expect(field(tester), isEmpty);
    });

    testWidgets('are cleared once sent', (tester) async {
      final chats = host();
      await _open(tester, chats);
      await type(tester, 'Fix the build');
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await tester.pumpAndSettle();
      expect(chats.fake.started, hasLength(1));
      final prefs = await SharedPreferences.getInstance();
      expect(
        prefs.getKeys().where((key) => key.startsWith('oc.draft.new-chat')),
        isEmpty,
      );
      await reopen(tester);
      expect(field(tester), isEmpty);
    });

    testWidgets('stay when the start fails', (tester) async {
      final chats = host();
      chats.fake.startError = StateError('SocketException: refused');
      await _open(tester, chats);
      await type(tester, 'Fix the build');
      await tester.tap(find.byKey(const ValueKey('chats-new-send')));
      await tester.pumpAndSettle();
      await back(tester);
      await reopen(tester);
      expect(field(tester), 'Fix the build');
    });

    testWidgets('go with the server profile when it is deleted', (
      tester,
    ) async {
      await _open(tester, host());
      await type(tester, 'Half a thought');
      await back(tester);
      final prefs = await SharedPreferences.getInstance();
      final keys = prefs
          .getKeys()
          .where((key) => key.startsWith('oc.draft.new-chat'))
          .toList();
      expect(keys, hasLength(1));
      expect(keys.single, endsWith('.prof-a'));
      final store = ProfileStore(prefs: prefs);
      expect(
        store.profileScopedPreferenceKeys('prof-a'),
        contains(keys.single),
      );
      expect(await store.removeScopedPreferences('prof-a'), isEmpty);
      expect(prefs.getString(keys.single), isNull);
      await reopen(tester);
      expect(field(tester), isEmpty);
    });

    testWidgets('an offered text is not replaced by a saved draft', (
      tester,
    ) async {
      final chats = host();
      await _open(tester, chats);
      await type(tester, 'My own words');
      await back(tester);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      await tester.pumpWidget(
        chatsApp(
          chats,
          Builder(
            builder: (context) => TextButton(
              onPressed: () =>
                  showNewChat(context, initialText: 'An offer to read'),
              child: const Text('offer'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('offer'));
      await tester.pumpAndSettle();
      expect(field(tester), 'An offer to read');
      await back(tester);
      await _open(tester, chats);
      expect(field(tester), 'My own words');
    });
  });
}
