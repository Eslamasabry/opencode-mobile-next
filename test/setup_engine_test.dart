import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/setup_scripts.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxRuntime;

/// The native side, scripted: a status, the check output, and a job the
/// test moves along by setting [job].
class FakeLinux extends BuiltinLinux {
  FakeLinux({this.installed = true});

  bool installed;
  bool serverRunning = false;

  /// id → (passes, version) for the combined check run.
  Map<String, (bool, String?)> checks = {};
  final runs = <String>[];
  Completer<void>? holdChecks;

  Map<String, Object?>? job;
  final started = <Map<String, Object?>>[];
  var cancels = 0;
  final steps = <Map<String, Object?>>[];
  var statusReads = 0;

  /// What the next status reads do instead of answering: 'throw' fails the
  /// call, 'hang' never answers it (a reply lost on the way back).
  final statusFaults = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    serverRunning: serverRunning,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    runs.add(script);
    await holdChecks?.future;
    final out = StringBuffer();
    for (final match in RegExp(
      r"::oc-check-begin %s\\n' '([a-z0-9]+)'",
    ).allMatches(script)) {
      final id = match.group(1)!;
      final (ok, version) = checks[id] ?? (false, null);
      out
        ..writeln()
        ..writeln('::oc-check-begin $id')
        ..writeln(ok ? (version ?? '') : 'command not found')
        ..writeln()
        ..writeln('::oc-check-end $id ${ok ? 0 : 1}');
    }
    return BuiltinLinuxRunResult(exitCode: 0, output: out.toString());
  }

  @override
  Future<void> startSetup({
    required String jobId,
    required List<Map<String, Object?>> components,
    required Map<String, Map<String, String>> params,
    required Map<String, String> texts,
  }) async {
    started.add({
      'jobId': jobId,
      'components': components,
      'params': params,
      'texts': texts,
    });
    final now = DateTime.now().millisecondsSinceEpoch;
    job = {
      'jobId': jobId,
      'state': 'running',
      'current': null,
      'order': [for (final c in components) c['id']],
      'components': {
        for (final c in components)
          c['id'] as String: {
            'state': c['skipped'] == true ? 'skipped' : 'pending',
            'weight': c['weight'],
            'version': c['version'],
            if (c['data'] != null) 'data': c['data'],
          },
      },
      'startedAt': now,
      'updatedAt': now,
      'error': null,
      'logTail': '',
      'params': params,
    };
  }

  @override
  Future<String?> setupStatus() async {
    statusReads++;
    if (statusFaults.isNotEmpty) {
      switch (statusFaults.removeAt(0)) {
        case 'throw':
          throw StateError('the channel failed');
        case 'hang':
          return Completer<String?>().future;
      }
    }
    return job == null ? null : jsonEncode(job);
  }

  @override
  Future<void> cancelSetup() async {
    cancels++;
    if (job != null && job!['state'] == 'running') job!['state'] = 'cancelled';
  }

  @override
  Future<void> completeSetupStep({
    required String jobId,
    required String id,
    required bool ok,
    String? error,
    String? version,
  }) async {
    steps.add({'jobId': jobId, 'id': id, 'ok': ok, 'error': error});
    final component = (job!['components'] as Map)[id] as Map;
    component['state'] = ok ? 'done' : 'failed';
    component['error'] = error;
    job!['state'] = ok ? 'done' : 'failed';
    job!['error'] = error;
  }

  /// Moves the running job to [id] with [fields].
  void advance(String id, Map<String, Object?> fields) {
    job!['current'] = id;
    final components = job!['components'] as Map;
    for (final entry in components.entries) {
      final value = entry.value as Map;
      if (entry.key == id) {
        value
          ..['state'] = 'running'
          ..addAll(fields);
        break;
      }
      if (value['state'] == 'pending' || value['state'] == 'running') {
        value['state'] = 'done';
      }
    }
  }
}

AppLocalizations get en => lookupAppLocalizations(const Locale('en'));

