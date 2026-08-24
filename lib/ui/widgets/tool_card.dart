import 'package:flutter/material.dart';

import '../../api/models.dart';

/// Renders a single tool invocation as an expandable card with status,
/// title, input and output.
class ToolCard extends StatefulWidget {
  final String toolName;
  final ToolState state;
  const ToolCard({super.key, required this.toolName, required this.state});

  @override
  State<ToolCard> createState() => _ToolCardState();
}

class _ToolCardState extends State<ToolCard> {
  bool _expanded = false;

  IconData get _icon {
    final n = widget.toolName.toLowerCase();
    if (n.contains('bash') || n.contains('shell') || n.contains('terminal')) {
      return Icons.terminal_rounded;
    }
    if (n.contains('edit') || n.contains('write')) {
      return Icons.edit_note_rounded;
    }
    if (n.contains('read')) return Icons.description_rounded;
    if (n.contains('grep') || n.contains('find')) return Icons.search_rounded;
    if (n.contains('glob')) return Icons.folder_open_rounded;
    if (n.contains('webfetch') || n.contains('fetch') || n.contains('web')) {
      return Icons.public_rounded;
    }
    if (n.contains('todo')) return Icons.checklist_rounded;
    if (n.contains('task') || n.contains('agent')) {
      return Icons.smart_toy_rounded;
    }
    return Icons.build_rounded;
  }

  Color get _statusColor {
    switch (widget.state.status) {
      case 'completed':
        return Colors.green.shade400;
      case 'error':
        return Theme.of(context).colorScheme.error;
      default:
        return Theme.of(context).colorScheme.primary;
    }
  }

  bool get _running =>
      widget.state.status == 'pending' || widget.state.status == 'running';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasBody =
        (widget.state.output?.isNotEmpty ?? false) ||
        (widget.state.inputJson?.isNotEmpty ?? false);
    final title = widget.state.title ?? widget.toolName;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .35),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor.withValues(alpha: .4)),
      ),
      child: Column(
        children: [
          Semantics(
            button: hasBody,
            expanded: hasBody ? _expanded : null,
            label: '$title, ${widget.state.status}',
            child: InkWell(
              onTap: hasBody
                  ? () => setState(() => _expanded = !_expanded)
                  : null,
              borderRadius: BorderRadius.circular(8),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 7, 8, 7),
                  child: Row(
                    children: [
                      Icon(_icon, size: 15, color: theme.hintColor),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall!.copyWith(
                            color: theme.colorScheme.onSurface.withValues(
                              alpha: .85,
                            ),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                      if (_running && !reduceMotion)
                        SizedBox(
                          width: 12,
                          height: 12,
                          child: CircularProgressIndicator(
                            strokeWidth: 1.6,
                            color: theme.colorScheme.primary,
                          ),
                        )
                      else
                        Icon(
                          _running
                              ? Icons.hourglass_top_rounded
                              : widget.state.status == 'error'
                              ? Icons.error_outline_rounded
                              : Icons.check_circle_outline_rounded,
                          size: 14,
                          color: _statusColor,
                        ),
                      if (hasBody) ...[
                        const SizedBox(width: 4),
                        AnimatedRotation(
                          turns: _expanded ? .5 : 0,
                          duration: reduceMotion
                              ? Duration.zero
                              : const Duration(milliseconds: 150),
                          child: Icon(
                            Icons.expand_more_rounded,
                            size: 16,
                            color: theme.hintColor,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (_expanded && hasBody)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (widget.state.inputJson?.isNotEmpty ?? false) ...[
                    Text(
                      'INPUT',
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.hintColor,
                        fontSize: 9,
                      ),
                    ),
                    const SizedBox(height: 2),
                    _Mono(text: widget.state.inputJson!, maxLines: 20),
                  ],
                  if (widget.state.output?.isNotEmpty ?? false) ...[
                    const SizedBox(height: 6),
                    Text(
                      widget.state.status == 'error' ? 'ERROR' : 'OUTPUT',
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.hintColor,
                        fontSize: 9,
                      ),
                    ),
                    const SizedBox(height: 2),
                    _Mono(text: widget.state.output!, maxLines: 200),
                  ],
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _Mono extends StatelessWidget {
  final String text;
  final int maxLines;
  const _Mono({required this.text, this.maxLines = 100});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(8),
      constraints: BoxConstraints(maxHeight: maxLines * 18.0),
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .4)
            : Colors.black.withValues(alpha: .04),
        borderRadius: BorderRadius.circular(6),
      ),
      child: SingleChildScrollView(
        child: SelectableText(
          text.length > 8000 ? '${text.substring(0, 8000)}\n… truncated' : text,
          style: theme.textTheme.bodySmall!.copyWith(
            fontFamily: 'monospace',
            fontSize: 11.5,
          ),
        ),
      ),
    );
  }
}
