import 'dart:async';

import 'package:flutter/material.dart';

import 'automatic_setup.dart';
import 'automatic_setup_platform.dart';
import 'controller.dart';
import 'presentation.dart';

import 'audio.dart';
import 'device.dart';
import 'model_manager.dart';
import 'model_manifest.dart';
import '../ui/app_theme.dart';
import '../ui/kit/kit.dart';
import '../platform/platform_capabilities.dart';
import '../l10n/app_localizations.dart';

AppLocalizations _voiceStrings(BuildContext context) => voiceStrings(context);

bool _setupBusy(VoiceModelManager manager) =>
    manager.state == VoiceModelState.downloading ||
    manager.state == VoiceModelState.verifying;

/// The voice model setup sheet (voice-model-setup-sheet), Settings › Voice,
/// in the kit's one sheet frame. The first mic tap does not open it: that is
/// [showVoiceAutomaticSetupSheet], which picks the pack itself. Completes with true when the person chose "Use Balanced" or
/// "Done" with the model on the phone, false otherwise (Not now, close,
/// swipe).
///
/// The pinned primary follows the manager: "Download Balanced (153 MB)"
/// before the model is on the phone, "Use Balanced" once a download here
/// finishes, and nothing while a download runs (the progress in the body
/// says what is happening and holds Cancel download). Opened with the model
/// already on the phone and in use, there is nothing to start: no primary,
/// and the secondary is "Done". A download keeps running in the manager if
/// the sheet is closed; reopening shows its progress.
Future<bool> showVoiceModelSetupSheet(
  BuildContext context,
  VoiceModelManager manager,
) async {
  if (!platformCapabilities.supportsVoice) return false;
  final strings = _voiceStrings(context);
  final navigator = Navigator.of(context);
  final primary = ValueNotifier<KitAction?>(null);
  // Opened with the chosen model on the phone: it is already the one in
  // use, so "Use Balanced" would only close the sheet; "Done" does that.
  final ready =
      !_setupBusy(manager) && manager.isInstalled(manager.selectedPack);
  void sync() => primary.value = _setupPrimary(
    manager,
    strings,
    use: () => navigator.pop(true),
    inUse: ready,
  );
  sync();
  manager.addListener(sync);
  try {
    return await showKitSheet<bool>(
          context,
          title: strings.e7VoiceUiLocalInput,
          subtitle: strings.voiceSetupSubtitle,
          icon: AppIconography.waveform,
          primaryListenable: primary,
          secondary: KitAction(
            key: const Key('voice-model-secondary-action'),
            label: ready ? strings.voiceSetupDone : strings.e7VoiceUiNotNow,
            onPressed: () => navigator.pop(ready),
          ),
          body: (context) => _VoiceModelSetupBody(manager: manager),
        ) ??
        false;
  } finally {
    manager.removeListener(sync);
    primary.dispose();
  }
}

/// Settings › Voice typing as a page of its own, like its siblings: the same
/// model choice, download and language the setup sheet holds, with the
/// download pinned at the bottom. The sheet stays for the chat's mic, which
/// asks in the middle of a task.
class VoiceSettingsScreen extends StatelessWidget {
  const VoiceSettingsScreen({super.key, required this.manager});

  final VoiceModelManager manager;

  @override
  Widget build(BuildContext context) {
    final strings = _voiceStrings(context);
    final tokens = KitTokens.of(context);
    final navigator = Navigator.of(context);
    return ListenableBuilder(
      listenable: manager,
      builder: (context, _) {
        final ready =
            !_setupBusy(manager) && manager.isInstalled(manager.selectedPack);
        final primary = _setupPrimary(
          manager,
          strings,
          use: navigator.maybePop,
          inUse: ready,
        );
        return KitScreen(
          topBar: KitTopBar(title: strings.settingsV3VoiceTyping),
          width: KitScreenWidth.reading,
          bottom: primary == null ? null : KitActionStack(primary: primary),
          body: ListView(
            key: const ValueKey('voice-settings-list'),
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              KitText(
                strings.voiceSetupSubtitle,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
              SizedBox(height: tokens.space4),
              _VoiceModelSetupBody(manager: manager),
            ],
          ),
        );
      },
    );
  }
}

