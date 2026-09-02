import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/connection.dart';
import '../app_theme.dart';

/// The two transcript display switches — "Reasoning" and "Timestamps &
/// usage" — named as nouns that describe the setting, not verbs that describe
/// the next click. Each flip writes the preference straight to the connection
/// and updates in place, so the sheet that hosts them stays open and a reader
/// can set both without reopening it. Drop it under a "Transcript" label in
/// any session menu.
class TranscriptDisplayToggles extends StatefulWidget {
  const TranscriptDisplayToggles({
    super.key,
    required this.reasoningExpanded,
    required this.timestampsVisible,
    this.dense = false,
  });

  final bool reasoningExpanded;
  final bool timestampsVisible;
  final bool dense;

  @override
  State<TranscriptDisplayToggles> createState() =>
      _TranscriptDisplayTogglesState();
}

class _TranscriptDisplayTogglesState extends State<TranscriptDisplayToggles> {
  late bool _reasoning = widget.reasoningExpanded;
  late bool _timestamps = widget.timestampsVisible;

  /// The connection the transcript preferences live on; null only in hosts
  /// without a provider scope (isolated widget tests), where the switch still
  /// flips locally.
  ConnectionController? _connection() {
    try {
      return ProviderScope.containerOf(
        context,
        listen: false,
      ).read(connProvider);
    } catch (_) {
      return null;
    }
  }

  Future<void> _setReasoning(bool value) async {
    setState(() => _reasoning = value);
    await _connection()?.setTranscriptReasoningExpanded(value);
  }

  Future<void> _setTimestamps(bool value) async {
    setState(() => _timestamps = value);
    await _connection()?.setTranscriptTimestampsVisible(value);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hint = theme.textTheme.bodySmall?.copyWith(
      color: AppTheme.mutedOf(theme),
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        SwitchListTile(
          key: const ValueKey('session-view-thinking'),
          dense: widget.dense,
          secondary: const Icon(Icons.psychology_alt_outlined),
          title: const Text('Reasoning'),
          subtitle: Text(
            _reasoning
                ? 'Expanded under each answer'
                : 'Collapsed until you tap it',
            style: hint,
          ),
          value: _reasoning,
          onChanged: (value) => unawaited(_setReasoning(value)),
        ),
        SwitchListTile(
          key: const ValueKey('session-view-timestamps'),
          dense: widget.dense,
          secondary: const Icon(Icons.schedule_rounded),
          title: const Text('Timestamps & usage'),
          subtitle: Text(
            _timestamps
                ? 'Time, tokens and cost under each message'
                : 'Hidden to keep the transcript quiet',
            style: hint,
          ),
          value: _timestamps,
          onChanged: (value) => unawaited(_setTimestamps(value)),
        ),
      ],
    );
  }
}
