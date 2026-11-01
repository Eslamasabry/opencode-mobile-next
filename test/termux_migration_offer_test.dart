import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/termux_migration.dart';
import 'package:opencode_mobile/state/termux_migration_offer.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();
  final entered = Completer<void>();
  final release = Completer<void>();
  bool hold = true;
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (hold && key == 'flutter.oc.termuxMigrationOffer.phone') {
      hold = false;
      entered.complete();
      await release.future;
    }
    return super.setValue(type, key, value);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'deletion drains dismissal and keeps admission closed through its sweep',
    () async {
      SharedPreferences.setMockInitialValues({
        'oc.profiles': '[{"id":"phone"}]',
      });
      final disk = _Disk(
        await SharedPreferencesStorePlatform.instance.getAll(),
      );
      SharedPreferencesStorePlatform.instance = disk;
      SharedPreferences.resetStatic();
      final prefs = await SharedPreferences.getInstance();
      final write = TermuxMigrationOfferStore.dismiss(
        prefs,
        'phone',
        mayWrite: () => true,
      );
      final failed = expectLater(
        write,
        throwsA(isA<TermuxMigrationException>()),
      );
      await disk.entered.future;
      var drained = false;
      final drain = ProfileStore(
        prefs: prefs,
      ).removeScopedPreferences('phone').then((_) => drained = true);
      await Future<void>.delayed(Duration.zero);
      expect(drained, isFalse);
      disk.release.complete();
      await failed;
      await drain;
      expect(prefs.containsKey('oc.termuxMigrationOffer.phone'), isFalse);
      await expectLater(
        TermuxMigrationOfferStore.dismiss(prefs, 'phone', mayWrite: () => true),
        throwsA(isA<TermuxMigrationException>()),
      );
      await prefs.reload();
      expect(prefs.containsKey('oc.termuxMigrationOffer.phone'), isFalse);
      await TermuxMigrationOfferStore.cancelDeletion(prefs, 'phone');
      await TermuxMigrationOfferStore.dismiss(
        prefs,
        'phone',
        mayWrite: () => true,
      );
      expect(prefs.getBool('oc.termuxMigrationOffer.phone'), isTrue);
    },
  );
}
