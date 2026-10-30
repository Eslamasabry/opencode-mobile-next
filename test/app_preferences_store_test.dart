import 'dart:async';
import 'dart:ui' show Locale;

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/settings_scope.dart';
import 'package:opencode_mobile/state/app_preferences.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/read_state_consent.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import 'package:shared_preferences_platform_interface/types.dart';

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();

  Completer<void>? gate;
  bool Function(String key, Object? value)? refuse;
  bool refuseReads = false;
  final calls = <(String, Object?)>[];

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    calls.add((key, value));
    await gate?.future;
    return refuse?.call(key, value) == true
        ? false
        : super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async {
    calls.add((key, null));
    return refuse?.call(key, null) == true ? false : super.remove(key);
  }

  @override
  Future<Map<String, Object>> getAll() async {
    if (refuseReads) throw StateError('storage unavailable');
    return super.getAll();
  }

  @override
  Future<Map<String, Object>> getAllWithParameters(
    GetAllParameters parameters,
  ) {
    if (refuseReads) throw StateError('storage unavailable');
    return super.getAllWithParameters(parameters);
  }
}

Future<(SharedPreferences, _Disk, AppPreferencesStore)> _owner([
  Map<String, Object> initial = const {},
]) async {
  SharedPreferences.setMockInitialValues(initial);
  final prefs = await SharedPreferences.getInstance();
  final disk = _Disk(await SharedPreferencesStorePlatform.instance.getAll());
  SharedPreferencesStorePlatform.instance = disk;
  return (prefs, disk, AppPreferencesStore.forPreferences(prefs));
}

