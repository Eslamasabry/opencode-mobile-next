import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../app_theme.dart';

/// Rich blocks the agent can emit inside fenced code with a reserved info
/// string. [MarkdownText] recognises the fences `choices`, `checklist` and
/// `command` (case-insensitive) and renders these widgets instead of a code
/// block, so a model can offer tappable options, show progress, or hand the
/// user a command to run locally without any new wire format.
abstract final class AgentBlockKinds {
  static const choices = 'choices';
  static const checklist = 'checklist';
  static const command = 'command';

  static bool matches(String? info) {
    final kind = info?.trim().toLowerCase();
    return kind == choices || kind == checklist || kind == command;
  }
}

/// Carries the current `onChoice` handler down to [AgentChoicesBlock] without
/// baking it into the parsed block widgets, so a fresh closure per rebuild
/// never forces a markdown re-parse during streaming.
class AgentChoiceScope extends InheritedWidget {
  const AgentChoiceScope({
    super.key,
    required this.onChoice,
    required super.child,
  });

  final ValueChanged<String>? onChoice;

  static ValueChanged<String>? maybeOf(BuildContext context) => context
      .dependOnInheritedWidgetOfExactType<AgentChoiceScope>()
      ?.onChoice;

  @override
  bool updateShouldNotify(AgentChoiceScope oldWidget) =>
      onChoice != oldWidget.onChoice;
}

/// ```choices — one option per non-empty line, rendered as tappable outlined
/// cards. Tapping hands the option text to the nearest [AgentChoiceScope];
/// without one the text is copied so the user can paste it into the composer.
class AgentChoicesBlock extends StatelessWidget {
  const AgentChoicesBlock({super.key, required this.options});

  final List<String> options;

  /// Splits the fence body into options, dropping blanks and list markers.
  static List<String> parse(String body) => body
      .split('\n')
      .map((line) => line.trim().replaceFirst(RegExp(r'^(?:[-*+]|\d+[.)])\s+'), ''))
      .where((line) => line.isNotEmpty)
      .toList();

  Future<void> _select(BuildContext context, String option) async {
    final onChoice = AgentChoiceScope.maybeOf(context);
    if (onChoice != null) {
      onChoice(option);
      return;
    }
    await Clipboard.setData(ClipboardData(text: option));
    if (!context.mounted) return;
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      const SnackBar(
        content: Text('Copied. Paste it into the composer'),
        duration: Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (options.isEmpty) return const SizedBox.shrink();
    return Column(
      key: const Key('agent-choices-block'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var index = 0; index < options.length; index++)
          Padding(
            padding: EdgeInsets.only(top: index == 0 ? 0 : 6),
            child: Semantics(
              button: true,
              label: 'Choose: ${options[index]}',
              excludeSemantics: true,
              child: Material(
                color: Colors.transparent,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(AppTheme.radiusControl),
                  side: BorderSide(
                    color: theme.colorScheme.primary.withValues(alpha: .45),
                  ),
                ),
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  key: Key('agent-choice-$index'),
                  onTap: () => _select(context, options[index]),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 48),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 10,
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: Text(
                              options[index],
                              style: theme.textTheme.bodyMedium,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Icon(
                            Icons.arrow_forward_rounded,
                            size: 18,
                            color: theme.colorScheme.primary,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// One line of a ```checklist fence.
class AgentChecklistItem {
  const AgentChecklistItem(this.label, {required this.done});

  final String label;
  final bool done;
}

/// ```checklist — lines starting with `[ ]` or `[x]` render as a read-only
/// checklist. Done items are muted; nothing is struck through so the text
/// stays legible at large text sizes.
class AgentChecklistBlock extends StatelessWidget {
  const AgentChecklistBlock({super.key, required this.items});

  final List<AgentChecklistItem> items;

  static final _itemPattern = RegExp(
    r'^(?:[-*+]\s+)?\[([ xX])\]\s*(.*)$',
  );

  static List<AgentChecklistItem> parse(String body) {
    final items = <AgentChecklistItem>[];
    for (final raw in body.split('\n')) {
      final line = raw.trim();
      if (line.isEmpty) continue;
      final match = _itemPattern.firstMatch(line);
      if (match == null) {
        items.add(AgentChecklistItem(line, done: false));
        continue;
      }
      items.add(
        AgentChecklistItem(
          match.group(2)!.trim(),
          done: match.group(1)!.toLowerCase() == 'x',
        ),
      );
    }
    return items;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      key: const Key('agent-checklist-block'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final item in items)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 3),
            child: Semantics(
              label: '${item.done ? 'Done' : 'To do'}: ${item.label}',
              excludeSemantics: true,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(top: 1),
                    child: Icon(
                      item.done
                          ? Icons.check_circle_rounded
                          : Icons.radio_button_unchecked_rounded,
                      key: Key(
                        item.done ? 'agent-check-done' : 'agent-check-open',
                      ),
                      size: 18,
                      color: item.done
                          ? AppTheme.successOf(theme)
                          : muted,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      item.label,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: item.done ? muted : null,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// ```command — each line is a shell command for the user to run on their
/// own machine, in mono with a 48dp copy button. Headed "Run on your
/// computer" so it never reads as something the agent already ran.
class AgentCommandBlock extends StatelessWidget {
  const AgentCommandBlock({super.key, required this.commands});

  final List<String> commands;

  static List<String> parse(String body) => body
      .split('\n')
      .map((line) => line.trimRight())
      .where((line) => line.trim().isNotEmpty)
      .toList();

  Future<void> _copy(BuildContext context, String command) async {
    await Clipboard.setData(ClipboardData(text: command));
    if (!context.mounted) return;
    ScaffoldMessenger.maybeOf(context)?.showSnackBar(
      const SnackBar(
        content: Text('Command copied'),
        duration: Duration(seconds: 1),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    if (commands.isEmpty) return const SizedBox.shrink();
    return Container(
      key: const Key('agent-command-block'),
      width: double.infinity,
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .45)
            : Colors.black.withValues(alpha: .05),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppTheme.hairline(theme)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 2),
            child: Row(
              children: [
                Icon(Icons.terminal_rounded, size: 14, color: muted),
                const SizedBox(width: 6),
                Text(
                  'Run on your computer',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: muted,
                    letterSpacing: .3,
                  ),
                ),
              ],
            ),
          ),
          for (var index = 0; index < commands.length; index++)
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 0, 4, 0),
              child: Row(
                children: [
                  Expanded(
                    child: SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: SelectableText(
                        commands[index],
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontFamily: AppTheme.monoFamily,
                          height: 1.45,
                        ),
                      ),
                    ),
                  ),
                  IconButton(
                    key: Key('agent-command-copy-$index'),
                    tooltip: 'Copy command',
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                    iconSize: 18,
                    icon: Icon(AppIcons.copy, color: muted),
                    onPressed: () => _copy(context, commands[index]),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 4),
        ],
      ),
    );
  }
}
