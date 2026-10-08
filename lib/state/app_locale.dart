import 'dart:ui' show Locale;

import 'package:flutter/widgets.dart' show basicLocaleListResolution;
import 'package:shared_preferences/shared_preferences.dart';

/// Device-wide preference, deliberately outside profile deletion. A null
/// override follows Flutter's platform locale resolution (English fallback).
class AppLocaleStore {
  AppLocaleStore(this._preferences);

  static const preferenceKey = 'oc.appLocale';

  /// Every language the app ships, by the code the choice is saved under.
  /// Brazilian Portuguese is `pt` and Simplified Chinese is `zh`: one ARB
  /// per language, matched by language code only (docs/l10n/fg5-key-set.md).
  static const supportedLanguages = {'en', 'ar', 'es', 'ja', 'pt', 'ru', 'zh'};

  /// The order of the language choices (after "Use system language"):
  /// English first, then by code. Each shows its own name.
  static const pickerLanguages = ['en', 'ar', 'es', 'ja', 'pt', 'ru', 'zh'];

  final SharedPreferences _preferences;
  Future<void>? _pending;

  Locale? get value {
    final language = _preferences.getString(preferenceKey);
    return supportedLanguages.contains(language) ? Locale(language!) : null;
  }

  /// Serial writes avoid an older slow save replacing a newer choice. A
  /// refused platform write also refreshes SharedPreferences' optimistic cache.
  Future<void> save(Locale? locale) {
    if (locale != null && !supportedLanguages.contains(locale.languageCode)) {
      return Future<void>.error(ArgumentError.value(locale, 'locale'));
    }
    Future<void> write() async {
      try {
        final saved = locale == null
            ? await _preferences.remove(preferenceKey)
            : await _preferences.setString(preferenceKey, locale.languageCode);
        if (!saved) throw const AppLocaleSaveException();
      } catch (_) {
        try {
          await _preferences.reload();
        } catch (_) {
          // The controller retains the last acknowledged locale either way.
        }
        throw const AppLocaleSaveException();
      }
    }

    final result = _pending == null ? write() : _pending!.then((_) => write());
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }
}

class AppLocaleSaveException implements Exception {
  const AppLocaleSaveException();
}

/// Picks the app's language from the device's language list. Flutter's own
/// rule falls back to the *first* supported locale, which is Arabic (the
/// generated list is alphabetical); a device in a language the app does not
/// ship must read English instead, so English leads the list it searches.
Locale resolveAppLocales(List<Locale>? preferred, Iterable<Locale> supported) =>
    basicLocaleListResolution(preferred, [
      const Locale('en'),
      ...supported.where((locale) => locale.languageCode != 'en'),
    ]);
