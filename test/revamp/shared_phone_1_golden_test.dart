// Golden renders of shared-phone-1's pages (wave 2a), rebuilt from kit
// parts: "This phone" in its states and the setup output. Phone 412x915 and one wide
// window (1280x800), dark and light (owner decision 2026-09-27: no Arabic),
// with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/shared_phone_1_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';
import 'package:opencode_mobile/ui/widgets/setup_terminal.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/fake_setup_engine.dart';
import 'shared_phone_1_fixtures.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Size size,
  required Widget Function(BuildContext context) body,
  List<Override> overrides = const [],
  FutureOr<void> Function(BuildContext context)? open,
  Future<void> Function(WidgetTester tester)? act,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    late BuildContext context;
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: ProviderScope(
          overrides: overrides,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: light),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: Scaffold(
              body: SafeArea(
                child: Builder(
                  builder: (inner) {
                    context = inner;
                    final tokens = KitTokens.of(inner);
                    return Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 560),
                        child: ListView(
                          padding: EdgeInsets.all(tokens.space4),
                          children: [body(inner)],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (open != null) unawaited(Future.sync(() => open(context)));
    if (act != null) await act(tester);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  setUpAll(loadCaptureFonts);

  late PhoneStore store;
  late PhoneConnection connection;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = PhoneStore(prefs: await SharedPreferences.getInstance());
    connection = PhoneConnection(store);
    PhoneSetup.engine = FakeSetupEngine();
  });

  tearDown(() => connection.dispose());

  Widget column(BuildContext context, List<Widget> children) {
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final (i, child) in children.indexed) ...[
          if (i > 0) SizedBox(height: tokens.space5),
          child,
        ],
      ],
    );
  }

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';
    for (final size in [_phone, _wide]) {
      testWidgets('This phone in its states ($theme, $size)', (tester) async {
        final profile = phoneProfile();
        store.saved.add(profile);
        final linux = PhoneLinux();
        await _shot(
          tester,
          'phone_server_card_states',
          light: light,
          size: size,
          overrides: [
            builtinServerStarterProvider.overrideWith(
              (ref) => BuiltinServerStarter(linux: linux),
            ),
          ],
          body: (context) => column(context, [
            PhoneServerCard(
              connection: connection,
              profile: profile,
              connected: true,
              onDisconnect: () {},
              linux: PhoneLinux(),
              pollInterval: null,
            ),
            PhoneServerCard(
              connection: connection,
              profile: profile,
              onOpen: () {},
              linux: PhoneLinux(),
              pollInterval: null,
            ),
            PhoneServerCard(
              connection: connection,
              profile: profile,
              onOpen: () {},
              linux: PhoneLinux(running: false),
              pollInterval: null,
            ),
          ]),
        );
      });

      testWidgets('setup output ($theme, $size)', (tester) async {
        final scroll = ScrollController();
        addTearDown(scroll.dispose);
        await _shot(
          tester,
          'setup_terminal_states',
          light: light,
          size: size,
          body: (context) => column(context, [
            SetupTerminal(output: '', running: true, controller: scroll),
            SetupTerminal(
              output:
                  '[oc] Installing OpenCode 1.18.29\n'
                  'npm WARN deprecated inflight@1.0.6\n'
                  'npm ERR! code ECONNRESET\n'
                  '[oc] Setup stopped',
              running: false,
              controller: scroll,
              onCopy: () {},
              copyTooltip: l10n.e7SetupCopyFailureReport,
            ),
          ]),
        );
      });
    }
  }
}
