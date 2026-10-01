import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/server_gateway.dart' show ProductException;
import '../../domain/workspace_paths.dart' show managedProjectsDirectory;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit_needs_you.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_text.dart';
import 'product_states.dart' show showProductError;
import 'relative_time.dart';
import 'session_title.dart';
import '../../domain/team_directories.dart';
import '../screens/shared_storage_access_flow.dart';

/// Working in several projects at once, from the Work tab.
///
/// The app shows one project at a time; the server does not work that way.
/// A run started in one project keeps going when you look at another. This
/// section, under the current project's own conversations, lists the other
/// projects in play once each: its name and what is going on there
/// (needs you, running, unreviewed, or when it was last used). Tapping a
/// row switches to that project; the trailing button opens the conversation
/// that is live there, when there is one.
///
/// It replaces the old chip strip plus "In other projects" list, which named
/// the same projects twice (work-tab cleanup, 2026-09-24).
class OtherProjectsPanel extends StatefulWidget {
  const OtherProjectsPanel({
    super.key,
    required this.controller,
    this.currentDirectory,
    this.onAllProjects,
    this.maxShown = 3,
  });

  final ConnectionController controller;

  /// The project the Work tab is about, when it is not open yet (being
  /// restored): it is the header's, never an "other" project. Defaults to
  /// the open folder.
  final String? currentDirectory;

  /// Opens the full project list; shown as "All projects" when there are
  /// more than [maxShown] other projects.
  final VoidCallback? onAllProjects;
  final int maxShown;

  @override
  State<OtherProjectsPanel> createState() => _OtherProjectsPanelState();
}

/// One other project and what is going on there, already worked out.
class _ProjectRow {
  _ProjectRow(this.directory, this.order);

  final String directory;

  /// Position in the recent-projects order, for a stable sort.
  final int order;

  /// The workspace last used there, reopened with the folder.
  String? workspace;
  String? name;
  int running = 0;
  int waiting = 0;
  bool unreviewed = false;
  int? lastActivity;

  /// The conversation the trailing button opens: waiting, else running.
  ElsewhereConversation? live;
}

class _OtherProjectsPanelState extends State<OtherProjectsPanel> {
  List<ElsewhereConversation> _elsewhere = const [];
  (int, int, String?)? _loadedFor;
  Timer? _timer;
  String? _opening;
  String? _switching;
  bool _loading = false;

  ConnectionController get _conn => widget.controller;

  (int, int, String?) get _scope =>
      (_conn.connectionRevision, _conn.locationRevision, _conn.profile?.id);

  @override
  void initState() {
    super.initState();
    _conn.addListener(_changed);
    _conn.elsewhereAttention.addListener(_changed);
    // What is running elsewhere changes without any event reaching this
    // project's stream, so it is looked up again while the tab is open.
    _timer = Timer.periodic(const Duration(seconds: 20), (_) => _load());
    _load();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _conn.elsewhereAttention.removeListener(_changed);
    _conn.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    // The rows read the controller directly (recent projects, the current
    // one), so any change redraws them; a new project or server reloads the
    // conversations as well.
    if (mounted) setState(() {});
    if (_loadedFor != _scope) _load();
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    final scope = _scope;
    try {
      final found = await _conn.conversationsElsewhere(limit: 12);
      if (!mounted || scope != _scope) return;
      setState(() {
        _elsewhere = found;
        _loadedFor = scope;
      });
    } catch (_) {
      // A convenience list: the Work tab stands without it.
      if (mounted && scope == _scope) setState(() => _loadedFor = scope);
    } finally {
      _loading = false;
      // The project changed while this was in flight: look again now, not
      // at the next tick.
      if (mounted && scope != _scope) unawaited(_load());
    }
  }

  /// Hosts without the app's delegates (isolated previews, some tests) fall
  /// back to English, as the rest of the Work tab does.
  static AppLocalizations _strings(BuildContext context) =>
      Localizations.of<AppLocalizations>(context, AppLocalizations) ??
      lookupAppLocalizations(const Locale('en'));

  static String _folderName(String directory) {
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }

  static bool _isProject(String? directory, String? here) =>
      directory != null &&
      directory != here &&
      // The phone server's folder of projects is not a project.
      directory.replaceAll(RegExp(r'/+$'), '') != managedProjectsDirectory &&
      // Nor is a folder the AI Team made for its own agents.
      !isAiTeamDirectory(directory);

