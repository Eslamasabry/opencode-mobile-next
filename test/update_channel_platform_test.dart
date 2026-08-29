import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/update/desktop_release_check.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';

/// Records whether the GitHub release check was reached at all.
class _CountingChecker extends DesktopReleaseChecker {
  var called = false;

  @override
  Future<DesktopReleaseInfo?> fetchLatest() async {
    called = true;
    return null;
  }
}

Future<void> _pumpNotice(
  WidgetTester tester, {
  required GlobalKey<ScaffoldMessengerState> messengerKey,
  required DesktopReleaseChecker checker,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      scaffoldMessengerKey: messengerKey,
      home: DesktopReleaseNotice(
        messengerKey: messengerKey,
        checker: checker,
        currentBuildNumberLoader: () async => 20,
        child: const Scaffold(body: SizedBox.shrink()),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() => debugPlatformCapabilities = null);

  test('the unavailable service never claims an update', () async {
    const service = UnavailableAppUpdateService();
    expect(service.isAvailable, isFalse);
    expect(await service.checkForUpdate(), AppUpdateState.unavailable);
    await expectLater(service.downloadUpdate(), completes);
  });

  testWidgets(
    'the GitHub release check stays off where Shorebird patches',
    (tester) async {
      // Android: code push is the update channel, so a second notice must not
      // also poll GitHub. Previously this was decided by dart:io Platform,
      // which reports the Linux *host* — so the suite ran the desktop check
      // on every test that mounted the app.
      final checker = _CountingChecker();
      await _pumpNotice(
        tester,
        messengerKey: GlobalKey<ScaffoldMessengerState>(),
        checker: checker,
      );
      expect(checker.called, isFalse);
    },
  );

  testWidgets('the GitHub release check runs on desktop', (tester) async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final checker = _CountingChecker();
    await _pumpNotice(
      tester,
      messengerKey: GlobalKey<ScaffoldMessengerState>(),
      checker: checker,
    );
    expect(checker.called, isTrue);
  });
}
