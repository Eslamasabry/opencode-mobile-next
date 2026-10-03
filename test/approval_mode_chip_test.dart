import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

/// The composer's approval chip is a mode switcher: always on screen in a
/// conversation, naming the mode with no count, and opening a menu of the
/// modes this phone really supports.

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {}

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _Controller extends ConnectionController {
  _Controller(super.store, {super.isIsolated});
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');
}

class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => [
    ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost'),
  ];
}

Future<(_Controller, SharedPreferences)> _boot({
  AutomationSupervision supervision = AutomationSupervision.balanced,
  bool isolated = false,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  await AutomationPolicyController.forProfile(
    prefs,
    'server-a',
  ).setSupervision(supervision);
  final controller = _Controller(_Store(prefs: prefs), isIsolated: isolated)
    ..api = _Api()
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  controller.sessionsById['parent'] = Session(id: 'parent', title: 'Parent');
  return (controller, prefs);
}

Future<void> _pump(WidgetTester tester, ConnectionController controller) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const ChatScreen(sessionID: 'parent'),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

final _chip = find.byKey(const Key('auto-approval-indicator'));
final _menu = find.byKey(const Key('approval-mode-menu'));

Future<void> _openMenu(WidgetTester tester) async {
  await tester.tap(_chip);
  await tester.pumpAndSettle();
  expect(_menu, findsOneWidget);
}

Future<void> _tapKey(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
}

SessionAutoApprovalStore _stored(SharedPreferences prefs) =>
    SessionAutoApprovalStore(prefs);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('the chip names each mode, with no count', (tester) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller);
    expect(_chip, findsOneWidget);
    expect(find.text('Asks first'), findsOneWidget);

    await controller.setSessionAutoApproval(
      'parent',
      const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
    );
    await tester.pumpAndSettle();
    expect(find.text('Auto-approve'), findsOneWidget);
    expect(
      find.descendant(of: _chip, matching: find.textContaining('·')),
      findsNothing,
    );

    await controller.setSessionAutoApproval('parent', null);
    await controller.setApprovesEverything(true);
    await tester.pumpAndSettle();
    expect(find.text('Approves everything'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tapping the chip lists the supported modes and settings', (
    tester,
  ) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller);
    await _openMenu(tester);
    expect(find.byKey(const Key('approval-mode-ask')), findsOneWidget);
    expect(find.byKey(const Key('approval-mode-auto')), findsOneWidget);
    expect(find.byKey(const Key('approval-mode-everything')), findsOneWidget);
    expect(find.text('Approval settings…'), findsOneWidget);
    // One plain line each, and the current mode carries the check.
    expect(
      find.text('Every permission request waits for you.'),
      findsOneWidget,
    );
    expect(find.text('Allows each request once, here only.'), findsOneWidget);
    expect(
      find.descendant(of: _menu, matching: find.byIcon(AppIconography.check)),
      findsOneWidget,
    );
  });

  testWidgets('a policy that forbids automatic answers offers only asking', (
    tester,
  ) async {
    final (controller, _) = await _boot(
      supervision: AutomationSupervision.high,
    );
    await _pump(tester, controller);
    await _openMenu(tester);
    expect(find.byKey(const Key('approval-mode-ask')), findsOneWidget);
    expect(find.byKey(const Key('approval-mode-auto')), findsNothing);
    expect(find.byKey(const Key('approval-mode-everything')), findsNothing);
    expect(find.byKey(const Key('approval-mode-settings')), findsOneWidget);
  });

  testWidgets('a stricter mode applies at once and is saved', (tester) async {
    final (controller, prefs) = await _boot();
    await controller.setSessionAutoApproval(
      'parent',
      const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
    );
    await _pump(tester, controller);
    await _openMenu(tester);
    await _tapKey(tester, 'approval-mode-ask');

    expect(find.byKey(const Key('approval-mode-confirm')), findsNothing);
    expect(controller.autoApprovalFor('parent').automatic, isFalse);
    expect(find.text('Asks first'), findsOneWidget);
    expect(find.text('This conversation asks first.'), findsOneWidget);
    final saved = _stored(prefs).explicitFor('server-a', 'parent');
    expect(saved?.mode, AutoApprovalMode.ask);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('a more permissive mode confirms; Cancel keeps the old mode', (
    tester,
  ) async {
    final (controller, prefs) = await _boot();
    await _pump(tester, controller);
    await _openMenu(tester);
    await _tapKey(tester, 'approval-mode-auto');

    expect(find.byKey(const Key('approval-mode-confirm')), findsOneWidget);
    expect(
      find.textContaining('Nothing is saved as always allowed'),
      findsOneWidget,
    );
    expect(controller.autoApprovalFor('parent').automatic, isFalse);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(controller.autoApprovalFor('parent').automatic, isFalse);
    expect(_stored(prefs).explicitFor('server-a', 'parent'), isNull);
    expect(find.text('Asks first'), findsOneWidget);

    await _openMenu(tester);
    await _tapKey(tester, 'approval-mode-auto');
    await tester.tap(find.text('Auto-approve this conversation').last);
    await tester.pumpAndSettle();
    expect(controller.autoApprovalFor('parent').automatic, isTrue);
    expect(
      find.text('This conversation approves automatically.'),
      findsOneWidget,
    );
    // Persisted through the same store the sheet writes.
    expect(
      _stored(prefs).explicitFor('server-a', 'parent')?.mode,
      AutoApprovalMode.autoOnce,
    );
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('approve everything is confirmed and saved server-wide', (
    tester,
  ) async {
    final (controller, prefs) = await _boot();
    await _pump(tester, controller);
    await _openMenu(tester);
    await _tapKey(tester, 'approval-mode-everything');
    expect(find.textContaining('without asking you'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(controller.approvesEverything, isFalse);

    await _openMenu(tester);
    await _tapKey(tester, 'approval-mode-everything');
    await tester.tap(find.text('Approve everything').last);
    await tester.pumpAndSettle();
    expect(controller.approvesEverything, isTrue);
    expect(_stored(prefs).approvesEverything('server-a'), isTrue);
    expect(find.text('Approves everything'), findsOneWidget);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('Approval settings opens the detailed sheet', (tester) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller);
    await _openMenu(tester);
    await _tapKey(tester, 'approval-mode-settings');
    expect(find.byKey(const Key('session-approvals-sheet')), findsOneWidget);
  });

  testWidgets('disconnected: automatic mode reads paused, asking does not', (
    tester,
  ) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller);
    controller.status = StreamStatus.reconnecting;
    controller.notifyListeners();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Asks first'), findsOneWidget);

    await controller.setSessionAutoApproval(
      'parent',
      const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
    );
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Auto-approve paused'), findsOneWidget);
    await tester.pump(const Duration(seconds: 9));
  });

  testWidgets('an isolated walkthrough shows no chip', (tester) async {
    final (controller, _) = await _boot(isolated: true);
    await _pump(tester, controller);
    expect(_chip, findsNothing);
  });
}
