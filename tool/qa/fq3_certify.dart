import 'dart:ffi';
import 'dart:io';

import 'fq3/common.dart';
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

/// The old matrix requires both OC1 and OC2 and cannot certify this app.
Future<void> main(List<String> args) async {
  stderr.writeln(
    'The historical dual-engine FQ3 runner is retired. '
    'Use tool/qa/oc2_live_proof_test.dart for current OC2 client checks. '
    'Existing evidence and ownership records remain untouched.',
  );
  exitCode = 2;
}

/// Exact-directory/title recovery closes the gap before an ID callback commits.
Future<void> cleanupLedger(Fq3Wire wire, SessionOwnership ownership) async {
  if (ownership.engine != 'opencode2') {
    throw const ProbeFailure('retired_protocol_ownership_preserved');
  }
  const prefix = '/api';
  final query = {'location[directory]': ownership.directory};
  await wire.request('GET', '/api/health');
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
            .request('POST', '$prefix/session/$id/interrupt', query: query)
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
