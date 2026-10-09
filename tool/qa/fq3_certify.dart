import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ffi';

import 'fq3/common.dart';
import 'fq3/device.dart';
import 'fq3/evidence.dart';
import 'fq3/history.dart';
import 'fq3/history_manifest.dart';
import 'fq3/oc1.dart';
import 'fq3/oc2.dart';
import 'fq3/session_ownership.dart';

const evidenceDirectory = 'docs/qa/FQ3e-2026-10-09';
bool inheritedReservation = false;
typedef NativeFlock = Int32 Function(Int32, Int32);
typedef HostFlock = int Function(int, int);

void adoptReservation(String value) {
  final fd = int.tryParse(value);
  if (fd == null ||
      fd < 3 ||
      File('/proc/self/fd/$fd').resolveSymbolicLinksSync() !=
          File(lock).resolveSymbolicLinksSync()) {
    throw const ProbeFailure('invalid_inherited_lock');
  }
  final flock = DynamicLibrary.open(
    'libc.so.6',
  ).lookupFunction<NativeFlock, HostFlock>('flock');
  if (flock(fd, 2 | 4) != 0) {
    throw const ProbeFailure('invalid_inherited_lock');
  }
  inheritedReservation = true;
}

const lock = '/home/eslam/Storage/tmp/oc-emulator.lock';
const phases = <String, Set<String>>{
  'stream': {'stream'},
  'reconnect': {'reconnect'},
  'model': {'modelSwitch'},
  'abort': {'abort'},
  'allow': {'permissionAllow'},
  'deny': {'permissionDeny'},
  'image': {'image'},
  'cards': {'cards'},
};
String? argument(List<String> args, String flag) {
  final index = args.indexOf(flag);
  return index >= 0 && index + 1 < args.length ? args[index + 1] : null;
}

Map<String, dynamic> map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : {};
List<Map<String, dynamic>> list(Object? value) {
  final data = value is Map ? value['data'] : value;
  return data is List ? data.whereType<Map>().map(map).toList() : [];
}

Map<String, Object?> fail(String code) => {
  'state': 'fail',
  'code': code,
  'facts': <String, Object?>{},
};

Future<void> main(List<String> args) async {
  final inherited = argument(args, '--inherited-emulator-lock-fd');
  if (inherited != null) adoptReservation(inherited);
  final runID =
      argument(args, '--run-id') ??
      'fq3-${DateTime.now().toUtc().toIso8601String().replaceAll(RegExp(r'[^0-9]'), '')}';
  if (!RegExp(r'^fq3-[A-Za-z0-9_-]{1,80}$').hasMatch(runID)) {
    stderr.writeln('Invalid run ID');
    exitCode = 2;
    return;
  }
  Directory(evidenceDirectory).createSync(recursive: true);
  if (args.contains('--phase')) {
    await phase(args, runID);
  } else if (args.contains('--histories')) {
    await histories(args, runID);
  } else if (args.contains('--cleanup-only')) {
    await cleanupOwnedSessions(
      runID,
      includeArchived: args.contains('--archived'),
      allOwned: args.contains('--all-owned'),
    );
  } else {
    await orchestrate(args, runID);
  }
}

