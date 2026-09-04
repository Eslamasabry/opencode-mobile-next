part of '../chat_screen.dart';

/// Only text from this conversation is offered. Reuse never resends a
/// message or silently copies attachments from an earlier request.
class _PromptHistorySheet extends StatefulWidget {
  const _PromptHistorySheet({required this.prompts});
  final List<String> prompts;

  @override
  State<_PromptHistorySheet> createState() => _PromptHistorySheetState();
}

class _PromptHistorySheetState extends State<_PromptHistorySheet> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final matches = widget.prompts
        .where((text) => text.toLowerCase().contains(_query))
        .toList();
    return Padding(
      padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: math.max(
            160,
            media.size.height * .75 - media.viewInsets.bottom,
          ),
        ),
        child: SafeArea(
          top: false,
          // The heading and search scroll with the results. Keeping them
          // fixed can leave no room for results above a large keyboard.
          child: ListView(
            key: const Key('prompt-history-sheet'),
            shrinkWrap: true,
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(
                  _chatL10n(context).composerReuseTitle,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                child: Text(_chatL10n(context).composerReuseDescription),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: TextField(
                  key: const Key('prompt-history-search'),
                  decoration: InputDecoration(
                    hintText: _chatL10n(context).composerReuseSearch,
                    prefixIcon: const Icon(Icons.search_rounded),
                  ),
                  onChanged: (value) =>
                      setState(() => _query = value.trim().toLowerCase()),
                ),
              ),
              const SizedBox(height: 8),
              if (matches.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(20),
                  child: Text(_chatL10n(context).composerReuseEmpty),
                ),
              for (var index = 0; index < matches.length; index++) ...[
                if (index > 0) const Divider(height: 1, indent: 20),
                ListTile(
                  key: ValueKey('reuse-prompt-$index'),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 8,
                  ),
                  title: Text(
                    matches[index],
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: const Icon(Icons.add_rounded),
                  onTap: () => Navigator.pop(context, matches[index]),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
