import 'dart:async';

import 'package:dio/dio.dart';

import '../../../domain/orchestration_gateway.dart';
import '../../../domain/phone_project_engine.dart';
import '../../../ui/kit/kit_redact.dart';
import '../none.dart';

/// App client only. Closing never stops the native daemon.
class PhoneEngineGateway extends NullOrchestrationGateway
    implements OrchestrationProjectGateway {
  PhoneEngineGateway({
    required String baseUrl,
    required this.profileId,
    required String bearerToken,
    PhoneEngineHealth? health,
    this.probedCapabilities,
    HttpClientAdapter? adapter,
    this.pollInterval = const Duration(seconds: 3),
    Duration timeout = const Duration(seconds: 15),
    DateTime Function()? now,
  }) : _health = health,
       _now = now ?? DateTime.now {
    final uri = Uri.tryParse(baseUrl);
    if (uri == null ||
        !uri.hasAuthority ||
        uri.scheme != 'http' ||
        !(uri.host == '127.0.0.1' || uri.host == '::1') ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        (uri.path.isNotEmpty && uri.path != '/') ||
        bearerToken.isEmpty ||
        bearerToken.contains(RegExp(r'[\r\n]')) ||
        profileId.isEmpty) {
      throw const PhoneEngineException('endpointInvalid');
    }
    _baseUrl = uri.replace(path: '').toString();
    _bearerToken = bearerToken;
    KitRedact.registerKnownSecret(bearerToken);
    _dio = Dio(
      BaseOptions(
        baseUrl: _baseUrl,
        connectTimeout: timeout,
        receiveTimeout: timeout,
        sendTimeout: timeout,
        responseType: ResponseType.json,
        followRedirects: false,
        maxRedirects: 0,
        validateStatus: (_) => true,
        headers: {
          'Accept': 'application/json',
          'Authorization': 'Bearer $bearerToken',
        },
      ),
    );
    if (adapter != null) _dio.httpClientAdapter = adapter;
  }

  /// One-shot callers revalidate lifetime/ownership after the health await and
  /// immediately before sending a command. Ordinary controllers leave it null.
  Future<void> Function()? beforeCommand;
  final String profileId;
  final DateTime Function() _now;
  final OrchestrationCapabilities? probedCapabilities;
  late final String _bearerToken;
  final Duration pollInterval;
  late final String _baseUrl;
  late final Dio _dio;
  PhoneEngineHealth? _health;
  PhoneEngineHealth? get health => _health;
  bool _closed = false, _deleting = false;
  Future<void>? _closing, _deletion;
  final Set<Future<void>> _inflight = {};
  final _snapshots = StreamController<TeamWorkspace>.broadcast();
  Timer? _pollTimer;
  bool _polling = false;
  final Set<void Function()> _closeListeners = {};

  /// Allows profile ownership to release clients when their caller closes them.
  void addCloseListener(void Function() listener) {
    if (_closed) {
      listener();
    } else {
      _closeListeners.add(listener);
    }
  }

  @override
  bool get isClosed => _closed || _deleting;

  /// Separate from deletion admission so the owner can drain every live client.
  bool get isDisconnected => _closed;

  void beginDeletion() {
    _deleting = true;
    _pollTimer?.cancel();
  }

  @override
  OrchestrationHostIdentity get host => OrchestrationHostIdentity(
    provider: 'phoneEngine',
    url: _baseUrl,
    hostMode: OrchestrationHostMode.phone,
    version: _health?.engineVersion,
  );

  @override
  OrchestrationCapabilities get capabilities {
    final h = _health;
    if (h == null) return probedCapabilities ?? OrchestrationCapabilities.none;
    bool all(List<TeamProjectAction> actions) =>
        actions.every(h.commandActions.contains);
    return OrchestrationCapabilities(
      projects: true,
      phoneHost: true,
      projectLifecycle: all([
        TeamProjectAction.createProject,
        TeamProjectAction.createQuickTask,
        TeamProjectAction.deleteProject,
      ]),
      livingSpec: all([
        TeamProjectAction.saveSpecDraft,
        TeamProjectAction.approveSpec,
      ]),
      projectLanes:
          h.canExecute &&
          all([
            TeamProjectAction.approvePlan,
            TeamProjectAction.pauseTask,
            TeamProjectAction.stopTask,
          ]),
      projectPlacement:
          h.canExecute &&
          all([TeamProjectAction.moveTask, TeamProjectAction.updateServer]),
      projectVerification:
          h.canExecute &&
          all([TeamProjectAction.verifyTask, TeamProjectAction.recheckTask]),
      projectMergeQueue:
          h.canExecute && all([TeamProjectAction.processMergeQueue]),
      projectPromotion: h.canExecute && all([TeamProjectAction.promote]),
      projectResume:
          h.canExecute &&
          all([TeamProjectAction.resumeProject, TeamProjectAction.resumeTask]),
      projectBudgets: all([TeamProjectAction.updateSettings]),
      projectDigest: all([TeamProjectAction.acknowledgeDigest]),
    );
  }

  Future<T> _track<T>(Future<T> Function() action) {
    if (isClosed) {
      return Future.error(const PhoneEngineException('engineClosed'));
    }
    final future = Future<T>.sync(action);
    final settled = future.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    _inflight.add(settled);
    unawaited(settled.then((_) => _inflight.remove(settled)));
    return future;
  }

  static String? _refusalCode(Object? value, {bool allowEmpty = false}) {
    if (value is! String ||
        !(allowEmpty && value.isEmpty ||
            RegExp(r'^[A-Za-z][A-Za-z0-9_]{0,63}$').hasMatch(value)) ||
        KitRedact.text(value) != value) {
      return null;
    }
    return value;
  }

  Future<Object?> _request(
    String method,
    String path, {
    Object? data,
    Map<String, Object?>? query,
  }) async {
    try {
      final transport = _closed
          ? Dio(
              BaseOptions(
                baseUrl: _baseUrl,
                followRedirects: false,
                maxRedirects: 0,
                connectTimeout: const Duration(seconds: 15),
                receiveTimeout: const Duration(seconds: 15),
                responseType: ResponseType.json,
                validateStatus: (_) => true,
                headers: {'Authorization': 'Bearer $_bearerToken'},
              ),
            )
          : _dio;
      late final Response<Object?> response;
      try {
        response = await transport.request<Object?>(
          path,
          data: data,
          queryParameters: query,
          options: Options(method: method),
        );
      } finally {
        if (!identical(transport, _dio)) transport.close(force: true);
      }
      final status = response.statusCode ?? 0;
      if (status < 200 || status >= 300) {
        final body = response.data;
        // Redirect bodies are not an engine refusal and never authorize
        // another endpoint. Every other authenticated refusal retains only
        // its bounded symbolic code, never server text or credential payloads.
        if (!(status >= 300 && status < 400) && body is Map) {
          final code = _refusalCode(body['code']);
          if (code != null) {
            if (method == 'POST' &&
                path == '/v1/commands' &&
                body['accepted'] == false &&
                body['projectId'] is String &&
                body['revision'] is int &&
                body['replayed'] is bool) {
              // Some adapters use a refusal HTTP status with the complete
              // command receipt. Preserve its reviewed metadata as well.
              return body;
            }
            throw PhoneEngineException(code);
          }
        }
        throw PhoneEngineException(
          status >= 300 && status < 400
              ? 'redirectRefused'
              : status == 401 || status == 403
              ? 'authenticationRequired'
              : 'engineUnavailable',
        );
      }
      return response.data;
    } on PhoneEngineException {
      rethrow;
    } catch (_) {
      // Ambiguous writes are never resubmitted, and no exception retains auth.
      throw PhoneEngineException(
        method == 'POST' ? 'transportUncertain' : 'engineUnavailable',
      );
    }
  }

  Future<PhoneEngineHealth> probe() => _track(() async {
    final h = PhoneEngineHealth.fromJson(
      await _request('GET', '/v1/health'),
      profileId,
    );
    _health = h;
    return h;
  });

  @override
  Future<TeamWorkspace> teamWorkspace() => _track(() async {
    _health ??= PhoneEngineHealth.fromJson(
      await _request('GET', '/v1/health'),
      profileId,
    );
    final raw = await _request('GET', '/v1/workspace');
    if (raw is! Map ||
        raw['schemaVersion'] is! int ||
        raw['schemaVersion'] != 1) {
      throw const PhoneEngineException('schemaUnsupported');
    }
    if (raw['revision'] is! int ||
        raw['simulated'] != false ||
        ['projects', 'servers', 'roles'].any((k) => raw[k] is! List)) {
      throw const PhoneEngineException('payloadInvalid');
    }
    try {
      final workspace = TeamWorkspace.fromJson(Map<String, dynamic>.from(raw));
      return workspace.copyWith(
        projects: List.unmodifiable(
          workspace.projects.map(
            (project) => _presentPhoneProject(
              project,
              commandActions: _health!.commandActions,
            ),
          ),
        ),
      );
    } catch (_) {
      throw const PhoneEngineException('payloadInvalid');
    }
  });

  @override
  Stream<TeamWorkspace> watchTeamWorkspace() {
    if (isClosed) {
      return Stream.error(const PhoneEngineException('engineClosed'));
    }
    _pollTimer ??= Timer.periodic(pollInterval, (_) => unawaited(_poll()));
    // No mutation/event replay: the authoritative snapshot is refetched.
    return _snapshots.stream;
  }

  Future<void> _poll() async {
    if (_polling || isClosed) return;
    _polling = true;
    try {
      final snapshot = await teamWorkspace();
      if (!isClosed) _snapshots.add(snapshot);
    } catch (_) {
      if (!isClosed) {
        _snapshots.addError(const PhoneEngineException('engineUnavailable'));
      }
    } finally {
      _polling = false;
    }
  }

  static const _executionActions = {
    TeamProjectAction.approvePlan,
    TeamProjectAction.retryPlan,
    TeamProjectAction.resumeProject,
    TeamProjectAction.resumeTask,
    TeamProjectAction.restartTask,
    TeamProjectAction.verifyTask,
    TeamProjectAction.fixFindings,
    TeamProjectAction.recheckTask,
    TeamProjectAction.processMergeQueue,
    TeamProjectAction.resolveConflict,
    TeamProjectAction.promote,
    TeamProjectAction.undoMerge,
    TeamProjectAction.moveTask,
  };
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand command) =>
      _track(() async {
        try {
          final h = await probe();
          if (!h.commandActions.contains(command.action)) {
            return const TeamCommandResult(
              accepted: false,
              code: 'unsupportedCommand',
            );
          }
          if (_executionActions.contains(command.action) && !h.canExecute) {
            return TeamCommandResult(
              accepted: false,
              code: !h.boundary
                  ? 'boundaryUnverified'
                  : !h.protocolVerified
                  ? 'protocolUnverified'
                  : 'engineUnavailable',
            );
          }
          Object? safe(Object? v) => switch (v) {
            String s => KitRedact.text(s),
            List items => items.map(safe).toList(),
            Map items => items.map((k, v) => MapEntry(k, safe(v))),
            _ => v,
          };
          await beforeCommand?.call();
          final raw = await _request(
            'POST',
            '/v1/commands',
            data: safe(command.toJson()),
          );
          if (raw is! Map ||
              raw['accepted'] is! bool ||
              raw['code'] is! String ||
              raw['projectId'] is! String ||
              raw['revision'] is! int ||
              raw['replayed'] is! bool) {
            throw const PhoneEngineException('payloadInvalid');
          }
          final code = raw['code'] as String;
          if (_refusalCode(code, allowEmpty: raw['accepted'] == true) == null) {
            throw const PhoneEngineException('payloadInvalid');
          }
          return TeamCommandResult(
            accepted: raw['accepted'] as bool,
            code: code,
            projectId: raw['projectId'] as String,
            revision: raw['revision'] as int,
            replayed: raw['replayed'] as bool,
          );
        } on PhoneEngineException catch (error) {
          return TeamCommandResult(
            accepted: false,
            code: error.code,
            projectId: command.projectId,
          );
        }
      });

  /// Observation lease, separate from executable project commands. Never retried.
  Future<void> sendChatBusy({
    required int until,
    required List<String> sessionIds,
    required List<String> directories,
    required bool known,
    required String appInstance,
    required int sequence,
  }) => _track(() async {
    final now = _now().millisecondsSinceEpoch;
    if (until <= now ||
        until > now + 30000 ||
        sequence < 0 ||
        !RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(appInstance) ||
        sessionIds.length > 256 ||
        directories.length > 256 ||
        [
          ...sessionIds,
          ...directories,
        ].any((s) => s.isEmpty || s.length > 4096)) {
      throw const PhoneEngineException('payloadInvalid');
    }
    final result = await _request(
      'POST',
      '/v1/chatBusy',
      data: {
        'until': until,
        'sessionIds': sessionIds.map(KitRedact.text).toList(),
        'directories': directories.map(KitRedact.text).toList(),
        'known': known,
        'appInstance': appInstance,
        'sequence': sequence,
      },
    );
    if (result is! Map || result['accepted'] != true) {
      throw const PhoneEngineException('chatAdmissionUnavailable');
    }
  });

  /// Metadata only; never forwards provider payloads, text or credentials.
  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) =>
      _track(() async {
        final raw = await _request(
          'GET',
          '/v1/events',
          query: {'after': afterSeq ?? 0, 'limit': limit.clamp(1, 100)},
        );
        if (raw is! List) throw const PhoneEngineException('payloadInvalid');
        final result = <ActivityEvent>[];
        for (final item in raw) {
          if (item is! Map || item['seq'] is! int || item['type'] is! String) {
            {
              throw const PhoneEngineException('payloadInvalid');
            }
          }
          result.add(
            ActivityEvent(
              seq: item['seq'] as int,
              type: KitRedact.text(item['type'] as String),
            ),
          );
        }
        return List.unmodifiable(result);
      });

  Future<void> _drain() async {
    while (_inflight.isNotEmpty) {
      await Future.wait(_inflight.toList());
    }
  }

  @override
  Future<void> close() => _closing ??= _close();
  Future<void> _close() async {
    _closed = true;
    _pollTimer?.cancel();
    try {
      await _drain();
      await _snapshots.close();
    } finally {
      _dio.close(force: true);
      final listeners = _closeListeners.toList();
      _closeListeners.clear();
      for (final listener in listeners) {
        listener();
      }
    }
  }

  @override
  Future<void> deleteLocalData() => _deletion ??= _delete();
  Future<void> _delete() async {
    beginDeletion();
    await _drain();
    PhoneEngineHealth.fromJson(await _request('GET', '/v1/health'), profileId);
    final result = await _request('DELETE', '/v1/profile');
    if (result is! Map || result['deleted'] != true) {
      {
        throw const PhoneEngineException('payloadInvalid');
      }
    }
    await close();
    _dio.close(force: true);
  }
}

