part of '../connection.dart';

// Appearance, language and reader preferences.

/// [ConnectionController]'s appearance and reader preferences.
mixin _ConnectionControllerSettings on ChangeNotifier {
  ConnectionController get _self;

  late final AppLocaleStore _localeStore;
  late final ValueNotifier<Locale?> appLocale;
  late final ValueNotifier<AppAppearance> appearance;
  late final ValueNotifier<ThemePackId> themePack;

  /// Settings › Appearance › Effects, provided to the app by
  /// `KitEffectsScope` in `main.dart`.
  late final ValueNotifier<KitEffects> effects;
  bool transcriptReasoningExpanded = false;
  bool transcriptTimestampsVisible = false;

  Future<void> setTranscriptReasoningExpanded(bool expanded) =>
      _self._setTranscriptReasoningExpanded(expanded);

  Future<void> setTranscriptTimestampsVisible(bool visible) =>
      _self._setTranscriptTimestampsVisible(visible);

  Future<void> setAppLocale(Locale? value) async {
    await _localeStore.save(value);
    if (_self._disposed) return;
    appLocale.value = value == null ? null : Locale(value.languageCode);
  }

  Future<void> setThemePack(ThemePackId value) async {
    await _self.store.setThemePack(value);
    themePack.value = value;
  }

  Future<void> setAppearance(AppAppearance value) async {
    await _self.store.setAppearance(value);
    if (_self._disposed) return;
    appearance.value = value;
  }

  /// Shows the new choice at once and saves it; a refused save puts the
  /// saved choice back and rethrows.
  Future<void> setEffects(KitEffects value) => _self._setEffects(value);
}

extension _ConnectionControllerSettingsImpl on ConnectionController {
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
    final before = effects.value;
    effects.value = value;
    try {
      await store.setEffects(value);
    } catch (_) {
      if (!_disposed) effects.value = before;
      rethrow;
    }
  }
}
