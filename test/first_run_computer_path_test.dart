import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';
import 'package:opencode_mobile/ui/setup_commands.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/first_run_path.dart';
import 'support/server_editor.dart';

/// Saved profiles without the secure-storage channel, which is unmocked in
/// widget tests and would hang a real load.
class _Store extends ProfileStore {
  _Store({required super.prefs, this.seeded = const []});

  final List<ServerProfile> seeded;

  @override
  String? get activeId => null;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(seeded);
}

Future<(_Store, ConnectionController)> _state({
  List<ServerProfile> seeded = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  final store = _Store(
    prefs: await SharedPreferences.getInstance(),
    seeded: seeded,
  );
  return (store, ConnectionController(store));
}

Widget _app(
  ProfileStore store,
  ConnectionController controller, {
  double textScale = 1,
  Locale locale = const Locale('en'),
}) => ProviderScope(
  overrides: [
    bootstrapProvider.overrideWithValue(AppBootstrap(store)),
    connProvider.overrideWithValue(controller),
  ],
  child: MaterialApp(
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child ?? const SizedBox.shrink(),
    ),
    home: const ServersScreen(),
  ),
);

/// The command block showing exactly [text] (a long command is laid out a
/// line at a time, so its words are not one text widget).
Finder _codeBlock(String text) =>
    find.byWidgetPredicate((w) => w is KitCodeBlock && w.text == text);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('password commands prompt privately and pass SEC-4 unchanged', () {
    for (final entry in {
      'PASEO_PASSWORD': SetupCommands.paseoStartPrivateNetwork,
    }.entries) {
      expect(KitRedact.text(entry.value), entry.value);
      expect(entry.value, contains('IFS= read -rsp '));
      expect(entry.value, contains('test -n "\$${entry.key}" &&'));
      expect(entry.value, contains('export ${entry.key} &&'));
      expect(entry.value, isNot(contains('${entry.key}=')));
    }
  });

  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  const termux = MethodChannel('oc/termux');
  const tailscale = MethodChannel('oc/tailscale');
  final copied = <String>[];

  setUp(() {
    copied.clear();
    messenger.setMockMethodCallHandler(
      termux,
      (call) async => {'installed': false},
    );
    messenger.setMockMethodCallHandler(
      tailscale,
      (call) async => call.method == 'check' ? 'installed' : true,
    );
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') {
        copied.add((call.arguments as Map)['text'] as String);
      }
      return null;
    });
  });
  tearDown(() {
    debugPlatformCapabilities = null;
    messenger.setMockMethodCallHandler(termux, null);
    messenger.setMockMethodCallHandler(tailscale, null);
    messenger.setMockMethodCallHandler(SystemChannels.platform, null);
  });

  testWidgets('"On my computer" opens Add server at its first step', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await chooseComputerOnWelcome(tester);

    // One path (P3.9): the editor itself, asking what runs there.
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
    expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
    expect(find.text('Step 1 of 4 · What runs there'), findsOneWidget);
    // Not alternatives: the step says they run side by side.
    expect(
      find.byKey(const ValueKey('agent-choice-side-by-side')),
      findsOneWidget,
    );
    expect(find.text('OpenCode on a computer'), findsOneWidget);
    expect(find.text('Claude Code or Pi'), findsOneWidget);
    expect(find.text('Codex'), findsOneWidget);
  });

  const expected = {
    'opencode': (
      title: 'OpenCode',
      command: SetupCommands.pair,
      field: 'server-url-field',
      otherField: 'codex-server-address-field',
    ),
    'paseo': (
      title: 'Agents through Paseo',
      command: SetupCommands.paseoStart,
      field: 'codex-server-address-field',
      otherField: 'server-url-field',
    ),
    'codex': (
      title: 'Codex',
      command: SetupCommands.codexStart,
      field: 'codex-server-address-field',
      otherField: 'server-url-field',
    ),
  };

  for (final MapEntry(key: agent, value: want) in expected.entries) {
    testWidgets('choosing $agent opens its connect screen, selector hidden', (
      tester,
    ) async {
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await openFirstRunConnect(tester, agent: agent);
      await openServerManualAddress(tester);

      expect(
        find.byKey(const ValueKey('server-profile-editor')),
        findsOneWidget,
      );
      expect(find.widgetWithText(KitTopBar, want.title), findsOneWidget);
      expect(
        find.byKey(const ValueKey('server-backend-selector')),
        findsNothing,
      );
      expect(find.byType(ChoiceChip), findsNothing);
      expect(find.byKey(ValueKey(want.field)), findsOneWidget);
      expect(find.byKey(ValueKey(want.otherField)), findsNothing);
      // Paseo and Codex share their fields; the secret's label tells them
      // apart.
      if (agent == 'paseo') {
        expect(find.text('Daemon password (optional)'), findsOneWidget);
      }
      if (agent == 'codex') {
        expect(find.text('Connection token'), findsOneWidget);
      }

      // The one command for this agent, above the fields, and it copies.
      final command = find.byKey(const ValueKey('connect-command'));
      expect(command, findsOneWidget);
      expect(tester.widget<KitCodeBlock>(command).text, want.command);
      expect(
        tester.getRect(command).bottom,
        lessThanOrEqualTo(tester.getRect(find.byKey(ValueKey(want.field))).top),
      );
      // R3 moved Copy onto the command's first line.
      await tester.tap(
        find.descendant(
          of: command,
          matching: find.byKey(const ValueKey('kit-code-copy')),
        ),
      );
      await tester.pump();
      expect(copied, [want.command]);
      // Let the "Copied" snackbar run out before the tree is torn down.
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();

      // Scan / Paste pairing stays exactly where it exists today.
      expect(
        find.byKey(const ValueKey('server-pairing-paste')),
        agent == 'opencode' ? findsOneWidget : findsNothing,
      );
      expect(
        find.byKey(const ValueKey('server-pairing-scan')),
        agent == 'opencode' ? findsOneWidget : findsNothing,
      );
    });
  }

  testWidgets('Back from the connect step returns to the first step', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await openFirstRunConnect(tester, agent: 'paseo');
    await tester.tap(find.byKey(const ValueKey('server-editor-back')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
    expect(find.byKey(const ValueKey('server-profile-editor')), findsOneWidget);
  });

  testWidgets('"Show the commands" holds the guide\'s other commands', (
    tester,
  ) async {
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await openFirstRunConnect(tester, agent: 'codex');

    expect(_codeBlock(SetupCommands.codexToken), findsNothing);
    await tester.tap(find.text('Show the commands'));
    await tester.pumpAndSettle();
    expect(_codeBlock(SetupCommands.codexToken), findsOneWidget);
    expect(_codeBlock(SetupCommands.codexUsb), findsOneWidget);
  });

  for (final agent in ['opencode', 'paseo']) {
    testWidgets('"Not on the same network?" leads to Tailscale ($agent)', (
      tester,
    ) async {
      final (store, controller) = await _state();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(store, controller));
      await openFirstRunConnect(tester, agent: agent);
      await openServerManualAddress(tester);

      final link = find.byKey(const ValueKey('connect-not-same-network'));
      await tester.scrollUntilVisible(
        link,
        160,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Not on the same network?'), findsOneWidget);
      // Directly under the address field it explains.
      final address = find.byKey(
        ValueKey(
          agent == 'opencode'
              ? 'server-url-field'
              : 'codex-server-address-field',
        ),
      );
      expect(
        tester.getRect(link).top,
        greaterThanOrEqualTo(tester.getRect(address).bottom),
      );
      await tester.ensureVisible(link);
      await tester.pumpAndSettle();
      await tester.tap(link);
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      // OpenCode: the flow's Tailscale step. Paseo listens on ws://, so
      // the Tailscale page opens for its guidance.
      if (agent == 'opencode') {
        expect(
          find.byKey(const ValueKey('server-tailscale-step')),
          findsOneWidget,
        );
        expect(find.byType(TailscaleSetupScreen), findsNothing);
      } else {
        expect(find.byType(TailscaleSetupScreen), findsOneWidget);
      }
    });
  }

  testWidgets('no Tailscale link where the platform has no handoff', (
    tester,
  ) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final (store, controller) = await _state();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await openFirstRunConnect(tester);
    await openServerManualAddress(tester);

    expect(
      find.byKey(const ValueKey('connect-not-same-network')),
      findsNothing,
    );
    // No camera path either, so the next move is paste only.
    expect(find.text('Then paste the code it prints.'), findsOneWidget);
  });

  testWidgets('"Add server" beside saved servers starts the same flow', (
    tester,
  ) async {
    final (store, controller) = await _state(
      seeded: [
        ServerProfile(
          id: 'work',
          name: 'Workstation',
          baseUrl: 'https://box.example',
        ),
      ],
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(store, controller));
    await tester.tap(find.text('Add server'));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('server-backend-selector')),
      findsOneWidget,
    );
    // The same first step as "On my computer": one row per type (ledger
    // row 15), none chosen yet.
    expect(find.byKey(const ValueKey('server-kind-step')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('server-backend-opencode')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('server-backend-codex')), findsOneWidget);
    expect(find.byKey(const ValueKey('server-backend-paseo')), findsOneWidget);
    expect(find.text('OpenCode on a computer'), findsOneWidget);
    // The chosen type's command leads the next step; back and another type
    // shows its own.
    await chooseServerKind(tester);
    final command = find.byKey(const ValueKey('connect-command'));
    expect(
      find.descendant(of: command, matching: find.text(SetupCommands.pair)),
      findsOneWidget,
    );
    await tester.tap(find.byKey(const ValueKey('server-editor-back')));
    await tester.pumpAndSettle();
    await chooseServerKind(tester, kind: 'paseo');
    expect(
      find.descendant(
        of: command,
        matching: find.text(SetupCommands.paseoStart),
      ),
      findsOneWidget,
    );
  });

  for (final locale in const [Locale('en'), Locale('ar')]) {
    for (final agent in ['opencode', 'paseo', 'codex']) {
      testWidgets(
        'computer path fits 320dp at 2.5x text ($agent, ${locale.languageCode})',
        (tester) async {
          await tester.binding.setSurfaceSize(const Size(320, 640));
          addTearDown(() => tester.binding.setSurfaceSize(null));
          final (store, controller) = await _state();
          addTearDown(controller.dispose);
          await tester.pumpWidget(
            _app(store, controller, textScale: 2.5, locale: locale),
          );
          await tester.pumpAndSettle();
          await chooseComputerOnWelcome(tester);
          expect(tester.takeException(), isNull);

          for (final key in ['opencode', 'paseo', 'codex']) {
            final choice = find.byKey(ValueKey('server-backend-$key'));
            await tester.scrollUntilVisible(
              choice,
              120,
              scrollable: find.byType(Scrollable).first,
            );
            final rect = tester.getRect(choice);
            expect(rect.left, greaterThanOrEqualTo(0), reason: key);
            expect(rect.right, lessThanOrEqualTo(320), reason: key);
          }
          final choice = find.byKey(ValueKey('server-backend-$agent'));
          await tester.ensureVisible(choice);
          await tester.pumpAndSettle();
          // A choice can be taller than the space left at this text size, so
          // its centre may be below the fold; its top is always in view.
          await tester.tapAt(tester.getTopLeft(choice) + const Offset(24, 24));
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);

          final list = find
              .descendant(
                of: find.byKey(const ValueKey('server-profile-fields')),
                matching: find.byType(Scrollable),
              )
              .first;
          final command = find.byKey(const ValueKey('connect-command'));
          expect(tester.getRect(command).left, greaterThanOrEqualTo(0));
          expect(tester.getRect(command).right, lessThanOrEqualTo(320));
          final disclosure = find
              .descendant(
                of: find.byKey(const ValueKey('connect-show-commands')),
                // Its header: the fold's first line of words, in any locale.
                matching: find.byType(Text),
              )
              .first;
          await tester.scrollUntilVisible(disclosure, 120, scrollable: list);
          await tester.ensureVisible(disclosure);
          await tester.pumpAndSettle();
          await tester.tap(disclosure);
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          expect(
            find.descendant(
              of: find.byKey(const ValueKey('connect-computer-command')),
              matching: find.byType(KitCodeBlock),
            ),
            findsAtLeast(2),
          );
          // OpenCode keeps the address (and this link) under "Enter the
          // address instead".
          final manual = find.byKey(const ValueKey('server-manual-address'));
          if (manual.evaluate().isNotEmpty) {
            await tester.scrollUntilVisible(manual, 160, scrollable: list);
            await tester.ensureVisible(manual);
            await tester.pumpAndSettle();
            // Like the kind rows, this label can be taller than the viewport
            // at 2.5x. Its visible leading edge remains a full row target.
            await tester.tapAt(
              tester.getTopLeft(manual) + const Offset(24, 24),
            );
            await tester.pumpAndSettle();
          }
          final link = find.byKey(const ValueKey('connect-not-same-network'));
          await tester.scrollUntilVisible(link, 160, scrollable: list);
          final rect = tester.getRect(link);
          expect(rect.left, greaterThanOrEqualTo(0));
          expect(rect.right, lessThanOrEqualTo(320));
          expect(rect.height, greaterThanOrEqualTo(48));
          await tester.scrollUntilVisible(
            find.byKey(const ValueKey('test-server-connection')),
            200,
            scrollable: list,
          );
          expect(tester.takeException(), isNull);
        },
      );
    }
  }
}
