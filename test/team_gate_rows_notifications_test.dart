// TEAM-203 (second half): the gate rows, the capability gates, the four
// notification kinds and the layout. Notifications post once per gate with
// ids only, respect the background toggle, and a tap opens the exact sheet
// without sending. Layout at 320dp × 2.5x, LTR and RTL, English and Arabic.
// The answer flows and receipts are in team_gate_answer_test.dart.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/background/team_alerts.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/team_link.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/session_link.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen;
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/team_gate_answer_fixtures.dart';

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost:4096');

  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
}

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.saved});

  final ServerProfile saved;
  String? selectedId;

  @override
  List<ServerProfile> get profiles => [saved];

  @override
  String? get activeId => selectedId;

  @override
  Future<void> setActiveId(String? id) async {
    selectedId = id;
  }
}

/// A connection whose plugin controller a test sets, as the real one does
/// on connect.
class _Connection extends ConnectionController {
  _Connection(super.store, {super.backgroundLive});

  OrchestrationController? team;
  ServerProfile? connected;

  @override
  OrchestrationController? get orchestration => team;

  @override
  ServerProfile? get profile => connected;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(projectManagement: false);

  @override
  bool get isConnected => status == StreamStatus.connected;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  @override
  Future<void> refreshPendingForms() async {}

  void attach(OrchestrationController? next) {
    team?.removeListener(notifyListeners);
    team = next?..addListener(notifyListeners);
  }

  @override
  void dispose() {
    attach(null);
    super.dispose();
  }
}

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;
  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;
  @override
  Future<void> downloadUpdate() async {}
}

