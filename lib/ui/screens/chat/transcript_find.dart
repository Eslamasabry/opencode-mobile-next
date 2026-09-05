part of '../chat_screen.dart';

class _TranscriptFindBar extends StatelessWidget {
  const _TranscriptFindBar({
    required this.controller,
    required this.focusNode,
    required this.count,
    required this.current,
    required this.hasOlder,
    required this.loading,
    required this.onChanged,
    required this.onNext,
    required this.onPrevious,
    required this.onClose,
    required this.onLoadOlder,
    required this.needsReload,
    required this.searchingAll,
    required this.onCancelLoading,
    this.error,
  });
  final TextEditingController controller;
  final FocusNode focusNode;
  final int count;
  final int current;
  final bool hasOlder;
  final bool loading;
  final bool needsReload;
  final bool searchingAll;
  final VoidCallback onCancelLoading;
  final Object? error;
  final ValueChanged<String> onChanged;
  final VoidCallback onNext;
  final VoidCallback onPrevious;
  final VoidCallback onClose;
  final VoidCallback onLoadOlder;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    return Material(
      key: const ValueKey('transcript-find-bar'),
      color: Theme.of(context).colorScheme.surfaceContainer,
      child: CallbackShortcuts(
        bindings: {const SingleActivator(LogicalKeyboardKey.escape): onClose},
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      key: const ValueKey('transcript-find-input'),
                      controller: controller,
                      focusNode: focusNode,
                      onChanged: onChanged,
                      onSubmitted: (_) => onNext(),
                      textInputAction: TextInputAction.search,
                      decoration: InputDecoration(
                        hintText: l10n.transcriptFindHint,
                        prefixIcon: const Icon(Icons.search_rounded),
                        isDense: true,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: onClose,
                    tooltip: l10n.transcriptFindClose,
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
              Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 4,
                children: [
                  Semantics(
                    liveRegion: true,
                    child: Text(
                      controller.text.trim().isEmpty
                          ? l10n.transcriptFindScope
                          : count == 0
                          ? l10n.transcriptFindNone
                          : l10n.transcriptFindCount(current + 1, count),
                    ),
                  ),
                  IconButton(
                    key: const ValueKey('transcript-find-previous'),
                    onPressed: count == 0 ? null : onPrevious,
                    tooltip: l10n.transcriptFindPrevious,
                    icon: const Icon(Icons.keyboard_arrow_up_rounded),
                  ),
                  IconButton(
                    key: const ValueKey('transcript-find-next'),
                    onPressed: count == 0 ? null : onNext,
                    tooltip: l10n.transcriptFindNext,
                    icon: const Icon(Icons.keyboard_arrow_down_rounded),
                  ),
                  if (searchingAll)
                    TextButton(
                      onPressed: onCancelLoading,
                      child: Text(
                        MaterialLocalizations.of(context).cancelButtonLabel,
                      ),
                    )
                  else if (hasOlder || error != null)
                    TextButton(
                      key: const ValueKey('transcript-find-older'),
                      onPressed: loading ? null : onLoadOlder,
                      child: Text(
                        needsReload
                            ? l10n.historyReload
                            : l10n.transcriptFindAll,
                      ),
                    ),
                ],
              ),
              if (loading) const LinearProgressIndicator(minHeight: 2),
              Text(
                error != null
                    ? productErrorText(error!)
                    : hasOlder
                    ? l10n.transcriptFindPartial
                    : l10n.transcriptFindComplete,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: error != null
                      ? Theme.of(context).colorScheme.error
                      : null,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
