import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/isolated_task_launch.dart';

const _project = WorkspaceProject(
  id: 'project-1',
  name: 'OpenCode Mobile',
  directory: '/work/app',
  worktrees: [],
  updatedAt: 1,
);

const _created = WorktreeInfo(
  name: 'wake-fix',
  directory: '/data/worktree/project-1/wake-fix',
);

EventEnvelope _ready(String directory, {String? project = 'project-1'}) =>
    EventEnvelope(
      type: 'worktree.ready',
      directory: directory,
      project: project,
      properties: const {'name': 'wake-fix', 'branch': 'opencode/wake-fix'},
    );

EventEnvelope _failed(String directory) => EventEnvelope(
  type: 'worktree.failed',
  directory: directory,
  project: 'project-1',
  properties: const {'message': 'setup script exited 1'},
);

class _Harness {
  _Harness({this.timeout = const Duration(seconds: 45)});

  final Duration timeout;
  final events = StreamController<EventEnvelope>.broadcast();
  final createCompleter = Completer<WorktreeInfo>();
  final openCalls = <String>[];
  Object? openError;
  Session? openResult;
  int createCalls = 0;

  late final launch = IsolatedTaskLaunch(
    project: _project,
    requestedName: 'wake-fix',
    events: events.stream,
    readinessTimeout: timeout,
    create: () {
      createCalls += 1;
      return createCompleter.future;
    },
    open: (directory) async {
      openCalls.add(directory);
      if (openError case final error?) throw error;
      return openResult ??
          Session(id: 'ses_new', directory: directory, projectID: 'project-1');
    },
  );

  Future<void> close() async {
    launch.dispose();
    await events.close();
  }
}

