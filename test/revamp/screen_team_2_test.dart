// screen-team-2 (wave 2b): the AI Team's Start-a-task sheet keeps what the
// person typed. The objective is a KitDraft per server profile: it survives
// typing, a dismissal and a reopen, a refusal keeps the sheet open with the
// host's words (edit and send again), a task the host took clears it, and
// the profile deletion sweep removes it (P7.1, DATA-1, DATA-2, G10).

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/start_run_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// An in-memory front host with a planner: every message is recorded and
/// answered by [answer] (accepted by default).
class _Gateway implements OrchestrationGateway {
  final stream = StreamController<OrchestrationEvent>.broadcast();
  final messages = <String>[];
  Future<MutationReceipt> Function(String requestId)? answer;
  bool _closed = false;

  @override
  OrchestrationCapabilities get capabilities =>
      OrchestrationCapabilities.gascityFront;

  @override
  OrchestrationHostIdentity? get host => const OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://127.0.0.1:8373',
    city: 'bright-lights',
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
  Future<List<OrchestrationProject>> projects() async => const [
    OrchestrationProject(id: 'ocproof', name: 'ocproof', rig: 'ocproof'),
  ];
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => const [];
  @override
  Future<OrchestrationRun?> run(String id) async => null;
  @override
  Future<List<WorkItem>> work({String? projectId}) async => const [];
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => const [];
  @override
  Future<WorkItem?> workItem(String id) async => null;
  @override
  Future<List<OrchestrationAgent>> agents() async => const [
    OrchestrationAgent(
      id: 'gastown.mayor',
      name: 'gastown.mayor',
      state: AgentState.idle,
      rawState: 'idle',
      sessionId: 'bl-8jc',
      pack: 'gastown',
      raw: {'name': 'gastown.mayor', 'suspended': false},
    ),
  ];
  @override
  Future<OrchestrationAgent?> agent(String id) async => null;
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

