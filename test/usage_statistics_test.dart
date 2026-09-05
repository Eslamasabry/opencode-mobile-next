import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/usage_overview.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/usage_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts;
import 'api2_interaction_gateway_test.dart'
    show withServer, gatewayFor, writeJson;

Map<String, dynamic> fixture() =>
    (jsonDecode(
          File('test/fixtures/api2/session_stats.json').readAsStringSync(),
        )
        as Map<String, dynamic>)['data'];
UsageStatistics stats({double cost = 3.42}) =>
    UsageStatistics.fromJson(fixture()..['cost'] = cost);
Map<String, dynamic> empty() => {
  'range': {'from': 0, 'to': 0},
  'sessions': 0,
  'subagents': 0,
  'prompts': 0,
  'steps': 0,
  'tokens': {
    'input': 0,
    'output': 0,
    'reasoning': 0,
    'cache': {'read': 0, 'write': 0},
  },
  'cost': 0,
  'tools': {
    'mode': 'summary',
    'totals': {'calls': 0, 'succeeded': 0, 'failed': 0, 'unfinished': 0},
  },
  'activeDays': 0,
  'streak': 0,
  'activity': [],
  'models': [],
};

class _Repository extends ProductRepository implements UsageStatisticsGateway {
  final queries = <UsageQuery>[];
  final pending = <Completer<UsageStatistics>>[];
  bool defer = false;
  bool supported = true;
  Object? failure;
  UsageStatistics result = stats();
  WorkspaceProject? project = const WorkspaceProject(
    id: 'project-primary',
    name: 'Shopfront',
    directory: '/work/shop',
    worktrees: [],
    updatedAt: 1,
  );
  @override
  bool get usageStatisticsSupported => supported;
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => project;
  @override
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query) async {
    queries.add(query);
    if (failure != null) throw failure!;
    if (!defer) return result;
    final response = Completer<UsageStatistics>();
    pending.add(response);
    return response.future;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Legacy extends ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');
  @override
  Future<Health> health() async => Health(healthy: true, version: 'fixture');
}

class _Connection extends ConnectionController {
  _Connection(super.store);
  Completer<void>? wake;
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    await wake?.future;
    return repository;
  }

  @override
  Future<ServerGateway?> prepareActionTransport() async => api;
  void move() {
    locationRevision++;
    notifyListeners();
  }
}

Future<
  ({_Connection connection, _Repository repository, UsageOverview overview})
>
harness({bool nativeTimezone = false}) async {
  final repository = _Repository();
  repository.result = UsageStatistics.fromJson(
    fixture()
      ..['range'] = {
        'from': DateTime(2026, 8, 7).millisecondsSinceEpoch,
        'to': DateTime(2026, 9, 5, 12).millisecondsSinceEpoch + 1,
      },
  );
  final connection =
      _Connection(ProfileStore(prefs: await SharedPreferences.getInstance()))
        ..repository = repository
        ..api = _Api();
  final overview = UsageOverview(
    connection,
    clock: () => DateTime(2026, 9, 5, 12),
    timezoneLoader: nativeTimezone ? null : () async => 'Asia/Dubai',
  );
  addTearDown(connection.dispose);
  addTearDown(overview.dispose);
  return (connection: connection, repository: repository, overview: overview);
}

