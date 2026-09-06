import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show Health;
import 'package:opencode_mobile/domain/provider_quota.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/provider_quota_overview.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/provider_quota_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../tool/capture/fixtures.dart'
    show capturePng, captureTheme, loadCaptureFonts;
import 'provider_quota_test.dart' show providerQuotaFixture;

final _epoch = DateTime.utc(2026, 9, 6, 12);
final _l10n = lookupAppLocalizations(const Locale('en'));
const _password = 'fixture-only-quota-screen-password';
const _privateError = 'fixture-private-provider-error';
const _origin = 'https://collector.example:8443';

class _NoHttp extends HttpOverrides {
  int clients = 0;

  @override
  HttpClient createHttpClient(SecurityContext? context) {
    clients++;
    throw StateError('Quota widget tests must not create HTTP clients');
  }
}

class _Gateway implements ProviderQuotaGateway {
  final result = Completer<ProviderQuotaSnapshot>();
  int reads = 0;
  int closes = 0;

  @override
  Future<ProviderQuotaSnapshot> readSnapshot() {
    reads++;
    return result.future;
  }

  @override
  void close() {
    closes++;
    // Intentionally still completable: cancellation must also reject late data.
  }
}

class _HealthGateway implements ServerGateway {
  int healthCalls = 0;

  @override
  Future<Health> health() async {
    healthCalls++;
    return Health(healthy: true, version: 'fixture-v1');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Unexpected OpenCode call in a quota widget test');
}

class _Connection extends ConnectionController {
  _Connection(super.store);

  final healthGateway = _HealthGateway();
  bool allowSettingsHealth = false;
  int transportCalls = 0;
  int repositoryCalls = 0;
  int wakeCalls = 0;

  @override
  Future<ServerGateway?> prepareActionTransport() async {
    transportCalls++;
    if (allowSettingsHealth) return healthGateway;
    throw StateError('Quota must not use the OpenCode action transport');
  }

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    repositoryCalls++;
    throw StateError('Quota must not read OpenCode provider configuration');
  }

  @override
  Future<void> resumeFromLifecycle() async {
    wakeCalls++;
    throw StateError('Quota must not wake or probe OpenCode');
  }

  void signal() => notifyListeners();
}

class _Harness {
  final _Connection connection;
  late final ProviderQuotaOverview overview;
  final gateways = <_Gateway>[];
  DateTime now = _epoch;

  _Harness(this.connection) {
    overview = ProviderQuotaOverview(
      connection,
      clock: () => now,
      gatewayFactory: (_) {
        final gateway = _Gateway();
        gateways.add(gateway);
        return gateway;
      },
    );
  }

  void disposeOverview() {
    overview.dispose();
    for (final gateway in gateways) {
      if (!gateway.result.isCompleted) {
        gateway.result.complete(_snapshot(at: now));
      }
    }
  }
}

/// An injected overview belongs to its caller, not ProviderQuotaScreen. Release
/// it on unmount, BEFORE the binding checks for pending timers: addTearDown
/// alone runs after those invariant checks.
class _OverviewOwner extends StatefulWidget {
  final ProviderQuotaOverview overview;
  final Widget child;

  const _OverviewOwner({required this.overview, required this.child});

  @override
  State<_OverviewOwner> createState() => _OverviewOwnerState();
}

class _OverviewOwnerState extends State<_OverviewOwner> {
  @override
  Widget build(BuildContext context) => widget.child;

  @override
  void dispose() {
    widget.overview.dispose();
    super.dispose();
  }
}

ProviderQuotaSnapshot _snapshot({
  double usedPercent = 25.5,
  QuotaProvider provider = QuotaProvider.codex,
  DateTime? at,
  void Function(Map<String, dynamic>)? configure,
}) {
  // Clone through JSON to model wire map types, without mutating the shared
  // handwritten fixture (whose tests are owned by the state agent).
  final value =
      jsonDecode(
            jsonEncode(
              providerQuotaFixture(
                fetchedAtMs: (at ?? _epoch).millisecondsSinceEpoch,
                provider: provider,
              ),
            ),
          )
          as Map<String, dynamic>;
  ((value['windows'] as List).first as Map<String, dynamic>)['usedPercent'] =
      usedPercent;
  configure?.call(value);
  return ProviderQuotaSnapshot.fromJson(value);
}

Finder get _readButton => find.ancestor(
  of: find.text(_l10n.quotaRead),
  matching: find.byWidgetPredicate((widget) => widget is FilledButton),
);

Finder get _retryButton => find.widgetWithText(TextButton, _l10n.quotaRefresh);
Finder get _stopButton =>
    find.widgetWithText(TextButton, _l10n.quotaForgetConsent);
