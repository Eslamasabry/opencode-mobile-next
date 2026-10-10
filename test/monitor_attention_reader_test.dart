import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/project_fixture_gateway.dart';
import 'package:opencode_mobile/state/team_project_persistence.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/attention_feed.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/work_row_status.dart';
import 'package:opencode_mobile/state/monitor_attention_reader.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/profiles.dart';

import 'support/profile_monitor_fixture.dart';
import 'package:opencode_mobile/orchestration/adapters/inapp/phone_engine_gateway.dart';
import 'phone_project_engine_gateway_test.dart' as phone;

final at = DateTime.utc(2026, 9, 28);
ServerProfile profile({bool team = false}) => ServerProfile(
  id: 'server',
  name: 'Server',
  baseUrl: 'https://server.example',
  orchestration: team
      ? const OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'fixture://unused',
        )
      : null,
);

MessageWithParts message(String role, int time, {String? finish}) =>
    MessageWithParts(
      info: MessageInfo(
        id: '$role-$time',
        sessionID: 's',
        role: role,
        finish: finish,
        time: MsgTime(
          created: time,
          completed: finish == null ? null : time + 1,
        ),
      ),
      parts: [],
    );

class _Messages extends MonitorTestGateway {
  final calls = <String>[];
  final limits = <int>[];
  ServerPage<MessageWithParts> page = ServerPage(
    items: [
      message('user', 1),
      message('assistant', 2, finish: 'length'),
    ],
  );
  Future<ServerPage<MessageWithParts>> Function()? pendingPage;
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    calls.add(id);
    limits.add(limit);
    return pendingPage == null ? page : await pendingPage!();
  }
}

class _Team implements OrchestrationGateway {
  final calls = <String>[];
  List<WorkItem> items = const [];
  List<OrchestrationGate> pending = const [];
  bool failWork = false;
  @override
  bool isClosed = false;
  @override
  OrchestrationCapabilities get capabilities =>
      const OrchestrationCapabilities(workGraph: true, gatesInteractions: true);
  @override
  Future<List<WorkItem>> work({String? projectId}) async {
    calls.add('work');
    if (failWork) throw StateError('technical text must never be retained');
    return items;
  }

  @override
  Future<List<OrchestrationGate>> gates() async {
    calls.add('gates');
    return pending;
  }

