/// "Running on this phone": the app's processes grouped under what started
/// them, as a tree, with the heavy and stuck marks that need more than one
/// reading.
///
/// [PhoneProcessWatch] reads [PhoneProcesses] only when a page asks (This
/// phone and Running on this phone sample while they are open, never in
/// the background) and keeps a short history per process, so that "heavy"
/// means it stayed over [PhoneProcessWatch.heavyCpu] for
/// [PhoneProcessWatch.heavyFor] across readings, never one busy second.
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'phone_processes.dart';

/// What started a group of processes, in the order the page shows them.
enum PhoneProcessGroupKind {
  /// Left running by a Linux that has ended (native `orphan`).
  leftBehind,

  /// The in-app OpenCode server and what it runs.
  openCode,

  /// One Claude Code conversation (a process with an agent session and
  /// everything under it).
  claudeCode,

  /// The agent helper (Paseo) and its sign-ins, outside any conversation.
  agentHelper,

  /// The AI Team engine.
  aiTeam,

  /// A terminal session.
  terminal,

  /// A setup step or a check.
  setup,

  /// Another named service of the app.
  helpers,

  /// The app's own Android processes.
  app,

  /// None of the above.
  other,
}

/// One process in its group's tree.
class PhoneProcessNode {
  const PhoneProcessNode({
    required this.process,
    required this.depth,
    required this.root,
    required this.heavy,
    required this.stuck,
    this.heavyFor,
    this.session,
  });

  final PhoneProcess process;

  /// 0 for a process whose parent is not in the same group.
  final int depth;

  /// The root of a tree the app runs (a server, the agent helper, a
  /// terminal, a setup step): native refuses to stop it, so no Stop is
  /// offered.
  final bool root;

  /// Over [PhoneProcessWatch.heavyCpu] in every reading for at least
  /// [PhoneProcessWatch.heavyFor].
  final bool heavy;

  /// Left behind, or about 100% for more than
  /// [PhoneProcessWatch.stuckAfter] with no conversation above it.
  final bool stuck;

  /// How long it has been heavy, when [heavy].
  final Duration? heavyFor;

  /// The agent session of the conversation above it (its own or an
  /// ancestor's), if any.
  final String? session;

  int get pid => process.pid;

  bool get leftBehind => process.owner == PhoneProcessOwner.orphan;

  /// Whether this page offers to stop it: never the app nor a tree's root.
  bool get stoppable => !root && process.owner != PhoneProcessOwner.app;
}

/// The processes one thing started, as a tree in reading order.
class PhoneProcessGroup {
  const PhoneProcessGroup({
    required this.kind,
    required this.nodes,
    this.session,
  });

  final PhoneProcessGroupKind kind;

  /// The conversation's agent session, for [PhoneProcessGroupKind.claudeCode].
  final String? session;

  /// Parents before their children, siblings by process id.
  final List<PhoneProcessNode> nodes;

  /// A stable id for keys and animations.
  String get id => session == null ? kind.name : '${kind.name}:$session';

  double get cpuPercent =>
      nodes.fold(0, (sum, n) => sum + n.process.cpuPercent);
  int get rssBytes => nodes.fold(0, (sum, n) => sum + n.process.rssBytes);
}

/// One reading, grouped and marked.
class PhoneProcessReading {
  const PhoneProcessReading({
    required this.snapshot,
    required this.groups,
    required this.at,
  });

  final PhoneProcessSnapshot snapshot;
  final List<PhoneProcessGroup> groups;
  final DateTime at;

  Iterable<PhoneProcessNode> get nodes => groups.expand((g) => g.nodes);

  /// The processes Android counts toward its limit: all but the app's own.
  int get childCount =>
      snapshot.processes.where((p) => p.owner != PhoneProcessOwner.app).length;

  bool get nearLimit => childCount >= PhoneProcessWatch.nearLimit;

  List<PhoneProcessNode> get leftBehind => [
    for (final node in nodes)
      if (node.leftBehind && node.stoppable) node,
  ];

  /// Heavy ones, the busiest first.
  List<PhoneProcessNode> get heavy => [
    for (final node in nodes)
      if (node.heavy) node,
  ]..sort((a, b) => b.process.cpuPercent.compareTo(a.process.cpuPercent));

  int get rssBytes => snapshot.processes.fold(0, (sum, p) => sum + p.rssBytes);

  /// Something on This phone should point at the page.
  bool get needsLook => leftBehind.isNotEmpty || heavy.isNotEmpty;
}

/// What the watch remembers about one process between readings.
class _Track {
  _Track(this.name, this.elapsed);

