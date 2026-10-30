// TEAM-207: the host's supervision policy on the phone (02-ux §7). The
// model decodes the front's `/front/policy` document leniently; the Gas
// City control adapter reads the route (with `?rig=`) and answers null
// for a front without it or a bare supervisor; the controller caches the
// policy with the projects scope and drops it on stop; Task details
// (slice-P3.5: the retired run Overview's facts) shows the read-only
// "Supervision · …" line and boundary chips after the steps, above its
// Technical details fold; the Start-a-run sheet
// shows the Boundaries row; both are absent (no widget) when the gateway has no
// policy side. Also 320 dp / 2.5× LTR + RTL.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitChip, KitText;
import 'package:opencode_mobile/ui/screens/team/policy_block.dart';
import 'package:opencode_mobile/ui/screens/team/task_details_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/start_run_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/part_semantics.dart';

const _runId = 'oc-xru';

const _policyJson = {
  'rig': 'ocproof',
  'supervision': 'high',
  'boundaries': [
    {'key': 'require_approval', 'text': 'Never merge without approval'},
    {'key': 'require_tests', 'text': 'Require tests before merge'},
    {'key': 'extra-1', 'text': 'Never touch production'},
  ],
};

/// A front-like gateway without a policy side (an older front, or the
/// fixture): reads from lists, every control accepted.
class _Gateway implements OrchestrationGateway {
  final runList = <OrchestrationRun>[];
  final workList = <WorkItem>[];
  final agentList = <OrchestrationAgent>[];
  final projectList = <OrchestrationProject>[];
  final stream = StreamController<OrchestrationEvent>.broadcast();
  bool _closed = false;

  @override
  OrchestrationCapabilities get capabilities =>
      OrchestrationCapabilities.gascityFront;

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

  @override
  Future<List<OrchestrationProject>> projects() async => projectList;
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => runList;
  @override
  Future<OrchestrationRun?> run(String id) async {
    for (final run in runList) {
      if (run.id == id) return run;
    }
    return null;
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) async => workList;
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => const [];
  @override
  Future<WorkItem?> workItem(String id) async => null;
  @override
  Future<List<OrchestrationAgent>> agents() async => agentList;
  @override
  Future<OrchestrationAgent?> agent(String id) async {
    for (final agent in agentList) {
      if (agent.id == id) return agent;
    }
    return null;
  }

  @override
  Future<List<OrchestrationGate>> gates() async => const [];
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

  Future<MutationReceipt> _ok(String requestId) async => MutationReceipt(
    id: requestId,
    status: MutationReceiptStatus.accepted,
    upstreamStatus: 202,
  );

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => _ok(requestId);
  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => _ok(requestId);
  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => _ok(requestId);
  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => _ok(requestId);
  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _ok(requestId);

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => _ok(requestId);
}

/// The same gateway with a policy side: what a front with the route is.
class _PolicyGateway extends _Gateway implements OrchestrationPolicyGateway {
  OrchestrationPolicy? policyDoc = OrchestrationPolicy.fromJson(_policyJson);
  Object? policyError;
  int policyReads = 0;
  final policyRigs = <String?>[];

  @override
  Future<OrchestrationPolicy?> policy({String? projectId}) async {
    policyReads += 1;
    policyRigs.add(projectId);
    final error = policyError;
    if (error != null) throw error;
    return policyDoc;
  }
}

class _RealHttpOverrides extends HttpOverrides {}

/// An in-process front answering `/front/policy` for the route test.
class _PolicyHost {
  _PolicyHost._(this.server);

  final HttpServer server;
  final requests = <String>[];
  bool hasRoute = true;

  String get url => 'http://127.0.0.1:${server.port}';

