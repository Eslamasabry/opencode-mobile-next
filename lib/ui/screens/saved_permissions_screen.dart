import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../permission_presentation.dart';
import '../widgets/product_states.dart';

typedef SavedPermissionRepositoryResolver =
    Future<ProductRepository?> Function();

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

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<ProductRepository?> _resolveRepository() =>
      widget.repositoryResolver?.call() ??
      widget.controller.prepareActionRepository();

  Future<void> _load() async {
    if (_loading || _removing.isNotEmpty) return;
    final generation = ++_generation;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repository = await _resolveRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final permissions = List<SavedPermission>.of(
        await repository.listSavedPermissions(),
      );
      if (!mounted || generation != _generation) return;
      permissions.sort((a, b) {
        final action = a.action.compareTo(b.action);
        return action == 0 ? a.resource.compareTo(b.resource) : action;
      });
      setState(() => _permissions = permissions);
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _error = error.toString());
      }
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _revoke(SavedPermission permission) async {
    if (_removing.contains(permission.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        icon: const Icon(Icons.gpp_maybe_outlined),
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
              style: const TextStyle(fontFamily: 'monospace'),
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
    if (confirmed != true || !mounted) return;

    setState(() {
      _removing.add(permission.id);
      _error = null;
    });
    try {
      final repository = await _resolveRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      await repository.removeSavedPermission(permission.id);
      if (!mounted) return;
      setState(() {
        _permissions = (_permissions ?? const [])
            .where((item) => item.id != permission.id)
            .toList();
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Always allowed action revoked')),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString());
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) setState(() => _removing.remove(permission.id));
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
                : const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: permissions == null && _error == null
            ? const LoadingList(rows: 4)
            : permissions?.isEmpty == true && _error == null
            ? const ProductEmptyState(
                icon: Icons.verified_user_outlined,
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
                        Icons.error_outline_rounded,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      title: const Text('The last action failed'),
                      subtitle: Text(_error!),
                    ),
                  for (final permission in permissions)
                    ListTile(
                      key: ValueKey('saved-permission-${permission.id}'),
                      leading: const Icon(Icons.admin_panel_settings_outlined),
                      title: Text(permissionRequestTitle(permission.action)),
                      subtitle: SelectableText(
                        permission.resource.trim().isEmpty
                            ? '(all matching resources)'
                            : permission.resource,
                        maxLines: 3,
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 12.5,
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
                              icon: const Icon(Icons.delete_outline_rounded),
                            ),
                    ),
                ],
              ),
      ),
    );
  }
}