Future<void> phase(List<String> args, String runID) async {
  final engine = argument(args, '--engine');
  final caseName = argument(args, '--case');
  if (engine == null ||
      caseName == null ||
      !['opencode', 'opencode2'].contains(engine) ||
      !phases.containsKey(caseName)) {
    throw const ProbeFailure('invalid_phase');
  }
  final revision =
      argument(args, '--revision') ??
      (await Process.run('git', [
        'rev-parse',
        'HEAD',
      ])).stdout.toString().trim();
  final attempt =
      argument(args, '--attempt') ??
      'attempt-${DateTime.now().microsecondsSinceEpoch}-$pid';
  PhoneRuntime? runtime;
  Fq3Wire? wire;
  ProbeRun? run;
  String? cliVersion;
  var preflightCode = 'runtime_unavailable';
  String? cleanupCode;
  SessionOwnership? ownership;
  String? serverKind;
  final requestedModel = argument(args, '--model');
  final testedModel = requestedModel == null
      ? 'server-default'
      : isPublicModelReference(requestedModel)
      ? requestedModel
      : null;
  try {
    if (testedModel == null) throw const ProbeFailure('phase_model_invalid');
    await PhoneRuntime.restoreNormalApp();
    runtime = await PhoneRuntime.inspect(runID);
    ownership = SessionOwnership.create(
      File(
        '$evidenceDirectory/${SessionOwnership.filename(runID, engine, caseName, attempt)}',
      ),
      runID: runID,
      sourceRevision: revision,
      attemptID: attempt,
      engine: engine,
      caseName: caseName,
      appUID: runtime.uid,
      appBuild: runtime.appBuild,
    );
    cliVersion = await runtime.version(engine == 'opencode2');
    final endpoint = await runtime.start(
      engine == 'opencode2',
      appManagedOnly: args.contains('--app-managed-only'),
      forceOwned: args.contains('--force-owned'),
    );
    serverKind = runtime.lastServerKind;
    wire = Fq3Wire(baseUrl: endpoint, password: runtime.password);
    final options = ProbeOptions(
      directory: runtime.directory,
      title: '$runID-$engine-$caseName',
      model: requestedModel,
      capabilities: phases[caseName],
      onSessionCreated: ownership.recordCreated,
    );
    run = engine == 'opencode2'
        ? await runProtocol2(wire, options)
        : await runProtocol1(wire, options);
    final expected = engine == 'opencode2' ? '2.0.10' : '1.18.32';
    if (cliVersion != expected || run.observedVersion != expected) {
      run.results['version'] = fail('runtime_version_mismatch');
    }
  } on ProbeFailure catch (error) {
    preflightCode = error.code;
  } catch (_) {
    preflightCode = 'phase_failed';
  } finally {
    if (wire != null && run != null) {
      for (final id in run.sessionIDs) {
        try {
          await wire
              .request(
                'POST',
                engine == 'opencode2'
                    ? '/api/session/$id/interrupt'
                    : '/session/$id/abort',
              )
              .timeout(const Duration(seconds: 5));
        } catch (_) {
          /* Only owned sessions; server teardown is the final fence. */
        }
      }
    }
    if (wire != null &&
        ownership != null &&
        !args.contains('--retain-history')) {
      try {
        await cleanupLedger(wire, ownership);
      } catch (_) {
        cleanupCode = 'owned_session_cleanup_failed';
      }
    }
    try {
      try {
        await wire?.close();
      } finally {
        await runtime?.close();
      }
    } on ProbeFailure catch (error) {
      cleanupCode = error.code;
    } catch (_) {
      cleanupCode = 'cleanup_failed';
    }
    try {
      await PhoneRuntime.restoreNormalApp();
    } catch (_) {
      cleanupCode = 'app_restore_failed';
    }
    if (!args.contains('--retain-history') &&
        !await cleanupOwnedSessions(runID)) {
      cleanupCode = 'owned_session_cleanup_failed';
    }
  }
  final output = <String, Object?>{
    'runID': runID,
    'sourceRevision': revision,
    'attemptID': attempt,
    'cleanupCode': cleanupCode,
    'engine': engine,
    'case': caseName,
    'cliVersion': cliVersion,
    'observedVersion': run?.observedVersion,
    'appUID': runtime?.uid,
    'appBuild': runtime?.appBuild,
    'serverKind': serverKind,
    'testedModel': testedModel,
    'results':
        run?.results ??
        {
          for (final key in {...capabilityKeys.take(3), ...phases[caseName]!})
            key: fail(preflightCode),
        },
    'ownedSessions': run?.sessionIDs ?? [],
    'observations': run?.observations ?? {},
  };
  File('$evidenceDirectory/$runID-$engine-$caseName.json').writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(output)}\n',
  );
  stdout.writeln('$engine $caseName: evidence saved');
  if (cleanupCode != null) exitCode = 1;
}

Future<int> lockedChild(List<String> arguments) async {
  if (inheritedReservation) {
    final previous = exitCode;
    exitCode = 0;
    try {
      await main(arguments);
      return exitCode;
    } finally {
      exitCode = previous;
    }
  }
  // Waiting holds no lock. No timeout can silently skip a shared-device phase.
  final child = await Process.start('flock', [
    lock,
    Platform.resolvedExecutable,
    Platform.script.toFilePath(),
    ...arguments,
  ]);
  final output = child.stdout.transform(utf8.decoder).forEach(stdout.write);
  final errors = child.stderr.drain<void>();
  final code = await child.exitCode;
  await output;
  await errors;
  return code;
}