typedef _NativeCall = ({String method, Map<String, dynamic>? arguments});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    store = OrchestrationStore(prefs);
    clock = DateTime.utc(2026, 9, 11, 12, 30);
    nextKey = 0;
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

  // ---------------------------------------------------------------------
  // Rows
  // ---------------------------------------------------------------------

  group('rows', () {
    testWidgets('home Needs you: the chip and the row removal', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => g
          ..gateList.add(choiceGate())
          ..answer = (call) async =>
              MutationReceipt.rejected(call.requestId, 'refused'),
      );
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        app(TeamHomeScreen(controller: team, now: () => clock)),
      );
      await tester.pumpAndSettle();
      // One question: shown first on the home, in full, no tab to open.
      final row = find.byKey(const ValueKey('team-home-gate-req-1'));
      final chip = find.byKey(const ValueKey('team-home-gate-req-1-receipt'));
      expect(row, findsOneWidget);
      // The row's line carries no receipt word until an answer is sent.
      expect(
        find.descendant(
          of: chip,
          matching: find.textContaining('Not', findRichText: true),
        ),
        findsNothing,
      );
      await team.answerGate('req-1', const GateResponse.choice('SQLite'));
      await tester.pump();
      await tester.pump();
      expect(chip, findsOneWidget);
      // The card's refused receipt says why, above the answers again.
      expect(
        find.descendant(
          of: chip,
          matching: find.textContaining('Not accepted', findRichText: true),
        ),
        findsOneWidget,
      );
      // Rejected never removes the row.
      expect(row, findsOneWidget);
      // Only a confirmation does.
      gateway.push(const GateChanged(gateId: 'req-1', resolved: true));
      await tester.pump();
      await tester.pump();
      expect(team.mutationFor('req-1')!.status, MutationStatus.rejected);
      expect(row, findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  // ---------------------------------------------------------------------
  // Capabilities
  // ---------------------------------------------------------------------

  group('capabilities', () {
    testWidgets('no control capability: actions absent, host line stays', (
      tester,
    ) async {
      final (team, _) = await boot(
        capabilities: OrchestrationCapabilities.gascityRead,
        configure: (g) {
          g.gateList.addAll([
            choiceGate(),
            textGate(),
            confirmGate(destructive: true),
            beadGate(),
          ]);
          runShape(g);
        },
      );
      for (final id in ['req-1', 'req-text', 'req-destroy', 'bead:w-gate']) {
        await pumpSheet(tester, team, id);
        expect(send, findsNothing);
        expect(find.byKey(const ValueKey('team-gate-approve')), findsNothing);
        expect(find.byKey(const ValueKey('team-gate-deny')), findsNothing);
        expect(find.byKey(const ValueKey('team-gate-mark-done')), findsNothing);
        expect(find.byKey(const ValueKey('team-gate-composer')), findsNothing);
        expect(
          find.byKey(const ValueKey('team-gate-answer-on-host')),
          findsOneWidget,
        );
        await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
        await tester.pumpAndSettle();
      }
      await pumpSheet(tester, team, 'run:oc-loy');
      expect(find.byKey(const ValueKey('team-gate-run-retry')), findsNothing);
      // Reads are on: the agent screens still open, the two tertiary
      // actions shown (design standard §2), no Close. Report this failure
      // (P8.4) never answers the gate, so it stays too, under More.
      expect(find.byKey(const ValueKey('team-gate-run-agent')), findsOneWidget);
      expect(find.byKey(const ValueKey('team-gate-run-logs')), findsOneWidget);
      expect(find.byKey(const ValueKey('team-gate-run-cancel')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('team-gate-run-report')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('team-gate-run-cancel')), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Notifications
  // ---------------------------------------------------------------------

  group('notifications', () {
    OrchestrationSnapshot snap({
      List<OrchestrationGate> gates = const [],
      List<OrchestrationRun> runs = const [],
    }) => OrchestrationSnapshot(gates: gates, runs: runs, refreshedAt: clock);

    test('tracker: baseline, one alert per id, kinds, settled', () {
      final tracker = TeamAlertTracker(profileId: gateProfileId);
      expect(tracker.observe(const OrchestrationSnapshot()).isEmpty, isTrue);
      expect(tracker.primed, isFalse);
      // The first snapshot with data announces nothing.
      expect(tracker.observe(snap(gates: [choiceGate()])).isEmpty, isTrue);
      expect(tracker.primed, isTrue);

      final diff = tracker.observe(
        snap(gates: [choiceGate(), textGate(), beadGate(), runGate()]),
      );
      expect(diff.alerts, [
        const TeamAlert(
          kind: CodingAlertKind.teamDecision,
          id: 'req-text',
          key: 'team:srv-1:gate:req-text',
        ),
        const TeamAlert(
          kind: CodingAlertKind.teamRunFailed,
          id: 'run:oc-loy',
          key: 'team:srv-1:gate:run:oc-loy',
        ),
      ]);
      // Same gates again: nothing.
      expect(
        tracker
            .observe(
              snap(gates: [choiceGate(), textGate(), beadGate(), runGate()]),
            )
            .isEmpty,
        isTrue,
      );
      // Leaving settles its alert (the failed run's too).
      final settled = tracker.observe(snap(gates: [choiceGate()]));
      expect(settled.settled, [
        'team:srv-1:gate:req-text',
        'team:srv-1:gate:run:oc-loy',
      ]);
      expect(settled.alerts, isEmpty);
      // A gate that left and came back is not announced twice.
      final again = tracker.observe(snap(gates: [choiceGate(), textGate()]));
      expect(again.isEmpty, isTrue);
      expect(tracker.observe(snap(gates: [choiceGate()])).isEmpty, isTrue);

      // Review ready pushes; a run completing pushes once.
      final review = OrchestrationGate(
        id: 'review:w-done',
        kind: GateKind.reviewReady,
        title: 'Scaffold calc.py',
      );
      final working = OrchestrationRun(
        id: 'oc-run',
        title: 'Run',
        state: RunState.working,
      );
      var d = tracker.observe(snap(gates: [review], runs: [working]));
      expect(d.alerts.single.kind, CodingAlertKind.teamReview);
      final done = OrchestrationRun(
        id: 'oc-run',
        title: 'Run',
        state: RunState.completed,
      );
      d = tracker.observe(snap(gates: [review], runs: [done]));
      expect(d.alerts, [
        const TeamAlert(
          kind: CodingAlertKind.teamCompleted,
          id: 'oc-run',
          key: 'team:srv-1:run:oc-run',
        ),
      ]);
      expect(
        tracker.observe(snap(gates: [review], runs: [done])).isEmpty,
        isTrue,
      );
      // A run first seen already completed is history, not news.
      final old = OrchestrationRun(
        id: 'oc-old',
        title: 'Old',
        state: RunState.completed,
      );
      expect(
        tracker.observe(snap(gates: [review], runs: [done, old])).isEmpty,
        isTrue,
      );
    });

    Future<({ConnectionController controller, List<_NativeCall> calls})>
    background({bool backgrounded = true}) async {
      final calls = <_NativeCall>[];
      final live = BackgroundLiveController(
        preferences: prefs,
        liveStatusDebounce: Duration.zero,
        invoke: (method, [arguments]) async {
          if (method == 'getBackgroundPause') {
            return const {
              'supported': true,
              'active': false,
              'paused': false,
              'reason': 'none',
              'at': null,
              'canResume': false,
            };
          }
          if (method == 'updateLiveStatus') return const {'updated': true};
          calls.add((method: method, arguments: arguments));
          if (method == 'showCodingAlert') return const {'shown': true};
          if (method == 'dismissCodingAlert') return const {'dismissed': true};
          return const {
            'enabled': true,
            'active': true,
            'notificationGranted': true,
            'batteryOptimizationIgnored': false,
          };
        },
      );
      await prefs.setBool(BackgroundLiveController.preferenceKey, true);
      await live.restore();
      calls.clear();
      final controller = _Connection(
        ProfileStore(prefs: prefs),
        backgroundLive: live,
      )..connected = profile;
      addTearDown(controller.dispose);
      if (backgrounded) controller.suspendForLifecycle();
      return (controller: controller, calls: calls);
    }

    test(
      'one alert per gate, ids and fixed copy only, dismissed on settle',
      () async {
        SharedPreferences.setMockInitialValues({
          BackgroundLiveController.preferenceKey: true,
        });
        prefs = await SharedPreferences.getInstance();
        store = OrchestrationStore(prefs);
        final harness = await background();
        final (team, gateway) = await boot(start: false);
        harness.controller.adoptOrchestrationForTesting(team);
        await team.start();
        await Future<void>.delayed(Duration.zero);
        // The first snapshot is the baseline: nothing to announce.
        expect(harness.calls, isEmpty);

        gateway.gateList.add(choiceGate());
        await team.refresh();
        await Future<void>.delayed(Duration.zero);
        final shown = harness.calls
            .where((c) => c.method == 'showCodingAlert')
            .toList();
        expect(shown, hasLength(1));
        expect(shown.single.arguments, {
          'kind': 'team_decision',
          'sessionID': 'req-1',
          'key': 'team:srv-1:gate:req-1',
          'quickReply': false,
          'requestID': '',
          'profileID': 'srv-1',
          'allowActions': false,
          'subtext': 'Workstation',
        });
        // No title, prompt or option travels.
        final payload = shown.single.arguments.toString();
        expect(payload, isNot(contains('persistence')));
        expect(payload, isNot(contains('SQLite')));

        // A refetch with the same gate: still one.
        await team.refresh();
        await Future<void>.delayed(Duration.zero);
        expect(
          harness.calls.where((c) => c.method == 'showCodingAlert'),
          hasLength(1),
        );

        // The gate leaves: its alert is dismissed by key.
        gateway.gateList.clear();
        await team.refresh();
        await Future<void>.delayed(Duration.zero);
        final dismissed = harness.calls
            .where((c) => c.method == 'dismissCodingAlert')
            .toList();
        expect(dismissed, hasLength(1));
        expect(dismissed.single.arguments, {'key': 'team:srv-1:gate:req-1'});
        // Nothing was ever sent by alerting.
        expect(gateway.calls, isEmpty);
      },
    );

    test('in the foreground nothing is posted', () async {
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      prefs = await SharedPreferences.getInstance();
      store = OrchestrationStore(prefs);
      final harness = await background(backgrounded: false);
      final (team, gateway) = await boot(start: false);
      harness.controller.adoptOrchestrationForTesting(team);
      await team.start();
      gateway.gateList.add(choiceGate());
      gateway.runList.add(failedRun());
      await team.refresh();
      await Future<void>.delayed(Duration.zero);
      expect(
        harness.calls.where((c) => c.method == 'showCodingAlert'),
        isEmpty,
      );
    });

    test('TeamLink: ids only, both shapes, strict parse', () {
      final gate = TeamLink.tryCreate(
        kind: TeamLinkKind.gate,
        profileId: 'srv-1',
        id: 'bead:w-gate',
      )!;
      expect(gate.toString(), 'opencode-mobile://team/gate/srv-1/bead:w-gate');
      expect(TeamLink.parse(gate.toString()), gate);
      final run = TeamLink.tryCreate(
        kind: TeamLinkKind.run,
        profileId: 'srv-1',
        id: 'oc-loy',
      )!;
      expect(run.toString(), 'opencode-mobile://team/run/srv-1/oc-loy');
      expect(TeamLink.parse(run.toString()), run);
      for (final bad in [
        'opencode-mobile://session?profile=srv-1&session=s',
        'opencode-mobile://team/gate/srv-1',
        'opencode-mobile://team/gate/srv-1/req-1/extra',
        'opencode-mobile://team/other/srv-1/req-1',
        'opencode-mobile://team/gate/srv-1/req-1?x=1',
        'opencode-mobile://team/gate/srv-1/req-1#f',
        'https://team/gate/srv-1/req-1',
        'opencode-mobile://team/gate/../req-1',
        'opencode-mobile://team/gate/srv-1/-bad',
        '',
        42,
      ]) {
        expect(TeamLink.parse(bad), isNull, reason: '$bad');
      }
      expect(
        TeamLink.tryCreate(kind: TeamLinkKind.gate, profileId: 'a b', id: 'x'),
        isNull,
      );
    });

    test(
      'SessionLinkIntent accepts a team link beside session links',
      () async {
        final intent = SessionLinkIntent(
          channel: const MethodChannel('oc/link-test'),
        );
        addTearDown(intent.dispose);
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(
              const MethodChannel('oc/link-test'),
              (call) async => call.method == 'consumeSessionLink'
                  ? 'opencode-mobile://team/gate/srv-1/req-1'
                  : null,
            );
        addTearDown(
          () => TestDefaultBinaryMessengerBinding
              .instance
              .defaultBinaryMessenger
              .setMockMethodCallHandler(
                const MethodChannel('oc/link-test'),
                null,
              ),
        );
        if (!SessionLinkIntent.supported) return;
        await intent.start();
        expect(intent.pending.value, isNull);
        expect(
          intent.pendingTeam.value,
          const TeamLink(
            kind: TeamLinkKind.gate,
            profileId: 'srv-1',
            id: 'req-1',
          ),
        );
        expect(intent.takeTeam(), isNotNull);
        expect(intent.pendingTeam.value, isNull);
      },
    );

    Future<_Connection> shell({
      required CodingAlertKind kind,
      required String id,
      required OrchestrationController team,
    }) async {
      final profileStore = _MemoryProfileStore(prefs: prefs, saved: profile);
      await profileStore.setActiveId(profile.id);
      var consumed = false;
      final live = BackgroundLiveController(
        preferences: prefs,
        liveStatusDebounce: Duration.zero,
        invoke: (method, [arguments]) async {
          if (method == 'getBackgroundPause') {
            return const {
              'supported': true,
              'active': false,
              'paused': false,
              'reason': 'none',
              'at': null,
              'canResume': false,
            };
          }
          if (method == 'consumeCodingAlertOpen' && !consumed) {
            consumed = true;
            return {
              'kind': kind.wireValue,
              'sessionID': id,
              'profileID': profile.id,
            };
          }
          return const {
            'enabled': true,
            'active': true,
            'notificationGranted': true,
            'batteryOptimizationIgnored': false,
          };
        },
      );
      final connection = _Connection(profileStore, backgroundLive: live)
        ..api = _Api()
        ..repository = _Repository()
        ..version = '1.18.23'
        ..status = StreamStatus.connected
        ..connected = profile
        ..attach(team);
      addTearDown(connection.dispose);
      return connection;
    }

    Widget shellApp(ConnectionController controller) => ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: OcApp(updateService: _NoUpdateService()),
    );

    testWidgets('a completed-run notification tap opens the task\'s '
        'conversation (P0.3), not the run page', (tester) async {
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      prefs = await SharedPreferences.getInstance();
      store = OrchestrationStore(prefs);
      final (team, gateway) = await boot(
        configure: (g) => g.runList.add(
          OrchestrationRun(
            id: 'oc-done',
            title: 'Ship it',
            state: RunState.completed,
            updatedAt: clock,
          ),
        ),
      );
      final connection = await shell(
        kind: CodingAlertKind.teamCompleted,
        id: 'oc-done',
        team: team,
      );
      await tester.pumpWidget(shellApp(connection));
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TeamConversationScreen>(find.byType(TeamConversationScreen))
            .runId,
        'oc-done',
      );
      expect(find.byKey(const ValueKey('team-run')), findsNothing);
      expect(gateway.calls, isEmpty);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Layout
  // ---------------------------------------------------------------------

  group('layout', () {
    Future<void> reveal(WidgetTester tester, Finder target) async {
      await tester.scrollUntilVisible(
        target,
        150,
        scrollable: find
            .descendant(of: sheet, matching: find.byType(Scrollable))
            .first,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(target, findsOneWidget);
      final rect = tester.getRect(target);
      expect(rect.left, greaterThanOrEqualTo(0));
      expect(rect.right, lessThanOrEqualTo(320));
    }

    Future<void> revealButton(WidgetTester tester, Finder target) async {
      await reveal(tester, target);
      expect(tester.getSize(target).height, greaterThanOrEqualTo(48));
    }

    for (final direction in TextDirection.values) {
      for (final locale in const [Locale('en'), Locale('ar')]) {
        final tag = '${direction.name} ${locale.languageCode}';

        testWidgets('320dp 2.5x $tag: every variant with actions fits', (
          tester,
        ) async {
          final (team, _) = await boot(
            configure: (g) {
              g.gateList.addAll([
                choiceGate(),
                confirmGate(destructive: true),
                textGate(),
                beadGate(),
              ]);
              runShape(g);
            },
          );
          Future<void> open(String id) => pumpSheet(
            tester,
            team,
            id,
            size: const Size(320, 740),
            locale: locale,
            direction: direction,
            textScale: 2.5,
          );
          Future<void> close() async {
            // At 250 % text the header scrolls away with the body (the kit
            // frame's header spacer, 2d0ac9fe): scroll the body back to the
            // top and the close button is there again.
            final close = find.byKey(const ValueKey('kit-sheet-close'));
            final body = tester.state<ScrollableState>(
              find
                  .descendant(of: sheet, matching: find.byType(Scrollable))
                  .first,
            );
            body.position.jumpTo(0);
            await tester.pumpAndSettle();
            final rect = tester.getRect(close);
            expect(rect.top, greaterThanOrEqualTo(tester.getRect(sheet).top));
            expect(tester.getSize(close).height, greaterThanOrEqualTo(48));
            await tester.tap(close);
            await tester.pumpAndSettle();
            expect(sheet, findsNothing);
          }

          await open('req-1');
          expect(tester.getSize(sheet).width, 320);
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-option-1')),
          );
          await close();

          await open('req-destroy');
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-approve')),
          );
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-deny')),
          );
          await close();

          await open('req-text');
          await reveal(
            tester,
            find.byKey(const ValueKey('team-gate-composer')),
          );
          await revealButton(tester, send);
          await close();

          await open('bead:w-gate');
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-mark-done')),
          );
          await close();

          await open('run:oc-loy');
          for (final key in [
            'team-gate-run-agent',
            'team-gate-run-logs',
            // Cancel work is under More (standard §2).
            'kit-actions-more',
          ]) {
            await revealButton(tester, find.byKey(ValueKey(key)));
          }
          await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
          await tester.pumpAndSettle();
          expect(
            find.byKey(const ValueKey('team-gate-run-cancel')),
            findsOneWidget,
          );
          // The last variant: the menu is the last thing to fit.
          await tester.tapAt(Offset.zero);
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
        });

        testWidgets('320dp 2.5x $tag: the receipt with Retry fits', (
          tester,
        ) async {
          final (team, _) = await boot(
            configure: (g) => g
              ..gateList.add(choiceGate())
              ..answer = (call) async => MutationReceipt(
                id: call.requestId,
                status: MutationReceiptStatus.pending,
              ),
          );
          await team.answerGate('req-1', const GateResponse.choice('SQLite'));
          await pumpSheet(
            tester,
            team,
            'req-1',
            size: const Size(320, 740),
            locale: locale,
            direction: direction,
            textScale: 2.5,
          );
          await reveal(tester, receiptLine);
          await revealButton(tester, retry);
          expect(tester.takeException(), isNull);
        });
      }
    }
  });
}
