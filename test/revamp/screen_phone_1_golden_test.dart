// Golden renders of screen-phone-1's pages (wave 2b), rebuilt from kit
// parts: phone setup's screen A (fresh, stopped part way, ready with Termux
// also there), its Customize sheet (first setup, nothing left to add), the
// ready screen, the welcome's setup line, Termux storage (intro, scan,
// report, an open category, the clean question, a failed scan) and the
// This phone in the app (not set up, running, its log, the Remove question;
// P1.5 replaced the built-in server page). Phone
// 412x915 and one wide window (1280x800), dark and light (owner decision
// 2026-09-27: no Arabic), with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_phone_1_golden_test.dart
// and look at every changed image before committing it.

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxRuntime;
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_customize_sheet.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_ready_screen.dart';
import 'package:opencode_mobile/ui/screens/termux_storage_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/phone_setup_scenes.dart' show WelcomePhoneCardScene;
import 'screen_phone_1_fixtures.dart';

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != phoneSize) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

SetupProgress _stopped() => const SetupProgress(
  state: SetupState.interrupted,
  overall: .5,
  components: [
    ComponentProgress(id: 'linux', state: ComponentState.done),
    ComponentProgress(id: 'essentials', state: ComponentState.done),
    ComponentProgress(id: 'python', state: ComponentState.done),
    ComponentProgress(id: 'node', state: ComponentState.pending),
    ComponentProgress(id: 'opencode', state: ComponentState.pending),
  ],
);

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget home,
  Size size = phoneSize,
  SetupProgress? progress,
  Set<String> optionalInstalled = const {},
  PhoneLinux? linux,
  List<ServerProfile> profiles = const [],
  Future<void> Function()? act,
}) async {
  final boundary = GlobalKey();
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    await pumpPhone(
      tester,
      home: home,
      size: size,
      light: light,
      progress: progress,
      optionalInstalled: optionalInstalled,
      linux: linux,
      profiles: profiles,
      boundary: boundary,
    );
    if (act != null) await act();
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
    await unmountPhone(tester);
  }
}

Widget _customizeHost({required bool addMode}) => Scaffold(
  body: Builder(
    builder: (context) => Center(
      child: KitButton.primary(
        label: 'open',
        expand: false,
        onPressed: () => showSetupCustomizeSheet(
          context,
          engine: PhoneSetup.engine,
          addMode: addMode,
        ),
      ),
    ),
  ),
);

