// Golden renders of screen-settings-1's pages (wave 2b), rebuilt from kit
// parts: the Settings hub (one pane on a phone, two panes from expanded),
// Notifications, Appearance, Privacy and local data with its delete
// confirmation, and Always allowed actions (loaded, empty, error, revoke
// confirmation). Phone 412x915 and one wide window (1280x800), dark and
// light (owner decision 2026-09-27: no Arabic), with the app's real fonts at
// DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_settings_1_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/saved_permissions_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/settings_scenes.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

class _Permissions implements ProductRepository {
  _Permissions(this.permissions, {this.error});

  final List<SavedPermission> permissions;
  final Object? error;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<SavedPermission>> listSavedPermissions() async {
    if (error case final error?) throw error;
    return permissions;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

const _grants = [
  SavedPermission(
    id: 'bash',
    projectID: 'shopfront',
    action: 'bash',
    resource: 'flutter test *',
  ),
  SavedPermission(
    id: 'edit',
    projectID: 'shopfront',
    action: 'edit',
    resource: 'lib/checkout/**',
  ),
  SavedPermission(
    id: 'fetch',
    projectID: 'shopfront',
    action: 'webfetch',
    resource: '',
  ),
];

/// A Settings-area scene from test/support/settings_scenes.dart, optionally
/// re-laid out at [size] and with [then] run before the capture.
Future<void> _scene(
  WidgetTester tester,
  SettingsScene scene,
  String shot, {
  required bool light,
  Size size = _phone,
  Future<void> Function()? then,
}) async {
  final boundary = GlobalKey();
  final done = await mountSettingsScene(
    tester,
    scene,
    light: light,
    boundary: boundary,
  );
  try {
    if (size != _phone) {
      tester.view.physicalSize = size;
      await tester.pumpAndSettle();
    }
    if (then != null) await then();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await done();
  }
}

Future<void> _permissions(
  WidgetTester tester,
  String shot, {
  required bool light,
  required ProductRepository repository,
  Size size = _phone,
  Future<void> Function()? then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  SharedPreferences.setMockInitialValues({});
  final controller =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..repository = repository
        ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: SavedPermissionsScreen(controller: controller),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (then != null) await then();
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
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('settings hub · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.hub,
        'settings_hub_loaded',
        light: light,
      );
    });
    testWidgets('what runs by itself · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.automation,
        'settings_automation_loaded',
        light: light,
      );
    });
    testWidgets('settings hub two panes · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.hub,
        'settings_hub_loaded',
        light: light,
        size: _wide,
      );
    });
    testWidgets('settings hub report a problem badge · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.hub,
        'settings_hub_report_problem',
        light: light,
        then: () async {
          // Report a problem is a row of the hub's Help page (vision
          // 2026-10-20: five groups).
          final help = find.byKey(const ValueKey('settings-hub-row-help'));
          await tester.scrollUntilVisible(
            help,
            200,
            scrollable: find.byType(Scrollable).first,
          );
          await tester.tap(help);
          await tester.pumpAndSettle();
          await tester.ensureVisible(
            find.byKey(const ValueKey('library-report-bug')),
          );
          await tester.pumpAndSettle();
        },
      );
    });
    testWidgets('notifications · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.notifications,
        'settings_notifications_loaded',
        light: light,
      );
    });
    testWidgets('appearance · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.appearance,
        'settings_appearance_loaded',
        light: light,
      );
    });
    testWidgets('privacy · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.privacy,
        'settings_privacy_loaded',
        light: light,
      );
    });
    testWidgets('privacy delete drafts sheet · $mode', (tester) async {
      await _scene(
        tester,
        SettingsScene.privacy,
        'settings_privacy_clear_drafts_sheet_confirming',
        light: light,
        then: () async {
          await tester.tap(find.byKey(const ValueKey('clear-session-drafts')));
          await tester.pumpAndSettle();
        },
      );
    });
    testWidgets('saved permissions loaded · $mode', (tester) async {
      await _permissions(
        tester,
        'settings_saved_permissions_loaded',
        light: light,
        repository: _Permissions(_grants),
      );
    });
    testWidgets('saved permissions empty · $mode', (tester) async {
      await _permissions(
        tester,
        'settings_saved_permissions_empty',
        light: light,
        repository: _Permissions(const []),
      );
    });
    testWidgets('saved permissions error · $mode', (tester) async {
      await _permissions(
        tester,
        'settings_saved_permissions_error',
        light: light,
        repository: _Permissions(
          const [],
          error: const ProductException(
            'Always allowed actions are not available on this server.',
          ),
        ),
      );
    });
    testWidgets('saved permissions revoke confirmation · $mode', (
      tester,
    ) async {
      await _permissions(
        tester,
        'settings_saved_permissions_revoke_dialog_confirming',
        light: light,
        repository: _Permissions(_grants),
        then: () async {
          await tester.tap(
            find.byKey(const ValueKey('revoke-saved-permission-bash')),
          );
          await tester.pumpAndSettle();
        },
      );
    });
  }

  // The loaded pages on a wide window, dark only (TEST-10).
  testWidgets('notifications wide · dark', (tester) async {
    await _scene(
      tester,
      SettingsScene.notifications,
      'settings_notifications_loaded',
      light: false,
      size: _wide,
    );
  });
  testWidgets('appearance wide · dark', (tester) async {
    await _scene(
      tester,
      SettingsScene.appearance,
      'settings_appearance_loaded',
      light: false,
      size: _wide,
    );
  });
  testWidgets('privacy wide · dark', (tester) async {
    await _scene(
      tester,
      SettingsScene.privacy,
      'settings_privacy_loaded',
      light: false,
      size: _wide,
    );
  });
  testWidgets('what runs by itself wide · dark', (tester) async {
    await _scene(
      tester,
      SettingsScene.automation,
      'settings_automation_loaded',
      light: false,
      size: _wide,
    );
  });
  testWidgets('saved permissions wide · dark', (tester) async {
    await _permissions(
      tester,
      'settings_saved_permissions_loaded',
      light: false,
      repository: _Permissions(_grants),
      size: _wide,
    );
  });
}
