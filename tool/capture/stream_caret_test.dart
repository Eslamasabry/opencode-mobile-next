// The typing caret in a real conversation, mid-stream, dark and light: the
// checkout chat from capture_test.dart with its reply still being written.
//
//   STREAM_CARET_OUT=/some/dir flutter test tool/capture/stream_caret_test.dart
//
// Images land in STREAM_CARET_OUT (default build/stream-caret).

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_stream_caret.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/provider_logo.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

final _out = Platform.environment['STREAM_CARET_OUT'] ?? 'build/stream-caret';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    await loadCaptureFonts();
    ProviderLogo.logoLoaderOverride = (_) => null;
    Directory(_out).createSync(recursive: true);
  });
  tearDownAll(() => ProviderLogo.logoLoaderOverride = null);

  for (final light in [false, true]) {
    final name = light ? 'light' : 'dark';
    testWidgets('chat mid-stream, $name', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.reset);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      try {
        SharedPreferencesStorePlatform.instance =
            InMemorySharedPreferencesStore.empty();
        final api = CaptureApi()..diffs = sampleDiffs();
        api.messagesHandler = (_) async => sampleTranscript(streaming: true);
        final controller = await captureController(
          prefs: await SharedPreferences.getInstance(),
          api: api,
        );
        addTearDown(controller.dispose);
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: const ChatScreen(sessionID: checkoutSessionID),
            boundaryKey: key,
            controller: controller,
            light: light,
          ),
        );
        for (var i = 0; i < 60; i++) {
          await tester.pump(const Duration(milliseconds: 33));
        }
        // One caret, on the reply still being written.
        expect(find.byType(KitStreamCaret), findsOneWidget);
        await writePng(
          '$_out/stream-caret-$name.png',
          await capturePng(tester, key),
        );
      } finally {
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}
