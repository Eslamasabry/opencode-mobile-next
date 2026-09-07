import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/plugin_inventory.dart';
import '../../domain/server_gateway.dart' show StreamStatus;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';

class PluginsScreen extends StatefulWidget {
  const PluginsScreen({super.key, required this.controller});
  final ConnectionController controller;
  @override
  State<PluginsScreen> createState() => _PluginsScreenState();
}

class _PluginsScreenState extends State<PluginsScreen> {
  StreamSubscription<dynamic>? _events;
  Object? _source;
  List<PluginInfo>? _plugins;
  bool _loading = false;
  bool _failed = false;
  bool _reloadQueued = false;
  int _request = 0;
  ConnectionController get _controller => widget.controller;
  bool get _connected =>
      _controller.status == StreamStatus.connected &&
      _controller.isProfileReadable(_controller.profile?.id ?? '');
  bool get _supported =>
      _controller.capabilities.pluginInventory &&
      _controller.repository is PluginGateway;
  Object get _scope => (
    _controller.profile?.id,
    _controller.profile?.baseUrl,
    _controller.profile?.username,
    _controller.directory,
    _controller.workspace,
    _controller.locationRevision,
    _controller.connectionRevision,
    _controller.repository,
    _connected,
    _supported,
  );

  @override
  void initState() {
    super.initState();
    _attach();
    unawaited(_load());
  }

  void _attach() {
    _source = _scope;
    _controller.addListener(_changed);
    _controller.profileDataChanges.addListener(_changed);
    _events = _controller.events.listen((event) {
      if (event.type == 'plugin.added' || event.type == 'plugin.updated') {
        unawaited(_load());
      }
    });
  }

  void _detach(ConnectionController controller) {
    controller.removeListener(_changed);
    controller.profileDataChanges.removeListener(_changed);
    unawaited(_events?.cancel());
  }

  @override
  void didUpdateWidget(covariant PluginsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.controller, widget.controller)) {
      _detach(oldWidget.controller);
      _request++;
      _plugins = null;
      _loading = false;
      _failed = false;
      _reloadQueued = false;
      _attach();
      unawaited(_load());
    }
  }

  void _changed() {
    if (!mounted || _source == _scope) return;
    setState(() {
      _source = _scope;
      _request++;
      _plugins = null;
      _loading = false;
      _failed = false;
      _reloadQueued = false;
    });
    unawaited(_load());
  }

  Future<void> _load() async {
    if (!mounted || !_connected || !_supported) return;
    if (_loading) {
      _reloadQueued = true;
      return;
    }
    final scope = _scope;
    final request = ++_request;
    final gateway = _controller.repository as PluginGateway;
    setState(() {
      _loading = true;
      _failed = false;
    });
    bool current() => mounted && request == _request && scope == _scope;
    try {
      final result = await gateway.listPlugins();
      if (current()) setState(() => _plugins = result);
    } catch (_) {
      if (current()) setState(() => _failed = true);
    } finally {
      if (current()) {
        setState(() => _loading = false);
        if (_reloadQueued) {
          _reloadQueued = false;
          unawaited(_load());
        }
      }
    }
  }

  @override
  void dispose() {
    _request++;
    _detach(_controller);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.pluginsTitle),
        actions: [
          if (_connected && _supported)
            IconButton(
              tooltip: l10n.pluginsRefresh,
              onPressed: _loading ? null : _load,
              icon: const Icon(Icons.refresh_rounded),
            ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.pluginsDescription),
            const SizedBox(height: 20),
            if (!_supported)
              Text(l10n.pluginsUnsupported)
            else if (!_connected)
              Text(l10n.pluginsDisconnected)
            else ...[
              if (_loading) const LinearProgressIndicator(),
              if (_failed) ...[
                Text(l10n.pluginsLoadFailed),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton(
                    onPressed: _loading ? null : _load,
                    child: Text(l10n.pluginsRetry),
                  ),
                ),
              ],
              if (_plugins?.isEmpty == true && !_loading && !_failed)
                Text(l10n.pluginsEmpty),
              for (final plugin in _plugins ?? const <PluginInfo>[]) ...[
                _row(plugin, l10n),
                const Divider(height: 1),
              ],
            ],
          ],
        ),
      ),
    );
  }

  Widget _row(PluginInfo plugin, AppLocalizations l10n) {
    final theme = Theme.of(context);
    final source = switch (plugin.source) {
      PluginSourceKind.builtin => l10n.pluginsSourceBuiltin,
      PluginSourceKind.package => l10n.pluginsSourcePackage,
      PluginSourceKind.local => l10n.pluginsSourceLocal,
      PluginSourceKind.sdk => l10n.pluginsSourceSdk,
      PluginSourceKind.unknown => l10n.pluginsSourceUnknown,
    };
    final status = switch (plugin.status) {
      PluginStatus.active => l10n.pluginsStatusActive,
      PluginStatus.failed => l10n.pluginsStatusFailed,
      PluginStatus.unknown => l10n.pluginsStatusUnknown,
    };
    final tone = switch (plugin.status) {
      PluginStatus.active => AppStatusTone.ok,
      PluginStatus.failed => AppStatusTone.failure,
      PluginStatus.unknown => AppStatusTone.neutral,
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            plugin.status == PluginStatus.active
                ? Icons.check_circle_outline
                : plugin.status == PluginStatus.failed
                ? Icons.error_outline
                : Icons.help_outline,
            color: AppTheme.statusColor(theme, tone),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  plugin.id ?? l10n.pluginsUnnamed,
                  style: theme.textTheme.titleMedium,
                ),
                Text(
                  status,
                  style: TextStyle(color: AppTheme.statusColor(theme, tone)),
                ),
                Text(
                  plugin.packageName == null
                      ? source
                      : '$source · ${plugin.packageName}',
                ),
                if (plugin.terminalUi) Text(l10n.pluginsTerminalUi),
                if (plugin.status == PluginStatus.failed)
                  Text(l10n.pluginsFailureDetail),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