  Future<void> _switchTo(String directory, {String? workspace}) async {
    if (_switching != null) return;
    final access = await SharedStorageAccessFlow.ensure(
      context,
      _conn.profile,
      directory,
    );
    if (!mounted || access != SharedStorageOutcome.proceed) return;
    setState(() => _switching = directory);
    try {
      await _conn.selectLocation(directory: directory, workspace: workspace);
      final error = _conn.locationError;
      if (error != null && mounted) showProductError(context, error);
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _switching = null);
    }
  }

  Future<void> _open(ElsewhereConversation item) async {
    if (_opening != null) return;
    final id = item.session.id;
    if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(id)) return;
    final profileID = _conn.profile?.id;
    setState(() => _opening = id);
    final navigator = Navigator.of(context);
    final strings = _strings(context);
    try {
      // The conversation's project becomes the current one: everything a
      // conversation needs (its events, approvals, files) is per project.
      await _conn.selectLocationForExistingSession(
        directory: item.directory,
        workspace: item.session.workspaceID,
      );
      if (_conn.profile?.id != profileID || _conn.directory != item.directory) {
        throw ProductException(strings.e7WorkspaceLocationChangedReturn);
      }
      await navigator.pushNamed('/chat/$id');
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _opening = null);
    }
  }

  /// Every other project once, needs-you first, then running, then in the
  /// order they were last used.
  List<_ProjectRow> _rows() {
    final here = _conn.directory ?? widget.currentDirectory;
    final rows = <String, _ProjectRow>{};
    _ProjectRow row(String directory) =>
        rows.putIfAbsent(directory, () => _ProjectRow(directory, rows.length));
    for (final location in _conn.recentLocations) {
      if (!_isProject(location.directory, here)) continue;
      row(location.directory!).workspace = location.workspace;
    }
    final live = {
      for (final project in _conn.elsewhereAttention.activity(except: here))
        if (_isProject(project.directory, here)) project.directory: project,
    };
    for (final directory in live.keys) {
      row(directory);
    }
    for (final item in _elsewhere) {
      if (!_isProject(item.directory, here)) continue;
      final entry = row(item.directory);
      entry.name ??= item.projectName?.trim().isNotEmpty == true
          ? item.projectName!.trim()
          : null;
      final updated = item.session.time?.updated ?? item.session.time?.created;
      if (updated != null && (entry.lastActivity ?? 0) < updated) {
        entry.lastActivity = updated;
      }
      final activity = live[item.directory];
      final waiting = activity?.waiting.contains(item.session.id) ?? false;
      final running =
          item.running ||
          (activity?.running.contains(item.session.id) ?? false);
      if (running) entry.running++;
      if (waiting) {
        entry.live = item;
      } else if (running && entry.live == null) {
        entry.live = item;
      }
      if (!running &&
          !waiting &&
          item.session.time?.idle != null &&
          _conn.isSessionUnread(item.session)) {
        entry.unreviewed = true;
      }
    }
    for (final entry in rows.values) {
      final activity = live[entry.directory];
      if (activity == null) continue;
      entry.waiting = activity.waiting.length;
      if (activity.running.length > entry.running) {
        entry.running = activity.running.length;
      }
    }
    final sorted = rows.values.toList()
      ..sort((a, b) {
        final byWaiting = (b.waiting > 0 ? 1 : 0) - (a.waiting > 0 ? 1 : 0);
        if (byWaiting != 0) return byWaiting;
        final byRunning = (b.running > 0 ? 1 : 0) - (a.running > 0 ? 1 : 0);
        if (byRunning != 0) return byRunning;
        return a.order.compareTo(b.order);
      });
    return sorted;
  }

  @override
  Widget build(BuildContext context) {
    final strings = _strings(context);
    final rows = _rows();
    if (rows.isEmpty) return const SizedBox.shrink();
    final shown = rows.take(widget.maxShown).toList();
    final onAll = widget.onAllProjects;
    // One panel of rows under its section name (KIT-27, VL §5).
    return KitRowGroup(
      key: const Key('other-projects-panel'),
      label: strings.workOtherProjects,
      children: [
        for (final entry in shown)
          _OtherProjectTile(
            key: ValueKey('other-project-${entry.directory}'),
            entry: entry,
            name: entry.name ?? _folderName(entry.directory),
            strings: strings,
            busy: _switching != null || _opening != null,
            onTap: () => _switchTo(entry.directory, workspace: entry.workspace),
            onOpenLive: entry.live == null ? null : () => _open(entry.live!),
            onForget: () => _conn.forgetRecentLocation(entry.directory),
          ),
        if (rows.length > shown.length && onAll != null)
          KitRow(
            key: const ValueKey('other-projects-all'),
            leading: KitRow.icon(context, AppIconography.folders),
            title: strings.workAllProjects,
            trailing: const KitChevron(),
            onTap: onAll,
          ),
      ],
    );
  }
}

