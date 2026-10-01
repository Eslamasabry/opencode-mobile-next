import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi {
  _Api(this.gate, {this.fails = false}) : super(baseUrl: 'http://localhost');
  final Completer<void> gate;
  final bool fails;
  @override
  Future<Health> health() async {
    await gate.future;
    if (fails) throw StateError('unreachable');
    return Health(healthy: true, version: '1.18.23');
  }
}

class _Controller extends ConnectionController {
  _Controller(super.store, this.transport);
  final Completer<void> transport;
  @override
  Future<ServerGateway?> prepareActionTransport() async {
    await transport.future;
    return api;
  }
}

Future<_Controller> _make(
  Completer<void> transport,
  Completer<void> gate, {
  bool fails = false,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'speed-1',
        'name': 'W',
        'baseUrl': 'http://localhost:4096',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'speed-1',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return _Controller(store, transport)
    ..api = _Api(gate, fails: fails)
    ..status = StreamStatus.connected;
}

Widget _app(ConnectionController c, ValueNotifier<bool> visible) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: ValueListenableBuilder<bool>(
    valueListenable: visible,
    builder: (_, on, child) => TickerMode(enabled: on, child: child!),
    child: SettingsScreen(controller: c),
  ),
);

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
    PerfTrace.logSink = null;
    serverHealthCache.clear();
  });

  testWidgets('index is built once per language, not per build', (
    tester,
  ) async {
    final done = Completer<void>()..complete();
    final c = await _make(done, done);
    await tester.pumpWidget(_app(c, ValueNotifier(true)));
    await tester.pumpAndSettle();
    final before = allSearchEntriesBuilds;
    for (var i = 0; i < 5; i++) {
      c.notifyListeners();
      await tester.pump();
    }
    expect(allSearchEntriesBuilds, before);
  });

  testWidgets('no rebuild while the tab is hidden', (tester) async {
    final done = Completer<void>()..complete();
    final c = await _make(done, done);
    final visible = ValueNotifier(true);
    await tester.pumpWidget(_app(c, visible));
    await tester.pumpAndSettle();
    visible.value = false;
    await tester.pump();
    final hidden = settingsBuildCount;
    for (var i = 0; i < 5; i++) {
      c.notifyListeners();
      await tester.pump();
    }
    expect(settingsBuildCount, hidden);
    visible.value = true;
    await tester.pump();
    expect(settingsBuildCount, hidden + 1);
  });

  testWidgets('cached health shows at once with a slow transport', (
    tester,
  ) async {
    serverHealthCache['speed-1'] = Health(healthy: true, version: '9.9.9');
    final transport = Completer<void>();
    final c = await _make(transport, Completer<void>()..complete());
    await tester.pumpWidget(_app(c, ValueNotifier(true)));
    await tester.pump();
    expect(find.textContaining('9.9.9'), findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsNothing);
    transport.complete();
    await tester.pumpAndSettle();
    expect(find.textContaining('1.18.23'), findsOneWidget);
    expect(
      PerfTrace.recent(50).any((s) => s.name == 'settings.health'),
      isTrue,
    );
  });

  // A cached answer that a new check contradicts must not survive it: the
  // page once said "Server healthy" beside "The server did not answer."
  testWidgets('a failed check drops the cached healthy answer', (tester) async {
    final en = lookupAppLocalizations(const Locale('en'));
    serverHealthCache['speed-1'] = Health(healthy: true, version: '9.9.9');
    final done = Completer<void>()..complete();
    final c = await _make(done, done, fails: true);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ServerSettingsScreen(controller: c),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text(en.e7SettingsUi59), findsNothing);
    expect(find.text(en.e7SettingsUi60), findsOneWidget);
    expect(find.textContaining('9.9.9'), findsNothing);
    expect(serverHealthCache, isNot(contains('speed-1')));
  });
}
