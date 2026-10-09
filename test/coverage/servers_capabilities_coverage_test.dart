// Coverage ratchet for the server's ability switches (ServerCapabilities)
// against the "Available on this server" page. Every switch is "shown" (a
// row on that page turns on and off with it) or "ignored: <reason>" (plumbing
// the app handles itself, or a gate its own page already explains).
//
// The test does not take the ledger's word for "shown": it flips each switch
// alone, from all-off and from all-on, and finds the rows that answer. A
// "shown" switch that moves no row fails, and so does a switch that moves a
// row while the ledger says "ignored". Each shown switch is then drawn on the
// real page with it off and its row's words are read.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'paseo_coverage_support.dart';

class _Api extends OpenCodeApi {
  _Api(this._capabilities) : super(baseUrl: 'http://localhost');

  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;
}

class _Repository implements ProductRepository, UsageStatisticsGateway {
  @override
  bool get usageStatisticsSupported => true;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  final samples =
      jsonDecode(
            File(
              'test/fixtures/coverage/servers_capabilities_samples.json',
            ).readAsStringSync(),
          )
          as Map<String, dynamic>;
  final flags = [
    for (final f
        in ((samples['schema'] as Map)['capability'] as List).cast<Map>())
      f['path'] as String,
  ];
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/servers_capabilities_ledger.json',
                ).readAsStringSync(),
              )
              as Map)
          .cast<String, String>();

  ServerCapabilities caps(Map<String, bool> overrides, {required bool base}) =>
      Function.apply(ServerCapabilities.new, const [], {
            for (final flag in flags) Symbol(flag): overrides[flag] ?? base,
          })
          as ServerCapabilities;

  bool open(ServerFeature feature, ServerCapabilities server) =>
      feature.available((
        server: server,
        device: platformCapabilities,
        usageStatistics: true,
      ));

  /// The page's server rows that answer to [flag] alone, each with the
  /// background it was found against: every other switch on (the row then
  /// goes missing when [flag] goes off) or off (the row then appears when
  /// [flag] goes on).
  Map<String, bool> rowsOf(String flag) {
    final rows = <String, bool>{};
    for (final base in [false, true]) {
      final a = caps(const {}, base: base);
      final b = caps({flag: !base}, base: base);
      for (final feature in serverFeatures) {
        if (feature.source == ServerFeatureSource.server &&
            open(feature, a) != open(feature, b)) {
          rows[feature.id] = base;
        }
      }
    }
    return rows;
  }

  test('every switch has a ledger decision', () {
    expect(
      flags.toSet().difference(
        ledger.keys.map((k) => k.substring('capability.'.length)).toSet(),
      ),
      isEmpty,
      reason: 'switches with no entry in servers_capabilities_ledger.json',
    );
    expect(
      ledger.keys
          .map((k) => k.substring('capability.'.length))
          .toSet()
          .difference(flags.toSet()),
      isEmpty,
      reason: 'ledger entries for switches the app no longer has',
    );
    for (final entry in ledger.entries) {
      expect(
        entry.value == 'shown' ||
            (entry.value.startsWith('ignored: ') && entry.value.length > 20),
        isTrue,
        reason: '${entry.key}: "shown" or "ignored: <reason>"',
      );
    }
  });

  test('"shown" switches move a row, and only they do', () {
    final wrong = <String>[];
    for (final flag in flags) {
      final rows = rowsOf(flag);
      final decision = ledger['capability.$flag'];
      if (decision == 'shown' && rows.isEmpty) {
        wrong.add('$flag: ledger says shown, no row answers to it');
      }
      if (decision != 'shown' && rows.isNotEmpty) {
        wrong.add('$flag: moves ${rows.keys} but the ledger says "$decision"');
      }
    }
    expect(wrong, isEmpty);
  });

  Future<(ConnectionController, GlobalKey)> pumpPage(
    WidgetTester tester,
    ServerCapabilities server, {
    double height = 4200,
  }) async {
    tester.view.physicalSize = Size(412, height);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(secure, null),
    );
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
    final store = ProfileStore(prefs: await SharedPreferences.getInstance());
    await store.load();
    final controller = ConnectionController(store)
      ..api = _Api(server)
      ..repository = _Repository()
      ..status = StreamStatus.connected;
    addTearDown(controller.dispose);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: ServerCapabilitiesScreen(controller: controller),
        boundaryKey: boundary,
        controller: controller,
        store: store,
      ),
    );
    await tester.pumpAndSettle();
    return (controller, boundary);
  }

  testWidgets('every shown switch turns its row off on the real page', (
    tester,
  ) async {
    final l10n = lookupAppLocalizations(const Locale('en'));
    final problems = <String>[];
    for (final flag in flags) {
      if (ledger['capability.$flag'] != 'shown') continue;
      for (final MapEntry(key: id, value: others) in rowsOf(flag).entries) {
        final feature = serverFeatures.firstWhere((f) => f.id == id);
        // Others on: the switch off takes the row to "Not on this server".
        // Others off: the switch on brings it to the list of what works.
        await pumpPage(tester, caps({flag: !others}, base: others));
        if (others) {
          if (find
              .byKey(ValueKey('capability-unavailable-$id'))
              .evaluate()
              .isEmpty) {
            problems.add('$flag off: no "Not on this server" row for $id');
            continue;
          }
        } else {
          final fold = find.byKey(
            const ValueKey('capabilities-available-fold'),
          );
          if (fold.evaluate().isEmpty) {
            problems.add('$flag on: no list of what works for $id');
            continue;
          }
          await tester.tap(fold);
          await tester.pumpAndSettle();
          final row = find.byKey(ValueKey('capability-available-$id'));
          if (row.evaluate().isEmpty) {
            problems.add('$flag on: no "Works here" row for $id');
            continue;
          }
        }
        final text = flat(screenText(tester).join('\n'));
        for (final words in [feature.title(l10n), feature.detail(l10n)]) {
          if (!text.contains(flat(words))) {
            problems.add('$flag: "$words" is not on the page');
          }
        }
        await tester.pumpWidget(const SizedBox.shrink());
      }
    }
    expect(problems, isEmpty);
  });

  testWidgets('the page drawn with everything off and everything on', (
    tester,
  ) async {
    final (_, boundary) = await pumpPage(
      tester,
      caps(const {}, base: false),
      height: 915,
    );
    await writeCasePng(tester, boundary, 'srvcaps_nothing');
    await tester.pumpWidget(const SizedBox.shrink());
    final (_, boundaryOn) = await pumpPage(
      tester,
      caps(const {}, base: true),
      height: 915,
    );
    final fold = find.byKey(const ValueKey('capabilities-available-fold'));
    if (fold.evaluate().isNotEmpty) {
      await tester.tap(fold);
      await tester.pumpAndSettle();
    }
    await writeCasePng(tester, boundaryOn, 'srvcaps_everything');
  });
}
