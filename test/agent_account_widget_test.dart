import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/agent_account.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/agent_account.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/agent_account_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts, capturePng;
import 'support/account_fakes.dart';

class _Store extends ProfileStore {
  _Store({required super.prefs});
  final saved = ServerProfile(
    id: 'account-fixture',
    name: 'My Codex host',
    baseUrl: 'https://fixture.invalid',
    backend: ServerBackend.codex,
  );
  @override
  List<ServerProfile> get profiles => [saved];
  @override
  String? get activeId => saved.id;
}

class _Gateway extends OpenCodeApi implements AgentAccountGateway {
  _Gateway() : super(baseUrl: 'https://fixture.invalid');
  bool accountEnabled = true;
  final opened = <FakeAccountSession>[];
  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(agentAccount: accountEnabled);
  @override
  AgentAccountSession openAccountSession() {
    final session = FakeAccountSession();
    opened.add(session);
    return session;
  }
}

class _Connection extends ConnectionController {
  _Connection(super.store) : super(isIsolated: true);
  void changed() => notifyListeners();
}

Widget _app(
  Widget child, {
  bool dark = false,
  double scale = 1,
  GlobalKey? boundary,
}) => MaterialApp(
  theme: dark ? AppTheme.dark() : AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: child!,
  ),
  home: boundary == null ? child : RepaintBoundary(key: boundary, child: child),
);