Future<void> orchestrate(List<String> args, String runID) async {
  var cleaned = false;
  try {
    // Recovery drains prior attempts of this run before any new sessions exist.
    final recovered = await lockedChild([
      '--cleanup-only',
      '--run-id',
      runID,
      '--all-owned',
    ]);
    if (recovered != 0) {
      throw const ProbeFailure('owned_session_cleanup_failed');
    }
    await _orchestrate(args, runID);
  } finally {
    try {
      cleaned = await lockedChild(['--cleanup-only', '--run-id', runID]) == 0;
    } catch (_) {
      cleaned = false;
    }
    if (!cleaned) {
      stderr.writeln('FQ3 cleanup failed; durable ownership intent retained');
      exitCode = 1;
    }
  }
  final evidence = '$evidenceDirectory/$runID.json';
  final report = reportAfterCleanup(
    map(jsonDecode(File(evidence).readAsStringSync())),
    cleanupCompleted: cleaned,
  );
  final staged = File('$evidence.pending')
    ..writeAsStringSync(
      '${const JsonEncoder.withIndent('  ').convert(report)}\n',
      flush: true,
    );
  staged.renameSync(evidence);
  if (args.contains('--no-matrix')) return;
  final generated = await Process.run('python3', [
    'tool/qa/fq3/update_matrix.py',
    '--run',
    evidence,
  ]);
  if (generated.exitCode != 0) {
    stdout.writeln('Matrix generation rejected evidence; report retained');
    exitCode = 1;
  } else {
    stdout.writeln(
      'Generated protocol certification matrix after final cleanup',
    );
  }
}

/// Exact-directory/title recovery closes the gap before an ID callback commits.
Future<void> cleanupLedger(Fq3Wire wire, SessionOwnership ownership) async {
  final oc2 = ownership.engine == 'opencode2';
  final prefix = oc2 ? '/api' : '';
  final query = oc2
      ? {'location[directory]': ownership.directory}
      : {'directory': ownership.directory};
  await wire.request('GET', oc2 ? '/api/health' : '/global/health');
  if (!ownership.legacy) {
    final rawCandidates = await wire.request(
      'GET',
      '$prefix/session',
      query: {'directory': ownership.directory, 'limit': '200'},
    );
    if (rawCandidates is! List &&
        !(rawCandidates is Map && rawCandidates['data'] is List)) {
      throw const ProbeFailure('ownership_discovery_invalid');
    }
    final items = rawCandidates is List
        ? rawCandidates
        : (rawCandidates as Map)['data'] as List;
    if (items.any(
      (item) =>
          item is! Map ||
          !ownedSessionID(item['id']) ||
          item['title'] is! String,
    )) {
      throw const ProbeFailure('ownership_discovery_invalid');
    }
    final candidates = list(rawCandidates);
    if (items.length >= 200) {
      throw const ProbeFailure('ownership_discovery_incomplete');
    }
    for (final session in candidates) {
      if (!ownedSessionID(session['id']) ||
          !ownership.matchesTitle(session['title'])) {
        continue;
      }
      final raw = await wire.request(
        'GET',
        '$prefix/session/${session['id']}',
        query: query,
      );
      final envelope = map(raw);
      final detail = envelope['data'] is Map ? map(envelope['data']) : envelope;
      if (detail['id'] != session['id'] || !ownership.matchesSession(detail)) {
        throw const ProbeFailure('ownership_session_scope_mismatch');
      }
      await ownership.recordCreated(detail['id'] as String);
    }
  }
  for (final id in ownership.sessionIDs) {
    try {
      final raw = await wire.request(
        'GET',
        '$prefix/session/$id',
        query: query,
      );
      final envelope = map(raw);
      final session = envelope['data'] is Map
          ? map(envelope['data'])
          : envelope;
      if (session['id'] != id || !ownership.matchesSession(session)) {
        throw const ProbeFailure('ownership_session_scope_mismatch');
      }
      try {
        await wire
            .request(
              'POST',
              '$prefix/session/$id/${oc2 ? 'interrupt' : 'abort'}',
              query: query,
            )
            .timeout(const Duration(seconds: 5));
      } catch (_) {
        // DELETE is the final session fence; no arbitrary response is emitted.
      }
      await wire
          .request('DELETE', '$prefix/session/$id', query: query)
          .timeout(const Duration(seconds: 15));
      await ownership.markDeleted(id);
    } on ProbeFailure catch (error) {
      if (error.code != 'http_404') rethrow;
      await ownership.markDeleted(id);
    }
  }
  ownership.removeIfEmpty();
}

