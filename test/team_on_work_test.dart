// The AI Team on the Work tab and in its Technical details, over the
// fixture gateway. The Work tab's team card (map page embedded-team-card)
// was deleted by slice-P3.1: the team's open tasks are rows in Work's one
// list (target-ia §1.4), and the host disclaimer lives in Technical
// details.

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/widgets/team_technical_details.dart';
import 'package:shared_preferences/shared_preferences.dart';

Directory _findFixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError(
    'tool/qa/gascity_fixture not found from ${Directory.current}',
  );
}

/// The fixture with per-scope overrides, a read counter and an owned event
/// stream so a test can drop the connection.
class _Gateway implements OrchestrationGateway {
  _Gateway(this.inner);

  final FixtureOrchestrationGateway inner;
  final calls = <String, int>{};
  final stream = StreamController<OrchestrationEvent>.broadcast();
  List<OrchestrationRun>? runsOverride;
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;
  List<OrchestrationGate>? gatesOverride;
  bool failReads = false;

  int count(String name) => calls[name] ?? 0;
  void _hit(String name) {
    calls[name] = count(name) + 1;
    if (failReads) throw StateError('read failed: $name');
  }

  @override
  OrchestrationCapabilities get capabilities => inner.capabilities;
  @override
  OrchestrationHostIdentity? get host => inner.host;
  @override
  bool get isClosed => inner.isClosed;
  @override
  Future<void> close() async {
    await stream.close();
    await inner.close();
  }

  @override
  Future<List<OrchestrationProject>> projects() {
    _hit('projects');
    return inner.projects();
  }

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async {
    _hit('runs');
    return runsOverride ?? await inner.runs(projectId: projectId);
  }

  @override
  Future<OrchestrationRun?> run(String id) => inner.run(id);

  @override
  Future<List<WorkItem>> work({String? projectId}) async {
    _hit('work');
    return workOverride ?? await inner.work(projectId: projectId);
  }

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) =>
      inner.readyWork(projectId: projectId);

  @override
  Future<WorkItem?> workItem(String id) => inner.workItem(id);

  @override
  Future<List<OrchestrationAgent>> agents() async {
    _hit('agents');
    return agentsOverride ?? await inner.agents();
  }

  @override
  Future<OrchestrationAgent?> agent(String id) => inner.agent(id);

  @override
  Future<List<OrchestrationGate>> gates() async {
    _hit('gates');
    return gatesOverride ?? await inner.gates();
  }

  @override
  Future<OrchestrationUsage?> usage() {
    _hit('usage');
    return inner.usage();
  }

  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) {
    _hit('activity');
    return inner.activity(afterSeq: afterSeq, limit: limit);
  }

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => inner.respond(gateId, response, requestId: requestId);

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => inner.message(agentId, text, requestId: requestId);

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => inner.controlAgent(agentId, action, requestId: requestId);

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => inner.cancelRun(runId, requestId: requestId);

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => inner.assign(workId, agentId: agentId, requestId: requestId);

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => inner.createWork(
    title: title,
    description: description,
    projectId: projectId,
    requestId: requestId,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late String fixturePath;
  late SharedPreferences prefs;
  late OrchestrationStore store;
  late DateTime clock;

  setUp(() async {
    fixturePath = _findFixtureRoot().path;
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    store = OrchestrationStore(prefs);
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

  OrchestrationConfig config({
    OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
    OrchestrationHostKind? hostKind,
    String? url,
  }) => OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: url ?? fixturePath,
    city: 'bright-lights',
    hostMode: hostMode,
    hostKind: hostKind,
    enabledAt: DateTime.utc(2026, 9, 10),
  );

  ServerProfile profile({OrchestrationConfig? config}) => ServerProfile(
    id: 'srv-1',
    name: 'Workstation',
    baseUrl: 'https://server.example:4096',
    orchestration: config,
  );

  /// A controller over the wrapped fixture; [start] is awaited unless
  /// [started] is false.
  Future<(OrchestrationController, _Gateway)> boot({
    bool started = true,
    OrchestrationProbe? probe,
    void Function(_Gateway gateway)? configure,
    OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
    OrchestrationHostKind? hostKind,
    String? url,
  }) async {
    final gateway = _Gateway(
      FixtureOrchestrationGateway(fixturePath: fixturePath, hostMode: hostMode),
    );
    configure?.call(gateway);
    final cfg = config(hostMode: hostMode, hostKind: hostKind, url: url);
    final controller = OrchestrationController(
      profile: profile(config: cfg),
      config: cfg,
      store: store,
      gatewayFactory: (_, _) => gateway,
      probe: probe,
      now: () => clock,
    );
    addTearDown(controller.dispose);
    if (started) await controller.start();
    return (controller, gateway);
  }

  Widget app(
    Widget home, {
    bool reduceMotion = true,
    Locale locale = const Locale('en'),
    bool scroll = true,
  }) => MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: reduceMotion),
      child: child!,
    ),
    home: Scaffold(body: scroll ? SingleChildScrollView(child: home) : home),
  );

  // TEAM-206: the one-line disclaimer of 03-onboarding §4 per host kind,
  // moved from the card to Technical details by the AI Team redesign.
  group('disclaimer per host kind (Technical details)', () {
    const expected = {
      OrchestrationHostKind.pc: 'Runs as fast as your computer; keep it awake',
      OrchestrationHostKind.laptop:
          'Sleep and lid-close pause the team; runs resume on wake',
      OrchestrationHostKind.wsl:
          'Sleep and lid-close pause the team; runs resume on wake. '
          'WSL also stops when its last terminal closes.',
      OrchestrationHostKind.phone:
          'Android may stop it when the screen is off; slower than a computer',
    };

    Future<String?> disclaimerIn(
      WidgetTester tester,
      OrchestrationController controller, {
      Locale locale = const Locale('en'),
    }) async {
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () => showTeamHostDetailsSheet(context, controller),
              child: const Text('details'),
            ),
          ),
          locale: locale,
        ),
      );
      await tester.tap(find.text('details'));
      await tester.pumpAndSettle();
      final line = find.byKey(const ValueKey('team-host-disclaimer'));
      return line.evaluate().isEmpty ? null : tester.widget<KitText>(line).text;
    }

    for (final entry in expected.entries) {
      testWidgets('${entry.key.name} shows its line, and only its line', (
        tester,
      ) async {
        final (controller, _) = await boot(
          hostMode: entry.key.mode,
          hostKind: entry.key,
        );
        expect(await disclaimerIn(tester, controller), entry.value);
        for (final other in expected.values) {
          expect(
            find.text(other),
            other == entry.value ? findsOneWidget : findsNothing,
          );
        }
      });
    }

    testWidgets('a config without a kind shows the mode default', (
      tester,
    ) async {
      final (controller, _) = await boot();
      expect(
        await disclaimerIn(tester, controller),
        expected[OrchestrationHostKind.pc],
      );
    });

    testWidgets('Arabic carries the laptop line', (tester) async {
      final (controller, _) = await boot(
        hostKind: OrchestrationHostKind.laptop,
      );
      final l10n = lookupAppLocalizations(const Locale('ar'));
      expect(
        await disclaimerIn(tester, controller, locale: const Locale('ar')),
        l10n.teamUiHostKindDisclaimerLaptop,
      );
      expect(
        l10n.teamUiHostKindDisclaimerLaptop,
        isNot(expected[OrchestrationHostKind.laptop]),
      );
    });
  });
}