Widget _storage() => TermuxStorageScreen(
  now: () => DateTime.fromMillisecondsSinceEpoch(1788800120000),
  pollInterval: const Duration(hours: 1),
);

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
    useNoTermuxJob();
  });

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';

    // --- Phone setup ---------------------------------------------------------

    for (final size in [phoneSize, wideSize]) {
      testWidgets('setup start fresh ($theme, $size)', (tester) async {
        await _shot(
          tester,
          'phone_setup_start_fresh',
          light: light,
          size: size,
          home: startScreen(),
        );
      });
    }

    testWidgets('setup start stopped part way ($theme)', (tester) async {
      await _shot(
        tester,
        'phone_setup_start_stopped',
        light: light,
        home: startScreen(),
        progress: _stopped(),
      );
    });

    testWidgets('setup start ready, Termux too, other ways ($theme)', (
      tester,
    ) async {
      await _shot(
        tester,
        'phone_setup_start_ready_other_ways',
        light: light,
        home: startScreen(
          inApp: true,
          termux: TermuxRunningServer.running(
            runtime: TermuxRuntime.openCode1,
            version: '1.18.29',
            observedAt: DateTime(2026, 9, 24),
          ),
        ),
        act: () async {
          final toggle = find.byKey(
            const ValueKey('phone-setup-start-other-ways'),
          );
          await tester.ensureVisible(toggle);
          await tester.tap(toggle);
        },
      );
    });

    testWidgets('customize first setup ($theme)', (tester) async {
      await _shot(
        tester,
        'phone_setup_customize_first',
        light: light,
        home: startScreen(),
        act: () async {
          await tester.tap(
            find.byKey(const ValueKey('phone-setup-start-customize')),
          );
        },
      );
    });

    testWidgets('customize nothing left to add ($theme)', (tester) async {
      await _shot(
        tester,
        'phone_setup_customize_all_installed',
        light: light,
        home: _customizeHost(addMode: true),
        optionalInstalled: {'python'},
        act: () => tester.tap(find.text('open')),
      );
    });

    for (final size in [phoneSize, if (!light) wideSize]) {
      testWidgets('setup ready ($theme, $size)', (tester) async {
        await _shot(
          tester,
          'phone_setup_ready_default',
          light: light,
          size: size,
          home: PhoneSetupReadyScreen(linux: PhoneLinux(running: true)),
        );
      });
    }

    testWidgets('welcome line, stopped part way ($theme)', (tester) async {
      await _shot(
        tester,
        'phone_setup_welcome_stopped',
        light: light,
        progress: _stopped(),
        home: const Material(
          child: SafeArea(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: WelcomePhoneCardScene(),
            ),
          ),
        ),
      );
    });

    // --- Termux storage ------------------------------------------------------

    testWidgets('storage intro ($theme)', (tester) async {
      StorageChannel().install();
      await _shot(
        tester,
        'phone_termux_storage_intro',
        light: light,
        home: _storage(),
      );
    });

    testWidgets('storage scanning ($theme)', (tester) async {
      StorageChannel(
        state: 'running',
        log:
            '[oc] Measuring storage on this phone\n[oc] Build caches\n'
            '[oc] Agent scratch',
      ).install();
      await _shot(
        tester,
        'phone_termux_storage_scanning',
        light: light,
        home: _storage(),
      );
    });

    for (final size in [phoneSize, wideSize]) {
      testWidgets('storage report ($theme, $size)', (tester) async {
        StorageChannel(state: 'done', report: storageReport()).install();
        await _shot(
          tester,
          'phone_termux_storage_report',
          light: light,
          size: size,
          home: _storage(),
        );
      });
    }

    testWidgets('storage clean question ($theme)', (tester) async {
      StorageChannel(state: 'done', report: storageReport()).install();
      await _shot(
        tester,
        'phone_termux_storage_clean_sheet',
        light: light,
        home: _storage(),
        act: () async {
          await tester.tap(
            find.byKey(const Key('termux-storage-cat-build_caches')),
          );
          await _settle(tester);
          final clean = find.byKey(
            const Key('termux-storage-clean-build_caches'),
          );
          await tester.ensureVisible(clean);
          await _settle(tester);
          await tester.tap(clean);
        },
      );
    });

    if (!light) {
      testWidgets('storage category open (dark)', (tester) async {
        StorageChannel(state: 'done', report: storageReport()).install();
        await _shot(
          tester,
          'phone_termux_storage_category_open',
          light: light,
          home: _storage(),
          act: () => tester.tap(
            find.byKey(const Key('termux-storage-cat-build_caches')),
          ),
        );
      });

      testWidgets('storage scan failed (dark)', (tester) async {
        StorageChannel(state: 'failed').install();
        await _shot(
          tester,
          'phone_termux_storage_failed',
          light: light,
          home: _storage(),
        );
      });
    }

    // --- This phone (P1.5: the built-in server page is gone) ----------------

    testWidgets('this phone not set up ($theme)', (tester) async {
      await _shot(
        tester,
        'phone_this_phone_not_set_up',
        light: light,
        home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
        linux: PhoneLinux(installed: false, openCode: false),
      );
    });

    for (final size in [phoneSize, if (!light) wideSize]) {
      testWidgets('this phone running ($theme, $size)', (tester) async {
        await _shot(
          tester,
          'phone_this_phone_running',
          light: light,
          size: size,
          home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
          linux: PhoneLinux(running: true),
          profiles: [inAppProfile],
        );
      });
    }

    testWidgets('this phone details with the server log ($theme)', (
      tester,
    ) async {
      final linux = PhoneLinux(
        running: true,
        log:
            'opencode server listening on http://127.0.0.1:4097\n'
            'INFO  session created\nERROR provider timed out\n',
      );
      await _shot(
        tester,
        'phone_this_phone_details_log',
        light: light,
        home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
        linux: linux,
        profiles: [inAppProfile],
        act: () async {
          final details = find.byKey(const ValueKey('this-phone-details'));
          await tester.ensureVisible(details);
          await _settle(tester);
          await tester.tap(details);
          await _settle(tester);
          await tester.ensureVisible(
            find.byKey(const ValueKey('this-phone-log')),
          );
        },
      );
    });

    testWidgets('this phone remove question ($theme)', (tester) async {
      await _shot(
        tester,
        'phone_this_phone_remove_confirm',
        light: light,
        home: const ThisPhoneScreen(kind: PhoneHostKind.inApp),
        linux: PhoneLinux(),
        profiles: [inAppProfile],
        act: () async {
          final remove = find.byKey(const ValueKey('this-phone-remove'));
          await tester.ensureVisible(remove);
          await _settle(tester);
          await tester.tap(remove);
        },
      );
    });
  }
}
