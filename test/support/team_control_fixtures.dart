import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/client/http.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

const profileId = 'srv-1';

/// Keystore double: nothing is written by this bead, so an empty map.
class MemorySecureStorage extends FlutterSecureStorage {
  MemorySecureStorage();

  final values = <String, String>{};

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => values[key];

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    values.remove(key);
  }
}

/// One recorded control call.
class Call {
  const Call(this.verb, this.target, this.requestId, {this.arg});

  final String verb;
  final String target;
  final String requestId;
  final Object? arg;

  @override
  String toString() => '$verb $target $requestId';
}

/// A gateway with every control on, empty reads, a pushable event stream
/// and a scripted answer per control call. [answer] sees the request id
/// so a test can check what the store holds at send time.
class ScriptedGateway implements OrchestrationGateway {
  ScriptedGateway({this.capabilities = OrchestrationCapabilities.fixture});

  @override
  final OrchestrationCapabilities capabilities;
  final gateList = <OrchestrationGate>[];
  final agentList = <OrchestrationAgent>[];
  final calls = <Call>[];
  final stream = StreamController<OrchestrationEvent>.broadcast();
  Future<MutationReceipt> Function(Call call)? answer;
  bool _closed = false;

  int get callCount => calls.length;

  @override
  OrchestrationHostIdentity? get host => const OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://127.0.0.1:8373',
    hostMode: OrchestrationHostMode.computer,
  );

  @override
  bool get isClosed => _closed;

  @override
  Future<void> close() async {
    _closed = true;
    await stream.close();
  }

  void push(OrchestrationEvent event) => stream.add(event);

  Future<MutationReceipt> _call(Call call) {
    calls.add(call);
    final script = answer;
    if (script == null) {
      return Future.value(
        MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.accepted,
          upstreamStatus: 202,
        ),
      );
    }
    return script(call);
  }

  @override
  Future<List<OrchestrationProject>> projects() async => const [];
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => const [];
  @override
  Future<OrchestrationRun?> run(String id) async => null;
  int workReads = 0;
  @override
  Future<List<WorkItem>> work({String? projectId}) async {
    workReads += 1;
    return const [];
  }

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => const [];
  @override
  Future<WorkItem?> workItem(String id) async => null;
  int agentsReads = 0;
  @override
  Future<List<OrchestrationAgent>> agents() async {
    agentsReads += 1;
    return agentList;
  }

  @override
  Future<OrchestrationAgent?> agent(String id) async {
    for (final a in agentList) {
      if (a.id == id) return a;
    }
    return null;
  }

  @override
  Future<List<OrchestrationGate>> gates() async => gateList;
  @override
  Future<OrchestrationUsage?> usage() async => null;
  @override
  Future<List<ActivityEvent>> activity({
    int? afterSeq,
    int limit = 100,
  }) async => const [];

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => _call(Call('respond', gateId, requestId, arg: response));

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => _call(Call('message', agentId, requestId, arg: text));

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => _call(Call('controlAgent', agentId, requestId, arg: action));

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => _call(Call('cancelRun', runId, requestId));

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _call(Call('assign', workId, requestId, arg: agentId));

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => _call(Call('createWork', title, requestId, arg: projectId));
}

/// [ScriptedGateway] with the merge roles (TEAM-205): readiness from a
/// field, approve / merge through the same scripted answer.
class MergeGateway extends ScriptedGateway
    implements OrchestrationMergeGateway {
  MergeGateway({super.capabilities});

  MergeReadiness? readiness;
  int readinessReads = 0;

  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async {
    readinessReads += 1;
    return readiness;
  }

  @override
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  }) => _call(Call('approveMerge', mergeRequestId, requestId));

  @override
  Future<MutationReceipt> merge(String runId, {required String requestId}) =>
      _call(Call('merge', runId, requestId));
}

OrchestrationHttpResponse frontProblem(int status, String code) =>
    OrchestrationHttpResponse(
      statusCode: status,
      requestId: 'front-1',
      body: {
        'type': 'urn:opencode-mobile:front:$code',
        'title': 'Problem',
        'status': status,
        'detail': 'detail for $code',
        'code': code,
      },
    );

late SharedPreferences prefs;

late OrchestrationStore store;

late DateTime clock;

var nextKey = 0;

OrchestrationConfig frontConfig() => OrchestrationConfig(
  provider: OrchestrationProvider.gascity,
  url: 'http://127.0.0.1:8373',
  city: 'bright-lights',
  front: true,
  enabledAt: DateTime.utc(2026, 9, 10),
);

ServerProfile profile() => ServerProfile(
  id: profileId,
  name: 'Workstation',
  baseUrl: 'https://server.example:4096',
  orchestration: frontConfig(),
);

String storedKey(String key) => OrchestrationStore.mutationKey(profileId, key);

Map<String, Object?>? stored(String key) {
  final raw = prefs.getString(storedKey(key));
  return raw == null ? null : jsonDecode(raw) as Map<String, Object?>;
}

/// A controller over [gateway], started, with a short result window.
Future<OrchestrationController> boot(
  ScriptedGateway gateway, {
  Duration timeout = const Duration(milliseconds: 150),
}) async {
  final controller = OrchestrationController(
    profile: profile(),
    config: frontConfig(),
    store: store,
    probe: (_) async => ProbeFound(
      host: gateway.host!,
      city: 'bright-lights',
      front: true,
      identityAllowed: true,
      capabilities: gateway.capabilities,
    ),
    gatewayFactory: (_, _) => gateway,
    now: () => clock,
    mintKey: () => 'key-${++nextKey}',
    refreshDebounce: const Duration(milliseconds: 10),
    mutationTimeout: timeout,
  );
  addTearDown(controller.dispose);
  await controller.start();
  expect(controller.phase, OrchestrationPhase.ready);
  return controller;
}

Future<void> settle() => Future<void>.delayed(const Duration(milliseconds: 20));
