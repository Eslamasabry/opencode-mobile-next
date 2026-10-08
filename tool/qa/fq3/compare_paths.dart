import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../fq3_certify.dart' as driver;
import 'common.dart';
import 'device.dart';
import 'diagnostics.dart';
import 'oc1.dart';
import 'oc2.dart';
import 'session_ownership.dart';

Map<String, dynamic> _map(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : {};
List<Map<String, dynamic>> _list(Object? value) {
  final data = value is Map ? value['data'] : value;
  return data is List ? data.whereType<Map>().map(_map).toList() : [];
}

/// A bounded diagnostic. Caller holds the emulator lock; every session is owned.
/// Neither request bodies, replies, provider configuration nor errors are saved.
Future<void> main(List<String> args) async {
  final runID = driver.argument(args, '--run-id') ?? '';
  final engine = driver.argument(args, '--engine') ?? 'opencode2';
  final source = driver.argument(args, '--source') ?? 'app-managed';
  final shape = driver.argument(args, '--shape') ?? 'app';
  final scenario = driver.argument(args, '--scenario') ?? 'stream';
  if (!RegExp(r'^fq3-[A-Za-z0-9_-]{1,80}$').hasMatch(runID) ||
      !{'opencode', 'opencode2'}.contains(engine) ||
      !{'app-managed', 'owned'}.contains(source) ||
      !{'app', 'adapter'}.contains(shape) ||
      !{'stream', 'abort'}.contains(scenario) ||
      (shape == 'app' && (engine != 'opencode2' || scenario != 'stream'))) {
    stderr.writeln('Diagnostic refused: invalid_options');
    exitCode = 1;
    return;
  }
  final revision = (await Process.run('git', [
    'rev-parse',
    'HEAD',
  ])).stdout.toString().trim();
  final dirty =
      (await Process.run('git', ['diff', '--quiet', 'HEAD'])).exitCode != 0;
  final attempt = 'diagnostic-${DateTime.now().microsecondsSinceEpoch}-$pid';
  PhoneRuntime? runtime;
  _AuditWire? wire;
  SessionOwnership? ownership;
  Map<String, Object?> result = driver.fail('diagnostic_failed');
  final observations = <String, Object?>{};
  String? cleanupCode;
  try {
    runtime = await PhoneRuntime.inspect(runID);
    ownership = SessionOwnership.create(
      File(
        '${driver.evidenceDirectory}/${SessionOwnership.filename(runID, engine, scenario, attempt)}',
      ),
      runID: runID,
      sourceRevision: revision,
      attemptID: attempt,
      engine: engine,
      caseName: scenario,
      appUID: runtime.uid,
      appBuild: runtime.appBuild,
    );
    final endpoint = await runtime.start(
      engine == 'opencode2',
      appManagedOnly: source == 'app-managed',
      forceOwned: source == 'owned',
    );
    wire = _AuditWire(baseUrl: endpoint, password: runtime.password);
    observations['cliVersionMatched'] =
        await runtime.version(engine == 'opencode2') ==
        (engine == 'opencode2' ? '2.0.10' : '1.18.32');
    if (shape == 'app') {
      final location = {'location[directory]': runtime.directory};
      final health = _map(await wire.request('GET', '/api/health'));
      observations['httpVersionMatched'] = health['version'] == '2.0.10';
      await wire.openEvents('/api/event', query: location);
      final requested = driver.argument(args, '--model');
      await waitOc2ModelCatalog(
        wire,
        directory: runtime.directory,
        model: requested,
      );
      Map<String, String>? requestedRef;
      if (requested != null) {
        final split = requested.indexOf('/');
        if (split <= 0 || split == requested.length - 1) {
          throw const ProbeFailure('invalid_model_option');
        }
        requestedRef = {
          'providerID': requested.substring(0, split),
          'id': requested.substring(split + 1),
        };
      }
      final created = _map(
        _map(
          await wire.request(
            'POST',
            '/api/session',
            body: {
              'title': '${ownership.titlePrefix}-session-1',
              'location': {'directory': runtime.directory},
              'model': ?requestedRef,
            },
          ),
        )['data'],
      );
      final id = created['id'];
      if (!ownedSessionID(id)) {
        throw const ProbeFailure('invalid_owned_session');
      }
      await ownership.recordCreated(id as String);
      const expected = 'FQ3B_APP_OK';
      // Api2Gateway ordinary send omits delivery/resume when no queue choice.
      await wire.request(
        'POST',
        '/api/session/$id/prompt',
        body: {'text': 'Reply exactly $expected.'},
      );
      final deadline = DateTime.now().add(const Duration(seconds: 100));
      var complete = false;
      var answer = false;
      while (DateTime.now().isBefore(deadline)) {
        final messages = _list(
          await wire.request(
            'GET',
            '/api/session/$id/message',
            query: {'limit': '200', 'order': 'asc'},
          ),
        );
        final facts = oc2DiagnosticReplyFacts(
          messages,
          sessionID: id,
          expectedToken: expected,
        );
        complete = facts['completedReply'] == true;
        answer = facts['expectedTokenInCompletedReply'] == true;
        if (complete) break;
        await Future<void>.delayed(const Duration(milliseconds: 250));
      }
      observations['completedReply'] = complete;
      observations['expectedTokenInCompletedReply'] = answer;
      result = complete && answer
          ? {
              'state': 'pass',
              'code': 'verified',
              'facts': {'asserted': true},
            }
          : driver.fail('app_prompt_not_completed');
    } else {
      final options = ProbeOptions(
        directory: runtime.directory,
        title: ownership.titlePrefix,
        model: driver.argument(args, '--model'),
        capabilities: {scenario},
        onSessionCreated: ownership.recordCreated,
      );
      final run = engine == 'opencode2'
          ? await runProtocol2(wire, options)
          : await runProtocol1(wire, options);
      result = Map<String, Object?>.from(run.results[scenario] as Map);
    }
    await _collect(
      wire,
      engine,
      ownership.sessionIDs,
      ownership.directory,
      observations,
    );
  } on ProbeFailure catch (error) {
    result = driver.fail(error.code);
  } catch (_) {
    result = driver.fail('diagnostic_failed');
  } finally {
    if (wire != null && ownership != null) {
      try {
        await driver.cleanupLedger(wire, ownership);
      } catch (_) {
        cleanupCode = 'owned_session_cleanup_failed';
      }
    }
    try {
      await wire?.close();
    } catch (_) {
      cleanupCode = 'wire_cleanup_failed';
    }
    try {
      await runtime?.close();
    } catch (_) {
      cleanupCode = 'process_cleanup_failed';
    }
    try {
      await PhoneRuntime.restoreNormalApp();
    } catch (_) {
      cleanupCode = 'app_restore_failed';
    }
  }
  final record = {
    'runID': runID,
    'sourceRevision': revision,
    'workingTreeDirty': dirty,
    'scope': 'development-diagnostic',
    'engine': engine,
    'requestedSource': source,
    'serverKind': runtime?.lastServerKind,
    'testedModel': driver.argument(args, '--model') ?? 'server-default',
    'promptShape': shape,
    'scenario': scenario,
    'appBuild': runtime?.appBuild,
    'appUID': runtime?.uid,
    'result': result,
    'observations': observations,
    'cleanupCode': cleanupCode,
  };
  File('${driver.evidenceDirectory}/$runID-diagnostic.json').writeAsStringSync(
    '${const JsonEncoder.withIndent('  ').convert(record)}\n',
  );
  stdout.writeln(
    'Diagnostic: ${result['state']} (${result['code']}); cleanup=${cleanupCode ?? 'verified'}',
  );
  if (cleanupCode != null) exitCode = 1;
}

class _AuditWire extends Fq3Wire {
  final prompts = <Map<String, dynamic>>[];
  _AuditWire({required super.baseUrl, required super.password});
  @override
  Future<dynamic> request(
    String method,
    String path, {
    Object? body,
    Map<String, String>? query,
  }) {
    if (method == 'POST' &&
        (path.endsWith('/prompt') || path.endsWith('/prompt_async'))) {
      prompts.add(_map(body));
    }
    return super.request(method, path, body: body, query: query);
  }
}

Future<void> _collect(
  _AuditWire wire,
  String engine,
  List<String> ids,
  String directory,
  Map<String, Object?> out,
) async {
  if (ids.isEmpty) return;
  final id = ids.last;
  final oc2 = engine == 'opencode2';
  final messages = _list(
    await wire.request(
      'GET',
      '${oc2 ? '/api' : ''}/session/$id/message',
      query: {'limit': '200', 'order': 'asc', if (!oc2) 'directory': directory},
    ),
  );
  final lastPrompt = wire.prompts.lastOrNull ?? {};
  final promptID = lastPrompt['messageID'];
  final requestedModel = _map(lastPrompt['model']);
  final promptText = oc2
      ? lastPrompt['text'] as String? ?? ''
      : _list(
          lastPrompt['parts'],
        ).map((p) => p['text'] as String? ?? '').join('\n');
  final token = RegExp(r'FQ3[A-Za-z0-9_]+').firstMatch(promptText)?.group(0);
  var completed = 0, ownParts = 0, foreignParts = 0, matchedModels = 0;
  var finalToken = false, otherToken = false;
  var insensitiveToken = false, numericLines = 0, finalLength = 0;
  for (final message in messages) {
    final info = oc2 ? message : _map(message['info']);
    if ((oc2 ? info['type'] : info['role']) != 'assistant') continue;
    final parts = _list(
      message[oc2 ? 'content' : 'parts'],
    ).where((p) => p['type'] == 'text').toList();
    final isFinal =
        _map(info['time'])['completed'] is num &&
        info['error'] == null &&
        (oc2 || info['parentID'] == promptID);
    if (isFinal) completed++;
    if (!oc2 &&
        info['providerID'] == requestedModel['providerID'] &&
        info['modelID'] == requestedModel['modelID']) {
      matchedModels++;
    }
    for (final part in parts) {
      final own = oc2
          ? (!info.containsKey('sessionID') || info['sessionID'] == id)
          : part['sessionID'] == id && part['messageID'] == info['id'];
      if (isFinal) {
        if (own) {
          ownParts++;
        } else {
          foreignParts++;
        }
      }
      final contains =
          token != null && (part['text'] as String? ?? '').contains(token);
      if (isFinal && own) {
        finalToken = finalToken || contains;
        final value = part['text'] as String? ?? '';
        finalLength += value.length;
        insensitiveToken =
            insensitiveToken ||
            (token != null &&
                value.toLowerCase().contains(token.toLowerCase()));
        numericLines += value
            .split('\n')
            .where((line) => RegExp(r'^\s*\d+\s*$').hasMatch(line))
            .length;
      } else {
        otherToken = otherToken || contains;
      }
    }
  }
  out.addAll({
    'messages': messages.length,
    'completedFreshAssistants': completed,
    'ownedFinalTextParts': ownParts,
    'foreignFinalTextParts': foreignParts,
    'requestedModelMatches': matchedModels,
    'expectedTokenInOwnedFinal': finalToken,
    'expectedTokenInOtherParts': otherToken,
    'expectedTokenIgnoringCase': insensitiveToken,
    'followUpNumericLines': numericLines,
    'followUpTextLength': finalLength,
  });
  for (final type in [
    'session.execution.started',
    'session.execution.succeeded',
    'session.execution.failed',
    'session.execution.interrupted',
    'session.text.delta',
    'session.retry.scheduled',
    'session.step.failed',
  ]) {
    out[type.replaceAll('.', '_')] = wire.events
        .where((e) => e['type'] == type && _map(e['data'])['sessionID'] == id)
        .length;
  }
  if (oc2) {
    final active = _map(
      _map(await wire.request('GET', '/api/session/active'))['data'],
    );
    out['activeAtObservation'] = active.containsKey(id);
    out['pendingInbox'] = _list(
      await wire.request('GET', '/api/session/$id/inbox'),
    ).length;
    out['pendingPermissions'] = _list(
      await wire.request('GET', '/api/session/$id/permission'),
    ).length;
    final retryErrors = wire.events
        .where(
          (e) =>
              e['type'] == 'session.retry.scheduled' &&
              _map(e['data'])['sessionID'] == id,
        )
        .map((e) => _map(_map(e['data'])['error']))
        .toList();
    for (final type in [
      'APIError',
      'ProviderAuthError',
      'ProviderModelNotFoundError',
      'TypeValidationError',
      'UnknownError',
    ]) {
      out['retry_$type'] = retryErrors
          .where((e) => e['type'] == type || e['name'] == type)
          .length;
    }
    for (final status in [400, 401, 403, 404, 429, 500, 502, 503]) {
      out['retry_http_$status'] = retryErrors
          .where(
            (e) =>
                e['status'] == status ||
                e['statusCode'] == status ||
                _map(e['data'])['statusCode'] == status,
          )
          .length;
    }
    final session = _map(
      _map(await wire.request('GET', '/api/session/$id'))['data'],
    );
    final model = _map(session['model']).isNotEmpty
        ? _map(session['model'])
        : messages
                  .where((m) => m['type'] == 'assistant')
                  .map((m) => _map(m['model']))
                  .firstOrNull ??
              {};
    out['selectedOpenCodeProvider'] = model['providerID'] == 'opencode';
    final modelID = model['id'];
    if (model['providerID'] == 'opencode' &&
        modelID is String &&
        RegExp(r'^[A-Za-z0-9._/-]{1,128}$').stringMatch(modelID) == modelID) {
      out['publicOpenCodeModelID'] = modelID;
    }
  }
}
