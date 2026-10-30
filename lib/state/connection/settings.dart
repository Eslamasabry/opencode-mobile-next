part of '../connection.dart';

// Appearance, language and reader preferences.

/// [ConnectionController]'s appearance and reader preferences.
mixin _ConnectionControllerSettings on ChangeNotifier {
  ConnectionController get _self;

  late final ValueNotifier<Locale?> appLocale;
  late final ValueNotifier<AppAppearance> appearance;
  late final ValueNotifier<ThemePackId> themePack;

  /// Settings › Appearance › Accent: a swatch id, or null to match the
  /// theme's own partner colour.
  late final ValueNotifier<String?> appearanceAccent;

  /// Settings › Appearance › Effects, provided to the app by
  /// `KitEffectsScope` in `main.dart`.
  late final ValueNotifier<KitEffects> effects;

  /// Settings › Appearance › Text size: the app's own multiplier.
  late final ValueNotifier<KitTextSize> textSize;
  bool transcriptReasoningExpanded = false;
  bool transcriptTimestampsVisible = false;

  Future<void> setTranscriptReasoningExpanded(bool expanded) =>
      _self._setTranscriptReasoningExpanded(expanded);

  Future<void> setTranscriptTimestampsVisible(bool visible) =>
      _self._setTranscriptTimestampsVisible(visible);

  Future<void> setAppLocale(Locale? value) async {
    try {
      await _self.store.appPreferences.setLocale(value);
    } on SettingsFailure catch (error) {
      if (error.reason == SettingsFailureReason.storageFailed) {
        throw const AppLocaleSaveException();
      }
      if (error.reason == SettingsFailureReason.invalidValue) {
        throw ArgumentError.value(value, 'locale', 'Unsupported language');
      }
      rethrow;
    }
    if (_self._disposed) return;
    await _self.backgroundLive.refreshNativeLocale();
  }

  Future<void> setThemePack(ThemePackId value) async {
    await _self.store.setThemePack(value);
    if (!_self._disposed) {
      themePack.value = value;
      _self._notifyListeners();
    }
  }

  Future<void> setAppearance(AppAppearance value) async {
    await _self.store.setAppearance(value);
    if (_self._disposed) return;
    appearance.value = value;
    _self._notifyListeners();
  }

  Future<void> setTextSize(KitTextSize value) async {
    await _self.store.setTextSize(value);
  }

  /// Saves the Accent swatch (null: match the theme); every controller
  /// follows once storage acknowledges it.
  Future<void> setAppearanceAccent(String? value) async {
    await _self.store.setAppearanceAccent(value);
    if (_self._disposed) return;
    appearanceAccent.value = value;
  }

  /// Publishes the choice to every controller after storage acknowledges it.
  Future<void> setEffects(KitEffects value) => _self._setEffects(value);
}

extension _ConnectionControllerSettingsImpl on ConnectionController {
  void _appPreferencesChanged() {
    if (_disposed) return;
    final value = store.appPreferences.value;
    appLocale.value = value.locale;
    appearance.value = value.appearance;
    themePack.value = value.themePack;
    appearanceAccent.value = value.accent;
    effects.value = value.effects;
    textSize.value = value.textSize;
    transcriptReasoningExpanded = value.reasoningExpanded;
    transcriptTimestampsVisible = value.timestampsVisible;
    _notifyListeners();
  }

  /// The body of [setTranscriptReasoningExpanded].
  Future<void> _setTranscriptReasoningExpanded(bool expanded) async {
    await store.setTranscriptReasoningExpanded(expanded);
    if (_disposed) return;
    transcriptReasoningExpanded = expanded;
    _notifyListeners();
  }

  /// The body of [setTranscriptTimestampsVisible].
  Future<void> _setTranscriptTimestampsVisible(bool visible) async {
    await store.setTranscriptTimestampsVisible(visible);
    if (_disposed) return;
    transcriptTimestampsVisible = visible;
    _notifyListeners();
  }

  /// The body of [setEffects].
  Future<void> _setEffects(KitEffects value) async {
    await store.setEffects(value);
  }
}