// Translate phone wire states into the existing domain presentation vocabulary.
// The engine remains authoritative for revisions, approval and retry admission.
TeamProject _presentPhoneProject(
  TeamProject project, {
  required Set<TeamProjectAction> commandActions,
}) {
  final planning = project.planningState;
  var status = project.status;
  if (status == 'running' && _hasCompletedPhonePromotion(project)) {
    status = 'done';
  }
  if (status == 'needsPlanApproval') {
    status = 'plan';
  } else if (!project.planApproved &&
      planning != null &&
      const ['failed', 'interrupted'].contains(planning.stage) &&
      !const ['paused', 'pausedBudget', 'stopped'].contains(status) &&
      !_resumableInterruption(planning.reason) &&
      commandActions.contains(TeamProjectAction.retryPlan)) {
    // This editor affordance explicitly asks the person to start another plan.
    // The original checkpoint stays reviewable; reads never replay its prompt.
    status = 'planFailed';
  } else if (!project.planApproved &&
      (status == 'planning' || status == 'interrupted')) {
    status = switch (planning?.stage) {
      'starting' ||
      'planning' ||
      'preparing' ||
      'submitting' ||
      'running' ||
      'resuming' => 'running',
      'failed' => 'failed',
      'interrupted' => switch (planning?.reason) {
        'restartNeedsReconciliation' ||
        'pauseNeedsReconciliation' ||
        'serverRestarted'
            when commandActions.contains(TeamProjectAction.resumeProject) =>
          'paused',
        _ => 'failed',
      },
      'paused' => 'paused',
      'stopped' => 'stopped',
      _ => 'waiting',
    };
  } else if (status == 'interrupted' ||
      (status == 'running' &&
          project.planApproved &&
          project.tasks.any((task) => task.status == 'interrupted') &&
          !project.tasks.any(
            (task) => _activePhoneTaskStates.contains(task.status),
          ))) {
    final interrupted = project.tasks.where(
      (task) => task.status == 'interrupted',
    );
    status =
        interrupted.isNotEmpty &&
            interrupted.every(
              (task) => _resumableTaskInterruption(task.reason),
            ) &&
            commandActions.contains(TeamProjectAction.resumeProject)
        ? 'paused'
        : 'failed';
  }
  final summary =
      !project.planApproved &&
          planning != null &&
          const ['failed', 'interrupted'].contains(planning.stage)
      ? _planningCheckpointSummary(
          planning,
          canResume:
              project.status != 'stopped' &&
              _resumableInterruption(planning.reason) &&
              commandActions.contains(TeamProjectAction.resumeProject),
        )
      : null;
  return project.copyWith(
    status: status,
    tasks: List.unmodifiable(
      project.tasks.map((task) {
        final needsFix = task.status == 'needsFix';
        return task.copyWith(
          status: switch (task.status) {
            'checked' => 'verified',
            'needsFix' => 'review',
            'merging' || 'resuming' => 'running',
            'interrupted'
                when project.status != 'stopped' &&
                    _resumableTaskInterruption(task.reason) &&
                    commandActions.contains(TeamProjectAction.resumeTask) =>
              'paused',
            'interrupted' => 'review',
            _ => task.status,
          },
          reason: needsFix && task.reason.isEmpty
              ? 'checkerFindings'
              : task.reason,
          // A checker proposal cannot close its own findings. Legacy phone
          // checkpoints with findings remain blocked by native authority.
          findings: needsFix
              ? List.unmodifiable(
                  task.findings.map(
                    (finding) => finding.copyWith(status: 'open'),
                  ),
                )
              : task.findings,
        );
      }),
    ),
    requests: List.unmodifiable([
      ...project.requests,
      // A checker that finished but whose answer could not be read: the
      // person is asked to resume it (resumeTask re-reads that answer; it
      // does not ask the model again).
      if (project.status != 'stopped' &&
          commandActions.contains(TeamProjectAction.resumeTask))
        for (final task in project.tasks.where(
          (task) =>
              task.status == 'interrupted' &&
              task.reason == _checkerAnswerUnreadable,
        ))
          TeamRequest(
            id: 'phone-interrupted-${task.id}',
            kind: 'interrupted',
            title: 'The check finished, but its answer could not be read',
            taskId: task.id,
            createdAt: task.changedAt,
          ),
    ]),
    timeline: List.unmodifiable([
      ...project.timeline,
      if (summary != null)
        TeamTimelineEvent(
          id: 'phone-planning-checkpoint',
          kind: 'planningCheckpoint',
          actor: 'engine',
          at: planning?.updatedAt ?? '',
          text: summary,
        ),
      for (final task in project.tasks.where(
        (task) => task.status == 'interrupted',
      ))
        TeamTimelineEvent(
          id: 'phone-task-checkpoint-${task.id}',
          kind: 'taskCheckpoint',
          actor: 'engine',
          at: task.changedAt,
          taskId: task.id,
          text: _taskCheckpointSummary(
            task,
            canResume:
                project.status != 'stopped' &&
                _resumableTaskInterruption(task.reason) &&
                commandActions.contains(TeamProjectAction.resumeTask),
          ),
        ),
    ]),
  );
}

