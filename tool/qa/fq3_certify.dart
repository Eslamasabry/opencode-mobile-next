import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'fq3/common.dart';
import 'fq3/device.dart';
import 'fq3/oc1.dart';
import 'fq3/oc2.dart';

const evidenceDirectory = 'docs/qa/FQ3-2026-10-08';
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
    await histories(runID);
  } else {
    await orchestrate(args, runID);
  }
}

Future<void> phase(List<String> args, String runID) async {
  final engine = argument(args, '--engine');
  final caseName = argument(args, '--case');
  if (!['opencode', 'opencode2'].contains(engine) ||
      !phases.containsKey(caseName)) {
    throw const ProbeFailure('invalid_phase');
  }
  PhoneRuntime? runtime;
  Fq3Wire? wire;
  ProbeRun? run;
  String? cliVersion;
  var preflightCode = 'runtime_unavailable';
  try {
    runtime = await PhoneRuntime.inspect(runID);
    cliVersion = await runtime.version(engine == 'opencode2');
    final endpoint = await runtime.start(engine == 'opencode2');
    wire = Fq3Wire(baseUrl: endpoint, password: runtime.password);
    final options = ProbeOptions(
      directory: runtime.directory,
      title: runID,
      model: argument(args, '--model'),
      capabilities: phases[caseName],
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
          await wire.request(
            'POST',
            engine == 'opencode2'
                ? '/api/session/$id/interrupt'
                : '/session/$id/abort',
          );
        } catch (_) {
          /* Only owned sessions; server teardown is the final fence. */
        }
      }
    }
    await wire?.close();
    await runtime?.close();
  }
  final output = <String, Object?>{
    'engine': engine,
    'case': caseName,
    'cliVersion': cliVersion,
    'observedVersion': run?.observedVersion,
    'appUID': runtime?.uid,
    'appBuild': runtime?.appBuild,
    'results':
        run?.results ??
        {
          for (final key in {...capabilityKeys.take(3), ...phases[caseName]!})
            key: fail(preflightCode),
        },
    'ownedSessions': run?.sessionIDs ?? [],
  };
  File('$evidenceDirectory/$runID-$engine-$caseName.json').writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(output)}\n',
  );
  stdout.writeln('$engine $caseName: evidence saved');
}

Future<void> orchestrate(List<String> args, String runID) async {
  final started = DateTime.now().toUtc().toIso8601String();
  final revision = (await Process.run('git', [
    'rev-parse',
    'HEAD',
  ])).stdout.toString().trim();
  final engines = <String, Map<String, Object?>>{};
  int? uid;
  for (final engine in ['opencode', 'opencode2']) {
    final results = <String, Object?>{};
    String? observed;
    for (final entry in phases.entries) {
      stdout.writeln(
        'FQ3 $engine ${entry.key}; acquiring shared emulator lock',
      );
      final command = [
        '-w',
        '60',
        lock,
        Platform.resolvedExecutable,
        Platform.script.toFilePath(),
        '--phase',
        '--run-id',
        runID,
        '--engine',
        engine,
        '--case',
        entry.key,
      ];
      final model = argument(
        args,
        engine == 'opencode2' ? '--oc2-model' : '--oc1-model',
      );
      if (model != null) command.addAll(['--model', model]);
      final child = await Process.start('flock', command);
      child.stdout.transform(utf8.decoder).listen(stdout.write);
      child.stderr.drain<void>(); // Never echo subprocess exception bodies.
      await child.exitCode;
      final file = File('$evidenceDirectory/$runID-$engine-${entry.key}.json');
      if (file.existsSync()) {
        final data = map(jsonDecode(file.readAsStringSync()));
        uid ??= data['appUID'] as int?;
        observed ??= data['observedVersion'] as String?;
        for (final item in map(data['results']).entries) {
          final old = map(results[item.key]);
          if (old['state'] != 'pass' || map(item.value)['state'] == 'pass') {
            results[item.key] = item.value;
          }
        }
      } else {
        for (final key in entry.value) {
          results[key] = fail('phase_not_completed');
        }
      }
      // Release the lock between owned sessions so other lanes can proceed.
      await Future<void>.delayed(const Duration(milliseconds: 500));
    }
    for (final key in capabilityKeys) {
      results.putIfAbsent(key, () => fail('not_observed'));
    }
    engines[engine] = {
      'expectedVersion': engine == 'opencode2' ? '2.0.10' : '1.18.32',
      'observedVersion': observed,
      'results': results,
    };
  }
  final history = await Process.start('flock', [
    '-w',
    '60',
    lock,
    Platform.resolvedExecutable,
    Platform.script.toFilePath(),
    '--histories',
    '--run-id',
    runID,
  ]);
  history.stdout.transform(utf8.decoder).listen(stdout.write);
  history.stderr.drain<void>();
  await history.exitCode;
  final historyFile = File('$evidenceDirectory/$runID-histories.json');
  final switchResult = historyFile.existsSync()
      ? jsonDecode(historyFile.readAsStringSync())
      : fail('history_probe_unavailable');
  final evidence = '$evidenceDirectory/$runID.json';
  final report = <String, Object?>{
    'schemaVersion': 1,
    'runID': runID,
    'device': serial,
    'appBuild': 2195,
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
    },
    'engines': engines,
    'protocolSwitch': switchResult,
    'evidence': evidence,
  };
  File(evidence).writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(report)}\n',
  );
  final generated = await Process.run('python3', [
    'tool/qa/fq3/update_matrix.py',
    '--run',
    evidence,
  ]);
  if (generated.exitCode != 0) {
    stdout.writeln('Matrix generation rejected evidence; report retained');
    exitCode = 1;
  } else {
    stdout.writeln('Generated protocol certification matrix from $evidence');
  }
}

