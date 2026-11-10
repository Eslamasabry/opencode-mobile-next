import 'package:opencode_mobile/domain/server_gateway.dart';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/automation_policy.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_auto_approval.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart'
    show loadCaptureFonts, capturePng, writePng;
import 'support/complete_message_history.dart';
import 'support/composer_tools.dart';

const _capture = bool.fromEnvironment('E7_APPROVALS_CAPTURE');
final _boundary = GlobalKey();

Future<void> _captureScreen(WidgetTester tester, String name) async {
  if (!_capture) return;
  await writePng(
    'docs/qa/session-auto-approval/$name.png',
    await capturePng(tester, _boundary, pixelRatio: 1),
  );
}

class _FakeApi extends FakeServerGateway with CompleteMessageHistory {
  _FakeApi() : super(baseUrl: 'http://localhost');
  final replies = <(String, String)>[];
  Completer<void>? hold;
  Object? fail;

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    await hold?.future;
    if (fail case final error?) throw error;
    replies.add((requestID, reply));
  }

  // A signed-in model, so the status slot is free for the Auto-approve note
  // (the "No model signed in" note outranks it).
  @override
  Future<ProvidersResponse> providers() async => ProvidersResponse(
    providers: [
      ProviderInfo(id: 'anthropic', name: 'Anthropic', modelIDs: ['sonnet']),
    ],
  );

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');
}

/// The server is known to the store, as a saved profile is.
class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => [
    ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost'),
  ];
}

Future<(_Controller, _FakeApi)> _boot() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  // Since 9bcf3cca (enforce automation policy) a session's automatic
  // approval acts only when the server's automation policy allows it.
  await AutomationPolicyController.forProfile(
    prefs,
    'server-a',
  ).setSupervision(AutomationSupervision.balanced);
  final api = _FakeApi();
  // Requests arrive while the agent works, and the Auto-approve line
  // under the header shows only then (chat fidelity, 20 Oct 2026; idle,
  // the chip over the composer says it).
  final controller = _Controller(_Store(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected
    ..busySessions = {'parent', 'child'};
  addTearDown(controller.dispose);
  controller.sessionsById['parent'] = Session(id: 'parent', title: 'Parent');
  controller.sessionsById['child'] = Session(
    id: 'child',
    title: 'Child',
    parentID: 'parent',
  );
  return (controller, api);
}

Widget _app(
  ConnectionController controller,
  String sessionID,
  TextDirection direction,
) => ProviderScope(
  overrides: [connProvider.overrideWithValue(controller)],
  child: MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: const TextScaler.linear(2.5)),
      child: RepaintBoundary(
        key: _boundary,
        child: Directionality(textDirection: direction, child: child!),
      ),
    ),
    home: ChatScreen(sessionID: sessionID),
  ),
);

EventEnvelope _ask(String id, String session) => EventEnvelope(
  type: 'permission.asked',
  properties: {
    'id': id,
    'sessionID': session,
    'permission': 'bash',
    'patterns': ['git status'],
    'metadata': <String, Object?>{},
    'always': <String>[],
  },
);

Future<void> _openApprovals(WidgetTester tester) async {
  // Approvals is a command in the command sheet (slice-P10.1).
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  final commands = find.byKey(const Key('composer-tool-commands'));
  await tester.ensureVisible(commands);
  await tester.pumpAndSettle();
  await tester.tap(commands);
  await tester.pumpAndSettle();
  await tester.pumpAndSettle();
  // At 2.5x text on 320dp the kit sheet's header scrolls away with the body
  // (KitSheet: no room to keep it fixed), so the search starts below the
  // fold: bring it into view first.
  final search = find.byKey(
    const Key('command-launcher-search'),
    skipOffstage: false,
  );
  await tester.ensureVisible(search);
  await tester.pumpAndSettle();
  await tester.enterText(search, 'approvals');
  await tester.pump();
  final approvals = find.byKey(const Key('command-mobile-approvals'));
  await tester.ensureVisible(approvals);
  await tester.pumpAndSettle();
  await tester.tap(approvals);
  await tester.pumpAndSettle();
  expect(find.byKey(const Key('session-approvals-sheet')), findsOneWidget);
}

/// The approvals sheet has only the kit sheet's own Close. At 2.5x text the
/// header scrolls away with the body, so scroll back to the top first.
Future<void> _closeSheet(WidgetTester tester) async {
  final body = find
      .ancestor(
        of: find.byKey(const Key('session-approvals-sheet')),
        matching: find.byType(Scrollable),
      )
      .first;
  tester.state<ScrollableState>(body).position.jumpTo(0);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
  await tester.pumpAndSettle();
}

/// The one muted line under the header while Auto-approve is on.
final _line = find.byKey(const ValueKey('chat-status-auto-approve'));

/// The Auto-approve chip over the composer, first in the chips row.
final _chip = find.byKey(const Key('auto-approval-indicator'));