KitAction? _setupPrimary(
  VoiceModelManager manager,
  AppLocalizations strings, {
  required VoidCallback use,
  bool inUse = false,
}) {
  if (_setupBusy(manager)) return null;
  final pack = manager.selectedPack;
  final name = voicePackLabel(pack, strings);
  const key = Key('voice-model-primary-action');
  if (manager.isInstalled(pack)) {
    // Already on the phone and in use: the secondary "Done" closes.
    if (inUse) return null;
    return KitAction(
      key: key,
      label: strings.voiceSetupUsePack(name),
      icon: AppIconography.mic,
      onPressed: use,
    );
  }
  final support = manager.supportFor(pack);
  return KitAction(
    key: key,
    label: strings.voiceSetupDownloadPack(
      name,
      voiceSizeText(strings, pack.downloadBytes),
    ),
    icon: AppIconography.download,
    onPressed: support.supported
        ? () => unawaited(manager.downloadSelected())
        : null,
    disabledReason: support.supported
        ? null
        : voiceSupportReason(manager, pack, support, strings),
  );
}

class _VoiceModelSetupBody extends StatelessWidget {
  const _VoiceModelSetupBody({required this.manager});

  final VoiceModelManager manager;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: manager,
    builder: (context, _) {
      final strings = _voiceStrings(context);
      final tokens = KitTokens.of(context);
      final busy = _setupBusy(manager);
      final selected = manager.selectedPack;
      final selectedName = voicePackLabel(selected, strings);
      final gap = SizedBox(height: tokens.space3);
      final error = manager.error;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          // What is happening comes first: the download the pinned
          // Download just started, or why setup failed, is never below the
          // fold (STATE-1).
          if (busy) ...[
            _VoiceDownloadProgress(manager: manager),
            SizedBox(height: tokens.space4),
          ],
          if (error != null) ...[
            KitNotice(
              tone: AppStatusTone.failure,
              message: strings.e7VoiceUiSetupFailed(
                voiceErrorText(error, strings, manager: manager),
              ),
            ),
            KitDetailsFold(
              label: strings.e7VoiceUiTechnicalDetails,
              text: error.toString(),
            ),
            gap,
          ],
          KitNotice(
            icon: AppIconography.locked,
            message: strings.e7VoiceUiPrivacyDownload,
            liveRegion: false,
          ),
          if (!manager.deviceInfo.hasMicrophone) ...[
            gap,
            KitNotice(message: strings.e7VoiceUiNoBuiltInMic),
          ],
          SizedBox(height: tokens.space4),
          KitText(strings.voiceSetupModelLabel, role: KitTextRole.label),
          SizedBox(height: tokens.space1),
          // A tap only chooses; the pinned primary downloads. (A list that
          // acts on tap would mark a pack that is not on the phone as
          // "Current".)
          KitChoiceList<String>.single(
            semanticsLabel: strings.voiceSetupModelLabel,
            actsOnTap: false,
            selected: selected.id,
            onSelected: (id) =>
                unawaited(manager.selectPack(voiceModelPack(id))),
            choices: [
              for (final pack in voiceModelPacks)
                _packChoice(context, manager, pack, busy: busy),
            ],
          ),
          if (manager.isInstalled(selected) && !busy) ...[
            SizedBox(height: tokens.space1),
            KitActionStack(
              tertiary: [
                KitAction(
                  key: Key('voice-redownload-${selected.id}'),
                  label: strings.voiceSetupRedownloadPack(selectedName),
                  icon: AppIconography.retry,
                  onPressed: () => unawaited(manager.redownloadPack(selected)),
                ),
                KitAction(
                  key: Key('voice-delete-${selected.id}'),
                  label: strings.voiceSetupDeletePack(
                    selectedName,
                    voiceSizeText(strings, selected.downloadBytes),
                  ),
                  icon: AppIconography.delete,
                  destructive: true,
                  onPressed: () => unawaited(_confirmDelete(context, selected)),
                ),
              ],
            ),
          ],
          SizedBox(height: tokens.space4),
          KitText(strings.e7VoiceUiLanguage, role: KitTextRole.label),
          SizedBox(height: tokens.space2),
          KitSegmented<VoiceLanguage>(
            key: const Key('voice-model-language'),
            semanticsLabel: strings.e7VoiceUiLanguage,
            selected: manager.language,
            onChanged: busy
                ? null
                : (language) => unawaited(manager.setLanguage(language)),
            disabledReason: busy ? strings.voiceSetupBusyReason : null,
            segments: [
              for (final language in VoiceLanguage.values)
                KitSegment(
                  value: language,
                  label: voiceLanguageLabel(language, strings),
                ),
            ],
          ),
        ],
      );
    },
  );

  KitChoice<String> _packChoice(
    BuildContext context,
    VoiceModelManager manager,
    VoiceModelPack pack, {
    required bool busy,
  }) {
    final strings = _voiceStrings(context);
    final support = manager.supportFor(pack);
    // On the phone: say so; otherwise that it is not, and what it costs to
    // download. The description already says which one is recommended.
    final line = [
      manager.isInstalled(pack)
          ? strings.e7VoiceUiInstalled
          : strings.voiceSetupNotDownloaded(
              voiceSizeText(strings, pack.downloadBytes),
            ),
      voicePackDescription(pack, strings),
    ].join(' · ');
    // While a download runs the rows rest, dimmed, with their own line:
    // the progress above already says why (no "Available after the
    // download" on every row).
    final resting = busy && support.supported;
    return KitChoice<String>(
      key: Key('voice-model-${pack.id}'),
      value: pack.id,
      title: voicePackLabel(pack, strings),
      supporting: resting ? null : line,
      enabled: !busy && support.supported,
      disabledReason: !support.supported
          ? voiceSupportReason(manager, pack, support, strings)
          : resting
          ? line
          : null,
    );
  }

  Future<void> _confirmDelete(BuildContext context, VoiceModelPack pack) async {
    final strings = _voiceStrings(context);
    final name = voicePackLabel(pack, strings);
    final confirmed = await showKitConfirm(
      context,
      title: strings.e7VoiceUiDeletePack(name),
      body: strings.e7VoiceUiDeleteDetail(
        voiceSizeText(strings, pack.downloadBytes),
      ),
      confirmLabel: strings.voiceSetupDeletePack(
        name,
        voiceSizeText(strings, pack.downloadBytes),
      ),
      cancelLabel: strings.voiceSetupKeepPack(name),
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: const Key('voice-delete-confirm'),
    );
    if (confirmed) await manager.deletePack(pack);
  }
}

