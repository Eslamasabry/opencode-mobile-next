// The one-time "Help improve OpenCode Mobile" question on a phone, light and
// dark, and in Arabic. Regenerate deliberately:
//   flutter test --update-goldens test/goldens/telemetry_consent_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/telemetry/telemetry.dart';
import 'package:opencode_mobile/telemetry/telemetry_provider.dart';
import 'package:opencode_mobile/ui/widgets/telemetry_consent.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit/kit_gallery.dart';

class _Vendor extends NoOpTelemetryAdapter {
  const _Vendor();
  @override
  Future<bool> probe() async => true;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  late SharedPreferences prefs;
  late TelemetryService telemetry;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    telemetry = TelemetryService(adapter: const _Vendor());
    await telemetry.start();
  });

  void open(BuildContext context) {
    // Each theme pass asks afresh.
    unawaited(prefs.remove(telemetryConsentAskedKey));
    unawaited(
      askTelemetryConsentOnce(context, prefs: prefs, telemetry: telemetry),
    );
  }

  const phone = Size(412, 915);
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('telemetry consent · 412x915 · $mode', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_confirm_telemetry_consent',
          phone,
          light: light,
        ),
        size: phone,
        light: light,
        open: open,
      );
    });
    testWidgets('telemetry consent · ar · 412x915 · $mode', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_confirm_telemetry_consent',
          phone,
          light: light,
          ar: true,
        ),
        size: phone,
        light: light,
        locale: const Locale('ar'),
        open: open,
      );
    });
  }
}
