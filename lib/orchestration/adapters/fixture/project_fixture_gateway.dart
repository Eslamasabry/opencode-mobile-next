import 'dart:async';
import 'dart:convert';
import '../../../domain/orchestration_gateway.dart';
import '../../../ui/kit/kit_redact.dart';
import '../none.dart';

part 'project_fixture/apply.dart';
part 'project_fixture/helpers.dart';
part 'project_fixture/advance.dart';
part 'project_fixture/seed.dart';

/// Local deterministic simulator. Never starts agents or writes repositories.
class ProjectFixtureGateway extends NullOrchestrationGateway
    implements OrchestrationProjectGateway {
  ProjectFixtureGateway({
    required this.persistence,
    DateTime Function()? now,
    Duration? tickInterval,
    this.seedDemo = true,
    this.readOnly = false,
    this.isCharging = true,
  }) : _now = now ?? DateTime.now {
    if (tickInterval != null && !readOnly) {
      _timer = Timer.periodic(tickInterval, (_) {
        unawaited(advance().catchError((Object _) {}));
      });
    }
  }
  final TeamProjectPersistence persistence;
  final DateTime Function() _now;
  final bool seedDemo;
  final bool readOnly;
  bool isCharging;
  final _changes = StreamController<TeamWorkspace>.broadcast();
  TeamWorkspace? _state;
  Map<String, dynamic> _requests = {};
  Future<void> _tail = Future.value();
  Timer? _timer;
  bool _closed = false;
  Future<void>? _closing;
  @override
  bool get isClosed => _closed;
  @override
  OrchestrationHostIdentity get host => const OrchestrationHostIdentity(
    provider: 'fixture',
    url: 'fixture://project-demo',
    hostMode: OrchestrationHostMode.computer,
  );
  @override
  OrchestrationCapabilities get capabilities => const OrchestrationCapabilities(
    projects: true,
    workGraph: true,
    gatesInteractions: true,
    controlRespond: true,
    eventStream: true,
    projectLifecycle: true,
    livingSpec: true,
    projectLanes: true,
    projectPlacement: true,
    projectVerification: true,
    projectMergeQueue: true,
    projectPromotion: true,
    projectResume: true,
    projectBudgets: true,
    projectDigest: true,
  );
  String get _at => _now().toUtc().toIso8601String();
  Never _fail<T>(String code) => throw _Refused(code);
  Future<T> _serial<T>(Future<T> Function() action) {
    final result = _tail.then((_) => action());
    _tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  Future<void> _load() async {
    if (_state != null) return;
    final raw = await persistence.read();
    if (raw != null) {
      final json = jsonDecode(raw) as Map<String, dynamic>;
      if (json['schemaVersion'] != 1) _fail<void>('unsupportedVersion');
      _requests = Map<String, dynamic>.from(json['requests'] as Map? ?? {});
      var state = TeamWorkspace.fromJson(
        Map<String, dynamic>.from(json['workspace'] as Map),
      );
      if (readOnly) {
        _state = state;
        return;
      }
      state = state.copyWith(
        projects: state.projects
            .map(
              (p) => p.copyWith(
                tasks: p.tasks
                    .map(
                      (t) => t.status == 'running'
                          ? t.copyWith(
                              status: 'interrupted',
                              reason: 'Continue from the saved branch',
                              changedAt: _at,
                            )
                          : t,
                    )
                    .toList(),
              ),
            )
            .toList(),
      );
      await _persist(state, _requests);
      _state = state;
    } else {
      if (readOnly) {
        _state = const TeamWorkspace();
        return;
      }
      final state = _initial();
      await _persist(state, {});
      _state = state;
    }
  }

  Future<void> _persist(TeamWorkspace state, Map<String, dynamic> requests) =>
      persistence.write(
        jsonEncode(
          _redact({
            'schemaVersion': 1,
            'workspace': state.toJson(),
            'requests': requests,
          }),
        ),
      );
  Object? _redact(Object? v) => switch (v) {
    String s => KitRedact.text(s),
    List a => a.map(_redact).toList(),
    Map a => a.map((k, v) => MapEntry(k, _redact(v))),
    _ => v,
  };
  @override
  Future<TeamWorkspace> teamWorkspace() => _serial(() async {
    if (_closed) return _state ?? const TeamWorkspace();
    await _load();
    return _state!;
  });
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => _changes.stream;
  @override
  Future<TeamCommandResult> executeProject(
    TeamProjectCommand command,
  ) => _serial(() async {
    if (readOnly) {
      return const TeamCommandResult(accepted: false, code: 'readOnly');
    }
    if (_closed) {
      return const TeamCommandResult(accepted: false, code: 'closed');
    }
    try {
      await _load();
      if (command.requestId.trim().isEmpty) _fail<void>('requestIdRequired');
      final fingerprint = jsonEncode(_redact(command.toJson()));
      final previous = _requests[command.requestId];
      if (previous is Map) {
        if (previous['fingerprint'] != fingerprint) {
          _fail<void>('requestIdReused');
        }
        return TeamCommandResult(
          accepted: true,
          projectId: previous['projectId'] as String,
          revision: previous['revision'] as int,
          replayed: true,
        );
      }
      final next = _apply(_state!, command);
      final id =
          command.projectId.isEmpty &&
              (command.action == TeamProjectAction.createProject ||
                  command.action == TeamProjectAction.createQuickTask)
          ? next.projects.last.id
          : command.projectId;
      final revision =
          next.projects.where((p) => p.id == id).firstOrNull?.revision ??
          next.revision;
      final requests = Map<String, dynamic>.from(_requests)
        ..[command.requestId] = {
          'fingerprint': fingerprint,
          'projectId': id,
          'revision': revision,
        };
      // Decode the redacted representation so published snapshots also contain no secrets.
      final safe = TeamWorkspace.fromJson(
        Map<String, dynamic>.from(_redact(next.toJson()) as Map),
      );
      await _persist(safe, requests);
      _state = safe;
      _requests = requests;
      if (!_closed) _changes.add(safe);
      return TeamCommandResult(
        accepted: true,
        projectId: id,
        revision: revision,
      );
    } on _Refused catch (e) {
      return TeamCommandResult(accepted: false, code: e.code);
    } catch (_) {
      return const TeamCommandResult(accepted: false, code: 'saveFailed');
    }
  });
  Future<void> advance() async {
    if (_closed) return;
    final state = await teamWorkspace();
    for (final project in state.projects.where((p) => p.status == 'running')) {
      await executeProject(
        TeamProjectCommand(
          requestId: 'tick-${state.revision}-${project.id}',
          action: TeamProjectAction.advance,
          projectId: project.id,
          expectedRevision: project.revision,
        ),
      );
    }
  }

  @override
  Future<void> close() => _closing ??= _close();
  Future<void> _close() async {
    _closed = true;
    _timer?.cancel();
    await _tail;
    if (!_changes.isClosed) await _changes.close();
  }

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => _changes.stream.expand(
    (w) => <OrchestrationEvent>[
      BeadChanged(
        beadId: 'project-workspace',
        change: BeadChange.updated,
        seq: w.revision * 2,
      ),
      GateChanged(gateId: 'project-requests', seq: w.revision * 2 + 1),
    ],
  );

  @override
  Future<void> deleteLocalData() async {
    if (readOnly) {
      await close();
      return;
    }
    _closed = true;
    _timer?.cancel();
    await _tail;
    await persistence.delete();
    _state = const TeamWorkspace();
    _requests = {};
    if (!_changes.isClosed) await _changes.close();
  }

  @override
  Future<List<OrchestrationProject>> projects() async => (await teamWorkspace())
      .projects
      .map(
        (p) => OrchestrationProject(
          id: p.id,
          name: p.name,
          raw: {'simulated': true},
        ),
      )
      .toList();
  @override
  Future<List<WorkItem>> work({String? projectId}) async =>
      (await teamWorkspace()).projects
          .where((p) => projectId == null || p.id == projectId)
          .expand(
            (p) => p.tasks.map(
              (t) => WorkItem(
                id: t.id,
                title: t.title,
                state: WorkState.fromProvider(t.status),
                projectId: p.id,
                assignee: t.roleId,
                dependsOn: t.dependsOn,
                updatedAt: DateTime.tryParse(t.changedAt),
                raw: {'simulated': true},
              ),
            ),
          )
          .toList();
  @override
  Future<List<OrchestrationGate>> gates() async => (await teamWorkspace())
      .projects
      .expand(
        (p) => p.requests
            .where((r) => !r.answered)
            .map(
              (r) => OrchestrationGate(
                id: r.id,
                kind: GateKind.freeText,
                title: r.title,
                workId: r.taskId,
                createdAt: DateTime.tryParse(r.createdAt),
                raw: {'projectId': p.id, 'simulated': true},
              ),
            ),
      )
      .toList();
  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) async {
    final state = await teamWorkspace();
    final p = state.projects
        .where((p) => p.requests.any((r) => r.id == gateId))
        .firstOrNull;
    if (p == null) {
      return MutationReceipt.rejected(requestId, 'Request unavailable');
    }
    final r = await executeProject(
      TeamProjectCommand(
        requestId: requestId,
        action: TeamProjectAction.answerRequest,
        projectId: p.id,
        expectedRevision: p.revision,
        targetId: gateId,
        text: response.text ?? response.choice ?? '${response.confirmed}',
      ),
    );
    return MutationReceipt(
      id: requestId,
      status: r.accepted
          ? MutationReceiptStatus.accepted
          : MutationReceiptStatus.rejected,
      message: r.code,
    );
  }
}

class _Refused implements Exception {
  const _Refused(this.code);
  final String code;
}
