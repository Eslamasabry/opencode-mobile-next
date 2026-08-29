import 'dart:async';

import 'package:flutter/material.dart';

import 'controller.dart';
import 'audio.dart';
import 'device.dart';
import 'model_manager.dart';
import 'model_manifest.dart';
import '../ui/app_theme.dart';

Future<bool> showVoiceModelSetupSheet(
  BuildContext context,
  VoiceModelManager manager,
) async =>
    await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (context) => _VoiceModelSetupSheet(manager: manager),
    ) ??
    false;

class _VoiceModelSetupSheet extends StatelessWidget {
  const _VoiceModelSetupSheet({required this.manager});

  final VoiceModelManager manager;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return AnimatedBuilder(
      animation: manager,
      builder: (context, _) {
        final busy =
            manager.state == VoiceModelState.downloading ||
            manager.state == VoiceModelState.verifying;
        return FractionallySizedBox(
          heightFactor: .9,
          child: Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              0,
              20,
              16 + MediaQuery.viewInsetsOf(context).bottom,
            ),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primaryContainer,
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: const Icon(Icons.graphic_eq_rounded),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Local voice input',
                              style: theme.textTheme.titleLarge,
                            ),
                            Text(
                              'Choose a multilingual Whisper INT8 model',
                              style: theme.textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: theme.colorScheme.outlineVariant,
                      ),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.lock_outline_rounded,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(width: 10),
                        const Expanded(
                          child: Text(
                            'Audio stays on this device. Transcription is local and audio is discarded after use. The one-time model download requires internet access.',
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  if (!manager.deviceInfo.hasMicrophone) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'Android reports no built-in microphone. Voice input may still work with a wired or USB microphone.',
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: voiceModelPacks.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final pack = voiceModelPacks[index];
                      return _VoiceModelCard(
                        manager: manager,
                        pack: pack,
                        busy: busy,
                        onDelete: () => _confirmDelete(context, pack),
                      );
                    },
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<VoiceLanguage>(
                    key: ValueKey(manager.language),
                    initialValue: manager.language,
                    isExpanded: true,
                    decoration: const InputDecoration(
                      labelText: 'Transcription language',
                      border: OutlineInputBorder(),
                    ),
                    items: [
                      for (final language in VoiceLanguage.values)
                        DropdownMenuItem(
                          value: language,
                          child: Text(language.label),
                        ),
                    ],
                    onChanged: busy
                        ? null
                        : (language) {
                            if (language != null) manager.setLanguage(language);
                          },
                  ),
                  if (busy) ...[
                    const SizedBox(height: 14),
                    Semantics(
                      liveRegion: true,
                      excludeSemantics: true,
                      label: manager.state == VoiceModelState.verifying
                          ? 'Verifying downloaded model'
                          : 'Downloading voice model ${((manager.progress?.fraction ?? 0) * 10).floor() * 10} percent',
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          LinearProgressIndicator(
                            value: manager.state == VoiceModelState.verifying
                                ? null
                                : manager.progress?.fraction,
                          ),
                          const SizedBox(height: 6),
                          Text(
                            manager.state == VoiceModelState.verifying
                                ? 'Verifying size and SHA-256…'
                                : '${formatModelBytes(manager.progress?.received ?? 0)} of ${formatModelBytes(manager.selectedPack.downloadBytes)}',
                          ),
                        ],
                      ),
                    ),
                  ],
                  if (manager.error != null) ...[
                    const SizedBox(height: 10),
                    Semantics(
                      liveRegion: true,
                      child: Text(
                        'Model setup failed: ${manager.error}',
                        style: TextStyle(color: theme.colorScheme.error),
                      ),
                    ),
                  ],
                  const SizedBox(height: 12),
                  _AdaptiveVoiceActions(
                    secondary: OutlinedButton(
                      key: const Key('voice-model-secondary-action'),
                      style: _voiceButtonStyle(),
                      onPressed: busy
                          ? manager.cancelDownload
                          : () => Navigator.pop(context, false),
                      child: Text(busy ? 'Cancel download' : 'Not now'),
                    ),
                    primary: FilledButton.icon(
                      key: const Key('voice-model-primary-action'),
                      style: _voiceButtonStyle(),
                      onPressed: busy
                          ? null
                          : manager.isInstalled(manager.selectedPack)
                          ? () => Navigator.pop(context, true)
                          : manager.supportFor(manager.selectedPack).supported
                          ? manager.downloadSelected
                          : null,
                      icon: Icon(
                        manager.isInstalled(manager.selectedPack)
                            ? Icons.mic_rounded
                            : Icons.download_rounded,
                      ),
                      label: Text(
                        manager.isInstalled(manager.selectedPack)
                            ? 'Use model'
                            : 'Download',
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> _confirmDelete(BuildContext context, VoiceModelPack pack) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete ${pack.label}?'),
        content: Text(
          'This removes ${formatModelBytes(pack.downloadBytes)} from app-private storage. You can download it again later.',
        ),
        actions: [
          TextButton(
            style: _voiceButtonStyle(),
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep'),
          ),
          FilledButton(
            style: _voiceButtonStyle(),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) await manager.deletePack(pack);
  }
}

class _VoiceModelCard extends StatelessWidget {
  const _VoiceModelCard({
    required this.manager,
    required this.pack,
    required this.busy,
    required this.onDelete,
  });

  final VoiceModelManager manager;
  final VoiceModelPack pack;
  final bool busy;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final selected = manager.selectedPack.id == pack.id;
    final installed = manager.isInstalled(pack);
    final support = manager.supportFor(pack);
    final enabled = !busy && support.supported;
    final badges = [
      if (pack.id == 'base') 'default',
      if (pack.id == 'small') 'optional',
      installed ? 'installed' : 'not installed',
    ];

    return Semantics(
      container: true,
      explicitChildNodes: true,
      child: AnimatedContainer(
        duration: MediaQuery.disableAnimationsOf(context)
            ? Duration.zero
            : const Duration(milliseconds: 160),
        decoration: BoxDecoration(
          color: selected
              ? theme.colorScheme.primaryContainer.withValues(alpha: .45)
              : theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: selected
                ? theme.colorScheme.primary
                : theme.colorScheme.outlineVariant,
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              button: true,
              selected: selected,
              enabled: enabled,
              excludeSemantics: true,
              label:
                  '${pack.label}, ${formatModelBytes(pack.downloadBytes)}, ${badges.join(', ')}. ${pack.description}',
              hint: !support.supported
                  ? support.reason
                  : busy
                  ? 'Unavailable while model setup is in progress'
                  : selected
                  ? 'Selected'
                  : 'Double tap to select',
              child: InkWell(
                key: Key('voice-model-${pack.id}'),
                borderRadius: BorderRadius.circular(16),
                onTap: enabled ? () => manager.selectPack(pack) : null,
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ConstrainedBox(
                        constraints: const BoxConstraints(
                          minWidth: 48,
                          minHeight: 48,
                        ),
                        child: Icon(
                          selected
                              ? Icons.radio_button_checked_rounded
                              : Icons.radio_button_unchecked_rounded,
                          color: selected
                              ? theme.colorScheme.primary
                              : theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Wrap(
                              spacing: 8,
                              runSpacing: 4,
                              crossAxisAlignment: WrapCrossAlignment.center,
                              children: [
                                Text(
                                  pack.label,
                                  style: theme.textTheme.titleMedium,
                                ),
                                if (pack.id == 'base')
                                  const Chip(
                                    label: Text('Default'),
                                    visualDensity: VisualDensity.compact,
                                  ),
                                if (pack.id == 'small')
                                  const Chip(
                                    label: Text('Optional'),
                                    visualDensity: VisualDensity.compact,
                                  ),
                                if (installed)
                                  const Chip(
                                    avatar: Icon(Icons.check_rounded, size: 16),
                                    label: Text('Installed'),
                                    visualDensity: VisualDensity.compact,
                                  ),
                              ],
                            ),
                            const SizedBox(height: 3),
                            Text(
                              '${formatModelBytes(pack.downloadBytes)} download',
                              style: theme.textTheme.labelLarge,
                            ),
                            const SizedBox(height: 3),
                            Text(
                              pack.description,
                              style: theme.textTheme.bodySmall,
                            ),
                            if (!support.supported) ...[
                              const SizedBox(height: 6),
                              Text(
                                support.reason!,
                                style: TextStyle(
                                  color: theme.colorScheme.error,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (installed && !busy)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
                child: Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: [
                    TextButton.icon(
                      key: Key('voice-redownload-${pack.id}'),
                      style: _voiceButtonStyle(),
                      onPressed: () => manager.redownloadPack(pack),
                      icon: const Icon(Icons.refresh_rounded),
                      label: const Text('Re-download'),
                    ),
                    TextButton.icon(
                      key: Key('voice-delete-${pack.id}'),
                      style: _voiceButtonStyle(),
                      onPressed: onDelete,
                      icon: const Icon(Icons.delete_outline_rounded),
                      label: const Text('Delete'),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _AdaptiveVoiceActions extends StatelessWidget {
  const _AdaptiveVoiceActions({required this.secondary, required this.primary});

  final Widget secondary;
  final Widget primary;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final stack =
          constraints.maxWidth < 360 ||
          MediaQuery.textScalerOf(context).scale(1) > 1.3;
      if (stack) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [secondary, const SizedBox(height: 8), primary],
        );
      }
      return Row(
        children: [
          Expanded(child: secondary),
          const SizedBox(width: 10),
          Expanded(child: primary),
        ],
      );
    },
  );
}

ButtonStyle _voiceButtonStyle() =>
    const ButtonStyle(minimumSize: WidgetStatePropertyAll(Size(48, 48)));

Future<String?> showVoiceComposerSheet(
  BuildContext context,
  VoiceComposerController controller,
) => showModalBottomSheet<String>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: true,
  isDismissible: false,
  enableDrag: false,
  builder: (context) => _VoiceComposerSheet(controller: controller),
);

class _VoiceComposerSheet extends StatefulWidget {
  const _VoiceComposerSheet({required this.controller});

  final VoiceComposerController controller;

  @override
  State<_VoiceComposerSheet> createState() => _VoiceComposerSheetState();
}

class _VoiceComposerSheetState extends State<_VoiceComposerSheet> {
  final TextEditingController _draft = TextEditingController();
  String _syncedDraft = '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(widget.controller.startListening());
    });
  }

  Future<void> _close() async {
    await widget.controller.cancel();
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_close());
      },
      child: AnimatedBuilder(
        animation: widget.controller,
        builder: (context, _) {
          final controller = widget.controller;
          if (controller.state == VoiceComposerState.draft &&
              controller.draft != _syncedDraft) {
            _syncedDraft = controller.draft;
            _draft.value = TextEditingValue(
              text: controller.draft,
              selection: TextSelection.collapsed(
                offset: controller.draft.length,
              ),
            );
          }
          return Padding(
            padding: EdgeInsets.fromLTRB(
              20,
              0,
              20,
              16 + MediaQuery.viewInsetsOf(context).bottom,
            ),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _VoiceStatus(controller: controller),
                  const SizedBox(height: 16),
                  if (controller.state == VoiceComposerState.draft)
                    TextField(
                      key: const Key('voice-draft-field'),
                      controller: _draft,
                      minLines: 3,
                      maxLines: 8,
                      autofocus: true,
                      decoration: const InputDecoration(
                        labelText: 'Review transcript',
                        helperText:
                            'Edit before inserting. Voice input never sends automatically.',
                        alignLabelWithHint: true,
                        border: OutlineInputBorder(),
                      ),
                    ),
                  if (controller.state == VoiceComposerState.error) ...[
                    Semantics(
                      liveRegion: true,
                      child: Text(
                        '${controller.error}',
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    if (controller.error is VoicePermissionDenied &&
                        (controller.error! as VoicePermissionDenied).permanent)
                      OutlinedButton.icon(
                        style: _voiceButtonStyle(),
                        onPressed: voiceDevicePlatform.openAppSettings,
                        icon: const Icon(Icons.settings_outlined),
                        label: const Text('Open app settings'),
                      ),
                    FilledButton.icon(
                      style: _voiceButtonStyle(),
                      onPressed: controller.startListening,
                      icon: const Icon(Icons.refresh_rounded),
                      label: const Text('Try again'),
                    ),
                  ],
                  if (controller.state == VoiceComposerState.idle) ...[
                    FilledButton.icon(
                      style: _voiceButtonStyle(),
                      onPressed: controller.startListening,
                      icon: const Icon(Icons.mic_rounded),
                      label: const Text('Start listening'),
                    ),
                  ],
                  const SizedBox(height: 16),
                  if (controller.state == VoiceComposerState.draft)
                    _AdaptiveVoiceActions(
                      secondary: OutlinedButton(
                        key: const Key('voice-composer-cancel'),
                        style: _voiceButtonStyle(),
                        onPressed: _close,
                        child: const Text('Cancel'),
                      ),
                      primary: FilledButton.icon(
                        key: const Key('insert-voice-draft'),
                        style: _voiceButtonStyle(),
                        onPressed: () async {
                          final text = _draft.text.trim();
                          if (text.isEmpty) return;
                          await controller.cancel();
                          if (context.mounted) Navigator.pop(context, text);
                        },
                        icon: const Icon(Icons.add_rounded),
                        label: const Text('Insert'),
                      ),
                    )
                  else
                    OutlinedButton(
                      key: const Key('voice-composer-cancel'),
                      style: _voiceButtonStyle(),
                      onPressed: _close,
                      child: const Text('Cancel'),
                    ),
                  const SizedBox(height: 8),
                  TextButton.icon(
                    style: _voiceButtonStyle(),
                    onPressed:
                        controller.state == VoiceComposerState.listening ||
                            controller.state ==
                                VoiceComposerState.initializing ||
                            controller.state == VoiceComposerState.loading ||
                            controller.state ==
                                VoiceComposerState.finishingCancellation ||
                            controller.state == VoiceComposerState.transcribing
                        ? null
                        : () async {
                            final ready = await showVoiceModelSetupSheet(
                              context,
                              controller.models,
                            );
                            if (ready && context.mounted) setState(() {});
                          },
                    icon: const Icon(Icons.tune_rounded),
                    label: Text(
                      '${controller.models.selectedPack.label} · ${controller.models.language.label}',
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  @override
  void dispose() {
    _draft.dispose();
    super.dispose();
  }
}

class _VoiceStatus extends StatelessWidget {
  const _VoiceStatus({required this.controller});

  final VoiceComposerController controller;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final listening = controller.state == VoiceComposerState.listening;
    final status = switch (controller.state) {
      VoiceComposerState.listening =>
        'Listening ${_formatElapsed(controller.elapsed)} of 0:30',
      VoiceComposerState.initializing => 'Starting microphone…',
      VoiceComposerState.loading => 'Loading local model…',
      VoiceComposerState.transcribing => 'Transcribing on this device…',
      VoiceComposerState.finishingCancellation =>
        'Finishing canceled transcription…',
      VoiceComposerState.draft => 'Transcript ready to review',
      VoiceComposerState.error => 'Voice input needs attention',
      VoiceComposerState.idle => 'Ready for local voice input',
      VoiceComposerState.modelRequired => 'A local model is required',
      VoiceComposerState.downloading => 'Downloading voice model…',
      VoiceComposerState.verifying => 'Verifying voice model…',
    };
    return Column(
      children: [
        Container(
          width: 76,
          height: 76,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: listening
                ? theme.colorScheme.errorContainer
                : theme.colorScheme.primaryContainer,
            border: Border.all(
              color: listening
                  ? theme.colorScheme.error
                  : theme.colorScheme.primary,
              width: 2,
            ),
          ),
          child: Icon(
            listening ? Icons.mic_rounded : Icons.graphic_eq_rounded,
            size: 34,
            color: listening
                ? theme.colorScheme.error
                : theme.colorScheme.primary,
          ),
        ),
        const SizedBox(height: 12),
        Semantics(
          liveRegion: true,
          label: listening
              ? 'Listening. Double tap Stop recording when done.'
              : status,
          excludeSemantics: true,
          child: Text(
            status,
            style: theme.textTheme.titleMedium,
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: 4),
        const Text('Audio stays on this device', textAlign: TextAlign.center),
        if (listening) ...[
          const SizedBox(height: 14),
          _LevelMeter(level: controller.level),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              key: const Key('stop-voice-recording'),
              style: FilledButton.styleFrom(
                backgroundColor: theme.colorScheme.error,
                foregroundColor: theme.colorScheme.onError,
                minimumSize: const Size(48, 48),
              ),
              onPressed: controller.stopListening,
              icon: const Icon(AppIcons.stop),
              label: const Text('Stop recording'),
            ),
          ),
        ],
        if (controller.state == VoiceComposerState.initializing ||
            controller.state == VoiceComposerState.loading ||
            controller.state == VoiceComposerState.finishingCancellation ||
            controller.state == VoiceComposerState.transcribing)
          const Padding(
            padding: EdgeInsets.only(top: 14),
            child: LinearProgressIndicator(),
          ),
      ],
    );
  }
}

class _LevelMeter extends StatelessWidget {
  const _LevelMeter({required this.level});

  final double level;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.error;
    return ExcludeSemantics(
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(9, (index) {
          final threshold = (index + 1) / 12;
          return Container(
            width: 6,
            height: 12.0 + (index - 4).abs() * 2,
            margin: const EdgeInsets.symmetric(horizontal: 2),
            decoration: BoxDecoration(
              color: level >= threshold ? color : color.withValues(alpha: .2),
              borderRadius: BorderRadius.circular(4),
            ),
          );
        }),
      ),
    );
  }
}

String _formatElapsed(Duration duration) {
  final seconds = duration.inSeconds.clamp(0, 30);
  return '0:${seconds.toString().padLeft(2, '0')}';
}
