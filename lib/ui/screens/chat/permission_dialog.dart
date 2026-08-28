part of '../chat_screen.dart';

class _PermissionDialog extends StatefulWidget {
  final PermissionRequest permission;
  final Future<void> Function(String reply) onReply;

  const _PermissionDialog({required this.permission, required this.onReply});

  @override
  State<_PermissionDialog> createState() => _PermissionDialogState();
}

class _PermissionDialogState extends State<_PermissionDialog> {
  bool _replying = false;
  Object? _error;

  Future<void> _reply(String reply) async {
    if (reply == 'always' && !await _confirmAlways()) return;
    setState(() {
      _replying = true;
      _error = null;
    });
    try {
      await widget.onReply(reply);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _replying = false;
        _error = error;
      });
    }
  }

  Future<bool> _confirmAlways() async {
    final permission = widget.permission;
    final broader = permission.always.isNotEmpty
        ? permission.always
        : permission.patterns;
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            scrollable: true,
            icon: const Icon(Icons.warning_amber_rounded),
            title: const Text('Confirm broader access'),
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Context: ${permission.permission} in this chat'),
                const SizedBox(height: 12),
                const Text('Always allow patterns:'),
                const SizedBox(height: 4),
                SelectableText(
                  broader.isEmpty
                      ? '(all matching requests)'
                      : broader.join('\n'),
                  style: const TextStyle(fontFamily: 'AppMono'),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Consequence: future matching actions can run without asking again for the lifetime of this OpenCode server. Allow once is safer.',
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Keep asking'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Confirm always allow'),
              ),
            ],
          ),
        ) ??
        false;
  }

  @override
  Widget build(BuildContext context) {
    final permission = widget.permission;
    final theme = Theme.of(context);
    final requestedPatterns = permission.patterns.isEmpty
        ? 'OpenCode wants to use ${permission.permission}.'
        : permission.patterns.join('\n');
    return AlertDialog(
      scrollable: true,
      icon: const Icon(Icons.admin_panel_settings_outlined),
      title: Text(permissionRequestTitle(permission.permission)),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Requested for this action', style: theme.textTheme.labelLarge),
          const SizedBox(height: 4),
          SelectableText(requestedPatterns),
          if (permission.always.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(
              'Always allow would also cover',
              style: theme.textTheme.labelLarge,
            ),
            const SizedBox(height: 4),
            SelectableText(permission.always.join('\n')),
          ],
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(
              'Reply failed: $_error',
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: _replying ? null : () => _reply('reject'),
          child: const Text('Reject'),
        ),
        OutlinedButton(
          onPressed: _replying ? null : () => _reply('always'),
          child: const Text('Always allow'),
        ),
        FilledButton(
          onPressed: _replying ? null : () => _reply('once'),
          child: _replying
              ? const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Allow once'),
        ),
      ],
    );
  }
}

