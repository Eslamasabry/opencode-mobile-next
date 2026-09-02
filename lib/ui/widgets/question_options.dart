import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart' show PendingQuestion, QuestionChoice;
import '../app_theme.dart';

/// True when [question] is too big to answer inline above the composer and
/// the chat should show a compact card whose Answer button opens the full
/// sheet: more than two prompts, or any choice description past ~140
/// characters. Mirrors `formPrefersFullScreen` so the two request surfaces
/// grow the same way.
bool questionPrefersSheet(PendingQuestion question) =>
    question.prompts.length > 2 ||
    question.prompts.any(
      (prompt) =>
          prompt.choices.any((choice) => choice.description.length > 140),
    );

/// One selectable choice of a question prompt, rendered identically by the
/// Activity sheet and the inline chat card: a 48 dp tappable row with the
/// label, the description as a subtitle, a radio or checkbox affordance, and
/// an optional "Recommended" mark.
class QuestionOptionRow extends StatelessWidget {
  const QuestionOptionRow({
    super.key,
    required this.choice,
    required this.selected,
    required this.multiple,
    required this.onTap,
    this.recommended = false,
    this.enabled = true,
  });

  final QuestionChoice choice;
  final bool selected;

  /// Checkbox affordance for multi-select prompts, radio for single-select.
  final bool multiple;
  final VoidCallback? onTap;
  final bool recommended;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final tint = enabled ? scheme.primary : AppTheme.mutedOf(theme);
    final icon = multiple
        ? (selected
              ? Icons.check_box_rounded
              : Icons.check_box_outline_blank_rounded)
        : (selected
              ? Icons.radio_button_checked_rounded
              : Icons.radio_button_unchecked_rounded);
    return Semantics(
      button: true,
      selected: selected,
      enabled: enabled,
      child: InkWell(
        key: ValueKey('question-option-${choice.label}'),
        onTap: enabled ? onTap : null,
        borderRadius: BorderRadius.circular(10),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Icon(
                  icon,
                  size: 22,
                  color: selected ? tint : AppTheme.mutedOf(theme),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        choice.label,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontWeight: selected
                              ? FontWeight.w600
                              : FontWeight.w500,
                        ),
                      ),
                      if (choice.description.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 1),
                          child: Text(
                            choice.description,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: AppTheme.mutedOf(theme),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                if (recommended)
                  Padding(
                    padding: const EdgeInsets.only(left: 8),
                    child: Container(
                      key: ValueKey('question-recommended-${choice.label}'),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 1,
                      ),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                          color: scheme.primary.withValues(alpha: .5),
                        ),
                      ),
                      child: Text(
                        'Recommended',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: scheme.primary,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The free-text answer field shown when a prompt accepts a custom answer;
/// shared so the sheet and the card ask in the same words.
class QuestionCustomAnswerField extends StatelessWidget {
  const QuestionCustomAnswerField({
    super.key,
    required this.controller,
    required this.onChanged,
    this.enabled = true,
    this.maxLines = 3,
    this.onSubmitted,
  });

  final TextEditingController controller;
  final ValueChanged<String> onChanged;
  final bool enabled;
  final int maxLines;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) => TextField(
    controller: controller,
    enabled: enabled,
    minLines: 1,
    maxLines: maxLines,
    onChanged: onChanged,
    onSubmitted: onSubmitted,
    decoration: const InputDecoration(
      labelText: 'Your answer',
      border: OutlineInputBorder(),
    ),
  );
}
