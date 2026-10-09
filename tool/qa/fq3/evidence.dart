import 'dart:convert';

import 'common.dart';

const currentCertificationBuild = 2203;

bool isPublicModelReference(Object? value) =>
    value is String &&
    RegExp(
          r'^[A-Za-z0-9][A-Za-z0-9._-]{0,95}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$',
        ).stringMatch(value) ==
        value;

Map<String, Object?> modelSelectionFor(String? model) {
  if (model != null && !isPublicModelReference(model)) {
    throw const ProbeFailure('phase_model_invalid');
  }
  return {
    'source': model == null ? 'server-default' : 'explicit',
    'requested': model,
  };
}

/// Late cleanup cannot leave capability passes in the current run's report.
Map<String, dynamic> reportAfterCleanup(
  Map<String, dynamic> report, {
  required bool cleanupCompleted,
}) {
  final updated = jsonDecode(jsonEncode(report)) as Map<String, dynamic>;
  final attestation = updated['attestation'];
  if (attestation is! Map) throw const ProbeFailure('invalid_run_evidence');
  attestation['cleanupCompleted'] = cleanupCompleted;
  if (!cleanupCompleted) {
    Map<String, Object?> failure() => {
      'state': 'fail',
      'code': 'owned_session_cleanup_failed',
      'facts': <String, Object?>{},
    };
    final engines = updated['engines'];
    if (engines is! Map || !engines.values.every((engine) => engine is Map)) {
      throw const ProbeFailure('invalid_run_evidence');
    }
    for (final engine in engines.values) {
      (engine as Map)['results'] = {
        for (final key in capabilityKeys) key: failure(),
      };
    }
    updated['protocolSwitch'] = failure();
  }
  return updated;
}

const _versions = {'opencode': '1.18.32', 'opencode2': '2.0.10'};
const _cases = {
  'stream',
  'reconnect',
  'model',
  'abort',
  'allow',
  'deny',
  'image',
  'cards',
};
const _metadata = {
  'runID',
  'sourceRevision',
  'attemptID',
  'engine',
  'case',
  'appUID',
  'appBuild',
  'cliVersion',
  'observedVersion',
  'cleanupCode',
  'serverKind',
  'testedModel',
};

/// Validates the identity of one completed attempt before its results merge.
///
/// Results and owned session IDs remain the caller's responsibility. No input
/// values are copied into exceptions, and the supplied map is never changed.
int validatePhaseEvidence(
  Map<String, dynamic> evidence, {
  required String runID,
  required String sourceRevision,
  required String attemptID,
  required String engine,
  required String caseName,
  int? expectedAppUID,
  String? expectedTestedModel,
}) {
  final version = _versions[engine];
  if (version == null ||
      !_cases.contains(caseName) ||
      !RegExp(r'^fq3-[A-Za-z0-9_-]{1,80}$').hasMatch(runID) ||
      !RegExp(r'^[0-9a-f]{40}$').hasMatch(sourceRevision) ||
      !RegExp(r'^[A-Za-z0-9_-]{1,160}$').hasMatch(attemptID)) {
    throw const ProbeFailure('phase_expectation_invalid');
  }
  if (!_metadata.every(evidence.containsKey)) {
    throw const ProbeFailure('phase_metadata_missing');
  }
  if (evidence['runID'] != runID ||
      evidence['sourceRevision'] != sourceRevision ||
      evidence['attemptID'] != attemptID ||
      evidence['engine'] != engine ||
      evidence['case'] != caseName) {
    throw const ProbeFailure('phase_identity_mismatch');
  }
  final uid = evidence['appUID'];
  if (uid is! int || uid < 10000) {
    throw const ProbeFailure('phase_uid_invalid');
  }
  if (evidence['appBuild'] is! int ||
      evidence['appBuild'] != currentCertificationBuild) {
    throw const ProbeFailure('phase_build_mismatch');
  }
  if (evidence['cliVersion'] != version ||
      evidence['observedVersion'] != version) {
    throw const ProbeFailure('phase_version_mismatch');
  }
  if (evidence['cleanupCode'] != null) {
    throw const ProbeFailure('phase_cleanup_failed');
  }
  if (!const {'app-managed', 'owned'}.contains(evidence['serverKind'])) {
    throw const ProbeFailure('phase_server_kind_invalid');
  }
  final testedModel = evidence['testedModel'];
  if (testedModel != 'server-default' && !isPublicModelReference(testedModel)) {
    throw const ProbeFailure('phase_model_invalid');
  }
  if (expectedTestedModel != null && testedModel != expectedTestedModel) {
    throw const ProbeFailure('phase_model_mismatch');
  }
  return requireConsistentAppUID(expectedAppUID, uid);
}

/// Retains a single real application UID for every contributing phase.
int requireConsistentAppUID(int? expectedUID, int observedUID) {
  if (observedUID < 10000 || (expectedUID != null && expectedUID < 10000)) {
    throw const ProbeFailure('phase_uid_invalid');
  }
  if (expectedUID != null && expectedUID != observedUID) {
    throw const ProbeFailure('phase_uid_conflict');
  }
  return observedUID;
}