  String name;
  Duration elapsed;
  DateTime? highSince;
  double lastCpu = 0;

  bool sameAs(PhoneProcess p) =>
      p.name == name && p.elapsed + const Duration(seconds: 2) >= elapsed;
}

/// Reads the app's processes on demand and marks the heavy and stuck ones
/// from the readings so far. One per app ([phoneProcessWatchProvider]), so
/// This phone and Running on this phone share the history.
class PhoneProcessWatch {
  PhoneProcessWatch({PhoneProcesses? processes, DateTime Function()? clock})
    : _processes = processes ?? PhoneProcesses(),
      _clock = clock ?? DateTime.now;

  /// Over this share of one core counts toward "heavy".
  static const heavyCpu = 80.0;

  /// For at least this long, across readings.
  static const heavyFor = Duration(minutes: 2);

  /// "About 100%": at or over this in two readings in a row.
  static const stuckCpu = 90.0;

  /// For a process that has run longer than this.
  static const stuckAfter = Duration(minutes: 10);

  /// Readings further apart than this do not prove a process stayed busy
  /// in between, so a streak starts again.
  static const maxGap = Duration(seconds: 30);

  /// Android stops an app's child processes above [androidLimit], the
  /// oldest first; the page warns from [nearLimit].
  static const androidLimit = 32;
  static const nearLimit = 28;

  final PhoneProcesses _processes;
  final DateTime Function() _clock;
  final _tracks = <int, _Track>{};
  DateTime? _lastAt;
  Future<PhoneProcessReading>? _inFlight;

  /// The last reading, if any.
  PhoneProcessReading? get last => _last;
  PhoneProcessReading? _last;

  /// Reads now (about a second); callers that ask meanwhile share it.
  Future<PhoneProcessReading> sample() =>
      _inFlight ??= _sample().whenComplete(() => _inFlight = null);

  Future<PhoneProcessReading> _sample() async {
    final snapshot = await _processes.list();
    return record(snapshot, _clock());
  }

  /// Stops exactly [pids].
  Future<PhoneProcessStopResult> stop(Iterable<int> pids) =>
      _processes.stop(pids);

  /// Adds [snapshot], taken at [at], to the history and groups it.
  PhoneProcessReading record(PhoneProcessSnapshot snapshot, DateTime at) {
    final previousAt = _lastAt;
    final continuous =
        previousAt != null && at.difference(previousAt) <= maxGap;
    _lastAt = at;
    final live = <int>{};
    final heavy = <int, Duration>{};
    final aboutFull = <int>{};
    for (final p in snapshot.processes) {
      live.add(p.pid);
      final seen = _tracks[p.pid];
      final known = seen != null && continuous && seen.sameAs(p);
      final track = known ? seen : _tracks[p.pid] = _Track(p.name, p.elapsed);
      final high = p.cpuPercent > heavyCpu;
      track.highSince = high ? (known ? track.highSince ?? at : at) : null;
      final since = track.highSince;
      if (since != null && at.difference(since) >= heavyFor) {
        heavy[p.pid] = at.difference(since);
      }
      if (known && track.lastCpu >= stuckCpu && p.cpuPercent >= stuckCpu) {
        aboutFull.add(p.pid);
      }
      track
        ..lastCpu = p.cpuPercent
        ..elapsed = p.elapsed
        ..name = p.name;
    }
    _tracks.removeWhere((pid, _) => !live.contains(pid));
    final reading = PhoneProcessReading(
      snapshot: snapshot,
      groups: groupPhoneProcesses(
        snapshot.processes,
        heavy: heavy,
        aboutFull: aboutFull,
      ),
      at: at,
    );
    _last = reading;
    return reading;
  }
}

