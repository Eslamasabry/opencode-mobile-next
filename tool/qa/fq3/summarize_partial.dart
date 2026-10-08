import 'dart:convert';
import 'dart:io';

import 'common.dart';
import 'evidence.dart';

/// Summarize five independent 2197 phases; never merge historical matrix cells.
void main(List<String> args) {
  if (args.length != 3) {
    stderr.writeln('Usage: summarize_partial.dart RUN_ID REVISION ATTEMPT');
    exitCode = 2;
    return;
  }
  try {
    final [runID, revision, attempt] = args;
    if (!RegExp(r'^fq3-[A-Za-z0-9_-]{1,80}$').hasMatch(runID)) {
      throw const ProbeFailure('invalid_run_id');
    }
    const directory = 'docs/qa/FQ3c-2026-10-08';
    const cases = {
      'model': 'modelSwitch',
      'allow': 'permissionAllow',
      'deny': 'permissionDeny',
      'image': 'image',
      'cards': 'cards',
    };
    final outcomes = <String, Object?>{};
    int? uid;
    for (final entry in cases.entries) {
      final file = File('$directory/$runID-opencode2-${entry.key}.json');
      if (file.lengthSync() > 65536) {
        throw const ProbeFailure('phase_evidence_too_large');
      }
      final phase = jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
      uid = validatePhaseEvidence(
        phase,
        runID: runID,
        sourceRevision: revision,
        attemptID: attempt,
        engine: 'opencode2',
        caseName: entry.key,
        expectedAppUID: uid,
        expectedTestedModel: 'opencode/big-pickle',
      );
      final result = (phase['results'] as Map)[entry.value] as Map;
      if (!{'pass', 'fail'}.contains(result['state']) ||
          result['code'] is! String ||
          !RegExp(
            r'^[a-z][a-z0-9_]{0,63}$',
          ).hasMatch(result['code'] as String)) {
        throw const ProbeFailure('invalid_phase_result');
      }
      outcomes[entry.value] = {
        'state': result['state'],
        'code': result['code'],
        'serverKind': phase['serverKind'],
        'evidence': file.path.split('/').last,
        'ownedSessionCount': (phase['ownedSessions'] as List).length,
        'cleanupCompleted': true,
      };
    }
    if (Directory(directory).listSync().whereType<File>().any(
      (file) =>
          file.path.split('/').last.startsWith('$runID-') &&
          file.path.endsWith('-ownership.json'),
    )) {
      throw const ProbeFailure('ownership_journal_remaining');
    }
    final summary = {
      'schemaVersion': 1,
      'runID': runID,
      'sourceRevision': revision,
      'attemptID': attempt,
      'appBuild': currentCertificationBuild,
      'appUID': uid,
      'engine': 'opencode2',
      'version': '2.0.10',
      'testedModel': 'opencode/big-pickle',
      'scope':
          'five standalone phases; no dual-engine or history certification',
      'capabilities': outcomes,
      'cleanupCompleted': true,
      'protocolMatrixUpdated': false,
      'deviceQualified': false,
    };
    final output = File('$directory/$runID-summary.json');
    if (output.existsSync())
      throw const ProbeFailure('evidence_already_exists');
    output.writeAsStringSync(
      '${const JsonEncoder.withIndent('  ').convert(summary)}\n',
    );
    stdout.writeln('Five-phase summary saved; protocol matrix unchanged');
  } catch (_) {
    stderr.writeln(
      'Partial summary refused: identity, results or cleanup invalid',
    );
    exitCode = 1;
  }
}