Map<String, Object?> spec(FakeLinux linux, String id) =>
    (linux.started.last['components']! as List)
        .cast<Map<String, Object?>>()
        .firstWhere((c) => c['id'] == id);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late FakeLinux linux;
  late ChannelSetupEngine engine;
  final finishes = <SetupFinishRequest>[];
  String? finishError;

  setUp(() {
    finishes.clear();
    finishError = null;
    linux = FakeLinux();
    engine = ChannelSetupEngine(
      linux: linux,
      strings: () => en,
      pollInterval: const Duration(milliseconds: 5),
      finisher: (request) async {
        finishes.add(request);
        return finishError;
      },
    );
  });

  tearDown(() => engine.dispose());

  test(
    'startSetup receives retained download plus known extracted payload peak',
    () async {
      engine.dispose();
      engine = ChannelSetupEngine(
        linux: linux,
        strings: () => en,
        components: (_, _) => const [
          SetupComponent(
            id: 'fixture',
            title: 'Fixture',
            shortTitle: 'Fixture',
            checkScript: 'false',
            installScript: ':',
            downloadBytes: 120000000,
            installedBytes: 350000000,
          ),
        ],
        pollInterval: const Duration(milliseconds: 5),
      );
      await engine.run({'fixture'});
      expect(linux.started, hasLength(1));
      expect(spec(linux, 'fixture')['data'], {
        'requiredFreeBytes': '${120000000 + 350000000 + 64 * 1024 * 1024}',
      });
    },
  );

  group('resume rule', () {
    test('components whose checks pass are skipped with their version, '
        'the rest get the prelude and their install script', () async {
      linux.checks = {
        'linux': (true, '24.04.5'),
        'essentials': (true, '2.43.0'),
        'python': (true, '3.12.3'),
      };
      await engine.run({'python'});

      expect(linux.runs, hasLength(1), reason: 'one proot run for all checks');
      final ids = [
        for (final c in linux.started.single['components']! as List) c['id'],
      ];
      expect(ids, [
        'linux',
        'essentials',
        'python',
        'node',
        'opencode',
        'start',
      ]);
      for (final id in ['linux', 'essentials', 'python']) {
        expect(spec(linux, id)['skipped'], isTrue, reason: id);
      }
      expect(spec(linux, 'essentials')['version'], '2.43.0');
      expect(spec(linux, 'node')['script'], startsWith(setupPrelude));
      expect(spec(linux, 'node')['script'], contains('oc_download'));
      // The pinned native program, not npm (slice-builtin-opencode-pin).
      expect(
        spec(linux, 'opencode')['script'],
        withSetupPrelude(
          SetupScripts.openCodeNativeInstall(TermuxRuntime.openCode1),
        ),
      );
      expect(spec(linux, 'start')['step'], isTrue);
      expect(spec(linux, 'start')['data'], {
        'requiredFreeBytes': '300000000',
        'runtime': 'opencode1',
        'openCodeChanged': 'true',
      });

      final progress = engine.progress.value;
      expect(progress.state, SetupState.running);
      expect(progress.jobId, linux.started.single['jobId']);
      expect(
        progress.components.map((c) => c.state).take(3),
        everyElement(ComponentState.skipped),
      );
    });

    test('nothing is checked inside Ubuntu before it is installed; the '
        'Linux base is native with localised labels', () async {
      linux.installed = false;
      await engine.run(const {});
      expect(linux.runs, isEmpty);
      final base = spec(linux, 'linux');
      expect(base['native'], isTrue);
      expect(base['skipped'], isNull);
      expect(base['labels'], {
        'download': en.phoneSetupStageDownloadingLinux,
        'unpack': en.phoneSetupStageUnpackingLinux,
      });
      // Python is optional: not selected, not in the job.
      expect([
        for (final c in linux.started.single['components']! as List) c['id'],
      ], isNot(contains('python')));
    });

    test('the running state and the job list are published before run '
        'returns, while the checks still run', () async {
      linux.holdChecks = Completer<void>();
      final running = engine.run({'python'});
      await Future<void>.delayed(Duration.zero);
      await Future<void>.delayed(Duration.zero);
      final progress = engine.progress.value;
      expect(progress.state, SetupState.running);
      expect(progress.jobId, isNotNull);
      expect(progress.components.first.state, ComponentState.checking);
      expect(progress.components.last.id, 'start');
      expect(progress.components.last.state, ComponentState.pending);
      linux.holdChecks!.complete();
      await running;
    });

    test('an interrupted job continues with its own params unless new ones '
        'are given', () async {
      linux.job = {
        'jobId': 'old',
        'state': 'interrupted',
        'order': ['linux', 'opencode'],
        'components': {
          'linux': {'state': 'done'},
          'opencode': {'state': 'pending'},
        },
        'params': {
          'opencode': {'runtime': 'opencode2'},
        },
      };
      linux.checks = {'linux': (true, '24.04.5')};
      await engine.run(const {});
      expect(linux.started.single['params'], {
        'opencode': {'runtime': 'opencode2'},
      });
      expect(
        spec(linux, 'opencode')['script'],
        contains(OpenCodePins.v2Arm64.sha256),
      );
      expect(
        spec(linux, 'start')['data'],
        containsPair('runtime', 'opencode2'),
      );
      // The check asked about OpenCode 2, not 1.
      expect(linux.runs.single, contains('oc_bin=\$(command -v opencode2)'));

      linux.job!['state'] = 'failed';
      await engine.run(
        const {},
        params: const {
          'opencode': {'runtime': 'opencode1'},
        },
      );
      expect(linux.started.last['params'], {
        'opencode': {'runtime': 'opencode1'},
      });
    });

    // Open point 2: a notification tap after a cold start must know that a
    // job was the first setup, and only setup.json survives the process.
    test('a first setup says so in its params, and the flag survives a '
        'restart and a Continue', () async {
      await engine.run(const {}, params: SetupJobParams.firstSetup);
      expect(linux.started.single['params'], SetupJobParams.firstSetup);
      expect(engine.progress.value.firstSetup, isTrue);

      // The app is killed: a fresh engine reads only setup.json.
      linux.job!['state'] = 'interrupted';
      final restarted = ChannelSetupEngine(
        linux: linux,
        strings: () => en,
        pollInterval: const Duration(milliseconds: 5),
      );
      addTearDown(restarted.dispose);
      await restarted.restore();
      expect(restarted.progress.value.firstSetup, isTrue);

      // Continue from screen B passes no params and keeps the flag.
      await restarted.run(const {});
      expect(linux.started.last['params'], SetupJobParams.firstSetup);
      expect(restarted.progress.value.firstSetup, isTrue);
    });

    test('the first-setup flag is laid over a stopped job\'s params, never '
        'instead of them', () async {
      linux.job = {
        'jobId': 'old',
        'state': 'failed',
        'order': ['linux', 'opencode'],
        'components': {
          'linux': {'state': 'done'},
          'opencode': {'state': 'failed'},
        },
        'params': {
          'opencode': {'runtime': 'opencode2'},
        },
      };
      linux.checks = {'linux': (true, '24.04.5')};
      await engine.run(const {}, params: SetupJobParams.firstSetup);
      expect(linux.started.single['params'], {
        'opencode': {'runtime': 'opencode2'},
        ...SetupJobParams.firstSetup,
      });
    });

    test('jobs from the This phone card are not first setups', () async {
      await engine.run(
        {'opencode'},
        params: const {
          'opencode': {'runtime': 'opencode2'},
        },
      );
      expect(engine.progress.value.firstSetup, isFalse);
      await engine.restore();
      expect(engine.progress.value.firstSetup, isFalse);
    });

    test('Continue after a failed OpenCode install (build 2062: npm\'s '
        'wrapper in place, the start failed) replaces only OpenCode', () async {
      linux.job = {
        'jobId': 'old',
        'state': 'failed',
        'current': 'start',
        'order': ['linux', 'essentials', 'python', 'node', 'opencode', 'start'],
        'components': {
          for (final id in [
            'linux',
            'essentials',
            'python',
            'node',
            'opencode',
          ])
            id: {'state': 'done'},
          'start': {'state': 'failed', 'error': 'OpenCode did not answer'},
        },
        'params': SetupJobParams.firstSetup,
      };
      // Everything below OpenCode is in place; the npm wrapper still prints
      // the pinned version but is not the pinned program, so its check fails.
      linux.checks = {
        'linux': (true, '24.04.5'),
        'essentials': (true, '2.43.0'),
        'python': (true, '3.12.3'),
        'node': (true, '24.21.0'),
      };
      await engine.resume();

      final started = (linux.started.single['components']! as List)
          .cast<Map<String, Object?>>();
      expect(
        [for (final c in started) c['id']],
        ['linux', 'essentials', 'python', 'node', 'opencode', 'start'],
      );
      for (final id in ['linux', 'essentials', 'python', 'node']) {
        expect(spec(linux, id)['skipped'], isTrue, reason: id);
      }
      expect(spec(linux, 'opencode')['skipped'], isNot(isTrue));
      expect(
        spec(linux, 'opencode')['script'],
        contains(OpenCodePins.v1Arm64.sha256),
      );
      expect(spec(linux, 'start')['data'], {
        'requiredFreeBytes': '300000000',
        'runtime': 'opencode1',
        'openCodeChanged': 'true',
      });
      // The check that failed is the native one, not a version print.
      expect(
        linux.runs.single,
        contains('readlink -f'),
        reason: 'a wrapper that prints the right version is not enough',
      );
    });

    test('a finished job does not lend its params to the next run', () async {
      linux.job = {
        'jobId': 'old',
        'state': 'done',
        'order': ['linux'],
        'components': {
          'linux': {'state': 'done'},
        },
        'params': {
          'opencode': {'runtime': 'opencode2'},
        },
      };
      await engine.run(const {});
      expect(linux.started.single['params'], isEmpty);
    });

    test('a job already running is watched, not started again', () async {
      linux.job = {
        'jobId': 'live',
        'state': 'running',
        'current': 'node',
        'order': ['node'],
        'components': {
          'node': {'state': 'running', 'done': 5, 'total': 10},
        },
      };
      await engine.run(const {});
      expect(linux.started, isEmpty);
      expect(engine.progress.value.jobId, 'live');
      expect(engine.progress.value.components.single.bytesDone, 5);
    });
  });

  group('the start step', () {
    test('runs the finisher once and reports it done', () async {
      linux.checks = {
        for (final id in ['linux', 'essentials', 'node', 'opencode'])
          id: (true, '1'),
      };
      await engine.run(const {});
      expect(
        spec(linux, 'start')['data'],
        containsPair('openCodeChanged', 'false'),
      );
      linux.advance('start', {'stage': en.phoneSetupStageStarting});
      void listener() {}
      engine.progress.addListener(listener);
      await pumpUntil(() => linux.steps.isNotEmpty);
      await Future<void>.delayed(const Duration(milliseconds: 30));
      engine.progress.removeListener(listener);
      expect(finishes, hasLength(1));
      expect(finishes.single.runtime, TermuxRuntime.openCode1);
      expect(finishes.single.openCodeChanged, isFalse);
      expect(linux.steps.single, containsPair('ok', true));
      expect(engine.progress.value.state, SetupState.done);
      expect(engine.progress.value.overall, 1);
    });

    test('a failed start fails the job in plain words', () async {
      finishError = 'the server stopped';
      await engine.run(const {});
      linux.advance('start', {});
      await pumpUntil(() => engine.progress.value.state == SetupState.failed);
      final start = engine.progress.value.components.last;
      expect(start.error, en.phoneSetupErrorStart('the server stopped'));
      expect(engine.progress.value.error, start.error);
      expect(engine.progress.value.canContinue, isTrue);
    });

    test('the job keeps polling without listeners while it runs, and stops '
        'once it is over and nobody watches', () async {
      await engine.run(const {});
      await Future<void>.delayed(const Duration(milliseconds: 40));
      final whileRunning = linux.statusReads;
      await Future<void>.delayed(const Duration(milliseconds: 40));
      expect(linux.statusReads, greaterThan(whileRunning));
      linux.job!['state'] = 'done';
      await Future<void>.delayed(const Duration(milliseconds: 40));
      final after = linux.statusReads;
      await Future<void>.delayed(const Duration(milliseconds: 40));
      expect(linux.statusReads, after);
    });
    test('polling survives a status read that fails or never answers '
        '(Android 15 run, 2026-09-24: the screen froze on the Linux base '
        'while the job finished)', () async {
      final watching = ChannelSetupEngine(
        linux: linux,
        strings: () => en,
        pollInterval: const Duration(milliseconds: 5),
        readTimeout: const Duration(milliseconds: 20),
        finisher: (request) async => null,
      );
      addTearDown(watching.dispose);
      await watching.run(const {});
      void listener() {}
      watching.progress.addListener(listener);
      addTearDown(() => watching.progress.removeListener(listener));
      linux.statusFaults.addAll(['throw', 'hang']);
      linux.advance('start', {'stage': en.phoneSetupStageStarting});
      await pumpUntil(() => watching.progress.value.state == SetupState.done);
      expect(linux.statusFaults, isEmpty);
      expect(linux.steps.single, containsPair('ok', true));
    });
  });

  group('cancel', () {
    test('stops the native job; finished components stay', () async {
      linux.installed = false;
      await engine.run(const {});
      linux.advance('node', {'stage': 'Downloading Node.js', 'done': 1});
      await engine.cancel();
      expect(linux.cancels, 1);
      final progress = engine.progress.value;
      expect(progress.state, SetupState.cancelled);
      expect(progress.canContinue, isTrue);
      expect(progress.components.first.state, ComponentState.done);
    });

    test(
      'while still checking, nothing is handed to the native side',
      () async {
        linux.holdChecks = Completer<void>();
        final running = engine.run(const {});
        await Future<void>.delayed(Duration.zero);
        await engine.cancel();
        linux.holdChecks!.complete();
        await running;
        expect(linux.started, isEmpty);
        expect(linux.cancels, 0);
        expect(engine.progress.value.state, SetupState.cancelled);
      },
    );
  });

  test('restore reads an interrupted job after a restart', () async {
    linux.job = jsonDecode(recordedInterrupted) as Map<String, Object?>;
    await engine.restore();
    final progress = engine.progress.value;
    expect(progress.jobId, 'setup-1790196784299510');
    expect(progress.state, SetupState.interrupted);
    expect(progress.canContinue, isTrue);
    expect(progress.components.map((c) => c.id), [
      'linux',
      'essentials',
      'python',
      'node',
      'opencode',
      'start',
    ]);
    final node = progress.components[3];
    expect(node.state, ComponentState.pending);
    expect(node.bytesDone, 23134208);
    expect(node.bytesTotal, 58088022);
    expect(progress.components[1].version, '2.43.0');
    expect(progress.logTail, contains('Downloading Node.js'));
  });

  test('installedOptional runs only the optional checks', () async {
    linux.checks = {'python': (true, '3.12.3')};
    expect(await engine.installedOptional(), {'python'});
    expect(linux.runs.single, contains("'python'"));
    expect(linux.runs.single, isNot(contains("'node'")));
    linux.installed = false;
    expect(await engine.installedOptional(), isEmpty);
  });

  group('selection', () {
    SetupComponent c(
      String id, {
      List<String> deps = const [],
      bool req = false,
    }) => SetupComponent(
      id: id,
      title: id,
      shortTitle: id,
      checkScript: '',
      installScript: '',
      dependsOn: deps,
      required: req,
    );

    test('required and selected components, their dependencies, in '
        'dependency order even when the registry is not', () {
      final registry = [
        c('tool', deps: ['node']),
        c('opencode', deps: ['node'], req: true),
        c('node', deps: ['base']),
        c('base', req: true),
        c('extra'),
      ];
      expect(expandSelection(registry, {'tool'}).map((x) => x.id), [
        'base',
        'node',
        'tool',
        'opencode',
      ]);
      expect(expandSelection(registry, {}).map((x) => x.id), [
        'base',
        'node',
        'opencode',
      ]);
    });

    test('a dependency cycle is an error, not a hang', () {
      expect(
        () => expandSelection([
          c('a', deps: ['b'], req: true),
          c('b', deps: ['a']),
        ], {}),
        throwsStateError,
      );
    });

    test('the real registry: every dependency exists and comes first', () {
      final registry = setupComponents(en);
      final seen = <String>{};
      for (final component in registry) {
        for (final dependency in component.dependsOn) {
          expect(seen, contains(dependency), reason: component.id);
        }
        seen.add(component.id);
      }
      expect(registry.where((c) => c.jobStep).map((c) => c.id), ['start']);
      // The start follows everything installed inside Linux; only what the
      // app installs into its own storage (voice typing) may come after it.
      final start = registry.indexWhere((c) => c.id == 'start');
      expect(registry.skip(start + 1).every((c) => c.app != null), isTrue);
    });
  });

  group('weights and ETA', () {
    SetupComponent w(String id, int seconds) => SetupComponent(
      id: id,
      title: id,
      shortTitle: id,
      checkScript: '',
      installScript: '',
      estimatedSeconds: seconds,
    );
    final components = [w('a', 10), w('b', 30), w('c', 60)];

    test('done counts whole, bytes and percent count real, a bare stage '
        'counts half', () {
      double overall(List<ComponentProgress> list) =>
          overallFraction(components, list);
      const done = ComponentProgress(id: 'a', state: ComponentState.done);
      expect(
        overall([
          done,
          const ComponentProgress(
            id: 'b',
            state: ComponentState.running,
            bytesDone: 15,
            bytesTotal: 30,
          ),
          const ComponentProgress(id: 'c', state: ComponentState.pending),
        ]),
        closeTo((10 + 15) / 100, 1e-9),
      );
      expect(
        overall([
          done,
          const ComponentProgress(
            id: 'b',
            state: ComponentState.running,
            percent: 20,
          ),
          const ComponentProgress(id: 'c', state: ComponentState.pending),
        ]),
        closeTo((10 + 6) / 100, 1e-9),
      );
      expect(
        overall([
          done,
          const ComponentProgress(
            id: 'b',
            state: ComponentState.running,
            stage: 'Installing',
          ),
          const ComponentProgress(id: 'c', state: ComponentState.pending),
        ]),
        closeTo((10 + 15) / 100, 1e-9),
      );
    });

    test('a new stage never moves the bar backwards', () {
      final floors = <String, double>{};
      final downloading = overallFraction(components, [
        const ComponentProgress(id: 'a', state: ComponentState.done),
        const ComponentProgress(
          id: 'b',
          state: ComponentState.running,
          bytesDone: 27,
          bytesTotal: 30,
        ),
        const ComponentProgress(id: 'c', state: ComponentState.pending),
      ], floors: floors);
      final unpacking = overallFraction(components, [
        const ComponentProgress(id: 'a', state: ComponentState.done),
        const ComponentProgress(
          id: 'b',
          state: ComponentState.running,
          stage: 'Unpacking',
        ),
        const ComponentProgress(id: 'c', state: ComponentState.pending),
      ], floors: floors);
      expect(unpacking, downloading);
    });

    test('no ETA before 10 s of real progress; then the remaining estimates '
        'scaled by the measured pace', () {
      final progress = [
        const ComponentProgress(id: 'a', state: ComponentState.skipped),
        const ComponentProgress(id: 'b', state: ComponentState.done),
        const ComponentProgress(id: 'c', state: ComponentState.pending),
      ];
      // a was there before (not this job's pace); b took 60 s for 30 s of
      // estimate, so the job runs at half speed: c's 60 s become 120 s.
      expect(
        estimateEta(
          components: components,
          progress: progress,
          overall: .4,
          elapsed: const Duration(seconds: 9),
        ),
        isNull,
      );
      expect(
        estimateEta(
          components: components,
          progress: progress,
          overall: .4,
          elapsed: const Duration(seconds: 60),
        ),
        120,
      );
      // Nothing done by this job yet: no pace, no ETA.
      expect(
        estimateEta(
          components: components,
          progress: progress,
          overall: .1,
          elapsed: const Duration(seconds: 60),
        ),
        isNull,
      );
    });
  });

  group('setup.json', () {
    test('the recorded interrupted job parses completely', () {
      final record = SetupJobRecord.parse(recordedInterrupted)!;
      expect(record.state, 'interrupted');
      expect(record.canContinue, isTrue);
      expect(record.current, 'node');
      expect(record.components.map((c) => c.id).toList(), [
        'linux',
        'essentials',
        'python',
        'node',
        'opencode',
        'start',
      ]);
      final linux = record.component('linux')!;
      expect(linux.componentState, ComponentState.done);
      expect(linux.done, 29564928);
      expect(linux.weight, 25);
      expect(linux.endedAt! - linux.startedAt!, 8451);
      expect(record.component('essentials')!.percent, 99);
      expect(record.component('start')!.data, {
        'openCodeChanged': 'true',
        'runtime': 'opencode1',
      });
    });

    test('keys keep the written order without "order"; unknown fields and '
        'states are tolerated; garbage is no job', () {
      final record = SetupJobRecord.parse(
        jsonEncode({
          'jobId': 'j',
          'state': 'something-new',
          'surprise': [1, 2],
          'components': {
            'b': {'state': 'running', 'done': 1.0, 'extra': true},
            'a': {'state': 'weird'},
          },
        }),
      )!;
      expect(record.components.map((c) => c.id), ['b', 'a']);
      expect(record.components.first.done, 1);
      expect(record.components.last.componentState, ComponentState.pending);
      final progress = progressFromRecord(
        record,
        const [],
        en,
        now: DateTime.now(),
      );
      expect(progress.state, SetupState.interrupted);
      expect(SetupJobRecord.parse(null), isNull);
      expect(SetupJobRecord.parse(''), isNull);
      expect(SetupJobRecord.parse('{not json'), isNull);
      expect(SetupJobRecord.parse('[]'), isNull);
      expect(SetupJobRecord.parse('{"state":"running"}'), isNull);
    });
  });

  group('errors in plain words', () {
    SetupJobComponent failed(String error) =>
        SetupJobComponent(id: 'node', state: 'failed', error: error);

    test('a download without internet names the part and the cause', () {
      for (final tail in [
        'curl: (6) Could not resolve host: nodejs.org\n'
            '[oc] Download failed (curl exit 6): https://nodejs.org/x',
        'Err:1 http://archive.ubuntu.com noble InRelease\n'
            "  Temporary failure resolving 'archive.ubuntu.com'",
        'java.net.UnknownHostException: Unable to resolve host '
            '"cdimage.ubuntu.com": No address associated with hostname',
      ]) {
        final text = describeSetupFailure(
          failed(tail.split('\n').last),
          tail,
          'Node.js',
          en,
        );
        expect(
          text.component,
          'Could not download Node.js: no internet connection',
        );
        expect(text.job, en.phoneSetupErrorNoInternet);
      }
    });

    test('checksum, full storage and anything else', () {
      expect(
        describeSetupFailure(
          failed(
            '[oc] The download did not match its checksum and was deleted',
          ),
          '',
          'Node.js',
          en,
        ).component,
        en.phoneSetupErrorChecksum('Node.js'),
      );
      expect(
        describeSetupFailure(
          failed('tar: node/bin/node: Cannot write: No space left on device'),
          '',
          'Node.js',
          en,
        ).component,
        en.phoneSetupErrorNoSpace('Node.js'),
      );
      expect(
        describeSetupFailure(
          failed('exit 1'),
          'npm ERR! code E403',
          'OpenCode',
          en,
        ).component,
        'Could not install OpenCode',
      );
    });

    test('a failed job maps to failed progress with both errors', () {
      final record = SetupJobRecord.parse(
        jsonEncode({
          'jobId': 'j',
          'state': 'failed',
          'current': 'node',
          'error': 'exit 6',
          'logTail': 'curl: (6) Could not resolve host: nodejs.org\n',
          'order': ['node'],
          'components': {
            'node': {'state': 'failed', 'error': 'exit 6'},
          },
        }),
      )!;
      final progress = progressFromRecord(
        record,
        setupComponents(en).where((c) => c.id == 'node').toList(),
        en,
        now: DateTime.now(),
      );
      expect(progress.state, SetupState.failed);
      expect(progress.error, en.phoneSetupErrorNoInternet);
      expect(
        progress.components.single.error,
        'Could not download Node.js: no internet connection',
      );
      expect(progress.logTail, contains('Could not resolve host'));
    });

    test('OCTRACE timing lines never reach the job log people see; the log '
        'ends with the real error', () {
      final record = SetupJobRecord.parse(
        jsonEncode({
          'jobId': 'j',
          'state': 'failed',
          'current': 'opencode',
          'error': OpenCodeInstallFailure.noStart,
          'logTail': [
            '==> Checking that OpenCode starts',
            '[oc] What OpenCode said:',
            '  Error: Failed to start server',
            OpenCodeInstallFailure.noStart,
            '[2026-09-28 10:00:00 UTC] timing · OCTRACE',
            'OCTRACE 2.9ms linux.setupStatus',
          ].join('\n'),
          'order': ['opencode'],
          'components': {
            'opencode': {
              'state': 'failed',
              'error': OpenCodeInstallFailure.noStart,
            },
          },
        }),
      )!;
      final progress = progressFromRecord(
        record,
        setupComponents(en).where((c) => c.id == 'opencode').toList(),
        en,
        now: DateTime.now(),
      );
      expect(progress.logTail, isNot(contains('OCTRACE')));
      expect(
        progress.logTail.trimRight().split('\n').last,
        OpenCodeInstallFailure.noStart,
      );
      expect(progress.error, en.phoneSetupErrorOpenCodeNoStart);
      expect(
        progress.components.single.error,
        en.phoneSetupErrorOpenCodeNoStart,
      );
      expect(progress.error, isNot(contains('[oc]')));
    });
  });

  group('combined checks', () {
    test('parse passing, failing and missing checks with versions', () {
      final results = parseCombinedChecks(
        '\n::oc-check-begin a\nnoise\n1.2.3\n\n::oc-check-end a 0\n'
        '\n::oc-check-begin b\n::oc-check-end b 1\n'
        '\n::oc-check-begin c\npartial output',
      );
      expect(results['a'], (ok: true, version: '1.2.3'));
      expect(results['b'], (ok: false, version: null));
      expect(results.containsKey('c'), isFalse);
    });
  });

  group('trace', () {
    setUp(PerfTrace.resetForTesting);

    test('each finished component is a span timed by the runner\'s own '
        'clock, recorded once however often it is read', () async {
      linux.job = jsonDecode(recordedInterrupted) as Map<String, Object?>;
      await engine.restore();
      await engine.restore();

      final components = PerfTrace.spans
          .where((span) => span.name == 'setup.component')
          .toList();
      expect(
        {for (final s in components) s.attrs['id']: s.durationMs},
        {'linux': 8451, 'essentials': 28963, 'python': 16683},
      );
      final job = PerfTrace.spans.singleWhere((s) => s.name == 'setup.job');
      expect(job.durationMs, 55783);
      expect(job.attrs['state'], 'interrupted');
      expect(job.failed, isTrue);
    });

    test('the checks are one span', () async {
      await engine.run({'python'});
      final check = PerfTrace.spans.singleWhere((s) => s.name == 'setup.check');
      expect(check.attrs['components'], '6');
    });
  });
}