/// Groups [processes] under what started them, each group a tree.
/// [heavy] maps a pid to how long it has been heavy; [aboutFull] holds the
/// pids at about 100% in the last two readings.
List<PhoneProcessGroup> groupPhoneProcesses(
  List<PhoneProcess> processes, {
  Map<int, Duration> heavy = const {},
  Set<int> aboutFull = const {},
}) {
  final byPid = {for (final p in processes) p.pid: p};

  // The agent session above a process: its own, else the nearest
  // ancestor's (Claude Code's tools carry none themselves).
  final sessions = <int, String?>{};
  String? sessionOf(PhoneProcess p) {
    if (sessions.containsKey(p.pid)) return sessions[p.pid];
    sessions[p.pid] = null; // a loop in a bad reading ends here
    final parent = byPid[p.ppid];
    final found =
        p.session ??
        (parent == null || parent.pid == p.pid ? null : sessionOf(parent));
    return sessions[p.pid] = found;
  }

  PhoneProcessGroupKind kindOf(PhoneProcess p) => switch (p.owner) {
    PhoneProcessOwner.orphan => PhoneProcessGroupKind.leftBehind,
    PhoneProcessOwner.opencode => PhoneProcessGroupKind.openCode,
    PhoneProcessOwner.paseo || PhoneProcessOwner.agentSignIn =>
      sessionOf(p) == null
          ? PhoneProcessGroupKind.agentHelper
          : PhoneProcessGroupKind.claudeCode,
    PhoneProcessOwner.engine => PhoneProcessGroupKind.aiTeam,
    PhoneProcessOwner.terminal => PhoneProcessGroupKind.terminal,
    PhoneProcessOwner.task => PhoneProcessGroupKind.setup,
    PhoneProcessOwner.helper => PhoneProcessGroupKind.helpers,
    PhoneProcessOwner.app => PhoneProcessGroupKind.app,
    PhoneProcessOwner.other => PhoneProcessGroupKind.other,
  };

  String groupId(PhoneProcess p) {
    final kind = kindOf(p);
    return kind == PhoneProcessGroupKind.claudeCode
        ? '${kind.name}:${sessionOf(p)}'
        : kind.name;
  }

  // The root of a tree the app runs: its parent is in no tree of the same
  // owner (native refuses to stop these).
  const treeOwners = {
    PhoneProcessOwner.opencode,
    PhoneProcessOwner.paseo,
    PhoneProcessOwner.agentSignIn,
    PhoneProcessOwner.engine,
    PhoneProcessOwner.helper,
    PhoneProcessOwner.task,
    PhoneProcessOwner.terminal,
  };
  bool isRoot(PhoneProcess p) =>
      treeOwners.contains(p.owner) && byPid[p.ppid]?.owner != p.owner;

  final members = <String, List<PhoneProcess>>{};
  for (final p in processes) {
    (members[groupId(p)] ??= []).add(p);
  }

  final groups = <PhoneProcessGroup>[];
  for (final entry in members.entries) {
    final inGroup = {for (final p in entry.value) p.pid};
    final children = <int, List<PhoneProcess>>{};
    final roots = <PhoneProcess>[];
    for (final p in entry.value) {
      if (p.ppid != p.pid && inGroup.contains(p.ppid)) {
        (children[p.ppid] ??= []).add(p);
      } else {
        roots.add(p);
      }
    }
    final nodes = <PhoneProcessNode>[];
    final placed = <int>{};
    void visit(PhoneProcess p, int depth) {
      if (!placed.add(p.pid)) return;
      final heavyFor = heavy[p.pid];
      final root = isRoot(p);
      final session = sessionOf(p);
      final stuck =
          p.owner == PhoneProcessOwner.orphan ||
          (!root &&
              p.owner != PhoneProcessOwner.app &&
              session == null &&
              p.elapsed > PhoneProcessWatch.stuckAfter &&
              aboutFull.contains(p.pid));
      nodes.add(
        PhoneProcessNode(
          process: p,
          depth: depth,
          root: root,
          heavy: heavyFor != null,
          heavyFor: heavyFor,
          stuck: stuck,
          session: session,
        ),
      );
      final kids = (children[p.pid] ?? const <PhoneProcess>[]).toList()
        ..sort((a, b) => a.pid.compareTo(b.pid));
      for (final kid in kids) {
        visit(kid, depth + 1);
      }
    }

    roots.sort((a, b) => a.pid.compareTo(b.pid));
    for (final root in roots) {
      visit(root, 0);
    }
    // A parent loop in a bad reading leaves members unplaced: shown flat.
    for (final p in entry.value) {
      visit(p, 0);
    }
    final first = entry.value.first;
    final kind = kindOf(first);
    groups.add(
      PhoneProcessGroup(
        kind: kind,
        session: kind == PhoneProcessGroupKind.claudeCode
            ? sessionOf(first)
            : null,
        nodes: List.unmodifiable(nodes),
      ),
    );
  }
  groups.sort((a, b) {
    final order = a.kind.index.compareTo(b.kind.index);
    if (order != 0) return order;
    return a.nodes.first.pid.compareTo(b.nodes.first.pid);
  });
  return List.unmodifiable(groups);
}

/// The app's one watch: This phone and Running on this phone share its
/// history. Tests override it with a watch over a fake [PhoneProcesses].
final phoneProcessWatchProvider = Provider<PhoneProcessWatch>(
  (ref) => PhoneProcessWatch(),
);
