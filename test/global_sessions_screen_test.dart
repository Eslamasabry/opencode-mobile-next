import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/global_sessions_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
    if (pageHandler != null) return pageHandler!(query);
    return ServerPage(items: await handler!(query));
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FinderController extends ConnectionController {
  _FinderController(super.store);

  final locations = <({String? directory, String? workspace})>[];

  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    locations.add((directory: directory, workspace: workspace));
    this.directory = directory;
    this.workspace = workspace;
  }

  void signalRepository(ProductRepository value) {
    repository = value;
    dataRefreshRevision += 1;
    notifyListeners();
  }
}

GlobalSessionResult _result(
  int index, {
  int? updated,
  bool archived = false,
  String? directory,
  String? workspace,
}) => GlobalSessionResult(
  session: Session(
    id: 'ses_$index',
    title: 'Session $index',
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
  return _FinderController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  double textScale = 1,
  Map<String, WidgetBuilder> routes = const {},
}) => MaterialApp(
  routes: routes,
  home: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: GlobalSessionsScreen(controller: controller),
    ),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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
    expect(find.text('No sessions yet'), findsNothing);
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
    expect(find.textContaining('Archived'), findsOneWidget);
  });

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

    expect(find.text('50+'), findsOneWidget);
    final list = find.byKey(
      const PageStorageKey<String>('global-sessions-list'),
    );
    await tester.drag(list, const Offset(0, -10000));
    await tester.pumpAndSettle();

    expect(repository.calls, hasLength(2));
    expect(repository.calls.last.cursor, 'opaque/next+token=');
    expect(find.text('51'), findsOneWidget);
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

    final row = find.bySemanticsLabel(
      RegExp(r'Open Global project session\. runtime-probe'),
    );
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

  testWidgets('wake refresh searches through the replacement repository', (
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
    expect(replacement.calls, hasLength(1));
    expect(find.text('Session 1'), findsNothing);
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

    // Continue here lives in each row's overflow menu, never as a row icon.
    expect(find.byIcon(Icons.move_to_inbox_rounded), findsNothing);
    for (final (id, offered) in const [
      ('ses_1', false),
      ('ses_2', false),
      ('ses_3', true),
    ]) {
      await tester.tap(find.byKey(ValueKey('global-session-actions-$id')));
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
    expect(find.text('Continue this session here?'), findsOneWidget);
    expect(repository.stealCalls, isEmpty);

    await tester.tap(find.widgetWithText(FilledButton, 'Continue here'));
    await tester.pumpAndSettle();

    expect(repository.stealCalls, ['ses_2']);
    expect(find.text('stolen chat opened'), findsOneWidget);
    // Opening did not re-route through the stale stored location.
    expect(controller.locations, isEmpty);
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
    await tester.tap(find.widgetWithText(FilledButton, 'Continue here'));
    await tester.pumpAndSettle();

    expect(find.text('Sync is unavailable'), findsOneWidget);
    expect(find.byKey(const ValueKey('global-session-ses_2')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('global-session-actions-ses_2')),
      findsOneWidget,
    );
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
    expect(find.text('Continue this session here?'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

/// Opens the row's overflow menu and picks Continue here.
Future<void> _continueHere(WidgetTester tester, String id) async {
  await tester.tap(find.byKey(ValueKey('global-session-actions-$id')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(ValueKey('steal-session-$id')));
  await tester.pumpAndSettle();
}
