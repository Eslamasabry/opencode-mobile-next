// slice-close-servers (review-board closure, 2026-09-28): This phone says
// what went wrong in plain words and offers the act that fixes it. When the
// app cannot prove it owns the old server processes, the way forward is
// Android's app settings, never another start.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import 'revamp/screen_phone_1_fixtures.dart';

class _OwnershipUnknownLinux extends PhoneLinux {
  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    starts++;
    throw const BuiltinLinuxException(
      'The app cannot safely identify previous work.',
      code: 'runtime_ownership_unknown',
      details: 'IllegalStateException: ownershipUnknown',
    );
  }
}

Future<void> _settle(WidgetTester tester, {int frames = 10}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  Future<void> done(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 2));
    await unmountPhone(tester);
  }

  testWidgets('ownership failure offers settings instead of another start', (
    tester,
  ) async {
    final linux = _OwnershipUnknownLinux();
    final profile = ServerProfile(
      id: 'builtin',
      name: 'This phone',
      baseUrl: BuiltinLinux.serverUrl,
      flavor: ServerFlavor.v2,
      password: 'synthetic-test-secret',
    );
    var settings = 0;
    const channel = MethodChannel('oc/app');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'openAppSettings') settings++;
      return true;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    await pumpPhone(
      tester,
      home: const ThisPhoneScreen(),
      profiles: [profile],
      linux: linux,
    );
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('this-phone-start')));
    await _settle(tester);
    expect(
      find.text(AppLocalizationsEn().inAppServerOwnershipRecoveryBody),
      findsOneWidget,
    );
    expect(find.textContaining('ownershipUnknown'), findsNothing);
    expect(find.byKey(const ValueKey('this-phone-start')), findsNothing);
    expect(find.byKey(const ValueKey('this-phone-reinstall')), findsNothing);
    await tester.tap(
      find.byKey(const ValueKey('this-phone-recover-ownership')),
    );
    await _settle(tester);
    expect(settings, 1);
    expect(linux.starts, 1);
    await done(tester);
  });
}
