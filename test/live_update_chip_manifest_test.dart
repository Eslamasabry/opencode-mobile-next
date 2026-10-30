// The status bar chip (Android 16 QPR1+ live updates) needs the promoted
// notifications permission, or the system never promotes the notification
// that asks for it and the chip never shows (found 2026-10-26: the renderer
// asked, the manifest never declared it).
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('a notification that asks to be promoted has the permission', () {
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final renderer = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/StatusNotificationRenderer.kt',
    ).readAsStringSync();
    expect(renderer, contains('setRequestPromotedOngoing'));
    expect(
      manifest,
      contains('android.permission.POST_PROMOTED_NOTIFICATIONS'),
    );
  });
}
