import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/shared_storage_path.dart';

void main() {
  test('shared storage paths are recognised by every name Android gives', () {
    for (final path in [
      '/sdcard',
      '/sdcard/CodeAnything',
      '/sdcard/CodeAnything/',
      '/storage/emulated/0/CodeAnything',
      '/storage/emulated/10/Documents',
      '/storage/self/primary/x',
      '/storage/1A2B-3C4D/projects/app',
      '/mnt/sdcard/app',
      '/data/data/com.termux/files/home/storage/shared/app',
      '~/storage/shared/app',
      '/root/projects/../../sdcard/app',
    ]) {
      expect(isSharedStoragePath(path), isTrue, reason: path);
    }
  });

  test('the app project space and other servers are not shared storage', () {
    for (final path in [
      null,
      '',
      '/',
      '/root/projects/app',
      '/root/projects/sdcard',
      '/home/me/sdcard',
      '/data/data/com.termux/files/home/projects/app',
      '/data/data/com.termux/files/home',
      '/storage',
      '/storage/emulated',
      '/storagefoo/emulated/0',
      '/sdcardx/app',
      'C:/Users/me/project',
    ]) {
      expect(isSharedStoragePath(path), isFalse, reason: '$path');
    }
  });

  test('the storage permission is declared and bound only outside the '
      'confined tier, with nothing stored for it', () {
    const native =
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    expect(manifest, contains('android.permission.MANAGE_EXTERNAL_STORAGE'));
    expect(
      manifest,
      contains('READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"'),
    );
    expect(
      manifest,
      contains('WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29"'),
    );

    final linux = File('$native/BuiltinLinux.kt').readAsStringSync();
    expect(linux, contains('--bind=/storage"'));
    expect(linux, contains(':/sdcard"'));
    expect(linux, contains('+ sharedStorageBinds() +'));
    // The AI Team's confined tier is never given shared storage.
    expect(
      RegExp(
        r'fun sharedStorageBinds[^}]*if \(prootIsConfined\) return emptyList\(\)',
        dotAll: true,
      ).hasMatch(linux),
      isTrue,
    );
    final protectedBody = linux.substring(
      linux.indexOf('private fun protectedCommand'),
      linux.indexOf('private fun requirePhoneBoundaryKernel'),
    );
    expect(protectedBody, isNot(contains('/storage')));
    expect(protectedBody, isNot(contains('sdcard')));

    final activity = File('$native/MainActivity.kt').readAsStringSync();
    expect(activity, contains('StorageAccess.register('));
    expect(activity, contains('StorageAccess.onPermissionResult('));

    // Nothing about the permission is saved, so profile deletion has no
    // per-profile key to sweep.
    for (final file in [
      'lib/platform/storage_access.dart',
      'lib/state/shared_storage_gate.dart',
      'lib/ui/screens/shared_storage_access_flow.dart',
    ]) {
      expect(File(file).readAsStringSync(), isNot(contains('prefs')));
      expect(
        File(file).readAsStringSync(),
        isNot(contains('SharedPreferences')),
      );
    }
  });
}
