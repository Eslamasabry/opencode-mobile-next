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

  void _showFailure(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
  }

  Future<void> _connect(ServerProfile p) async {
    if (_busy) return;
    if (p.requiresPasswordReentry) {
      await _edit(p);
      return;
    }
    setState(() => _busy = true);
    final conn = ref.read(connProvider);
    Object? failure;
    try {
      await conn.connect(p);
    } catch (error) {
      failure = error;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (!mounted) return;
    if (conn.api != null && failure == null) {
      Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
    } else {
      _showFailure(
        '${conn.lastError ?? failure?.toString() ?? 'Connection failed'} '
        'Check the server address and credentials, then try again.',
      );
    }
  }

  Future<void> _edit([ServerProfile? existing]) async {
    final result = await showDialog<ServerProfile>(
      context: context,
      builder: (_) => _ProfileDialog(existing: existing),
    );
    if (result == null || !mounted) return;
    final store = ref.read(bootstrapProvider).store;
    final wasActive = store.activeId == result.id;
    var saved = false;
    setState(() => _busy = true);
    try {
      await store.upsert(result);
      saved = true;
      if (wasActive) {
        final savedProfile = store.profiles.firstWhere(
          (profile) => profile.id == result.id,
        );
        final conn = ref.read(connProvider);
        await conn.connect(savedProfile);
        if (conn.api == null) {
          throw StateError(conn.lastError ?? 'The server did not connect.');
        }
      }
    } catch (error) {
      if (saved) {
        _showFailure(
          '${result.name} was saved, but it could not reconnect. Check the '
          'server address and credentials, then try again. ($error)',
        );
      } else {
        _showFailure(
          'Could not save ${result.name}. The existing profile was left '
          'unchanged. Check device storage and try again. ($error)',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _delete(ServerProfile p) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Remove ${p.name}?'),
        content: const Text(
          'The saved server will be removed from this device.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(ctx).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final store = ref.read(bootstrapProvider).store;
    final wasActive = store.activeId == p.id;
    var removed = false;
    setState(() => _busy = true);
    try {
      // Remove the durable profile first. A failed delete must not tear down a
      // still-saved active connection.
      await store.remove(p.id);
      removed = true;
      if (wasActive) {
        await ref.read(connProvider).disconnect(keepActive: true);
      }
    } catch (error) {
      if (removed) {
        _showFailure(
          '${p.name} was removed, but its connection could not be closed '
          'cleanly. Restart the app before connecting elsewhere. ($error)',
        );
      } else {
        _showFailure(
          'Could not remove ${p.name}. The saved profile and current '
          'connection were kept. Check device storage and try again. ($error)',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final bootstrap = ref.watch(bootstrapProvider);
    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Icon(
              Icons.terminal_rounded,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 10),
            const Text('OpenCode'),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'About and open source notices',
            icon: const Icon(Icons.info_outline_rounded),
            onPressed: () => Navigator.pushNamed(context, '/about'),
          ),
          IconButton(
            tooltip: 'Setup guide',
            icon: const Icon(Icons.help_outline_rounded),
            onPressed: () => Navigator.pushNamed(context, '/guide'),
          ),
        ],
      ),
      body: Builder(
        builder: (context) {
          final store = bootstrap.store;
          final activeId = store.activeId;
          final needsPassword = store.profiles.any(
            (profile) =>
                profile.id == activeId && profile.requiresPasswordReentry,
          );
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
            children: [
              Text(
                'SERVERS',
                style: Theme.of(context).textTheme.labelSmall!.copyWith(
                  color: Theme.of(context).hintColor,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(height: 6),
              if (needsPassword) ...[
                Semantics(
                  container: true,
                  liveRegion: true,
                  excludeSemantics: true,
                  label:
                      'Password re-entry required for the active server. Edit the server and save its password before connecting.',
                  child: Container(
                    key: const Key('password-reentry-banner'),
                    margin: const EdgeInsets.only(bottom: 10),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.lock_reset_rounded,
                          color: Theme.of(context).colorScheme.onErrorContainer,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'A saved password can no longer be read. Edit the active server and re-enter its password before connecting.',
                            style: TextStyle(
                              color: Theme.of(
                                context,
                              ).colorScheme.onErrorContainer,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
              if (_busy)
                Semantics(
                  label: 'Server operation in progress',
                  child: const Padding(
                    padding: EdgeInsets.symmetric(vertical: 8),
                    child: LinearProgressIndicator(
                      key: Key('server-operation-progress'),
                    ),
                  ),
                ),
              for (final p in store.profiles)
                Card.filled(
                  margin: const EdgeInsets.symmetric(vertical: 4),
                  color: p.id == activeId
                      ? Theme.of(
                          context,
                        ).colorScheme.primaryContainer.withValues(alpha: .35)
                      : null,
                  child: ListTile(
                    enabled: !_busy,
                    onTap: _busy ? null : () => _connect(p),
                    onLongPress: _busy ? null : () => _delete(p),
                    isThreeLine: p.requiresPasswordReentry,
                    leading: CircleAvatar(
                      backgroundColor: p.id == activeId
                          ? Theme.of(context).colorScheme.primary
                          : Theme.of(
                              context,
                            ).colorScheme.surfaceContainerHighest,
                      child: Icon(
                        p.baseUrl.contains('127.0.0.1') ||
                                p.baseUrl.contains('localhost')
                            ? Icons.smartphone_rounded
                            : Icons.dns_rounded,
                        size: 18,
                        color: p.id == activeId
                            ? Theme.of(context).colorScheme.onPrimary
                            : Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                    title: Text(
                      p.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          p.baseUrl,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 11,
                          ),
                        ),
                        if (p.requiresPasswordReentry)
                          Text(
                            'Password re-entry required',
                            key: ValueKey('password-reentry-${p.id}'),
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                      ],
                    ),
                    trailing: PopupMenuButton<String>(
                      enabled: !_busy,
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
                  child: Column(
                    children: [
                      Icon(
                        Icons.cloud_off_rounded,
                        size: 42,
                        color: Theme.of(context).hintColor,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'No servers yet',
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Add a remote host or the on-device Termux preset.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodySmall!.copyWith(
                          color: Theme.of(context).hintColor,
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: _busy ? null : () => _edit(),
                icon: const Icon(Icons.add_rounded),
                label: const Text('Add server'),
              ),
              const SizedBox(height: 24),
              Text(
                'QUICK ADD',
                style: Theme.of(context).textTheme.labelSmall!.copyWith(
                  color: Theme.of(context).hintColor,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(height: 6),
              Card.filled(
                margin: const EdgeInsets.symmetric(vertical: 4),
                child: ListTile(
                  onTap: () => Navigator.pushNamed(context, '/termux-setup'),
                  leading: const Icon(Icons.smartphone_rounded),
                  title: const Text('On-device (Termux)'),
                  subtitle: const Text(
                    'Guided setup — the app drives Termux for you',
                  ),
                ),
              ),
              Card.filled(
                margin: const EdgeInsets.symmetric(vertical: 4),
                child: ListTile(
                  onTap: _busy ? null : () => _edit(),
                  leading: const Icon(Icons.dns_rounded),
                  title: const Text('Remote machine (LAN)'),
                  subtitle: const Text('HTTPS URL or secure loopback tunnel'),
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
  late final TextEditingController _name = TextEditingController(
    text: widget.existing?.name ?? '',
  );
  late final TextEditingController _url = TextEditingController(
    text: widget.existing?.baseUrl ?? 'https://',
  );
  late final TextEditingController _user = TextEditingController(
    text: widget.existing?.username ?? '',
  );
  late final TextEditingController _pass = TextEditingController(
    text: widget.existing?.password ?? '',
  );
  String? _error;
  bool get _needsPassword => widget.existing?.requiresPasswordReentry ?? false;

  @override
  void dispose() {
    _name.dispose();
    _url.dispose();
    _user.dispose();
    _pass.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(
        widget.existing == null
            ? 'Add server'
            : _needsPassword
            ? 'Re-enter password'
            : 'Edit server',
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_needsPassword) ...[
              Semantics(
                container: true,
                excludeSemantics: true,
                label:
                    'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires a password.',
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.errorContainer,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires one.',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.onErrorContainer,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
            ],
            TextField(
              controller: _name,
              decoration: const InputDecoration(labelText: 'Name'),
              autofocus: widget.existing == null,
            ),
            TextField(
              controller: _url,
              keyboardType: TextInputType.url,
              onChanged: (_) => setState(() => _error = null),
              decoration: InputDecoration(
                labelText: 'Server URL',
                hintText: 'https://host:4096',
                errorText: _error,
                helperText:
                    'HTTP works only with localhost or 127.0.0.1 for Termux.',
                helperMaxLines: 2,
              ),
            ),
            TextField(
              controller: _user,
              onChanged: (_) => setState(() => _error = null),
              decoration: const InputDecoration(
                labelText: 'Username (optional)',
                hintText: 'opencode',
              ),
            ),
            TextField(
              controller: _pass,
              autofocus: _needsPassword,
              onChanged: (_) => setState(() => _error = null),
              obscureText: true,
              decoration: InputDecoration(
                labelText: _needsPassword
                    ? 'Re-enter password'
                    : 'Password (optional)',
                helperText: _needsPassword
                    ? 'Saving an empty value confirms this server no longer uses a password.'
                    : 'Set OPENCODE_SERVER_PASSWORD on the server',
                helperMaxLines: 2,
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () {
            var url = _url.text.trim();
            final error = validateServerProfileUrl(
              url,
              username: _user.text,
              password: _pass.text,
            );
            if (error != null) {
              setState(() => _error = error);
              return;
            }
            final uri = Uri.parse(url);
            url = uri.replace(scheme: uri.scheme.toLowerCase()).toString();
            Navigator.pop(
              context,
              ServerProfile(
                id:
                    widget.existing?.id ??
                    DateTime.now().microsecondsSinceEpoch.toString(),
                name: _name.text.trim().isEmpty
                    ? Uri.parse(url).host
                    : _name.text.trim(),
                baseUrl: url.endsWith('/')
                    ? url.substring(0, url.length - 1)
                    : url,
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