Future<void> flush() => Future<void>.delayed(Duration.zero);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'temporary missing repository keeps usage refresh recoverable',
    () async {
      final h = await harness();
      await h.overview.refresh();
      final previous = h.overview.snapshot;
      h.connection.repository = null;
      await h.overview.refresh();
      expect(h.overview.error, isA<UsageRefreshInterrupted>());
      expect(h.overview.snapshot, same(previous));
      expect(h.repository.supported, isTrue);
      h.connection.repository = h.repository;
      await h.overview.refresh();
      expect(h.overview.error, isNull);
      expect(h.overview.snapshot, isNotNull);
    },
  );

  test(
    'response preserves tokens, models and reliability without fake empty values',
    () {
      final result = stats();
      expect(result.sessions, 12);
      expect(result.prompts, 38);
      expect(result.tokens.total, 216800);
      expect(result.models.first.variant, 'high');
      expect(result.models.last.cost, .71);
      expect(
        UsageStatistics.fromJson(
          empty()..['range'] = {'from': -1, 'to': 0},
        ).from,
        -1,
      );
      expect(
        () => UsageStatistics.fromJson(
          empty()..['range'] = {'from': 0, 'to': 1e18},
        ),
        throwsFormatException,
      );
      expect(result.tools!.successRate, closeTo(72 / 77, .00001));
      expect(result.tools!.unfinished, 3);
      expect(result.activity.first.date, '2026-09-03');
      expect(UsageStatistics.fromJson(empty()).isEmpty, isTrue);
      expect(UsageStatistics.fromJson(empty()).tools!.successRate, isNull);
      expect(
        () => UsageStatistics.fromJson(fixture()..remove('cost')),
        throwsFormatException,
      );
      expect(
        () => UsageStatistics.fromJson(fixture()..['prompts'] = -1),
        throwsFormatException,
      );
      expect(
        () => UsageStatistics.fromJson(
          fixture()..['range'] = {'from': 10, 'to': 1},
        ),
        throwsFormatException,
      );
    },
  );

  test('calendar ranges include today and omit from for all time', () {
    final now = DateTime(2026, 3, 10, 14, 30);
    final expected = {
      UsageRange.today: DateTime(2026, 3, 10),
      UsageRange.thirtyDays: DateTime(2026, 2, 9),
      UsageRange.year: DateTime(2026, 1, 1),
    };
    for (final entry in expected.entries) {
      final query = UsageQuery.forRange(
        entry.key,
        now: now,
        timezone: 'America/New_York',
      );
      expect(query.from, entry.value.millisecondsSinceEpoch);
      expect(query.to, now.millisecondsSinceEpoch + 1);
      expect(query.toQuery()['timezone'], 'America/New_York');
    }
    final all = UsageQuery.forRange(
      UsageRange.allTime,
      now: now,
      timezone: 'Asia/Dubai',
    );
    expect(all.toQuery().containsKey('from'), isFalse);
    expect(all.toQuery().containsKey('project'), isFalse);
    final midnight = UsageQuery.forRange(
      UsageRange.today,
      now: DateTime(2026, 1, 1),
      timezone: 'UTC',
    );
    expect(midnight.to - midnight.from!, 1);
    expect(
      () => const UsageQuery(from: 2, to: 1, timezone: 'UTC').toQuery(),
      throwsArgumentError,
    );
  });

  test(
    'wire request is global unless an explicit project ID is selected',
    () async {
      await withServer(
        handler: (request) => writeJson(request, {'data': fixture()}),
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final repository = Api2OperationsGateway(client: gateway.client);
          final result = await repository.loadUsageStatistics(
            const UsageQuery(from: 100, to: 200, timezone: 'Asia/Dubai'),
          );
          await repository.loadUsageStatistics(
            const UsageQuery(
              to: 200,
              timezone: 'America/New_York',
              projectID: 'project-two',
            ),
          );
          expect(result.cost, 3.42);
          expect(requests[0].uri.path, '/api/session/stats');
          expect(requests[0].uri.queryParameters, {
            'from': '100',
            'to': '200',
            'timezone': 'Asia/Dubai',
            'tools': 'summary',
          });
          expect(requests[1].uri.queryParameters, {
            'to': '200',
            'timezone': 'America/New_York',
            'project': 'project-two',
            'tools': 'summary',
          });
        },
      );
    },
  );

  test(
    'authorization errors stay distinct; absent endpoint disables capability',
    () async {
      var status = 401;
      await withServer(
        handler: (request) => writeJson(request, {
          '_tag': status == 401 ? 'UnauthorizedError' : 'NotFoundError',
          'message': 'unavailable',
        }, status: status),
        (server, _) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final repository = Api2OperationsGateway(client: gateway.client);
          const query = UsageQuery(to: 200, timezone: 'UTC');
          await expectLater(
            repository.loadUsageStatistics(query),
            throwsA(isA<Api2AuthRequired>()),
          );
          expect(repository.usageStatisticsSupported, isTrue);
          status = 404;
          await expectLater(
            repository.loadUsageStatistics(query),
            throwsA(isA<UsageUnsupported>()),
          );
          expect(repository.usageStatisticsSupported, isFalse);
        },
      );
    },
  );

  test(
    'native timezone is sent and unreadable timezone never silently becomes UTC',
    () async {
      const channel = MethodChannel('flutter_timezone');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'identifier': 'America/New_York'},
      );
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final h = await harness(nativeTimezone: true);
      await h.overview.refresh();
      expect(h.repository.queries.single.timezone, 'America/New_York');
      messenger.setMockMethodCallHandler(channel, (_) async => 'Etc/Unknown');
      await h.overview.setRange(UsageRange.today);
      expect(h.overview.error, isA<UsageTimezoneUnavailable>());
      expect(h.repository.queries, hasLength(1));
      expect(h.overview.snapshot, isNull);
    },
  );

  test(
    'current project resolves its ID; missing project cannot become all projects',
    () async {
      final h = await harness();
      await h.overview.setScope(UsageScope.currentProject);
      expect(h.repository.queries.single.projectID, 'project-primary');
      expect(h.overview.snapshot!.projectName, 'Shopfront');
      h.repository.project = null;
      await h.overview.setRange(UsageRange.today);
      expect(h.overview.error, isA<UsageProjectUnavailable>());
      expect(h.overview.snapshot, isNull);
      expect(h.repository.queries, hasLength(1));
      await h.overview.setScope(UsageScope.allProjects);
      expect(h.repository.queries.last.projectID, isNull);
    },
  );

  test(
    'out-of-order responses cannot relabel old totals with new filters',
    () async {
      final h = await harness();
      h.repository.defer = true;
      final first = h.overview.refresh();
      await flush();
      final second = h.overview.setRange(UsageRange.today);
      await flush();
      expect(h.repository.pending, hasLength(2));
      h.repository.pending[1].complete(stats(cost: 2));
      await second;
      h.repository.pending[0].complete(stats(cost: 1));
      await first;
      expect(h.overview.snapshot!.range, UsageRange.today);
      expect(h.overview.snapshot!.statistics.cost, 2);
    },
  );

  test(
    'location changes during wake detach the page without dispatching',
    () async {
      final h = await harness();
      h.connection.wake = Completer<void>();
      final pending = h.overview.refresh();
      h.connection.move();
      h.connection.wake!.complete();
      await pending;
      expect(h.overview.detached, isTrue);
      expect(h.overview.loading, isFalse);
      expect(h.overview.snapshot, isNull);
      expect(h.repository.queries, isEmpty);
    },
  );

  test(
    'failed refresh retains only the previous result for unchanged filters',
    () async {
      final h = await harness();
      await h.overview.refresh();
      final previous = h.overview.snapshot;
      h.repository.failure = const Api2NetworkError('Offline');
      await h.overview.refresh();
      expect(h.overview.snapshot, same(previous));
      expect(h.overview.error, isA<Api2NetworkError>());
      await h.overview.setScope(UsageScope.currentProject);
      expect(h.overview.snapshot, isNull);
    },
  );

  testWidgets(
    'Settings hides usage for v1 and known unsupported repositories',
    (tester) async {
      final h = await harness();
      await tester.pumpWidget(
        MaterialApp(home: SettingsScreen(controller: h.connection)),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('settings-category-usage')),
        findsOneWidget,
      );
      h.repository.supported = false;
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        MaterialApp(home: SettingsScreen(controller: h.connection)),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('settings-category-usage')),
        findsNothing,
      );
      h.connection.repository = _Legacy();
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(
        MaterialApp(home: SettingsScreen(controller: h.connection)),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('settings-category-usage')),
        findsNothing,
      );
    },
  );

  testWidgets(
    'phone usage layout shows scoped totals and retains retry feedback',
    (tester) async {
      final h = await harness();
      tester.view.physicalSize = const Size(411, 820);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final preview = Platform.environment['OC_USAGE_CAPTURE'];
      if (preview != null) await loadCaptureFonts();
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: true),
            home: UsageScreen(controller: h.connection, overview: h.overview),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(r'$3.42'), findsOneWidget);
      expect(find.text('Timezone: Asia/Dubai'), findsOneWidget);
      if (preview != null) {
        final png = await capturePng(tester, boundary, pixelRatio: 1);
        File(preview).writeAsBytesSync(png);
      }
      h.repository.failure = const Api2NetworkError('Offline');
      await tester.tap(find.byKey(const ValueKey('refresh-usage')));
      await tester.pumpAndSettle();
      expect(
        find.text('Showing the previous result for these filters.'),
        findsOneWidget,
      );
      expect(find.text(r'$3.42'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('empty range and 320px large text remain usable', (tester) async {
    final h = await harness();
    h.repository.result = UsageStatistics.fromJson(empty());
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(1.6)),
          child: child!,
        ),
        home: UsageScreen(controller: h.connection, overview: h.overview),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text(
        'No activity in this range. Try a wider range or All projects.',
      ),
      250,
      scrollable: find.byType(Scrollable).first,
    );
    expect(tester.takeException(), isNull);
    expect(
      find.text(
        'No activity in this range. Try a wider range or All projects.',
      ),
      findsOneWidget,
    );
  });
}
