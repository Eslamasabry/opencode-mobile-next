import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api(this._capabilities) : super(baseUrl: 'http://localhost');

  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;
}

class _Repository implements ProductRepository, UsageStatisticsGateway {
  _Repository(this.usageStatisticsSupported);

  @override
  final bool usageStatisticsSupported;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(
  ServerCapabilities capabilities, {
  bool usageStatistics = true,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Workstation',
        'baseUrl': 'http://localhost:4096',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = _Api(capabilities)
    ..repository = _Repository(usageStatistics)
    ..status = StreamStatus.connected;
}

Widget _app(
  ConnectionController controller, {
  Locale locale = const Locale('en'),
  String? focus,
}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: ServerCapabilitiesScreen(controller: controller, focusFeature: focus),
);

/// Unfolds the one "… work here" row so the available features are built.
Future<void> _unfold(WidgetTester tester) async {
  final fold = find.byKey(const ValueKey('capabilities-available-fold'));
  if (fold.evaluate().isEmpty) return;
  await tester.tap(fold);
  await tester.pumpAndSettle();
}

/// The feature ids of the rows in list order, from their keys.
List<String> _order(WidgetTester tester) => [
  for (final element
      in find
          .byWidgetPredicate(
            (widget) =>
                widget is KitRow &&
                widget.key is ValueKey<String> &&
                (widget.key! as ValueKey<String>).value.startsWith(
                  'capability-',
                ),
          )
          .evaluate())
    (element.widget.key! as ValueKey<String>).value,
];

Finder _key(String key) => find.byKey(ValueKey(key));

/// The ids listed with a state (available, unavailable, device), read from
/// the row keys. The list is lazy,
/// so the whole screen is laid out on a very tall surface first.
Set<String> _listed(WidgetTester tester, String slug) => {
  for (final element
      in find
          .byWidgetPredicate(
            (widget) =>
                widget is KitRow &&
                widget.key is ValueKey<String> &&
                (widget.key! as ValueKey<String>).value.startsWith(
                  'capability-$slug-',
                ),
          )
          .evaluate())
    (element.widget.key! as ValueKey<String>).value.substring(
      'capability-$slug-'.length,
    ),
};

void _tall(WidgetTester tester) {
  tester.view.physicalSize = const Size(400, 6000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    KitCapabilities.debugReset();
  });

  test('feature ids are unique and every one has words in both languages', () {
    final ids = [for (final feature in serverFeatures) feature.id];
    expect(ids.toSet().length, ids.length);
    for (final locale in const [Locale('en'), Locale('ar')]) {
      final l10n = lookupAppLocalizations(locale);
      for (final feature in serverFeatures) {
        expect(feature.title(l10n).trim(), isNotEmpty, reason: feature.id);
        expect(feature.detail(l10n).trim(), isNotEmpty, reason: feature.id);
      }
    }
  });

  test('no backend name appears in the words of the screen', () {
    final l10n = lookupAppLocalizations(const Locale('en'));
    final words = [
      l10n.capabilityScreenTitle,
      // capabilityScreenIntroWithGaps is left out on purpose: like the
      // kit's "Works on …" line, it says where missing features work, and
      // R16 names "other OpenCode servers" there once.
      l10n.capabilityScreenIntro('Workstation'),
      l10n.capabilityAllAvailable,
      l10n.capabilityStateHere,
      l10n.capabilityStateNotServer,
      l10n.capabilityStateNotDevice,
      l10n.capabilityNeedsAndroid,
      l10n.capabilityAvailableCount(17),
      l10n.capabilityAvailableCountDetail,
      l10n.capabilityAddServer,
      l10n.capabilityAddServerDetail,
      for (final feature in serverFeatures) ...[
        feature.title(l10n),
        feature.detail(l10n),
      ],
    ].join('\n').toLowerCase();
    for (final name in ['opencode', 'codex', 'paseo', 'claude', 'termux']) {
      expect(words, isNot(contains(name)), reason: name);
    }
  });

  testWidgets('OpenCode 1: everything it serves unfolds from one row', (
    tester,
  ) async {
    _tall(tester);
    final controller = await _controller(ServerCapabilities.allV1);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(find.textContaining('Workstation'), findsOneWidget);
    // Folded: only the missing feature and the one summary row show.
    expect(_listed(tester, 'available'), isEmpty);
    expect(find.textContaining('features work here'), findsOneWidget);
    await _unfold(tester);
    final available = _listed(tester, 'available');
    expect(
      available,
      containsAll([
        'files',
        'changes',
        'terminal',
        'shell',
        'attachments',
        'compact',
        'share',
        'fork',
        'revert',
        'skills',
        'mcp',
        'cloud-environments',
        'worktrees',
        'usage',
        'background-notifications',
      ]),
    );
    // allV1 leaves the plugin inventory off: it is the one server-side gap.
    expect(_listed(tester, 'unavailable'), {
      'plugins',
      'dev-services',
      'web-search',
      'send-while-working',
    });
    expect(_listed(tester, 'device'), isEmpty);
    // Nothing is listed twice.
    expect(available.intersection(_listed(tester, 'unavailable')), isEmpty);
  });

  testWidgets('OpenCode 2: the shell is under Not available on this server', (
    tester,
  ) async {
    _tall(tester);
    final controller = await _controller(api2ServerCapabilities);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    expect(api2ServerCapabilities.shellSettings, isFalse);
    await _unfold(tester);
    expect(_listed(tester, 'unavailable'), contains('shell'));
    expect(_listed(tester, 'available'), isNot(contains('shell')));
    expect(
      find.descendant(
        of: _key('capability-unavailable-shell'),
        matching: find.text('Default shell'),
      ),
      findsOneWidget,
    );
    // Every feature is in exactly one list, decided by the capability set.
    final facts = (
      server: api2ServerCapabilities,
      device: const PlatformCapabilities.android(),
      usageStatistics: true,
    );
    for (final feature in serverFeatures) {
      final present = feature.available(facts);
      expect(
        _listed(tester, 'available').contains(feature.id),
        present,
        reason: feature.id,
      );
      expect(
        _listed(tester, 'unavailable').contains(feature.id),
        !present,
        reason: feature.id,
      );
    }
  });

  for (final backend in {
    'Codex-like': codexServerCapabilities,
    'Paseo-like': paseoServerCapabilities,
  }.entries) {
    testWidgets('${backend.key}: the hidden Project tools are explained', (
      tester,
    ) async {
      _tall(tester);
      final capabilities = backend.value;
      final controller = await _controller(
        capabilities,
        usageStatistics: false,
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      await _unfold(tester);

      final available = _listed(tester, 'available');
      final unavailable = _listed(tester, 'unavailable');
      for (final (id, present) in [
        ('files', capabilities.fileBrowsing),
        ('terminal', capabilities.terminal),
        ('shell', capabilities.shellSettings),
        ('worktrees', capabilities.projectManagement),
        ('attachments', capabilities.promptAttachments),
        ('compact', capabilities.sessionCompact),
        ('share', capabilities.sessionShare),
        ('fork', capabilities.sessionFork),
        ('revert', capabilities.sessionRevert),
        ('skills', capabilities.serverCatalog),
        ('mcp', capabilities.serverCatalog),
        ('plugins', capabilities.pluginInventory),
        ('cloud-environments', capabilities.managedWorkspaces),
        ('usage', false),
      ]) {
        expect(available.contains(id), present, reason: '$id available');
        expect(unavailable.contains(id), !present, reason: '$id unavailable');
      }
      // These backends hide most of the Project tab; the explanation must
      // actually be there.
      expect(capabilities.fileBrowsing, isFalse);
      expect(unavailable, containsAll(['files', 'terminal', 'mcp', 'usage']));
      // The phone still does what the phone does.
      expect(available, contains('background-notifications'));
      expect(_listed(tester, 'device'), isEmpty);
      expect(_key('server-capabilities-all'), findsNothing);
    });
  }

  testWidgets(
    'a desktop says what the device cannot do, not blaming the server',
    (tester) async {
      _tall(tester);
      debugPlatformCapabilities = const PlatformCapabilities(
        platform: TargetPlatform.linux,
      );
      final controller = await _controller(
        const ServerCapabilities(
          pluginInventory: true,
          developmentServices: true,
          webSearch: true,
          inbox: true,
        ),
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();

      // The server does everything, so it is not blamed for the device.
      expect(_listed(tester, 'unavailable'), isEmpty);
      expect(_key('server-capabilities-all'), findsOneWidget);
      expect(
        find.textContaining('Not on this device', findRichText: true),
        findsNWidgets(3),
      );
      expect(_listed(tester, 'device'), {
        'background-notifications',
        'on-this-phone',
        'voice',
      });
    },
  );

  testWidgets('one list: what is missing first, each saying where to get it', (
    tester,
  ) async {
    _tall(tester);
    final controller = await _controller(
      codexServerCapabilities,
      usageStatistics: false,
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await _unfold(tester);

    final order = _order(tester);
    final firstAvailable = order.indexWhere(
      (key) => key.startsWith('capability-available-'),
    );
    final lastMissing = order.lastIndexWhere(
      (key) => !key.startsWith('capability-available-'),
    );
    expect(lastMissing, lessThan(firstAvailable));
    // A missing server feature says so in words. Which servers have it is
    // said once in the intro; a row repeats it only when its answer differs
    // from the most common one (R16).
    final files = _key('capability-unavailable-files');
    expect(
      find.descendant(
        of: files,
        matching: find.textContaining('Not on this server', findRichText: true),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: files,
        matching: find.textContaining('Works on', findRichText: true),
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: _key('capability-unavailable-worktrees'),
        matching: find.textContaining('Works on', findRichText: true),
      ),
      findsOneWidget,
    );
    expect(
      find.textContaining('Missing features work on other OpenCode servers.'),
      findsOneWidget,
    );
    // No state headings: one list.
    expect(find.text('Not available on this server'), findsNothing);
    expect(find.text('Available here'), findsNothing);
  });

  testWidgets('opened about one feature: that feature is first', (
    tester,
  ) async {
    _tall(tester);
    final controller = await _controller(ServerCapabilities.allV1);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, focus: 'terminal'));
    await tester.pumpAndSettle();

    // Available, yet shown first without unfolding.
    expect(_order(tester).first, 'capability-available-terminal');
  });

  testWidgets('Add a server appears only when the app can open that flow', (
    tester,
  ) async {
    _tall(tester);
    final controller = await _controller(codexServerCapabilities);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    expect(_key('capabilities-add-server'), findsNothing);

    final requests = <KitEnableRequest>[];
    KitCapabilities.registerFlow(
      KitEnableFlows.addServer,
      (context, request) async => requests.add(request),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(_key('capabilities-add-server'));
    await tester.pumpAndSettle();
    expect(requests.single.capability, 'server.any');
    expect(requests.single.source, 'server-capabilities');
  });

  group('layout at 320 dp and 2.5x text', () {
    for (final locale in const [Locale('en'), Locale('ar')]) {
      testWidgets('no overflow in ${locale.languageCode}', (tester) async {
        // 320 dp wide; tall enough that every row is laid out at once, so
        // the check is about width, not scrolling.
        const phone = Size(320, 30000);
        tester.view.devicePixelRatio = 1;
        tester.view.physicalSize = phone;
        addTearDown(tester.view.reset);
        final controller = await _controller(codexServerCapabilities);
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          MediaQuery(
            data: const MediaQueryData(
              size: phone,
              textScaler: TextScaler.linear(AppTheme.maxTextScale),
            ),
            child: _app(controller, locale: locale),
          ),
        );
        await tester.pumpAndSettle();
        await _unfold(tester);
        expect(tester.takeException(), isNull);
        expect(
          Directionality.of(tester.element(_key('server-capabilities'))),
          locale.languageCode == 'ar' ? TextDirection.rtl : TextDirection.ltr,
        );
        for (final feature in serverFeatures) {
          final row = find.byWidgetPredicate(
            (widget) =>
                widget is KitRow &&
                widget.key is ValueKey<String> &&
                (widget.key! as ValueKey<String>).value.endsWith(
                  '-${feature.id}',
                ),
          );
          expect(row, findsOneWidget, reason: feature.id);
          expect(
            tester.getSize(row).width,
            lessThanOrEqualTo(320),
            reason: feature.id,
          );
        }
      });
    }
  });
}