// Native checking maps to `review` in task snapshots. Treat it as possibly
// active so a failed parallel lane never hides a checker still doing work.
const _activePhoneTaskStates = {
  'starting',
  'resuming',
  'planning',
  'preparing',
  'submitting',
  'working',
  'checking',
  'review',
  'collecting',
  'merging',
  'promoting',
  'running',
};

/// The engine's reason for a checker that completed but whose final answer
/// could not be parsed. The saved session is complete, so Resume re-reads it.
const _checkerAnswerUnreadable = 'structuredOutputInvalid';

/// What a task can resume from: the restart/pause checkpoints, and a finished
/// checker whose answer could not be read (root-cause-4.md).
bool _resumableTaskInterruption(String reason) =>
    _resumableInterruption(reason) || reason == _checkerAnswerUnreadable;

bool _resumableInterruption(String reason) => const [
  'restartNeedsReconciliation',
  'pauseNeedsReconciliation',
  'serverRestarted',
].contains(reason);

// Exact static admission codes from the native scheduler. Dynamic native/model
// text is never substituted for these application-authored diagnostic codes.
const _phoneAdmissionReasons = {
  'jobNotQueued',
  'projectNotRunning',
  'serverOffline',
  'chatBusy',
  'chatStateUnknown',
  'chargingRequired',
  'chargingUnknown',
  'chooseExecutionMode',
  'serverCapUnknown',
  'laneCap',
  'invalidDependencies',
  'dependencyPending',
  'missingDependency',
  'chooseBudget',
  'totalUsageUnknown',
  'dailyUsageUnknown',
  'invalidBudget',
  'budgetReached',
  'tokenUsageUnknown',
  'taskTokenBudgetReached',
};

