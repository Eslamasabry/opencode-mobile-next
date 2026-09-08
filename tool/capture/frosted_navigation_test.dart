import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/widgets/glass_surface.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [false, true]) {
    for (final mode in ['normal', 'reduced', 'large']) {
      testWidgets('frosted navigation ${light ? 'light' : 'dark'} $mode', (
        tester,
      ) async {
        const secure = MethodChannel(
          'plugins.it_nomads.com/flutter_secure_storage',
        );
        tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
        addTearDown(
          () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
            secure,
            null,
          ),
        );
        tester.view.physicalSize = Size(mode == 'large' ? 320 : 390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final prefs = await setupCapturePreferences();
        final api = CaptureApi();
        final now = DateTime.now().millisecondsSinceEpoch;
        // Genuine session rows and their colored working status provide the
        // underlay; no decorative image or fake glass texture is added.
        for (var i = 0; i < 24; i++) {
          final id = 'glass-session-$i';
          api.sessionsById[id] = Session(
            id: id,
            title:
                'Review ${['checkout', 'payments', 'search', 'accounts'][i % 4]} module $i',
            directory: projectDirectory,
            time: SessionTime(
              created: now - i * 60000,
              updated: now - i * 60000,
            ),
            summary: SessionDiffSummary(
              additions: 24 + i,
              deletions: i,
              files: 3,
            ),
          );
          api.busy.add(id);
        }
        final controller = await captureController(prefs: prefs, api: api);
        final boundary = GlobalKey();
        try {
          await tester.pumpWidget(
            captureApp(
              home: Builder(
                builder: (context) => MediaQuery(
                  data: MediaQuery.of(context).copyWith(
                    disableAnimations: mode == 'reduced',
                    textScaler: TextScaler.linear(mode == 'large' ? 2.5 : 1),
                  ),
                  child: const HomeScreen(),
                ),
              ),
              boundaryKey: boundary,
              controller: controller,
              light: light,
            ),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 600));
          final scroll = tester.state<ScrollableState>(
            find
                .descendant(
                  of: find.byKey(const PageStorageKey('workspace-scroll')),
                  matching: find.byType(Scrollable),
                )
                .first,
          );
          scroll.position.jumpTo(220);
          await tester.pump(const Duration(milliseconds: 200));
          final dock = tester.getRect(find.byType(GlassSurface));
          final action = tester.getRect(
            find.byKey(const ValueKey('workspace-quick-ask')),
          );
          expect(action.bottom, lessThanOrEqualTo(dock.top - 12));
          expect(
            find.byType(BackdropFilter),
            mode == 'reduced' ? findsNothing : findsOneWidget,
          );
          expect(tester.takeException(), isNull);
          final name = '${light ? 'light' : 'dark'}-$mode';
          await writePng(
            'docs/qa/frosted-navigation/$name-scrolled.png',
            await capturePng(tester, boundary, pixelRatio: 1),
          );
          if (mode == 'normal') {
            scroll.position.jumpTo(scroll.position.maxScrollExtent);
            await tester.pump(const Duration(milliseconds: 200));
            await writePng(
              'docs/qa/frosted-navigation/$name-end.png',
              await capturePng(tester, boundary, pixelRatio: 1),
            );
          }
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          await tester.pump();
        }
      });
    }
  }
}
