// slice-inbox-work (P4.2b + P5.5): the Inbox lists what every saved server
// waits on in one list, each row naming its server, and says plainly which
// servers it cannot speak for; Work and Inbox rows say what the work is
// doing from the one WorkRowStatus source, resting still once disconnected.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/sse.dart' show StreamStatus;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/attention_feed.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profile_monitor.dart';
import 'package:opencode_mobile/state/work_row_status_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/ui/widgets/attention_feed_rows.dart';

import 'support/profile_monitor_fixture.dart';
import 'support/stash_memory_vault.dart';

class _Gateway extends MonitorTestGateway {
  _Gateway({super.requests});
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => [];
}

/// The real controller; [poke] stands in for the transport's own
/// notification after a test flips a field it has no setter event for.
class _Controller extends ConnectionController {
  _Controller(super.store, {super.monitorGatewayFactory})
    : super(
        draftAttachmentVault: StashMemoryVault(),
        stashAttachmentVault: StashMemoryVault(),
      );

  void poke() => notifyListeners();
}

Widget _app(Widget home) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: true),
    child: child!,
  ),
  home: Scaffold(body: home),
);

/// Busy rows animate (spinner), so settle by hand.
Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

String _plain(String text) =>
    text.replaceAll(RegExp('[\u2066-\u2069\u200e\u200f\u202a-\u202e]'), '');

/// Rendered text containing [part], bidi isolates ignored (server names
/// are isolated, COPY-30).
Finder _has(String part) => find.byWidgetPredicate(
  (widget) =>
      widget is RichText && _plain(widget.text.toPlainText()).contains(part),
);

String _spanText(InlineSpan span) => _plain(span.toPlainText());