void main() {
  test('ready event received before the create response is retained', () async {
    final h = _Harness();
    addTearDown(h.close);
    final phases = <IsolatedTaskPhase>[];
    h.launch.addListener(() => phases.add(h.launch.phase));

    unawaited(h.launch.start());
    expect(h.launch.phase, IsolatedTaskPhase.creating);
    expect(h.createCalls, 1);

    // The server finished setup before its HTTP answer reached the phone.
    h.events.add(_ready('/data/worktree/project-1/wake-fix/'));
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.creating);

    h.createCompleter.complete(_created);
    await Future<void>.delayed(Duration.zero);

    expect(h.launch.phase, IsolatedTaskPhase.ready);
    expect(h.launch.worktree?.branch, 'opencode/wake-fix');
    expect(phases, contains(IsolatedTaskPhase.ready));
    expect(phases, isNot(contains(IsolatedTaskPhase.preparing)));
  });

  test('events for another project or directory are ignored', () async {
    final h = _Harness();
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.events.add(_ready('/data/worktree/project-1/wake-fix', project: 'p2'));
    h.events.add(_ready('/data/worktree/project-1/other'));
    h.createCompleter.complete(_created);
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.preparing);

    h.events.add(_ready('/data/worktree/project-1/other'));
    h.events.add(_failed('/data/worktree/project-1/other'));
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.preparing);

    h.events.add(_ready('/data/worktree/project-1/wake-fix'));
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.ready);
  });

  test(
    'failure event keeps the created worktree and reports the message',
    () async {
      final h = _Harness();
      addTearDown(h.close);
      unawaited(h.launch.start());
      h.createCompleter.complete(_created);
      await Future<void>.delayed(Duration.zero);
      h.events.add(_failed('/data/worktree/project-1/wake-fix'));
      await Future<void>.delayed(Duration.zero);

      expect(h.launch.phase, IsolatedTaskPhase.failed);
      expect(h.launch.message, 'setup script exited 1');
      expect(h.launch.worktree, isNotNull, reason: 'never dropped or deleted');
      expect(h.launch.canOpen, isFalse);
      expect(h.launch.isTerminal, isTrue);
    },
  );

  test('create failure surfaces the error with no worktree', () async {
    final h = _Harness();
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.createCompleter.completeError(const ProductException('nope'));
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.failed);
    expect(h.launch.failure, isA<ProductException>());
    expect(h.launch.worktree, isNull);
  });

  // Real timers with a short wait: the launch owns a plain [Timer], and the
  // widget test covers the same transitions under the test binding's clock.
  const shortWait = Duration(milliseconds: 40);
  Future<void> pastWait() => Future<void>.delayed(shortWait * 3);

  test('timeout becomes unconfirmed; open needs explicit acceptance', () async {
    final h = _Harness(timeout: shortWait);
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.createCompleter.complete(_created);
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.preparing);

    await pastWait();
    expect(h.launch.phase, IsolatedTaskPhase.unconfirmed);
    expect(h.launch.canOpen, isTrue);

    await h.launch.open();
    expect(h.openCalls, isEmpty, reason: 'implicit open is refused');
    expect(h.launch.phase, IsolatedTaskPhase.unconfirmed);

    h.launch.keepWaiting();
    expect(h.launch.phase, IsolatedTaskPhase.preparing);
    await pastWait();
    expect(h.launch.phase, IsolatedTaskPhase.unconfirmed);

    // A late ready still upgrades an unconfirmed worktree.
    h.events.add(_ready('/data/worktree/project-1/wake-fix'));
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.ready);

    await h.launch.open();
    expect(h.openCalls, ['/data/worktree/project-1/wake-fix']);
    expect(h.launch.phase, IsolatedTaskPhase.opened);
    expect(h.launch.session?.id, 'ses_new');
  });

  test('open anyway from unconfirmed records the choice', () async {
    final h = _Harness(timeout: shortWait);
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.createCompleter.complete(_created);
    await pastWait();
    expect(h.launch.phase, IsolatedTaskPhase.unconfirmed);

    await h.launch.open(acceptUnconfirmed: true);
    expect(h.openCalls, hasLength(1));
    expect(h.launch.phase, IsolatedTaskPhase.opened);
  });

  test('a failed open keeps the launch openable', () async {
    final h = _Harness();
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.createCompleter.complete(_created);
    await Future<void>.delayed(Duration.zero);
    h.events.add(_ready('/data/worktree/project-1/wake-fix'));
    await Future<void>.delayed(Duration.zero);

    h.openError = const ProductException('OpenCode did not switch');
    await h.launch.open();
    expect(h.launch.phase, IsolatedTaskPhase.ready);
    expect(h.launch.openError, isA<ProductException>());
    expect(h.launch.canOpen, isTrue);

    h.openError = null;
    await h.launch.open();
    expect(h.launch.phase, IsolatedTaskPhase.opened);
    expect(h.launch.openError, isNull);
  });

  test('cancel before the response keeps whatever the server made', () async {
    final h = _Harness();
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.launch.cancel();
    expect(h.launch.phase, IsolatedTaskPhase.cancelled);
    expect(
      h.launch.worktree,
      isNull,
      reason: 'unknown until the server answers',
    );

    // The request was already executing; its answer still lands.
    h.createCompleter.complete(_created);
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.cancelled);
    expect(h.launch.worktree?.directory, _created.directory);
    expect(h.launch.canOpen, isFalse);
    expect(h.openCalls, isEmpty);
  });

  test('cancel while preparing stops waiting without deleting', () async {
    final h = _Harness();
    addTearDown(h.close);
    unawaited(h.launch.start());
    h.createCompleter.complete(_created);
    await Future<void>.delayed(Duration.zero);
    h.launch.cancel();
    expect(h.launch.phase, IsolatedTaskPhase.cancelled);
    expect(h.launch.worktree, isNotNull);

    // Late events no longer move a cancelled launch.
    h.events.add(_ready('/data/worktree/project-1/wake-fix'));
    await Future<void>.delayed(Duration.zero);
    expect(h.launch.phase, IsolatedTaskPhase.cancelled);
    expect(h.launch.canCancel, isFalse);
  });

  test('start is idempotent and cancel is inert once terminal', () async {
    final h = _Harness();
    addTearDown(h.close);
    unawaited(h.launch.start());
    unawaited(h.launch.start());
    expect(h.createCalls, 1);
    h.createCompleter.completeError(StateError('down'));
    await Future<void>.delayed(Duration.zero);
    h.launch.cancel();
    expect(h.launch.phase, IsolatedTaskPhase.failed);
  });
}