  Future<MutationReceipt> _accept(String requestId) => Future.value(
    MutationReceipt(
      id: requestId,
      status: MutationReceiptStatus.accepted,
      correlationId: 'corr-$requestId',
      upstreamStatus: 202,
    ),
  );

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) {
    messages.add(text);
    return (answer ?? _accept)(requestId);
  }

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => _accept(requestId);
  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => _accept(requestId);
  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => _accept(requestId);
  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _accept(requestId);
  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => _accept(requestId);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final l10n = lookupAppLocalizations(const Locale('en'));
  const profileId = 'srv-1';
  final draftKey = KitDraft.keyFor(teamStartRunObjectiveDraft, profileId);
  const objective = ValueKey('team-start-run-objective');

  late SharedPreferences prefs;
  var nextKey = 0;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    nextKey = 0;
  });

  Future<(OrchestrationController, _Gateway)> boot() async {
    final gateway = _Gateway();
    final config = OrchestrationConfig(
      provider: OrchestrationProvider.gascity,
      url: 'http://127.0.0.1:8373',
      city: 'bright-lights',
      front: true,
      enabledAt: DateTime.utc(2026, 9, 10),
    );
    final controller = OrchestrationController(
      profile: ServerProfile(
        id: profileId,
        name: 'Development PC',
        baseUrl: 'https://server.example:4096',
        orchestration: config,
      ),
      config: config,
      store: OrchestrationStore(prefs),
      probe: (_) async => ProbeFound(
        host: gateway.host!,
        city: 'bright-lights',
        front: true,
        identityAllowed: true,
        capabilities: gateway.capabilities,
      ),
      gatewayFactory: (_, _) => gateway,
      now: () => DateTime.utc(2026, 9, 27, 9, 41),
      mintKey: () => 'key-${++nextKey}',
      refreshDebounce: const Duration(milliseconds: 10),
      mutationTimeout: const Duration(seconds: 30),
    );
    addTearDown(controller.dispose);
    await controller.start();
    expect(controller.phase, OrchestrationPhase.ready);
    return (controller, gateway);
  }

  /// A page whose one button opens the sheet; the sheet's answer lands in
  /// [results].
  Future<List<MutationRecord?>> pumpOpener(
    WidgetTester tester,
    OrchestrationController controller,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final results = <MutationRecord?>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: Builder(
          builder: (context) => Center(
            child: KitButton.primary(
              key: const ValueKey('open'),
              label: 'Open',
              onPressed: () async => results.add(
                (await showStartRunSheet(context, controller))?.record,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    return results;
  }

  Future<void> open(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('open')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('team-start-run-sheet')), findsOneWidget);
  }

  /// Lets a sent message's wait for the host's echo run out.
  Future<void> drain(WidgetTester tester) async {
    await tester.pump(const Duration(seconds: 31));
    await tester.pump();
  }

  /// Send sits at the end of the sheet's scrolling body.
  Future<void> send(WidgetTester tester) async {
    final button = find.byKey(const ValueKey('team-start-run-send'));
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    await tester.tap(button);
    await tester.pumpAndSettle();
  }

  testWidgets('the objective survives typing, a swipe and a reopen; the '
      'profile sweep removes it', (tester) async {
    final (controller, gateway) = await boot();
    await pumpOpener(tester, controller);
    await open(tester);
    await tester.enterText(find.byKey(objective), 'Ship dark mode');
    await tester.pumpAndSettle();
    expect(prefs.getString(draftKey), 'Ship dark mode');

    // A swipe down dismisses the sheet silently: nothing is lost, so
    // nothing is asked.
    await tester.fling(
      find.text(l10n.teamUiStartRunTitle),
      const Offset(0, 800),
      2000,
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('team-start-run-sheet')), findsNothing);
    expect(gateway.messages, isEmpty);
    expect(prefs.getString(draftKey), 'Ship dark mode');

    // Reopened, the words are back.
    await open(tester);
    expect(
      find.descendant(
        of: find.byKey(objective),
        matching: find.text('Ship dark mode'),
      ),
      findsOneWidget,
    );

    // Deleting the server profile sweeps it (oc.draft.<target>.<profile>).
    final store = ProfileStore(prefs: prefs);
    expect(store.profileScopedPreferenceKeys(profileId), contains(draftKey));
    expect(await store.removeScopedPreferences(profileId), isEmpty);
    expect(prefs.getString(draftKey), isNull);
  });

  testWidgets(
    'a refused task stays in the sheet with plain words, the host\'s text under Details and '
    'the draft, to edit and send again',
    (tester) async {
      final (controller, gateway) = await boot();
      gateway.answer = (requestId) async =>
          MutationReceipt.rejected(requestId, 'the planner is busy');
      final results = await pumpOpener(tester, controller);
      await open(tester);
      await tester.enterText(find.byKey(objective), 'Ship dark mode');
      await tester.pumpAndSettle();
      await send(tester);

      expect(gateway.messages, hasLength(1));
      expect(
        find.byKey(const ValueKey('team-start-run-sheet')),
        findsOneWidget,
      );
      final refused = find.byKey(const ValueKey('team-start-run-refused'));
      expect(refused, findsOneWidget);
      // Plain words first; the host's own text waits under Details.
      expect(
        find.descendant(
          of: refused,
          matching: find.text('The host did not accept this task.'),
        ),
        findsOneWidget,
      );
      expect(find.textContaining('the planner is busy'), findsNothing);
      await tester.tap(
        find.byKey(const ValueKey('team-start-run-refused-details')),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('the planner is busy'), findsWidgets);
      expect(
        find.descendant(
          of: refused,
          matching: find.text(l10n.teamStartRunRefusedKept),
        ),
        findsOneWidget,
      );
      expect(results, isEmpty, reason: 'the sheet did not close');
      expect(prefs.getString(draftKey), 'Ship dark mode');

      // Edit and send again: the host takes it this time.
      gateway.answer = null;
      await tester.enterText(find.byKey(objective), 'Ship dark mode today');
      await tester.pumpAndSettle();
      await send(tester);
      expect(gateway.messages, hasLength(2));
      expect(gateway.messages.last, contains('Ship dark mode today'));
      expect(find.byKey(const ValueKey('team-start-run-sheet')), findsNothing);
      expect(results.single?.kind, MutationKind.message);
      await drain(tester);
    },
  );

  testWidgets('a task the host took clears the draft', (tester) async {
    final (controller, gateway) = await boot();
    await pumpOpener(tester, controller);
    await open(tester);
    await tester.enterText(find.byKey(objective), 'Ship dark mode');
    await tester.pumpAndSettle();
    expect(prefs.getString(draftKey), 'Ship dark mode');
    await send(tester);
    expect(gateway.messages, hasLength(1));
    expect(find.byKey(const ValueKey('team-start-run-sheet')), findsNothing);
    expect(prefs.getString(draftKey), isNull);

    // The next open starts empty.
    await open(tester);
    expect(
      find.descendant(
        of: find.byKey(objective),
        matching: find.text('Ship dark mode'),
      ),
      findsNothing,
    );
    await drain(tester);
  });
}