/// Called under the emulator lock, including after fenced/failed parent runs.
Future<bool> cleanupOwnedSessions(
  String runID, {
  bool includeArchived = false,
  bool allOwned = false,
}) async {
  var failed = false;
  try {
    await PhoneRuntime.restoreNormalApp();
    if (includeArchived) {
      const archive = 'docs/qa/FQ3-2026-10-08';
      for (final engine in ['opencode', 'opencode2']) {
        for (final caseName in phases.keys) {
          final archived = File('$archive/$runID-$engine-$caseName.json');
          if (!archived.existsSync()) continue;
          if (FileSystemEntity.isLinkSync(archived.path) ||
              archived.lengthSync() > 64 * 1024) {
            throw const ProbeFailure('ownership_evidence_invalid');
          }
          final phase = map(jsonDecode(archived.readAsStringSync()));
          final ids = phase['ownedSessions'];
          if (ids is! List || ids.isEmpty) continue;
          if (phase['runID'] != runID ||
              phase['engine'] != engine ||
              phase['case'] != caseName ||
              !ids.every(ownedSessionID)) {
            throw const ProbeFailure('ownership_evidence_invalid');
          }
          SessionOwnership.create(
            File(
              '$evidenceDirectory/${SessionOwnership.filename(runID, engine, caseName, 'archived-recorded-ids')}',
            ),
            runID: runID,
            sourceRevision: phase['sourceRevision'] as String,
            attemptID: 'archived-recorded-ids',
            engine: engine,
            caseName: caseName,
            appUID: phase['appUID'] as int,
            appBuild: phase['appBuild'] as int,
            legacy: true,
            sessionIDs: ids.cast<String>(),
          );
        }
      }
    }
    final files =
        Directory(evidenceDirectory)
            .listSync(followLinks: false)
            .where((entry) => entry.path.endsWith('-ownership.json'))
            .toList()
          ..sort((a, b) => a.path.compareTo(b.path));
    for (final file in files) {
      PhoneRuntime? runtime;
      Fq3Wire? wire;
      var stage = 'read';
      try {
        final ownership = SessionOwnership.read(File(file.path));
        if (!allOwned && ownership.runID != runID) continue;
        stage = 'inspect';
        runtime = await PhoneRuntime.inspect(ownership.runID);
        stage = 'uid';
        requireConsistentAppUID(ownership.appUID, runtime.uid);
        stage = 'start';
        wire = Fq3Wire(
          baseUrl: await runtime.start(ownership.engine == 'opencode2'),
          password: runtime.password,
        );
        stage = 'delete';
        await cleanupLedger(wire, ownership);
      } on ProbeFailure catch (error) {
        failed = true;
        stdout.writeln('FQ3 owned cleanup $stage: ${error.code}');
      } catch (_) {
        failed = true;
        stdout.writeln('FQ3 owned cleanup $stage: cleanup_failed');
      } finally {
        try {
          await wire?.close();
          await runtime?.close();
        } catch (_) {
          failed = true;
          stdout.writeln('FQ3 owned cleanup close: cleanup_failed');
        }
      }
    }
  } catch (_) {
    failed = true;
  }
  try {
    await PhoneRuntime.restoreNormalApp();
  } catch (_) {
    failed = true;
  }
  if (failed) {
    stderr.writeln('FQ3 owned cleanup incomplete; intent retained');
    exitCode = 1;
  } else {
    stdout.writeln('FQ3 owned cleanup complete');
  }
  return !failed;
}

