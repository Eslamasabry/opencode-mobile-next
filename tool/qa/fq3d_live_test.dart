// Explicit live proof; excluded from the ordinary test/ suite.
// Run via tool/qa/fq3d_run.py through machine_lock, using pinned Flutter.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/sse2.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';

import 'fq3d/proof.dart';

class _RealHttp extends HttpOverrides {}

class _Stop implements Exception {
  final String reason;
  const _Stop(this.reason);
}

const _png =
    'iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAK0lEQVR4nO3N'
    'sQkAAAzDsPz/dHpDpi4CjwalydS4d90BAAAAAAAAAADAG3A2h/wuHtDxTwAAAABJRU5ErkJggg==';

String _publicRef(Api2ModelRef ref) {
  final value = '${ref.providerID}/${ref.id}';
  return RegExp(r'^[a-zA-Z0-9_.-]{1,48}/[a-zA-Z0-9_.-]{1,100}$').hasMatch(value)
      ? value
      : 'unpublishable_model_id';
}

class _Execution {
  bool started = false;
  bool succeeded = false;
  bool failed = false;
  bool auth = false;
  int retries = 0;
  final statuses = <int>{};
  final errorTypes = <String>{};
  final routeFailureKinds = <String>{};

  void record(Api2StructuredError? error) {
    auth |= providerAuthFailure(error);
    if (const {
      'provider.auth',
      'provider.no-route',
      'provider.unknown',
      'provider.internal',
      'provider.transport',
      'provider.quota',
      'provider.rate-limit',
      'provider.invalid-request',
      'provider.unsupported-operation',
      'provider.invalid-output',
      'provider.content-filter',
      'permission.rejected',
      'tool.execution',
      'unknown',
      'aborted',
    }.contains(error?.type)) {
      errorTypes.add(error!.type!);
    }
    final message = error?.message;
    if (error?.type == 'provider.no-route' && message != null) {
      if (message.startsWith('Model unavailable: ')) {
        routeFailureKinds.add('model_unavailable');
      }
      if (message.startsWith('Unsupported package for ')) {
        routeFailureKinds.add('unsupported_model_package');
      }
      if (message.startsWith('Variant unavailable for ')) {
        routeFailureKinds.add('variant_unavailable');
      }
      if (message.startsWith('Cannot initialize ')) {
        routeFailureKinds.add(
          message.endsWith('required to resolve the provider endpoint')
              ? 'unresolved_provider_endpoint_variables'
              : 'model_initialization_failed',
        );
      }
    }
    final status = error?.status;
    if (status != null && status >= 100 && status <= 599) statuses.add(status);
    // Stable errors can wrap status in data. Persist only bounded integers;
    // neither error text nor the wrapper is ever exported.
    final details = error?.raw['data'];
    for (final fields in [error?.raw, if (details is Map) details]) {
      for (final key in ['status', 'statusCode']) {
        final code = fields?[key];
        if (code is int && code >= 100 && code <= 599) statuses.add(code);
      }
    }
    if (const {
      'HTTP 503',
      'HTTP 503 Service Unavailable',
      'HTTP/1.1 503 Service Unavailable',
      'HTTP/2 503 Service Unavailable',
    }.contains(error?.message)) {
      statuses.add(503);
    }
  }
}

void main() {
  test(
    'FQ3d real app client: model switch and image causes on PC',
    () async {
      await HttpOverrides.runZoned(
        _run,
        createHttpClient: (_) => _RealHttp().createHttpClient(null),
      );
    },
    skip: Platform.environment['FQ3D_LIVE'] != '1',
    timeout: const Timeout(Duration(minutes: 9)),
  );
}

