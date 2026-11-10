import 'support/fake_server_capabilities.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ServerCapabilities;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/composer_tools.dart';

/// How a conversation answers permission requests (chat fidelity round,
/// 2026-10-20): while the agent is not working, the Auto-approve and model
/// chips sit over the composer; while it works they fold away (concept C),
/// one muted line under the header says Auto-approve is on, and the switch,
/// the model and the approval settings stay behind the composer's +.

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api({this.asksBeforeActing = true}) : super(baseUrl: 'http://localhost');

  /// Whether this runtime sends permission requests at all.
  final bool asksBeforeActing;

  @override
  ServerCapabilities get capabilities => asksBeforeActing
      ? testServerCapabilities
      : const ServerCapabilities(permissionRequests: false);

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
  bool ownsSelection = false;
  @override
  bool get serverOwnsSessionSelection =>
      ownsSelection || super.serverOwnsSessionSelection;
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
  bool asksBeforeActing = true,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  await AutomationPolicyController.forProfile(
    prefs,
    'server-a',
  ).setSupervision(supervision);
  final controller = _Controller(_Store(prefs: prefs), isIsolated: isolated)
    ..api = _Api(asksBeforeActing: asksBeforeActing)
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  controller.sessionsById['parent'] = Session(id: 'parent', title: 'Parent');
  return (controller, prefs);
}

Future<void> _pump(
  WidgetTester tester,
  ConnectionController controller, {
  String session = 'parent',
  bool settle = true,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(sessionID: session),
      ),
    ),
  );
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }
}

/// Short enough that the chips over the composer fold into the + sheet, so the
/// line under the header is the one that says Auto-approve is on.
const _short = Size(412, 500);

final _line = find.byKey(const ValueKey('chat-status-auto-approve'));
final _chip = find.byKey(const Key('auto-approval-indicator'));
final _menu = find.byKey(const Key('approval-mode-menu'));

Future<void> _openMenu(WidgetTester tester) async {
  await tester.tap(_chip);
  await tester.pumpAndSettle();
  expect(_menu, findsOneWidget);
}

/// The agent is at work on this conversation.
void _working(ConnectionController controller) {
  controller.busySessions = {'parent'};
  controller.notifyListeners();
}

final _switchRow = find.byKey(const Key('composer-setting-approval'));

Future<void> _openPlus(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
}

Future<void> _tapKey(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(Key(key)));
  await tester.pumpAndSettle();
}

SessionAutoApprovalStore _stored(SharedPreferences prefs) =>
    SessionAutoApprovalStore(prefs);