void _tall(WidgetTester tester) {
  tester.view.physicalSize = const Size(800, 1800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    AutomationPolicyController.resetShared();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
  });

  /// Server 1 connected with one permission; Servers 2 and 3 saved, their
  /// checks off until a test turns one on.
  Future<_Controller> boot() async {
    final store = await monitorStore(count: 3);
    await store.setActiveId('profile-1');
    final controller = _Controller(
      store,
      monitorGatewayFactory: (_) => (
        gateway: _Gateway(requests: [request(7)]),
        operations: MonitorTestOperations(),
      ),
    );
    controller.api = _Gateway(requests: [request(1)]);
    controller.repository = MonitorTestOperations();
    controller.status = StreamStatus.connected;
    await controller.refreshPendingPermissions();
    await controller.refreshPendingQuestions();
    return controller;
  }

  testWidgets('one Inbox list holds every server, each row naming its server, '
      'the connected server\'s request once', (tester) async {
    _tall(tester);
    final controller = await boot();
    await controller.profileMonitor.setRules(
      'profile-2',
      const ProfileNotifyRules(enabled: true),
    );
    await controller.profileMonitor.refresh();
    await tester.pumpWidget(
      _app(ActivityScreen(controller: controller, embedded: true)),
    );
    await _frames(tester);

    // The connected server's request keeps its answerable row, not a second
    // feed row.
    expect(
      find.byKey(const ValueKey('activity-permission-request-1')),
      findsOneWidget,
    );
    expect(_has('Server 1'), findsNothing);
    // Server 2's request, in the same list, naming its server.
    expect(_has('Needs your OK · Server 2'), findsOneWidget);
    final feed = controller.attentionFeed;
    expect(inboxFeedItems(controller, feed).map((item) => item.profileID), [
      'profile-2',
    ]);
    // Server 3 is not checked, said plainly, with the way to turn it on.
    expect(_has('Not checking Server 3'), findsOneWidget);
    expect(find.byKey(const ValueKey('activity-all-clear')), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  });

  testWidgets('checks off: the Inbox says which servers it cannot speak for '
      'and opens Notifications; showing it never opts in', (tester) async {
    _tall(tester);
    final controller = await boot();
    await tester.pumpWidget(
      _app(ActivityScreen(controller: controller, embedded: true)),
    );
    await _frames(tester);

    expect(_has('Not checking Server 2, Server 3'), findsOneWidget);
    expect(
      find.text(
        "Their requests don't show here. Turn on checks in Notifications.",
      ),
      findsOneWidget,
    );
    expect(controller.profileMonitor.rulesFor('profile-2').enabled, isFalse);

    await tester.tap(find.byKey(const ValueKey('attention-checks-off')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('monitor-enabled-profile-2')),
      findsOneWidget,
    );
    expect(controller.profileMonitor.rulesFor('profile-2').enabled, isFalse);
    expect(controller.profileMonitor.rulesFor('profile-3').enabled, isFalse);

    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  });

  testWidgets('each Inbox row says its state from WorkRowStatus and names its '
      'server; old rows rest still with their time', (tester) async {
    _tall(tester);
    final controller = await boot();
    final now = DateTime(2026, 9, 28, 15);
    final seen = DateTime(2026, 9, 28, 14, 2);
    AttentionFeedItem item(
      String id,
      AttentionKind kind,
      WorkRowPhase phase, {
      bool fresh = true,
      String? title,
      String? taskID,
    }) => AttentionFeedItem(
      identity: id,
      profileID: 'profile-2',
      serverName: 'Server 2',
      kind: kind,
      title: title,
      target: AttentionTarget(
        profileID: 'profile-2',
        kind: kind,
        sessionID: 's-$id',
        requestID: 'r-$id',
        taskID: taskID,
      ),
      status: WorkRowStatus(
        facts: WorkRowFacts(phase: phase),
        observedAt: seen,
        isFresh: fresh,
      ),
    );
    final items = [
      item('ask', AttentionKind.permission, WorkRowPhase.needsYou),
      item(
        'old',
        AttentionKind.question,
        WorkRowPhase.needsYou,
        fresh: false,
        title: 'Old question',
      ),
      item(
        'fail',
        AttentionKind.failedRun,
        WorkRowPhase.failed,
        title: 'Broken build',
      ),
      item(
        'gate',
        AttentionKind.teamGate,
        WorkRowPhase.failed,
        taskID: 'task-1',
      ),
    ];
    await tester.pumpWidget(
      _app(
        ListView(
          children: [
            for (final entry in items)
              AttentionFeedRow(
                key: ValueKey(entry.identity),
                controller: controller,
                item: entry,
                now: now,
                onOpenConversation: (_, _) {},
              ),
          ],
        ),
      ),
    );
    await _frames(tester);

    // A fresh request: the pointing needs-you row, server named.
    expect(find.text('Permission required'), findsOneWidget);
    expect(_has('Needs your OK · Server 2'), findsOneWidget);
    // Known only from an older check: the state words with as-of time, no
    // live needs-you row.
    final old = tester.widget<KitRow>(
      find.descendant(
        of: find.byKey(const ValueKey('old')),
        matching: find.byType(KitRow),
      ),
    );
    expect(
      _spanText(old.supporting!),
      allOf(startsWith('Needs you · as of'), endsWith(' · on Server 2')),
    );
    // A failure: Failed, and where.
    expect(find.text('Broken build'), findsOneWidget);
    final failed = tester.widget<KitRow>(
      find.descendant(
        of: find.byKey(const ValueKey('fail')),
        matching: find.byType(KitRow),
      ),
    );
    expect(_spanText(failed.supporting!), 'Failed · on Server 2');
    expect(
      tester
          .widget<KitTaskMark>(
            find.descendant(
              of: find.byKey(const ValueKey('fail')),
              matching: find.byType(KitTaskMark),
            ),
          )
          .state,
      KitTaskState.failed,
    );
    // A failed team task without a title: generic title, the Team word.
    expect(find.text('Team task'), findsOneWidget);
    final gate = tester.widget<KitRow>(
      find.descendant(
        of: find.byKey(const ValueKey('gate')),
        matching: find.byType(KitRow),
      ),
    );
    expect(_spanText(gate.supporting!), 'Failed · Team · on Server 2');

    // A row no longer in the feed (answered elsewhere) is looked up again
    // before opening: it says so and opens nothing.
    await tester.tap(find.text('Broken build'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('attention-open-changed')),
      findsOneWidget,
    );

    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  });

  testWidgets('a removed server\'s rows leave the Inbox with it', (
    tester,
  ) async {
    _tall(tester);
    final controller = await boot();
    await controller.profileMonitor.setRules(
      'profile-2',
      const ProfileNotifyRules(enabled: true),
    );
    await controller.profileMonitor.refresh();
    await tester.pumpWidget(
      _app(ActivityScreen(controller: controller, embedded: true)),
    );
    await _frames(tester);
    expect(_has('Server 2'), findsOneWidget);

    await controller.store.remove('profile-2');
    controller.poke();
    await _frames(tester);
    expect(_has('Server 2'), findsNothing);
    expect(
      controller.attentionFeed.items.where((i) => i.profileID == 'profile-2'),
      isEmpty,
    );
    // The connected server's own request is untouched.
    expect(
      find.byKey(const ValueKey('activity-permission-request-1')),
      findsOneWidget,
    );

    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  });
}
