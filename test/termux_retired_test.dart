// A phone whose saved setup ran OpenCode (or Claude Code) in Termux, from
// an older build, after the Termux path was removed (owner, 2026-11-07):
// everything it saved still loads, its Termux connection is recognised as
// that setup, the one notice says what to do, and forgetting the
// connection sweeps the keys the old build wrote for it.
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_retired.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/termux_retired_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

ServerProfile _profile(
  String id,
  String url, {
  ServerBackend backend = ServerBackend.openCode,
}) => ServerProfile(id: id, name: id, baseUrl: url)..backend = backend;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // ProfileStore.load restores secrets through secure storage, whose
    // unmocked channel never answers in a widget test.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  group('recognising a saved Termux setup', () {
    test('OpenCode at Termux\'s address, or its Claude Code daemon, on '
        'Android', () {
      expect(
        isTermuxRetiredProfile(
          _profile('t', 'http://127.0.0.1:4096'),
          android: true,
        ),
        isTrue,
      );
      expect(
        isTermuxRetiredProfile(
          _profile('t', 'http://localhost:4096'),
          android: true,
        ),
        isTrue,
      );
      expect(
        isTermuxRetiredProfile(
          _profile('c', 'ws://127.0.0.1:6767', backend: ServerBackend.paseo),
          android: true,
        ),
        isTrue,
      );
    });

    test('nothing else is: the built-in server, other ports, remote '
        'servers, Codex, and any desktop', () {
      for (final profile in [
        _profile('b', BuiltinLinux.serverUrl),
        _profile('f', 'http://127.0.0.1:4123'),
        _profile('r', 'https://work.example:4096'),
        _profile('s', 'https://127.0.0.1:4096'),
        _profile('x', 'ws://127.0.0.1:6767', backend: ServerBackend.codex),
        _profile('p', 'ws://127.0.0.1:4099', backend: ServerBackend.paseo),
      ]) {
        expect(
          isTermuxRetiredProfile(profile, android: true),
          isFalse,
          reason: profile.baseUrl,
        );
      }
      expect(
        isTermuxRetiredProfile(
          _profile('t', 'http://127.0.0.1:4096'),
          android: false,
        ),
        isFalse,
      );
      expect(isTermuxRetiredProfile(null, android: true), isFalse);
    });
  });

  test(
    'an older build\'s saved setup loads whole; the Termux connection is '
    'found, the rest stays as it was, and forgetting it sweeps its keys',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final writer = ProfileStore(prefs: prefs);
      await writer.upsert(_profile('termux', 'http://127.0.0.1:4096'));
      await writer.upsert(_profile('work', 'https://work.example'));
      await writer.upsert(
        _profile('claude', 'ws://127.0.0.1:6767', backend: ServerBackend.paseo),
      );
      await writer.setActiveId('termux');
      // What the Termux path left behind for its connection.
      await prefs.setString(
        'oc.managedServerRecovery.termux',
        jsonEncode({'enabled': true, 'runtime': 'opencode2'}),
      );
      await prefs.setString('oc.termuxMigrationOffer.termux', 'dismissed');
      await prefs.setString('oc.model.work', 'anthropic|claude');

      final store = ProfileStore(prefs: prefs);
      final loaded = await store.load();
      expect(
        loaded.map((p) => p.id),
        containsAll(['termux', 'work', 'claude']),
      );
      expect(store.activeId, 'termux');
      expect(termuxRetiredProfile(store, android: true)?.id, 'termux');
      expect(
        isTermuxRetiredProfile(
          loaded.singleWhere((p) => p.id == 'claude'),
          android: true,
        ),
        isTrue,
      );
      expect(
        isTermuxRetiredProfile(
          loaded.singleWhere((p) => p.id == 'work'),
          android: true,
        ),
        isFalse,
      );

      final failed = await store.removeScopedPreferences('termux');
      expect(failed, isEmpty);
      expect(prefs.getString('oc.managedServerRecovery.termux'), isNull);
      expect(prefs.getString('oc.termuxMigrationOffer.termux'), isNull);
      expect(prefs.getString('oc.model.work'), 'anthropic|claude');
    },
  );

  testWidgets('the notice says it once, with its two ways forward', (
    tester,
  ) async {
    var setUp = 0;
    var connect = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: TermuxRetiredNotice(
            onSetUpLinux: () => setUp++,
            onConnectComputer: () => connect++,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.text(
        'Termux is no longer supported. Set up built-in Linux on this phone, '
        'or connect to your computer. Your projects in Termux are still '
        "there; copy them to the phone's shared storage or push them with "
        'Git to bring them over.',
      ),
      findsOneWidget,
    );
    await tester.tap(find.text('Set up built-in Linux'));
    await tester.tap(find.text('Connect a computer'));
    expect((setUp, connect), (1, 1));
  });
}