Future<void> _orchestrate(List<String> args, String runID) async {
  final started = DateTime.now().toUtc().toIso8601String();
  final revision = (await Process.run('git', [
    'rev-parse',
    'HEAD',
  ])).stdout.toString().trim();
  final attempt = 'attempt-${DateTime.now().microsecondsSinceEpoch}-$pid';
  final engines = <String, Map<String, Object?>>{};
  int? uid;
  String? fence;
  for (final engine in ['opencode', 'opencode2']) {
    final model = argument(
      args,
      engine == 'opencode2' ? '--oc2-model' : '--oc1-model',
    );
    final selection = modelSelectionFor(model);
    final results = <String, Object?>{};
    String? observed;
    for (final entry in phases.entries) {
      if (fence != null) {
        for (final key in entry.value) {
          results[key] = fail(fence);
        }
        continue;
      }
      stdout.writeln(
        'FQ3 $engine ${entry.key}; waiting for shared emulator lock',
      );
      final command = [
        '--phase',
        '--retain-history',
        '--run-id',
        runID,
        '--revision',
        revision,
        '--attempt',
        attempt,
        '--engine',
        engine,
        '--case',
        entry.key,
      ];
      if (argument(args, '--app-managed-engine') == engine) {
        command.add('--app-managed-only');
      }
      if (args.contains('--force-owned')) command.add('--force-owned');
      if (model != null) command.addAll(['--model', model]);
      final code = await lockedChild(command);
      final file = File('$evidenceDirectory/$runID-$engine-${entry.key}.json');
      var failure = 'phase_not_completed';
      try {
        if (!file.existsSync()) throw const ProbeFailure('phase_not_completed');
        final data = map(jsonDecode(file.readAsStringSync()));
        // Validate freshness even on nonzero exit, to identify cleanup failure.
        if (data['attemptID'] != attempt ||
            data['sourceRevision'] != revision ||
            data['runID'] != runID) {
          throw const ProbeFailure('stale_phase_evidence');
        }
        if (data['cleanupCode'] != null) {
          fence = 'cleanup_failed';
          throw const ProbeFailure('cleanup_failed');
        }
        if (code != 0) throw const ProbeFailure('phase_process_failed');
        final expectedVersion = engine == 'opencode2' ? '2.0.10' : '1.18.32';
        if ((data['cliVersion'] != null &&
                data['cliVersion'] != expectedVersion) ||
            (data['observedVersion'] != null &&
                data['observedVersion'] != expectedVersion)) {
          fence = 'runtime_version_changed';
        }
        uid = validatePhaseEvidence(
          data,
          runID: runID,
          sourceRevision: revision,
          attemptID: attempt,
          engine: engine,
          caseName: entry.key,
          expectedAppUID: uid,
          expectedTestedModel: model ?? 'server-default',
        );
        observed = data['observedVersion'] as String;
        for (final item in map(data['results']).entries) {
          if (!capabilityKeys.contains(item.key)) continue;
          final old = map(results[item.key]);
          if (old['state'] != 'pass' || map(item.value)['state'] == 'pass') {
            results[item.key] = item.value;
          }
        }
        failure = '';
      } on ProbeFailure catch (error) {
        failure = error.code;
        if ({
          'phase_uid_conflict',
          'phase_build_mismatch',
        }.contains(error.code)) {
          fence = error.code;
        }
      } catch (_) {
        failure = 'invalid_phase_evidence';
      }
      if (failure.isNotEmpty) {
        for (final key in entry.value) {
          results[key] = fail(failure);
        }
      }
      await Future<void>.delayed(const Duration(milliseconds: 500));
    }
    for (final key in capabilityKeys) {
      results.putIfAbsent(key, () => fail('not_observed'));
    }
    engines[engine] = {
      'expectedVersion': engine == 'opencode2' ? '2.0.10' : '1.18.32',
      'observedVersion': observed,
      'results': results,
      'modelSelection': selection,
    };
  }
  Map<String, Object?> switchResult = fail(
    fence ?? 'history_probe_unavailable',
  );
  if (fence == null) {
    stdout.writeln('FQ3 history switch; waiting for shared emulator lock');
    final code = await lockedChild([
      '--histories',
      '--run-id',
      runID,
      '--revision',
      revision,
      '--attempt',
      attempt,
      '--uid',
      '${uid ?? 0}',
      if (argument(args, '--app-managed-engine') != null) ...[
        '--app-managed-engine',
        argument(args, '--app-managed-engine')!,
      ],
      if (args.contains('--force-owned')) '--force-owned',
      for (final flag in ['--oc1-model', '--oc2-model'])
        if (argument(args, flag) != null) ...[flag, argument(args, flag)!],
    ]);
    try {
      final data = map(
        jsonDecode(
          File('$evidenceDirectory/$runID-histories.json').readAsStringSync(),
        ),
      );
      if (code != 0 ||
          data['runID'] != runID ||
          data['sourceRevision'] != revision ||
          data['attemptID'] != attempt ||
          data['appUID'] != uid ||
          data['appBuild'] != currentCertificationBuild) {
        throw const ProbeFailure('invalid_history_evidence');
      }
      final admitted = <Map<String, dynamic>>[];
      final owned = <String, Set<String>>{'opencode': {}, 'opencode2': {}};
      for (final engine in ['opencode', 'opencode2']) {
        for (final scenario in phases.keys) {
          final phaseData = map(
            jsonDecode(
              File(
                '$evidenceDirectory/$runID-$engine-$scenario.json',
              ).readAsStringSync(),
            ),
          );
          validatePhaseEvidence(
            phaseData,
            runID: runID,
            sourceRevision: revision,
            attemptID: attempt,
            engine: engine,
            caseName: scenario,
            expectedAppUID: uid,
            expectedTestedModel:
                argument(
                  args,
                  engine == 'opencode2' ? '--oc2-model' : '--oc1-model',
                ) ??
                'server-default',
          );
          admitted.add(phaseData);
          owned[engine]!.addAll(
            (phaseData['ownedSessions'] as List).cast<String>(),
          );
        }
      }
      validateHistoryBinding(
        data,
        runID: runID,
        sourceRevision: revision,
        attemptID: attempt,
        phaseManifest: phaseSessionManifest(admitted),
        appUID: uid!,
        oc1Sessions: owned['opencode']!.length,
        oc2Sessions: owned['opencode2']!.length,
      );
      switchResult = map(data['result']);
    } on ProbeFailure catch (error) {
      switchResult = fail(error.code);
    } catch (_) {
      switchResult = fail('history_probe_unavailable');
    }
  }
  if (fence != null) {
    for (final engine in engines.values) {
      engine['results'] = {for (final key in capabilityKeys) key: fail(fence)};
    }
  }
  final evidence = '$evidenceDirectory/$runID.json';
  final report = <String, Object?>{
    'schemaVersion': 2,
    'runID': runID,
    'device': serial,
    'appBuild': currentCertificationBuild,
    'sourceRevision': revision,
    'startedAt': started,
    'scope': 'phone-runtime',
    'attestation': {
      'runtime': 'in-app-ubuntu',
      'transport': 'adb-forward',
      'live': uid != null,
      'appUID': uid ?? 0,
      'versionProbeUID': uid ?? 0,
      'buildVerified': uid != null,
      'credentialSource': 'runtime-launch-config',
      'cleanupCompleted': false,
    },
    'engines': engines,
    'protocolSwitch': switchResult,
    'evidence': evidence,
  };
  File(evidence).writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(report)}\n',
  );
}

