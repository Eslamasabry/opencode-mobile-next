import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/connection.dart';
import '../../state/profiles.dart';

/// Manage opencode server profiles and connect.
class ServersScreen extends ConsumerStatefulWidget {
  const ServersScreen({super.key});

  @override
  ConsumerState<ServersScreen> createState() => _ServersScreenState();
}

class _ServersScreenState extends ConsumerState<ServersScreen> {
  bool _busy = false;

  Future<void> _connect(ServerProfile p) async {
    if (_busy) return;
    setState(() => _busy = true);
    await ref.read(connProvider).connect(p);
    if (!mounted) return;
    setState(() => _busy = false);
    final conn = ref.read(connProvider);
    if (conn.api != null && mounted) {
      Navigator.of(context).pushReplacementNamed('/home');
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(conn.lastError ?? 'Connection failed'),
          backgroundColor: Theme.of(context).colorScheme.error));
    }
  }

  Future<void> _edit([ServerProfile? existing]) async {
    final result = await showDialog<ServerProfile>(
      context: context,
      builder: (_) => _ProfileDialog(existing: existing),
    );
    if (result == null) return;
    await ref.read(bootstrapProvider).value!.store.upsert(result);
    await ref.read(bootstrapProvider).value!.store.load();
    // refresh store reference inside controller
    if (mounted) setState(() {});
  }

  Future<void> _delete(ServerProfile p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Remove ${p.name}?'),
        content: const Text('The saved server will be removed from this device.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          FilledButton(
              style: FilledButton.styleFrom(
                  backgroundColor: Theme.of(ctx).colorScheme.error),
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Remove')),
        ],
      ),
    );
    if (ok != true) return;
    await ref.read(bootstrapProvider).value!.store.remove(p.id);
    await ref.read(bootstrapProvider).value!.store.load();
    if (mounted) setState(() {});
  }

  void _addPreset(String name, String url, String hint) async {
    final profiles = ref.read(bootstrapProvider).value!.store.profiles;
    if (profiles.any((p) => p.baseUrl == url)) {
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$name preset already exists')));
      return;
    }
    final preset = ServerProfile(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      name: name,
      baseUrl: url,
    );
    await ref.read(bootstrapProvider).value!.store.upsert(preset);
    await ref.read(bootstrapProvider).value!.store.load();
    if (mounted) {
      setState(() {});
      _showHint(hint);
    }
  }

  void _showHint(String hint) {
    showModalBottomSheet(
      context: context,
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment:
            CrossAxisAlignment.start, children: [
          Text('Start the server', style: Theme.of(ctx).textTheme.titleMedium),
          const SizedBox(height: 8),
          Text(hint, style: Theme.of(ctx).textTheme.bodySmall),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final bootstrap = ref.watch(bootstrapProvider);
    return Scaffold(
      appBar: AppBar(
        title: Row(children: [
          Icon(Icons.terminal_rounded, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 10),
          const Text('OpenCode'),
        ]),
        actions: [
          IconButton(
            tooltip: 'Setup guide',
            icon: const Icon(Icons.help_outline_rounded),
            onPressed: () => Navigator.pushNamed(context, '/guide'),
          ),
        ],
      ),
      body: bootstrap.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('$e')),
        data: (b) {
          final store = b.store;
          final activeId = store.activeId;
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            children: [
              Text('SERVERS',
                  style: Theme.of(context).textTheme.labelSmall!.copyWith(
                      color: Theme.of(context).hintColor, letterSpacing: 1)),
              const SizedBox(height: 6),
              for (final p in store.profiles)
                Card.filled(
                  margin: const EdgeInsets.symmetric(vertical: 4),
                  color: p.id == activeId
                      ? Theme.of(context).colorScheme.primaryContainer.withValues(alpha: .35)
                      : null,
                  child: ListTile(
                    onTap: () => _connect(p),
                    onLongPress: () => _delete(p),
                    leading: CircleAvatar(
                      backgroundColor: p.id == activeId
                          ? Theme.of(context).colorScheme.primary
                          : Theme.of(context).colorScheme.surfaceContainerHighest,
                      child: Icon(
                        p.baseUrl.contains('127.0.0.1') || p.baseUrl.contains('localhost')
                            ? Icons.smartphone_rounded
                            : Icons.dns_rounded,
                        size: 18,
                        color: p.id == activeId
                            ? Theme.of(context).colorScheme.onPrimary
                            : Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                    title: Text(p.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(p.baseUrl,
                        maxLines: 1, overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
                    trailing: PopupMenuButton<String>(
                      onSelected: (v) {
                        if (v == 'edit') _edit(p);
                        if (v == 'del') _delete(p);
                        if (v == 'conn') _connect(p);
                      },
                      itemBuilder: (_) => const [
                        PopupMenuItem(value: 'conn', child: Text('Connect')),
                        PopupMenuItem(value: 'edit', child: Text('Edit')),
                        PopupMenuItem(value: 'del', child: Text('Remove')),
                      ],
                    ),
                  ),
                ),
              if (store.profiles.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 32),
                  child: Column(children: [
                    Icon(Icons.cloud_off_rounded,
                        size: 42, color: Theme.of(context).hintColor),
                    const SizedBox(height: 12),
                    Text('No servers yet',
                        style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 4),
                    Text('Add a remote host or the on-device Termux preset.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodySmall!
                            .copyWith(color: Theme.of(context).hintColor)),
                  ]),
                ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _busy ? null : () => _edit(),
                icon: const Icon(Icons.add_rounded),
                label: const Text('Add server'),
              ),
              const SizedBox(height: 24),
              Text('QUICK ADD',
                  style: Theme.of(context).textTheme.labelSmall!.copyWith(
                      color: Theme.of(context).hintColor, letterSpacing: 1)),
              const SizedBox(height: 6),
              Card.filled(
                margin: const EdgeInsets.symmetric(vertical: 4),
                child: ListTile(
                  onTap: () => Navigator.pushNamed(context, '/termux-setup'),
                  leading: const Icon(Icons.smartphone_rounded),
                  title: const Text('On-device (Termux)'),
                  subtitle: const Text('Guided setup — the app drives Termux for you'),
                ),
              ),
              Card.filled(
                margin: const EdgeInsets.symmetric(vertical: 4),
                child: ListTile(
                  onTap: () => _addPreset(
                    'Remote dev box',
                    'http://192.168.1.100:4096',
                    'On your machine run:\n\nOPENCODE_SERVER_PASSWORD=secret '
                        'opencode serve --hostname 0.0.0.0 --port 4096',
                  ),
                  leading: const Icon(Icons.dns_rounded),
                  title: const Text('Remote machine (LAN)'),
                  subtitle: const Text('opencode serve --hostname 0.0.0.0'),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _ProfileDialog extends StatefulWidget {
  final ServerProfile? existing;
  const _ProfileDialog({this.existing});

  @override
  State<_ProfileDialog> createState() => _ProfileDialogState();
}

class _ProfileDialogState extends State<_ProfileDialog> {
  late final TextEditingController _name =
      TextEditingController(text: widget.existing?.name ?? '');
  late final TextEditingController _url = TextEditingController(
      text: widget.existing?.baseUrl ?? 'http://');
  late final TextEditingController _user =
      TextEditingController(text: widget.existing?.username ?? '');
  late final TextEditingController _pass =
      TextEditingController(text: widget.existing?.password ?? '');

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.existing == null ? 'Add server' : 'Edit server'),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(
            controller: _name,
            decoration: const InputDecoration(labelText: 'Name'),
            autofocus: widget.existing == null,
          ),
          TextField(
            controller: _url,
            keyboardType: TextInputType.url,
            decoration: const InputDecoration(labelText: 'Server URL',
                hintText: 'http://host:4096'),
          ),
          TextField(
            controller: _user,
            decoration: const InputDecoration(labelText: 'Username (optional)',
                hintText: 'opencode'),
          ),
          TextField(
            controller: _pass,
            obscureText: true,
            decoration: const InputDecoration(labelText: 'Password (optional)',
                helperText: 'Set OPENCODE_SERVER_PASSWORD on the server'),
          ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        FilledButton(
          onPressed: () {
            var url = _url.text.trim();
            if (!url.startsWith('http://') && !url.startsWith('https://')) {
              url = 'http://$url';
            }
            if (Uri.tryParse(url)?.host.isEmpty ?? true) return;
            Navigator.pop(
              context,
              ServerProfile(
                id: widget.existing?.id ??
                    DateTime.now().microsecondsSinceEpoch.toString(),
                name: _name.text.trim().isEmpty ? Uri.parse(url).host : _name.text.trim(),
                baseUrl: url.endsWith('/') ? url.substring(0, url.length - 1) : url,
                username: _user.text.trim(),
                password: _pass.text,
              ),
            );
          },
          child: const Text('Save'),
        ),
      ],
    );
  }
}
