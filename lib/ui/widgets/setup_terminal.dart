import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';

/// A readable setup log. Copying is delegated to the caller so display-only
/// cleanup never changes the original diagnostic output.
class SetupTerminal extends StatelessWidget {
  final String output;
  final bool running;
  final ScrollController controller;
  final VoidCallback? onCopy;
  final String copyTooltip;
  final bool expand;

  const SetupTerminal({
    super.key,
    required this.output,
    required this.running,
    required this.controller,
    this.onCopy,
    this.copyTooltip = 'Copy output',
    this.expand = false,
  });

  // CSI styling/cursor sequences and OSC titles/hyperlinks have no meaning in
  // selectable text. Remove their control bytes, retaining the visible text.
  static final _ansi = RegExp(
    r'\x1B\][^\x07\x1B]*(?:\x07|\x1B\\)|\x1B\[[0-?]*[ -/]*[@-~]|\x1B[@-_]',
  );
  static final _error = RegExp(
    r'^(?:(?:npm|bun)\s+)?(?:err!?\b|error\b|fatal\b|failed\b)|^status error\b',
    caseSensitive: false,
  );
  static final _warning = RegExp(
    r'^(?:(?:npm|bun)\s+)?warn(?:ing)?\b|\bretrying\b',
    caseSensitive: false,
  );
  static final _success = RegExp(
    r'^(?:authenticated server ready\b|setup complete\b|installation complete\b)',
    caseSensitive: false,
  );

  List<TextSpan> _lines(String text, ThemeData theme) {
    final lines = text.split('\n');
    return [
      for (var i = 0; i < lines.length; i++)
        TextSpan(
          text: '${lines[i]}${i < lines.length - 1 ? '\n' : ''}',
          style: _lineStyle(lines[i], theme),
        ),
    ];
  }

  TextStyle? _lineStyle(String line, ThemeData theme) {
    final trimmed = line.trimLeft();
    final stage = trimmed.startsWith('[oc]');
    final message = stage ? trimmed.substring(4).trimLeft() : trimmed;
    final scheme = theme.colorScheme;
    if (_error.hasMatch(message)) {
      return TextStyle(color: scheme.error, fontWeight: FontWeight.w600);
    }
    if (_warning.hasMatch(message)) {
      return TextStyle(
        color: AppTheme.statusColor(theme, AppStatusTone.attention),
        fontWeight: FontWeight.w600,
      );
    }
    if (stage && _success.hasMatch(message)) {
      return TextStyle(
        color: AppTheme.successOf(theme),
        fontWeight: FontWeight.w600,
      );
    }
    return stage
        ? TextStyle(color: scheme.primary, fontWeight: FontWeight.w600)
        : null;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final visibleOutput = output.replaceAll(_ansi, '');
    final empty = visibleOutput.trim().isEmpty;

    return SizedBox(
      key: const Key('setup-live-output'),
      height: expand
          ? null
          : (MediaQuery.sizeOf(context).height * .42).clamp(240.0, 420.0),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.only(
              left: 16,
              right: 4,
              top: 4,
              bottom: 4,
            ),
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(color: scheme.outlineVariant)),
            ),
            child: Row(
              children: [
                Icon(
                  running ? Icons.graphic_eq_rounded : Icons.subject_rounded,
                  size: 20,
                  color: scheme.primary,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    running ? 'LIVE OUTPUT' : 'LAST OUTPUT',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: scheme.primary,
                      fontWeight: FontWeight.w700,
                      letterSpacing: .8,
                    ),
                  ),
                ),
                const SizedBox(width: 4),
                IconButton(
                  constraints: const BoxConstraints(
                    minWidth: 48,
                    minHeight: 48,
                  ),
                  onPressed: onCopy,
                  tooltip: copyTooltip,
                  icon: const Icon(AppIcons.copy, size: 20),
                  color: scheme.primary,
                ),
              ],
            ),
          ),
          Expanded(
            child: Scrollbar(
              controller: controller,
              thumbVisibility: true,
              child: OwnScrollbar(
                child: SingleChildScrollView(
                  controller: controller,
                  padding: const EdgeInsets.all(16),
                  child: SizedBox(
                    width: double.infinity,
                    child: empty
                        ? Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                AppLocalizations.of(context).setupOutputWaiting,
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: scheme.onSurface,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                AppLocalizations.of(
                                  context,
                                ).setupOutputWaitingDetail,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                  height: 1.5,
                                ),
                              ),
                            ],
                          )
                        : SelectableText.rich(
                            TextSpan(children: _lines(visibleOutput, theme)),
                            style: TextStyle(
                              color: scheme.onSurface,
                              fontFamily: AppTheme.monoFamily,
                              fontSize: 13,
                              height: 1.6,
                            ),
                          ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
