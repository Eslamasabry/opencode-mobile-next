import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/connection_status.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The Keystore's own words when an entry can no longer be opened. They
/// must never reach the screen as copy (no raw errors).
const _keystoreFailure =
    'javax.crypto.AEADBadTagException: KeyStoreException: Signature/MAC '
    'verification failed';

const _secureStorage = MethodChannel(
  'plugins.it_nomads.com/flutter_secure_storage',
);

/// Never connects: the point is what the app says before any attempt.
class _RecordingConnection extends ConnectionController {
  _RecordingConnection(super.store);

  int connectCalls = 0;

  @override
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    connectCalls++;
  }
}

/// A real [ProfileStore] loading one saved OpenCode server whose password
/// the phone's secure storage can no longer read (a device restore or a
/// lock-screen change invalidates the Keystore key).
Future<ProfileStore> _storeWithUnreadablePassword(WidgetTester tester) async {
  final server = ServerProfile(
    id: 'server-1',
    name: 'Workstation',
    baseUrl: 'https://server.example:4096',
    username: 'opencode',
  );
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([server.toJson()]),
    'oc.activeProfile': server.id,
  });
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(_secureStorage, (call) async {
    if (call.method == 'read') {
      throw PlatformException(code: 'Exception', message: _keystoreFailure);
    }
    return null;
  });
  addTearDown(() => messenger.setMockMethodCallHandler(_secureStorage, null));
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await tester.runAsync(store.load);
  return store;
}

Widget _app(ProfileStore store, ConnectionController connection) =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(store)),
        connProvider.overrideWithValue(connection),
      ],
      child: const OcApp(),
    );

void main() {
  testWidgets(
    'an unreadable saved password asks for the password, never "not answering"',
    (tester) async {
      final store = await _storeWithUnreadablePassword(tester);
      expect(store.profiles.single.requiresPasswordReentry, isTrue);
      final connection = _RecordingConnection(store);
      addTearDown(connection.dispose);

      await tester.pumpWidget(_app(store, connection));
      await tester.pumpAndSettle();

      expect(connection.connectCalls, 0);
      expect(find.textContaining("isn't answering"), findsNothing);
      expect(
        connection.connectionStatus.phase,
        ConnectionStatusPhase.credentialsUnreadable,
      );
      expect(
        find.text("Can't read the saved password for Workstation"),
        findsOneWidget,
      );
      expect(find.textContaining("isn't answering"), findsNothing);
      expect(find.textContaining('Reconnect'), findsNothing);
      expect(find.textContaining('password changed'), findsNothing);
      // The line and the page say it once: no second notice under it.
      expect(find.textContaining('can no longer be read'), findsNothing);
      // The server's row says the same state in its words.
      expect(
        find.textContaining("Can't read the saved password"),
        findsWidgets,
      );
      expect(find.textContaining('AEADBadTag'), findsNothing);
      expect(find.textContaining('KeyStoreException'), findsNothing);

      // Details: why, in plain words, and that the password did not change.
      await tester.tap(find.byKey(const ValueKey('kit-status-more')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Details').last);
      await tester.pumpAndSettle();
      expect(find.textContaining('secure storage'), findsOneWidget);
      expect(
        find.textContaining('The password itself was not changed'),
        findsOneWidget,
      );
      expect(find.textContaining('AEADBadTag'), findsNothing);
      expect(find.textContaining('KeyStoreException'), findsNothing);
      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();

      // The action names its target and opens that server's password.
      await tester.tap(find.text('Enter the password').first);
      await tester.pumpAndSettle();
      expect(find.byType(ServersScreen, skipOffstage: false), findsWidgets);
      expect(find.text('Re-enter password'), findsWidgets);
      final focused = FocusManager.instance.primaryFocus?.context;
      expect(focused, isNotNull);
      final field = focused!.findAncestorWidgetOfExactType<TextField>();
      expect(field?.obscureText, isTrue, reason: 'the password is focused');
      expect(connection.connectCalls, 0);
    },
  );

  testWidgets('a rejected password keeps its own line and action', (
    tester,
  ) async {
    final server = ServerProfile(
      id: 'server-1',
      name: 'Workstation',
      baseUrl: 'https://server.example:4096',
      username: 'opencode',
    );
    SharedPreferences.setMockInitialValues({
      'oc.profiles': jsonEncode([server.toJson()]),
      'oc.activeProfile': server.id,
    });
    final messenger = tester.binding.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      _secureStorage,
      (call) async => call.method == 'read' ? 'secret' : null,
    );
    addTearDown(() => messenger.setMockMethodCallHandler(_secureStorage, null));
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await tester.runAsync(store.load);
    expect(store.profiles.single.requiresPasswordReentry, isFalse);
    final connection = _RecordingConnection(store);
    addTearDown(connection.dispose);

    connection.passwordRejected = true;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: ConnectionStatusBanner(controller: connection)),
      ),
    );
    await tester.pump();

    expect(
      connection.connectionStatus.phase,
      ConnectionStatusPhase.credentialsRequired,
    );
    expect(
      find.textContaining('changed. Update it to reconnect.'),
      findsOneWidget,
    );
    expect(find.text('Update password'), findsOneWidget);
    expect(find.textContaining("Can't read the saved password"), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