Future<void> pumpUntil(bool Function() done) async {
  for (var i = 0; i < 400 && !done(); i++) {
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
  expect(done(), isTrue, reason: 'condition not reached');
}

/// setup.json as the emulator wrote it on 2026-09-24 after the app was
/// force-stopped while Node downloaded, then read back by a new process
/// (running → interrupted, node back to pending with its bytes kept).
const recordedInterrupted = r'''
{"jobId":"setup-1790196784299510","state":"interrupted","current":"node",
"order":["linux","essentials","python","node","opencode","start"],
"components":{
"linux":{"state":"done","weight":25,"stage":"Unpacking Linux base","done":29564928,"total":30028293,"percent":null,"version":"24.04.5","error":null,"startedAt":1790196784379,"endedAt":1790196792830},
"essentials":{"state":"done","weight":60,"stage":"Installing packages","done":null,"total":null,"percent":99,"version":"2.43.0","error":null,"startedAt":1790196792830,"endedAt":1790196821793},
"python":{"state":"done","weight":60,"stage":"Installing packages","done":null,"total":null,"percent":98,"version":"3.12.3","error":null,"startedAt":1790196821793,"endedAt":1790196838476},
"node":{"state":"pending","weight":30,"stage":"Downloading Node.js 24.21.0","done":23134208,"total":58088022,"percent":null,"version":null,"error":null,"startedAt":1790196838476,"endedAt":null},
"opencode":{"state":"pending","weight":240,"stage":null,"done":null,"total":null,"percent":null,"version":null,"error":null,"startedAt":null,"endedAt":null},
"start":{"state":"pending","weight":20,"stage":null,"done":null,"total":null,"percent":null,"version":null,"error":null,"startedAt":null,"endedAt":null,"data":{"openCodeChanged":"true","runtime":"opencode1"}}},
"overall":0.33,"startedAt":1790196784379,"updatedAt":1790196840162,"error":null,
"logTail":"Setting up python3-pip (24.0+dfsg-1ubuntu1.3) ...\nProcessing triggers for libc-bin (2.39-0ubuntu8.8) ...\n==> Downloading Node.js 24.21.0\n",
"params":{}}
''';
