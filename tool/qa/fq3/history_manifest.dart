import 'dart:convert';

import 'package:crypto/crypto.dart';

import 'common.dart';
import 'evidence.dart';

const _publicMetadata = [
  'runID',
  'sourceRevision',
  'attemptID',
  'engine',
  'case',
  'appUID',
  'appBuild',
  'cliVersion',
  'observedVersion',
  'testedModel',
];

/// Binds history proof to the ordered, already validated phase/session set.
///
/// Only public identity metadata and bounded owned session IDs enter the hash.
/// Result bodies, message contents and provider data are deliberately excluded.
String phaseSessionManifest(List<Map<String, dynamic>> phases) {
  if (phases.isEmpty || phases.length > 64) {
    throw const ProbeFailure('history_manifest_invalid');
  }
  final selected = <Map<String, Object?>>[];
  for (final phase in phases) {
    final metadata = <String, Object?>{};
    for (final key in _publicMetadata) {
      final value = phase[key];
      if (key == 'appUID' || key == 'appBuild') {
        if (value is! int) {
          throw const ProbeFailure('history_manifest_invalid');
        }
      } else if (key == 'testedModel') {
        if (value != 'server-default' && !isPublicModelReference(value)) {
          throw const ProbeFailure('history_manifest_invalid');
        }
      } else if (value is! String || value.isEmpty || value.length > 160) {
        throw const ProbeFailure('history_manifest_invalid');
      }
      metadata[key] = value;
    }
    final sessions = phase['ownedSessions'];
    if (sessions is! List || sessions.length > 128) {
      throw const ProbeFailure('history_manifest_invalid');
    }
    final identifiers = <String>[];
    for (final id in sessions) {
      if (id is! String ||
          RegExp(r'^ses_[A-Za-z0-9]{1,128}$').stringMatch(id) != id) {
        throw const ProbeFailure('history_manifest_invalid');
      }
      identifiers.add(id);
    }
    metadata['ownedSessions'] = identifiers;
    selected.add(metadata);
  }
  return sha256.convert(utf8.encode(jsonEncode(selected))).toString();
}

/// Admits only a history result bound to this candidate and current sessions.
void validateHistoryBinding(
  Map<String, dynamic> history, {
  required String runID,
  required String sourceRevision,
  required String attemptID,
  required String phaseManifest,
  required int appUID,
  required int oc1Sessions,
  required int oc2Sessions,
}) {
  if (appUID < 10000 ||
      oc1Sessions < 0 ||
      oc2Sessions < 0 ||
      RegExp(r'^[0-9a-f]{64}$').stringMatch(phaseManifest) != phaseManifest) {
    throw const ProbeFailure('history_expectation_invalid');
  }
  if (history['runID'] != runID ||
      history['sourceRevision'] != sourceRevision ||
      history['attemptID'] != attemptID ||
      history['phaseManifest'] != phaseManifest ||
      history['appUID'] is! int ||
      history['appUID'] != appUID ||
      history['appBuild'] is! int ||
      history['appBuild'] != currentCertificationBuild) {
    throw const ProbeFailure('history_binding_mismatch');
  }
  if (history['cleanupCompleted'] is! bool ||
      history['cleanupCompleted'] != true) {
    throw const ProbeFailure('history_cleanup_failed');
  }
  final result = history['result'];
  if (result is! Map ||
      !const {'pass', 'fail'}.contains(result['state']) ||
      result['code'] is! String ||
      RegExp(r'^[a-z][a-z0-9_]{0,63}$').stringMatch(result['code'] as String) !=
          result['code'] ||
      result['facts'] is! Map) {
    throw const ProbeFailure('history_result_invalid');
  }
  final code = result['code'] as String;
  if (history['cleanupCode'] != null ||
      code == 'cleanup_failed' ||
      code.endsWith('_cleanup_failed')) {
    throw const ProbeFailure('history_cleanup_failed');
  }
  if (result['state'] == 'pass') {
    final facts = result['facts'] as Map;
    if (oc1Sessions == 0 ||
        oc2Sessions == 0 ||
        facts['oc1Sessions'] is! int ||
        facts['oc2Sessions'] is! int ||
        facts['oc1Sessions'] != oc1Sessions ||
        facts['oc2Sessions'] != oc2Sessions) {
      throw const ProbeFailure('history_session_count_mismatch');
    }
  }
}