  static Future<_PolicyHost> start() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final host = _PolicyHost._(server);
    server.listen(host._handle);
    return host;
  }

  Future<void> close() => server.close(force: true);

  Future<void> _handle(HttpRequest request) async {
    final uri = request.uri;
    requests.add(
      '${request.method} ${uri.path}${uri.hasQuery ? '?' : ''}'
      '${uri.query}',
    );
    await request.drain<void>();
    Object? body;
    var status = 200;
    var contentType = ContentType.json;
    if (uri.path == '/v0/city/bright-lights/front/policy' && hasRoute) {
      final rig = uri.queryParameters['rig'];
      if (rig == '../x') {
        status = 422;
        contentType = ContentType('application', 'problem+json');
        body = {
          'type': 'urn:opencode-mobile:front:rig-unusable',
          'title': 'Unprocessable',
          'status': 422,
          'code': 'rig-unusable',
        };
      } else {
        body = {
          ..._policyJson,
          'rig': ?rig,
          if (rig == 'other') 'supervision': 'balanced',
          if (rig == 'other') 'boundaries': <Object?>[],
        };
      }
    } else {
      status = 404;
      contentType = ContentType('application', 'problem+json');
      body = {
        'type': 'urn:opencode-mobile:front:not-found',
        'title': 'Not Found',
        'status': 404,
      };
    }
    request.response
      ..statusCode = status
      ..headers.contentType = contentType
      ..headers.set('X-GC-Request-Id', 'front-${requests.length}')
      ..write(jsonEncode(body));
    await request.response.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late OrchestrationStore store;
  late DateTime clock;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
    clock = DateTime.utc(2026, 9, 11, 12, 30);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  OrchestrationRun run() => OrchestrationRun(
    id: _runId,
    title: 'Offline-first sessions',
    state: RunState.working,
    rawState: 'open',
    kind: RunKind.batch,
    stepCount: 2,
    completedSteps: 1,
    startedAt: clock.subtract(const Duration(hours: 3)),
    updatedAt: clock,
    raw: const {'id': _runId, 'issue_type': 'convoy'},
  );

  OrchestrationAgent mayor() => const OrchestrationAgent(
    id: 'gastown.mayor',
    name: 'gastown.mayor',
    state: AgentState.idle,
    rawState: 'idle',
    sessionId: 'bl-8jc',
    pack: 'gastown',
    raw: {'name': 'gastown.mayor', 'suspended': false},
  );

  Future<OrchestrationController> boot(_Gateway gateway) async {
    gateway
      ..runList.add(run())
      ..agentList.add(mayor())
      ..projectList.add(
        const OrchestrationProject(
          id: 'ocproof',
          name: 'ocproof',
          rig: 'ocproof',
        ),
      )
      ..workList.add(
        const WorkItem(
          id: 'w1',
          title: 'Storage layer',
          state: WorkState.working,
          runId: _runId,
        ),
      );
    final config = OrchestrationConfig(
      provider: OrchestrationProvider.gascity,
      url: 'http://127.0.0.1:8373',
      city: 'bright-lights',
      front: true,
      enabledAt: DateTime.utc(2026, 9, 10),
    );
    final controller = OrchestrationController(
      profile: ServerProfile(
        id: 'srv-1',
        name: 'Workstation',
        baseUrl: 'https://server.example:4096',
        orchestration: config,
      ),
      config: config,
      store: store,
      gatewayFactory: (_, _) => gateway,
      probe: (_) async => ProbeFound(
        host: gateway.host!,
        front: true,
        identityAllowed: true,
        capabilities: OrchestrationCapabilities.gascityFront,
      ),
      now: () => clock,
      refreshDebounce: const Duration(milliseconds: 10),
    );
    addTearDown(controller.dispose);
    await controller.start();
    expect(controller.phase, OrchestrationPhase.ready);
    return controller;
  }

  Widget app(
    Widget home, {
    Locale locale = const Locale('en'),
    double scale = 1,
  }) => MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
      child: child!,
    ),
    home: home,
  );

  Future<void> size(WidgetTester tester, Size size, {double ratio = 1}) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = ratio;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  Future<void> pumpRun(
    WidgetTester tester,
    OrchestrationController controller, {
    Locale locale = const Locale('en'),
    double scale = 1,
  }) async {
    await tester.pumpWidget(
      app(
        Scaffold(
          body: SingleChildScrollView(
            child: TeamTaskDetails(
              controller: controller,
              runId: _runId,
              now: () => clock,
            ),
          ),
        ),
        locale: locale,
        scale: scale,
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> pumpSheet(
    WidgetTester tester,
    OrchestrationController controller, {
    Locale locale = const Locale('en'),
    double scale = 1,
  }) async {
    await tester.pumpWidget(
      app(
        Scaffold(
          body: SingleChildScrollView(
            child: StartRunSheet(controller: controller),
          ),
        ),
        locale: locale,
        scale: scale,
      ),
    );
    await tester.pumpAndSettle();
  }

  Finder key(String name) => find.byKey(ValueKey(name));

  // The words of a Text or a KitText.
  String? textOf(WidgetTester tester, Finder finder) =>
      switch (tester.widget(finder)) {
        final Text text => text.data,
        final KitText text => text.text,
        final other => fail('not text: $other'),
      };

  group('model', () {
    test('decodes the front document leniently', () {
      final policy = OrchestrationPolicy.fromJson(_policyJson)!;
      expect(policy.rig, 'ocproof');
      expect(policy.supervision, OrchestrationSupervision.high);
      expect(policy.boundaries.map((b) => b.key), [
        'require_approval',
        'require_tests',
        'extra-1',
      ]);
      expect(policy.boundaries.first.text, 'Never merge without approval');
      expect(policy.raw, _policyJson);
      expect(policy.toJson(), {
        'rig': 'ocproof',
        'supervision': 'high',
        'boundaries': _policyJson['boundaries'],
      });
    });

    test('unknown or missing supervision is balanced; odd entries are '
        'dropped; non-policy JSON is null', () {
      expect(
        OrchestrationSupervision.parse('AUTONOMOUS'),
        OrchestrationSupervision.autonomous,
      );
      expect(
        OrchestrationSupervision.parse('yolo'),
        OrchestrationSupervision.balanced,
      );
      expect(
        OrchestrationSupervision.parse(null),
        OrchestrationSupervision.balanced,
      );
      final policy = OrchestrationPolicy.fromJson({
        'boundaries': [
          {'key': 'require_tests'},
          {'text': 'no key'},
          'junk',
          {'key': '  ', 'text': 'blank key'},
        ],
      })!;
      expect(policy.rig, isNull);
      expect(policy.supervision, OrchestrationSupervision.balanced);
      expect(policy.boundaries, [
        const PolicyBoundary(key: 'require_tests', text: 'require_tests'),
      ]);
      expect(OrchestrationPolicy.fromJson({'rig': 'x'}), isNull);
      expect(OrchestrationPolicy.fromJson('nope'), isNull);
      expect(OrchestrationPolicy.fromJson(null), isNull);
    });
  });

  group('GasCityControl route', () {
    // The widgets binding stubs HttpClient (every request answers 400);
    // this test talks to an in-process server, so it restores real I/O.
    setUp(() => HttpOverrides.global = _RealHttpOverrides());
    tearDown(() => HttpOverrides.global = null);

    test('GET /front/policy with and without ?rig=; 404 and 422 answer '
        'null; a bare supervisor never asks', () async {
      final host = await _PolicyHost.start();
      addTearDown(host.close);
      final gateway = GasCityGateway(
        url: host.url,
        city: 'bright-lights',
        front: true,
      );
      addTearDown(gateway.close);
      expect(gateway, isA<OrchestrationPolicyGateway>());

      final policy = await gateway.policy();
      expect(policy?.rig, 'ocproof');
      expect(policy?.supervision, OrchestrationSupervision.high);
      expect(policy?.boundaries, hasLength(3));
      expect(host.requests.last, 'GET /v0/city/bright-lights/front/policy');

      final other = await gateway.policy(projectId: 'other');
      expect(other?.rig, 'other');
      expect(other?.supervision, OrchestrationSupervision.balanced);
      expect(other?.boundaries, isEmpty);
      expect(
        host.requests.last,
        'GET /v0/city/bright-lights/front/policy?rig=other',
      );

      expect(await gateway.policy(projectId: '../x'), isNull);

      host.hasRoute = false;
      expect(await gateway.policy(), isNull);

      final before = host.requests.length;
      final bare = GasCityGateway(url: host.url, city: 'bright-lights');
      addTearDown(bare.close);
      expect(await bare.policy(), isNull);
      expect(host.requests.length, before);
    });
  });

  group('controller', () {
    test('caches the policy with the projects scope, refreshes it, drops '
        'it on stop', () async {
      final gateway = _PolicyGateway();
      final controller = await boot(gateway);
      expect(controller.policy?.supervision, OrchestrationSupervision.high);
      expect(controller.policy?.boundaries, hasLength(3));
      expect(gateway.policyReads, 1);
      expect(gateway.policyRigs, [null]);

      gateway.policyDoc = const OrchestrationPolicy(
        rig: 'ocproof',
        supervision: OrchestrationSupervision.autonomous,
      );
      await controller.refresh();
      expect(gateway.policyReads, 2);
      expect(
        controller.policy?.supervision,
        OrchestrationSupervision.autonomous,
      );
      expect(controller.policy?.boundaries, isEmpty);

      await controller.stop();
      expect(controller.policy, isNull);
    });

    test('a failing policy read keeps the previous document and marks the '
        'refresh as a read failure; a null answer clears it', () async {
      final gateway = _PolicyGateway();
      final controller = await boot(gateway);
      expect(controller.policy, isNotNull);

      gateway.policyError = StateError('front down');
      await controller.refresh();
      expect(controller.policy?.supervision, OrchestrationSupervision.high);
      expect(controller.lastError?.kind, OrchestrationErrorKind.readFailed);

      gateway
        ..policyError = null
        ..policyDoc = null;
      await controller.refresh();
      expect(controller.policy, isNull);
    });

    test('null without a policy side on the gateway', () async {
      final controller = await boot(_Gateway());
      expect(controller.policy, isNull);
      await controller.refresh();
      expect(controller.policy, isNull);
    });
  });

  group('Task details', () {
    testWidgets('shows the supervision line, the rig, the boundary chips '
        'and the read-only helper after the steps', (tester) async {
      await size(tester, const Size(800, 2400));
      final controller = await boot(_PolicyGateway());
      await pumpRun(tester, controller);

      // On the sheet itself, not behind a fold.
      expect(key('team-run-policy'), findsOneWidget);
      expect(
        textOf(tester, key('team-run-policy-supervision')),
        'Supervision · High',
      );
      expect(textOf(tester, key('team-run-policy-rig')), 'for ocproof');
      for (final entry in const {
        'require_approval': 'Never merge without approval',
        'require_tests': 'Require tests before merge',
        'extra-1': 'Never touch production',
      }.entries) {
        final chip = key('team-run-policy-boundary-${entry.key}');
        expect(chip, findsOneWidget);
        expect(
          find.descendant(of: chip, matching: find.text(entry.value)),
          findsOneWidget,
        );
      }
      expect(key('team-run-policy-boundary-none'), findsNothing);
      expect(
        textOf(tester, key('team-run-policy-from-host')),
        'Set on the host · read-only here',
      );

      // Read-only: no button, switch or field anywhere in the block.
      final block = key('team-run-policy');
      expect(
        find.descendant(of: block, matching: find.byType(ButtonStyleButton)),
        findsNothing,
      );
      expect(
        find.descendant(of: block, matching: find.byType(Switch)),
        findsNothing,
      );
      expect(
        find.descendant(of: block, matching: find.byType(TextField)),
        findsNothing,
      );
      final chips = tester.widgetList<KitChip>(
        find.descendant(of: block, matching: find.byType(KitChip)),
      );
      expect(chips, hasLength(3));
      expect(chips.every((chip) => chip.onRemove == null), isTrue);

      // Order: the status line → the steps → the policy → Technical
      // details.
      expect(
        tester.getBottomLeft(key('team-task-details-status')).dy,
        lessThanOrEqualTo(tester.getTopLeft(block).dy),
      );
      expect(
        tester.getBottomLeft(key('team-task-details-steps')).dy,
        lessThanOrEqualTo(tester.getTopLeft(block).dy),
      );
      expect(
        tester.getBottomLeft(block).dy,
        lessThanOrEqualTo(
          tester.getTopLeft(key('team-task-details-technical')).dy,
        ),
      );
    });

    testWidgets('no boundaries: the "none set" line instead of chips', (
      tester,
    ) async {
      await size(tester, const Size(800, 2400));
      final gateway = _PolicyGateway()
        ..policyDoc = const OrchestrationPolicy(
          supervision: OrchestrationSupervision.autonomous,
        );
      final controller = await boot(gateway);
      await pumpRun(tester, controller);
      expect(
        textOf(tester, key('team-run-policy-supervision')),
        'Supervision · Autonomous',
      );
      expect(key('team-run-policy-rig'), findsNothing);
      expect(key('team-run-policy-boundary-none'), findsOneWidget);
      expect(find.byType(KitChip), findsNothing);
    });

    testWidgets('absent without a policy side (no front route)', (
      tester,
    ) async {
      await size(tester, const Size(800, 2400));
      final controller = await boot(_Gateway());
      await pumpRun(tester, controller);
      expect(key('team-run-policy'), findsNothing);
      expect(find.byType(TeamPolicyBlock), findsNothing);
      expect(find.textContaining('Supervision'), findsNothing);
      // The sheet itself is still there.
      expect(key('team-task-details-body'), findsOneWidget);
    });

    testWidgets('Arabic, 320 dp at 2.5×, RTL: Task details, the line and '
        'every chip render without overflow', (tester) async {
      await size(tester, const Size(320, 1600), ratio: 1);
      final controller = await boot(_PolicyGateway());
      await pumpRun(tester, controller, locale: const Locale('ar'), scale: 2.5);
      expect(tester.takeException(), isNull);
      expect(key('team-run-policy'), findsOneWidget);
      expect(
        textOf(tester, key('team-run-policy-supervision')),
        'الإشراف · عالٍ',
      );
      expect(key('team-run-policy-boundary-require_approval'), findsOneWidget);
      expect(key('team-run-policy-boundary-extra-1'), findsOneWidget);
      expect(
        Directionality.of(tester.element(key('team-run-policy'))),
        TextDirection.rtl,
      );
    });
  });

  group('Start-a-run sheet', () {
    // P6.1: the sheet starts at this server's level from Settings › What
    // runs by itself — High until a person chose another there.
    for (final (stored, expected) in [
      (null, 'high'),
      (AutomationSupervision.autonomous, 'autonomous'),
    ]) {
      testWidgets('supervision starts at ${stored?.name ?? 'the default'}', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        await size(tester, const Size(800, 2400));
        AutomationPolicyController.resetShared();
        final controller = await boot(_Gateway());
        if (stored != null) {
          await tester.runAsync(
            () => controller.automation.setSupervision(stored),
          );
        }
        await pumpSheet(tester, controller);
        for (final level in AutomationSupervision.values) {
          expect(
            partSemantics(
              tester,
              key('team-start-run-supervision-${level.name}'),
            ),
            isSemantics(isSelected: level.name == expected),
            reason: level.name,
          );
        }
        semantics.dispose();
      });
    }

    testWidgets('Boundaries row from controller.policy, read-only', (
      tester,
    ) async {
      await size(tester, const Size(800, 2400));
      final controller = await boot(_PolicyGateway());
      await pumpSheet(tester, controller);
      expect(find.byType(StartRunSheet), findsOneWidget);
      expect(key('team-start-run-boundaries'), findsOneWidget);
      expect(find.text('Boundaries'), findsOneWidget);
      expect(key('team-start-run-boundary-require_approval'), findsOneWidget);
      expect(key('team-start-run-boundary-require_tests'), findsOneWidget);
      expect(key('team-start-run-boundary-extra-1'), findsOneWidget);
      expect(find.text('Never merge without approval'), findsOneWidget);
      expect(find.text('Require tests before merge'), findsOneWidget);
      expect(
        textOf(tester, key('team-start-run-boundaries-from-host')),
        'Set on the host · read-only here',
      );
      final row = key('team-start-run-boundaries');
      expect(
        find.descendant(of: row, matching: find.byType(ButtonStyleButton)),
        findsNothing,
      );
      expect(
        find.descendant(of: row, matching: find.byType(Checkbox)),
        findsNothing,
      );
      // The row sits after the choices and before Send; no read-only
      // Planner row sits among them (the primary names the planner).
      expect(key('team-start-run-planner'), findsNothing);
      expect(find.text('Send to the Mayor'), findsOneWidget);
      final planner = tester.getBottomLeft(
        key('team-start-run-supervision-balanced'),
      );
      final boundaries = tester.getTopLeft(row);
      final send = tester.getTopLeft(key('team-start-run-send'));
      expect(boundaries.dy, greaterThanOrEqualTo(planner.dy));
      expect(send.dy, greaterThan(boundaries.dy));
      // The supervision choice is still the person's: the host's level
      // does not preselect it.
      expect(key('team-start-run-supervision-balanced'), findsOneWidget);
    });

    testWidgets('absent without a policy side', (tester) async {
      await size(tester, const Size(800, 2400));
      final controller = await boot(_Gateway());
      await pumpSheet(tester, controller);
      expect(find.byType(StartRunSheet), findsOneWidget);
      expect(key('team-start-run-boundaries'), findsNothing);
      expect(find.text('Boundaries'), findsNothing);
      expect(find.byType(TeamBoundariesRow), findsNothing);
    });

    testWidgets('Arabic, 320 dp at 2.5×: the row wraps without overflow', (
      tester,
    ) async {
      await size(tester, const Size(320, 2000));
      final controller = await boot(_PolicyGateway());
      await pumpSheet(
        tester,
        controller,
        locale: const Locale('ar'),
        scale: 2.5,
      );
      expect(tester.takeException(), isNull);
      expect(key('team-start-run-boundaries'), findsOneWidget);
      expect(find.text('الحدود'), findsOneWidget);
      expect(key('team-start-run-boundary-require_approval'), findsOneWidget);
    });
  });
}
