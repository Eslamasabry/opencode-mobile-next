import 'common.dart';

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
  if (evidence['appBuild'] is! int || evidence['appBuild'] != 2195) {
    throw const ProbeFailure('phase_build_mismatch');
  }
  if (evidence['cliVersion'] != version ||
      evidence['observedVersion'] != version) {
    throw const ProbeFailure('phase_version_mismatch');
  }
  if (evidence['cleanupCode'] != null) {
    throw const ProbeFailure('phase_cleanup_failed');
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
