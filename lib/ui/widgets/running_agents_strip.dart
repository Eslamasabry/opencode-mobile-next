import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../app_theme.dart';
import 'session_title.dart';

/// One session in the family the chat belongs to: the parent that delegated,
/// the current session, and every sibling or child subagent.
class RunningAgentEntry {
  const RunningAgentEntry({
    required this.session,
    required this.busy,
    required this.current,
    required this.relation,
  });

  final Session session;
  final bool busy;
  final bool current;
  final RunningAgentRelation relation;

  String get label => relation == RunningAgentRelation.parent
      ? 'Parent · ${presentedSessionTitle(session, fallback: 'Main session')}'
      : presentedSessionTitle(
          session,
          fallback: session.agent?.isNotEmpty == true
              ? session.agent!
              : 'Subagent',
        );
}

enum RunningAgentRelation { parent, current, sibling, child }

/// Builds the strip entries for [sessionID] from the session map and the set
/// of busy sessions: the parent (when delegated), siblings sharing that
/// parent, and children this session delegated to. Returns an empty list
/// unless at least one *other* member of the family is running — the strip
/// exists to switch between concurrently working agents, not to list history.
List<RunningAgentEntry> runningAgentEntries({
  required String sessionID,
  required Map<String, Session> sessions,
  required Set<String> busy,
}) {
  final current = sessions[sessionID];
  if (current == null) return const [];
  final parentID = current.parentID;
  final parent = parentID == null ? null : sessions[parentID];
  int created(Session s) => s.time?.created ?? 0;
  final siblings =
      sessions.values
          .where((s) => parentID != null && s.parentID == parentID)
          .toList()
        ..sort((a, b) => created(a).compareTo(created(b)));
  final children =
      sessions.values.where((s) => s.parentID == sessionID).toList()
        ..sort((a, b) => created(a).compareTo(created(b)));
  final entries = <RunningAgentEntry>[
    if (parent != null)
      RunningAgentEntry(
        session: parent,
        busy: busy.contains(parent.id),
        current: false,
        relation: RunningAgentRelation.parent,
      ),
    if (parent == null && !children.any((s) => s.id == sessionID))
      RunningAgentEntry(
        session: current,
        busy: busy.contains(sessionID),
        current: true,
        relation: RunningAgentRelation.current,
      ),
    for (final s in siblings)
      RunningAgentEntry(
        session: s,
        busy: busy.contains(s.id),
        current: s.id == sessionID,
        relation: s.id == sessionID
            ? RunningAgentRelation.current
            : RunningAgentRelation.sibling,
      ),
    for (final s in children)
      RunningAgentEntry(
        session: s,
        busy: busy.contains(s.id),
        current: false,
        relation: RunningAgentRelation.child,
      ),
  ];
  final othersRunning = entries.any((e) => !e.current && e.busy);
  if (!othersRunning) return const [];
  // Running agents first so the switch target is one tap away, then the
  // current session for orientation, then whatever has finished.
  final running = entries.where((e) => e.busy && !e.current).toList();
  final self = entries.where((e) => e.current).toList();
  final idle = entries.where((e) => !e.busy && !e.current).toList();
  return [...running, ...self, ...idle];
}

/// A one-line chip strip above the composer that names every agent in the
/// current session's family and lets the user jump to one that is running.
class RunningAgentsStrip extends StatelessWidget {
  const RunningAgentsStrip({
    super.key,
    required this.entries,
    required this.onOpen,
  });

  final List<RunningAgentEntry> entries;
  final ValueChanged<Session> onOpen;

  @override
  Widget build(BuildContext context) {
    if (entries.isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final runningCount = entries.where((e) => e.busy).length;
    return Semantics(
      container: true,
      label: '$runningCount agents running',
      child: Padding(
        key: const ValueKey('running-agents-strip'),
        padding: const EdgeInsets.fromLTRB(8, 4, 8, 6),
        child: SizedBox(
          height: 36,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 2),
            itemCount: entries.length,
            separatorBuilder: (_, _) => const SizedBox(width: 6),
            itemBuilder: (context, index) {
              final entry = entries[index];
              final selected = entry.current;
              final color = switch (entry.relation) {
                RunningAgentRelation.parent => scheme.tertiary,
                _ => scheme.primary,
              };
              return Material(
                key: ValueKey('running-agent-${entry.session.id}'),
                color: selected
                    ? scheme.primaryContainer.withValues(alpha: .7)
                    : scheme.surfaceContainerHigh.withValues(alpha: .8),
                shape: StadiumBorder(
                  side: BorderSide(
                    color: selected
                        ? scheme.primary.withValues(alpha: .7)
                        : AppTheme.hairline(theme),
                  ),
                ),
                child: InkWell(
                  customBorder: const StadiumBorder(),
                  onTap: selected ? null : () => onOpen(entry.session),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 11),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (entry.busy)
                          _LiveDot(color: color)
                        else
                          Icon(
                            entry.relation == RunningAgentRelation.parent
                                ? Icons.arrow_upward_rounded
                                : Icons.check_rounded,
                            size: 13,
                            color: scheme.onSurfaceVariant,
                          ),
                        const SizedBox(width: 7),
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 180),
                          child: Text(
                            entry.label,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.labelMedium?.copyWith(
                              fontWeight: selected
                                  ? FontWeight.w600
                                  : FontWeight.w500,
                              color: selected
                                  ? scheme.onPrimaryContainer
                                  : scheme.onSurface,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}

/// A small breathing dot marking a session the server is still working on.
class _LiveDot extends StatefulWidget {
  const _LiveDot({required this.color});
  final Color color;

  @override
  State<_LiveDot> createState() => _LiveDotState();
}

class _LiveDotState extends State<_LiveDot>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
  );
  bool _animating = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final animate = !MediaQuery.disableAnimationsOf(context);
    if (animate == _animating) return;
    _animating = animate;
    if (animate) {
      _c.repeat(reverse: true);
    } else {
      _c.stop();
      _c.value = 1;
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: _c,
    builder: (context, _) {
      final t = Curves.easeInOut.transform(_c.value);
      return Container(
        width: 8,
        height: 8,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: widget.color.withValues(alpha: .55 + .45 * t),
          boxShadow: AppTheme.glow(widget.color, strength: .25 + .35 * t),
        ),
      );
    },
  );
}