Future<void> _tap(WidgetTester tester, String label) async {
  final finder = find.text(label);
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder.hitTestable());
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  testWidgets(
    'explicit sign-in opens reviewed official host and cancel removes code',
    (tester) async {
      final session = FakeAccountSession();
      final controller = AgentAccountController(session);
      await controller.refresh();
      await tester.pumpWidget(
        _app(
          AgentAccountPanel(
            controller: controller,
            profileName: 'My Codex host',
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(session.starts, 0);
      await _tap(tester, 'Sign in with ChatGPT');
      expect(find.text('Sign-in in progress'), findsOneWidget);
      expect(find.text('Ready to sign in'), findsNothing);
      expect(find.text('TEST-1234'), findsOneWidget);
      await _tap(tester, 'Open official sign-in');
      expect(find.text('Open external link?'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.text('auth.openai.com'),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find.byType(AlertDialog),
          matching: find.text('TEST-1234'),
        ),
        findsNothing,
      );
      await _tap(tester, 'Cancel');
      await _tap(tester, 'Cancel sign-in');
      expect(find.text('TEST-1234'), findsNothing);
      expect(find.text('Sign-in cancelled'), findsOneWidget);
      expect(session.starts, 1);
      expect(session.cancels, 1);
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
    },
  );

  testWidgets('rate and usage failures remain independently unavailable', (
    tester,
  ) async {
    final session = FakeAccountSession()
      ..account = fixtureSignedIn
      ..tokens = const AccountTokenUsage(lifetimeTokens: 0);
    session.onLimits = () async =>
        throw const AgentAccountException(AgentAccountFailure.unavailable);
    final controller = AgentAccountController(session);
    await controller.refresh();
    await tester.pumpWidget(
      _app(
        AgentAccountPanel(controller: controller, profileName: 'My Codex host'),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Signed in on the host'), findsOneWidget);
    expect(
      find.text('Rate limits are unavailable for this account or host.'),
      findsOneWidget,
    );
    await tester.ensureVisible(find.text('Lifetime tokens'));
    await tester.pumpAndSettle();
    expect(find.text('0'), findsOneWidget);
    expect(find.text('Peak daily tokens'), findsNothing);
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });

  testWidgets(
    'Servers account entry reaches the panel through its capability',
    (tester) async {
      final store = _Store(prefs: await SharedPreferences.getInstance());
      final gateway = _Gateway();
      final connection = _Connection(store)
        ..api = gateway
        ..status = StreamStatus.connected;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            bootstrapProvider.overrideWithValue(AppBootstrap(store)),
            connProvider.overrideWithValue(connection),
          ],
          child: _app(const ServersScreen()),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byType(PopupMenuButton<String>).first);
      await tester.pumpAndSettle();
      await _tap(tester, 'Codex account');
      expect(find.text('Ready to sign in'), findsOneWidget);
      expect(gateway.opened.single.starts, 0);
      await tester.pumpWidget(const SizedBox());
      connection.dispose();
    },
  );

  testWidgets(
    'scope change drops pending code and never binds replacement account',
    (tester) async {
      final store = _Store(prefs: await SharedPreferences.getInstance());
      final gateway = _Gateway();
      final connection = _Connection(store)
        ..api = gateway
        ..status = StreamStatus.connected;
      await tester.pumpWidget(_app(AgentAccountScreen(connection: connection)));
      await tester.pumpAndSettle();
      await _tap(tester, 'Sign in with ChatGPT');
      expect(find.text('TEST-1234'), findsOneWidget);
      connection.locationRevision++;
      connection.changed();
      await tester.pumpAndSettle();
      expect(find.text('TEST-1234'), findsNothing);
      expect(find.textContaining('This connection changed.'), findsOneWidget);
      expect(gateway.opened.single.closed, isTrue);
      connection.changed();
      await tester.pumpAndSettle();
      expect(gateway.opened.length, 1);
      await tester.pumpWidget(const SizedBox());
      connection.dispose();
    },
  );

  testWidgets(
    'same-gateway reconnect reads fresh account without replaying sign-in',
    (tester) async {
      final store = _Store(prefs: await SharedPreferences.getInstance());
      final gateway = _Gateway();
      final connection = _Connection(store)
        ..api = gateway
        ..status = StreamStatus.connected;
      await tester.pumpWidget(_app(AgentAccountScreen(connection: connection)));
      await tester.pumpAndSettle();
      await _tap(tester, 'Sign in with ChatGPT');
      final old = gateway.opened.single;
      old.active = false;
      old.notifications.add(const AccountEvent(AccountEventKind.disconnected));
      connection.status = StreamStatus.disconnected;
      connection.changed();
      await tester.pumpAndSettle();
      expect(find.text('TEST-1234'), findsNothing);
      connection.status = StreamStatus.connected;
      connection.changed();
      await tester.pumpAndSettle();
      expect(gateway.opened.length, 2);
      expect(gateway.opened.last.reads, 1);
      expect(gateway.opened.last.starts, 0);
      expect(find.text('Ready to sign in'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      connection.dispose();
    },
  );

  testWidgets(
    'browser round trip keeps explicit device attempt and refreshes on resume',
    (tester) async {
      final store = _Store(prefs: await SharedPreferences.getInstance());
      final gateway = _Gateway();
      final connection = _Connection(store)
        ..api = gateway
        ..status = StreamStatus.connected;
      await tester.pumpWidget(_app(AgentAccountScreen(connection: connection)));
      await tester.pumpAndSettle();
      await _tap(tester, 'Sign in with ChatGPT');
      final session = gateway.opened.single;
      final before = session.reads;
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
        await tester.pump();
      }
      await tester.pumpAndSettle();
      expect(session.reads, greaterThan(before));
      expect(session.starts, 1);
      expect(find.text('TEST-1234'), findsOneWidget);
      session.account = fixtureSignedIn;
      session.notifications.add(
        const AccountEvent(AccountEventKind.loginCompleted, success: true),
      );
      await tester.pumpAndSettle();
      expect(find.text('TEST-1234'), findsNothing);
      expect(find.text('Signed in on the host'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      connection.dispose();
    },
  );

  for (final state in [
    'signed-out',
    'waiting',
    'usage',
    'partial',
    'uncertain',
    'error',
    'unsupported',
    'disconnected',
    'loading',
  ]) {
    for (final style in ['light', 'dark', 'large']) {
      if (style != 'light' && !['waiting', 'usage'].contains(state)) continue;
      testWidgets('production capture $state $style', (tester) async {
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final session = FakeAccountSession();
        final controller = AgentAccountController(session);
        if (state == 'usage' || state == 'partial') {
          session.account = fixtureSignedIn;
          session.buckets = [
            AccountRateBucket(
              'Codex',
              AccountRateWindow(32, 300, DateTime.utc(2026, 9, 8, 18)),
              const AccountRateWindow(68, 10080, null),
            ),
          ];
          if (state == 'usage') {
            session.tokens = const AccountTokenUsage(
              lifetimeTokens: 184250,
              peakDailyTokens: 12640,
            );
          }
        }
        if (state == 'unsupported') session.supported = false;
        if (state == 'error') {
          session.onRead = () async => throw const AgentAccountException(
            AgentAccountFailure.invalidResponse,
          );
        }
        if (state != 'loading') await controller.refresh();
        if (state == 'waiting' || state == 'uncertain') {
          await controller.signIn();
          if (state == 'uncertain') {
            session.onCancel = () async => false;
            await controller.cancel();
          }
        }
        if (state == 'disconnected') {
          session.active = false;
          controller.invalidate();
        }
        final key = GlobalKey();
        await tester.pumpWidget(
          _app(
            AgentAccountPanel(
              controller: controller,
              profileName: 'My Codex host',
            ),
            dark: style == 'dark',
            scale: style == 'large' ? 1.8 : 1,
            boundary: key,
          ),
        );
        await tester.pump(const Duration(milliseconds: 250));
        expect(tester.takeException(), isNull);
        if (state == 'uncertain') {
          expect(find.text('Sign-in needs attention'), findsOneWidget);
          expect(find.text('Ready to sign in'), findsNothing);
        }
        if (state == 'usage') {
          expect(find.text('5-hour window'), findsOneWidget);
          expect(find.text('7-day window'), findsOneWidget);
        }
        final out = Platform.environment['CODEX_ACCOUNT_CAPTURE_DIR'];
        if (out != null) {
          final bytes = await capturePng(tester, key, pixelRatio: 1);
          final path = File('$out/$state-$style.png');
          path.parent.createSync(recursive: true);
          path.writeAsBytesSync(bytes);
        }
        if (['waiting', 'usage', 'partial'].contains(state)) {
          final target = state == 'waiting' ? 'Cancel sign-in' : 'Token usage';
          await tester.ensureVisible(find.text(target));
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          if (out != null) {
            File(
              '$out/$state-$style-detail.png',
            ).writeAsBytesSync(await capturePng(tester, key, pixelRatio: 1));
          }
        }
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
      });
    }
  }
}
