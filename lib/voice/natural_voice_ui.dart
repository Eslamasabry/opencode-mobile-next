import 'dart:async';

import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import '../platform/platform_capabilities.dart';
import '../ui/app_theme.dart';
import '../ui/kit/kit.dart';
import 'model_download.dart';
import 'natural_voice.dart';
import 'presentation.dart';

/// Settings › Voice: who reads replies aloud. "Phone's voice" is the default;
/// "Natural voice" downloads one voice file set (a few seconds on Wi-Fi),
/// shows its progress, and can be removed again. Hidden where read-aloud does
/// not exist.
class NaturalVoiceSection extends StatefulWidget {
  const NaturalVoiceSection({super.key, this.controller});

  /// For tests; by default the phone-wide one is used.
  final NaturalVoiceController? controller;

  @override
  State<NaturalVoiceSection> createState() => _NaturalVoiceSectionState();
}

class _NaturalVoiceSectionState extends State<NaturalVoiceSection> {
  NaturalVoiceController? _controller;

  @override
  void initState() {
    super.initState();
    _controller = widget.controller;
    if (_controller == null && platformCapabilities.supportsReadAloud) {
      unawaited(_load());
    }
  }

  Future<void> _load() async {
    try {
      final controller = await NaturalVoiceController.shared();
      if (mounted) setState(() => _controller = controller);
    } catch (_) {
      // Without the files' folder there is nothing to offer.
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    if (controller == null || !platformCapabilities.supportsReadAloud) {
      return const SizedBox.shrink();
    }
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) => _body(context, controller),
    );
  }

  Widget _body(BuildContext context, NaturalVoiceController controller) {
    final strings = voiceStrings(context);
    final tokens = KitTokens.of(context);
    final pack = naturalVoicePacks.first;
    final size = voiceSizeText(strings, pack.downloadBytes);
    final installed = controller.isInstalled(pack);
    final busy = controller.busy;
    final error = controller.error;
    final selected = controller.chosen && installed ? 'natural' : 'phone';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(height: tokens.space6),
        KitText(strings.readAloudVoiceTitle, role: KitTextRole.label),
        SizedBox(height: tokens.space1),
        if (busy) ...[
          _progress(context, controller, pack),
          SizedBox(height: tokens.space3),
        ],
        if (error != null) ...[
          KitNotice(
            tone: AppStatusTone.failure,
            message: _errorText(strings, error),
          ),
          SizedBox(height: tokens.space3),
        ],
        KitChoiceList<String>.single(
          key: const Key('read-aloud-voice-choice'),
          semanticsLabel: strings.readAloudVoiceTitle,
          selected: selected,
          onSelected: (value) {
            if (value == 'phone') {
              unawaited(controller.choosePhoneVoice());
            } else {
              unawaited(controller.chooseNaturalVoice(pack));
            }
          },
          choices: [
            KitChoice<String>(
              key: const Key('read-aloud-voice-phone'),
              value: 'phone',
              title: strings.readAloudVoicePhone,
              supporting: strings.readAloudVoicePhoneDetail,
            ),
            KitChoice<String>(
              key: const Key('read-aloud-voice-natural'),
              value: 'natural',
              title: installed
                  ? strings.readAloudVoiceNatural
                  : strings.readAloudVoiceNaturalDownload(size),
              supporting: strings.readAloudVoiceNaturalDetail,
              enabled: !busy,
            ),
          ],
        ),
        if (installed && !busy) ...[
          SizedBox(height: tokens.space1),
          KitActionStack(
            tertiary: [
              KitAction(
                key: const Key('natural-voice-remove'),
                label: strings.naturalVoiceRemove(size),
                icon: AppIconography.delete,
                destructive: true,
                onPressed: () => unawaited(_confirmRemove(context, controller)),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _progress(
    BuildContext context,
    NaturalVoiceController controller,
    NaturalVoicePack pack,
  ) {
    final strings = voiceStrings(context);
    final tokens = KitTokens.of(context);
    final verifying = controller.state == NaturalVoiceState.verifying;
    final fraction = controller.progress?.fraction;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitText(
          verifying
              ? strings.e7VoiceUiVerifying
              : strings.naturalVoiceDownloading,
          role: KitTextRole.rowTitle,
        ),
        SizedBox(height: tokens.space2),
        KitProgressView(
          progress: verifying || fraction == null
              ? const KitProgress.waiting()
              : KitProgress.known(
                  fraction.clamp(0.0, 1.0),
                  caption: strings.e7VoiceUiDownloadProgress(
                    voiceSizeText(strings, controller.progress?.received ?? 0),
                    voiceSizeText(strings, pack.downloadBytes),
                  ),
                ),
        ),
        if (!verifying) ...[
          SizedBox(height: tokens.space2),
          KitActionStack(
            secondary: KitAction(
              key: const Key('natural-voice-cancel-download'),
              label: strings.e7VoiceUiCancelDownload,
              onPressed: controller.cancelDownload,
            ),
          ),
        ],
      ],
    );
  }

  String _errorText(AppLocalizations strings, Object error) {
    if (error is NaturalVoiceNoSpace) {
      return strings.naturalVoiceNoSpace(
        voiceSizeText(strings, error.requiredBytes),
      );
    }
    if (error is VoiceDownloadException) {
      return voiceErrorText(error, strings);
    }
    return strings.naturalVoiceFailed;
  }

  Future<void> _confirmRemove(
    BuildContext context,
    NaturalVoiceController controller,
  ) async {
    final strings = voiceStrings(context);
    final pack = naturalVoicePacks.first;
    final size = voiceSizeText(strings, pack.downloadBytes);
    final confirmed = await showKitConfirm(
      context,
      title: strings.naturalVoiceRemoveTitle,
      body: strings.naturalVoiceRemoveBody,
      confirmLabel: strings.naturalVoiceRemove(size),
      cancelLabel: strings.naturalVoiceKeep,
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      confirmKey: const Key('natural-voice-remove-confirm'),
    );
    if (confirmed) await controller.remove(pack);
  }
}
