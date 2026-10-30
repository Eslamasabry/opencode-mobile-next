import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/settings_scope.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();

  String? heldKey;
  Completer<void>? entered;
  Completer<void>? release;
  String? refusedKey;

  void hold(String key) {
    heldKey = 'flutter.$key';
    entered = Completer<void>();
    release = Completer<void>();
  }

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (key == heldKey) {
      heldKey = null;
      entered!.complete();
      await release!.future;
    }
    if (key == refusedKey) return false;
    return super.setValue(type, key, value);
  }
}

class _Reads extends ProductRepository implements SessionReadStateGateway {
  final receipts = <(String, int)>[];
  Completer<void>? entered;
  Completer<void>? release;

  @override
  Future<void> viewSession(String id, int idle) async {
    receipts.add((id, idle));
    entered?.complete();
    await release?.future;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// No connect/bootstrap: named controllers exercise production settings and
/// receipt paths without starting monitors, native listeners or a transport.
class _Controller extends ConnectionController {
  _Controller(super.store, this.owner) : super(isIsolated: true);

  final ServerProfile owner;
  Completer<void>? wakeEntered;
  Completer<void>? wakeRelease;

  @override
  ServerProfile get profile => owner;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    wakeEntered?.complete();
    await wakeRelease?.future;
    return repository;
  }
}

ServerProfile _profile(String id) => ServerProfile(
  id: id,
  name: id == 'laptop' ? 'Laptop' : 'Phone',
  baseUrl: 'https://$id.example.test',
);

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const background = MethodChannel('oc/background');
  late SharedPreferences prefs;
  late _Disk disk;
  late ProfileStore store;