String _taskCheckpointSummary(TeamTask task, {required bool canResume}) {
  // These are application-authored codes. Never copy paths, model error text
  // or secrets into the diagnostic timeline. Resume only refetches a session.
  final reason = switch (task.reason) {
    'structuredOutputInvalid' => 'checkerAnswerUnreadable',
    'restartNeedsReconciliation' ||
    'pauseNeedsReconciliation' ||
    'serverRestarted' ||
    'restartResend' ||
    'recoveryNeedsReview' ||
    'sessionUnknown' ||
    'sessionFailed' ||
    'sessionUncertain' ||
    'promptUncertain' ||
    'sessionCreateUncertain' => task.reason,
    _ when _phoneAdmissionReasons.contains(task.reason) => task.reason,
    _ => '',
  };
  if (canResume) {
    return 'Work was interrupted. Resume to check its existing session ($reason).';
  }
  return reason.isEmpty
      ? 'Work was interrupted and needs review.'
      : 'Work was interrupted and needs review ($reason).';
}

String _planningCheckpointSummary(
  TeamPlanningState planning, {
  required bool canResume,
}) {
  final reason = switch (planning.reason) {
    'sessionFailed' => 'sessionFailed',
    'promptUncertain' => 'promptUncertain',
    'modelUnavailable' => 'modelUnavailable',
    'modelNotConfigured' => 'modelNotConfigured',
    'modelInvalid' => 'modelInvalid',
    'invalid_model' ||
    'invalid_role' ||
    'authentication_failed' ||
    'transport_unavailable' ||
    'cloneFailed' ||
    'sessionCreateUncertain' ||
    'permission_policy_unconfirmed' ||
    'sessionUnknown' ||
    'sessionUncertain' ||
    'needsAnswer' ||
    'structuredOutputInvalid' ||
    'invalidPlan' ||
    'planTaskTitleRequired' ||
    'planPhaseInvalid' ||
    'usageUncertain' ||
    'recoveryNeedsReview' => planning.reason,
    'restartNeedsReconciliation' => 'restartNeedsReconciliation',
    'pauseNeedsReconciliation' => 'pauseNeedsReconciliation',
    'serverRestarted' => 'serverRestarted',
    'restartResend' => 'restartResend',
    _ when _phoneAdmissionReasons.contains(planning.reason) => planning.reason,
    _ => '',
  };
  if (reason.isEmpty) {
    return 'Planning stopped and needs review.';
  }
  if (canResume && planning.stage == 'interrupted') {
    return 'Planning was interrupted. Resume to check its existing session ($reason).';
  }
  return planning.stage == 'failed'
      ? 'Planning failed ($reason).'
      : 'Planning needs review ($reason).';
}

