import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../permission_presentation.dart';
import '../widgets/product_states.dart';
import '../app_theme.dart';

typedef SavedPermissionRepositoryResolver =
    Future<ServerOperationsGateway?> Function();

class SavedPermissionsScreen extends StatefulWidget {
  const SavedPermissionsScreen({
    super.key,
    required this.controller,
    this.repositoryResolver,
  });

  final ConnectionController controller;
  final SavedPermissionRepositoryResolver? repositoryResolver;

  @override
  State<SavedPermissionsScreen> createState() => _SavedPermissionsScreenState();
}

class _SavedPermissionsScreenState extends State<SavedPermissionsScreen> {
  List<SavedPermission>? _permissions;
  final Set<String> _removing = {};
  bool _loading = false;
  String? _error;
  int _generation = 0;
  Object? _scope;

  Object get _currentScope {
    final profile = widget.controller.profile;
    return (
      widget.controller,
      profile?.id,
      profile?.baseUrl,
      profile?.username,
      widget.controller.directory,
      widget.controller.workspace,
      widget.controller.locationRevision,
      widget.controller.connectionRevision,
      widget.controller.repository,
    );
  }

  @override
  void initState() {
    super.initState();
    _scope = _currentScope;
    widget.controller.addListener(_scopeChanged);
    widget.controller.profileDataChanges.addListener(_scopeChanged);
    unawaited(_load());
  }

  @override
  void dispose() {
    widget.controller.removeListener(_scopeChanged);
    widget.controller.profileDataChanges.removeListener(_scopeChanged);
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant SavedPermissionsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.controller, widget.controller)) return;
    oldWidget.controller.removeListener(_scopeChanged);
    oldWidget.controller.profileDataChanges.removeListener(_scopeChanged);
    widget.controller.addListener(_scopeChanged);
    widget.controller.profileDataChanges.addListener(_scopeChanged);
    _generation++;
    _scope = _currentScope;
    setState(() {
      _permissions = null;
      _loading = false;
      _error = null;
      _removing.clear();
    });
    unawaited(_load());
  }

  void _scopeChanged() {
    if (!mounted) return;
    final scope = _currentScope;
    if (scope == _scope) return;
    _scope = scope;
    _generation++;
    setState(() {
      _permissions = null;
      _loading = false;
      _error = null;
      _removing.clear();
    });
    unawaited(_load());
  }

  Future<ServerOperationsGateway?> _resolveRepository() =>
      widget.repositoryResolver?.call() ??
      widget.controller.prepareActionRepository();

  Future<void> _load() async {
    if (_loading || _removing.isNotEmpty) return;
    if (!mounted) return;
    final generation = ++_generation;
    final scope = _scope;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repository = await _resolveRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      if (!mounted || generation != _generation || scope != _currentScope) {
        return;
      }
      final permissions = List<SavedPermission>.of(
        await repository.listSavedPermissions(),
      );
      if (!mounted || generation != _generation || scope != _currentScope) {
        return;
      }
      permissions.sort((a, b) {
        final action = a.action.compareTo(b.action);
        return action == 0 ? a.resource.compareTo(b.resource) : action;
      });
      setState(() => _permissions = permissions);
    } catch (error) {
      if (mounted && generation == _generation && scope == _currentScope) {
        setState(() => _error = productErrorText(error));
      }
    } finally {
      if (mounted && generation == _generation && scope == _currentScope) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _revoke(SavedPermission permission) async {
    if (_removing.contains(permission.id)) return;
    final scope = _scope;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        icon: const Icon(AppIconography.privacyWarning),
        title: const Text('Revoke always allowed action?'),
        content: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'OpenCode will ask again before a future action matching this grant.',
            ),
            const SizedBox(height: 16),
            Text('Action', style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 4),
            Text(permissionRequestTitle(permission.action)),
            const SizedBox(height: 12),
            Text('Resource', style: Theme.of(context).textTheme.labelLarge),
            const SizedBox(height: 4),
            SelectableText(
              permission.resource.trim().isEmpty
                  ? '(all matching resources)'
                  : permission.resource,
              style: const TextStyle(fontFamily: AppTheme.monoFamily),
            ),
            const SizedBox(height: 12),
            const Text('This does not stop an action that is already running.'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep access'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Revoke access'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted || scope != _currentScope) return;

    setState(() {
      _removing.add(permission.id);
      _error = null;
    });
    try {
      final repository = await _resolveRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      if (!mounted || scope != _currentScope) return;
      await repository.removeSavedPermission(permission.id);
      if (!mounted || scope != _currentScope) return;
      setState(() {
        _permissions = (_permissions ?? const [])
            .where((item) => item.id != permission.id)
            .toList();
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Always allowed action revoked')),
      );
    } catch (error) {
      if (!mounted || scope != _currentScope) return;
      setState(() => _error = productErrorText(error));
      showProductError(context, error);
    } finally {
      if (mounted && scope == _currentScope) {
        setState(() => _removing.remove(permission.id));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final permissions = _permissions;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Always allowed actions'),
        actions: [
          IconButton(
            tooltip: 'Refresh always allowed actions',
            onPressed: _loading || _removing.isNotEmpty ? null : _load,
            icon: _loading
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(AppIconography.retry),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: permissions == null && _error == null
            ? const LoadingList(rows: 4)
            : permissions?.isEmpty == true && _error == null
            ? const ProductEmptyState(
                icon: AppIconography.privacy,
                title: 'No always allowed actions',
                message:
                    'Grants created with Always allow for this project will appear here.',
              )
            : permissions?.isEmpty != false && _error != null
            ? ProductErrorState(message: _error!, onRetry: _load)
            : ListView(
                key: const ValueKey('saved-permissions-list'),
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 24),
                children: [
                  SectionLabel(
                    'Current project',
                    trailing: Text(
                      '${permissions!.length} ${permissions.length == 1 ? 'grant' : 'grants'}',
                    ),
                  ),
                  if (_error != null)
                    ListTile(
                      leading: Icon(
                        AppIconography.error,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      title: const Text('The last action failed'),
                      subtitle: Text(_error!),
                    ),
                  for (final permission in permissions)
                    ListTile(
                      key: ValueKey('saved-permission-${permission.id}'),
                      leading: const Icon(AppIconography.privacy),
                      title: Text(permissionRequestTitle(permission.action)),
                      subtitle: SelectableText(
                        permission.resource.trim().isEmpty
                            ? '(all matching resources)'
                            : permission.resource,
                        maxLines: 3,
                        style: const TextStyle(
                          fontFamily: AppTheme.monoFamily,
                          fontSize: AppTheme.codeFontSize,
                        ),
                      ),
                      trailing: _removing.contains(permission.id)
                          ? const SizedBox.square(
                              dimension: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : IconButton(
                              key: ValueKey(
                                'revoke-saved-permission-${permission.id}',
                              ),
                              tooltip: 'Revoke ${permission.action} access',
                              onPressed: () => _revoke(permission),
                              icon: const Icon(AppIconography.delete),
                            ),
                    ),
                ],
              ),
      ),
    );
  }
}