  @override
  Future<void> close() async => isClosed = true;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ProbeVerdict> found(OrchestrationConfig _) async => const ProbeFound(
  host: OrchestrationHostIdentity(
    provider: 'fixture',
    url: 'fixture://unused',
    hostMode: OrchestrationHostMode.computer,
  ),
);
MonitorGatewayPair _pair(_Messages gateway) =>
    (gateway: gateway, operations: MonitorTestOperations());

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'phone monitor authenticates the selected profile and reads project attention',
    () async {
      final selected = profile()
        ..teamEngineAuth = 'private-phone-token'
        ..orchestration = const OrchestrationConfig(
          provider: OrchestrationProvider.phoneEngine,
          url: 'http://127.0.0.1:42761',
        );
      final adapter = phone.FakeEngineAdapter((request) async {
        expect(request.headers['Authorization'], 'Bearer private-phone-token');
        return phone.jsonBody(
          request.path == '/v1/health'
              ? phone.health('server')
              : const TeamWorkspace(
                  simulated: false,
                  projects: [
                    TeamProject(
                      id: 'project',
                      simulated: false,
                      tasks: [
                        TeamTask(
                          id: 'failed',
                          title: 'Failed task',
                          status: 'failed',
                        ),
                        TeamTask(
                          id: 'running',
                          title: 'Running task',
                          status: 'running',
                        ),
                        TeamTask(
                          id: 'interrupted',
                          title: 'Paused task',
                          status: 'interrupted',
                        ),
                      ],
                      requests: [
                        TeamRequest(
                          id: 'question',
                          taskId: 'task',
                          title: 'Choose a behavior',
                        ),
                        TeamRequest(
                          id: 'answered',
                          answered: true,
                          title: 'Already answered',
                        ),
                      ],
                    ),
                  ],
                ).toJson(),
        );
      });
      final reader = MonitorAttentionReader(
        probe: (_) => throw StateError('Credential-free probe must not run'),
        phoneGatewayBuilder: (owner) => PhoneEngineGateway(
          baseUrl: owner.orchestration!.url,
          profileId: owner.id,
          bearerToken: owner.teamEngineAuth,
          adapter: adapter,
        ),
      );
      final details = await reader.read(
        selected,
        _pair(_Messages()),
        [],
        {},
        () => true,
      );
      expect(details.teamComplete, isTrue);
      // The gateway presents an interrupted task that cannot resume itself
      // as `review` (and a resumable one as `paused`); neither is a failure
      // or a request, so only the open request and the failure need the
      // person. The running task never does.
      expect(
        details.items.map((item) => item.taskID),
        containsAll(['task', 'failed']),
      );
      expect(
        details.items.map((item) => item.taskID),
        isNot(contains('running')),
      );
      expect(details.items, hasLength(2));
      expect(adapter.requests.map((request) => request.path), [
        '/v1/health',
        '/v1/workspace',
      ]);
      expect(adapter.closed, isTrue);
      reader.dispose();
    },
  );

  test(
    'phone monitor treats wrong identity and missing credentials as unknown',
    () async {
      for (final missing in [false, true]) {
        final selected = profile()
          ..teamEngineAuth = missing ? '' : 'private-phone-token'
          ..orchestration = const OrchestrationConfig(
            provider: OrchestrationProvider.phoneEngine,
            url: 'http://127.0.0.1:42761',
          );
        final adapter = phone.FakeEngineAdapter(
          (_) async => phone.jsonBody(phone.health('another-profile')),
        );
        final reader = MonitorAttentionReader(
          phoneGatewayBuilder: (owner) => PhoneEngineGateway(
            baseUrl: owner.orchestration!.url,
            profileId: owner.id,
            bearerToken: owner.teamEngineAuth,
            adapter: adapter,
          ),
        );
        final details = await reader.read(
          selected,
          _pair(_Messages()),
          [],
          {},
          () => true,
        );
        expect(details.teamComplete, isFalse);
        expect(details.items, isEmpty);
        expect(adapter.requests.length, missing ? 0 : 1);
        if (!missing) expect(adapter.closed, isTrue);
        reader.dispose();
      }
    },
  );

  test('phone project inventory cap keeps coverage partial', () async {
    final selected = profile()
      ..teamEngineAuth = 'private-phone-token'
      ..orchestration = const OrchestrationConfig(
        provider: OrchestrationProvider.phoneEngine,
        url: 'http://127.0.0.1:42761',
      );
    final adapter = phone.FakeEngineAdapter(
      (request) async => phone.jsonBody(
        request.path == '/v1/health'
            ? phone.health('server')
            : TeamWorkspace(
                simulated: false,
                projects: [
                  TeamProject(
                    id: 'project',
                    tasks: List.generate(
                      300,
                      (i) =>
                          TeamTask(id: '$i', title: 'task', status: 'failed'),
                    ),
                  ),
                ],
              ).toJson(),
      ),
    );
    final reader = MonitorAttentionReader(
      phoneGatewayBuilder: (owner) => PhoneEngineGateway(
        baseUrl: owner.orchestration!.url,
        profileId: owner.id,
        bearerToken: owner.teamEngineAuth,
        adapter: adapter,
      ),
    );
    final details = await reader.read(
      selected,
      _pair(_Messages()),
      [],
      {},
      () => true,
    );
    expect(details.teamComplete, isFalse);
    expect(
      details.items,
      hasLength(MonitorAttentionReader.teamRecordLimit - 1),
    );
    expect(adapter.closed, isTrue);
    reader.dispose();
  });

  test(
    'project demo monitor reads saved decisions without mutating recovery',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final persistence = SharedPreferencesTeamProjectPersistence(
        prefs,
        'server',
      );
      final fixture = ProjectFixtureGateway(persistence: persistence);
      final saved = await fixture.teamWorkspace();
      await fixture.close();
      final before = await persistence.read();
      final p = profile()
        ..orchestration = const OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'fixture://project-demo',
        );
      final reader = MonitorAttentionReader();
      final details = await reader.read(
        p,
        _pair(_Messages()),
        [],
        {},
        () => true,
      );
      reader.dispose();
      expect(saved.projects, isNotEmpty);
      expect(details.teamComplete, isTrue);
      expect(details.items, isNotEmpty);
      expect(await persistence.read(), before);
    },
  );

  test('capped team inventory stays partial', () async {
    final team = _Team()
      ..pending = [
        for (var i = 0; i < 257; i++)
          OrchestrationGate(id: '$i', kind: GateKind.choice, title: 'Choose'),
      ];
    final reader = MonitorAttentionReader(
      probe: found,
      teamGatewayFactory: (_, _) => team,
    );
    addTearDown(reader.dispose);
    final details = await reader.read(
      profile(team: true),
      _pair(_Messages()),
      [],
      {},
      () => true,
    );
    expect(details.items.length, 256);
    expect(details.teamComplete, false);
    expect(details.complete, false);
    expect(team.isClosed, true);
  });
  test(
    'the shared deadline stops later reads and closes a late factory',
    () async {
      final late = Completer<OrchestrationGateway>();
      final team = _Team();
      final reader = MonitorAttentionReader(
        probe: found,
        teamGatewayFactory: (_, _) => late.future,
        // Long enough that a loaded runner reaches the factory before the
        // deadline; the test needs the factory pending when it expires.
        timeout: const Duration(milliseconds: 250),
      );
      addTearDown(reader.dispose);
      final details = await reader.read(
        profile(team: true),
        _pair(_Messages()),
        [],
        {},
        () => true,
      );
      expect(details.complete, false);
      expect(details.teamComplete, false);
      late.complete(team);
      await Future<void>.delayed(Duration.zero);
      expect(team.isClosed, true);
      expect(team.calls, isEmpty);
    },
  );
  test(
    'confirmed current failure is reported; busy clears it without history',
    () async {
      final reader = MonitorAttentionReader(now: () => at);
      addTearDown(reader.dispose);
      final gateway = _Messages();
      final sessions = [Session(id: 's', title: 'Task')];
      final failed = await reader.read(
        profile(),
        _pair(gateway),
        sessions,
        {},
        () => true,
      );
      expect(failed.complete, true);
      expect(failed.items.single.kind, AttentionKind.failedRun);
      expect(failed.items.single.facts.phase, WorkRowPhase.failed);
      expect(failed.checkedSessionIDs, {'s'});
      final busy = await reader.read(profile(), _pair(gateway), sessions, {
        's': 'busy',
      }, () => true);
      expect(busy.items, isEmpty);
      expect(busy.checkedSessionIDs, {'s'});
      expect(gateway.calls, ['s']);
      gateway.page = ServerPage(items: [message('user', 3)]);
      final nextTurn = await reader.read(
        profile(),
        _pair(gateway),
        sessions,
        {},
        () => true,
      );
      expect(nextTurn.items, isEmpty);
      expect(nextTurn.checkedSessionIDs, {'s'});
    },
  );

  test(
    'bounded rotation and truncated history retain uncertain coverage',
    () async {
      final reader = MonitorAttentionReader(now: () => at);
      addTearDown(reader.dispose);
      final gateway = _Messages();
      final sessions = [for (var i = 0; i < 6; i++) Session(id: '$i')];
      final first = await reader.read(
        profile(),
        _pair(gateway),
        sessions,
        {},
        () => true,
      );
      expect(first.complete, false);
      expect(gateway.calls, ['0', '1', '2', '3']);
      await reader.read(profile(), _pair(gateway), sessions, {}, () => true);
      expect(gateway.calls.skip(4), ['4', '5', '0', '1']);
      expect(gateway.limits, everyElement(50));
      gateway.page = ServerPage(
        items: [message('assistant', 2, finish: 'length')],
        nextCursor: 'older',
      );
      final unknown = await reader.read(
        profile(),
        _pair(gateway),
        [Session(id: 's')],
        {},
        () => true,
      );
      expect(unknown.items, isEmpty);
      expect(unknown.checkedSessionIDs, isEmpty);
      expect(unknown.complete, false);
      reader.forget('server');
      gateway.calls.clear();
      await reader.read(profile(), _pair(gateway), sessions, {}, () => true);
      expect(gateway.calls.first, '0');
    },
  );

  test(
    'revoked ownership rejects late history and performs no later calls',
    () async {
      final reader = MonitorAttentionReader(
        probe: (_) async => throw StateError('must not probe'),
      );
      addTearDown(reader.dispose);
      final gateway = _Messages();
      final pending = Completer<ServerPage<MessageWithParts>>();
      gateway.pendingPage = () => pending.future;
      var current = true;
      final result = reader.read(
        profile(team: true),
        _pair(gateway),
        [Session(id: 'a'), Session(id: 'b')],
        {},
        () => current,
      );
      current = false;
      pending.complete(gateway.page);
      final details = await result;
      expect(details.items, isEmpty);
      expect(details.complete, false);
      expect(details.checkedSessionIDs, isEmpty);
      expect(gateway.calls, ['a']);
    },
  );

  test(
    'team links use work session; missing session preserves gate fallback',
    () async {
      final team = _Team()
        ..items = const [
          WorkItem(
            id: 'w',
            title: 'Task',
            state: WorkState.working,
            sessionId: 'conversation',
          ),
        ]
        ..pending = const [
          OrchestrationGate(
            id: 'g',
            kind: GateKind.confirmation,
            title: 'Choose',
            workId: 'w',
          ),
          OrchestrationGate(
            id: 'missing',
            kind: GateKind.choice,
            title: 'Choose',
            workId: 'missing-work',
          ),
          OrchestrationGate(
            id: 'review',
            kind: GateKind.reviewReady,
            title: 'Review',
          ),
        ];
      final reader = MonitorAttentionReader(
        probe: found,
        teamGatewayFactory: (_, _) => team,
      );
      addTearDown(reader.dispose);
      final details = await reader.read(
        profile(team: true),
        _pair(_Messages()),
        [],
        {},
        () => true,
      );
      expect(details.complete, true);
      expect(details.teamComplete, true);
      expect(details.items.length, 2);
      expect(details.items.first.sessionID, 'conversation');
      expect(details.items.first.requestID, 'g');
      expect(details.items.last.sessionID, null);
      expect(details.items.last.taskID, 'missing-work');
      expect(team.isClosed, true);
    },
  );

  test(
    'partial team reads keep confirmed gates and close the owned gateway',
    () async {
      final team = _Team()
        ..failWork = true
        ..pending = const [
          OrchestrationGate(
            id: 'g',
            kind: GateKind.runFailed,
            title: 'Failure',
            runId: 'run',
          ),
        ];
      final reader = MonitorAttentionReader(
        probe: found,
        teamGatewayFactory: (_, _) => team,
      );
      addTearDown(reader.dispose);
      final details = await reader.read(
        profile(team: true),
        _pair(_Messages()),
        [],
        {},
        () => true,
      );
      expect(details.complete, false);
      expect(details.teamComplete, false);
      expect(details.items.single.taskID, isNull);
      expect(details.items.single.runID, 'run');
      expect(details.items.single.facts.phase, WorkRowPhase.failed);
      expect(team.isClosed, true);
    },
  );

  test(
    'team projection deduplicates failed work and run and preserves stale state',
    () {
      final rows = MonitorAttentionReader.teamObservations(
        gates: const [
          OrchestrationGate(
            id: 'g',
            kind: GateKind.runFailed,
            title: 'Failure',
            runId: 'run',
          ),
        ],
        work: const [
          WorkItem(
            id: 'w',
            title: 'Task',
            state: WorkState.failed,
            runId: 'run',
          ),
        ],
        runs: const [
          OrchestrationRun(id: 'run', title: 'Run', state: RunState.failed),
        ],
        observedAt: at,
        isFresh: false,
      );
      expect(rows.length, 1);
      expect(rows.single.isFresh, false);
    },
  );
}
