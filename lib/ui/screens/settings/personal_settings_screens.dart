part of '../settings_screen.dart';

/// The parts of Appearance a search result can mean.
enum AppearanceSection { mode, language, effects, theme }

/// Appearance category (`appearance-settings`): light or dark in one tap,
/// the language, the effects the person controls (glass, animations,
/// celebrations, vibration; design standard §10) and the theme packs as
/// swatches. Kit only (screen-settings-1): KitRowGroup panels of KitRow,
/// KitSegmented, KitPickerRow and KitSwitchRow, and a KitSwatchGrid.
class AppearanceSettingsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// The part a search result means: the screen opens scrolled to it.
  final AppearanceSection? initialSection;

  const AppearanceSettingsScreen({
    super.key,
    required this.controller,
    this.initialSection,
  });

  @override
  State<AppearanceSettingsScreen> createState() =>
      _AppearanceSettingsScreenState();
}

class _AppearanceSettingsScreenState extends State<AppearanceSettingsScreen> {
  final _sectionKeys = {
    for (final section in AppearanceSection.values)
      section: GlobalKey(debugLabel: 'appearance-${section.name}'),
  };

  /// A display choice (light or dark, language) the device refused to save.
  bool _displayFailed = false;

  @override
  void initState() {
    super.initState();
    if (widget.initialSection case final section?) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final target = _sectionKeys[section]?.currentContext;
        if (mounted && target != null) Scrollable.ensureVisible(target);
      });
    }
  }

  Future<void> _saveDisplay(Future<void> Function() write) async {
    setState(() => _displayFailed = false);
    try {
      await write();
    } catch (_) {
      if (mounted) setState(() => _displayFailed = true);
    }
  }

  static String _localeLabel(AppLocalizations l10n, String code) =>
      switch (code) {
        'ar' => l10n.e7LocaleUiArabic,
        'en' => l10n.e7LocaleUiEnglish,
        _ => l10n.e7LocaleUiSystem,
      };

  Widget _display(BuildContext context) {
    final controller = widget.controller;
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: Listenable.merge([
        controller.appearance,
        controller.appLocale,
      ]),
      builder: (context, _) {
        final appearance = controller.appearance.value;
        final locale = controller.appLocale.value?.languageCode ?? '';
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_displayFailed)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.gutter,
                  end: tokens.gutter,
                  bottom: tokens.space3,
                ),
                child: KitNotice(
                  key: const ValueKey('appearance-display-failed'),
                  tone: AppStatusTone.failure,
                  message: copy.effectsSaveFailed,
                ),
              ),
            KitRowGroup(
              label: copy.appearanceDisplaySection,
              children: [
                // Light or dark in one tap (map actionsMissing).
                KeyedSubtree(
                  key: _sectionKeys[AppearanceSection.mode],
                  child: KitRow(
                    key: const ValueKey('appearance-settings-entry'),
                    leading: KitRow.icon(context, AppIconography.contrast),
                    title: copy.e7SettingsUi69,
                    below: Padding(
                      padding: EdgeInsetsDirectional.only(
                        top: tokens.space2,
                        bottom: tokens.space1,
                      ),
                      child: KitSegmented<AppAppearance>(
                        semanticsLabel: copy.e7SettingsUi69,
                        selected: appearance,
                        segments: [
                          for (final value in AppAppearance.values)
                            KitSegment(
                              key: ValueKey('appearance-mode-${value.name}'),
                              value: value,
                              label: switch (value) {
                                AppAppearance.system =>
                                  copy.appearanceModeSystem,
                                _ => appearanceLabel(value, context),
                              },
                            ),
                        ],
                        onChanged: (value) =>
                            _saveDisplay(() => controller.setAppearance(value)),
                      ),
                    ),
                  ),
                ),
                KeyedSubtree(
                  key: _sectionKeys[AppearanceSection.language],
                  child: KitPickerRow<String>(
                    rowKey: const ValueKey('appearance-language'),
                    leading: KitRow.icon(context, AppIconography.globe),
                    title: copy.e7LocaleUiLanguage,
                    valueLabel: _localeLabel(copy, locale),
                    choices: [
                      for (final code in const ['', 'en', 'ar'])
                        KitChoice(
                          value: code,
                          title: _localeLabel(copy, code),
                          key: ValueKey('appearance-language-$code'),
                        ),
                    ],
                    selected: locale,
                    onSelected: (code) => _saveDisplay(
                      () => controller.setAppLocale(
                        code.isEmpty ? null : Locale(code),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  Widget _themes(BuildContext context) {
    final controller = widget.controller;
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    final brightness = Theme.of(context).brightness;
    final current = ThemeRoles.of(context);
    return ListenableBuilder(
      listenable: Listenable.merge([
        controller.themePack,
        harvestedDynamicPack,
      ]),
      builder: (context, _) {
        final selected = controller.themePack.value;
        // Thirty themes as list rows is a very long page. They are a grid of
        // swatches instead: each is a miniature of the theme, so choosing is
        // looking, not reading.
        return Padding(
          padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.space1,
                  end: tokens.space1,
                  bottom: tokens.labelGap,
                ),
                child: Semantics(
                  header: true,
                  child: KitText(copy.e7SettingsUi70, role: KitTextRole.label),
                ),
              ),
              KitSwatchGrid(
                label: copy.e7SettingsUi70,
                children: [
                  for (final id in ThemePackId.values)
                    _swatch(context, id, selected, brightness, current),
                ],
              ),
            ],
          ),
        );
      },
    );
  }

  KitSwatch _swatch(
    BuildContext context,
    ThemePackId id,
    ThemePackId selected,
    Brightness brightness,
    ThemeRoles current,
  ) {
    final pack = id == ThemePackId.dynamic
        ? harvestedDynamicPack.value
        : themePack(id);
    final available = pack != null;
    return KitSwatch(
      swatchKey: ValueKey('theme-pack-${id.name}'),
      // An unharvested Material You pack has no colours of its own yet: it
      // shows the current theme, disabled with the reason in words.
      roles: pack?.palette(brightness).themeRoles ?? current,
      label: themePackLabels[id]!,
      selected: selected == id,
      disabledReason: available
          ? null
          : _settingsCopy(context).e7AppearanceDynamicUnavailable,
      onPressed: available
          ? () => showThemePackPreview(
              context,
              controller: widget.controller,
              pack: id,
            )
          : null,
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final tokens = KitTokens.of(context);
    return KitScreen(
      topBar: KitTopBar(title: _settingsCopy(context).e7AppearanceTitle),
      width: KitScreenWidth.reading,
      // Not a lazy list: a dozen rows in one Column, and a search result
      // that means one part must find it laid out.
      body: ListView(
        key: const ValueKey('appearance-settings'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _display(context),
              SizedBox(height: tokens.sectionGap),
              KeyedSubtree(
                key: _sectionKeys[AppearanceSection.effects],
                child: _EffectsSection(controller: controller),
              ),
              SizedBox(height: tokens.sectionGap),
              KeyedSubtree(
                key: _sectionKeys[AppearanceSection.theme],
                child: _themes(context),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Settings › Appearance › Motion (design standard §10): how much the app
/// moves and whether finished moments celebrate, as one choice. Glass and
/// vibration are fixed parts of the design. A choice shows at once and
/// is saved; a refused save puts it back and says so. The system's
/// accessibility settings always win, and the rows say when they do.
class _EffectsSection extends StatefulWidget {
  const _EffectsSection({required this.controller});

  final ConnectionController controller;

  @override
  State<_EffectsSection> createState() => _EffectsSectionState();
}

class _EffectsSectionState extends State<_EffectsSection> {
  bool _failed = false;

  Future<void> _choose(KitEffects next) async {
    setState(() => _failed = false);
    try {
      await widget.controller.setEffects(next);
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    return ValueListenableBuilder<KitEffects>(
      valueListenable: widget.controller.effects,
      builder: (context, effects, _) {
        // The page shows the choices in force as the person makes them,
        // even where it sits above the app's own scope (tests, previews).
        return KitEffectsScope(
          effects: effects,
          child: Builder(
            builder: (context) {
              final tokens = KitTokens.of(context);
              // Reduced with Animations not Off: the system setting wins.
              final systemStill =
                  KitMotion.reduced(context) &&
                  effects.motion != KitMotionLevel.off;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  KitReveal(
                    child: _failed
                        ? Padding(
                            padding: EdgeInsetsDirectional.only(
                              start: tokens.gutter,
                              end: tokens.gutter,
                              bottom: tokens.space3,
                            ),
                            child: KitNotice(
                              tone: AppStatusTone.failure,
                              message: copy.effectsSaveFailed,
                            ),
                          )
                        : null,
                  ),
                  ValueListenableBuilder<bool>(
                    valueListenable: KitGlassSafety.turnedOffAfterCrashes,
                    builder: (context, off, _) => KitReveal(
                      child: off
                          ? Padding(
                              padding: EdgeInsetsDirectional.only(
                                start: tokens.gutter,
                                end: tokens.gutter,
                                bottom: tokens.space3,
                              ),
                              child: KitNotice(
                                key: const ValueKey('effects-glass-crash'),
                                message: copy.effectsGlassCrashOff,
                                actions: [
                                  KitAction(
                                    key: const ValueKey(
                                      'effects-glass-crash-on',
                                    ),
                                    label: copy.effectsGlassCrashOn,
                                    onPressed: KitGlassShader.turnLiquidBackOn,
                                  ),
                                ],
                              ),
                            )
                          : null,
                    ),
                  ),
                  KitRowGroup(
                    label: copy.effectsSection,
                    children: [
                      _EffectsPreview(effects: effects),
                      KitArrival(
                        id: 'effects-motion',
                        child: KitRow(
                          key: const ValueKey('effects-motion'),
                          leading: KitRow.icon(
                            context,
                            AppIconography.playCircle,
                          ),
                          title: copy.effectsAnimations,
                          supporting: TextSpan(
                            text: switch (effects.motion) {
                              KitMotionLevel.full => copy.effectsMotionFullHint,
                              KitMotionLevel.calm => copy.effectsMotionCalmHint,
                              KitMotionLevel.off => copy.effectsMotionOffHint,
                            },
                          ),
                          supportingMaxLines: 2,
                          below: Padding(
                            padding: EdgeInsetsDirectional.only(
                              top: tokens.space2,
                              bottom: tokens.space1,
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                KitSegmented<KitMotionLevel>(
                                  semanticsLabel: copy.effectsAnimations,
                                  selected: effects.motion,
                                  segments: [
                                    for (final (level, label) in [
                                      (
                                        KitMotionLevel.full,
                                        copy.effectsMotionFull,
                                      ),
                                      (
                                        KitMotionLevel.calm,
                                        copy.effectsMotionCalm,
                                      ),
                                      (
                                        KitMotionLevel.off,
                                        copy.effectsMotionOff,
                                      ),
                                    ])
                                      KitSegment(
                                        key: ValueKey(
                                          'effects-motion-${level.name}',
                                        ),
                                        value: level,
                                        label: label,
                                      ),
                                  ],
                                  onChanged: (level) => _choose(
                                    effects.copyWith(
                                      motion: level,
                                      celebrations:
                                          level == KitMotionLevel.full,
                                    ),
                                  ),
                                ),
                                if (systemStill)
                                  Padding(
                                    padding: EdgeInsetsDirectional.only(
                                      top: tokens.space2,
                                    ),
                                    child: KitText(
                                      copy.effectsMotionSystemOff,
                                      role: KitTextRole.secondary,
                                    ),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      KitArrival(
                        id: 'effects-glow',
                        child: KitSwitchRow(
                          key: const ValueKey('effects-glow'),
                          switchKey: const ValueKey('effects-glow-switch'),
                          leading: KitRow.icon(context, AppIconography.sparkle),
                          title: copy.effectsActivityGlow,
                          supporting: copy.effectsActivityGlowHint,
                          value: effects.activityGlow,
                          onChanged: (on) =>
                              _choose(effects.copyWith(activityGlow: on)),
                        ),
                      ),
                    ],
                  ),
                ],
              );
            },
          ),
        );
      },
    );
  }
}

/// A small live sample of the effects: the brand's portal drawing itself in
/// (again on each Animations change; finished at once under Off) beside a
/// miniature of the app's floating tab bar, the one place glass lives
/// (LOOK-27), built by the kit. Decorative: the rows say everything in
/// words. No loop, so the page rests.
class _EffectsPreview extends StatelessWidget {
  const _EffectsPreview({required this.effects});

  final KitEffects effects;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final copy = _settingsCopy(context);
    return ExcludeSemantics(
      child: IgnorePointer(
        child: Padding(
          padding: EdgeInsets.all(tokens.panelInset),
          child: Row(
            children: [
              KitIllustration(
                key: ValueKey('effects-preview-${effects.motion.name}'),
                scene: const KitPortalScene(),
                width: KitTokens.illustrationInline,
              ),
              SizedBox(width: tokens.space3),
              Expanded(
                child: KeyedSubtree(
                  key: const ValueKey('effects-preview-glass'),
                  child: KitNavBar(
                    destinations: [
                      KitNavDestination(
                        label: copy.effectsPreviewWork,
                        icon: AppIconography.workspace,
                        selectedIcon: AppIconography.workspaceSelected,
                      ),
                      KitNavDestination(
                        label: copy.effectsPreviewSettings,
                        icon: AppIconography.settings,
                      ),
                    ],
                    selected: 0,
                    onSelected: (_) {},
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Privacy and local data (`privacy-settings`): read-state sync, the unsent
/// work this device holds on the person's behalf with its size and a way
/// to delete it, and the privacy policy. Durable grants ("Always allowed
/// actions") live under Conversation defaults in the hub: they are about
/// how the agent works, not about privacy.
class PrivacySettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const PrivacySettingsScreen({super.key, required this.controller});

  @override
  State<PrivacySettingsScreen> createState() => _PrivacySettingsScreenState();
}

class _PrivacySettingsScreenState extends State<PrivacySettingsScreen> {
  bool _busy = false;
  bool _readPreferenceFailed = false;

  /// What the last delete did, said on the page (KIT-34: no snackbar for a
  /// delete that cannot be undone).
  ({String message, bool ok})? _result;

  ConnectionController get _controller => widget.controller;

  /// Rounded the way a phone's storage screens round: one decimal past a
  /// kilobyte, and never "0 B" for something that exists.
  static String formatBytes(int bytes) {
    if (bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  /// Asks, then clears; true when the person confirmed and it cleared.
  Future<bool> _confirmAndClear({
    required String title,
    required String body,
    required String confirmLabel,
    required String confirmKey,
    required Future<bool> Function() clear,
    required String cleared,
    required String failed,
  }) async {
    final ok = await showKitConfirm(
      context,
      title: title,
      body: body,
      confirmLabel: confirmLabel,
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: ValueKey(confirmKey),
    );
    if (!ok || !mounted) return false;
    setState(() {
      _busy = true;
      _result = null;
    });
    final succeeded = await clear();
    if (!mounted) return succeeded;
    setState(() {
      _busy = false;
      _result = (message: succeeded ? cleared : failed, ok: succeeded);
    });
    return succeeded;
  }

  Widget _readState(BuildContext context) => ListenableBuilder(
    listenable: _controller,
    builder: (context, _) {
      if (!_controller.supportsSessionReadState) {
        return const SizedBox.shrink();
      }
      final l10n =
          Localizations.of<AppLocalizations>(context, AppLocalizations) ??
          lookupAppLocalizations(const Locale('en'));
      final tokens = KitTokens.of(context);
      final saving = _controller.savingReadPrivacy;
      return Padding(
        padding: EdgeInsetsDirectional.only(bottom: tokens.sectionGap),
        child: KitRowGroup(
          label: l10n.privacySharedSection,
          children: [
            KitSwitchRow(
              key: const ValueKey('share-session-views'),
              leading: KitRow.icon(context, AppIconography.visible),
              title: l10n.shareSessionViewsTitle,
              supporting: _controller.shareSessionViews
                  ? l10n.shareSessionViewsOn
                  : l10n.shareSessionViewsOff,
              value: _controller.shareSessionViews,
              disabledReason: saving ? l10n.privacySaving : null,
              onChanged: saving
                  ? null
                  : (value) async {
                      setState(() => _readPreferenceFailed = false);
                      try {
                        await _controller.setShareSessionViews(value);
                      } catch (_) {
                        if (mounted) {
                          setState(() => _readPreferenceFailed = true);
                        }
                      }
                    },
              below: _readPreferenceFailed
                  ? KitNotice(
                      tone: AppStatusTone.failure,
                      message: l10n.shareSessionViewsSaveError,
                    )
                  : null,
            ),
          ],
        ),
      );
    },
  );

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    final scope = SearchScope.of(context, _controller);
    final policy = searchIndex(
      copy,
      scope,
    ).where((entry) => entry.id == 'settings-privacy-data-use').firstOrNull;
    final result = _result;
    return KitScreen(
      topBar: KitTopBar(title: copy.settingsHubPrivacyRow),
      width: KitScreenWidth.reading,
      loading: _busy,
      loadingLabel: copy.privacyDeleting,
      body: ListView(
        key: const ValueKey('privacy-settings'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          if (result != null)
            Padding(
              padding: EdgeInsetsDirectional.only(
                start: tokens.gutter,
                end: tokens.gutter,
                bottom: tokens.sectionGap,
              ),
              child: KitNotice(
                key: const ValueKey('privacy-clear-result'),
                tone: result.ok ? AppStatusTone.ok : AppStatusTone.failure,
                message: result.message,
                onDismiss: () => setState(() => _result = null),
                dismissLabel: copy.notifyDismiss,
              ),
            ),
          _readState(context),
          // Queued prompts carry attachment data URLs and drafts carry
          // whatever was typed but never sent. Both are the user's content,
          // held indefinitely until a server answers, so both get a size and
          // a way out that does not require deleting the server.
          ListenableBuilder(
            listenable: _controller,
            builder: (context, _) {
              final queued = _controller.totalQueuedPromptCount;
              final drafts = _controller.totalSessionDraftCount;
              final queuedBytes = _controller.queuedPromptBytes;
              final queueReadable = _controller.queuedPromptStorageReadable;
              final l10n = lookupAppLocalizations(
                Localizations.localeOf(context),
              );
              final draftBytes = _controller.sessionDraftBytes;
              return KitRowGroup(
                label: copy.e7SettingsUi76,
                children: [
                  KitRow(
                    key: const ValueKey('local-storage-usage'),
                    leading: KitRow.icon(context, AppIconography.database),
                    title: copy.e7SettingsUi77,
                    supporting: TextSpan(
                      text: !queueReadable
                          ? l10n.queueStorageCountUnknown
                          : copy.e7SettingsStorageSummary(
                              formatBytes(queuedBytes + draftBytes),
                              queued,
                              formatBytes(queuedBytes),
                              drafts,
                              formatBytes(draftBytes),
                              OfflineQueueStore.maxAge.inDays,
                            ),
                    ),
                    // A readout, not a door: the whole sentence stays.
                    supportingMaxLines: 4,
                  ),
                  // Destructive (§2): confirmed first, never primary. With
                  // nothing to delete the row rests, and its own line says
                  // so.
                  _DestructiveRow(
                    rowKey: 'clear-queued-prompts',
                    icon: AppIconography.outbox,
                    title: copy.e7SettingsUi78,
                    subtitle: !queueReadable
                        ? l10n.queueStorageUnreadable
                        : queued == 0
                        ? copy.e7SettingsUi79
                        : copy.e7SettingsQueueDeleteSummary(queued),
                    enabled: (queued > 0 || !queueReadable) && !_busy,
                    onTap: () => _confirmAndClear(
                      title: copy.e7SettingsUi80,
                      body: !queueReadable
                          ? l10n.queueStorageDiscardUnreadable
                          : copy.e7SettingsQueueDeleteBody(queued),
                      // The counted verb (privacy-settings-clear-queued-sheet).
                      confirmLabel: copy.privacyDeleteQueuedCount(
                        queueReadable ? queued : 0,
                      ),
                      confirmKey: 'privacy-clear-queued-confirm',
                      clear: _controller.clearAllQueuedPrompts,
                      cleared: copy.e7SettingsUi81,
                      failed: copy.e7SettingsUi82,
                    ),
                  ),
                  _DestructiveRow(
                    rowKey: 'clear-session-drafts',
                    icon: AppIconography.editNote,
                    title: copy.e7SettingsUi83,
                    subtitle: drafts == 0
                        ? copy.e7SettingsUi84
                        : copy.e7SettingsDraftDeleteSummary(drafts),
                    enabled: drafts > 0 && !_busy,
                    onTap: () => _confirmAndClear(
                      title: copy.e7SettingsUi85,
                      body: copy.e7SettingsDraftDeleteBody(drafts),
                      confirmLabel: copy.privacyDeleteDraftsCount(drafts),
                      confirmKey: 'privacy-clear-drafts-confirm',
                      clear: _controller.clearAllSessionDrafts,
                      cleared: copy.e7SettingsUi86,
                      failed: copy.e7SettingsUi87,
                    ),
                  ),
                ],
              );
            },
          ),
          // The policy itself: About's old Privacy tab lives here now
          // (P3.10), so what the app keeps and what it sends sit together.
          if (policy != null) ...[
            SizedBox(height: tokens.sectionGap),
            KitRowGroup(
              children: [
                KitRow(
                  key: const ValueKey('privacy-policy'),
                  leading: KitRow.icon(context, policy.icon),
                  title: policy.title,
                  supporting: TextSpan(text: copy.e7SettingsUi93),
                  supportingMaxLines: 2,
                  trailing: const KitChevron(),
                  onTap: () => policy.open(context, scope),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

/// A row that deletes something this device holds (design standard §2):
/// the kit's destructive row, always confirmed by its action, never a
/// primary button.
class _DestructiveRow extends StatelessWidget {
  const _DestructiveRow({
    required this.rowKey,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.enabled,
    required this.onTap,
  });

  final String rowKey;
  final IconData icon;
  final String title;
  final String subtitle;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => KitRow(
    key: ValueKey(rowKey),
    leading: KitRow.icon(context, icon),
    title: title,
    supporting: TextSpan(text: subtitle),
    supportingMaxLines: 2,
    destructive: true,
    enabled: enabled,
    onTap: onTap,
  );
}
