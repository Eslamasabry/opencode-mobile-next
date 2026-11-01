import 'dart:async';

import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import '../ui/app_theme.dart';
import '../ui/kit/kit.dart';
import '../voice/presentation.dart';
import 'local_llm_controller.dart';

String _blockText(AppLocalizations strings, LocalAssistantBlock block) =>
    switch (block) {
      LocalAssistantBlock.memory => strings.localAssistantUnavailableMemory,
      LocalAssistantBlock.abi => strings.localAssistantUnavailableAbi,
      LocalAssistantBlock.platform => strings.localAssistantUnavailablePlatform,
    };

/// Settings › Assistant on this phone: the switch, the one-time download with
/// its size up front, the chip it runs on, and Delete.
class LocalAssistantSettingsScreen extends StatelessWidget {
  const LocalAssistantSettingsScreen({super.key, required this.controller});

  final LocalAssistantController controller;

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final size = voiceSizeText(strings, controller.pack.downloadBytes);
        final block = controller.block;
        final phase = controller.phase;
        final busy = controller.busyDownloading;
        final primary = block != null || busy || controller.installed
            ? null
            : KitAction(
                key: const Key('local-assistant-download'),
                label: strings.localAssistantDownload(size),
                icon: AppIconography.download,
                onPressed: () => unawaited(controller.download()),
              );
        return KitScreen(
          topBar: KitTopBar(title: strings.localAssistantTitle),
          width: KitScreenWidth.reading,
          bottom: primary == null ? null : KitActionStack(primary: primary),
          body: ListView(
            key: const ValueKey('local-assistant-list'),
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              KitText(
                strings.localAssistantSubtitle,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
              SizedBox(height: tokens.space4),
              if (block != null)
                KitNotice(
                  key: const Key('local-assistant-unavailable'),
                  icon: AppIconography.info,
                  message: _blockText(strings, block),
                )
              else ...[
                KitSwitchRow(
                  switchKey: const Key('local-assistant-switch'),
                  title: strings.localAssistantSwitchTitle,
                  supporting: strings.localAssistantSwitchSupporting,
                  value: controller.enabled,
                  onChanged: (value) => unawaited(controller.setEnabled(value)),
                ),
                SizedBox(height: tokens.space3),
                KitNotice(
                  icon: AppIconography.locked,
                  message: strings.localAssistantPrivacy,
                  liveRegion: false,
                ),
                SizedBox(height: tokens.space4),
                if (busy) ...[
                  _Progress(controller: controller),
                  SizedBox(height: tokens.space3),
                ],
                if (phase == LocalAssistantPhase.needsWifi) ...[
                  KitNotice(
                    key: const Key('local-assistant-needs-wifi'),
                    tone: AppStatusTone.attention,
                    message: strings.localAssistantNeedsWifi(size),
                    actions: [
                      KitAction(
                        key: const Key('local-assistant-mobile-data'),
                        label: strings.localAssistantUseMobileData,
                        onPressed: () => unawaited(
                          controller.download(allowMobileData: true),
                        ),
                      ),
                    ],
                  ),
                  SizedBox(height: tokens.space3),
                ],
                if (phase == LocalAssistantPhase.noSpace) ...[
                  KitNotice(
                    key: const Key('local-assistant-no-space'),
                    tone: AppStatusTone.attention,
                    message: strings.localAssistantNoSpace(
                      voiceSizeText(strings, localAssistantRequiredBytes()),
                    ),
                  ),
                  SizedBox(height: tokens.space3),
                ],
                if (phase == LocalAssistantPhase.error) ...[
                  KitNotice(
                    key: const Key('local-assistant-failed'),
                    tone: AppStatusTone.failure,
                    message: strings.localAssistantSetupFailed,
                  ),
                  if (controller.error != null)
                    KitDetailsFold(
                      label: strings.e7VoiceUiTechnicalDetails,
                      text: controller.error.toString(),
                    ),
                  SizedBox(height: tokens.space3),
                ],
                KitText(
                  controller.installed
                      ? strings.localAssistantOnPhone(size)
                      : strings.localAssistantNotDownloaded(size),
                  role: KitTextRole.rowTitle,
                  key: const Key('local-assistant-status'),
                ),
                SizedBox(height: tokens.space1),
                KitText(
                  switch (controller.backend) {
                    'gpu' => strings.localAssistantBackendGpu,
                    'cpu' => strings.localAssistantBackendCpu,
                    _ => strings.localAssistantBackendIdle,
                  },
                  role: KitTextRole.secondary,
                  tone: KitTextTone.secondary,
                  key: const Key('local-assistant-backend'),
                ),
                if (controller.installed && !busy) ...[
                  SizedBox(height: tokens.space3),
                  KitActionStack(
                    tertiary: [
                      KitAction(
                        key: const Key('local-assistant-delete'),
                        label: strings.localAssistantDelete(size),
                        icon: AppIconography.delete,
                        destructive: true,
                        onPressed: () =>
                            unawaited(_confirmDelete(context, size)),
                      ),
                    ],
                  ),
                ],
              ],
            ],
          ),
        );
      },
    );
  }

  Future<void> _confirmDelete(BuildContext context, String size) async {
    final strings = AppLocalizations.of(context);
    final confirmed = await showKitConfirm(
      context,
      title: strings.localAssistantDeleteTitle,
      body: strings.localAssistantDeleteBody(size),
      confirmLabel: strings.localAssistantDelete(size),
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: const Key('local-assistant-delete-confirm'),
    );
    if (confirmed) await controller.deleteModel();
  }
}

