import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/server_probe.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../app_theme.dart';

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
          if (store.profiles.isEmpty) {
            return _WelcomeView(
              busy: _busy,
              onConnect: () => _edit(),
              onTermux: () => Navigator.pushNamed(context, '/termux-setup'),
              onGuide: () => Navigator.pushNamed(context, '/guide'),
            );
          }
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

/// The first-run experience: an identity moment plus the three concrete
/// paths into the product. Shown only while no server profile exists.
class _WelcomeView extends StatelessWidget {
  final bool busy;
  final VoidCallback onConnect;
  final VoidCallback onTermux;
  final VoidCallback onGuide;

  const _WelcomeView({
    required this.busy,
    required this.onConnect,
    required this.onTermux,
    required this.onGuide,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0,
          ),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 32),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Column(
                  key: const ValueKey('first-run-welcome'),
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          '❯',
                          style: theme.textTheme.headlineMedium!.copyWith(
                            color: theme.colorScheme.primary,
                            fontFamily: AppTheme.monoFamily,
                          ),
                        ),
                        const SizedBox(width: 9),
                        Container(
                          width: 13,
                          height: 26,
                          decoration: BoxDecoration(
                            color: theme.colorScheme.primary.withValues(
                              alpha: .45,
                            ),
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'Your coding agent, in your pocket',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.titleLarge,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'This app drives an OpenCode server. '
                      'Pick where yours runs.',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 24),
                    _WelcomeCard(
                      cardKey: const ValueKey('welcome-connect-card'),
                      icon: Icons.dns_rounded,
                      accent: true,
                      title: 'Connect to your computer',
                      subtitle:
                          'Paste the address of an OpenCode server you run — '
                          'the app fills in the rest',
                      onTap: busy ? null : onConnect,
                    ),
                    const SizedBox(height: 10),
                    _WelcomeCard(
                      cardKey: const ValueKey('welcome-termux-card'),
                      icon: Icons.smartphone_rounded,
                      title: 'Run OpenCode on this phone',
                      subtitle:
                          'Guided Termux setup — the app drives it for you',
                      onTap: busy ? null : onTermux,
                    ),
                    const SizedBox(height: 10),
                    _WelcomeCard(
                      cardKey: const ValueKey('welcome-guide-card'),
                      icon: Icons.menu_book_outlined,
                      title: 'Learn how OpenCode works',
                      subtitle: 'A two-minute guide to both paths',
                      onTap: onGuide,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _WelcomeCard extends StatelessWidget {
  final Key cardKey;
  final IconData icon;
  final String title;
  final String subtitle;
  final bool accent;
  final VoidCallback? onTap;

  const _WelcomeCard({
    required this.cardKey,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.accent = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      key: cardKey,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      color: accent
          ? theme.colorScheme.primaryContainer.withValues(alpha: .35)
          : null,
      child: ListTile(
        minTileHeight: 72,
        onTap: onTap,
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: theme.colorScheme.primary.withValues(alpha: .12),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Icon(icon, size: 22, color: theme.colorScheme.primary),
        ),
        title: Text(title),
        subtitle: Text(subtitle, maxLines: 3, overflow: TextOverflow.ellipsis),
        trailing: const Icon(Icons.chevron_right_rounded),
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
  // New profiles start empty: the normalizer on Test/Save adds the scheme,
  // and a pre-seeded 'https://' fought typed bare hosts.
  late final TextEditingController _url = TextEditingController(
    text: widget.existing?.baseUrl ?? '',
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
  bool _testing = false;
  ServerProbeResult? _testResult;
  int _urlLength = 0;
  int _probeGeneration = 0;
  bool get _needsPassword => widget.existing?.requiresPasswordReentry ?? false;

  @override
  void initState() {
    super.initState();
    _urlLength = _url.text.length;
  }

  /// A paste is a jump of several characters at once. When it lands without a
  /// scheme, expand it in place so `192.168.1.7:4096` just works.
  void _urlChanged(String value) {
    final pasted = value.length - _urlLength >= 4;
    _urlLength = value.length;
    if (pasted && !value.contains('://')) {
      final normalized = normalizeServerProfileUrl(value);
      if (normalized != value.trim()) {
        _urlLength = normalized.length;
        _url.value = TextEditingValue(
          text: normalized,
          selection: TextSelection.collapsed(offset: normalized.length),
        );
      }
    }
    setState(() {
      _error = null;
      _testResult = null;
    });
  }

  Future<void> _testConnection() async {
    if (_testing) return;
    final url = normalizeServerProfileUrl(_url.text);
    if (url != _url.text.trim()) {
      _urlLength = url.length;
      _url.value = TextEditingValue(
        text: url,
        selection: TextSelection.collapsed(offset: url.length),
      );
    }
    final error = validateServerProfileUrl(
      url,
      username: _user.text,
      password: _pass.text,
    );
    if (error != null) {
      setState(() {
        _error = error;
        _testResult = null;
      });
      _urlFocus.requestFocus();
      return;
    }
    final generation = ++_probeGeneration;
    setState(() {
      _testing = true;
      _testResult = null;
      _error = null;
    });
    final result = await serverProbe(
      baseUrl: url,
      username: _user.text.trim(),
      password: _pass.text,
    );
    if (!mounted || generation != _probeGeneration) return;
    setState(() {
      _testing = false;
      _testResult = result;
    });
  }

  bool get _dirty =>
      _name.text != (widget.existing?.name ?? '') ||
      _url.text != (widget.existing?.baseUrl ?? '') ||
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
    var url = normalizeServerProfileUrl(_url.text);
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
                onChanged: _urlChanged,
                decoration: InputDecoration(
                  labelText: 'Server URL',
                  hintText: '192.168.1.20:4096 or https://…',
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
                onChanged: (_) => setState(() {
                  _error = null;
                  _testResult = null;
                }),
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
                onChanged: (_) => setState(() {
                  _error = null;
                  _testResult = null;
                }),
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
              const SizedBox(height: 24),
              FilledButton.tonalIcon(
                key: const ValueKey('test-server-connection'),
                onPressed: _testing ? null : _testConnection,
                icon: _testing
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.network_check_rounded),
                label: Text(_testing ? 'Testing…' : 'Test connection'),
              ),
              if (_testResult case final result?) ...[
                const SizedBox(height: 12),
                Semantics(
                  container: true,
                  liveRegion: true,
                  child: Container(
                    key: ValueKey(
                      result.ok
                          ? 'server-test-success'
                          : 'server-test-failure',
                    ),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: result.ok
                          ? AppTheme.success(
                              theme.colorScheme,
                            ).withValues(alpha: .14)
                          : theme.colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          result.ok
                              ? Icons.check_circle_outline_rounded
                              : Icons.error_outline_rounded,
                          size: 20,
                          color: result.ok
                              ? AppTheme.success(theme.colorScheme)
                              : theme.colorScheme.onErrorContainer,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            result.ok
                                ? 'Connected — OpenCode '
                                      '${result.version ?? 'server'} '
                                      'answered. Save to finish.'
                                : result.message!,
                            style: TextStyle(
                              color: result.ok
                                  ? theme.colorScheme.onSurface
                                  : theme.colorScheme.onErrorContainer,
                              height: 1.35,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