final _storageFailure = throwsA(
  isA<SettingsFailure>().having(
    (failure) => failure.reason,
    'reason',
    SettingsFailureReason.storageFailed,
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'consent revocation immediately reaches device preference observers',
    () async {
      final (_, disk, owner) = await _owner({
        'oc.shareSessionViews.server': true,
      });
      expect(owner.readStateConsent.enabled('server'), isTrue);
      final seen = <bool>[];
      owner.addListener(
        () => seen.add(owner.readStateConsent.enabled('server')),
      );
      final gate = disk.gate = Completer<void>();
      final previousEpoch = owner.privacyEpoch;
      final save = owner.readStateConsent.setEnabled('server', false);
      expect(owner.privacyEpoch, greaterThan(previousEpoch));
      expect(owner.revision, greaterThan(0));
      expect(seen, isNotEmpty);
      expect(seen.every((enabled) => !enabled), isTrue);
      gate.complete();
      await save;
      expect(owner.readStateConsent.enabled('server'), isFalse);
    },
  );

  test(
    'one preferences instance shares the owner and consent authority',
    () async {
      final (prefs, _, owner) = await _owner();
      expect(AppPreferencesStore.forPreferences(prefs), same(owner));
      expect(
        owner.readStateConsent,
        same(ReadStateConsentStore.forPreferences(prefs)),
      );
      expect(owner.privacyEpoch, owner.readStateConsent.epoch);
      expect(owner.value.appearance, AppAppearance.dark);
      expect(owner.value.themePack, ThemePackId.opencode);
      expect(owner.value.locale, isNull);
      expect(owner.value.effects, KitEffects.defaults);
      expect(owner.value.reasoningExpanded, isFalse);
      expect(owner.value.timestampsVisible, isFalse);
      final (_, _, separate) = await _owner();
      expect(separate, isNot(same(owner)));
    },
  );

  test(
    'legacy values preserve defaults and fold separate celebrations',
    () async {
      final (_, disk, owner) = await _owner({
        'oc.appearance': 'light',
        'oc.themePack': 'unknown',
        'oc.appLocale': 'ar',
        'oc.effectsMotion': 'full',
        'oc.effectsCelebrations': false,
        'oc.effectsActivityGlow': false,
        'oc.effectsGlowStyle': 'unknown',
        'oc.effectsGlowSpeed': 42,
        'oc.transcript.reasoningExpanded': true,
        'oc.transcript.timestampsVisible': 'invalid',
      });
      expect(owner.value.appearance, AppAppearance.light);
      expect(owner.value.themePack, ThemePackId.opencode);
      expect(owner.value.locale, const Locale('ar'));
      expect(owner.value.effects.motion, KitMotionLevel.calm);
      expect(owner.value.effects.celebrations, isFalse);
      expect(owner.value.effects.activityGlow, isFalse);
      expect(owner.value.effects.glowStyle, KitGlowStyle.classic);
      expect(owner.value.effects.glowSpeed, KitGlowSpeed.normal);
      expect(owner.value.reasoningExpanded, isTrue);
      expect(owner.value.timestampsVisible, isFalse);
      expect(disk.calls, isEmpty);
    },
  );

  test('writes serialize and listeners see only acknowledged values', () async {
    final (_, disk, owner) = await _owner();
    final gate = disk.gate = Completer<void>();
    final seen = <AppAppearance>[];
    owner.addListener(() => seen.add(owner.value.appearance));
    final first = owner.setAppearance(AppAppearance.light);
    final second = owner.setAppearance(AppAppearance.system);
    await Future<void>.delayed(Duration.zero);
    expect(disk.calls, [('flutter.oc.appearance', 'light')]);
    expect(seen, isEmpty);
    expect(owner.value.appearance, AppAppearance.dark);
    gate.complete();
    await Future.wait([first, second]);
    expect(seen, [AppAppearance.light, AppAppearance.system]);
    expect(owner.revision, 2);
    expect((await disk.getAll())['flutter.oc.appearance'], 'system');
  });

  test('the live background held off by a style persists', () async {
    final (prefs, _, owner) = await _owner();
    final paper = KitEffects.defaults.copyWith(
      surfaceStyle: KitSurfaceStyle.paper,
      liveBackground: false,
      liveHeldByStyle: true,
    );
    await owner.setEffects(paper);
    expect(prefs.getBool('oc.effectsLiveHeldByStyle'), isTrue);
    await owner.setEffects(paper.copyWith(liveBackground: true));
    expect(prefs.containsKey('oc.effectsLiveHeldByStyle'), isFalse);
    final (_, _, reread) = await _owner({
      'oc.effectsBackgroundOn': false,
      'oc.effectsLiveHeldByStyle': true,
    });
    expect(reread.value.effects.liveHeldByStyle, isTrue);
  });

  test('all device choices persist under existing keys', () async {
    final (prefs, disk, owner) = await _owner();
    await owner.setThemePack(ThemePackId.gruvbox);
    await owner.setLocale(const Locale('ar', 'EG'));
    await owner.setReasoningExpanded(true);
    await owner.setTimestampsVisible(true);
    expect(owner.value.locale, const Locale('ar'));
    expect(prefs.getString('oc.themePack'), 'gruvbox');
    expect(prefs.getBool('oc.transcript.reasoningExpanded'), isTrue);
    expect(prefs.getBool('oc.transcript.timestampsVisible'), isTrue);
    expect((await disk.getAll())['flutter.oc.appLocale'], 'ar');
    await owner.setLocale(null);
    expect(owner.value.locale, isNull);
    expect((await disk.getAll()).containsKey('flutter.oc.appLocale'), isFalse);
    final revision = owner.revision;
    await expectLater(
      owner.setLocale(const Locale('de')),
      throwsA(
        isA<SettingsFailure>().having(
          (failure) => failure.reason,
          'reason',
          SettingsFailureReason.invalidValue,
        ),
      ),
    );
    expect(owner.revision, revision);
  });

  test(
    'refused write never publishes optimistic cache and next write works',
    () async {
      final (prefs, disk, owner) = await _owner({'oc.appearance': 'dark'});
      disk.refuse = (_, _) => true;
      await expectLater(
        owner.setAppearance(AppAppearance.light),
        _storageFailure,
      );
      expect(owner.value.appearance, AppAppearance.dark);
      expect(prefs.getString('oc.appearance'), 'dark');
      expect(owner.revision, 0);
      disk.refuse = null;
      await owner.setAppearance(AppAppearance.system);
      expect(owner.value.appearance, AppAppearance.system);
    },
  );

  test(
    'effects rollback restores earlier keys after a later refusal',
    () async {
      final (prefs, disk, owner) = await _owner({'oc.effectsMotion': 'full'});
      disk.refuse = (key, value) => key == 'flutter.oc.effectsActivityGlow';
      await expectLater(
        owner.setEffects(
          const KitEffects(motion: KitMotionLevel.off, activityGlow: false),
        ),
        _storageFailure,
      );
      expect(owner.value.effects, KitEffects.defaults);
      expect(prefs.getString('oc.effectsMotion'), 'full');
      expect(owner.revision, 0);
    },
  );

  test(
    'failed rollback reports failure and publishes reloaded partial truth',
    () async {
      final (_, disk, owner) = await _owner({'oc.effectsMotion': 'full'});
      disk.refuse = (key, value) =>
          key == 'flutter.oc.effectsActivityGlow' ||
          (key == 'flutter.oc.effectsMotion' && value == 'full');
      await expectLater(
        owner.setEffects(
          const KitEffects(motion: KitMotionLevel.off, activityGlow: false),
        ),
        _storageFailure,
      );
      expect(owner.value.effects.motion, KitMotionLevel.off);
      expect(owner.value.effects.activityGlow, isTrue);
      expect(owner.revision, 1);
      expect((await disk.getAll())['flutter.oc.effectsMotion'], 'off');
    },
  );

  test(
    'failed reload cannot leak optimistic values into a later save',
    () async {
      final (_, disk, owner) = await _owner({'oc.appearance': 'dark'});
      disk.refuse = (_, _) => true;
      disk.refuseReads = true;
      await expectLater(
        owner.setAppearance(AppAppearance.light),
        _storageFailure,
      );
      expect(owner.value.appearance, AppAppearance.dark);
      disk.refuse = null;
      final calls = disk.calls.length;
      await expectLater(owner.setReasoningExpanded(true), _storageFailure);
      expect(disk.calls.length, calls);
      expect(owner.value.reasoningExpanded, isFalse);
      disk.refuseReads = false;
      await owner.setReasoningExpanded(true);
      expect(owner.value.appearance, AppAppearance.dark);
      expect(owner.value.reasoningExpanded, isTrue);
    },
  );

  test(
    'Full removes legacy celebrations and preserves other effect choices',
    () async {
      final (prefs, _, owner) = await _owner({
        'oc.effectsMotion': 'full',
        'oc.effectsCelebrations': false,
        'oc.effectsGlowStyle': 'softRing',
        'oc.effectsCorners': 'sharp',
      });
      await owner.setEffects(
        owner.value.effects.copyWith(motion: KitMotionLevel.full),
      );
      expect(prefs.containsKey('oc.effectsCelebrations'), isFalse);
      expect(owner.value.effects.celebrations, isTrue);
      expect(owner.value.effects.glowStyle, KitGlowStyle.softRing);
      expect(owner.value.effects.fineTune.corners, KitCorners.sharp);
      expect(owner.value.effects.haptics, isTrue);
    },
  );

  test('the reply glow is one choice and each style keeps its depth and '
      'edges, whatever an earlier version stored', () async {
    final (_, _, owner) = await _owner({
      'oc.effectsGlowColours': 'two',
      'oc.effectsGlowSpeed': 'slow',
      'oc.effectsDepth': 'raised',
      'oc.effectsEdges': 'none',
      'oc.effectsDensity': 'compact',
    });
    final effects = owner.value.effects;
    expect(effects.glowColours, KitGlowColours.one);
    expect(effects.glowSpeed, KitGlowSpeed.normal);
    expect(effects.fineTune.depth, isNull);
    expect(effects.fineTune.edges, isNull);
    // Spacing (density) is still the person's.
    expect(effects.fineTune.density, KitDensity.compact);
  });
}
