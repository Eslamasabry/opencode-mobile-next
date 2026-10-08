import 'dart:convert';
import 'dart:io';

import '../fq3_certify.dart' as driver;
import 'common.dart';
import 'evidence.dart';
import 'history_manifest.dart';

/// Reassemble a paused run after its missing phases execute at the same HEAD.
/// This command neither runs a scenario nor changes its pass/fail assertions.
Future<void> main(List<String> args) async {
  try {
    final capture = args.length == 2 && args.first == '--capture-history';
    final runID = capture ? args.last : (args.length == 1 ? args.single : '');
    if (!RegExp(r'^fq3-[A-Za-z0-9_-]{1,80}$').hasMatch(runID)) {
      throw const ProbeFailure('invalid_run_id');
    }
    final path = '${driver.evidenceDirectory}/$runID.json';
    final report = readEvidence(path);
    final revision = (await Process.run('git', [
      'rev-parse',
      'HEAD',
    ])).stdout.toString().trim();
    if (report['runID'] != runID ||
        report['schemaVersion'] is! int ||
        report['schemaVersion'] != 2 ||
        report['sourceRevision'] != revision ||
        report['appBuild'] is! int ||
        report['appBuild'] != currentCertificationBuild) {
      throw const ProbeFailure('candidate_changed');
    }
    const frozen = [
      'tool/qa/fq3_certify.dart',
      'tool/qa/fq3/common.dart',
      'tool/qa/fq3/device.dart',
      'tool/qa/fq3/oc1.dart',
      'tool/qa/fq3/oc2.dart',
      'tool/qa/fq3/history.dart',
      'tool/qa/fq3/evidence.dart',
      'tool/qa/fq3/history_manifest.dart',
      'tool/qa/fq3/session_ownership.dart',
      'lib/api2/dialect.dart',
    ];
    final diff = await Process.run('git', [
      'diff',
      '--quiet',
      'HEAD',
      '--',
      ...frozen,
    ]);
    if (diff.exitCode != 0) {
      throw const ProbeFailure('candidate_source_changed');
    }
    final uid = driver.map(report['attestation'])['appUID'];
    if (uid is! int) throw const ProbeFailure('phase_uid_invalid');
    if (!capture &&
        driver.map(report['attestation'])['cleanupCompleted'] != true) {
      throw const ProbeFailure('owned_session_cleanup_failed');
    }
    final anchor = readEvidence(
      '${driver.evidenceDirectory}/$runID-opencode-stream.json',
    );
    final attempt = anchor['attemptID'];
    if (attempt is! String) throw const ProbeFailure('phase_metadata_missing');
    final admitted = <Map<String, dynamic>>[];
    final sessions = <String, Set<String>>{'opencode': {}, 'opencode2': {}};
    final engines = driver.map(report['engines']);
    for (final engine in ['opencode', 'opencode2']) {
      final record = driver.map(engines[engine]);
      final results = driver.map(record['results']);
      final selection = driver.map(record['modelSelection']);
      if (selection.length != 2 ||
          !selection.containsKey('source') ||
          !selection.containsKey('requested')) {
        throw const ProbeFailure('phase_model_invalid');
      }
      final requested = selection['requested'];
      final String expectedModel;
      if (selection.length == 2 &&
          selection['source'] == 'server-default' &&
          requested == null) {
        expectedModel = 'server-default';
      } else if (selection.length == 2 &&
          selection['source'] == 'explicit' &&
          isPublicModelReference(requested)) {
        expectedModel = requested as String;
      } else {
        throw const ProbeFailure('phase_model_invalid');
      }
      String? testedModel;
      for (final entry in driver.phases.entries) {
        final phase = readEvidence(
          '${driver.evidenceDirectory}/$runID-$engine-${entry.key}.json',
        );
        validatePhaseEvidence(
          phase,
          runID: runID,
          sourceRevision: revision,
          attemptID: attempt,
          engine: engine,
          caseName: entry.key,
          expectedAppUID: uid,
          expectedTestedModel: expectedModel,
        );
        testedModel = phase['testedModel'] as String;
        admitted.add(phase);
        sessions[engine]!.addAll(
          (phase['ownedSessions'] as List).cast<String>(),
        );
        final observations = driver.map(phase['results']);
        for (final capability in entry.value) {
          if (!observations.containsKey(capability)) {
            throw const ProbeFailure('phase_result_missing');
          }
          results[capability] = observations[capability];
        }
      }
      record['results'] = results;
      record['modelSelection'] = modelSelectionFor(
        testedModel == 'server-default' ? null : testedModel,
      );
      engines[engine] = record;
    }
    final manifest = phaseSessionManifest(admitted);
    final historyPath = '${driver.evidenceDirectory}/$runID-histories.json';
    if (capture) {
      final previous = File(historyPath);
      if (previous.existsSync()) previous.deleteSync();
      // Outer caller owns the shared device lock, as with --histories.
      await driver.histories([
        '--revision',
        revision,
        '--attempt',
        attempt,
        '--uid',
        '$uid',
        for (final engine in ['opencode', 'opencode2'])
          if (driver.map(
                driver.map(engines[engine])['modelSelection'],
              )['source'] ==
              'explicit') ...[
            engine == 'opencode2' ? '--oc2-model' : '--oc1-model',
            driver.map(
                  driver.map(engines[engine])['modelSelection'],
                )['requested']
                as String,
          ],
      ], runID);
      if (exitCode != 0) throw const ProbeFailure('history_process_failed');
      final after = <Map<String, dynamic>>[];
      for (final engine in ['opencode', 'opencode2']) {
        for (final caseName in driver.phases.keys) {
          after.add(
            readEvidence(
              '${driver.evidenceDirectory}/$runID-$engine-$caseName.json',
            ),
          );
        }
      }
      if (phaseSessionManifest(after) != manifest) {
        throw const ProbeFailure('phase_manifest_changed');
      }
      final captured = readEvidence(historyPath)..['phaseManifest'] = manifest;
      if (captured['cleanupCompleted'] != true ||
          !await driver.cleanupOwnedSessions(runID)) {
        throw const ProbeFailure('owned_session_cleanup_failed');
      }
      validateHistoryBinding(
        captured,
        runID: runID,
        sourceRevision: revision,
        attemptID: attempt,
        phaseManifest: manifest,
        appUID: uid,
        oc1Sessions: sessions['opencode']!.length,
        oc2Sessions: sessions['opencode2']!.length,
      );
      File(historyPath).writeAsStringSync('${jsonEncode(captured)}\n');
      final attestation = driver.map(report['attestation'])
        ..['cleanupCompleted'] = true;
      report['attestation'] = attestation;
      final staged = File('$path.cleanup.tmp')
        ..writeAsStringSync('${jsonEncode(report)}\n', flush: true);
      staged.renameSync(path);
      stdout.writeln(
        'Captured fresh history against frozen phase/session manifest',
      );
      return;
    }
    final history = readEvidence(historyPath);
    validateHistoryBinding(
      history,
      runID: runID,
      sourceRevision: revision,
      attemptID: attempt,
      phaseManifest: manifest,
      appUID: uid,
      oc1Sessions: sessions['opencode']!.length,
      oc2Sessions: sessions['opencode2']!.length,
    );
    report['engines'] = engines;
    report['protocolSwitch'] = history['result'];
    final encoded = '${const JsonEncoder.withIndent('  ').convert(report)}\n';
    // Validate before replacing the last usable canonical report.
    final validation = await Process.start('python3', [
      '-c',
      'import json,sys; from tool.qa.fq3.update_matrix import validate_run; validate_run(json.load(sys.stdin))',
    ]);
    final out = validation.stdout.drain<void>();
    final err = validation.stderr.drain<void>();
    validation.stdin.write(encoded);
    await validation.stdin.close();
    final status = await validation.exitCode;
    await out;
    await err;
    if (status != 0) throw const ProbeFailure('invalid_run_evidence');
    final staged = File('$path.reconcile.tmp')..writeAsStringSync(encoded);
    staged.renameSync(path);
    final generated = await Process.run('python3', [
      'tool/qa/fq3/update_matrix.py',
      '--run',
      path,
    ]);
    if (generated.exitCode != 0) {
      throw const ProbeFailure('invalid_run_evidence');
    }
    stdout.writeln(
      'Reconciled unchanged-candidate evidence and generated matrix',
    );
  } on ProbeFailure catch (error) {
    stderr.writeln('Reconciliation failed: ${error.code}');
    exitCode = 1;
  } catch (_) {
    stderr.writeln('Reconciliation failed: invalid_evidence');
    exitCode = 1;
  }
}

Map<String, dynamic> readEvidence(String path) {
  if (FileSystemEntity.isLinkSync(path)) {
    throw const ProbeFailure('unsafe_evidence_path');
  }
  final file = File(path);
  if (file.lengthSync() > 64 * 1024) {
    throw const ProbeFailure('evidence_too_large');
  }
  final value = jsonDecode(file.readAsStringSync());
  if (value is! Map<String, dynamic>) {
    throw const ProbeFailure('invalid_evidence');
  }
  return value;
}
