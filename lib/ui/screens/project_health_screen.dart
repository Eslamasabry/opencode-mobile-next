import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';

import '../../api/product_repository.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import '../widgets/product_states.dart' show productErrorText;

/// Project health (map pages project-health and
/// project-health-git-init-dialog; revamp unit screen-work-3): the branch
/// and changed files, the language servers and the formatters the server
/// reports, each on one [KitRowGroup].
///
/// Built from kit parts only: a [KitScreen] page with its title (pull to
/// refresh reloads every section) and the one loading bar. The rows say
/// each item's state, so the section labels carry no counts. Each section loads on its own: skeleton rows
/// while it loads, an inline [KitNotice.error] with Try again when its read
/// fails, an empty state that says what would be there, and its rows. A
/// section the server cannot report is not shown (and not asked for);
/// Initialize Git, where the server cannot run it, stays as a dimmed row
/// that says where to run it. Where it can, it is a "Set up" row that
/// confirms first ([showKitConfirm]) and says so in place when it is done or
/// when it failed.
class ProjectHealthScreen extends StatefulWidget {
  final ServerOperationsGateway repository;
  final Future<ServerOperationsGateway?> Function()? repositoryResolver;

  /// What the connected server can actually report. Defaults to the v1
  /// superset so the screen keeps its full shape unless a caller narrows it
  /// (`docs/opencode2-ui-design.md` §7, rows 17–19).
  final ServerCapabilities capabilities;

