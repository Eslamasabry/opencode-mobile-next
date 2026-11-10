/// Linux › Packages: the Ubuntu packages installed in the app's own Linux
/// (searchable), install by name, remove, and Update system.
///
/// Installing, removing and updating run as one background job that
/// reports progress, survives leaving this page, stops itself after 45
/// minutes, and never runs together with phone setup (see
/// `LinuxManager.requireQuiet`). Typed names are checked against Debian's
/// naming rules and against apt before anything runs. A removal first asks
/// apt what else would go; packages Linux or the setup's tools need (and
/// anything whose removal would take one with it) are refused with the
/// reason. Errors are plain words; the phone's text is not shown.
///
/// Kit parts only.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../builtin/linux_manager/linux_packages.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'linux_manager_common.dart';

class LinuxPackagesPage extends ConsumerStatefulWidget {
  const LinuxPackagesPage({super.key});

  @override
  ConsumerState<LinuxPackagesPage> createState() => _State();
}

class _State extends ConsumerState<LinuxPackagesPage> {
  List<LinuxPackage>? _packages;
  Object? _failure;
  bool _loading = true;
  bool _planning = false;
  bool _showAll = false;
  String _query = '';
  late final LinuxJobController _job = ref.read(linuxJobProvider);
  LinuxJobStatus _lastStatus = LinuxJobStatus.none;

  @override
  void initState() {
    super.initState();
    _lastStatus = _job.state.status;
    _job.addListener(_jobChanged);
    unawaited(_refresh());
  }

  @override
  void dispose() {
    _job.removeListener(_jobChanged);
    super.dispose();
  }

  /// A job that just ended changed the list: read it again.
  void _jobChanged() {
    final status = _job.state.status;
    final ended =
        _lastStatus == LinuxJobStatus.running &&
        status != LinuxJobStatus.running;
    _lastStatus = status;
    if (ended && mounted) unawaited(_refresh());
  }

  Future<void> _refresh() async {
    try {
      final list = await ref.read(linuxManagerProvider).installedPackages();
      if (!mounted) return;
      setState(() {
        _packages = list;
        _failure = null;
        _loading = false;
      });
    } on LinuxManagerException catch (error) {
      if (!mounted) return;
      setState(() {
        _failure = error;
        _loading = false;
      });
    }
  }

  bool get _jobRunning => _job.state.running;

  Future<void> _problem(Object error, String title) async {
    if (!mounted) return;
    final l10n = AppLocalizations.of(context);
    final details = linuxProblemDetails(error);
    await showKitAlert(
      context,
      title: title,
      body: linuxProblemText(l10n, error),
      details: [
        if (details != null) KitTechnicalValue(l10n.kitDetails, details),
      ],
      icon: AppIconography.warning,
      alertKey: const ValueKey('linux-packages-problem'),
    );
  }

  Future<void> _start(
    LinuxJobKind kind,
    List<String> names,
    String title,
  ) async {
    final l10n = AppLocalizations.of(context);
    final notice = switch (kind) {
      LinuxJobKind.install => l10n.linuxMgrJobNoticeInstall,
      LinuxJobKind.remove => l10n.linuxMgrJobNoticeRemove,
      LinuxJobKind.update => l10n.linuxMgrJobNoticeUpdate,
    };
    try {
      await _job.start(kind, names, notice: notice);
    } on LinuxManagerException catch (error) {
      await _problem(error, title);
    }
  }

  Future<void> _update() async {
    final l10n = AppLocalizations.of(context);
    final yes = await showKitConfirm(
      context,
      title: l10n.linuxMgrPkgUpdateTitle,
      body: l10n.linuxMgrPkgUpdateBody,
      confirmLabel: l10n.linuxMgrPkgUpdate,
      icon: AppIconography.systemDownload,
      sheetKey: const Key('linux-update-sheet'),
      confirmKey: const Key('linux-update-confirm'),
    );
    if (yes && mounted) {
      await _start(LinuxJobKind.update, const [], l10n.linuxMgrPkgUpdateTitle);
    }
  }

  Future<void> _install() async {
    final l10n = AppLocalizations.of(context);
    final manager = ref.read(linuxManagerProvider);
    List<String>? names;
    final typed = await showKitInputDialog(
      context,
      title: l10n.linuxMgrPkgInstall,
      label: l10n.linuxMgrPkgName,
      hint: 'htop',
      helper: l10n.linuxMgrPkgNameHelper,
      confirmLabel: l10n.linuxMgrPkgCheck,
      validate: (value) =>
          parseTypedPackageNames(value) == null && value.trim().isNotEmpty
          ? l10n.linuxMgrPkgNameInvalid
          : null,
      onSubmit: (value) async {
        final parsed = parseTypedPackageNames(value);
        if (parsed == null) return l10n.linuxMgrPkgNameInvalid;
        try {
          final found = await manager.checkPackages(parsed);
          final byName = {for (final c in found) c.name: c};
          for (final name in parsed) {
            final c = byName[name];
            if (c == null || !c.available) {
              return l10n.linuxMgrPkgUnknown(name);
            }
            if (c.installed) return l10n.linuxMgrPkgAlready(name);
          }
          names = parsed;
          return null;
        } on LinuxManagerException catch (error) {
          return linuxProblemText(l10n, error);
        }
      },
      dialogKey: const Key('linux-install-dialog'),
      fieldKey: const Key('linux-install-field'),
      confirmKey: const Key('linux-install-check'),
    );
    final chosen = names;
    if (typed == null || chosen == null || !mounted) return;
    final joined = chosen.join(', ');
    final yes = await showKitConfirm(
      context,
      title: l10n.linuxMgrPkgInstallTitle(joined),
      body: l10n.linuxMgrPkgInstallBody,
      confirmLabel: l10n.linuxMgrPkgInstallLabel(joined),
      icon: AppIconography.download,
      sheetKey: const Key('linux-install-sheet'),
      confirmKey: const Key('linux-install-confirm'),
    );
    if (yes && mounted) {
      await _start(LinuxJobKind.install, chosen, l10n.linuxMgrPkgInstall);
    }
  }