/// The composer's +, its More, then the "Approval settings…" row: the
/// detailed approvals sheet.
Future<void> _tapChip(WidgetTester tester) async {
  await _tapVisible(tester, find.byKey(const Key('composer-tools-button')));
  final more = find.byKey(const Key('composer-tray-more'));
  if (more.evaluate().isNotEmpty) await _tapVisible(tester, more);
  expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
  await _tapVisible(
    tester,
    find.byKey(const Key('composer-setting-approval-settings')),
  );
}

/// A choice row at 2.5x text is taller than the 740 dp window, so its
/// centre can be off screen: tap just inside its top edge.
Future<void> _tapChoice(WidgetTester tester, String key) async {
  final row = find.byKey(Key(key));
  await tester.ensureVisible(row);
  await tester.pumpAndSettle();
  final rect = tester.getRect(row);
  await tester.tapAt(Offset(rect.center.dx, rect.top + 48));
  await tester.pumpAndSettle();
}

Future<void> _tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  if (_capture) setUpAll(loadCaptureFonts);

  for (final direction in TextDirection.values) {
    testWidgets(
      '320dp 2.5x $direction approvals sheet, indicator and auto-approval record',
      (tester) async {
        tester.view.physicalSize = const Size(320, 740);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final (controller, api) = await _boot();
        await tester.pumpWidget(_app(controller, 'parent', direction));
        await tester.pumpAndSettle();

        // Default: asking, and nothing sits on screen to say so.
        expect(_line, findsNothing);

        await _openApprovals(tester);
        await _captureScreen(tester, 'sheet-ask-${direction.name}');
        // Subagent inheritance only shows once this conversation approves.
        final inherit = find.byKey(const Key('approvals-inherit-switch'));
        expect(inherit, findsNothing);

        // Choosing Auto-approve applies at once: no confirm step.
        await _tapChoice(tester, 'approvals-mode-auto');
        expect(controller.autoApprovalFor('parent').automatic, isTrue);
        expect(inherit, findsOneWidget);
        await _tapVisible(tester, find.text('Subagents follow this'));
        expect(
          controller.autoApprovalFor('parent').setting.inheritToChildren,
          isTrue,
        );
        expect(controller.autoApprovalFor('child').inheritedFrom, 'parent');
        // The one footnote says what holds.
        expect(find.textContaining('deny rules still apply'), findsOneWidget);
        await _captureScreen(tester, 'sheet-auto-${direction.name}');
        await _closeSheet(tester);
        expect(find.byKey(const Key('session-approvals-sheet')), findsNothing);

        // The chip over the composer names the mode while the setting is on,
        // beside Stop while the agent works, and carries no count of what it
        // approved. A 740 dp window has room for the chips, so the line under
        // the header does not say it twice (d12253567).
        expect(_chip, findsOneWidget);
        expect(find.text('Auto-approve on'), findsOneWidget);
        expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
        expect(_line, findsNothing);

        // A request is answered without a card, and the record names it.
        controller.handleEventForTesting(_ask('req-1', 'parent'));
        await tester.pumpAndSettle();
        expect(api.replies, [('req-1', 'once')]);
        expect(find.byKey(const Key('permission-card-review')), findsNothing);
        expect(find.text('Auto-approve · 1'), findsNothing);
        await _captureScreen(tester, 'indicator-${direction.name}');

        // The + reopens the sheet, which lists the record; turning the
        // switch off stops approval again.
        await _tapChip(tester);
        expect(
          find.byKey(const Key('session-approvals-sheet')),
          findsOneWidget,
        );
        // The history is one collapsed row; open it.
        await _tapVisible(tester, find.text('Approved automatically'));
        expect(
          find.textContaining('Auto-approved · Run a shell command'),
          findsOneWidget,
        );
        await _captureScreen(tester, 'sheet-record-${direction.name}');
        // Turning the switch off asks nothing: every request waits again.
        await _tapChoice(tester, 'approvals-mode-ask');
        expect(controller.autoApprovalFor('parent').automatic, isFalse);
        await _closeSheet(tester);
        expect(_line, findsNothing);
        controller.handleEventForTesting(_ask('req-2', 'parent'));
        await tester.pumpAndSettle();
        expect(api.replies, hasLength(1));
        expect(find.byKey(const Key('permission-card-review')), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      '320dp 2.5x $direction child session shows inheritance and can override',
      (tester) async {
        tester.view.physicalSize = const Size(320, 740);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final (controller, api) = await _boot();
        await controller.setSessionAutoApproval(
          'parent',
          const SessionAutoApproval(
            mode: AutoApprovalMode.autoOnce,
            inheritToChildren: true,
          ),
        );
        await tester.pumpWidget(_app(controller, 'child', direction));
        await tester.pumpAndSettle();

        // The sub-agent line keeps the one slot and carries the words.
        expect(find.text('Auto-approve is on'), findsOneWidget);
        await _captureScreen(tester, 'child-indicator-${direction.name}');

        await _tapChip(tester);
        // One quiet line says whose choice this is; nothing to override.
        expect(find.byKey(const Key('approvals-set-by')), findsOneWidget);
        expect(find.byKey(const Key('approvals-follow-parent')), findsNothing);
        await _captureScreen(tester, 'child-sheet-${direction.name}');

        // Choosing for this conversation makes it its own, here only.
        await _tapChoice(tester, 'approvals-mode-ask');
        expect(controller.autoApprovalFor('child').explicit, isTrue);
        expect(controller.autoApprovalFor('child').automatic, isFalse);
        expect(controller.autoApprovalFor('parent').automatic, isTrue);
        expect(find.byKey(const Key('approvals-set-by')), findsNothing);
        expect(
          find.byKey(const Key('approvals-follow-parent')),
          findsOneWidget,
        );

        // Following the parent again restores the inherited state.
        await _tapVisible(
          tester,
          find.byKey(const Key('approvals-follow-parent')),
        );
        expect(controller.autoApprovalFor('child').inheritedFrom, 'parent');
        expect(find.byKey(const Key('approvals-set-by')), findsOneWidget);
        await _closeSheet(tester);

        controller.handleEventForTesting(_ask('req-child', 'child'));
        await tester.pumpAndSettle();
        expect(api.replies, [('req-child', 'once')]);
        expect(find.byKey(const Key('permission-card-review')), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets('disconnecting pauses the line, which still folds away once '
      'seen', (tester) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval(
      'parent',
      const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
    );
    await tester.pumpWidget(_app(controller, 'parent', TextDirection.ltr));
    await tester.pumpAndSettle();
    expect(find.text('Auto-approve is on'), findsOneWidget);

    controller.status = StreamStatus.reconnecting;
    controller.notifyListeners();
    // The connection banner animates while reconnecting; pump a fixed frame.
    await tester.pump(const Duration(milliseconds: 400));
    expect(_line, findsOneWidget);
    expect(find.text('Auto-approve paused'), findsOneWidget);
    expect(tester.takeException(), isNull);
    // A request arriving now waits for a person: covered by the controller
    // test. (A permission card next to the reconnecting banner at 320dp/2.5x
    // is a pre-existing layout limit unrelated to this slice.)
    expect(api.replies, isEmpty);

    // After the 15-second quiet reconnect window nothing is approved by
    // itself: automatic approval stays gated on real reachability. The
    // line has said its piece and folded away (owner, 2026-10-27); the
    // mode stays where it lives (the chip, the + sheet).
    await tester.pump(const Duration(seconds: 16));
    await tester.pump(const Duration(milliseconds: 400));
    expect(_line, findsNothing);
    expect(controller.autoApprovalFor('parent').automatic, isTrue);
    expect(api.replies, isEmpty);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a failed automatic reply shows the request with the reason', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final (controller, api) = await _boot();
    await controller.setSessionAutoApproval(
      'parent',
      const SessionAutoApproval(mode: AutoApprovalMode.autoOnce),
    );
    api.fail = ApiException('server refused the reply');
    await tester.pumpWidget(_app(controller, 'parent', TextDirection.ltr));
    await tester.pumpAndSettle();

    controller.handleEventForTesting(_ask('req-1', 'parent'));
    await tester.pumpAndSettle();

    expect(api.replies, isEmpty);
    expect(find.byKey(const Key('permission-card-review')), findsOneWidget);
    expect(
      find.text('Automatic approval failed. Review this request.'),
      findsOneWidget,
    );
    // The card is the main thing while a person is needed; the line stays
    // under the header so the mode can still be turned off.
    expect(_line, findsOneWidget);
    expect(tester.takeException(), isNull);

    api.fail = null;
    await controller.answerPermission('req-1', 'reject');
    await tester.pumpAndSettle();
    expect(api.replies, [('req-1', 'reject')]);
    expect(find.byKey(const Key('permission-card-review')), findsNothing);
    expect(_line, findsOneWidget);
    expect(find.text('Auto-approve is on'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('approve everything is confirmed, saved, and can be declined', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final (controller, _) = await _boot();
    await tester.pumpWidget(_app(controller, 'parent', TextDirection.ltr));
    await tester.pumpAndSettle();
    await _openApprovals(tester);

    Future<void> tapEverything() =>
        _tapVisible(tester, find.byKey(const Key('approvals-mode-everything')));

    // Choosing it first states its scope in one sentence; Cancel changes
    // nothing.
    await tapEverything();
    expect(find.byKey(const Key('approval-mode-confirm')), findsOneWidget);
    expect(find.textContaining('without asking'), findsOneWidget);
    await _tapVisible(tester, find.text('Cancel'));
    expect(controller.approvesEverything, isFalse);

    // Approve everything applies to conversations that never had a setting.
    await tapEverything();
    await _tapVisible(tester, find.text('Approve everything').last);
    expect(controller.approvesEverything, isTrue);
    expect(controller.autoApprovalFor('parent').automatic, isTrue);
    expect(controller.autoApprovalFor('never-seen').automatic, isTrue);
    expect(find.text('Set by this server'), findsOneWidget);

    // Leaving it needs no confirmation and turns the server-wide switch off.
    await _tapChoice(tester, 'approvals-mode-ask');
    expect(controller.approvesEverything, isFalse);
    expect(controller.autoApprovalFor('never-seen').automatic, isFalse);
  });
}