  const ProjectHealthScreen({
    super.key,
    required this.repository,
    this.repositoryResolver,
    this.capabilities = ServerCapabilities.allV1,
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
  bool _gitInitialized = false;
  bool _refreshing = false;
  bool _initializingGit = false;
  int _generation = 0;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

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
      final message = _l10n.e7LibraryOpenCodeIsReconnectingTryAgainShortly;
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
      // Hidden sections are not fetched: a gated section must not spend a
      // request only to throw an "unavailable" error into a dropped state.
      if (widget.capabilities.languageServiceStatus)
        _loadLanguageServices(repository, generation),
      if (widget.capabilities.formatterStatus)
        _loadFormatters(repository, generation),
    ]);
    if (mounted && generation == _generation) {
      setState(() => _refreshing = false);
    }
  }

  Future<ServerOperationsGateway?> _resolveRepository() async =>
      widget.repositoryResolver?.call() ?? widget.repository;

  Future<void> _initializeGit() async {
    final l10n = _l10n;
    if (_initializingGit) return;
    final confirmed = await showKitConfirm(
      context,
      title: l10n.e7LibraryInitializeGitRepository,
      body: l10n.e7LibraryOpenCodeWillRunGitInitInThe,
      confirmLabel: l10n.e7LibraryInitializeGit,
      icon: AppIconography.branch,
      confirmKey: const ValueKey('confirm-git-initialization'),
    );
    if (!confirmed || !mounted) return;
    setState(() {
      _initializingGit = true;
      _gitInitializationError = null;
      _gitInitialized = false;
    });
    try {
      final repository = await _resolveRepository();
      if (repository == null) {
        throw ProductException(l10n.e7LibraryOpenCodeIsReconnectingTryAgain);
      }
      await repository.initializeGitRepository();
      await _load();
      if (mounted) setState(() => _gitInitialized = true);
    } catch (error) {
      if (mounted) {
        setState(() => _gitInitializationError = productErrorText(error));
      }
    } finally {
      if (mounted) setState(() => _initializingGit = false);
    }
  }

  Future<void> _loadVersionControl(
    ServerOperationsGateway repository,
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
    ServerOperationsGateway repository,
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
    ServerOperationsGateway repository,
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
    final l10n = _l10n;
    return KitScreen(
      // A status page read like settings: centred at the reading width.
      width: KitScreenWidth.reading,
      topBar: KitTopBar(title: l10n.e7LibraryProjectHealth),
      loading: _refreshing,
      loadingLabel: l10n.e7LibraryLoading(l10n.e7LibraryProjectHealth),
      body: KitRefresh(
        onRefresh: _load,
        child: ListView(
          key: const ValueKey('project-health-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            ..._versionControlSection(l10n),
            if (widget.capabilities.languageServiceStatus)
              ..._languageServiceSection(l10n),
            if (widget.capabilities.formatterStatus) ..._formatterSection(l10n),
          ],
        ),
      ),
    );
  }

  /// A section's message inside the list's side rails; [top] is the space
  /// above it (none right under a section label, which keeps its own).
  Widget _inset(Widget child, {bool top = true}) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: top
          ? EdgeInsetsDirectional.only(
              start: tokens.gutter,
              top: tokens.space2,
              end: tokens.gutter,
            )
          : EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: child,
    );
  }

  Widget _readFailed(AppLocalizations l10n, String message) => _inset(
    top: false,
    KitNotice.error(
      title: l10n.workUnknown,
      message: message,
      retry: KitAction(
        label: l10n.isolatedTaskRetryOpen,
        onPressed: _refreshing ? null : () => unawaited(_load()),
        disabledReason: _refreshing ? l10n.kitWorking : null,
      ),
    ),
  );

  List<Widget> _versionControlSection(AppLocalizations l10n) {
    final label = l10n.e7LibraryVersionControl;
    final error = _versionControlError;
    if (error != null) {
      return [KitSectionLabel(label), _readFailed(l10n, error)];
    }
    final vcs = _versionControl;
    if (vcs == null) {
      return [KitSectionLabel(label), const KitSkeletonRows(count: 2)];
    }
    final done = _gitInitialized
        ? _inset(
            KitNotice(
              key: const ValueKey('git-initialized'),
              message: l10n.e7LibraryGitRepositoryInitialized,
              tone: AppStatusTone.ok,
              onDismiss: () => setState(() => _gitInitialized = false),
            ),
          )
        : null;
    if (vcs.setupState == VersionControlSetupState.absent) {
      final initError = _gitInitializationError;
      return [
        // No "0 changed" beside "Git is not initialized".
        KitRowGroup(
          label: label,
          children: [
            KitRow(
              key: const ValueKey('git-not-initialized'),
              leading: KitRowIcon(AppIconography.branch),
              title: l10n.e7LibraryGitIsNotInitialized,
              supporting: TextSpan(
                text:
                    l10n.e7LibraryInitializeThisProjectToEnableBranchesWorking,
              ),
              supportingMaxLines: 2,
            ),
            // §7 row 19: health screens explain rather than vanish, so the
            // action stays visible and says where to run it instead.
            if (!widget.capabilities.gitInit)
              KitRow.unavailable(
                key: const ValueKey('gated-git-init'),
                leading: KitRowIcon(AppIconography.terminal),
                title: l10n.e7LibraryInitializeGit,
                reason: l10n.e7LibraryRunGitInitFromATerminal,
              )
            else
              KitRow(
                key: const ValueKey('initialize-git-repository'),
                leading: KitRowIcon(AppIconography.add),
                title: l10n.e7LibraryInitializeGit,
                supporting: TextSpan(text: l10n.projectHealthGitInitSupporting),
                supportingMaxLines: 2,
                trailing: _initializingGit
                    ? null
                    : KitRowValue(l10n.projectHealthSetUp),
                enabled: !_initializingGit,
                disabledReason: _initializingGit ? l10n.kitWorking : null,
                onTap: () => unawaited(_initializeGit()),
              ),
          ],
        ),
        if (initError != null)
          _inset(
            KitNotice.error(
              key: const ValueKey('git-init-error'),
              title: l10n.e7LibraryGitInitializationFailed,
              message: initError,
              retry: KitAction(
                label: l10n.isolatedTaskRetryOpen,
                onPressed: _initializingGit
                    ? null
                    : () => unawaited(_initializeGit()),
                disabledReason: _initializingGit ? l10n.kitWorking : null,
              ),
            ),
          ),
        ?done,
      ];
    }
    final branch = vcs.branch?.trim();
    final defaultBranch = vcs.defaultBranch?.trim();
    final clean = vcs.changes.isEmpty;
    return [
      ?done,
      KitRowGroup(
        label: label,
        children: [
          KitRow(
            key: const ValueKey('project-health-branch'),
            leading: KitRowIcon(AppIconography.branch),
            title: branch?.isNotEmpty == true
                ? branch!
                : l10n.e7LibraryNoActiveBranch,
            supporting: TextSpan(
              text: defaultBranch?.isNotEmpty == true
                  ? l10n.e7LibraryDefaultBranch(defaultBranch!)
                  : clean
                  ? l10n.e7LibraryWorkingTreeIsClean
                  : l10n.e7LibraryChangedFiles('${vcs.changes.length}'),
            ),
            trailing: clean
                ? null
                : _ChangeCounts(
                    additions: vcs.additions,
                    deletions: vcs.deletions,
                  ),
          ),
          if (clean)
            KitRow(
              leading: KitRowIcon(AppIconography.checks),
              title: l10n.e7LibraryNoUncommittedChanges,
            )
          else
            for (final file in vcs.changes) _ChangedFileRow(file: file),
        ],
      ),
    ];
  }

  List<Widget> _languageServiceSection(AppLocalizations l10n) {
    final label = l10n.e7LibraryLanguageServices;
    final error = _languageServicesError;
    if (error != null) {
      return [KitSectionLabel(label), _readFailed(l10n, error)];
    }
    final services = _languageServices;
    if (services == null) {
      return [KitSectionLabel(label), const KitSkeletonRows(count: 2)];
    }
    if (services.isEmpty) {
      return [
        KitSectionLabel(label),
        KitStateView(
          key: const ValueKey('project-health-no-language-services'),
          size: KitStateSize.inline,
          icon: AppIconography.code,
          title: l10n.e7LibraryNoActiveLanguageServices,
          body: l10n.e7LibraryOpenCodeActivatesThemWhileItInspectsSupported,
        ),
      ];
    }
    return [
      KitRowGroup(
        label: label,
        children: [
          for (final service in services)
            KitRow(
              leading: KitRowIcon(
                service.connected
                    ? AppIconography.checkCircle
                    : AppIconography.error,
              ),
              title: service.name,
              supporting: TextSpan(
                text: [
                  if (service.connected)
                    l10n.projectHealthRunning
                  else ...[
                    l10n.projectHealthNotRunning,
                    if (service.status.isNotEmpty) service.status,
                  ],
                  if (service.root.isNotEmpty) KitBidi.ltr(service.root),
                ].join(' · '),
              ),
              supportingMaxLines: 2,
            ),
        ],
      ),
    ];
  }

  List<Widget> _formatterSection(AppLocalizations l10n) {
    final label = l10n.e7LibraryFormatters;
    final error = _formattersError;
    if (error != null) {
      return [KitSectionLabel(label), _readFailed(l10n, error)];
    }
    final formatters = _formatters;
    if (formatters == null) {
      return [KitSectionLabel(label), const KitSkeletonRows(count: 2)];
    }
    if (formatters.isEmpty) {
      return [
        KitSectionLabel(label),
        KitStateView(
          key: const ValueKey('project-health-no-formatters'),
          size: KitStateSize.inline,
          icon: AppIconography.alignLeft,
          title: l10n.e7LibraryNoFormattersConfigured,
        ),
      ];
    }
    return [
      KitRowGroup(
        label: label,
        children: [
          for (final formatter in formatters)
            KitRow(
              leading: KitRowIcon(
                formatter.enabled
                    ? AppIconography.checkCircle
                    : AppIconography.removeCircle,
              ),
              title: formatter.name,
              supporting: TextSpan(
                text: [
                  formatter.enabled
                      ? l10n.e7LibraryEnabled
                      : l10n.e7LibraryDisabled,
                  if (formatter.extensions.isNotEmpty)
                    KitBidi.ltr(formatter.extensions.join(', ')),
                ].join(' · '),
              ),
              supportingMaxLines: 2,
            ),
        ],
      ),
    ];
  }

  @override
  void dispose() {
    _generation++;
    super.dispose();
  }
}