/// The running download: what is downloading, how far, and the one way to
/// stop it, next to the progress it stops.
class _VoiceDownloadProgress extends StatelessWidget {
  const _VoiceDownloadProgress({required this.manager});

  final VoiceModelManager manager;

  @override
  Widget build(BuildContext context) {
    final strings = _voiceStrings(context);
    final tokens = KitTokens.of(context);
    final verifying = manager.state == VoiceModelState.verifying;
    final fraction = manager.progress?.fraction;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          liveRegion: true,
          excludeSemantics: true,
          label: verifying
              ? strings.e7VoiceUiVerifying
              : strings.e7VoiceUiDownloadPercent(
                  ((fraction ?? 0) * 10).floor() * 10,
                ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              KitText(
                verifying
                    ? strings.e7VoiceUiVerifying
                    : strings.voiceSetupDownloadingPack(
                        voicePackLabel(manager.selectedPack, strings),
                      ),
                role: KitTextRole.rowTitle,
              ),
              SizedBox(height: tokens.space2),
              KitProgressView(
                progress: verifying || fraction == null
                    ? const KitProgress.waiting()
                    : KitProgress.known(
                        fraction.clamp(0.0, 1.0),
                        caption: strings.e7VoiceUiDownloadProgress(
                          voiceSizeText(
                            strings,
                            manager.progress?.received ?? 0,
                          ),
                          voiceSizeText(
                            strings,
                            manager.selectedPack.downloadBytes,
                          ),
                        ),
                      ),
              ),
            ],
          ),
        ),
        if (!verifying) ...[
          SizedBox(height: tokens.space2),
          KitActionStack(
            secondary: KitAction(
              key: const Key('voice-model-cancel-download'),
              label: strings.e7VoiceUiCancelDownload,
              onPressed: manager.cancelDownload,
            ),
          ),
        ],
      ],
    );
  }
}