class _Progress extends StatelessWidget {
  const _Progress({required this.controller});

  final LocalAssistantController controller;

  @override
  Widget build(BuildContext context) {
    final strings = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final verifying = controller.phase == LocalAssistantPhase.verifying;
    final progress = controller.progress;
    final fraction = progress?.fraction;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitText(
          verifying
              ? strings.localAssistantVerifying
              : strings.localAssistantDownloading,
          role: KitTextRole.rowTitle,
        ),
        SizedBox(height: tokens.space2),
        KitProgressView(
          progress: verifying || fraction == null
              ? const KitProgress.waiting()
              : KitProgress.known(
                  fraction.clamp(0.0, 1.0),
                  caption: strings.localAssistantProgress(
                    voiceSizeText(strings, progress?.received ?? 0),
                    voiceSizeText(strings, controller.pack.downloadBytes),
                  ),
                ),
        ),
        if (!verifying) ...[
          SizedBox(height: tokens.space2),
          KitActionStack(
            secondary: KitAction(
              key: const Key('local-assistant-cancel-download'),
              label: strings.localAssistantCancelDownload,
              onPressed: controller.cancelDownload,
            ),
          ),
        ],
      ],
    );
  }
}

/// "Summarize on this phone": a sheet that shows the summary as the model
/// writes it, with Stop while it runs and Copy when it has text.
Future<void> showLocalSummarySheet(
  BuildContext context,
  LocalAssistantController controller,
  String transcript,
) async {
  final strings = AppLocalizations.of(context);
  final navigator = Navigator.of(context);
  final run = controller.summarize(transcript);
  final primary = ValueNotifier<KitAction?>(null);
  final secondary = ValueNotifier<KitAction?>(null);
  void sync() {
    final active = run.active;
    primary.value = active
        ? KitAction(
            key: const Key('local-summary-stop'),
            label: strings.localAssistantStop,
            icon: AppIconography.stop,
            onPressed: () => unawaited(run.stop()),
          )
        : run.status == LocalSummaryStatus.failed && run.text.isEmpty
        ? KitAction(
            key: const Key('local-summary-retry'),
            label: strings.localAssistantTryAgain,
            icon: AppIconography.retry,
            onPressed: () {
              navigator.pop();
              unawaited(showLocalSummarySheet(context, controller, transcript));
            },
          )
        : run.text.isEmpty
        ? null
        : KitAction.copy(
            key: const Key('local-summary-copy'),
            label: strings.localAssistantCopy,
            text: () => run.text,
            redact: false,
          );
    secondary.value = KitAction(
      key: const Key('local-summary-done'),
      label: strings.localAssistantDone,
      onPressed: navigator.pop,
    );
  }

  sync();
  run.addListener(sync);
  try {
    await showKitSheet<void>(
      context,
      title: strings.localAssistantSheetTitle,
      subtitle: strings.localAssistantSheetSubtitle,
      icon: AppIconography.sparkle,
      primaryListenable: primary,
      secondaryListenable: secondary,
      body: (context) => _SummaryBody(run: run),
    );
  } finally {
    run.removeListener(sync);
    unawaited(run.stop());
    primary.dispose();
    secondary.dispose();
    run.dispose();
  }
}

class _SummaryBody extends StatelessWidget {
  const _SummaryBody({required this.run});

  final LocalSummaryRun run;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: run,
    builder: (context, _) {
      final strings = AppLocalizations.of(context);
      final tokens = KitTokens.of(context);
      final text = run.text.trim();
      final rate = run.wordsPerSecond;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (text.isEmpty && run.active)
            Semantics(
              liveRegion: true,
              child: KitProgressView(
                progress: KitProgress.waiting(
                  caption: strings.localAssistantPreparing,
                ),
              ),
            )
          else if (text.isNotEmpty)
            KitText.selectable(
              text,
              key: const Key('local-summary-text'),
              role: KitTextRole.body,
            ),
          if (run.status == LocalSummaryStatus.stopped) ...[
            SizedBox(height: tokens.space3),
            KitText(
              strings.localAssistantStopped,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ],
          if (run.status == LocalSummaryStatus.failed) ...[
            SizedBox(height: tokens.space3),
            KitNotice(
              tone: AppStatusTone.failure,
              message: strings.localAssistantRunFailed,
            ),
          ],
          if (run.status == LocalSummaryStatus.done && rate != null) ...[
            SizedBox(height: tokens.space3),
            KitText(
              strings.localAssistantStats(
                (run.firstTokenMs / 1000).toStringAsFixed(1),
                rate.toStringAsFixed(1),
              ),
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
            ),
          ],
        ],
      );
    },
  );
}
