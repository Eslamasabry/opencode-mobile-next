import 'dart:convert';

import '../agent_catalog.dart';
import 'agent_certification_snapshot.dart';

/// Qualification for exact agent/helper versions, optionally scoped to a CPU.
/// Installation, identity, and non-pass cells never grant runtime capabilities.
final class AgentCertificationMatrix {
  AgentCertificationMatrix._(this._records);

  static final bundled = AgentCertificationMatrix.fromJson(
    jsonDecode(agentCertificationBundledJson) as Map<String, dynamic>,
  );

  factory AgentCertificationMatrix.fromJson(Map<String, dynamic> json) {
    final records = <String, _CertificationRecord>{};
    final seen = <String>{};
    final duplicated = <String>{};
    final rows = json['agents'];
    if (rows is! List) return AgentCertificationMatrix._(const {});
    for (final row in rows) {
      if (row is! Map || row['id'] is! String) continue;
      final id = _canonicalId(row['id'] as String);
      if (AgentCatalog.builtIn.byId(id) == null) continue;
      if (!seen.add(id)) duplicated.add(id);
      final record = _CertificationRecord.parse(row);
      if (record != null) records[id] = record;
    }
    for (final id in duplicated) {
      records.remove(id);
    }
    return AgentCertificationMatrix._(Map.unmodifiable(records));
  }

  final Map<String, _CertificationRecord> _records;

  AgentCapabilities capabilitiesFor({
    required AgentDescriptor descriptor,
    required AgentArchitecture? architecture,
    required String? helperVersion,
  }) {
    final record = _records[_canonicalId(descriptor.id)];
    if (record == null ||
        helperVersion == null ||
        record.agentVersion != descriptor.recipe?.version ||
        record.helperVersion != helperVersion ||
        (record.architecture != null &&
            record.architecture != architecture?.name)) {
      return const AgentCapabilities();
    }
    return AgentCapabilities(
      resumeVerified: record.passed.contains('resume'),
      modelList: record.passed.contains('models'),
      permissions: record.passed.contains('permission'),
      images: record.passed.contains('images'),
      cancel: record.passed.contains('abort'),
    );
  }

  // FQ1 names the OpenCode 1 catalog entry `opencode`; allow only this alias.
  static String _canonicalId(String id) => id == 'opencode1' ? 'opencode' : id;
}

final class _CertificationRecord {
  const _CertificationRecord({
    required this.agentVersion,
    required this.helperVersion,
    required this.architecture,
    required this.passed,
  });

  final String agentVersion;
  final String helperVersion;
  final String? architecture;
  final Set<String> passed;

  static const _cells = {
    'install',
    'version',
    'signedOut',
    'signIn',
    'models',
    'smoke',
    'tools',
    'permission',
    'abort',
    'resume',
    'network',
    'cards',
    'images',
  };
  static const _states = {
    'pass',
    'fail',
    'untested',
    'partial',
    'off',
    'n/a',
    'blocked',
  };

  static _CertificationRecord? parse(Map row) {
    final agentVersion = row['agentVersion'];
    final helperVersion = row['helperVersion'];
    final architecture = row['architecture'];
    final cells = row['cells'];
    if (!_version(agentVersion) ||
        !_version(helperVersion) ||
        (row.containsKey('architecture') &&
            !AgentArchitecture.values.any(
              (value) => value.name == architecture,
            )) ||
        cells is! Map) {
      return null;
    }
    final passed = <String>{};
    for (final cell in cells.entries) {
      final value = cell.value;
      if (!_cells.contains(cell.key) || value is! Map) return null;
      final state = value['state'];
      final evidence = value['evidence'];
      if (state is! String ||
          (!_states.contains(state) &&
              !RegExp(r'^blocked:[A-Za-z0-9_-]+$').hasMatch(state)) ||
          (evidence != null && evidence is! String)) {
        return null;
      }
      if (state == 'pass' && _safeEvidence(evidence)) {
        passed.add(cell.key as String);
      }
    }
    return _CertificationRecord(
      agentVersion: agentVersion as String,
      helperVersion: helperVersion as String,
      architecture: architecture as String?,
      passed: Set.unmodifiable(passed),
    );
  }

  static bool _version(Object? value) =>
      value is String &&
      RegExp(r'^[A-Za-z0-9][A-Za-z0-9._+-]*$').hasMatch(value);

  /// Evidence is a repository document path with an optional document anchor.
  /// No URL, absolute path, query, traversal, encoding, or prose substitute.
  static bool _safeEvidence(Object? value) {
    if (value is! String || value.isEmpty || value != value.trim()) {
      return false;
    }
    final citation = RegExp(
      r'^(docs/[A-Za-z0-9_./-]+\.md)(?:\s*#([A-Za-z0-9_-]+))?$',
    ).firstMatch(value);
    if (citation == null || value.contains(RegExp(r'[\x00-\x1f\x7f]'))) {
      return false;
    }
    final path = citation[1]!;
    return path
        .split('/')
        .every(
          (segment) => segment.isNotEmpty && segment != '.' && segment != '..',
        );
  }
}