/// One changed file: its path, its change word, and its line counts.
class _ChangedFileRow extends StatelessWidget {
  final VersionControlFile file;

  const _ChangedFileRow({required this.file});

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // The change in words, never the server's own word.
    final word = switch (file.status) {
      'added' => l10n.kitDiffAddedFile,
      'deleted' => l10n.readerUiDeleted,
      'modified' => l10n.readerUiModified,
      _ => null,
    };
    return KitRow(
      leading: KitRowIcon(switch (file.status) {
        'added' => AppIconography.addCircle,
        'deleted' => AppIconography.removeCircle,
        _ => AppIconography.edit,
      }),
      title: file.path,
      supporting: word == null ? null : TextSpan(text: word),
      trailing: _ChangeCounts(
        additions: file.additions,
        deletions: file.deletions,
      ),
    );
  }
}

/// "+24 -3": added lines in the success tone with their sign, removed
/// lines muted with theirs; read out as words.
class _ChangeCounts extends StatelessWidget {
  final int additions;
  final int deletions;

  const _ChangeCounts({required this.additions, required this.deletions});

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    return Semantics(
      label: l10n.projectHealthLineCounts(additions, deletions),
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          KitText(
            '+$additions',
            role: KitTextRole.secondary,
            tone: KitTextTone.success,
            tabular: true,
          ),
          SizedBox(width: tokens.space2),
          KitText(
            '-$deletions',
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
            tabular: true,
          ),
        ],
      ),
    );
  }
}