Future<void> _auto(ConnectionController controller) =>
    controller.setSessionAutoApproval(
      'parent',
      const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('idle: the Auto-approve and model chips sit over the '
      'composer, and no line says it twice', (tester) async {
    final (controller, _) = await _boot();
    await _auto(controller);
    await _pump(tester, controller);
    expect(find.byKey(const Key('composer-status-strip')), findsOneWidget);
    expect(_chip, findsOneWidget);
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);
    expect(_line, findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('working: the chips stay in the row with Stop, and the line '
      'does not say it twice', (tester) async {
    final (controller, _) = await _boot();
    await _auto(controller);
    await _pump(tester, controller);
    _working(controller);
    await tester.pumpAndSettle();
    expect(_chip, findsOneWidget);
    expect(find.byKey(const Key('composer-model-context')), findsOneWidget);
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
    expect(_line, findsNothing);
    // The reply ends: the chips stay and Stop goes.
    controller.busySessions = {};
    controller.notifyListeners();
    await tester.pumpAndSettle();
    expect(_chip, findsOneWidget);
    expect(find.byKey(const Key('chat-stop-button')), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the chip says on or off at a glance, with no count', (
    tester,
  ) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller);
    // The state reads at a glance: "Ask first" with the plain shield, and
    // "Auto-approve on" with the kit's mint check.
    final onMark = find.descendant(
      of: _chip,
      matching: find.byKey(const ValueKey('kit-chip-check')),
    );
    expect(_chip, findsOneWidget);
    expect(find.text('Ask first'), findsOneWidget);
    expect(onMark, findsNothing);
    // Copy ending in a full stop is not read out with two.
    expect(tester.getSemantics(_chip).label, isNot(contains('..')));
    await _auto(controller);
    await tester.pumpAndSettle();
    expect(find.text('Auto-approve on'), findsOneWidget);
    expect(onMark, findsOneWidget);
    await controller.setSessionAutoApproval('parent', null);
    await controller.setApprovesEverything(true);
    await tester.pumpAndSettle();
    // Both automatic modes read the same; the menu says which.
    expect(find.text('Auto-approve on'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tapping the chip lists the modes, and a stricter one applies '
      'at once and is saved', (tester) async {
    final (controller, prefs) = await _boot();
    await _auto(controller);
    await _pump(tester, controller);
    await _openMenu(tester);
    expect(find.byKey(const Key('approval-mode-ask')), findsOneWidget);
    expect(find.byKey(const Key('approval-mode-auto')), findsOneWidget);
    expect(find.byKey(const Key('approval-mode-everything')), findsOneWidget);
    expect(find.byKey(const Key('approval-mode-settings')), findsOneWidget);
    await _tapKey(tester, 'approval-mode-ask');
    expect(find.byKey(const Key('approval-mode-confirm')), findsNothing);
    expect(controller.autoApprovalFor('parent').automatic, isFalse);
    expect(find.text('Ask first'), findsOneWidget);
    expect(
      _stored(prefs).explicitFor('server-a', 'parent')?.mode,
      AutoApprovalMode.ask,
    );
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('one line under the header while it works with auto-approve '
      'on', (tester) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller, size: _short);
    _working(controller);
    await tester.pumpAndSettle();
    expect(_line, findsNothing);

    await _auto(controller);
    await tester.pumpAndSettle();
    expect(_line, findsOneWidget);
    expect(find.text('Auto-approve is on'), findsOneWidget);

    await controller.setSessionAutoApproval('parent', null);
    await controller.setApprovesEverything(true);
    await tester.pumpAndSettle();
    // Both automatic modes read the same.
    expect(find.text('Auto-approve is on'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the line says its piece once: it folds away by itself and '
      'stays away in this conversation (owner, 2026-10-27)', (tester) async {
    final (controller, _) = await _boot();
    await _auto(controller);
    await _pump(tester, controller, size: _short);
    _working(controller);
    await tester.pump();
    await tester.pump();
    expect(_line, findsOneWidget);
    await tester.pump(const Duration(seconds: 7));
    await tester.pumpAndSettle();
    expect(_line, findsNothing);
    // The chip and the + sheet keep the state; reopening the chat does not
    // bring the line back.
    expect(controller.autoApprovalFor('parent').automatic, isTrue);
    await tester.pumpWidget(const SizedBox());
    await _pump(tester, controller, size: _short);
    await tester.pump();
    expect(_line, findsNothing);
  });

  testWidgets('Dismiss folds the line away now and for this conversation; '
      'turning auto-approve on again says it once more', (tester) async {
    final (controller, _) = await _boot();
    await _auto(controller);
    await _pump(tester, controller, size: _short);
    _working(controller);
    await tester.pump();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(_line, findsOneWidget);
    await tester.tap(
      find.descendant(of: _line, matching: find.byTooltip('Dismiss')),
    );
    await tester.pumpAndSettle();
    expect(_line, findsNothing);

    controller.busySessions = {};
    controller.notifyListeners();
    await tester.pumpAndSettle();
    // Off, then on again from the chip's menu (a tall window shows the chip).
    tester.view.physicalSize = const Size(412, 915);
    await tester.pumpAndSettle();
    await _openMenu(tester);
    await tester.tap(find.byKey(const Key('approval-mode-ask')));
    await tester.pumpAndSettle();
    await _openMenu(tester);
    await tester.tap(find.byKey(const Key('approval-mode-auto')));
    await tester.pumpAndSettle();
    tester.view.physicalSize = _short;
    await tester.pumpAndSettle();
    _working(controller);
    await tester.pump();
    await tester.pump();
    expect(_line, findsOneWidget);
    await tester.pump(const Duration(seconds: 7));
    await tester.pumpAndSettle();
  });

  testWidgets('the line turns auto-approve off in one tap, and it is saved', (
    tester,
  ) async {
    final (controller, prefs) = await _boot();
    await _auto(controller);
    await _pump(tester, controller, size: _short);
    _working(controller);
    await tester.pumpAndSettle();
    await _tapKey(tester, 'chat-auto-approve-off');

    expect(controller.autoApprovalFor('parent').automatic, isFalse);
    expect(_line, findsNothing);
    expect(find.text('This conversation asks first.'), findsOneWidget);
    expect(
      _stored(prefs).explicitFor('server-a', 'parent')?.mode,
      AutoApprovalMode.ask,
    );
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('a runtime that never asks before acting shows nothing', (
    tester,
  ) async {
    // FC1: the mode is shown for runtimes that ask (a capability), and
    // hidden otherwise, even when a saved mode would say "Auto-approve".
    final (controller, _) = await _boot(asksBeforeActing: false);
    await _auto(controller);
    await _pump(tester, controller);
    expect(_chip, findsNothing);
    _working(controller);
    await tester.pump(const Duration(milliseconds: 400));
    expect(_line, findsNothing);
    controller.busySessions = {};
    controller.notifyListeners();
    await tester.pumpAndSettle();
    await _openPlus(tester);
    expect(_switchRow, findsNothing);
    expect(
      find.byKey(const Key('composer-setting-approval-settings')),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('the + sheet turns auto-approve on and off, no confirm', (
    tester,
  ) async {
    final (controller, prefs) = await _boot();
    await _pump(tester, controller);
    await _openPlus(tester);
    expect(_switchRow, findsOneWidget);
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isFalse);

    await tester.tap(_switchRow);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('approval-mode-confirm')), findsNothing);
    expect(controller.autoApprovalFor('parent').automatic, isTrue);
    // The sheet shows the new state at once; it does not go stale.
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isTrue);
    expect(
      _stored(prefs).explicitFor('server-a', 'parent')?.mode,
      AutoApprovalMode.autoOnce,
    );

    await tester.tap(_switchRow);
    await tester.pumpAndSettle();
    expect(controller.autoApprovalFor('parent').automatic, isFalse);
    expect(tester.widget<Switch>(find.byType(Switch).first).value, isFalse);
    await tester.pump(const Duration(seconds: 4));
  });

  testWidgets('Approval settings opens the detailed sheet', (tester) async {
    final (controller, _) = await _boot();
    await _pump(tester, controller);
    await _openPlus(tester);
    await _tapKey(tester, 'composer-setting-approval-settings');
    expect(find.byKey(const Key('session-approvals-sheet')), findsOneWidget);
  });

  testWidgets(
    'disconnected: automatic mode reads paused, asking says nothing',
    (tester) async {
      final (controller, _) = await _boot();
      await _pump(tester, controller);
      controller.busySessions = {'parent'};
      controller.status = StreamStatus.reconnecting;
      controller.notifyListeners();
      await tester.pump(const Duration(milliseconds: 400));
      expect(_line, findsNothing);

      await _auto(controller);
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('Auto-approve paused'), findsOneWidget);
      // Nothing is being approved, so there is nothing to turn off.
      expect(find.byKey(const Key('chat-auto-approve-off')), findsNothing);
      await tester.pump(const Duration(seconds: 9));
      // The reconnect grace timer belongs to the controller, not the widget.
      controller.dispose();
    },
  );

  testWidgets('cold start shows the conversation model then fresh selection', (
    tester,
  ) async {
    final (controller, _) = await _boot();
    controller.ownsSelection = true;
    final previous = ModelRef(providerID: 'claude', modelID: 'old-model');
    controller.rememberModelName('claude', 'old-model', 'Saved Claude model');
    controller.sessionsById['parent'] = Session(
      id: 'parent',
      selection: SessionSelection(model: previous),
    );
    expect(controller.displayModelForSession('parent'), previous);
    controller.sessionsById['parent'] = Session(id: 'parent');
    controller.status = StreamStatus.connecting;
    await _pump(tester, controller);
    await _openPlus(tester);
    expect(find.textContaining('Loading conversation selection'), findsNothing);
    // On the model chip and on the sheet's Model row.
    expect(find.text('Saved Claude model'), findsNWidgets(2));
    expect(controller.modelForSession('parent'), isNull);
    await tester.tapAt(const Offset(10, 10));
    await tester.pumpAndSettle();
    controller.sessionsById['parent'] = Session(
      id: 'parent',
      selection: SessionSelection(
        model: ModelRef(providerID: 'claude', modelID: 'new-model'),
      ),
    );
    controller.rememberModelName('claude', 'new-model', 'Fresh Claude model');
    controller.status = StreamStatus.connected;
    controller.notifyListeners();
    await tester.pump(const Duration(milliseconds: 400));
    await _openPlus(tester);
    expect(find.text('Saved Claude model'), findsNothing);
    expect(find.text('Fresh Claude model'), findsNWidgets(2));
    expect(find.textContaining('Refreshing'), findsNothing);
  });

  testWidgets('cold start without a saved model offers a choice immediately', (
    tester,
  ) async {
    final (controller, _) = await _boot();
    controller.ownsSelection = true;
    controller.status = StreamStatus.connecting;
    await _pump(tester, controller);
    await _openPlus(tester);
    expect(find.textContaining('Loading conversation selection'), findsNothing);
    expect(find.text('Choose a model'), findsWidgets);
  });

  testWidgets('cold start does not label automatic approval paused', (
    tester,
  ) async {
    final (controller, _) = await _boot();
    await _auto(controller);
    controller.status = StreamStatus.connecting;
    controller.busySessions = {'parent'};
    // Within the line's few seconds: it folds away by itself after them.
    await _pump(tester, controller, settle: false);
    expect(find.text('Auto-approve paused'), findsNothing);
    expect(find.text('Auto-approve is on'), findsOneWidget);
    controller.status = StreamStatus.disconnected;
    controller.notifyListeners();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Auto-approve paused'), findsOneWidget);
    // The connection's own grace clock runs out inside the test.
    await tester.pump(const Duration(seconds: 9));
  });

  testWidgets('an isolated walkthrough shows no line and no chips', (
    tester,
  ) async {
    final (controller, _) = await _boot(isolated: true);
    await _pump(tester, controller);
    expect(_line, findsNothing);
    expect(_chip, findsNothing);
  });

  group('the approvals sheet', () {
    Future<void> openSheet(WidgetTester tester) async {
      await _openPlus(tester);
      await _tapKey(tester, 'composer-setting-approval-settings');
      expect(find.byKey(const Key('session-approvals-sheet')), findsOneWidget);
    }

    testWidgets('is one short choice list that writes what the menu writes', (
      tester,
    ) async {
      final (controller, prefs) = await _boot();
      await _pump(tester, controller);
      await openSheet(tester);
      expect(
        find.descendant(
          of: find.byKey(const Key('session-approvals-sheet')),
          matching: find.text('Ask first'),
        ),
        findsOneWidget,
      );
      expect(find.text('Auto-approve this conversation'), findsOneWidget);
      expect(find.text('Approve everything'), findsOneWidget);
      expect(find.text('You answer each request.'), findsOneWidget);
      // Nothing that only applies to auto-approving is on screen yet.
      expect(find.byKey(const Key('approvals-inherit-switch')), findsNothing);
      expect(find.byKey(const Key('approvals-record')), findsNothing);
      expect(find.byKey(const Key('approvals-rules-note')), findsNothing);

      await _tapKey(tester, 'approvals-mode-auto');
      expect(controller.autoApprovalFor('parent').automatic, isTrue);
      expect(
        _stored(prefs).explicitFor('server-a', 'parent')?.mode,
        AutoApprovalMode.autoOnce,
      );
      expect(find.byKey(const Key('approvals-inherit-switch')), findsOneWidget);
      expect(find.byKey(const Key('approvals-rules-note')), findsOneWidget);
      await _tapKey(tester, 'approvals-inherit-switch');
      expect(
        controller.autoApprovalFor('parent').setting.inheritToChildren,
        isTrue,
      );
      await _tapKey(tester, 'approvals-mode-ask');
      expect(controller.autoApprovalFor('parent').automatic, isFalse);
      expect(find.byKey(const Key('approvals-inherit-switch')), findsNothing);
    });

    testWidgets('Approve everything asks once, in place', (tester) async {
      final (controller, _) = await _boot();
      await _pump(tester, controller);
      await openSheet(tester);
      await _tapKey(tester, 'approvals-mode-everything');
      expect(find.byKey(const Key('approval-mode-confirm')), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(controller.approvesEverything, isFalse);
      await _tapKey(tester, 'approvals-mode-everything');
      await tester.tap(find.text('Approve everything').last);
      await tester.pumpAndSettle();
      expect(controller.approvesEverything, isTrue);
      expect(find.text('Set by this server'), findsOneWidget);
    });

    testWidgets('an inherited mode is one quiet line', (tester) async {
      final (controller, _) = await _boot();
      controller.sessionsById['child'] = Session(
        id: 'child',
        title: 'Child',
        parentID: 'parent',
      );
      await controller.setSessionAutoApproval(
        'parent',
        const SessionAutoApproval(
          mode: AutoApprovalMode.autoOnce,
          inheritToChildren: true,
        ),
      );
      await _pump(tester, controller, session: 'child');
      await openSheet(tester);
      expect(
        tester
            .widget<Text>(
              find
                  .descendant(
                    of: find.byKey(const Key('approvals-set-by')),
                    matching: find.byType(Text),
                  )
                  .first,
            )
            .data,
        allOf(startsWith('Set by'), contains('Parent')),
      );
    });
  });
}
