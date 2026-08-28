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
  int? cursor,
  int limit,
});

class _FinderRepository implements ProductRepository {
  _FinderRepository(this.handler);

  final Future<List<GlobalSessionResult>> Function(_SessionQuery query) handler;
  final calls = <_SessionQuery>[];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    int? cursor,
    int limit = 50,
  }) {
    final query = (
      search: search,
      includeArchived: includeArchived,
      cursor: cursor,
      limit: limit,
    );
    calls.add(query);
    return handler(query);
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

  testWidgets('finder paginates with the last server timestamp', (
    tester,
  ) async {
    final repository = _FinderRepository((query) async {
      if (query.cursor == null) {
        return List.generate(
          50,
          (index) => _result(index, updated: 5000 - index),
        );
      }
      return [_result(80, updated: 4800)];
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
    expect(repository.calls.last.cursor, 4951);
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
}