// Presentation follows confirmed repository truth; no write or synthetic receipt.
bool _hasCompletedPhonePromotion(TeamProject project) {
  if (!project.planApproved ||
      project.tasks.isEmpty ||
      project.mergeQueue.isEmpty) {
    return false;
  }
  final tasks = <String, TeamTask>{};
  for (final task in project.tasks) {
    if (task.id.isEmpty ||
        tasks.containsKey(task.id) ||
        task.repoId.isEmpty ||
        task.status != 'merged' ||
        task.findings.any((finding) => finding.status == 'open')) {
      return false;
    }
    tasks[task.id] = task;
  }
  final covered = <String>{};
  final mergeIds = <String>{};
  for (final item in project.mergeQueue) {
    final task = tasks[item.taskId];
    if (item.id.isEmpty ||
        !mergeIds.add(item.id) ||
        !item.checksPassed ||
        item.status != 'merged' ||
        task == null ||
        item.repoId != task.repoId) {
      return false;
    }
    covered.add(task.id);
  }
  if (covered.length != tasks.length) return false;
  for (final repoId in tasks.values.map((task) => task.repoId).toSet()) {
    final repos = project.repos.where((repo) => repo.id == repoId).toList();
    if (repos.length != 1) return false;
    final repo = repos.single;
    if (repo.devCommit.isEmpty || repo.devCommit != repo.mainCommit) {
      return false;
    }
    if (!project.receipts.any(
      (receipt) =>
          receipt.id.isNotEmpty &&
          receipt.kind == 'promote' &&
          receipt.actor == 'engine' &&
          receipt.repoId == repoId &&
          receipt.before.isNotEmpty &&
          receipt.before != receipt.after &&
          receipt.after == repo.mainCommit,
    )) {
      return false;
    }
  }
  return true;
}
