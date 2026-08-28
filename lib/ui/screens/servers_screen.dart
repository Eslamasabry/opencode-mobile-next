import 'dart:async';

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
    final result = await Navigator.of(context).push<ServerProfile>(
      MaterialPageRoute<ServerProfile>(
        builder: (_) => _ProfileEditorScreen(existing: existing),
      ),
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
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.terminal_rounded,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 10),
            const Flexible(
              child: Text(
                'OpenCode',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
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
                            fontFamily: 'AppMono',
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

class _ProfileEditorScreen extends StatefulWidget {
  final ServerProfile? existing;
  const _ProfileEditorScreen({this.existing});

  @override
  State<_ProfileEditorScreen> createState() => _ProfileEditorScreenState();
}

class _ProfileEditorScreenState extends State<_ProfileEditorScreen> {
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
  final _urlFocus = FocusNode();
  final _nameFocus = FocusNode();
  final _userFocus = FocusNode();
  final _passFocus = FocusNode();
  String? _error;
  bool _obscurePassword = true;
  bool _closing = false;
  bool get _needsPassword => widget.existing?.requiresPasswordReentry ?? false;

  bool get _dirty =>
      _name.text != (widget.existing?.name ?? '') ||
      _url.text != (widget.existing?.baseUrl ?? 'https://') ||
      _user.text != (widget.existing?.username ?? '') ||
      _pass.text != (widget.existing?.password ?? '');

  @override
  void dispose() {
    _name.dispose();
    _url.dispose();
    _user.dispose();
    _pass.dispose();
    _urlFocus.dispose();
    _nameFocus.dispose();
    _userFocus.dispose();
    _passFocus.dispose();
    super.dispose();
  }

  Future<void> _close() async {
    if (_closing) return;
    if (!_dirty) {
      Navigator.pop(context);
      return;
    }
    _closing = true;
    final discard = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Discard server changes?'),
        content: const Text('The server profile has not been saved.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep editing'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Discard'),
          ),
        ],
      ),
    );
    _closing = false;
    if (discard == true && mounted) Navigator.pop(context);
  }

  void _save() {
    var url = _url.text.trim();
    final error = validateServerProfileUrl(
      url,
      username: _user.text,
      password: _pass.text,
    );
    if (error != null) {
      setState(() => _error = error);
      _urlFocus.requestFocus();
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
        name: _name.text.trim().isEmpty ? uri.host : _name.text.trim(),
        baseUrl: url.endsWith('/') ? url.substring(0, url.length - 1) : url,
        username: _user.text.trim(),
        password: _pass.text,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.existing == null
        ? 'Add server'
        : _needsPassword
        ? 'Re-enter password'
        : 'Edit server';
    final theme = Theme.of(context);
    final compact =
        MediaQuery.sizeOf(context).width < 360 ||
        MediaQuery.textScalerOf(context).scale(14) > 20;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_close());
      },
      child: Scaffold(
        key: const ValueKey('server-profile-editor'),
        appBar: AppBar(
          leading: IconButton(
            tooltip: 'Close server editor',
            onPressed: _close,
            icon: const Icon(Icons.close_rounded),
          ),
          title: Text(title),
          actions: [
            TextButton(
              key: const ValueKey('save-server-profile'),
              onPressed: _save,
              child: Text(
                widget.existing == null && !compact ? 'Save server' : 'Save',
              ),
            ),
            const SizedBox(width: 4),
          ],
        ),
        body: SafeArea(
          top: false,
          child: ListView(
            key: const ValueKey('server-profile-fields'),
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
            children: [
              Text(
                'Connect to an OpenCode server running on this phone or another machine.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              if (_needsPassword) ...[
                const SizedBox(height: 16),
                Semantics(
                  container: true,
                  liveRegion: true,
                  excludeSemantics: true,
                  label:
                      'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires a password.',
                  child: Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires one.',
                      style: TextStyle(
                        color: theme.colorScheme.onErrorContainer,
                      ),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 24),
              TextField(
                key: const ValueKey('server-url-field'),
                controller: _url,
                focusNode: _urlFocus,
                autofocus: widget.existing == null && !_needsPassword,
                keyboardType: TextInputType.url,
                textInputAction: TextInputAction.next,
                onSubmitted: (_) => _nameFocus.requestFocus(),
                onChanged: (_) => setState(() => _error = null),
                decoration: InputDecoration(
                  labelText: 'Server URL',
                  hintText: 'https://host:4096',
                  errorText: _error,
                  errorMaxLines: 3,
                  helperText:
                      'Use HTTPS for remote machines. HTTP is limited to localhost or 127.0.0.1.',
                  helperMaxLines: 3,
                ),
              ),
              const SizedBox(height: 20),
              TextField(
                key: const ValueKey('server-name-field'),
                controller: _name,
                focusNode: _nameFocus,
                textInputAction: TextInputAction.next,
                onSubmitted: (_) => _userFocus.requestFocus(),
                decoration: const InputDecoration(
                  labelText: 'Display name (optional)',
                  hintText: 'Defaults to the server host',
                ),
              ),
              const SizedBox(height: 28),
              Text(
                'AUTHENTICATION',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  letterSpacing: 1,
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                key: const ValueKey('server-username-field'),
                controller: _user,
                focusNode: _userFocus,
                textInputAction: TextInputAction.next,
                onSubmitted: (_) => _passFocus.requestFocus(),
                onChanged: (_) => setState(() => _error = null),
                decoration: const InputDecoration(
                  labelText: 'Username (optional)',
                  hintText: 'opencode',
                ),
              ),
              const SizedBox(height: 20),
              TextField(
                key: const ValueKey('server-password-field'),
                controller: _pass,
                focusNode: _passFocus,
                autofocus: _needsPassword,
                onChanged: (_) => setState(() => _error = null),
                obscureText: _obscurePassword,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _save(),
                decoration: InputDecoration(
                  labelText: _needsPassword
                      ? 'Re-enter password'
                      : 'Password (optional)',
                  helperText: _needsPassword
                      ? 'Leave empty only if this server no longer uses a password.'
                      : 'Matches OPENCODE_SERVER_PASSWORD on the server.',
                  helperMaxLines: 2,
                  suffixIcon: IconButton(
                    key: const ValueKey('toggle-server-password'),
                    tooltip: _obscurePassword
                        ? 'Show server password'
                        : 'Hide server password',
                    onPressed: () =>
                        setState(() => _obscurePassword = !_obscurePassword),
                    icon: Icon(
                      _obscurePassword
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
