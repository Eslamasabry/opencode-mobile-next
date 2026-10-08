import 'dart:convert';
import 'dart:io';
import 'device.dart';
import 'common.dart';
import 'evidence.dart';

Map<String, dynamic> map(Object? v) =>
    v is Map ? Map<String, dynamic>.from(v) : {};
List<Map<String, dynamic>> list(Object? v) {
  final d = v is Map ? v['data'] : v;
  return d is List ? d.whereType<Map>().map(map).toList() : [];
}

String? publicRef(Object? v) {
  final m = map(v);
  final p = m['providerID'];
  final id = m['id'] ?? m['modelID'];
  if (p is! String || id is! String) {
    return null;
  }
  final ref = id.startsWith('$p/') ? id : '$p/$id';
  return isPublicModelReference(ref) ? ref : null;
}

/// Caller holds the emulator lock. Only public model IDs and counts are emitted.
Future<void> main() async {
  final r = await PhoneRuntime.inspect('fq3-20261008b-public-models');
  Fq3Wire? w;
  final result = <String, Object?>{};
  try {
    w = Fq3Wire(
      baseUrl: await r.start(true, appManagedOnly: true),
      password: r.password,
    );
    result['defaultModel'] = publicRef(
      map(await w.request('GET', '/api/model/default'))['data'],
    );
    final models = list(await w.request('GET', '/api/model'));
    result['enabledOpenCodeModels'] = models
        .where((m) => m['providerID'] == 'opencode' && m['enabled'] == true)
        .map(
          (m) => {
            'model': publicRef(m),
            'costInput': (m['cost'] is List
                ? list(m['cost']).firstOrNull ?? {}
                : map(m['cost']))['input'],
            'image': (map(m['capabilities'])['input'] as List? ?? []).contains(
              'image',
            ),
          },
        )
        .toList();
    final sessions = list(
      await w.request(
        'GET',
        '/api/session',
        query: {'limit': '20', 'order': 'desc'},
      ),
    );
    final counts = <String, int>{};
    for (final s in sessions.take(20)) {
      final id = s['id'];
      if (id is! String || !RegExp(r'^ses_[A-Za-z0-9]+$').hasMatch(id)) {
        continue;
      }
      final messages = list(
        await w.request(
          'GET',
          '/api/session/$id/message',
          query: {'limit': '10', 'order': 'desc'},
        ),
      );
      for (final m in messages) {
        if (m['type'] != 'assistant' ||
            map(m['time'])['completed'] is! num ||
            m['error'] != null) {
          continue;
        }
        final ref = publicRef(m['model']);
        if (ref != null) counts[ref] = (counts[ref] ?? 0) + 1;
      }
    }
    result['recentCompletedAssistantModels'] = counts;
    File(
      'docs/qa/FQ3b-2026-10-08/public-model-comparison.json',
    ).writeAsStringSync(
      '${const JsonEncoder.withIndent('  ').convert(result)}\n',
    );
    stdout.writeln(jsonEncode(result));
  } on ProbeFailure catch (e) {
    stdout.writeln(e.code);
  } catch (_) {
    stdout.writeln('metadata_probe_failed');
  } finally {
    await w?.close();
    await r.close();
    await PhoneRuntime.restoreNormalApp();
  }
}