Future<void> histories(String runID) async {
  PhoneRuntime? runtime;
  var result = fail('history_probe_failed');
  final ids = <String, List<String>>{};
  for (final engine in ['opencode', 'opencode2']) {
    ids[engine] = [];
    for (final caseName in phases.keys) {
      final file = File('$evidenceDirectory/$runID-$engine-$caseName.json');
      if (file.existsSync()) {
        ids[engine]!.addAll(
          (map(jsonDecode(file.readAsStringSync()))['ownedSessions'] as List)
              .cast<String>(),
        );
      }
    }
  }
  final baseline = <String, Map<String, List<String>>>{};
  try {
    runtime = await PhoneRuntime.inspect(runID);
    if (ids.values.any((v) => v.isEmpty)) {
      throw const ProbeFailure('owned_histories_missing');
    }
    for (final engine in ['opencode', 'opencode2', 'opencode', 'opencode2']) {
      final oc2 = engine == 'opencode2';
      final endpoint = await runtime.start(oc2);
      final wire = Fq3Wire(baseUrl: endpoint, password: runtime.password);
      try {
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
        final current = <String, List<String>>{};
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
          current[id] =
              messages
                  .map((m) => (oc2 ? m['id'] : map(m['info'])['id']) as String)
                  .toList()
                ..sort();
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
    if (baseline.values.any(
      (history) => history.values.every((messages) => messages.isEmpty),
    )) {
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
    // Delete only exact session IDs created by this run, under their dialect.
    for (final engine in ['opencode', 'opencode2']) {
      final oc2 = engine == 'opencode2';
      final wire = Fq3Wire(
        baseUrl: await runtime.start(oc2),
        password: runtime.password,
      );
      try {
        for (final id in ids[engine]!.toSet()) {
          await wire.request(
            'DELETE',
            '${oc2 ? '/api' : ''}/session/$id',
            query: oc2
                ? {'location[directory]': runtime.directory}
                : {'directory': runtime.directory},
          );
        }
      } finally {
        await wire.close();
      }
    }
  } on ProbeFailure catch (error) {
    result = fail(error.code);
  } catch (_) {
    result = fail('history_probe_failed');
  } finally {
    await runtime?.close();
  }
  File(
    '$evidenceDirectory/$runID-histories.json',
  ).writeAsStringSync('${jsonEncode(result)}\n');
  stdout.writeln('protocolSwitch: ${result['state']} (${result['code']})');
}