  Future<void> _remove(LinuxPackage pkg) async {
    final l10n = AppLocalizations.of(context);
    if (pkg.locked) return;
    final manager = ref.read(linuxManagerProvider);
    setState(() => _planning = true);
    List<String> plan;
    try {
      plan = await manager.planRemoval([pkg.name]);
    } on LinuxManagerException catch (error) {
      if (mounted) setState(() => _planning = false);
      await _problem(error, l10n.linuxMgrPkgRemove(pkg.name));
      return;
    }
    if (!mounted) return;
    setState(() => _planning = false);
    final byName = {for (final p in _packages ?? <LinuxPackage>[]) p.name: p};
    final blockers = [
      for (final name in plan)
        if (name != pkg.name && (byName[name]?.locked ?? false)) name,
    ];
    if (blockers.isNotEmpty) {
      await showKitAlert(
        context,
        title: l10n.linuxMgrPkgRemove(pkg.name),
        body: l10n.linuxMgrPkgBlockedPlan(blockers.join(', ')),
        icon: AppIconography.warning,
        alertKey: const ValueKey('linux-remove-blocked'),
      );
      return;
    }
    final also = [
      for (final name in plan)
        if (name != pkg.name) name,
    ];
    final yes = await showKitConfirm(
      context,
      title: l10n.linuxMgrPkgRemoveTitle(pkg.name),
      body: l10n.linuxMgrPkgRemoveBody,
      confirmLabel: l10n.linuxMgrPkgRemove(pkg.name),
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
      consequenceItems: [
        if (also.isNotEmpty)
          KitConsequence(
            l10n.linuxMgrPkgAlsoRemoved(also.join(', ')),
            mark: KitConsequenceMark.lost,
          ),
      ],
      sheetKey: const Key('linux-remove-sheet'),
      confirmKey: const Key('linux-remove-confirm'),
    );
    if (yes && mounted) {
      await _start(LinuxJobKind.remove, [
        pkg.name,
        ...also,
      ], l10n.linuxMgrPkgRemove(pkg.name));
    }
  }

