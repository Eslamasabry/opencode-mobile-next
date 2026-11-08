// Opt-in analytics never reads an advertising ID (PRIVACY.md): the Android
// manifest removes every ad permission the Firebase Analytics SDK would merge
// in, keeps collection off until consent, and turns off ad ID, SSAID, ad
// personalisation and automatic screen reporting. Found on the emulator: the
// SDK also merges Android's ad-services permissions and library.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final manifest = File(
    'android/app/src/main/AndroidManifest.xml',
  ).readAsStringSync().replaceAll(RegExp(r'\s+'), ' ');

  bool removed(String tag, String name) => RegExp(
    '<$tag android:name="${RegExp.escape(name)}"[^>]*tools:node="remove"',
  ).hasMatch(manifest);

  test('every advertising permission and library is removed', () {
    for (final permission in [
      'com.google.android.gms.permission.AD_ID',
      'android.permission.ACCESS_ADSERVICES_AD_ID',
      'android.permission.ACCESS_ADSERVICES_ATTRIBUTION',
    ]) {
      expect(
        removed('uses-permission', permission),
        isTrue,
        reason: permission,
      );
    }
    expect(removed('uses-library', 'android.ext.adservices'), isTrue);
  });

  test('collection is off until consent, and no ad signals', () {
    for (final flag in [
      'firebase_crashlytics_collection_enabled',
      'firebase_analytics_collection_enabled',
      'google_analytics_adid_collection_enabled',
      'google_analytics_ssaid_collection_enabled',
      'google_analytics_default_allow_ad_personalization_signals',
      'google_analytics_default_allow_ad_user_data',
      'google_analytics_default_allow_ad_storage',
      'google_analytics_automatic_screen_reporting_enabled',
    ]) {
      expect(
        manifest,
        contains('<meta-data android:name="$flag" android:value="false" />'),
        reason: flag,
      );
    }
    // Deactivated would be permanent: consent could never turn it on.
    expect(
      manifest,
      isNot(contains('firebase_analytics_collection_deactivated')),
    );
  });
}