/// The first mic tap when no speech model is on the phone (P10.4): one
/// sheet that picks the pack for this phone by total RAM (never Android's
/// per-app memory class), says its size, asks before downloading (in so
/// many words on mobile data), downloads with progress and a notification,
/// and starts listening when the model is ready.
///
/// Completes with true once the microphone is starting: the recording is
/// then [composer]'s, and the caller shows its recording surface (which
/// finds the composer already listening). False when the person closed it,
/// cancelled, or setup could not finish. Closing the sheet cancels the
/// download (a partial file is kept, so the next attempt resumes); leaving
/// the app does not, and a download that finishes meanwhile waits for the
/// person instead of opening the microphone in the background.
///
/// [setup] is for tests; by default a controller is made for [composer]
/// and disposed here.
Future<bool> showVoiceAutomaticSetupSheet(
  BuildContext context,
  VoiceComposerController composer, {
  VoiceAutomaticSetupController? setup,
  VoiceSetupPlatform platform = const AndroidVoiceSetupPlatform(),
}) async {
  if (!platformCapabilities.supportsVoice) return false;
  final strings = _voiceStrings(context);
  final controller =
      setup ??
      VoiceAutomaticSetupController(composer: composer, platform: platform);
  var started = false;
  try {
    unawaited(controller.requestMicrophone());
    started =
        await showKitSheet<bool>(
          context,
          title: strings.voiceAutoSetupTitle,
          icon: AppIconography.mic,
          sheetKey: const Key('voice-auto-setup'),
          body: (context) => _VoiceAutomaticSetupBody(setup: controller),
        ) ==
        true;
    return started;
  } finally {
    if (started) {
      controller.handOff();
    } else {
      await controller.cancel();
    }
    if (setup == null) controller.dispose();
  }
}

class _VoiceAutomaticSetupBody extends StatefulWidget {
  const _VoiceAutomaticSetupBody({required this.setup});

  final VoiceAutomaticSetupController setup;

  @override
  State<_VoiceAutomaticSetupBody> createState() =>
      _VoiceAutomaticSetupBodyState();
}