Future<void> _run() async {
  final previousLogSink = PerfTrace.logSink;
  PerfTrace.logSink = null;
  final run = DateTime.now().toUtc().microsecondsSinceEpoch.toString();
  final evidence = <String, Object?>{
    'schemaVersion': 1,
    'run': run,
    'platform': 'PC Linux; no emulator',
    'expectedVersion': '2.0.10',
    'client': 'lib/api2/Api2Client',
    'cases': <String, Object?>{},
  };
  final cases = evidence['cases']! as Map<String, Object?>;
  final owned = <String>[];
  final ownedTitles = <String, String>{};
  String? ownedDirectory;
  final states = <String, _Execution>{};
  Api2Client? client;
  Api2EventStream? stream;
  final sessionLogs = <Api2SessionLogStream>[];
  var stage = 'preflight';
  var cleanupOK = true;
  try {
    final environment = Platform.environment;
    final secret = environment['FQ3D_PASSWORD'];
    final directory = environment['FQ3D_DIRECTORY'];
    final pid = int.tryParse(environment['FQ3D_PID'] ?? '');
    final hash = environment['FQ3D_BINARY_SHA256'];
    if (secret == null ||
        directory == null ||
        pid == null ||
        hash == null ||
        !RegExp(r'^[A-Za-z0-9_-]{43}$').hasMatch(secret) ||
        !RegExp(r'^[a-f0-9]{64}$').hasMatch(hash) ||
        !Directory(directory).existsSync() ||
        !File('/proc/$pid/status').existsSync()) {
      throw const _Stop('owned_launcher_handoff_missing');
    }
    ownedDirectory = directory;
    evidence['ownedServerPID'] = pid;
    evidence['binarySha256'] = hash;
    // The launcher holds the generated password in memory and passes it only
    // in this child environment. It alone owns/stops the server process.
    client = Api2Client.connect(
      baseUrl: 'http://127.0.0.1:4097',
      password: secret,
      directory: directory,
    );
    client.transport.dio.options.receiveTimeout = const Duration(seconds: 12);
    stage = 'health';
    final health = await client.health();
    evidence['observedVersion'] = health.version;
    if (!health.healthy || health.version != '2.0.10') {
      throw const _Stop('owned_server_health_mismatch');
    }
    stage = 'catalog';
    var catalog = await client.models();
    final catalogWait = Stopwatch()..start();
    while (catalogWait.elapsed < const Duration(seconds: 25) &&
        !catalog.any(
          (m) => m.providerID == 'opencode' && catalogRef(m).id == 'big-pickle',
        )) {
      await Future<void>.delayed(const Duration(milliseconds: 500));
      catalog = await client.models();
    }
    final opencode = catalog.where((m) => m.providerID == 'opencode').toList();
    evidence['publicModels'] = [
      for (final row in opencode)
        {
          'model': _publicRef(catalogRef(row)),
          'catalogID': RegExp(r'^[a-zA-Z0-9_./-]{1,120}$').hasMatch(row.id)
              ? row.id
              : 'unpublishable_model_id',
          'enabled': row.enabled,
          'vision': row.capabilities.input.any((s) => s.startsWith('image')),
        },
    ];
    final wantedA = environment['FQ3D_MODEL_A'] ?? 'opencode/big-pickle';
    final wantedB = environment['FQ3D_MODEL_B'] ?? 'opencode/exo-free';
    evidence['recordedExoFreeInCatalog'] = opencode.any(
      (m) => catalogRef(m).id == 'exo-free',
    );
    final a = opencode
        .where((m) => _publicRef(catalogRef(m)) == wantedA)
        .firstOrNull;
    final b = opencode
        .where((m) => _publicRef(catalogRef(m)) == wantedB)
        .firstOrNull;
    if (a == null || b == null || !a.enabled || !b.enabled) {
      throw const _Stop('requested_models_missing_or_disabled');
    }
    final connected = Completer<void>();
    final streamFacts = <String, int>{
      'envelopes': 0,
      'execution': 0,
      'ownedSessionExecution': 0,
      'ownedDirectoryExecution': 0,
    };
    evidence['streamDiagnostics'] = streamFacts;
    stream = Api2EventStream(
      transport: client.transport,
      queryBuilder: () => {'location[directory]': directory},
      onStatus: (s) {
        if (s == Api2StreamStatus.connected && !connected.isCompleted) {
          connected.complete();
        }
      },
      onError: (_) {},
      onEvent: (envelope, _) {
        streamFacts['envelopes'] = streamFacts['envelopes']! + 1;
        if (envelope.event is Api2SessionExecutionEvent) {
          final execution = envelope.event as Api2SessionExecutionEvent;
          streamFacts['execution'] = streamFacts['execution']! + 1;
          if (states.containsKey(execution.sessionID)) {
            streamFacts['ownedSessionExecution'] =
                streamFacts['ownedSessionExecution']! + 1;
          }
          if (envelope.location?.directory == ownedDirectory) {
            streamFacts['ownedDirectoryExecution'] =
                streamFacts['ownedDirectoryExecution']! + 1;
          }
        }
        final event = envelope.event;
        final ownedID = switch (event) {
          Api2SessionExecutionEvent() => event.sessionID,
          Api2SessionRetryScheduledEvent() => event.sessionID,
          _ => null,
        };
        if (ownedID == null ||
            !states.containsKey(ownedID) ||
            !ownedEventScope(
              envelope,
              sessionID: ownedID,
              directory: directory,
            )) {
          return;
        }
        if (event is Api2SessionExecutionEvent) {
          final state = states[event.sessionID];
          if (state == null) return;
          if (event.phase == Api2Phase.started) state.started = true;
          if (!state.started) return;
          state.succeeded |= event.phase == Api2Phase.succeeded;
          state.failed |=
              event.phase == Api2Phase.failed ||
              event.phase == Api2Phase.interrupted;
          state.record(event.error);
        } else if (event is Api2SessionRetryScheduledEvent) {
          final state = states[event.sessionID];
          if (state == null || !state.started) return;
          state.retries++;
          state.record(event.error);
        }
      },
    )..start();
    await connected.future.timeout(const Duration(seconds: 15));

    Future<String> create(String scenario, Api2ModelRef model) async {
      final title = 'fq3d-$run-$scenario';
      final session = await client!.createSession(title: title, model: model);
      if (!RegExp(r'^ses_[A-Za-z0-9_-]+$').hasMatch(session.id) ||
          session.title != title ||
          session.directory != directory) {
        throw const _Stop('created_session_ownership_mismatch');
      }
      owned.add(session.id);
      ownedTitles[session.id] = title;
      return session.id;
    }

    Future<Map<String, Object?>> send(
      String session,
      Api2ModelRef selected,
      String prompt, {
      required String caseName,
      bool image = false,
    }) async {
      final facts = <String, Object?>{
        'requestedModel': _publicRef(selected),
        'stage': 'selection',
      };
      cases[caseName] = facts;
      facts['selectedModelInCatalogBefore'] = (await client!.models()).any(
        (m) => m.enabled && sameModel(catalogRef(m), selected),
      );
      final old = (await client!.messages(
        session,
      )).data.map((m) => m.id).toSet();
      await client!.switchModel(session, selected);
      facts['selectionRetained'] = sameModel(
        (await client!.session(session)).model,
        selected,
      );
      if (facts['selectionRetained'] != true) {
        facts['result'] = 'selection_mismatch';
        return facts;
      }
      facts['stage'] = 'prompt_admission';
      final state = _Execution();
      states[session] = state;
      // The global envelope's location is optional. Audit execution through
      // its exact session log route, then refetch HTTP messages for content.
      // Wait for initial log synchronization before arming this new turn, so
      // a model-A terminal replay cannot certify the following model-B send.
      final synced = Completer<void>();
      var armed = false;
      final log = Api2SessionLogStream(
        transport: client!.transport,
        sessionID: session,
        onStatus: (_) {},
        onError: (_) {},
        onSynced: (_) {
          if (!synced.isCompleted) synced.complete();
        },
        onEvent: (envelope) {
          if (!armed) return;
          final event = envelope.event;
          if (event is Api2SessionExecutionEvent &&
              event.sessionID == session) {
            state.started |= event.phase == Api2Phase.started;
            state.succeeded |= event.phase == Api2Phase.succeeded;
            state.failed |=
                event.phase == Api2Phase.failed ||
                event.phase == Api2Phase.interrupted;
            state.record(event.error);
          } else if (event is Api2SessionRetryScheduledEvent &&
              event.sessionID == session) {
            state.record(event.error);
          }
        },
      )..start();
      sessionLogs.add(log);
      await synced.future.timeout(const Duration(seconds: 15));
      armed = true;
      final bytes = base64Decode(_png);
      final receipt = await client!.prompt(
        session,
        text: prompt,
        files: image
            ? [Api2Client.inlineAttachment(bytes, mime: 'image/png')]
            : const [],
      );
      facts['receiptObserved'] = true;
      facts['stage'] = 'inference';
      Api2AssistantMessage? reply;
      final timer = Stopwatch()..start();
      while (timer.elapsed < const Duration(seconds: 70)) {
        final messages = (await client!.messages(session)).data;
        for (final message in messages.whereType<Api2AssistantMessage>()) {
          if (old.contains(message.id)) continue;
          reply = message;
          state.record(message.error);
        }
        if (state.auth || state.failed || state.succeeded) break;
        await Future<void>.delayed(const Duration(milliseconds: 600));
      }
      // Refetch once at the terminal boundary; streams are volatile.
      final messages = (await client!.messages(session)).data;
      for (final message in messages.whereType<Api2AssistantMessage>()) {
        if (!old.contains(message.id)) reply = message;
      }
      state.record(reply?.error);
      facts['selectedModelInCatalogAfter'] = (await client!.models()).any(
        (m) => m.enabled && sameModel(catalogRef(m), selected),
      );
      final user = messages
          .whereType<Api2UserMessage>()
          .where((m) => m.id == receipt.id)
          .firstOrNull;
      if (image) {
        facts['imageBytesRetained'] = user != null && retainedPng(user, bytes);
      }
      facts['elapsedMs'] = timer.elapsedMilliseconds;
      facts['retryCount'] = state.retries;
      facts['retryOrErrorStatuses'] = state.statuses.toList()..sort();
      facts['errorTypes'] = state.errorTypes.toList()..sort();
      facts['routeFailureKinds'] = state.routeFailureKinds.toList()..sort();
      facts['executionSucceeded'] = state.succeeded;
      facts['executionFailed'] = state.failed;
      facts['providerAuthFailed'] = state.auth;
      facts['assistantCompleted'] = reply?.completed ?? false;
      facts['answeredModel'] = reply?.model == null
          ? null
          : _publicRef(reply!.model!);
      if (!image) facts['requestedTokenMatched'] = reply?.text.trim() == 'pong';
      facts['answerVerified'] =
          !state.failed &&
          reply != null &&
          successfulAssistant(
            reply,
            selected,
            previousIDs: old,
            executionSucceeded: state.succeeded,
          ) &&
          (image
              ? imageAnswerMatches(reply.text)
              : reply.text.trim().isNotEmpty);
      facts['result'] = state.auth
          ? 'provider_auth_failed'
          : facts['answerVerified'] == true &&
                (!image || facts['imageBytesRetained'] == true)
          ? 'pass'
          : state.failed
          ? 'execution_failed'
          : !state.succeeded
          ? 'inference_timeout'
          : 'answer_proof_failed';
      if (facts['result'] != 'pass') {
        try {
          await client!.interrupt(session);
          facts['interruptCompleted'] = true;
        } catch (_) {
          facts['interruptCompleted'] = false;
        }
      }
      await log.dispose();
      return facts;
    }

    stage = 'model_a';
    final session = await create('model-switch', catalogRef(a));
    final first = await send(
      session,
      catalogRef(a),
      'Reply with exactly: pong',
      caseName: 'modelA',
    );
    cases['modelA'] = first;
    if (first['providerAuthFailed'] == true) {
      throw const _Stop('model_a_needs_provider_auth');
    }
    if (first['result'] != 'pass') {
      throw const _Stop('model_a_inference_failed');
    }
    stage = 'model_b';
    final second = await send(
      session,
      catalogRef(b),
      'Reply with exactly: pong',
      caseName: 'modelSwitch',
    );
    cases['modelSwitch'] = second;
    if (second['providerAuthFailed'] == true) {
      throw const _Stop('model_b_needs_provider_auth');
    }
    stage = 'image';
    if (!b.capabilities.input.any((s) => s.startsWith('image'))) {
      throw const _Stop('recorded_image_model_not_vision');
    }
    final imageSession = await create('image', catalogRef(b));
    final image = await send(
      imageSession,
      catalogRef(b),
      'Name the colors of the left and right panels of the attached image. '
      'Reply only JSON with keys left and right and lowercase color values.',
      caseName: 'image',
      image: true,
    );
    cases['image'] = image;
    if (image['providerAuthFailed'] == true) {
      throw const _Stop('image_needs_provider_auth');
    }
    evidence['runCompleted'] = true;
  } catch (error) {
    evidence['stoppedAt'] = stage;
    evidence['stopReason'] = switch (error) {
      _Stop() => error.reason,
      TimeoutException() => 'bounded_wait_timeout',
      Api2AuthRequired() => 'server_basic_auth_failed',
      Api2Error() => 'api_request_failed',
      _ => 'probe_failed',
    };
    if (error is Api2Error) evidence['httpStatus'] = error.statusCode;
  } finally {
    for (final log in sessionLogs) {
      try {
        await log.dispose();
      } catch (_) {
        cleanupOK = false;
      }
    }
    for (final id in owned.reversed) {
      try {
        final session = await client!.session(id);
        if (session.title != ownedTitles[id] ||
            session.directory != ownedDirectory) {
          cleanupOK = false;
          continue;
        }
      } catch (_) {
        cleanupOK = false;
        continue;
      }
      try {
        await client.interrupt(id);
      } catch (_) {
        cleanupOK = false;
      }
      try {
        await client.deleteSession(id);
      } catch (_) {
        cleanupOK = false;
      }
    }
    try {
      await stream?.dispose();
    } catch (_) {
      cleanupOK = false;
    }
    try {
      client?.transport.close();
    } catch (_) {
      cleanupOK = false;
    }
    PerfTrace.logSink = previousLogSink;
    evidence['ownedSessionCount'] = owned.length;
    evidence['cleanupOK'] = cleanupOK;
    final output = File('docs/qa/FQ3d-2026-10-08/pc-client-proof.json');
    await output.writeAsString(
      '${const JsonEncoder.withIndent('  ').convert(evidence)}\n',
    );
  }
  expect(cleanupOK, isTrue, reason: 'Owned resources must be cleaned up');
  expect(
    evidence['observedVersion'],
    '2.0.10',
    reason: 'Owned server proof prerequisite',
  );
  // Capability failures remain explicit results; this test asserts probe safety.
}