Finder get _refreshIcon => find.byTooltip(_l10n.quotaRefresh);
Finder get _primaryBar => find.byWidgetPredicate(
  (widget) =>
      widget is LinearProgressIndicator &&
      widget.semanticsLabel ==
          _l10n.quotaWindowRemainingLabel(_l10n.quotaPrimaryWindow),
);

Future<void> _frames(WidgetTester tester) async {
  // Bounded even when the controlled read leaves an indeterminate bar visible.
  for (var frame = 0; frame < 3; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _finishRouteAnimation(
  WidgetTester tester,
  Animation<double> animation,
  AnimationStatus target,
) async {
  // A new route first lays out offstage and may start its ticker on a later
  // frame. Wait for THIS finite animation, not all frames from live widgets.
  for (var frame = 0; frame < 40 && animation.status != target; frame++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
  expect(
    animation.status,
    target,
    reason: 'Navigation must finish within the bounded two-second frame budget',
  );
  // Let removal of a dismissed route reach the element tree.
  await tester.pump();
}

Future<void> _reveal(WidgetTester tester, Finder target) async {
  if (target.evaluate().isEmpty) {
    await tester.scrollUntilVisible(
      target,
      160,
      maxScrolls: 60,
      scrollable: find.byType(Scrollable).first,
    );
  } else {
    await tester.ensureVisible(target);
  }
  await tester.pump();
  expect(target.hitTestable(), findsOneWidget);
}

Future<void> _pumpHome(
  WidgetTester tester,
  Widget home, {
  ProviderQuotaOverview? ownedOverview,
  Size size = const Size(420, 1100),
  double textScale = 1,
  double keyboardInset = 0,
  Brightness brightness = Brightness.light,
  bool reducedMotion = false,
}) async {
  addTearDown(tester.view.reset);
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = size;
  tester.view.viewInsets = FakeViewPadding(bottom: keyboardInset);
  await tester.pumpWidget(
    MaterialApp(
      locale: const Locale('en'),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: brightness == Brightness.light
          ? AppTheme.light()
          : AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reducedMotion,
        ),
        child: child!,
      ),
      home: ownedOverview == null
          ? home
          : _OverviewOwner(overview: ownedOverview, child: home),
    ),
  );
  await _frames(tester);
}

Future<void> _pumpQuota(WidgetTester tester, _Harness h) => _pumpHome(
  tester,
  ProviderQuotaScreen(controller: h.connection, overview: h.overview),
  ownedOverview: h.overview,
);

Future<void> _consentAndRead(WidgetTester tester, _Harness h) async {
  final before = h.gateways.length;
  await _reveal(tester, find.byType(Checkbox));
  await tester.tap(find.byType(Checkbox));
  await tester.pump();
  expect(h.gateways, hasLength(before), reason: 'Checkbox alone is not a read');
  await _reveal(tester, _readButton);
  await tester.tap(_readButton);
  await tester.pump();
  expect(h.gateways, hasLength(before + 1));
  expect(h.gateways.last.reads, 1);
}

Future<void> _finishRead(
  WidgetTester tester,
  _Harness h,
  ProviderQuotaSnapshot snapshot,
) async {
  h.gateways.last.result.complete(snapshot);
  await _frames(tester);
}

Future<void> _refresh(WidgetTester tester, _Harness h) async {
  final before = h.gateways.length;
  await tester.tap(_refreshIcon);
  await tester.pump();
  expect(h.gateways, hasLength(before + 1));
}

void _expectNoPrivateCopy() {
  for (final value in [_password, _privateError, 'a' * 64]) {
    expect(find.textContaining(value), findsNothing);
  }
}

bool _focusWithin(Finder target) {
  final context = FocusManager.instance.primaryFocus?.context;
  if (context is! Element) return false;
  final elements = target.evaluate().toSet();
  var found = elements.contains(context);
  context.visitAncestorElements((element) {
    found = found || elements.contains(element);
    return !found;
  });
  return found;
}

Future<void> _tabTo(WidgetTester tester, Finder target) async {
  for (var attempt = 0; attempt < 12 && !_focusWithin(target); attempt++) {
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
  }
  expect(
    _focusWithin(target),
    isTrue,
    reason: 'Control must be keyboard reachable',
  );
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const secureChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );
  const backgroundChannel = MethodChannel('oc/background');
  const termuxChannel = MethodChannel('oc/termux');
  late List<String> secureMethods;

  setUp(() {
    final previousPreferences = SharedPreferencesStorePlatform.instance;
    addTearDown(() {
      SharedPreferencesStorePlatform.instance = previousPreferences;
      SharedPreferences.resetStatic();
    });
    SharedPreferences.setMockInitialValues({});

    final previousHttp = HttpOverrides.current;
    final http = _NoHttp();
    addTearDown(() => HttpOverrides.global = previousHttp);
    HttpOverrides.global = http;
    addTearDown(() => expect(http.clients, 0));

    secureMethods = [];
    final secureValues = <String, String>{};
    addTearDown(() => messenger.setMockMethodCallHandler(secureChannel, null));
    messenger.setMockMethodCallHandler(secureChannel, (call) async {
      secureMethods.add(call.method);
      final arguments = call.arguments as Map;
      final key = arguments['key'] as String;
      switch (call.method) {
        case 'read':
          return secureValues[key];
        case 'write':
          secureValues[key] = arguments['value'] as String;
        case 'delete':
          secureValues.remove(key);
      }
      return null;
    });
    addTearDown(
      () => messenger.setMockMethodCallHandler(backgroundChannel, null),
    );
    messenger.setMockMethodCallHandler(backgroundChannel, (_) async => null);
    var termuxCalls = 0;
    addTearDown(() => messenger.setMockMethodCallHandler(termuxChannel, null));
    messenger.setMockMethodCallHandler(termuxChannel, (_) async {
      termuxCalls++;
      throw StateError(
        'Quota UI must not read login files or install services',
      );
    });
    addTearDown(() => expect(termuxCalls, 0));

    // Restore even if a pause/loading assertion fails before its resumed step.
    addTearDown(
      () => binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed),
    );
    binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
  });

  Future<_Harness> harness(
    WidgetTester tester, {
    void Function(ServerProfile)? configure,
  }) async {
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    final profile = ServerProfile(
      id: 'quota-profile-a',
      name: 'Synthetic collector',
      baseUrl: _origin,
      username: 'fixture-user',
      password: _password,
    );
    configure?.call(profile);
    await store.upsert(profile);
    await store.upsert(
      ServerProfile(
        id: 'quota-profile-b',
        name: 'Other synthetic collector',
        baseUrl: 'https://other.example:8443',
        username: 'fixture-user',
        password: 'fixture-only-other-password',
      ),
    );
    await store.setActiveId(profile.id);
    final secureCallsAfterSetup = secureMethods.length;
    addTearDown(() {
      expect(secureMethods, hasLength(secureCallsAfterSetup));
      expect(secureMethods.where((method) => method == 'read'), isEmpty);
    });

    final connection = _Connection(store);
    addTearDown(connection.dispose);
    final h = _Harness(connection);
    addTearDown(h.disposeOverview);
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      expect(connection.repositoryCalls, 0);
      expect(connection.wakeCalls, 0);
      expect(connection.transportCalls, connection.allowSettingsHealth ? 1 : 0);
    });
    return h;
  }

  testWidgets('first visit requires both consent and an explicit read', (
    tester,
  ) async {
    final h = await harness(tester);
    final prefs = h.connection.store.prefs;
    final before = {for (final key in prefs.getKeys()) key: prefs.get(key)};
    await _pumpQuota(tester, h);

    expect(find.text(_l10n.quotaSetupTitle), findsOneWidget);
    expect(find.text(_origin), findsOneWidget);
    expect(find.text(providerQuotaPath), findsOneWidget);
    expect(find.text(_l10n.quotaSetupGuide), findsOneWidget);
    expect(_l10n.quotaSetupGuide, contains('tool/quota/README.md'));
    expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
    expect(tester.widget<FilledButton>(_readButton).onPressed, isNull);
    expect(_refreshIcon, findsNothing);
    await tester.tap(_readButton);
    await tester.pump(const Duration(seconds: 2));
    expect(h.gateways, isEmpty);

    await _consentAndRead(tester, h);
    expect(h.overview.loading, isTrue);
    expect(
      tester
          .widgetList<IconButton>(find.byType(IconButton))
          .singleWhere((button) => button.tooltip == _l10n.quotaRefresh)
          .onPressed,
      isNull,
    );
    expect(
      tester
          .widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator))
          .semanticsLabel,
      _l10n.quotaLoading,
    );
    await tester.pump(const Duration(seconds: 5));
    expect(h.gateways, hasLength(1), reason: 'No polling or duplicate read');
    await _finishRead(tester, h, _snapshot());
    expect(find.text('74.5% remaining'), findsOneWidget);
    expect({for (final key in prefs.getKeys()) key: prefs.get(key)}, before);
    _expectNoPrivateCopy();
  });

  testWidgets(
    'unsafe source configuration cannot enable consent or leak URL copy',
    (tester) async {
      final h = await harness(
        tester,
        configure: (profile) =>
            profile.baseUrl = '$_origin/?token=$_privateError',
      );
      await _pumpQuota(tester, h);
      expect(find.text(_origin), findsOneWidget);
      expect(find.text(_l10n.quotaSetupNeeded), findsOneWidget);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).onChanged, isNull);
      expect(tester.widget<FilledButton>(_readButton).onPressed, isNull);
      expect(h.gateways, isEmpty);
      _expectNoPrivateCopy();
    },
  );

  for (final sample in [
    (used: 0.0, remaining: '100%', progress: 1.0, usedLabel: '0%'),
    (used: 100.0, remaining: '0%', progress: 0.0, usedLabel: '100%'),
    (used: 25.5, remaining: '74.5%', progress: .745, usedLabel: '25.5%'),
  ]) {
    testWidgets('${sample.used}% used displays ${sample.remaining} remaining', (
      tester,
    ) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot(usedPercent: sample.used));
      expect(find.text(_l10n.quotaRemaining(sample.remaining)), findsOneWidget);
      expect(find.text(_l10n.quotaUsed(sample.usedLabel)), findsOneWidget);
      expect(
        tester.widget<LinearProgressIndicator>(_primaryBar).value,
        closeTo(sample.progress, .000001),
      );
      final progress = tester.getSemantics(_primaryBar).getSemanticsData();
      expect(
        progress.label,
        _l10n.quotaWindowRemainingLabel(_l10n.quotaPrimaryWindow),
      );
      // Flutter 3.47 range semantics require a number, not localized prose.
      expect(progress.value, sample.remaining.replaceFirst('%', ''));
      expect(find.text(_l10n.quotaUseBlocked), findsNothing);
      _expectNoPrivateCopy();
    });
  }

  testWidgets(
    'missing windows and explicit null reset replace old measurements honestly',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot());
      expect(find.text(_l10n.quotaHours(5)), findsOneWidget);

      await _refresh(tester, h);
      await _finishRead(
        tester,
        h,
        _snapshot(
          configure: (value) {
            final primary =
                (value['windows'] as List).first as Map<String, dynamic>;
            primary['durationSeconds'] = null;
            primary['resetsAtMs'] = null;
          },
        ),
      );
      expect(find.text('74.5% remaining'), findsOneWidget);
      expect(find.byType(LinearProgressIndicator), findsOneWidget);
      expect(find.text(_l10n.quotaResetUnknown), findsNWidgets(2));
      expect(find.textContaining('Reported reset:'), findsNothing);
      expect(find.text(_l10n.quotaHours(5)), findsNothing);

      await _refresh(tester, h);
      await _finishRead(
        tester,
        h,
        _snapshot(
          configure: (value) {
            value['windows'] = <Map<String, dynamic>>[
              {
                'id': 'primary',
                'status': 'missing',
                'usedPercent': null,
                'durationSeconds': null,
                'resetsAtMs': null,
              },
              {'id': 'secondary', 'status': 'missing'},
            ];
          },
        ),
      );
      expect(find.text(_l10n.quotaNotReported), findsNWidgets(2));
      expect(find.text(_l10n.quotaResetUnknown), findsNWidgets(2));
      expect(find.byType(LinearProgressIndicator), findsNothing);
      expect(find.textContaining('% remaining'), findsNothing);

      await _refresh(tester, h);
      await _finishRead(
        tester,
        h,
        _snapshot(
          configure: (value) {
            value['windows'] = <Map<String, dynamic>>[];
          },
        ),
      );
      expect(find.text(_l10n.quotaNotReported), findsOneWidget);
      expect(find.text(_l10n.quotaPrimaryWindow), findsNothing);
      expect(find.byType(LinearProgressIndicator), findsNothing);
    },
  );

  testWidgets(
    'failed refresh retains a labelled stale result and retry replaces it',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      final previous = _snapshot();
      await _finishRead(tester, h, previous);
      await _refresh(tester, h);
      expect(find.text('74.5% remaining'), findsOneWidget);
      h.gateways.last.result.completeError(StateError(_privateError));
      await _frames(tester);

      expect(find.text(_l10n.quotaUnavailable), findsOneWidget);
      expect(find.text(_l10n.quotaStale), findsOneWidget);
      expect(find.text('74.5% remaining'), findsOneWidget);
      expect(h.overview.snapshot, same(previous));
      _expectNoPrivateCopy();
      await _reveal(tester, _retryButton);
      await tester.tap(_retryButton);
      await tester.pump();
      expect(h.gateways, hasLength(3));
      await _finishRead(tester, h, _snapshot(usedPercent: 50));
      expect(find.text('50% remaining'), findsOneWidget);
      expect(find.text('74.5% remaining'), findsNothing);
      expect(find.text(_l10n.quotaUnavailable), findsNothing);
      expect(find.text(_l10n.quotaStale), findsNothing);
    },
  );

  testWidgets(
    'provider statuses and account mismatch replace allowances with safe copy',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot());
      final cases = [
        (
          ProviderQuotaStatus.ok,
          QuotaAccountStatus.mismatch,
          _l10n.quotaAccountUnverified,
        ),
        (
          ProviderQuotaStatus.ok,
          QuotaAccountStatus.unverified,
          _l10n.quotaAccountUnverified,
        ),
        (
          ProviderQuotaStatus.unconfigured,
          QuotaAccountStatus.unverified,
          _l10n.quotaUnconfigured,
        ),
        (
          ProviderQuotaStatus.unsupported,
          QuotaAccountStatus.unverified,
          _l10n.quotaProviderUnsupported,
        ),
        (
          ProviderQuotaStatus.authRequired,
          QuotaAccountStatus.unverified,
          _l10n.quotaProviderAuth,
        ),
        (
          ProviderQuotaStatus.rateLimited,
          QuotaAccountStatus.unverified,
          _l10n.quotaRateLimited,
        ),
        (
          ProviderQuotaStatus.unavailable,
          QuotaAccountStatus.unverified,
          _l10n.quotaUnavailable,
        ),
        (
          ProviderQuotaStatus.invalidResponse,
          QuotaAccountStatus.unverified,
          _l10n.quotaInvalidResponse,
        ),
      ];
      for (final (status, account, label) in cases) {
        await _refresh(tester, h);
        await _finishRead(
          tester,
          h,
          _snapshot(
            configure: (value) {
              value['status'] = status.name;
              value['freshness'] = 'none';
              value['ordinaryUsageAllowed'] = null;
              value['windows'] = <Map<String, dynamic>>[];
              value['account'] = <String, dynamic>{'status': account.name};
              value['error'] = _privateError;
            },
          ),
        );
        expect(
          find.text(label),
          findsOneWidget,
          reason: '${status.name}/${account.name}',
        );
        expect(find.text(_l10n.quotaCollectorAuth), findsNothing);
        expect(find.text(_l10n.quotaCodexAccount), findsNothing);
        expect(find.textContaining('% remaining'), findsNothing);
        expect(find.byType(LinearProgressIndicator), findsNothing);
        _expectNoPrivateCopy();
      }
    },
  );

  testWidgets(
    'collector failures use retry copy distinct from provider sign-in and raw errors',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      final cases = <(Object, String)>[
        (
          const ProviderQuotaFailure(QuotaFailureKind.collectorAuth),
          _l10n.quotaCollectorAuth,
        ),
        (
          const ProviderQuotaFailure(QuotaFailureKind.unsupported),
          _l10n.quotaCollectorMissing,
        ),
        (
          const ProviderQuotaFailure(QuotaFailureKind.unavailable),
          _l10n.quotaUnavailable,
        ),
        (
          const ProviderQuotaFailure(QuotaFailureKind.invalidResponse),
          _l10n.quotaInvalidResponse,
        ),
        (
          const FormatException(_privateError, _password),
          _l10n.quotaInvalidResponse,
        ),
        (StateError(_privateError), _l10n.quotaUnavailable),
      ];
      for (var index = 0; index < cases.length; index++) {
        if (index != 0) await _refresh(tester, h);
        h.gateways.last.result.completeError(cases[index].$1);
        await _frames(tester);
        expect(find.text(cases[index].$2), findsOneWidget);
        expect(find.text(_l10n.quotaProviderAuth), findsNothing);
        expect(find.byType(LinearProgressIndicator), findsNothing);
        expect(_retryButton, findsOneWidget);
        _expectNoPrivateCopy();
      }
    },
  );

  for (final changeProfile in [false, true]) {
    testWidgets(
      '${changeProfile ? 'profile' : 'location'} change drops the snapshot and rejects a late read',
      (tester) async {
        final h = await harness(tester);
        await _pumpQuota(tester, h);
        await _consentAndRead(tester, h);
        await _finishRead(tester, h, _snapshot());
        await _refresh(tester, h);
        final oldRead = h.gateways.last;
        if (changeProfile) {
          await h.connection.store.setActiveId('quota-profile-b');
        } else {
          h.connection.locationRevision++;
        }
        h.connection.signal();
        await _frames(tester);
        expect(find.text(_l10n.quotaSourceChanged), findsOneWidget);
        expect(find.textContaining('% remaining'), findsNothing);
        expect(_refreshIcon, findsNothing);
        expect(_stopButton, findsNothing);
        expect(h.overview.snapshot, isNull);
        expect(h.overview.consented, isFalse);
        expect(oldRead.closes, 1);
        oldRead.result.complete(_snapshot(usedPercent: 1));
        await _frames(tester);
        expect(find.text('99% remaining'), findsNothing);
        expect(h.gateways, hasLength(2));
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'stop using the collector cancels reads and requires fresh checkbox consent',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot());
      await _refresh(tester, h);
      final cancelled = h.gateways.last;
      await _reveal(tester, _stopButton);
      await tester.tap(_stopButton);
      await _frames(tester);

      expect(h.overview.snapshot, isNull);
      expect(h.overview.consented, isFalse);
      expect(cancelled.closes, 1);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      expect(tester.widget<FilledButton>(_readButton).onPressed, isNull);
      cancelled.result.complete(_snapshot(usedPercent: 1));
      await _frames(tester);
      expect(find.textContaining('% remaining'), findsNothing);
      expect(h.gateways, hasLength(2));
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot(usedPercent: 50));
      expect(find.text('50% remaining'), findsOneWidget);
    },
  );

  testWidgets(
    'backgrounding cancels the read and resume waits for manual retry',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot());
      await _refresh(tester, h);
      final cancelled = h.gateways.last;
      binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      expect(cancelled.closes, 1);
      expect(h.overview.canRead, isFalse);
      expect(find.text(_l10n.quotaStale), findsOneWidget);
      cancelled.result.complete(_snapshot(usedPercent: 1));
      await tester.pump();
      binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await _frames(tester);
      expect(h.gateways, hasLength(2));
      expect(find.text('74.5% remaining'), findsOneWidget);
      expect(find.text('99% remaining'), findsNothing);
      await _refresh(tester, h);
      await _finishRead(tester, h, _snapshot(usedPercent: 50));
      expect(find.text('50% remaining'), findsOneWidget);
    },
  );

  for (final pending in [false, true]) {
    testWidgets(
      'ending an injected visit cancels expiry${pending ? ' and a pending read' : ''}',
      (tester) async {
        final h = await harness(tester);
        await _pumpQuota(tester, h);
        await _consentAndRead(tester, h);
        await _finishRead(tester, h, _snapshot());
        if (pending) await _refresh(tester, h);
        final last = h.gateways.last;
        await tester.pumpWidget(const SizedBox.shrink());
        // _OverviewOwner, not the screen, releases this supplied overview.
        expect(last.closes, 1);
        if (pending) last.result.complete(_snapshot(usedPercent: 1));
        await tester.pump();
        expect(h.overview.snapshot, isNull);
        expect(h.overview.consented, isFalse);
        expect(tester.takeException(), isNull);
        // Do not advance past expiry: the binding must detect a leaked timer.
      },
    );
  }

  testWidgets(
    'passing a reported reset marks stale without refilling or polling',
    (tester) async {
      final h = await harness(tester);
      await _pumpQuota(tester, h);
      await _consentAndRead(tester, h);
      await _finishRead(
        tester,
        h,
        _snapshot(
          usedPercent: 100,
          configure: (value) {
            ((value['windows'] as List).first
                as Map<String, dynamic>)['resetsAtMs'] = h.now
                .add(const Duration(seconds: 2))
                .millisecondsSinceEpoch;
          },
        ),
      );
      expect(find.text('0% remaining'), findsOneWidget);
      expect(find.text(_l10n.quotaResetPassed), findsNothing);
      h.now = h.now.add(const Duration(seconds: 2));
      await tester.pump(const Duration(seconds: 2));
      expect(find.text(_l10n.quotaResetPassed), findsOneWidget);
      expect(find.text(_l10n.quotaStale), findsOneWidget);
      expect(find.text('0% remaining'), findsOneWidget);
      expect(find.text('100% remaining'), findsNothing);
      expect(tester.widget<LinearProgressIndicator>(_primaryBar).value, 0);
      expect(h.gateways, hasLength(1));
    },
  );

  testWidgets(
    '360x740 at 2.5x with a 320dp keyboard keeps every action reachable',
    (tester) async {
      final h = await harness(tester);
      await _pumpHome(
        tester,
        ProviderQuotaScreen(controller: h.connection, overview: h.overview),
        ownedOverview: h.overview,
        size: const Size(360, 740),
        textScale: 2.5,
        keyboardInset: 320,
        reducedMotion: true,
      );
      final media = MediaQuery.of(
        tester.element(find.byType(ProviderQuotaScreen)),
      );
      expect(media.size, const Size(360, 740));
      expect(media.textScaler.scale(16), 40);
      expect(media.viewInsets.bottom, 320);
      expect(media.disableAnimations, isTrue);

      await _consentAndRead(tester, h);
      await _reveal(tester, _stopButton);
      expect(tester.getRect(_stopButton).bottom, lessThanOrEqualTo(420));
      h.gateways.last.result.completeError(StateError(_privateError));
      await _frames(tester);
      await _reveal(tester, _retryButton);
      expect(tester.getRect(_retryButton).bottom, lessThanOrEqualTo(420));
      expect(_refreshIcon.hitTestable(), findsOneWidget);
      await tester.tap(_retryButton);
      await tester.pump();
      await _finishRead(tester, h, _snapshot());
      await _reveal(tester, find.text('74.5% remaining'));
      await _reveal(tester, _stopButton);
      await tester.tap(_stopButton);
      await _frames(tester);
      await _reveal(tester, find.byType(Checkbox));
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      await _reveal(tester, _readButton);
      expect(tester.getRect(_readButton).bottom, lessThanOrEqualTo(420));
      expect(tester.widget<FilledButton>(_readButton).onPressed, isNull);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('keyboard consent and read remain separate accessible actions', (
    tester,
  ) async {
    final h = await harness(tester);
    await _pumpQuota(tester, h);
    await _reveal(tester, find.byType(CheckboxListTile));
    await _tabTo(tester, find.byType(CheckboxListTile));
    await tester.sendKeyEvent(LogicalKeyboardKey.space);
    await tester.pump();
    expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isTrue);
    expect(h.gateways, isEmpty);
    await _tabTo(tester, _readButton);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(h.gateways, hasLength(1));
    await _finishRead(tester, h, _snapshot());
    expect(find.text('74.5% remaining'), findsOneWidget);
  });

  for (final brightness in Brightness.values) {
    testWidgets(
      '${brightness.name} quota semantics and 48dp targets survive reduced motion',
      (tester) async {
        final h = await harness(tester);
        // testWidgets enables and owns the semantics handle for this test.
        await _pumpHome(
          tester,
          ProviderQuotaScreen(controller: h.connection, overview: h.overview),
          ownedOverview: h.overview,
          size: const Size(420, 1400),
          brightness: brightness,
          reducedMotion: true,
        );
        await _consentAndRead(tester, h);
        await _finishRead(tester, h, _snapshot());
        await tester.pump(const Duration(seconds: 1));
        final progress = tester.getSemantics(_primaryBar).getSemanticsData();
        expect(
          progress.label,
          _l10n.quotaWindowRemainingLabel(_l10n.quotaPrimaryWindow),
        );
        expect(progress.value, '74.5');
        expect(_refreshIcon.hitTestable(), findsOneWidget);
        expect(_stopButton.hitTestable(), findsOneWidget);
        expect(tester.getSize(_stopButton).height, greaterThanOrEqualTo(48));
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
        await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
        await expectLater(tester, meetsGuideline(textContrastGuideline));
        expect(tester.binding.hasScheduledFrame, isFalse);
        await tester.pump(const Duration(seconds: 1));
        expect(tester.widget<LinearProgressIndicator>(_primaryBar).value, .745);
        expect(h.gateways, hasLength(1));
        expect(tester.takeException(), isNull);
      },
    );
  }

  testWidgets(
    'replacing the screen controller never reuses the prior visit consent or snapshot',
    (tester) async {
      final first = await harness(tester);
      final second = _Harness(_Connection(first.connection.store));
      addTearDown(second.connection.dispose);
      final current = ValueNotifier(first);
      addTearDown(current.dispose);
      try {
        await _pumpHome(
          tester,
          ValueListenableBuilder<_Harness>(
            valueListenable: current,
            builder: (context, h, _) => ProviderQuotaScreen(
              controller: h.connection,
              overview: h.overview,
            ),
          ),
        );
        await _consentAndRead(tester, first);
        await _finishRead(tester, first, _snapshot());
        current.value = second;
        await _frames(tester);
        expect(find.text(_l10n.quotaCodexAccount), findsNothing);
        expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
        expect(second.gateways, isEmpty);
        expect(second.overview.consented, isFalse);
      } finally {
        first.disposeOverview();
        second.disposeOverview();
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      }
    },
  );

  testWidgets(
    'changing provider requires fresh consent and never relabels a late Codex read',
    (tester) async {
      final h = await harness(tester);
      await _pumpHome(
        tester,
        ProviderQuotaScreen(controller: h.connection, overview: h.overview),
        ownedOverview: h.overview,
        size: const Size(420, 1600),
      );
      await _consentAndRead(tester, h);
      final old = h.gateways.last;
      final claude = find.widgetWithText(ChoiceChip, _l10n.quotaClaude);
      await _reveal(tester, claude);
      await tester.tap(claude);
      await _frames(tester);
      expect(old.closes, 1);
      expect(h.overview.consented, isFalse);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      old.result.complete(_snapshot());
      await _frames(tester);
      expect(find.text(_l10n.quotaCodexAccount), findsNothing);
      expect(h.gateways, hasLength(1));
      await _consentAndRead(tester, h);
      await _finishRead(tester, h, _snapshot(provider: QuotaProvider.claude));
      expect(find.text(_l10n.quotaClaudeAccount), findsOneWidget);
      expect(find.text(_l10n.quotaSourceBound), findsOneWidget);
      expect(find.text(_l10n.quotaCodexAccount), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Settings opens Remaining usage for a saved v1 profile without quota probing',
    (tester) async {
      final h = await harness(tester);
      h.connection.allowSettingsHealth = true;
      expect(h.connection.serverFlavor.name, 'v1');
      expect(h.connection.supportsUsageStatistics, isFalse);
      await _pumpHome(tester, SettingsScreen(controller: h.connection));
      expect(h.connection.healthGateway.healthCalls, 1);
      final quotaRow = find.byKey(const ValueKey('settings-category-quota'));
      final quotaPageIncludingTransition = find.byType(
        ProviderQuotaScreen,
        skipOffstage: false,
      );
      await _reveal(tester, quotaRow);
      await tester.tap(quotaRow);
      await tester.pump();
      final quotaRoute = ModalRoute.of(
        tester.element(quotaPageIncludingTransition),
      )!;
      final quotaAnimation = quotaRoute.animation!;
      await _finishRouteAnimation(
        tester,
        quotaAnimation,
        AnimationStatus.completed,
      );
      expect(find.byType(ProviderQuotaScreen), findsOneWidget);
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      expect(tester.widget<FilledButton>(_readButton).onPressed, isNull);
      expect(h.gateways, isEmpty);
      await tester.pageBack();
      await tester.pump();
      await _finishRouteAnimation(
        tester,
        quotaAnimation,
        AnimationStatus.dismissed,
      );
      expect(quotaPageIncludingTransition, findsNothing);
      await _reveal(tester, quotaRow);
      await tester.tap(quotaRow);
      await tester.pump();
      final reopenedRoute = ModalRoute.of(
        tester.element(quotaPageIncludingTransition),
      )!;
      await _finishRouteAnimation(
        tester,
        reopenedRoute.animation!,
        AnimationStatus.completed,
      );
      expect(tester.widget<Checkbox>(find.byType(Checkbox)).value, isFalse);
      expect(_refreshIcon, findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  final capturePath = Platform.environment['OC_QUOTA_CAPTURE'];
  testWidgets('synthetic remaining usage rendered preview', (tester) async {
    // Opt-in only, with a pre-existing output directory. This is a synthetic
    // widget rendering, not a device capture or an automatically updated golden.
    final output = File(capturePath!);
    expect(
      output.parent.existsSync(),
      isTrue,
      reason:
          'Verify/create the capture parent before setting OC_QUOTA_CAPTURE',
    );
    final h = await harness(tester);
    await loadCaptureFonts();
    addTearDown(tester.view.reset);
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(411, 1100);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          theme: captureTheme(light: true),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: _OverviewOwner(
            overview: h.overview,
            child: ProviderQuotaScreen(
              controller: h.connection,
              overview: h.overview,
            ),
          ),
        ),
      ),
    );
    await _frames(tester);
    await _consentAndRead(tester, h);
    await _finishRead(tester, h, _snapshot());
    await tester.pump(const Duration(seconds: 1));
    expect(find.text(_origin), findsOneWidget);
    expect(find.text('74.5% remaining'), findsOneWidget);
    expect(find.text(_l10n.quotaNotReported), findsOneWidget);
    _expectNoPrivateCopy();
    expect(tester.takeException(), isNull);
    final png = await capturePng(tester, boundary, pixelRatio: 1);
    expect(png, isNotEmpty);
    output.writeAsBytesSync(png, flush: true);
  }, skip: capturePath == null || capturePath.trim().isEmpty);
}