  setUp(() async {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      (call) async => call.method == 'readAll' ? <String, String>{} : null,
    );
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      background,
      (_) async => <String, dynamic>{},
    );
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    disk = _Disk(await SharedPreferencesStorePlatform.instance.getAll());
    SharedPreferencesStorePlatform.instance = disk;
    store = ProfileStore(prefs: prefs);
    // Establish an empty migration cohort before adding fresh connections.
    await store.load();
    await store.upsert(_profile('laptop'));
    await store.upsert(_profile('phone'));
  });

  tearDown(() {
    binding.defaultBinaryMessenger.setMockMethodCallHandler(secure, null);
    binding.defaultBinaryMessenger.setMockMethodCallHandler(background, null);
  });

  _Controller controller(String id, {ProfileStore? profiles}) {
    final result = _Controller(profiles ?? store, _profile(id));
    addTearDown(result.dispose);
    return result;
  }

  _Reads reads(_Controller target) {
    final repository = _Reads();
    target.repository = repository;
    target.directory = '/project';
    target.sessionsById['chat'] = Session(
      id: 'chat',
      directory: '/project',
      time: SessionTime(created: 1, updated: 100, idle: 100, viewed: 0),
    );
    return repository;
  }

  test(
    'live controllers share acknowledged display preferences across stores',
    () async {
      final main = controller('laptop');
      final secondary = controller('phone');
      final anotherStore = ProfileStore(prefs: prefs);
      await anotherStore.load();
      final isolated = controller('laptop', profiles: anotherStore);
      expect(anotherStore.appPreferences, same(store.appPreferences));

      await main.setAppearance(AppAppearance.light);
      await secondary.setThemePack(ThemePackId.nord);
      await isolated.setAppLocale(const Locale('ar'));
      await main.setTranscriptReasoningExpanded(true);
      await secondary.setTranscriptTimestampsVisible(false);
      const chosen = KitEffects(
        motion: KitMotionLevel.calm,
        celebrations: false,
        activityGlow: false,
        glowStyle: KitGlowStyle.softRing,
      );
      await isolated.setEffects(chosen);

      for (final target in [main, secondary, isolated]) {
        expect(target.appearance.value, AppAppearance.light);
        expect(target.themePack.value, ThemePackId.nord);
        expect(target.appLocale.value, const Locale('ar'));
        expect(target.transcriptReasoningExpanded, isTrue);
        expect(target.transcriptTimestampsVisible, isFalse);
        expect(target.effects.value, chosen);
      }
    },
  );

  test(
    'disposing a secondary controller leaves shared preferences usable',
    () async {
      final main = controller('laptop');
      final secondary = _Controller(store, _profile('phone'));
      final owner = store.appPreferences;
      secondary.dispose();

      await main.setAppearance(AppAppearance.dark);
      expect(owner.value.appearance, AppAppearance.dark);
      final later = controller('phone');
      expect(later.appearance.value, AppAppearance.dark);
      await later.setAppLocale(const Locale('ar'));
      expect(main.appLocale.value, const Locale('ar'));
    },
  );

  test(
    'fresh profiles are private and consent changes only the named profile',
    () async {
      final laptop = controller('laptop');
      final phone = controller('phone');
      final secondLaptop = controller('laptop');
      expect(laptop.shareSessionViews, isFalse);
      expect(phone.shareSessionViews, isFalse);

      await laptop.setShareSessionViews(true, expectedProfileId: 'laptop');
      expect(secondLaptop.shareSessionViews, isTrue);
      expect(phone.shareSessionViews, isFalse);
      expect(prefs.getBool('oc.shareSessionViews.laptop'), isTrue);
      await expectLater(
        laptop.setShareSessionViews(true, expectedProfileId: 'phone'),
        throwsA(isA<SettingsFailure>()),
      );
      expect(phone.shareSessionViews, isFalse);
    },
  );

  test(
    'revoking sharing in another controller prevents a waiting receipt',
    () async {
      final main = controller('laptop');
      final secondStore = ProfileStore(prefs: prefs);
      await secondStore.load();
      final secondary = controller('laptop', profiles: secondStore);
      final repository = reads(secondary);
      await main.setShareSessionViews(true, expectedProfileId: 'laptop');
      secondary.wakeEntered = Completer<void>();
      secondary.wakeRelease = Completer<void>();
      final view = secondary.viewSession('chat', isForeground: () => true);
      await secondary.wakeEntered!.future;
      final before = secondary.readPrivacyRevision;

      await main.setShareSessionViews(false, expectedProfileId: 'laptop');
      expect(secondary.readPrivacyRevision, greaterThan(before));
      expect(secondary.readPrivacyRevision, main.readPrivacyRevision);
      secondary.wakeRelease!.complete();
      await view;
      expect(repository.receipts, isEmpty);
      expect(
        secondary.isSessionUnread(secondary.sessionsById['chat']!),
        isFalse,
      );
    },
  );

  test(
    'a transmitted receipt cannot acknowledge state after shared revocation',
    () async {
      final main = controller('laptop');
      final secondary = controller('laptop');
      final repository = reads(secondary)
        ..entered = Completer<void>()
        ..release = Completer<void>();
      await main.setShareSessionViews(true, expectedProfileId: 'laptop');
      final view = secondary.viewSession('chat', isForeground: () => true);
      await repository.entered!.future;
      await main.setShareSessionViews(false, expectedProfileId: 'laptop');
      repository.release!.complete();
      await view;

      expect(repository.receipts, [('chat', 100)]);
      expect(secondary.sessionsById['chat']!.time!.viewed, 0);
      expect(secondary.shareSessionViews, isFalse);
    },
  );

  test(
    'delayed opt-in followed by opt-out never publishes sharing on',
    () async {
      final main = controller('laptop');
      final secondary = controller('laptop');
      final observed = <bool>[];
      secondary.addListener(() => observed.add(secondary.shareSessionViews));
      disk.hold('oc.shareSessionViews.laptop');
      final enabling = main
          .setShareSessionViews(true, expectedProfileId: 'laptop')
          .then<Object?>((_) => null, onError: (Object error) => error);
      await disk.entered!.future;
      expect(secondary.shareSessionViews, isFalse);
      final disabling = secondary.setShareSessionViews(
        false,
        expectedProfileId: 'laptop',
      );
      expect(main.shareSessionViews, isFalse);
      disk.release!.complete();
      final superseded = await enabling;
      await disabling;
      if (superseded != null) expect(superseded, isA<SettingsFailure>());

      expect(observed, isNot(contains(true)));
      expect(main.shareSessionViews, isFalse);
      expect(secondary.shareSessionViews, isFalse);
      expect(
        (await disk.getAll())['flutter.oc.shareSessionViews.laptop'],
        isFalse,
      );
    },
  );

  test(
    'failed opt-out stays private in every controller and advances shared epoch',
    () async {
      final main = controller('laptop');
      final secondary = controller('laptop');
      final repository = reads(secondary);
      await main.setShareSessionViews(true, expectedProfileId: 'laptop');
      final before = secondary.readPrivacyRevision;
      disk.refusedKey = 'flutter.oc.shareSessionViews.laptop';
      await expectLater(
        main.setShareSessionViews(false, expectedProfileId: 'laptop'),
        throwsA(isA<SettingsFailure>()),
      );

      expect(main.shareSessionViews, isFalse);
      expect(secondary.shareSessionViews, isFalse);
      expect(secondary.readPrivacyRevision, greaterThan(before));
      expect(secondary.readPrivacyRevision, main.readPrivacyRevision);
      await secondary.viewSession('chat', isForeground: () => true);
      expect(repository.receipts, isEmpty);
    },
  );

  for (final legacy in [true, false, null]) {
    test(
      'load migrates existing profiles once from legacy consent $legacy',
      () async {
        SharedPreferences.setMockInitialValues({
          'oc.profiles': jsonEncode([_profile('old').toJson()]),
          'oc.shareSessionViews': ?legacy,
        });
        final legacyPrefs = await SharedPreferences.getInstance();
        final migrated = ProfileStore(prefs: legacyPrefs);
        await migrated.load();
        final existing = controller('old', profiles: migrated);
        expect(existing.shareSessionViews, legacy ?? true);
        await migrated.upsert(_profile('new'));
        await migrated.load();
        final fresh = controller('new', profiles: migrated);
        expect(fresh.shareSessionViews, isFalse);
        await existing.setShareSessionViews(false, expectedProfileId: 'old');
        final reloaded = ProfileStore(prefs: legacyPrefs);
        await reloaded.load();
        expect(
          controller('old', profiles: reloaded).shareSessionViews,
          isFalse,
        );
        expect(
          controller('new', profiles: reloaded).shareSessionViews,
          isFalse,
        );
      },
    );
  }
}
