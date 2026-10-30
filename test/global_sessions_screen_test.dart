import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';

typedef _SessionQuery = ({
  String? search,
  bool includeArchived,
  String? cursor,
  int limit,
});

class _FinderRepository implements ProductRepository {
  _FinderRepository(this.handler) : pageHandler = null;
  _FinderRepository.pages(this.pageHandler) : handler = null;

  final Future<List<GlobalSessionResult>> Function(_SessionQuery query)?
  handler;
  final Future<ServerPage<GlobalSessionResult>> Function(_SessionQuery query)?
  pageHandler;
  final calls = <_SessionQuery>[];
  final details = <String, Session>{};
  final stealCalls = <String>[];
  Object? stealError;

  @override
  Future<String> stealSessionIntoWorkspace(String sessionID) async {
    stealCalls.add(sessionID);
    final error = stealError;
    if (error != null) throw error;
    return sessionID;
  }

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async {
    final query = (
      search: search,
      includeArchived: includeArchived,
      cursor: cursor,
      limit: limit,
    );
    calls.add(query);
    final page = pageHandler != null
        ? await pageHandler!(query)
        : ServerPage(items: await handler!(query));
    for (final result in page.items) {
      details[result.session.id] = result.session;
    }
    return page;
  }

  @override
  Future<Session> getSessionDetails(String id) async => details[id]!;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FinderController extends ConnectionController {
  _FinderController(super.store);

  // This generic repository implements moving conversations between folders.
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(sessionSteal: true);

  final locations = <({String? directory, String? workspace})>[];

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    locations.add((directory: directory, workspace: workspace));
    this.directory = directory;
    this.workspace = workspace;
  }

  @override
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => selectLocation(directory: directory, workspace: workspace);

  void signalRepository(ProductRepository value) {
    repository = value;
    dataRefreshRevision += 1;
    notifyListeners();
  }

  Future<void> signalProfile(String id, ProductRepository value) async {
    await store.upsert(
      ServerProfile(id: id, name: 'Profile $id', baseUrl: 'http://localhost'),
    );
    await store.setActiveId(id);
    signalRepository(value);
  }
}

GlobalSessionResult _result(
  int index, {
  int? updated,
  bool archived = false,
  String? directory,
  String? workspace,
  String? title,
}) => GlobalSessionResult(
  session: Session(
    id: 'ses_$index',
    title: title ?? 'Session $index',
    projectID: 'project_$index',
    workspaceID: workspace,
    directory: directory ?? '/work/project-$index',
    path: 'packages/app-$index',
    time: SessionTime(
      created: (updated ?? 2000000 - index) - 100,
      updated: updated ?? 2000000 - index,
      archived: archived ? 2000100 - index : null,
    ),
  ),
  projectName: 'Project $index',
  projectDirectory: directory ?? '/work/project-$index',
);

