import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../widgets/product_states.dart';

class ProjectHealthScreen extends StatefulWidget {
  final ProductRepository repository;
  final Future<ProductRepository?> Function()? repositoryResolver;

  const ProjectHealthScreen({
    super.key,
    required this.repository,
    this.repositoryResolver,
  });

  @override
  State<ProjectHealthScreen> createState() => _ProjectHealthScreenState();
}

class _ProjectHealthScreenState extends State<ProjectHealthScreen> {
  VersionControlHealth? _versionControl;
  List<LanguageServiceHealth>? _languageServices;
  List<FormatterHealth>? _formatters;
  String? _versionControlError;
  String? _languageServicesError;
  String? _formattersError;
  String? _gitInitializationError;
  bool _refreshing = false;
  bool _initializingGit = false;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() {
      _refreshing = true;
      _versionControlError = null;
      _languageServicesError = null;
      _formattersError = null;
      _gitInitializationError = null;
    });
    final repository = await _resolveRepository();
    if (!mounted || generation != _generation) return;
    if (repository == null) {
      const message = 'OpenCode is reconnecting. Try again shortly.';
      setState(() {
        _versionControlError = message;
        _languageServicesError = message;
        _formattersError = message;
        _refreshing = false;
      });
      return;
    }
    await Future.wait([
      _loadVersionControl(repository, generation),
      _loadLanguageServices(repository, generation),
      _loadFormatters(repository, generation),
    ]);
    if (mounted && generation == _generation) {
      setState(() => _refreshing = false);
    }
  }

  Future<ProductRepository?> _resolveRepository() async =>
      widget.repositoryResolver?.call() ?? widget.repository;

  Future<void> _initializeGit() async {
    if (_initializingGit) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Initialize Git repository?'),
        content: const Text(
          'OpenCode will run git init in the current project. Existing files '
          'will not be changed or committed. This enables branch, working-tree, '
          'and Review features.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: const ValueKey('confirm-git-initialization'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Initialize Git'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _initializingGit = true;
      _gitInitializationError = null;
    });
    try {
      final repository = await _resolveRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      await repository.initializeGitRepository();
      await _load();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Git repository initialized')),
      );
    } catch (error) {
      if (mounted) {
        setState(() => _gitInitializationError = productErrorText(error));
      }
    } finally {
      if (mounted) setState(() => _initializingGit = false);
    }
  }

  Future<void> _loadVersionControl(
    ProductRepository repository,
    int generation,
  ) async {
    try {
      final value = await repository.loadVersionControlHealth();
      if (mounted && generation == _generation) {
        setState(() => _versionControl = value);
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _versionControlError = productErrorText(error));
      }
    }
  }

  Future<void> _loadLanguageServices(
    ProductRepository repository,
    int generation,
  ) async {
    try {
      final value = await repository.listLanguageServices();
      if (mounted && generation == _generation) {
        setState(() => _languageServices = value);
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _languageServicesError = productErrorText(error));
      }
    }
  }

  Future<void> _loadFormatters(
    ProductRepository repository,
    int generation,
  ) async {
    try {
      final value = await repository.listFormatters();
      if (mounted && generation == _generation) {
        setState(() => _formatters = value);
      }
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _formattersError = productErrorText(error));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Project health'),
        actions: [
          IconButton(
            tooltip: 'Refresh project health',
            onPressed: _refreshing || _initializingGit ? null : _load,
            icon: _refreshing
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
        child: ListView(
          key: const ValueKey('project-health-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            SectionLabel(
              'Version control',
              trailing: _versionControl == null
                  ? null
                  : Text('${_versionControl!.changes.length} changed'),
            ),
            ..._versionControlRows(),
            SectionLabel(
              'Language services',
              trailing: _languageServices == null
                  ? null
                  : Text(
                      '${_languageServices!.where((item) => item.connected).length}/${_languageServices!.length}',
                    ),
            ),
            ..._languageServiceRows(),
            SectionLabel(
              'Formatters',
              trailing: _formatters == null
                  ? null
                  : Text(
                      '${_formatters!.where((item) => item.enabled).length}/${_formatters!.length}',
                    ),
            ),
            ..._formatterRows(),
          ],
        ),
      ),
    );
  }

  List<Widget> _versionControlRows() {
    if (_versionControlError != null) {
      return [
        _HealthErrorTile(
          message: _versionControlError!,
          onRetry: _refreshing ? null : _load,
        ),
      ];
    }
    final vcs = _versionControl;
    if (vcs == null) {
      return const [_HealthLoadingTile(label: 'version control')];
    }
    if (vcs.setupState == VersionControlSetupState.absent) {
      return [
        const ListTile(
          key: ValueKey('git-not-initialized'),
          leading: Icon(Icons.account_tree_outlined),
          title: Text('Git is not initialized'),
          subtitle: Text(
            'Initialize this project to enable branches, working-tree changes, and Review.',
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          child: FilledButton(
            key: const ValueKey('initialize-git-repository'),
            onPressed: _initializingGit ? null : _initializeGit,
            child: _initializingGit
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Initialize Git'),
          ),
        ),
        if (_gitInitializationError != null)
          ListTile(
            leading: const Icon(Icons.error_outline_rounded),
            title: const Text('Git initialization failed'),
            subtitle: Text(
              _gitInitializationError!,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: TextButton(
              onPressed: _initializingGit ? null : _initializeGit,
              child: const Text('Try again'),
            ),
          ),
      ];
    }
    final branch = vcs.branch?.trim();
    final defaultBranch = vcs.defaultBranch?.trim();
    return [
      ListTile(
        leading: const Icon(Icons.account_tree_outlined),
        title: Text(branch?.isNotEmpty == true ? branch! : 'No active branch'),
        subtitle: Text(
          defaultBranch?.isNotEmpty == true
              ? 'Default branch: $defaultBranch'
              : vcs.changes.isEmpty
              ? 'Working tree is clean'
              : '${vcs.changes.length} changed files',
        ),
        trailing: vcs.changes.isEmpty
            ? const Icon(Icons.check_circle_outline_rounded)
            : _ChangeCounts(additions: vcs.additions, deletions: vcs.deletions),
      ),
      if (vcs.changes.isEmpty)
        const ListTile(
          leading: Icon(Icons.done_all_rounded),
          title: Text('No uncommitted changes'),
        )
      else
        for (final file in vcs.changes) _VersionControlFileTile(file: file),
    ];
  }

  List<Widget> _languageServiceRows() {
    if (_languageServicesError != null) {
      return [
        _HealthErrorTile(
          message: _languageServicesError!,
          onRetry: _refreshing ? null : _load,
        ),
      ];
    }
    final services = _languageServices;
    if (services == null) {
      return const [_HealthLoadingTile(label: 'language services')];
    }
    if (services.isEmpty) {
      return const [
        ListTile(
          leading: Icon(Icons.code_off_rounded),
          title: Text('No active language services'),
          subtitle: Text(
            'OpenCode activates them while it inspects supported source files during coding.',
          ),
        ),
      ];
    }
    return [
      for (final service in services)
        ListTile(
          leading: Icon(
            service.connected
                ? Icons.check_circle_outline_rounded
                : Icons.error_outline_rounded,
          ),
          title: Text(service.name),
          subtitle: Text(
            service.root.isEmpty
                ? service.status
                : '${service.status} · ${service.root}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
    ];
  }

  List<Widget> _formatterRows() {
    if (_formattersError != null) {
      return [
        _HealthErrorTile(
          message: _formattersError!,
          onRetry: _refreshing ? null : _load,
        ),
      ];
    }
    final formatters = _formatters;
    if (formatters == null) {
      return const [_HealthLoadingTile(label: 'formatters')];
    }
    if (formatters.isEmpty) {
      return const [
        ListTile(
          leading: Icon(Icons.format_align_left_rounded),
          title: Text('No formatters configured'),
        ),
      ];
    }
    return [
      for (final formatter in formatters)
        ListTile(
          leading: Icon(
            formatter.enabled
                ? Icons.check_circle_outline_rounded
                : Icons.remove_circle_outline_rounded,
          ),
          title: Text(formatter.name),
          subtitle: Text(
            formatter.extensions.isEmpty
                ? formatter.enabled
                      ? 'Enabled'
                      : 'Disabled'
                : '${formatter.enabled ? 'Enabled' : 'Disabled'} · ${formatter.extensions.join(', ')}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
    ];
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }
}

class _VersionControlFileTile extends StatelessWidget {
  final VersionControlFile file;

  const _VersionControlFileTile({required this.file});

  @override
  Widget build(BuildContext context) => ListTile(
    minTileHeight: 54,
    leading: Icon(_statusIcon(file.status), size: 20),
    title: Text(file.path, maxLines: 1, overflow: TextOverflow.ellipsis),
    subtitle: Text(file.status),
    trailing: _ChangeCounts(
      additions: file.additions,
      deletions: file.deletions,
    ),
  );

  static IconData _statusIcon(String status) => switch (status) {
    'added' => Icons.add_circle_outline_rounded,
    'deleted' => Icons.remove_circle_outline_rounded,
    _ => Icons.edit_outlined,
  };
}

class _ChangeCounts extends StatelessWidget {
  final int additions;
  final int deletions;

  const _ChangeCounts({required this.additions, required this.deletions});

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(
        '+$additions',
        style: TextStyle(color: Theme.of(context).colorScheme.primary),
      ),
      const SizedBox(width: 8),
      Text(
        '-$deletions',
        style: TextStyle(color: Theme.of(context).colorScheme.error),
      ),
    ],
  );
}

class _HealthLoadingTile extends StatelessWidget {
  final String label;

  const _HealthLoadingTile({required this.label});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const SizedBox.square(
      dimension: 20,
      child: CircularProgressIndicator(strokeWidth: 2),
    ),
    title: Text('Loading $label'),
  );
}

class _HealthErrorTile extends StatelessWidget {
  final String message;
  final VoidCallback? onRetry;

  const _HealthErrorTile({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const Icon(Icons.error_outline_rounded),
    title: const Text('Status unavailable'),
    subtitle: Text(message, maxLines: 3, overflow: TextOverflow.ellipsis),
    trailing: TextButton(onPressed: onRetry, child: const Text('Try again')),
  );
}