/// One other project as a [KitRow] (kit only, shared-shell-1). A project
/// that needs the person leads with the one needs-you mark and word
/// ([KitNeedsYou], LOOK-24); running and unreviewed are said in words
/// (STATE-9), never by colour alone. Long-press or right-click opens the
/// row's menu (KIT-28): Remove from recent projects, which deletes nothing.
class _OtherProjectTile extends StatelessWidget {
  const _OtherProjectTile({
    super.key,
    required this.entry,
    required this.name,
    required this.strings,
    required this.busy,
    required this.onTap,
    required this.onOpenLive,
    required this.onForget,
  });

  final _ProjectRow entry;
  final String name;
  final AppLocalizations strings;

  /// A switch or open from this section is in flight.
  final bool busy;
  final VoidCallback onTap;
  final VoidCallback? onOpenLive;
  final VoidCallback onForget;

  /// The needs-you word without its trailing separator, for a row whose
  /// supporting line has nothing after it.
  static TextSpan _needsYouAlone(BuildContext context) {
    final span = KitNeedsYou.span(context);
    return TextSpan(
      text: span.text?.replaceFirst(RegExp(r'\s*·\s*$'), ''),
      style: span.style,
    );
  }

  @override
  Widget build(BuildContext context) {
    final when = entry.lastActivity == null
        ? null
        : relativeTimeLabel(entry.lastActivity!, l10n: strings);
    final live = entry.live;
    final liveTitle = live == null
        ? null
        : presentedSessionTitle(
            live.session,
            fallback: strings.otherProjectsUntitled,
            l10n: strings,
          );
    final rest = liveTitle ?? when;
    final waiting = entry.waiting > 0;
    // Needs you outranks running outranks unreviewed: the first is stuck.
    final String? word = waiting
        ? null
        : entry.running > 0
        ? strings.workRunningCount(entry.running)
        : entry.unreviewed
        ? strings.workUnreviewed
        : null;
    final wordStyle = KitText.styleOf(
      context,
      KitTextRole.label,
      tone: KitTextTone.primary,
    );
    final InlineSpan? supporting = waiting
        ? TextSpan(
            children: [
              if (rest == null)
                _needsYouAlone(context)
              else ...[
                KitNeedsYou.span(context),
                TextSpan(text: rest),
              ],
            ],
          )
        : word == null && rest == null
        ? null
        : TextSpan(
            children: [
              if (word != null) TextSpan(text: word, style: wordStyle),
              if (word != null && rest != null) const TextSpan(text: ' · '),
              if (rest != null) TextSpan(text: rest),
            ],
          );
    // Switching or opening shows on the screen's one loading bar (the
    // folder is loading), so the row itself stays still.
    return KitRow(
      title: name,
      leading: waiting
          ? KitNeedsYou.mark()
          : KitRow.icon(context, AppIconography.files),
      supporting: supporting,
      onTap: busy ? null : onTap,
      // A tap switches to the project; its rarer acts are on long-press or
      // right-click, each naming what it acts on (R2): open its live
      // conversation, or take the project off the list (nothing is
      // deleted).
      menuLabel: name,
      menu: [
        if (onOpenLive != null && liveTitle != null)
          KitMenuItem(
            key: ValueKey('other-project-open-${entry.directory}'),
            label: strings.workOpenLiveConversation(liveTitle),
            enabled: !busy,
            onSelected: onOpenLive!,
          ),
        KitMenuItem(label: strings.otherProjectsForget, onSelected: onForget),
      ],
    );
  }
}
