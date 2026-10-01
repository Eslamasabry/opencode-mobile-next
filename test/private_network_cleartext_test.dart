import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/platform/connection_advice.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/pairing.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/first_run_path.dart';
import 'support/server_editor.dart';

const _channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
const _password = 'fixture-not-a-live-serve-password-000000000';

class _EmptyStore extends ProfileStore {
  _EmptyStore({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_channel, (_) async => null);
  });
  tearDown(() => serverProbe = probeServerConnection);

  group('isPrivateNetworkHost', () {
    test('private IPv4 ranges are in, their neighbours are out', () {
      for (final host in [
        '10.0.0.1',
        '10.255.255.254',
        '172.16.0.1',
        '172.31.255.255',
        '192.168.0.1',
        '192.168.255.255',
        '169.254.1.1',
      ]) {
        expect(isPrivateNetworkHost(host), isTrue, reason: host);
      }
      for (final host in [
        '9.255.255.255',
        '11.0.0.1',
        '172.15.255.255',
        '172.32.0.1',
        '192.167.1.1',
        '192.169.0.1',
        '169.253.1.1',
        '169.255.0.1',
        '8.8.8.8',
        '192.0.2.4',
        '100.64.0.1',
        '127.0.0.1',
        '0.0.0.0',
      ]) {
        expect(isPrivateNetworkHost(host), isFalse, reason: host);
      }
    });

    test('IPv6 unique-local and link-local are in, the rest is out', () {
      for (final host in [
        'fc00::1',
        'fd12:3456::1',
        '[fd12:3456::1]',
        'fe80::1',
        'febf::1',
        'fe80::1%wlan0',
        '::ffff:192.168.1.5',
      ]) {
        expect(isPrivateNetworkHost(host), isTrue, reason: host);
      }
      for (final host in [
        'fbff::1',
        'fec0::1',
        '2001:db8::1',
        '2606:4700::1111',
        '::1',
        '::',
        '::ffff:8.8.8.8',
      ]) {
        expect(isPrivateNetworkHost(host), isFalse, reason: host);
      }
    });

    test('only .local names count as private names', () {
      expect(isPrivateNetworkHost('desk.local'), isTrue);
      expect(isPrivateNetworkHost('Desk.Local.'), isTrue);
      expect(isPrivateNetworkHost('my.desk.local'), isTrue);
      for (final host in [
        'local',
        '.local',
        'a..local',
        'desk.localhost',
        'desk.local.example.com',
        'example.com',
        'router',
        'localhost',
        'pc.tail1234.ts.net',
        '10.0.0.1.example.com',
        '',
      ]) {
        expect(isPrivateNetworkHost(host), isFalse, reason: host);
      }
    });
  });

  group('validateServerProfileUrl', () {
    test('plain http to a private address is accepted, with credentials', () {
      for (final url in [
        'http://192.168.1.20:4096',
        'http://10.1.2.3:4096',
        'http://172.20.0.5:4096',
        'http://169.254.10.10:4096',
        'http://[fd00::5]:4096',
        'http://[fe80::5]:4096',
        'http://desk.local:4096',
      ]) {
        expect(validateServerProfileUrl(url), isNull, reason: url);
        expect(
          validateServerProfileUrl(url, username: 'opencode', password: 'x'),
          isNull,
          reason: url,
        );
        expect(serverUrlNeedsCleartextConfirmation(url), isTrue, reason: url);
      }
    });

    test('public addresses and other names stay refused over http', () {
      for (final url in [
        'http://192.0.2.4:4096',
        'http://8.8.8.8:4096',
        'http://172.32.0.1:4096',
        'http://192.169.1.1:4096',
        'http://[2001:db8::1]:4096',
        'http://server.example:4096',
        'http://router:4096',
        'http://100.64.0.1:4096',
      ]) {
        expect(
          validateServerProfileUrl(url),
          contains('HTTP is allowed only'),
          reason: url,
        );
        expect(
          validateServerProfileUrl(url, username: 'u', password: 'p'),
          contains('Basic credentials'),
          reason: url,
        );
      }
      expect(
        validateServerProfileUrl('http://192.0.2.4'),
        contains('Use HTTPS'),
      );
    });

    test('loopback and https never need the confirmation', () {
      for (final url in [
        'http://127.0.0.1:4096',
        'http://localhost:4096',
        'http://[::1]:4096',
        'https://192.168.1.20:4096',
        'https://desk.local',
      ]) {
        expect(serverUrlNeedsCleartextConfirmation(url), isFalse, reason: url);
      }
    });

    test('a bare private address still gains https, not http', () {
      expect(
        normalizeServerProfileUrl('192.168.1.20:4096'),
        'https://192.168.1.20:4096',
      );
      expect(normalizeServerProfileUrl('desk.local'), 'https://desk.local');
    });

    test('the confirmation is recorded against one exact origin', () {
      expect(
        cleartextOriginOf('HTTP://192.168.1.20:4096/'),
        'http://192.168.1.20:4096',
      );
      expect(
        cleartextOriginOf('http://192.168.1.20'),
        'http://192.168.1.20:80',
      );
      expect(cleartextOriginOf('http://[fd00::5]:1'), 'http://[fd00::5]:1');
    });

    test('connection advice tells a private http address apart', () {
      expect(
        explainConnectionAddress('http://192.168.1.20:4096'),
        ConnectionAdvice.privateHttp,
      );
      expect(
        explainConnectionAddress('http://192.0.2.4:4096'),
        ConnectionAdvice.remoteHttp,
      );
      expect(
        explainConnectionAddress('http://127.0.0.1:4096'),
        ConnectionAdvice.loopback,
      );
    });
  });

  group('Codex and Paseo stay HTTPS-only off this device', () {
    test('a private address is refused over ws://', () {
      expect(validateCodexServerUrl('ws://192.168.1.20:4099'), isNotNull);
      expect(validatePaseoServerUrl('ws://192.168.1.20:6767'), isNotNull);
      expect(
        () => codexEndpoint('ws://192.168.1.20:4099'),
        throwsA(isA<CodexFailure>()),
      );
      expect(validateCodexServerUrl('wss://192.168.1.20:4099'), isNull);
    });
  });

  group('pairing', () {
    PairingPayload payload(List<String> urls) => parsePairingPayload(
      jsonEncode({'urls': urls, 'username': 'opencode', 'password': _password}),
    ).payload!;

    test(
      'a private http address is held, never probed, until confirmed',
      () async {
        final probed = <String>[];
        Future<ServerProbeResult> probe({
          required String baseUrl,
          String? username,
          String? password,
        }) async {
          probed.add(baseUrl);
          return const ServerProbeResult.success(
            '1.0.0',
            flavor: ServerFlavor.v2,
          );
        }

        final held = await selectPairingUrl(
          payload(['http://192.168.1.20:4097']),
          probe: probe,
        );
        expect(probed, isEmpty);
        expect(held.ok, isFalse);
        expect(held.outcomes.single.needsCleartextConfirm, isTrue);
        expect(held.outcomes.single.probed, isFalse);
        expect(held.outcomes.single.url, 'http://192.168.1.20:4097');

        final confirmed = await selectPairingUrl(
          payload(['http://192.168.1.20:4097']),
          probe: probe,
          confirmedCleartextOrigins: {'http://192.168.1.20:4097'},
        );
        expect(probed, ['http://192.168.1.20:4097']);
        expect(confirmed.chosenUrl, 'http://192.168.1.20:4097');
      },
    );

    test(
      'a different private origin is not covered by the confirmation',
      () async {
        final selection = await selectPairingUrl(
          payload(['http://192.168.1.21:4097']),
          probe: ({required baseUrl, username, password}) async =>
              fail('must not be dialed: $baseUrl'),
          confirmedCleartextOrigins: {'http://192.168.1.20:4097'},
        );
        expect(selection.outcomes.single.needsCleartextConfirm, isTrue);
      },
    );

    test('public http addresses are still refused outright', () async {
      final selection = await selectPairingUrl(
        payload(['http://192.0.2.20:4097']),
        probe: ({required baseUrl, username, password}) async =>
            fail('must not be dialed: $baseUrl'),
        confirmedCleartextOrigins: {'http://192.0.2.20:4097'},
      );
      final outcome = selection.outcomes.single;
      expect(outcome.needsCleartextConfirm, isFalse);
      expect(outcome.probed, isFalse);
      expect(outcome.reason, contains('HTTPS is required'));
    });
  });

  group('stored confirmation', () {
    Future<ProfileStore> newStore([Map<String, Object> seed = const {}]) async {
      SharedPreferences.setMockInitialValues(seed);
      return ProfileStore(prefs: await SharedPreferences.getInstance());
    }

    ServerProfile profile(String url, {String id = 'p1'}) =>
        ServerProfile(id: id, name: 'Desk', baseUrl: url, password: 'pw');

    test('an unconfirmed private http profile is blocked', () async {
      final store = await newStore();
      final saved = profile('http://192.168.1.20:4096');
      expect(saved.cleartextUnconfirmed, isTrue);
      await store.upsert(saved);
      expect(store.prefs.getKeys(), isNot(contains('oc.cleartextOk.p1')));
      expect(saved.cleartextUnconfirmed, isTrue);
      expect(
        profile('https://192.168.1.20:4096').cleartextUnconfirmed,
        isFalse,
      );
      expect(profile('http://127.0.0.1:4096').cleartextUnconfirmed, isFalse);
    });

    test('confirming survives a rebuilt profile and a reload', () async {
      final store = await newStore();
      await store.upsert(
        profile('http://192.168.1.20:4096')
          ..cleartextConfirmedOrigin = 'http://192.168.1.20:4096',
      );
      expect(
        store.prefs.getString('oc.cleartextOk.p1'),
        'http://192.168.1.20:4096',
      );
      // An editor that rebuilds the profile cannot erase it by accident.
      final rebuilt = profile('http://192.168.1.20:4096');
      await store.upsert(rebuilt);
      expect(rebuilt.cleartextUnconfirmed, isFalse);

      final reloaded = ProfileStore(prefs: store.prefs);
      final loaded = (await reloaded.load()).single;
      expect(loaded.cleartextUnconfirmed, isFalse);
    });

    test(
      'moving the profile to another address drops the confirmation',
      () async {
        final store = await newStore();
        await store.upsert(
          profile('http://192.168.1.20:4096')
            ..cleartextConfirmedOrigin = 'http://192.168.1.20:4096',
        );
        final moved = profile('http://192.168.1.99:4096');
        await store.upsert(moved);
        expect(moved.cleartextUnconfirmed, isTrue);
        expect(store.prefs.getKeys(), isNot(contains('oc.cleartextOk.p1')));
      },
    );

    test('the deletion sweep finds and removes the key', () async {
      final store = await newStore();
      await store.upsert(
        profile('http://192.168.1.20:4096')
          ..cleartextConfirmedOrigin = 'http://192.168.1.20:4096',
      );
      expect(
        store.profileScopedPreferenceKeys('p1'),
        contains('oc.cleartextOk.p1'),
      );
      expect(await store.removeScopedPreferences('p1'), isEmpty);
      expect(store.prefs.getKeys(), isNot(contains('oc.cleartextOk.p1')));
    });

    test('connect refuses an unconfirmed profile in plain words', () async {
      final store = await newStore();
      final connection = ConnectionController(store);
      final unconfirmed = profile('http://192.168.1.20:4096');
      await connection.connect(unconfirmed);
      expect(connection.lastError, cleartextUnconfirmedMessage);
      expect(connection.api, isNull);
    });
  });

  group('add server', () {
    Future<void> pumpEditor(WidgetTester tester) async {
      tester.view.physicalSize = const Size(1200, 2600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      SharedPreferences.setMockInitialValues({});
      final store = _EmptyStore(prefs: await SharedPreferences.getInstance());
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(ConnectionController(store)),
          ],
          child: const MaterialApp(home: ServersScreen()),
        ),
      );
      await tester.pumpAndSettle();
      await openFirstRunConnect(tester);
      await openServerManualAddress(tester);
    }

    testWidgets('the warning shows and nothing is checked until confirmed', (
      tester,
    ) async {
      final probed = <String>[];
      serverProbe = ({required baseUrl, username, password}) async {
        probed.add(baseUrl);
        return const ServerProbeResult.success(
          '1.0.0',
          flavor: ServerFlavor.v2,
        );
      };
      await pumpEditor(tester);
      await tester.enterText(
        find.byKey(const ValueKey('server-url-field')),
        'http://192.168.1.20:4096',
      );
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('server-cleartext-warning')),
        findsOneWidget,
      );
      expect(find.textContaining('plain HTTP'), findsWidgets);
      expect(find.text('Use it anyway'), findsOneWidget);

      // Test connection before confirming sends nothing.
      await tester.tap(find.byKey(const ValueKey('test-server-connection')));
      await tester.pumpAndSettle();
      expect(probed, isEmpty);

      await tester.tap(find.byKey(const ValueKey('server-cleartext-confirm')));
      await tester.pumpAndSettle();
      expect(find.text('Use it anyway'), findsNothing);
      expect(
        find.byKey(const ValueKey('server-cleartext-warning')),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const ValueKey('test-server-connection')));
      await tester.pumpAndSettle();
      expect(probed, ['http://192.168.1.20:4096']);
    });

    testWidgets('https, loopback and public http show no warning', (
      tester,
    ) async {
      await pumpEditor(tester);
      for (final url in [
        'https://192.168.1.20:4096',
        'http://127.0.0.1:4096',
        'http://192.0.2.4:4096',
      ]) {
        await tester.enterText(
          find.byKey(const ValueKey('server-url-field')),
          url,
        );
        await tester.pumpAndSettle();
        expect(
          find.byKey(const ValueKey('server-cleartext-warning')),
          findsNothing,
          reason: url,
        );
      }
    });

    testWidgets('a pairing code with a private http address asks first', (
      tester,
    ) async {
      final probed = <String>[];
      serverProbe = ({required baseUrl, username, password}) async {
        probed.add('$baseUrl|$password');
        return const ServerProbeResult.success(
          '1.0.0',
          flavor: ServerFlavor.v2,
        );
      };
      await pumpEditor(tester);
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async => call.method == 'Clipboard.getData'
            ? {
                'text': jsonEncode({
                  'urls': ['http://192.168.1.20:4097'],
                  'username': 'opencode',
                  'password': _password,
                }),
              }
            : null,
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
      await tester.pumpAndSettle();

      expect(probed, isEmpty);
      expect(
        find.byKey(const ValueKey('server-cleartext-warning')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('server-pairing-failure')),
        findsNothing,
      );

      await tester.tap(find.byKey(const ValueKey('server-cleartext-confirm')));
      await tester.pumpAndSettle();
      expect(probed, ['http://192.168.1.20:4097|$_password']);
    });
  });
}
