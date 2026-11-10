import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/domain/agent_helper_status.dart';
import 'package:opencode_mobile/domain/server_trust.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Controller extends ConnectionController {
  _Controller(super.store);
  ConnectionStatusSnapshot snapshot = const ConnectionStatusSnapshot(
    phase: ConnectionStatusPhase.reconnecting,
    profileId: 'laptop',
    serverName: 'Laptop',
  );
  int retries = 0;
  AgentHelperStatus? helperStatus;
  @override
  Future<AgentHelperStatus?> readConnectionHelperExit() async => helperStatus;
  @override
  ConnectionStatusSnapshot get connectionStatus => snapshot;
  @override
  Future<void> retryConnection() async => retries++;
  void show(
    ConnectionStatusPhase phase, {
    bool token = false,
    bool retrying = false,
    DateTime? nextAttemptAt,
  }) {
    snapshot = ConnectionStatusSnapshot(
      phase: phase,
      profileId: 'laptop',
      serverName: 'Laptop',
      usesToken: token,
      retrying: retrying,
      nextAttemptAt: nextAttemptAt,
    );
    notifyListeners();
  }
}

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  // Longer than a page transition: Android's own is 450 ms (2026-10-26).
  await tester.pump(const Duration(milliseconds: 500));
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<_Controller> controller() async {
    SharedPreferences.setMockInitialValues({});
    final value = _Controller(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    );
    addTearDown(value.dispose);
    return value;
  }

  testWidgets(
    'unknown ownership banner offers settings without retry or restart',
    (tester) async {
      final c = await controller();
      c.show(ConnectionStatusPhase.notAnswering);
      var recovered = 0;
      var restarted = 0;
      KitStatus? status;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) {
              status = connectionKitStatus(
                context,
                c,
                serverOnThisPhone: true,
                onRestartServer: () async => restarted++,
                onRecoverOwnership: () => recovered++,
              );
              return const SizedBox();
            },
          ),
        ),
      );
      expect(status?.message, contains('Force stop'));
      expect(status?.action?.label, 'Open app settings');
      expect(status?.more, isEmpty);
      status?.action?.onPressed?.call();
      expect(recovered, 1);
      expect(restarted, 0);
      expect(c.retries, 0);
    },
  );

  testWidgets('this phone while its setup runs says so, not not answering', (
    tester,
  ) async {
    final c = await controller();
    c.show(ConnectionStatusPhase.notAnswering);
    KitStatus? status;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            status = connectionKitStatus(
              context,
              c,
              serverOnThisPhone: true,
              phoneSetupRunning: true,
              onRestartServer: () async {},
            );
            return const SizedBox();
          },
        ),
      ),
    );
    expect(status?.message, 'Setting up OpenCode on this phone');
    expect(status?.message, isNot(contains("isn't answering")));
    expect(status?.action, isNull);
  });

  testWidgets('a changed certificate shows no "isn\'t answering" line', (
    tester,
  ) async {
    final c = await controller();
    c.show(ConnectionStatusPhase.notAnswering);
    c.lastFailure = const ServerCertificateChangedException();
    KitStatus? status;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            status = connectionKitStatus(context, c);
            return const SizedBox();
          },
        ),
      ),
    );
    expect(status, isNull);
    c.lastFailure = null;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            status = connectionKitStatus(context, c);
            return const SizedBox();
          },
        ),
      ),
    );
    expect(status?.message, contains("isn't answering"));
  });

  testWidgets('Details retains BD13 unexpected helper exit after recovery', (
    tester,
  ) async {
    final c = await controller();
    c.helperStatus = AgentHelperStatus(
      running: true,
      lastExitAt: DateTime.utc(2026, 10, 9),
      lastExitCode: 137,
      lastStopRequested: false,
      possibleResourceKill: true,
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => KitScreen(
            body: KitButton.primary(
              label: 'Open details',
              onPressed: () => showConnectionDetailsSheet(context, c),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open details'));
    await _settle(tester);
    final fold = tester.widget<KitDetailsFold>(find.byType(KitDetailsFold));
    expect(fold.text, contains('2026-10-09T00:00:00.000Z'));
    expect(fold.text, contains('137'));
    expect(fold.text, contains('memory_or_phantom_kill (possible)'));
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'all routes share one connection phase and reveal lower notices in order',
    (tester) async {
      final c = await controller();
      final navigator = GlobalKey<NavigatorState>();
      final notices = ValueNotifier<List<KitStatus>>([
        const KitStatus(
          kind: KitStatusKind.appStopped,
          icon: Icons.info_outline,
          message: 'App stopped',
        ),
        const KitStatus(
          kind: KitStatusKind.heat,
          icon: Icons.info_outline,
          message: 'Phone hot',
        ),
        const KitStatus(
          kind: KitStatusKind.update,
          icon: Icons.info_outline,
          message: 'Update ready',
        ),
      ]);
      addTearDown(notices.dispose);
      var localAction = 0;
      await tester.pumpWidget(
        MaterialApp(
          navigatorKey: navigator,
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => ListenableBuilder(
            listenable: Listenable.merge([c, notices]),
            builder: (context, _) => AppConditionsScope(
              conditions: [
                connectionKitStatus(
                  context,
                  c,
                  actionContext: () => navigator.currentState?.overlay?.context,
                ),
                ...notices.value,
              ],
              child: child!,
            ),
          ),
          home: KitScreen(
            body: KitStatusContribution(
              status: KitStatus(
                kind: KitStatusKind.work,
                id: 'work:runaway',
                icon: AppIconography.info,
                message: 'Node is busy',
                action: KitAction(
                  label: 'Stop node',
                  onPressed: () => localAction++,
                ),
              ),
              child: const SizedBox.shrink(),
            ),
          ),
        ),
      );
      await _settle(tester);
      expect(find.text('Reconnecting…'), findsOneWidget);
      expect(find.byType(KitStatusLine), findsOneWidget);
      // The view owns no clock: elapsed time alone cannot change the snapshot.
      await tester.pump(const Duration(seconds: 9));
      expect(find.text('Reconnecting…'), findsOneWidget);
      c.show(ConnectionStatusPhase.notAnswering);
      await _settle(tester);
      expect(find.text("Laptop isn't answering"), findsOneWidget);
      navigator.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => const KitScreen(body: SizedBox()),
        ),
      );
      await _settle(tester);
      expect(find.text("Laptop isn't answering"), findsOneWidget);
      expect(find.byType(KitStatusLine), findsOneWidget);
      c.show(ConnectionStatusPhase.connected);
      await _settle(tester);
      expect(find.text('App stopped'), findsOneWidget);
      notices.value = notices.value.skip(1).toList();
      await _settle(tester);
      expect(find.text('Phone hot'), findsOneWidget);
      notices.value = notices.value.skip(1).toList();
      await _settle(tester);
      expect(find.text('Update ready'), findsOneWidget);
      navigator.currentState!.pop();
      await _settle(tester);
      expect(find.text('Node is busy'), findsOneWidget);
      expect(find.text('Update ready'), findsNothing);
      await tester.tap(find.text('Stop node'));
      expect(localAction, 1);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'connecting says it once, with no Reconnect action; busy is a quiet line',
    (tester) async {
      final c = await controller();
      Future<void> pump() async {
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: ListenableBuilder(
              listenable: c,
              builder: (context, _) => AppConditionsScope(
                conditions: [connectionKitStatus(context, c)],
                child: const KitScreen(body: SizedBox()),
              ),
            ),
          ),
        );
        await _settle(tester);
      }

      c.show(ConnectionStatusPhase.connecting);
      await pump();
      expect(find.text('Reconnecting…'), findsOneWidget);
      expect(find.textContaining('Reconnect to'), findsNothing);
      expect(
        find.byKey(const ValueKey('connection-banner-retry')),
        findsNothing,
      );
      c.show(ConnectionStatusPhase.serverBusy);
      await _settle(tester);
      expect(find.text('OpenCode on this phone is busy'), findsOneWidget);
      expect(find.text('Restart'), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('same words still replace retry action state', (tester) async {
    final c = await controller();
    c.show(ConnectionStatusPhase.notAnswering);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ListenableBuilder(
          listenable: c,
          builder: (context, _) => AppConditionsScope(
            conditions: [connectionKitStatus(context, c)],
            child: const KitScreen(body: SizedBox()),
          ),
        ),
      ),
    );
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('connection-banner-retry')));
    expect(c.retries, 1);
    c.show(ConnectionStatusPhase.notAnswering, retrying: true);
    await _settle(tester);
    expect(find.byKey(const ValueKey('connection-banner-retry')), findsNothing);
    c.show(ConnectionStatusPhase.credentialsRequired, token: true);
    await _settle(tester);
    expect(find.byKey(const ValueKey('banner-update-token')), findsOneWidget);
    expect(find.byKey(const ValueKey('connection-banner-retry')), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('local chat-style status preserves its controls in the slot', (
    tester,
  ) async {
    var restored = 0;
    var dismissed = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: KitScreen(
          status: KitStatus(
            kind: KitStatusKind.work,
            id: 'chat:undone',
            key: const ValueKey('chat-status-undone'),
            messageKey: const ValueKey('chat-status-message'),
            supportingKey: const ValueKey('chat-status-supporting'),
            supporting: 'Restore the turn',
            icon: Icons.history,
            message: 'Changes undone',
            action: KitAction(
              key: const ValueKey('chat-status-undo-put-back'),
              label: 'Put back',
              onPressed: () => restored++,
            ),
            dismissTooltip: 'Dismiss undo notice',
            onDismiss: () => dismissed++,
          ),
          body: const SizedBox(),
        ),
      ),
    );
    await _settle(tester);
    expect(find.byKey(const ValueKey('chat-status-undone')), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-status-message')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('chat-status-supporting')),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('chat-status-undo-put-back')));
    await tester.tap(find.byTooltip('Dismiss undo notice'));
    expect(restored, 1);
    expect(dismissed, 1);
    await tester.pumpWidget(const SizedBox());
  });

  group('the link retries on its own timer (vision 2026-10-20)', () {
    Future<KitStatus?> statusFor(WidgetTester tester, _Controller c) async {
      KitStatus? status;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Builder(
            builder: (context) {
              status = connectionKitStatus(context, c);
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      return status;
    }

    testWidgets('a scheduled try: muted, counts down, Try now first', (
      tester,
    ) async {
      final c = await controller();
      c.show(
        ConnectionStatusPhase.notAnswering,
        nextAttemptAt: DateTime.now().add(const Duration(seconds: 12)),
      );
      final status = (await statusFor(tester, c))!;
      expect(status.message, "Laptop isn't answering");
      // Nobody has to act: the neutral tone, never the failure glyph.
      expect(status.tone, AppStatusTone.neutral);
      expect(status.countdownTo, c.snapshot.nextAttemptAt);
      expect(
        status.countdownLine!(const Duration(milliseconds: 11200)),
        'trying again in 12 s',
      );
      expect(status.countdownLine!(Duration.zero), 'trying again now…');
      expect(status.quiet, isTrue);
      expect(status.action?.label, 'Try now');
      status.action!.onPressed!();
      expect(c.retries, 1);
      // No More: the words open Details.
      expect(status.more, isEmpty);
      expect(status.onOpen, isNotNull);
    });

    testWidgets('the countdown ticks on the line itself', (tester) async {
      final c = await controller();
      c.show(
        ConnectionStatusPhase.notAnswering,
        nextAttemptAt: DateTime.now().add(const Duration(seconds: 5)),
      );
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Builder(
              builder: (context) =>
                  KitStatusLine.of(connectionKitStatus(context, c)!),
            ),
          ),
        ),
      );
      final line = find.textContaining(
        "Laptop isn't answering · trying again in ",
      );
      expect(line, findsOneWidget);
      final before = tester.widget<Text>(line).data;
      expect(find.text('Try now'), findsOneWidget);
      // One caption line: no More.
      expect(find.byKey(const ValueKey('kit-status-more')), findsNothing);
      await tester.pump(const Duration(seconds: 1));
      expect(tester.widget<Text>(line).data, isNot(before));
      // The words open Details.
      await tester.tap(find.byKey(const ValueKey('kit-status-open')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('connection-banner-details-sheet')),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('nothing scheduled: no countdown, the old Reconnect', (
      tester,
    ) async {
      final c = await controller();
      c.show(ConnectionStatusPhase.notAnswering);
      final status = (await statusFor(tester, c))!;
      expect(status.countdownTo, isNull);
      expect(status.action?.label, isNot('Try now'));
    });

    testWidgets('a Try now in flight wins over the schedule', (tester) async {
      final c = await controller();
      c.show(
        ConnectionStatusPhase.notAnswering,
        retrying: true,
        nextAttemptAt: DateTime.now().add(const Duration(seconds: 8)),
      );
      final status = (await statusFor(tester, c))!;
      expect(status.countdownTo, isNull);
    });
  });
}
