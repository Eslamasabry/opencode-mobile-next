// Golden renders of This phone's per-tool removal (slice P1.4): the end of
// the list with "Remove Python" and "Remove AI Team" before "Remove
// OpenCode", a tool that another still needs, and the questions that say
// what goes, what stays and the space that comes back. Phone 412x915 and
// one wide window (1280x800), dark and light, with the app's real fonts.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/this_phone_remove_tools_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/component_removal.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../revamp/screen_phone_1_fixtures.dart';
import '../support/this_phone_tools_fakes.dart';

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 12; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _shot(
  WidgetTester tester,
  String name, {
  required bool light,
  Size size = phoneSize,
  Set<String> present = const {'node', 'opencode', 'python', 'aiteam'},
  String? open,
}) async {
  final boundary = GlobalKey();
  final linux = ToolsLinux(present: present);
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    await pumpPhone(
      tester,
      home: ThisPhoneScreen(
        removal: ComponentRemovalService(
          linux: linux,
          registry: toolsRegistry,
          team: ToolsTeam(linux),
        ),
      ),
      linux: linux,
      size: size,
      light: light,
      profiles: [inAppProfile],
      boundary: boundary,
    );
    await _settle(tester);
    final end = find.byKey(const ValueKey('this-phone-remove'));
    await tester.scrollUntilVisible(
      end,
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await _settle(tester);
    if (open != null) {
      await tester.tap(find.byKey(ValueKey('this-phone-remove-$open')));
      await _settle(tester);
    }
    expect(tester.takeException(), isNull);
    final suffix = [
      if (size != phoneSize) '${size.width.toInt()}x${size.height.toInt()}',
      light ? 'light' : 'dark',
    ].join('_');
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('this_phone_${name}_$suffix.png'),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
    await unmountPhone(tester);
  }
}

void main() {
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';
    testWidgets('remove rows ($theme)', (tester) async {
      await _shot(tester, 'remove_rows', light: light);
    });
    testWidgets('remove rows, wide ($theme)', (tester) async {
      await _shot(
        tester,
        'remove_rows',
        light: light,
        size: const Size(1280, 800),
      );
    });
    testWidgets('remove Python question ($theme)', (tester) async {
      await _shot(tester, 'remove_python', light: light, open: 'python');
    });
    testWidgets('remove Python question, wide ($theme)', (tester) async {
      await _shot(
        tester,
        'remove_python',
        light: light,
        size: const Size(1280, 800),
        open: 'python',
      );
    });
    testWidgets('remove AI Team question ($theme)', (tester) async {
      await _shot(tester, 'remove_aiteam', light: light, open: 'aiteam');
    });
  }

  testWidgets('a tool another still needs (dark)', (tester) async {
    await _shot(
      tester,
      'remove_needed',
      light: false,
      present: const {'node', 'opencode', 'python', 'notebooks'},
    );
  });
}