Future<void> histories(List<String> args, String runID) async {
  PhoneRuntime? runtime;
  var result = fail('history_probe_failed');
  final ids = <String, List<String>>{};
  final admitted = <Map<String, dynamic>>[];
  String? manifest;
  var cleanupCompleted = false;
  final baseline = <String, Map<String, String>>{};
  final nonempty = <String, bool>{};
  try {
    for (final engine in ['opencode', 'opencode2']) {
      ids[engine] = [];
      for (final caseName in phases.keys) {
        final file = File('$evidenceDirectory/$runID-$engine-$caseName.json');
        if (file.existsSync()) {
          final phaseData = map(jsonDecode(file.readAsStringSync()));
          if (phaseData['runID'] != runID ||
              phaseData['attemptID'] != argument(args, '--attempt') ||
              phaseData['sourceRevision'] != argument(args, '--revision')) {
            continue;
          }
          validatePhaseEvidence(
            phaseData,
            runID: runID,
            sourceRevision: argument(args, '--revision') ?? '',
            attemptID: argument(args, '--attempt') ?? '',
            engine: engine,
            caseName: caseName,
            expectedAppUID: int.tryParse(argument(args, '--uid') ?? ''),
            expectedTestedModel:
                argument(
                  args,
                  engine == 'opencode2' ? '--oc2-model' : '--oc1-model',
                ) ??
                'server-default',
          );
          admitted.add(phaseData);
          ids[engine]!.addAll(
            (phaseData['ownedSessions'] as List).cast<String>(),
          );
        }
      }
    }
    manifest = phaseSessionManifest(admitted);
    await PhoneRuntime.restoreNormalApp();
    runtime = await PhoneRuntime.inspect(runID);
    requireConsistentAppUID(
      int.tryParse(argument(args, '--uid') ?? ''),
      runtime.uid,
    );
    if (ids.values.any((v) => v.isEmpty)) {
      throw const ProbeFailure('owned_histories_missing');
    }
    for (final engine in ['opencode', 'opencode2', 'opencode', 'opencode2']) {
      final oc2 = engine == 'opencode2';
      final cliVersion = await runtime.version(oc2);
      if (cliVersion != (oc2 ? '2.0.10' : '1.18.32')) {
        throw const ProbeFailure('runtime_version_mismatch');
      }
      final endpoint = await runtime.start(
        oc2,
        appManagedOnly: argument(args, '--app-managed-engine') == engine,
        forceOwned: args.contains('--force-owned'),
      );
      final wire = Fq3Wire(baseUrl: endpoint, password: runtime.password);
      try {
        final health = map(
          await wire.request('GET', oc2 ? '/api/health' : '/global/health'),
        );
        if (health['version'] != cliVersion) {
          throw const ProbeFailure('runtime_version_mismatch');
        }
        final query = oc2
            ? {'location[directory]': runtime.directory}
            : {'directory': runtime.directory};
        final prefix = oc2 ? '/api' : '';
        final sessions = list(
          await wire.request(
            'GET',
            '$prefix/session',
            query: {'directory': runtime.directory, 'limit': '200'},
          ),
        );
        final current = <String, String>{};
        for (final id in ids[engine]!.toSet()) {
          if (!sessions.any((s) => s['id'] == id)) {
            throw const ProbeFailure('session_history_lost');
          }
          final messages = list(
            await wire.request(
              'GET',
              '$prefix/session/$id/message',
              query: query,
            ),
          );
          current[id] = historyProjection(messages, oc2: oc2);
          if (messages.isNotEmpty) nonempty[engine] = true;
        }
        if (baseline.containsKey(engine)) {
          for (final entry in current.entries) {
            if (jsonEncode(entry.value) !=
                jsonEncode(baseline[engine]![entry.key])) {
              throw const ProbeFailure('message_history_changed');
            }
          }
        } else {
          baseline[engine] = current;
        }
      } finally {
        await wire.close();
      }
    }
    if (['opencode', 'opencode2'].any((engine) => nonempty[engine] != true)) {
      throw const ProbeFailure('nonempty_histories_missing');
    }
    result = {
      'state': 'pass',
      'code': 'verified',
      'facts': {
        'asserted': true,
        'bothHistoriesPreserved': true,
        'freshClients': true,
        'oc1Sessions': baseline['opencode']!.length,
        'oc2Sessions': baseline['opencode2']!.length,
      },
    };
  } on ProbeFailure catch (error) {
    result = fail(error.code);
  } catch (_) {
    result = fail('history_probe_failed');
  } finally {
    try {
      await runtime?.close();
    } catch (_) {
      result = fail('cleanup_failed');
      exitCode = 1;
    }
    try {
      await PhoneRuntime.restoreNormalApp();
    } catch (_) {
      result = fail('app_restore_failed');
      exitCode = 1;
    }
    cleanupCompleted = await cleanupOwnedSessions(runID);
    if (!cleanupCompleted) {
      result = fail('owned_session_cleanup_failed');
      exitCode = 1;
    }
  }
  File('$evidenceDirectory/$runID-histories.json').writeAsStringSync(
    '${jsonEncode({'runID': runID, 'sourceRevision': argument(args, '--revision'), 'attemptID': argument(args, '--attempt'), 'phaseManifest': manifest, 'appUID': runtime?.uid, 'appBuild': runtime?.appBuild, 'cleanupCompleted': cleanupCompleted, 'result': result})}\n',
  );
  stdout.writeln('protocolSwitch: ${result['state']} (${result['code']})');
}