class _VoiceAutomaticSetupBodyState extends State<_VoiceAutomaticSetupBody>
    with WidgetsBindingObserver {
  bool _closed = false;

  VoiceAutomaticSetupController get _setup => widget.setup;
  VoiceModelManager get _models => _setup.composer.models;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _setup.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) => _changed());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Only really leaving counts: a permission prompt makes the app
    // inactive, not paused.
    if (state == AppLifecycleState.resumed) {
      _setup.setForeground(true);
    } else if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden) {
      _setup.setForeground(false);
    }
  }

  void _changed() {
    if (_closed || !mounted) return;
    final stage = _setup.stage;
    if (stage == VoiceSetupStage.starting ||
        stage == VoiceSetupStage.listening) {
      // The recording belongs to the composer's surface from here on.
      _setup.handOff();
      _close(true);
    } else if (stage == VoiceSetupStage.cancelled) {
      _close(false);
    }
  }

  void _close(bool started) {
    if (_closed) return;
    _closed = true;
    KitSheet.close<bool>(context, started);
  }

  /// Settings › Voice, for a choice this sheet does not make; a model made
  /// ready there starts listening here.
  Future<void> _openSettings() async {
    await showVoiceModelSetupSheet(context, _models);
    if (!mounted || _closed) return;
    if (_models.isReady) unawaited(_setup.requestMicrophone());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _setup.removeListener(_changed);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([_setup, _models]),
    builder: (context, _) {
      final strings = _voiceStrings(context);
      final tokens = KitTokens.of(context);
      final notNow = KitAction(
        key: const Key('voice-auto-setup-not-now'),
        label: strings.e7VoiceUiNotNow,
        onPressed: () => _close(false),
      );
      final retry = KitAction(
        key: const Key('voice-auto-setup-retry'),
        label: strings.e7VoiceUiRetry,
        icon: AppIconography.retry,
        onPressed: () => unawaited(_setup.requestMicrophone()),
      );
      final children = <Widget>[];
      KitAction? primary;
      KitAction? secondary = notNow;
      final tertiary = <KitAction>[];
      switch (_setup.stage) {
        case VoiceSetupStage.idle ||
            VoiceSetupStage.checking ||
            VoiceSetupStage.starting ||
            VoiceSetupStage.listening ||
            VoiceSetupStage.cancelled:
          children.add(
            KitProgressView(
              progress: KitProgress.waiting(
                caption: strings.voiceAutoSetupChecking,
              ),
            ),
          );
        case VoiceSetupStage.consentRequired:
          final pack = _setup.pack!;
          final size = voiceSizeText(strings, _setup.downloadBytes);
          final unmetered = _setup.network == VoiceSetupNetwork.unmetered;
          final metered = _setup.network == VoiceSetupNetwork.metered;
          children.addAll([
            KitText(strings.voiceAutoSetupOffer, role: KitTextRole.body),
            SizedBox(height: tokens.space2),
            KitText(
              strings.voiceAutoSetupPicked(voicePackLabel(pack, strings)),
              key: const Key('voice-auto-setup-pack'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
            if (!unmetered) ...[
              SizedBox(height: tokens.space3),
              KitNotice(
                key: const Key('voice-auto-setup-metered'),
                icon: AppIconography.warning,
                message: metered
                    ? strings.voiceAutoSetupMobileData
                    : strings.voiceAutoSetupUnknownNetwork,
              ),
            ],
            SizedBox(height: tokens.space3),
            _packDetails(strings, pack),
          ]);
          primary = KitAction(
            key: const Key('voice-auto-setup-download'),
            label: metered
                ? strings.voiceAutoSetupDownloadMobile(size)
                : strings.voiceAutoSetupDownload(size),
            icon: AppIconography.download,
            onPressed: () =>
                unawaited(_setup.confirmDownload(allowMetered: !unmetered)),
          );
          tertiary.add(
            KitAction(
              key: const Key('voice-auto-setup-other'),
              label: strings.voiceAutoSetupOtherModel,
              icon: AppIconography.settingsAdvanced,
              onPressed: () => unawaited(_openSettings()),
            ),
          );
        case VoiceSetupStage.downloading || VoiceSetupStage.verifying:
          final verifying = _setup.stage == VoiceSetupStage.verifying;
          final total = _setup.downloadBytes;
          final received = _setup.receivedBytes;
          final fraction = total > 0 ? received / total : null;
          children.addAll([
            Semantics(
              liveRegion: true,
              excludeSemantics: true,
              label: verifying
                  ? strings.e7VoiceUiVerifying
                  : strings.e7VoiceUiDownloadPercent(
                      ((fraction ?? 0) * 10).floor() * 10,
                    ),
              child: KitProgressView(
                progress: verifying || fraction == null
                    ? KitProgress.waiting(caption: strings.e7VoiceUiVerifying)
                    : KitProgress.known(
                        fraction.clamp(0.0, 1.0),
                        caption: strings.e7VoiceUiDownloadProgress(
                          voiceSizeText(strings, received),
                          voiceSizeText(strings, total),
                        ),
                      ),
              ),
            ),
            SizedBox(height: tokens.space3),
            KitText(
              _setup.notificationAvailable == true
                  ? strings.voiceAutoSetupNotified
                  : strings.voiceAutoSetupStartsAfter,
              key: const Key('voice-auto-setup-note'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ]);
          secondary = verifying
              ? null
              : KitAction(
                  key: const Key('voice-auto-setup-cancel'),
                  label: strings.e7VoiceUiCancelDownload,
                  onPressed: () => unawaited(_setup.cancel()),
                );
        case VoiceSetupStage.ready:
          children.add(
            KitNotice(
              tone: AppStatusTone.ok,
              message: strings.voiceAutoSetupReady,
            ),
          );
          primary = KitAction(
            key: const Key('voice-auto-setup-start'),
            label: strings.e7VoiceUiStartListening,
            icon: AppIconography.mic,
            onPressed: () => unawaited(_setup.requestMicrophone()),
          );
        case VoiceSetupStage.blocked || VoiceSetupStage.failed:
          final (message, action) = _problem(strings, retry);
          final error = _setup.problem == VoiceSetupProblem.downloadFailed
              ? _models.error
              : _setup.problem == VoiceSetupProblem.listeningFailed
              ? _setup.composer.error
              : null;
          children.addAll([
            KitNotice(
              key: const Key('voice-auto-setup-problem'),
              tone: _setup.stage == VoiceSetupStage.failed
                  ? AppStatusTone.failure
                  : AppStatusTone.neutral,
              message: message,
            ),
            if (error != null)
              KitDetailsFold(
                label: strings.e7VoiceUiTechnicalDetails,
                text: error.toString(),
              ),
          ]);
          primary = action;
      }
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          ...children,
          SizedBox(height: tokens.space4),
          KitActionBlock(
            primary: primary,
            secondary: secondary,
            tertiary: tertiary,
          ),
        ],
      );
    },
  );

  /// What stopped setup, in plain words, and the one way forward.
  (String, KitAction?) _problem(AppLocalizations strings, KitAction retry) {
    final settings = KitAction(
      key: const Key('voice-auto-setup-settings'),
      label: strings.voiceAutoSetupChooseModel,
      icon: AppIconography.settingsAdvanced,
      onPressed: () => unawaited(_openSettings()),
    );
    switch (_setup.problem) {
      case VoiceSetupProblem.unknownMemory:
        return (strings.voiceAutoSetupUnknownMemory, settings);
      case VoiceSetupProblem.noSupportedPack:
        return (_noPackReason(strings), null);
      case VoiceSetupProblem.offline:
        return (strings.voiceAutoSetupOffline, retry);
      case VoiceSetupProblem.busy:
        return (
          strings.voiceAutoSetupBusy,
          KitAction(
            key: const Key('voice-auto-setup-show-download'),
            label: strings.voiceAutoSetupShowDownload,
            onPressed: () => unawaited(_openSettings()),
          ),
        );
      case VoiceSetupProblem.downloadFailed:
        return (
          voiceErrorText(_models.error, strings, manager: _models),
          retry,
        );
      case VoiceSetupProblem.listeningFailed:
        final error = _setup.composer.error;
        if (error is VoicePermissionDenied && error.permanent) {
          return (
            voiceErrorText(error, strings),
            KitAction(
              key: const Key('voice-auto-setup-open-settings'),
              label: strings.voiceAllowMicInSettings,
              icon: AppIconography.settings,
              onPressed: () => unawaited(voiceDevicePlatform.openAppSettings()),
            ),
          );
        }
        return (voiceErrorText(error, strings), retry);
      case VoiceSetupProblem.unavailable
          when _setup.stage == VoiceSetupStage.blocked:
        return (strings.voiceAutoSetupNoCapture, null);
      case VoiceSetupProblem.unavailable || null:
        return (strings.e7VoiceUiInputFailed, retry);
    }
  }

  /// Why no pack fits: the smallest pack's own reason (memory, storage or
  /// the processor), which says what this phone lacks.
  String _noPackReason(AppLocalizations strings) {
    final smallest = [...voiceModelPacks]
      ..sort((a, b) => a.minimumMemoryMb.compareTo(b.minimumMemoryMb));
    final pack = smallest.first;
    final support = _models.supportFor(pack);
    return (support.supported
            ? null
            : voiceSupportReason(_models, pack, support, strings)) ??
        strings.voiceAutoSetupNoCapture;
  }

  /// The technical facts behind the pick, folded: the exact files and size
  /// and the memory rule. The only place a model file name or MiB appears.
  Widget _packDetails(AppLocalizations strings, VoiceModelPack pack) {
    final memory = _models.deviceInfo.totalMemoryMb;
    return KitDetailsFold(
      foldKey: const Key('voice-auto-setup-details'),
      values: [
        KitTechnicalValue(
          strings.voiceAutoSetupDetailFiles,
          pack.files.map((file) => file.name).join(', '),
        ),
        KitTechnicalValue(
          strings.voiceAutoSetupDetailSize,
          formatModelBytes(pack.downloadBytes),
        ),
        KitTechnicalValue(
          strings.voiceAutoSetupDetailMemory,
          strings.voiceAutoSetupDetailMemoryValue(
            pack.minimumMemoryMb,
            memory ?? 0,
          ),
        ),
      ],
    );
  }
}