  Future<void> _details(LinuxPackage pkg) async {
    final l10n = AppLocalizations.of(context);
    BuildContext? inside;
    await showKitSheet<void>(
      context,
      title: pkg.name,
      subtitle: pkg.version,
      icon: AppIconography.package,
      secondary: pkg.locked
          ? null
          : KitAction(
              key: const Key('linux-pkg-details-remove'),
              label: l10n.linuxMgrPkgRemove(pkg.name),
              icon: AppIconography.delete,
              destructive: true,
              onPressed: _jobRunning
                  ? null
                  : () {
                      final sheet = inside;
                      if (sheet != null && sheet.mounted) {
                        KitSheet.close<void>(sheet);
                      }
                      unawaited(_remove(pkg));
                    },
              disabledReason: _jobRunning ? l10n.linuxMgrProblemJob : null,
            ),
      body: (sheetContext) {
        inside = sheetContext;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            if (pkg.locked)
              KitText(
                l10n.linuxMgrPkgLocked,
                key: const Key('linux-pkg-details-locked'),
              ),
            KitDetailsFold(
              values: [
                KitTechnicalValue(l10n.kitDetails, pkg.name),
                KitTechnicalValue('Version', pkg.version),
                KitTechnicalValue('KB', '${pkg.sizeKb}'),
              ],
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final packages = _packages;
    final Widget body;
    if (packages == null && _loading) {
      body = ListView(
        key: const ValueKey('linux-packages-loading'),
        padding: KitScreen.padding(context),
        children: const [KitSkeletonRows()],
      );
    } else if (packages == null) {
      body = KitStateView.error(
        key: const ValueKey('linux-packages-load-error'),
        title: l10n.linuxMgrLoadFailedTitle,
        body: linuxProblemText(l10n, _failure ?? Object()),
        details: linuxProblemDetails(_failure ?? Object()),
        retry: KitAction(label: l10n.commonRetry, onPressed: _refresh),
      );
    } else {
      body = KitRefresh(
        onRefresh: _refresh,
        child: ListView(
          key: const ValueKey('linux-packages-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [_list(context, l10n, packages)],
        ),
      );
    }
    return KitScreen(
      topBar: KitTopBar(title: l10n.linuxMgrPkgTitle),
      width: KitScreenWidth.reading,
      loading: _planning,
      loadingLabel: l10n.linuxMgrPkgChecking,
      body: body,
    );
  }

  static const _cap = 60;

  Widget _list(
    BuildContext context,
    AppLocalizations l10n,
    List<LinuxPackage> all,
  ) {
    final tokens = KitTokens.of(context);
    final rails = linuxRails(context);
    final q = _query.trim().toLowerCase();
    final matches = q.isEmpty
        ? all
        : [
            for (final p in all)
              if (p.name.contains(q)) p,
          ];
    final shown = (_showAll || q.isNotEmpty || matches.length <= _cap)
        ? matches
        : matches.take(_cap).toList();
    return ListenableBuilder(
      listenable: _job,
      builder: (context, _) {
        final running = _jobRunning;
        final why = running ? l10n.linuxMgrProblemJob : null;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const LinuxJobPanel(),
            if (_failure != null)
              Padding(
                padding: rails,
                child: KitNotice.error(
                  message: linuxProblemText(l10n, _failure!),
                  details: linuxProblemDetails(_failure!),
                  retry: KitAction(
                    label: l10n.commonRetry,
                    onPressed: _refresh,
                  ),
                ),
              ),
            Padding(
              padding: EdgeInsetsDirectional.only(bottom: tokens.sectionGap),
              child: KitRowGroup(
                children: [
                  KitRow(
                    key: const ValueKey('linux-pkg-install'),
                    leading: KitRow.icon(context, AppIconography.download),
                    title: l10n.linuxMgrPkgInstall,
                    supporting: TextSpan(text: l10n.linuxMgrPkgInstallDetail),
                    trailing: const KitChevron(),
                    enabled: !running,
                    disabledReason: why,
                    onTap: () => unawaited(_install()),
                  ),
                  KitRow(
                    key: const ValueKey('linux-pkg-update'),
                    leading: KitRow.icon(
                      context,
                      AppIconography.systemDownload,
                    ),
                    title: l10n.linuxMgrPkgUpdate,
                    supporting: TextSpan(text: l10n.linuxMgrPkgUpdateDetail),
                    supportingMaxLines: 2,
                    trailing: const KitChevron(),
                    enabled: !running,
                    disabledReason: why,
                    onTap: () => unawaited(_update()),
                  ),
                ],
              ),
            ),
            Padding(
              padding: rails,
              child: KitSearchField(
                label: l10n.linuxMgrPkgSearch,
                resultCount: q.isEmpty ? null : matches.length,
                onChanged: (value) => setState(() => _query = value),
              ),
            ),
            Padding(
              padding: rails,
              child: KitText(
                l10n.linuxMgrPkgCount(all.length),
                key: const Key('linux-pkg-count'),
                role: KitTextRole.caption,
                tabular: true,
              ),
            ),
            if (matches.isEmpty)
              Padding(
                padding: rails,
                child: KitText(
                  l10n.linuxMgrPkgNoMatch,
                  key: const Key('linux-pkg-no-match'),
                  role: KitTextRole.secondary,
                ),
              )
            else
              Padding(
                padding: EdgeInsetsDirectional.only(bottom: tokens.sectionGap),
                child: KitRowGroup(
                  key: const Key('linux-pkg-group'),
                  children: [
                    for (final pkg in shown) _row(context, l10n, pkg, running),
                    if (shown.length < matches.length)
                      KitRow(
                        key: const Key('linux-pkg-show-all'),
                        leading: KitRow.icon(context, AppIconography.expand),
                        title: l10n.linuxMgrPkgShowAll(matches.length),
                        onTap: () => setState(() => _showAll = true),
                      ),
                  ],
                ),
              ),
          ],
        );
      },
    );
  }

  Widget _row(
    BuildContext context,
    AppLocalizations l10n,
    LinuxPackage pkg,
    bool running,
  ) {
    final size = pkg.sizeKb > 0 ? linuxSize(pkg.sizeKb * 1024) : null;
    return KitRow(
      key: Key('linux-pkg-${pkg.name}'),
      leading: KitRow.icon(
        context,
        pkg.locked ? AppIconography.locked : AppIconography.package,
      ),
      title: pkg.name,
      supporting: TextSpan(text: [pkg.version, ?size].join(' · ')),
      supportingMaxLines: 2,
      trailing: const KitChevron(),
      onTap: () => unawaited(_details(pkg)),
      menu: [
        KitMenuItem(
          label: l10n.kitDetails,
          icon: AppIconography.info,
          onSelected: () => unawaited(_details(pkg)),
        ),
        if (!pkg.locked)
          KitMenuItem(
            key: Key('linux-pkg-remove-${pkg.name}'),
            label: l10n.linuxMgrPkgRemove(pkg.name),
            icon: AppIconography.delete,
            destructive: true,
            enabled: !running,
            disabledReason: running ? l10n.linuxMgrProblemJob : null,
            onSelected: () => unawaited(_remove(pkg)),
          ),
      ],
    );
  }
}
