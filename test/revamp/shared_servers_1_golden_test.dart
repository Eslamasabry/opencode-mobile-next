// Golden renders of shared-servers-1's pages (wave 2a): the server switcher
// sheet, rebuilt from kit parts. Phone 412x915 and one wide
// window (1280x800), dark and light (owner decision 2026-09-27: no Arabic),
// with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/shared_servers_1_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/widgets/server_switcher_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'shared_servers_1_fixtures.dart';

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
  Widget body = const SizedBox.expand(),
  FutureOr<void> Function(BuildContext context)? open,
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
                  return body;
                },
              ),
            ),
          ),
        ),
      ),
    );
    if (open != null) unawaited(Future.sync(() => open(context)));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
    debugPlatformCapabilities = null;
  }
}

Future<SwitcherController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final controller = SwitcherController(
    SwitcherStore(prefs: await SharedPreferences.getInstance()),
    snapshots: {
      'laptop': snapshot('laptop', waiting: 1, running: 1),
      'lab': snapshot('lab', running: 2),
    },
  )..status = StreamStatus.connected;
  controller.api = FakeServerGateway(baseUrl: controller.profile!.baseUrl);
  return controller;
}

void main() {
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';
    for (final size in [_phone, _wide]) {
      testWidgets('server switcher ($theme, $size)', (tester) async {
        final controller = await _controller();
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'server_switcher_sheet',
          light: light,
          size: size,
          open: (context) => showServerSwitcher(context, controller),
        );
      });
    }
  }
}
