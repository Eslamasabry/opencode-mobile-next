// Golden renders of the home-screen widgets' floating windows at 412x915, dark
// and light, over a stand-in home screen (the real window is transparent over
// the launcher): the Conversations widget's project window, the style window, the steer box, the box with a draft that
// could not be sent, and the voice box.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/widget_overlay_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart'
    show debugDefaultTargetPlatformOverride;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart' show AppAppearance;
import 'package:opencode_mobile/widgets_overlay/widget_overlay_api.dart';
import 'package:opencode_mobile/widgets_overlay/widget_overlay_app.dart';
import 'package:opencode_mobile/widgets_overlay/widget_overlay_launch.dart';

import 'kit/kit_gallery.dart';

class _Api implements WidgetOverlayApi {
  _Api({this.result = const {'status': 'accepted'}, this.heard = ''});
  final Map<String, dynamic> result;
  final String heard;
  @override
  Future<Map<String, dynamic>> getLaunchArguments() async => {};
  @override
  Future<void> saveStyle({
    required String background,
    required String accent,
  }) async {}
  @override
  Future<void> saveProject(String project) async {}
  @override
  Future<String> dictate({String? languageTag}) async => heard;
  @override
  Future<Map<String, dynamic>> submitAction(String text) async => result;
  @override
  Future<void> close() async {}
}

/// A stand-in for the launcher behind the transparent window.
class _Home extends StatelessWidget {
  const _Home({required this.light, required this.child});
  final bool light;
  final Widget child;
  @override
  Widget build(BuildContext context) => Stack(
    textDirection: TextDirection.ltr,
    fit: StackFit.expand,
    children: [
      DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: light
                ? const [Color(0xFFCFE3F2), Color(0xFFE9DCEF)]
                : const [Color(0xFF1B2A3A), Color(0xFF2E2140)],
          ),
        ),
      ),
      child,
    ],
  );
}

void main() {
  setUpAll(loadKitGalleryFonts);

  Future<void> shot(
    WidgetTester tester, {
    required String name,
    required bool light,
    required String route,
    String steerMode = 'steer',
    String background = 'tinted',
    String accent = 'green',
    _Api? api,
    String? type,
    bool tapSend = false,
    List<String> projects = const [],
    String project = '',
  }) async {
    tester.view.physicalSize = const Size(412, 915) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final boundary = GlobalKey();
    debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: _Home(
          light: light,
          child: WidgetOverlayApp(
            api: api ?? _Api(),
            launch: WidgetOverlayLaunch(
              route: route,
              steerMode: steerMode,
              styleBackground: background,
              styleAccent: accent,
              projects: projects,
              project: project,
            ),
            appearance: light ? AppAppearance.light : AppAppearance.dark,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (type != null) {
      await tester.enterText(find.byType(TextField), type);
      await tester.pumpAndSettle();
    }
    if (tapSend) {
      await tester.tap(find.text('Send'));
      await tester.pumpAndSettle();
    }
    await expectLater(find.byKey(boundary), matchesGoldenFile('$name.png'));
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('configure · $mode', (tester) async {
      await shot(
        tester,
        name: 'widget_overlay_configure_$mode',
        light: light,
        route: '/widgets/configure',
      );
    });
    testWidgets('project · $mode', (tester) async {
      await shot(
        tester,
        name: 'widget_overlay_project_$mode',
        light: light,
        route: '/widgets/project',
        projects: const ['IPTV_King', 'settings-demo', 'oc_app'],
        project: 'settings-demo',
      );
    });
    testWidgets('steer · $mode', (tester) async {
      await shot(
        tester,
        name: 'widget_overlay_steer_$mode',
        light: light,
        route: '/widgets/steer',
        type: 'Also add a test for the empty case',
      );
    });
    testWidgets('after reply · $mode', (tester) async {
      await shot(
        tester,
        name: 'widget_overlay_after_reply_$mode',
        light: light,
        route: '/widgets/steer',
        steerMode: 'afterReply',
      );
    });
    testWidgets('could not send · $mode', (tester) async {
      await shot(
        tester,
        name: 'widget_overlay_failed_$mode',
        light: light,
        route: '/widgets/steer',
        api: _Api(
          result: const {'status': 'rejected', 'error': 'staleRequest'},
        ),
        type: 'Also add a test for the empty case',
        tapSend: true,
      );
    });
    testWidgets('voice · $mode', (tester) async {
      await shot(
        tester,
        name: 'widget_overlay_voice_$mode',
        light: light,
        route: '/widgets/voice',
        api: _Api(heard: 'Start a new conversation about the release notes'),
      );
    });
  }
}