Future<_FinderController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.upsert(
    ServerProfile(
      id: 'server',
      name: 'Test server',
      baseUrl: 'http://localhost',
    ),
  );
  await store.setActiveId('server');
  return _FinderController(store)
    ..repository = repository
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  double textScale = 1,
  bool rtl = false,
  Map<String, WidgetBuilder> routes = const {},
  bool archived = false,
}) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  routes: routes,
  home: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: Directionality(
        textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
        child: GlobalSessionsScreen(controller: controller, archived: archived),
      ),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secureChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureChannel, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secureChannel, null);
  });

  testWidgets('All conversations leaves out the AI Team\'s own sessions', (
    tester,
  ) async {
    // The emulator's server on 2026-09-24, after the in-app team ran one
    // task in my-app: its agents' sessions came back with the person's.
    const refinery = '/root/aiteam/city/.gc/worktrees/my-app/refinery';
    const polecat =
        '/root/aiteam/city/.gc/worktrees/my-app/polecats/gastown.furiosa';
    final repository = _FinderRepository.pages(
      (query) async => switch (query.cursor) {
        null => ServerPage(
          items: [
            _result(
              1,
              updated: 90,
              directory: refinery,
              title:
                  "I'll run the startup sequence to check for existing work "
                  'and prime the merge queue.<tool_call><fu…',
            ),
            _result(
              2,
              updated: 80,
              directory: refinery,
              title: 'Refinery merge queue patrol',
            ),
            _result(
              3,
              updated: 20,
              directory: '/root/projects/my-app',
              title: 'Add a dark mode toggle',
            ),
          ],
          nextCursor: 'more',
        ),
        _ => ServerPage(
          items: [
            _result(
              4,
              updated: 70,
              directory: polecat,
              title: 'Polecat startup: claim work and execute',
            ),
            _result(
              5,
              updated: 10,
              directory: '/root/projects/my-app',
              title: 'Fix the login form',
            ),
          ],
        ),
      },
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pumpAndSettle();

    expect(find.text('Add a dark mode toggle'), findsOneWidget);
    expect(find.text('Fix the login form'), findsOneWidget);
    for (final id in [1, 2, 4]) {
      expect(find.byKey(ValueKey('global-session-ses_$id')), findsNothing);
    }
    expect(find.textContaining('merge queue'), findsNothing);
    expect(find.textContaining('Polecat'), findsNothing);
    expect(find.textContaining('.gc/worktrees'), findsNothing);
  });

  for (final rtl in [false, true]) {
    testWidgets(
      '320dp 2.5x ${rtl ? 'RTL' : 'LTR'} finder keeps filters and project paths readable',
      (tester) async {
        tester.view.physicalSize = const Size(320, 760);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final repository = _FinderRepository(
          (_) async => [_result(1, directory: '/work/checkout')],
        );
        final controller = await _controller(repository);
        addTearDown(controller.dispose);
        await tester.pumpWidget(_app(controller, textScale: 2.5, rtl: rtl));
        await tester.pumpAndSettle();
        final chip = find.byKey(const ValueKey('include-archived-sessions'));
        final text = find.descendant(of: chip, matching: find.text('Archived'));
        expect(tester.getRect(chip).contains(tester.getCenter(text)), isTrue);
        expect(tester.takeException(), isNull);
        await tester.tap(chip);
        await tester.pumpAndSettle();
        expect(repository.calls.last.includeArchived, isTrue);
      },
    );
  }

  testWidgets('results are grouped by working directory, newest first', (
    tester,
  ) async {
    // The server page arrives in arbitrary order. The list groups it by
    // working directory, orders groups by their newest session, and lists
    // each group newest first; a later page slots into the right group.
    final repository = _FinderRepository.pages(
      (query) async => switch (query.cursor) {
        null => ServerPage(
          items: [
            _result(1, updated: 100, directory: '/work/alpha'),
            _result(2, updated: 300, directory: '/work/beta'),
            _result(3, updated: 200, directory: '/work/alpha'),
          ],
          nextCursor: 'more',
        ),
        _ => ServerPage(
          items: [_result(4, updated: 400, directory: '/work/alpha/')],
        ),
      },
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    // Rows and project sections in reading order, top to bottom.
    List<String> visibleOrder() {
      final marks = <(double, String)>[
        for (final dir in ['/work/alpha', '/work/beta'])
          if (find
              .byKey(ValueKey('global-session-group-$dir'))
              .evaluate()
              .isNotEmpty)
            (
              tester
                  .getTopLeft(find.byKey(ValueKey('global-session-group-$dir')))
                  .dy,
              dir,
            ),
        for (final text in tester.widgetList<Text>(find.byType(Text)))
          if ((text.data ?? '').startsWith('Session '))
            (tester.getTopLeft(find.byWidget(text)).dy, text.data!),
      ]..sort((a, b) => a.$1.compareTo(b.$1));
      return [for (final mark in marks) mark.$2];
    }

    expect(visibleOrder(), [
      '/work/beta',
      'Session 2',
      '/work/alpha',
      'Session 3',
      'Session 1',
    ]);
    // The section names the project; rows no longer repeat it.
    expect(find.text('Project 2'), findsOneWidget);
    expect(find.textContaining('Project 2 ·'), findsNothing);
    // No counts (R13): the rows say how many there are.
    expect(find.byKey(const ValueKey('global-session-count')), findsNothing);

    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pumpAndSettle();

    expect(visibleOrder(), [
      '/work/alpha',
      'Session 4',
      'Session 3',
      'Session 1',
      '/work/beta',
      'Session 2',
    ]);
    expect(find.textContaining('conversations in'), findsNothing);
  });

  testWidgets('folders with the same name are told apart by their parent', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (_) async => [
        GlobalSessionResult(
          session: Session(
            id: 'ses_a',
            title: 'Main checkout',
            directory: '/home/dev/Code/TradeNet',
            time: SessionTime(created: 1, updated: 300),
          ),
          projectDirectory: '/home/dev/Code/TradeNet',
        ),
        GlobalSessionResult(
          session: Session(
            id: 'ses_b',
            title: 'Worktree',
            directory: '/home/dev/Worktrees/TradeNet',
            time: SessionTime(created: 1, updated: 200),
          ),
          projectDirectory: '/home/dev/Worktrees/TradeNet',
        ),
      ],
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    // Each section shows the disambiguated label.
    expect(find.text('Code/TradeNet'), findsOneWidget);
    expect(find.text('Worktrees/TradeNet'), findsOneWidget);
    expect(find.text('TradeNet'), findsNothing);

    // The project filter narrows the list to that folder only.
    Future<void> pick(String directory) async {
      await tester.tap(find.byKey(const ValueKey('global-session-filters')));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byKey(ValueKey('global-session-folder-$directory')).last,
      );
      await tester.pumpAndSettle();
    }

    await pick('/home/dev/Worktrees/TradeNet');
    expect(find.text('Worktree'), findsOneWidget);
    expect(find.text('Main checkout'), findsNothing);

    // Choosing it again shows every project.
    await pick('/home/dev/Worktrees/TradeNet');
    expect(find.text('Main checkout'), findsOneWidget);
  });

  testWidgets('empty and duplicate pages keep their continuation reachable', (
    tester,
  ) async {
    final repository = _FinderRepository.pages(
      (query) async => switch (query.cursor) {
        null => const ServerPage(items: [], nextCursor: 'z-token'),
        'z-token' => ServerPage(items: [_result(1)], nextCursor: 'a-token'),
        'a-token' => ServerPage(items: [_result(1)], nextCursor: 'next-token'),
        _ => ServerPage(items: [_result(2)]),
      },
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    final more = find.byKey(const ValueKey('global-sessions-load-more'));
    expect(find.text('No conversations yet'), findsNothing);
    for (var i = 0; i < 3; i++) {
      await tester.tap(more);
      await tester.pumpAndSettle();
    }
    expect(repository.calls.map((query) => query.cursor), [
      null,
      'z-token',
      'a-token',
      'next-token',
    ]);
    expect(find.text('Session 1'), findsOneWidget);
    expect(find.text('Session 2'), findsOneWidget);
    expect(more, findsNothing);
  });

  testWidgets('failed next page preserves rows and retries the same cursor', (
    tester,
  ) async {
    var fail = true;
    final repository = _FinderRepository.pages((query) async {
      if (query.cursor == null) {
        return ServerPage(items: [_result(1)], nextCursor: 'retry-token');
      }
      if (fail) throw const ProductException('Next page unavailable');
      return ServerPage(items: [_result(2)]);
    });
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pumpAndSettle();
    expect(find.text('Session 1'), findsOneWidget);
    expect(find.text('Next page unavailable'), findsOneWidget);
    fail = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(repository.calls.map((query) => query.cursor), [
      null,
      'retry-token',
      'retry-token',
    ]);
    expect(find.text('Session 2'), findsOneWidget);
  });

  testWidgets('a 502 on the next page is said in words; the raw text is '
      'only in the copied details', (tester) async {
    // Owner bug report 2026-09-27: the list read "ApiException: upstream
    // answered 502 while reading page 2" as its words.
    final repository = _FinderRepository.pages((query) async {
      if (query.cursor == null) {
        return ServerPage(items: [_result(1)], nextCursor: 'page-2');
      }
      throw ApiException(
        'List sessions failed (HTTP 502): upstream answered 502 while '
        'reading page 2',
        statusCode: 502,
      );
    });
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pumpAndSettle();

    expect(find.text('Session 1'), findsOneWidget);
    expect(find.text('Could not load more conversations'), findsOneWidget);
    expect(
      find.text('The server had a problem (error 502). Try again in a moment.'),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsOneWidget);
    expect(find.textContaining('ApiException'), findsNothing);
    expect(find.textContaining('upstream answered'), findsNothing);
    expect(find.textContaining('HTTP 502'), findsNothing);

    await tester.tap(
      find.byKey(const ValueKey('global-sessions-page-failed-copy')),
    );
    await tester.pump();
    expect(copied, contains('upstream answered 502 while reading page 2'));
    await tester.pumpAndSettle(const Duration(seconds: 3));
  });

  testWidgets('failed refresh preserves rows and offers a refresh retry', (
    tester,
  ) async {
    var calls = 0;
    final repository = _FinderRepository((_) async {
      calls += 1;
      if (calls == 1) return [_result(1)];
      if (calls == 2) {
        throw const ProductException('Temporary refresh failure');
      }
      return [_result(2)];
    });
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.drag(
      find.byKey(const PageStorageKey('global-sessions-list')),
      const Offset(0, 320),
    );
    await tester.pumpAndSettle();

    expect(find.text('Session 1'), findsOneWidget);
    expect(find.text('Temporary refresh failure'), findsOneWidget);
    expect(find.text('Could not refresh conversations.'), findsOneWidget);

    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('Session 2'), findsOneWidget);
    expect(find.text('Session 1'), findsNothing);
  });

  testWidgets('editing query invalidates a pending page before debounce', (
    tester,
  ) async {
    final pending = Completer<ServerPage<GlobalSessionResult>>();
    final repository = _FinderRepository.pages((query) async {
      if (query.search == 'new query') return ServerPage(items: [_result(99)]);
      if (query.cursor == null) {
        return ServerPage(items: [_result(1)], nextCursor: 'old-token');
      }
      return pending.future;
    });
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pump();
    await tester.enterText(
      find.byKey(const ValueKey('global-session-search')),
      'new query',
    );
    pending.complete(ServerPage(items: [_result(2)]));
    await tester.pump();
    expect(find.text('Session 2'), findsNothing);
    await tester.pump(const Duration(milliseconds: 301));
    await tester.pumpAndSettle();
    expect(find.text('Session 99'), findsOneWidget);
    expect(find.text('Session 1'), findsNothing);
  });

  testWidgets('profile switch retires a delayed page from the old scope', (
    tester,
  ) async {
    final pending = Completer<ServerPage<GlobalSessionResult>>();
    final oldRepository = _FinderRepository.pages((query) async {
      if (query.cursor == null) {
        return ServerPage(items: [_result(1)], nextCursor: 'old-token');
      }
      return pending.future;
    });
    final newRepository = _FinderRepository((_) async => [_result(99)]);
    final controller = await _controller(oldRepository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pump();

    await controller.signalProfile('other-server', newRepository);
    await tester.pumpAndSettle();
    pending.complete(ServerPage(items: [_result(2)]));
    await tester.pumpAndSettle();

    expect(find.text('Session 99'), findsOneWidget);
    expect(find.text('Session 1'), findsNothing);
    expect(find.text('Session 2'), findsNothing);
  });

  testWidgets('repeated server cursor offers a restart instead of looping', (
    tester,
  ) async {
    final repository = _FinderRepository.pages(
      (query) async =>
          ServerPage(items: [_result(1)], nextCursor: 'repeated-token'),
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pumpAndSettle();
    expect(find.textContaining('could not advance'), findsOneWidget);
    expect(repository.calls, hasLength(2));
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(repository.calls.last.cursor, isNull);
  });

  testWidgets('finder searches server titles and includes archived on demand', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (query) async => [
        _result(query.includeArchived ? 2 : 1, archived: query.includeArchived),
      ],
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(repository.calls.single.search, '');
    expect(repository.calls.single.limit, 50);
    expect(find.text('Session 1'), findsOneWidget);
    expect(find.byType(Card), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('global-session-search')),
      '  wake cycle  ',
    );
    await tester.pump(const Duration(milliseconds: 301));
    await tester.pumpAndSettle();
    expect(repository.calls.last.search, 'wake cycle');

    await tester.tap(find.byKey(const ValueKey('include-archived-sessions')));
    await tester.pumpAndSettle();

    expect(repository.calls.last.includeArchived, isTrue);
    expect(find.text('Session 2'), findsOneWidget);
    // The row leads with its state word (STATE-9).
    expect(find.textContaining('Archived ·'), findsOneWidget);
  });

  testWidgets(
    'accessible clear search resets the query and reloads all sessions',
    (tester) async {
      final repository = _FinderRepository(
        (query) async => [_result(query.search?.isEmpty == true ? 1 : 9)],
      );
      final controller = await _controller(repository);
      addTearDown(controller.dispose);

      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('global-session-search')),
        'wake cycle',
      );
      await tester.pump(const Duration(milliseconds: 301));
      await tester.pumpAndSettle();
      expect(repository.calls.last.search, 'wake cycle');
      expect(find.byTooltip('Clear search'), findsOneWidget);

      await tester.tap(find.byTooltip('Clear search'));
      await tester.pumpAndSettle();

      expect(repository.calls.last.search, '');
      expect(find.text('Session 1'), findsOneWidget);
      expect(find.text('Session 9'), findsNothing);
    },
  );

  testWidgets('finder paginates with the exact opaque server token', (
    tester,
  ) async {
    final repository = _FinderRepository.pages((query) async {
      if (query.cursor == null) {
        return ServerPage(
          items: List.generate(
            50,
            (index) => _result(index, updated: 5000 - index),
          ),
          nextCursor: 'opaque/next+token=',
        );
      }
      return ServerPage(items: [_result(80, updated: 4800)]);
    });
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(find.text('Session 0'), findsOneWidget);
    final list = find.byKey(
      const PageStorageKey<String>('global-sessions-list'),
    );
    await tester.drag(list, const Offset(0, -10000));
    await tester.pumpAndSettle();

    expect(repository.calls, hasLength(2));
    expect(repository.calls.last.cursor, 'opaque/next+token=');
  });

  testWidgets('global project rows keep a useful label and tap semantics', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    final repository = _FinderRepository(
      (_) async => [
        GlobalSessionResult(
          session: Session(
            id: 'ses_global',
            title: 'Global project session',
            projectID: 'global',
            directory: '/tmp/runtime-probe',
            time: SessionTime(created: 1000, updated: 2000),
          ),
          projectDirectory: '/',
        ),
      ],
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    final row = find.bySemanticsLabel(RegExp(r'Global project session'));
    expect(row, findsOneWidget);
    expect(
      tester
          .getSemantics(row)
          .getSemanticsData()
          .hasAction(ui.SemanticsAction.tap),
      isTrue,
    );
    semantics.dispose();
  });

  testWidgets('cross-project result switches location before opening chat', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (_) async => [
        _result(7, directory: '/srv/ledger-mobile', workspace: 'wrk_7'),
      ],
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        controller,
        routes: {
          '/chat/ses_7': (_) => const Scaffold(body: Text('Opened session')),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Session 7'));
    await tester.pumpAndSettle();

    expect(controller.locations, [
      (directory: '/srv/ledger-mobile', workspace: 'wrk_7'),
    ]);
    expect(find.text('Opened session'), findsOneWidget);
  });

  testWidgets('retained search refreshes through the replacement repository', (
    tester,
  ) async {
    final retained = _FinderRepository((_) async => [_result(1)]);
    final replacement = _FinderRepository((_) async => [_result(2)]);
    final controller = await _controller(retained);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(find.text('Session 1'), findsOneWidget);

    controller.signalRepository(replacement);
    await tester.pumpAndSettle();

    expect(retained.calls, hasLength(1));
    expect(replacement.calls, isEmpty);
    expect(find.text('Session 1'), findsOneWidget);
    // Two ListViews exist now (the folder chip strip and the results); pull
    // to refresh on the results list.
    await tester.drag(
      find.byKey(const PageStorageKey<String>('global-sessions-list')),
      const Offset(0, 400),
    );
    await tester.pumpAndSettle();
    expect(replacement.calls, hasLength(1));
    expect(find.text('Session 1'), findsNothing);
    expect(find.text('Session 2'), findsOneWidget);
  });

  testWidgets('retained search results stay openable after reconnecting', (
    tester,
  ) async {
    final result = _result(1);
    final retained = _FinderRepository((_) async => [result]);
    final replacement = _FinderRepository((_) async => [result])
      ..details[result.session.id] = result.session;
    final controller = await _controller(retained);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        controller,
        routes: {
          '/chat/ses_1': (_) => const Scaffold(body: Text('Opened session')),
        },
      ),
    );
    await tester.pumpAndSettle();

    controller.locationRevision++;
    controller.signalRepository(replacement);
    await tester.pumpAndSettle();
    expect(replacement.calls, isEmpty);
    await tester.tap(find.text('Session 1'));
    await tester.pumpAndSettle();
    expect(find.text('Opened session'), findsOneWidget);
  });

  testWidgets('repository replacement retires a pending page and keeps retry', (
    tester,
  ) async {
    final pending = Completer<ServerPage<GlobalSessionResult>>();
    final retained = _FinderRepository.pages((query) async {
      if (query.cursor == null) {
        return ServerPage(items: [_result(1)], nextCursor: 'retry-token');
      }
      return pending.future;
    });
    final replacement = _FinderRepository.pages(
      (query) async => ServerPage(items: [_result(2)]),
    );
    final controller = await _controller(retained);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pump();

    controller.signalRepository(replacement);
    await tester.pumpAndSettle();
    pending.complete(ServerPage(items: [_result(3)]));
    await tester.pumpAndSettle();

    expect(find.text('Session 1'), findsOneWidget);
    expect(find.text('Session 3'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('global-sessions-load-more')));
    await tester.pumpAndSettle();
    expect(replacement.calls.last.cursor, 'retry-token');
    expect(find.text('Session 2'), findsOneWidget);
  });

  testWidgets('unavailable finder stays scoped on a compact large-text phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _FinderRepository(
      (_) => Future.error(
        const ProductException(
          'All-project session search is unavailable on this server',
        ),
      ),
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller, textScale: 2));
    await tester.pumpAndSettle();

    expect(
      find.text('All-project session search is unavailable on this server'),
      findsOneWidget,
    );
    expect(find.byType(GlobalSessionsScreen), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('steal affordance appears only for sessions elsewhere', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (query) async => [
        _result(1, directory: '/work/active'),
        // Plain cross-directory rows never offer steal: live OpenCode
        // refuses /sync/steal outside the workspace sync system, and plain
        // directory transfer remains the /move workflow.
        _result(2, directory: '/work/other'),
        _result(3, directory: '/work/other', workspace: 'ws-remote'),
      ],
    );
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    // Continue here lives in each row's menu, never as a row icon.
    expect(find.byIcon(AppIconography.inbox), findsNothing);
    for (final (id, offered) in const [
      ('ses_1', false),
      ('ses_2', false),
      ('ses_3', true),
    ]) {
      await tester.longPress(find.byKey(ValueKey('global-session-$id')));
      await tester.pumpAndSettle();
      expect(find.text('Open'), findsOneWidget, reason: id);
      expect(
        find.byKey(ValueKey('steal-session-$id')),
        offered ? findsOneWidget : findsNothing,
        reason: id,
      );
      await tester.tapAt(Offset.zero);
      await tester.pumpAndSettle();
    }
  });

  testWidgets('stealing confirms, calls the repository, and opens the chat', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (query) async => [
        _result(2, directory: '/work/other', workspace: 'ws-remote'),
      ],
    );
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        controller,
        routes: {
          '/chat/ses_2': (_) =>
              const Scaffold(body: Text('stolen chat opened')),
        },
      ),
    );
    await tester.pumpAndSettle();

    await _continueHere(tester, 'ses_2');
    expect(find.text('Move to active?'), findsOneWidget);
    expect(
      find.textContaining('moves from Project 2 to active'),
      findsOneWidget,
    );
    expect(
      find.textContaining('To move it back, open Project 2'),
      findsOneWidget,
    );
    expect(repository.stealCalls, isEmpty);

    await tester.tap(
      find.byKey(const ValueKey('global-sessions-move-confirm')),
    );
    await tester.pumpAndSettle();

    expect(repository.stealCalls, ['ses_2']);
    expect(find.text('stolen chat opened'), findsOneWidget);
    // Opening did not re-route through the stale stored location.
    expect(controller.locations, isEmpty);
  });

  testWidgets('placeholder titles stay readable in the list and confirmation', (
    tester,
  ) async {
    const rawTitle = 'New session - 2026-09-09T10:24:36.000Z';
    final result = _result(
      2,
      directory: '/work/other',
      workspace: 'ws-remote',
      title: rawTitle,
    );
    final repository = _FinderRepository((query) async => [result]);
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(find.text('New conversation'), findsOneWidget);
    expect(find.textContaining('2026-09-09T10:24'), findsNothing);
    await _continueHere(tester, 'ses_2');
    expect(
      find.textContaining('“New conversation” moves from'),
      findsOneWidget,
    );
    expect(find.textContaining('2026-09-09T10:24'), findsNothing);
    expect(result.session.title, rawTitle);
    expect(repository.stealCalls, isEmpty);
  });

  testWidgets('a failed steal reports inline and keeps the list', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (query) async => [
        _result(2, directory: '/work/other', workspace: 'ws-remote'),
      ],
    )..stealError = const ProductException('Sync is unavailable');
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    await _continueHere(tester, 'ses_2');
    await tester.tap(
      find.byKey(const ValueKey('global-sessions-move-confirm')),
    );
    await tester.pumpAndSettle();

    // The question stays open with Try again; nothing claims it moved and
    // the list is kept (DATA-14).
    expect(repository.stealCalls, ['ses_2']);
    expect(find.text('Move to active?'), findsOneWidget);
    expect(find.text('stolen chat opened'), findsNothing);
    expect(find.byKey(const ValueKey('global-session-ses_2')), findsOneWidget);
  });

  testWidgets('steal flow fits a 320dp phone at 2x text', (tester) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final repository = _FinderRepository(
      (query) async => [
        _result(2, directory: '/work/other', workspace: 'ws-remote'),
      ],
    );
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, textScale: 2));
    await tester.pumpAndSettle();

    await _continueHere(tester, 'ses_2');
    expect(find.text('Move to active?'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Archived is a filter: only archived rows, paged until found', (
    tester,
  ) async {
    // The server has no archived-only query: the filter asks for archived
    // ones too and pages on by itself while none have arrived.
    final repository = _FinderRepository.pages(
      (query) async => switch ((query.includeArchived, query.cursor)) {
        (false, _) => ServerPage(items: [_result(1), _result(2)]),
        (true, null) => ServerPage(
          items: [_result(1), _result(2)],
          nextCursor: 'more',
        ),
        (true, _) => ServerPage(
          items: [_result(3, archived: true, title: 'Old spike')],
        ),
      },
    );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(find.text('Session 1'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('include-archived-sessions')));
    await tester.pumpAndSettle();

    expect(repository.calls.map((q) => (q.includeArchived, q.cursor)), [
      (false, null),
      (true, null),
      (true, 'more'),
    ]);
    expect(find.text('Old spike'), findsOneWidget);
    expect(find.text('Session 1'), findsNothing);
    expect(find.text('Session 2'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('global-sessions-active')));
    await tester.pumpAndSettle();
    expect(repository.calls.last.includeArchived, isFalse);
    expect(find.text('Session 1'), findsOneWidget);
  });

  testWidgets('an empty Archived filter says so and leads back to Active', (
    tester,
  ) async {
    final repository = _FinderRepository((query) async => [_result(1)]);
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    // Work's Archived row opens the page on this filter.
    await tester.pumpWidget(_app(controller, archived: true));
    await tester.pumpAndSettle();

    expect(repository.calls.single.includeArchived, isTrue);
    expect(find.text('No archived conversations'), findsOneWidget);
    expect(find.text('Session 1'), findsNothing);
    await tester.tap(find.text('Show active conversations'));
    await tester.pumpAndSettle();
    expect(repository.calls.last.includeArchived, isFalse);
    expect(find.text('Session 1'), findsOneWidget);
  });

  testWidgets('the project Work has open is named In use', (tester) async {
    final repository = _FinderRepository(
      (_) async => [
        _result(1, directory: '/work/active'),
        _result(2, directory: '/work/other'),
      ],
    );
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(find.text('Project 1 · In use'), findsOneWidget);
    expect(find.text('Project 2'), findsOneWidget);
  });

  group('slice-R13', () {
    double top(WidgetTester tester, String text) =>
        tester.getTopLeft(find.text(text)).dy;

    testWidgets('inside a project, needs-you rows lead, then working ones, '
        'then the rest newest first', (tester) async {
      final repository = _FinderRepository(
        (_) async => [
          _result(1, updated: 500, directory: '/work/app', title: 'Newest'),
          _result(2, updated: 400, directory: '/work/app', title: 'Working'),
          _result(3, updated: 300, directory: '/work/app', title: 'Older'),
          _result(4, updated: 200, directory: '/work/app', title: 'Asks'),
        ],
      );
      final controller = await _controller(repository);
      controller.busySessions = {'ses_2', 'ses_4'};
      controller.permissions = {
        'perm-1': PermissionRequest(
          id: 'perm-1',
          sessionID: 'ses_4',
          permission: 'edit',
        ),
      };
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      // The working mark animates, so the frames are pumped, not settled.
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }

      expect(top(tester, 'Asks'), lessThan(top(tester, 'Working')));
      expect(top(tester, 'Working'), lessThan(top(tester, 'Newest')));
      expect(top(tester, 'Newest'), lessThan(top(tester, 'Older')));
      // The first row says why it leads, in words as well as its mark.
      expect(
        find.byKey(const ValueKey('global-session-needs-you-ses_4')),
        findsOneWidget,
      );
      expect(find.textContaining('Needs you'), findsOneWidget);
    });

    testWidgets('a conversation that starts working moves up without a '
        'reload', (tester) async {
      final repository = _FinderRepository(
        (_) async => [
          _result(1, updated: 500, directory: '/work/app', title: 'Newest'),
          _result(2, updated: 400, directory: '/work/app', title: 'Later'),
        ],
      );
      final controller = await _controller(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(top(tester, 'Newest'), lessThan(top(tester, 'Later')));
      final calls = repository.calls.length;

      controller.busySessions = {'ses_2'};
      controller.notifyListeners();
      await tester.pump();

      expect(top(tester, 'Later'), lessThan(top(tester, 'Newest')));
      expect(repository.calls, hasLength(calls));
    });

    testWidgets('no counts: no summary line and no number beside a '
        'project', (tester) async {
      final repository = _FinderRepository(
        (_) async => [
          _result(1, directory: '/work/alpha'),
          _result(2, directory: '/work/alpha'),
          _result(3, directory: '/work/beta'),
        ],
      );
      final controller = await _controller(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('global-session-count')), findsNothing);
      expect(find.text('2'), findsNothing);
      expect(find.text('1'), findsNothing);
      expect(find.textContaining('conversations in'), findsNothing);
    });

    testWidgets('the search field and the Active/Archived choice share the '
        'gutter, and projects are one section gap apart', (tester) async {
      final repository = _FinderRepository(
        (_) async => [
          _result(1, updated: 500, directory: '/work/alpha'),
          _result(2, updated: 400, directory: '/work/beta'),
        ],
      );
      final controller = await _controller(repository);
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      final field = tester.getTopLeft(find.byType(KitSearchField));
      final segments = tester.getTopLeft(
        find.byKey(const ValueKey('global-sessions-active')),
      );
      final alpha = tester.getRect(
        find.byKey(const ValueKey('global-session-group-/work/alpha')),
      );
      final beta = tester.getRect(
        find.byKey(const ValueKey('global-session-group-/work/beta')),
      );
      // The field, the choice and the project panels share the 16 dp rails.
      expect(field.dx, 16);
      expect(tester.getTopLeft(find.byType(KitSegmented<bool>)).dx, 16);
      expect(alpha.left, 0);
      expect(segments.dx, greaterThanOrEqualTo(16));
      // The second project's label starts one section gap (24) under the
      // first project's panel, not two (the group's own gap only).
      expect(beta.top, alpha.bottom);
      expect(
        tester.getTopLeft(find.text('Project 2')).dy - beta.top,
        moreOrLessEquals(24, epsilon: 0.01),
      );
    });
  });

  testWidgets('moving a working conversation warns before it moves', (
    tester,
  ) async {
    final repository = _FinderRepository(
      (query) async => [
        _result(2, directory: '/work/other', workspace: 'ws-remote'),
      ],
    );
    final controller = await _controller(repository);
    controller.directory = '/work/active';
    controller.busySessions.add('ses_2');
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    await tester.longPress(find.byKey(const ValueKey('global-session-ses_2')));
    await tester.pump(const Duration(seconds: 1));
    await tester.tap(find.byKey(const ValueKey('steal-session-ses_2')));
    await tester.pump(const Duration(seconds: 1));

    expect(find.textContaining('It is working now'), findsOneWidget);
    expect(repository.stealCalls, isEmpty);
  });
}

/// Opens the row's menu (long-press, KIT-28) and picks Continue here.
Future<void> _continueHere(WidgetTester tester, String id) async {
  await tester.longPress(find.byKey(ValueKey('global-session-$id')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(ValueKey('steal-session-$id')));
  await tester.pumpAndSettle();
}
