import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
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
  @override
  ConnectionStatusSnapshot get connectionStatus => snapshot;
  @override
  Future<void> retryConnection() async => retries++;
  void show(
    ConnectionStatusPhase phase, {
    bool token = false,
    bool retrying = false,
  }) {
    snapshot = ConnectionStatusSnapshot(
      phase: phase,
      profileId: 'laptop',
      serverName: 'Laptop',
      usesToken: token,
      retrying: retrying,
    );
    notifyListeners();
  }
}

Future<void> _settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 350));
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
      expect(find.text('Reconnecting to Laptop…'), findsOneWidget);
      expect(find.byType(KitStatusLine), findsOneWidget);
      // The view owns no clock: elapsed time alone cannot change the snapshot.
      await tester.pump(const Duration(seconds: 9));
      expect(find.text('Reconnecting to Laptop…'), findsOneWidget);
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
}
