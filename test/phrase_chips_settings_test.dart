import 'package:opencode_mobile/domain/server_gateway.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phrase_chips.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/search/search_index.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends FakeServerGateway {
  _Api() : super(baseUrl: 'http://localhost');
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Workstation',
        'flavor': 'v2',
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
    ..api = _Api()
    ..status = StreamStatus.connected;
}

Widget _app(ConnectionController controller, {Locale? locale}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: SettingsScreen(
    controller: controller,
    initialGroup: SettingsGroup.chat,
  ),
);

Finder _row(String key) => find.byKey(ValueKey(key));

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
  tearDown(() => debugPlatformCapabilities = null);

  testWidgets('the switch turns phrase suggestions off and on', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      _row('settings-phrase-chips'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Phrase suggestions'), findsOneWidget);
    expect(PhraseChipStore.enabled(controller.store.prefs), isTrue);
    await tester.ensureVisible(_row('settings-phrase-chips'));
    await tester.pumpAndSettle();
    await tester.tap(_row('settings-phrase-chips'));
    await tester.pumpAndSettle();
    expect(PhraseChipStore.enabled(controller.store.prefs), isFalse);
    await tester.tap(_row('settings-phrase-chips'));
    await tester.pumpAndSettle();
    expect(PhraseChipStore.enabled(controller.store.prefs), isTrue);
  });

  testWidgets('Show hidden phrases appears only when some are hidden', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final store = PhraseChipStore.forProfile(
      controller.store.prefs,
      'profile-1',
    );
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      _row('settings-phrase-chips'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(_row('settings-phrase-chips-show-hidden'), findsNothing);

    await tester.runAsync(() => store.hide('Run the tests'));
    await tester.pumpAndSettle();
    expect(_row('settings-phrase-chips-show-hidden'), findsOneWidget);
    expect(find.text('1 hidden phrase'), findsOneWidget);

    await tester.tap(_row('settings-phrase-chips-show-hidden'));
    await tester.pumpAndSettle();
    expect(store.hiddenCount, 0);
    expect(_row('settings-phrase-chips-show-hidden'), findsNothing);
  });

  testWidgets('the row reads in Arabic', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller, locale: const Locale('ar')));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      _row('settings-phrase-chips'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('اقتراح العبارات'), findsOneWidget);
  });

  test('the launcher finds the setting', () async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    final en = lookupAppLocalizations(const Locale('en'));
    final ids = [
      for (final entry in searchEntries(
        en,
        SearchScope(controller: controller),
        'phrase',
      ))
        entry.id,
    ];
    expect(ids, contains('settings-phrase-chips'));
  });
}
