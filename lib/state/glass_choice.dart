import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// How much glass the chat's floating composer and its header show when
/// the conversation scrolls under them (Settings › Appearance › Glass). A
/// device preference: every connection shares it, and it is not swept with
/// a profile. Balanced is the default; Off keeps both solid.
///
/// The design kit reads it through `KitFrost` (lib/ui/kit/kit_frost.dart),
/// which also turns the glass off under Saver, reduced motion, high
/// contrast and a style that keeps its own floating look.
enum KitGlassChoice { off, weak, balanced, strong }

/// Where [KitGlassChoice] is kept: one SharedPreferences key, read once on
/// first use and written on every change.
abstract final class GlassChoiceStore {
  /// A device key with no profile id in it, so the profile deletion sweep
  /// (`ProfileStore.profileScopedPreferenceKeys`) leaves it alone.
  static const prefsKey = 'oc.glass';

  /// What the person chose; [KitGlassChoice.balanced] until it is read.
  static final ValueNotifier<KitGlassChoice> choice = ValueNotifier(
    KitGlassChoice.balanced,
  );

  static Future<void>? _loading;

  /// Reads the stored choice once (later calls share the first read). A
  /// store that cannot be opened (a test without mocked preferences) leaves
  /// the default in place.
  static Future<void> ensureLoaded() => _loading ??= _load();

  static Future<void> _load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final stored = parse(prefs.getString(prefsKey));
      if (stored != null) choice.value = stored;
    } catch (_) {
      // No store: the default stands.
    }
  }

  /// [name] as a choice; null for a missing or unknown name.
  static KitGlassChoice? parse(String? name) {
    for (final value in KitGlassChoice.values) {
      if (value.name == name) return value;
    }
    return null;
  }

  /// Shows [next] at once and stores it; false when the store refused.
  static Future<bool> save(KitGlassChoice next) async {
    choice.value = next;
    try {
      final prefs = await SharedPreferences.getInstance();
      return await prefs.setString(prefsKey, next.name);
    } catch (_) {
      return false;
    }
  }

  /// Back to the default and forgets the read (tests).
  @visibleForTesting
  static void debugReset() {
    choice.value = KitGlassChoice.balanced;
    _loading = null;
  }
}
