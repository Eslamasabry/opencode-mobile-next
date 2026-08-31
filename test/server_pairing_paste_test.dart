import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Stands in for a real serve password. Never a live one.
const _password = 'Ig9K30-es1a7CqKcImcWUQcsghzCPYPKXHCB2c8Qtco';

String pairJson({
  List<String> urls = const ['http://127.0.0.1:4097'],
  String username = 'opencode',
  String password = _password,
}) =>
    '{"urls":${jsonEncode(urls)},"username":${jsonEncode(username)},'
    '"password":${jsonEncode(password)}}';

/// Presents no saved profiles without touching the real secure-storage
/// channel, which is unmocked in widget tests and would hang a real upsert.
class _EmptyStore extends ProfileStore {
  _EmptyStore({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];
}

void setClipboard(WidgetTester tester, String? text) {
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    SystemChannels.platform,
    (call) async {
      if (call.method == 'Clipboard.getData') {
        return text == null ? null : {'text': text};
      }
      return null;
    },
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    ),
  );
}

Future<void> pumpEditor(WidgetTester tester) async {
  // A surface tall enough that every field in the editor's ListView is built;
  // otherwise reading a controller off an unbuilt row fails for reasons that
  // have nothing to do with pairing.
  tester.view.physicalSize = const Size(1200, 2600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final store = _EmptyStore(prefs: prefs);
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
  // Open the editor from the first-run welcome card.
  await tester.tap(find.byKey(const ValueKey('welcome-connect-card')));
  await tester.pumpAndSettle();
  expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
}

String fieldText(WidgetTester tester, String key) =>
    tester.widget<TextField>(find.byKey(ValueKey(key))).controller!.text;

void main() {
  setUp(() {
    // Unmocked flutter_secure_storage reads hang inside testWidgets.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  tearDown(() {
    serverProbe = probeServerConnection;
    debugPlatformCapabilities = null;
  });

  testWidgets('pasting a pairing code fills url, username and password', (
    tester,
  ) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            const ServerProbeResult.success(
              '0.0.0-beta-18600',
              flavor: ServerFlavor.v2,
            );
    await pumpEditor(tester);
    setClipboard(tester, pairJson());

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    expect(fieldText(tester, 'server-url-field'), 'http://127.0.0.1:4097');
    expect(fieldText(tester, 'server-username-field'), 'opencode');
    expect(fieldText(tester, 'server-password-field'), _password);
  });

  testWidgets('the confirmation names the host it chose', (tester) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            baseUrl == 'https://desk.example:4097'
            ? const ServerProbeResult.success(
                '0.0.0-beta-18600',
                flavor: ServerFlavor.v2,
              )
            : const ServerProbeResult.failure('The connection was refused.');
    debugPlatformCapabilities = const PlatformCapabilities.android();
    await pumpEditor(tester);
    setClipboard(
      tester,
      pairJson(
        urls: ['http://127.0.0.1:4097', 'https://desk.example:4097'],
      ),
    );

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    final notice = find.byKey(const ValueKey('server-pairing-notice'));
    expect(notice, findsOneWidget);
    // Which host, so the user can tell a LAN pair from a bridged loopback.
    expect(
      find.textContaining('desk.example'),
      findsWidgets,
      reason: 'the chosen host must be named',
    );
    expect(fieldText(tester, 'server-url-field'), 'https://desk.example:4097');
  });

  testWidgets('the pairing password never appears in the confirmation', (
    tester,
  ) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            const ServerProbeResult.success('1.0.0', flavor: ServerFlavor.v2);
    await pumpEditor(tester);
    setClipboard(tester, pairJson());

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    // Every rendered string except the obscured password field itself.
    final texts = tester
        .widgetList<Text>(find.byType(Text))
        .map((t) => t.data ?? '')
        .toList();
    for (final text in texts) {
      expect(
        text,
        isNot(contains(_password)),
        reason: 'the serve password must never be rendered as plain text',
      );
    }
    // And it is in the password field, which is obscured.
    final field = tester.widget<TextField>(
      find.byKey(const ValueKey('server-password-field')),
    );
    expect(field.obscureText, isTrue);
    expect(field.controller!.text, _password);
  });

  testWidgets('a failure names every address and what happened to it', (
    tester,
  ) async {
    serverProbe =
        ({required baseUrl, username, password}) async => baseUrl.contains('4097')
            ? const ServerProbeResult.failure(
                'The connection was refused. Is opencode serve running on '
                'that host and port?',
                suggestsMissingServer: true,
              )
            : const ServerProbeResult.failure(
                'The connection timed out. Check the address, and that the '
                'server is reachable from this phone.',
                suggestsMissingServer: true,
              );
    debugPlatformCapabilities = const PlatformCapabilities.android();
    await pumpEditor(tester);
    setClipboard(
      tester,
      pairJson(
        urls: ['http://127.0.0.1:4097', 'https://desk.example:9999'],
      ),
    );

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('server-pairing-failure')),
      findsOneWidget,
    );
    // Per-address, not generic: the user has to know which one to fix.
    expect(find.textContaining('127.0.0.1:4097'), findsWidgets);
    expect(find.textContaining('refused'), findsWidgets);
    expect(find.textContaining('desk.example:9999'), findsWidgets);
    expect(find.textContaining('timed out'), findsWidgets);
    // And on a phone, what to actually do about it.
    expect(find.textContaining('adb reverse'), findsWidgets);
  });

  testWidgets('a cleartext LAN address is explained, not silently dropped', (
    tester,
  ) async {
    serverProbe = ({required baseUrl, username, password}) async {
      fail('A cleartext LAN address must never be dialed: $baseUrl');
    };
    await pumpEditor(tester);
    setClipboard(tester, pairJson(urls: ['http://192.0.2.20:4097']));

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('server-pairing-failure')),
      findsOneWidget,
    );
    expect(find.textContaining('HTTPS is required'), findsWidgets);
  });

  testWidgets('a malformed pairing code fails honestly without a crash', (
    tester,
  ) async {
    await pumpEditor(tester);
    for (final bad in <String>[
      '{"urls":[',
      '["http://127.0.0.1:4097"]',
      '{"urls":[],"username":"opencode","password":"$_password"}',
      '{"urls":["http://127.0.0.1:4097"]}',
      'x' * 20000,
    ]) {
      setClipboard(tester, bad);
      await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('server-pairing-failure')),
        findsOneWidget,
        reason: 'should have explained the failure for: '
            '${bad.length > 40 ? '${bad.substring(0, 40)}…' : bad}',
      );
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('an empty clipboard says so rather than failing silently', (
    tester,
  ) async {
    await pumpEditor(tester);
    setClipboard(tester, null);
    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('server-pairing-failure')),
      findsOneWidget,
    );
    expect(find.textContaining('clipboard is empty'), findsWidgets);
  });

  testWidgets('a pairing code pasted into the URL field is never left there', (
    tester,
  ) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            const ServerProbeResult.success('1.0.0', flavor: ServerFlavor.v2);
    await pumpEditor(tester);

    // The payload carries the password; the URL field must not hold it even
    // for a frame after the paste is understood.
    await tester.enterText(
      find.byKey(const ValueKey('server-url-field')),
      pairJson(),
    );
    await tester.pumpAndSettle();

    final url = fieldText(tester, 'server-url-field');
    expect(url, isNot(contains(_password)));
    expect(url, isNot(contains('"urls"')));
    expect(url, 'http://127.0.0.1:4097');
    expect(fieldText(tester, 'server-password-field'), _password);
  });

  testWidgets('a pairing code in the clipboard is honoured by the password '
      'paste button too', (tester) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            const ServerProbeResult.success('1.0.0', flavor: ServerFlavor.v2);
    await pumpEditor(tester);
    setClipboard(tester, pairJson());

    await tester.tap(find.byKey(const ValueKey('server-password-paste')));
    await tester.pumpAndSettle();

    // Not the raw JSON stuffed into the password field.
    expect(fieldText(tester, 'server-password-field'), _password);
    expect(fieldText(tester, 'server-url-field'), 'http://127.0.0.1:4097');
  });

  testWidgets('an ordinary password paste still works', (tester) async {
    await pumpEditor(tester);
    setClipboard(tester, '  server password $_password  ');
    await tester.tap(find.byKey(const ValueKey('server-password-paste')));
    await tester.pumpAndSettle();
    expect(fieldText(tester, 'server-password-field'), _password);
    expect(fieldText(tester, 'server-url-field'), isEmpty);
  });

  testWidgets('a server that answers with a stale password says so', (
    tester,
  ) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            const ServerProbeResult.failure(
              'Password rejected. Copy the current "server password" line '
              'from the server output — it changes on every restart unless '
              'OPENCODE_PASSWORD is set.',
              flavor: ServerFlavor.v2,
              needsPassword: true,
            );
    await pumpEditor(tester);
    setClipboard(tester, pairJson());

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    // The host was right, so the address is filled and the notice names it.
    expect(fieldText(tester, 'server-url-field'), 'http://127.0.0.1:4097');
    expect(find.byKey(const ValueKey('server-pairing-notice')), findsOneWidget);
    // The credential problem is the probe's verdict to report, through the
    // existing row — not a second pairing-specific error saying the same
    // thing, and not "no address answered", which would be a lie.
    expect(
      find.byKey(const ValueKey('server-pairing-failure')),
      findsNothing,
    );
    expect(find.byKey(const ValueKey('server-test-failure')), findsOneWidget);
    expect(find.textContaining('Password rejected'), findsWidgets);
  });

  testWidgets('a successful pair does not repeat the probe verdict', (
    tester,
  ) async {
    serverProbe =
        ({required baseUrl, username, password}) async =>
            const ServerProbeResult.success(
              '0.0.0-beta-18600',
              flavor: ServerFlavor.v2,
            );
    await pumpEditor(tester);
    setClipboard(tester, pairJson());

    await tester.tap(find.byKey(const ValueKey('server-pairing-paste')));
    await tester.pumpAndSettle();

    // One statement of the flavor and version, in the row that already
    // existed for it; the pairing notice adds only the chosen address.
    expect(find.text('OpenCode 2 · 0.0.0-beta-18600'), findsOneWidget);
    expect(find.text('Connected — save to finish.'), findsOneWidget);
    expect(find.text('Paired with 127.0.0.1.'), findsOneWidget);
  });

  testWidgets('the pairing action is on the editor for both platforms', (
    tester,
  ) async {
    for (final caps in const [
      PlatformCapabilities.android(),
      PlatformCapabilities.linuxDesktop(),
    ]) {
      debugPlatformCapabilities = caps;
      // Tear the previous tree down: an identical const MaterialApp would be
      // reused, leaving the pushed editor route in place.
      await tester.pumpWidget(const SizedBox.shrink());
      await pumpEditor(tester);
      expect(find.byKey(const ValueKey('server-pairing-actions')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('server-pairing-paste')),
        findsOneWidget,
        reason: 'paste pairing needs no permissions and is always offered',
      );
    }
  });
}
