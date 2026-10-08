import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/app_locale.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();
  bool refuse = false;
  Completer<void>? gate;
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    await gate?.future;
    return refuse ? false : super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async => refuse ? false : super.remove(key);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Disk disk;
  late ConnectionController controller;

  setUp(() async {
    SharedPreferences.setMockInitialValues({
      AppLocaleStore.preferenceKey: 'en',
    });
    prefs = await SharedPreferences.getInstance();
    disk = _Disk(await SharedPreferencesStorePlatform.instance.getAll());
    SharedPreferencesStorePlatform.instance = disk;
    controller = ConnectionController(
      ProfileStore(prefs: prefs),
      isIsolated: true,
    );
  });
  tearDown(() => controller.dispose());

  test(
    'saved language refreshes native notifications after acknowledged write',
    () async {
      final calls = <String?>[];
      const channel = MethodChannel('oc/background');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'refreshNativeLocale') {
              calls.add(
                (await disk.getAll())['flutter.oc.appLocale'] as String?,
              );
            }
            return <String, dynamic>{};
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      await controller.setAppLocale(const Locale('ar'));
      await controller.setAppLocale(null);
      expect(calls, ['ar', null]);
      disk.refuse = true;
      await expectLater(
        controller.setAppLocale(const Locale('en')),
        throwsA(isA<AppLocaleSaveException>()),
      );
      expect(calls, ['ar', null]);
    },
  );

  test(
    'Arabic override survives new controller and system removes override',
    () async {
      await controller.setAppLocale(const Locale('ar', 'EG'));
      final restarted = ConnectionController(
        ProfileStore(prefs: prefs),
        isIsolated: true,
      );
      expect(restarted.appLocale.value, const Locale('ar'));
      restarted.dispose();
      await controller.setAppLocale(null);
      expect(controller.appLocale.value, isNull);
      expect(
        (await disk.getAll()).containsKey(
          'flutter.${AppLocaleStore.preferenceKey}',
        ),
        isFalse,
      );
    },
  );

  test('refused write and remove keep acknowledged locale and cache', () async {
    disk.refuse = true;
    await expectLater(
      controller.setAppLocale(const Locale('ar')),
      throwsA(isA<AppLocaleSaveException>()),
    );
    expect(controller.appLocale.value, const Locale('en'));
    expect(AppLocaleStore(prefs).value, const Locale('en'));
    await expectLater(
      controller.setAppLocale(null),
      throwsA(isA<AppLocaleSaveException>()),
    );
    expect(controller.appLocale.value, const Locale('en'));
    disk.refuse = false;
    await controller.setAppLocale(const Locale('ar'));
    expect(controller.appLocale.value, const Locale('ar'));
  });

  test(
    'queued rapid choices wait for storage and newest choice wins',
    () async {
      disk.gate = Completer<void>();
      final first = controller.setAppLocale(const Locale('ar'));
      final second = controller.setAppLocale(const Locale('en'));
      await Future<void>.delayed(Duration.zero);
      expect(controller.appLocale.value, const Locale('en'));
      disk.gate!.complete();
      await Future.wait([first, second]);
      expect(controller.appLocale.value, const Locale('en'));
      expect(AppLocaleStore(prefs).value, const Locale('en'));
    },
  );

  test(
    'unsupported stored value follows system; unsupported saves rejected',
    () async {
      await prefs.setString(AppLocaleStore.preferenceKey, 'de');
      expect(AppLocaleStore(prefs).value, isNull);
      await expectLater(
        controller.setAppLocale(const Locale('de')),
        throwsArgumentError,
      );
      expect(controller.appLocale.value, const Locale('en'));
    },
  );

  test('profile deletion key sweep excludes global language choice', () {
    expect(
      controller.store.profileScopedPreferenceKeys('server-id'),
      isNot(contains(AppLocaleStore.preferenceKey)),
    );
  });
}
