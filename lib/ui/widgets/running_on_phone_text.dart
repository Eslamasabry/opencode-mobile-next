/// The words of "Running on this phone": a process's plain name (never its
/// command line), how hard it works, its memory, and its group's label and
/// mark. Shared by the page and This phone's row.
library;

import 'package:flutter/widgets.dart';

import '../../builtin/phone_process_watch.dart';
import '../../builtin/phone_processes.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../screens/termux_processes_screen.dart'
    show formatTermuxCpuPct, formatTermuxDuration;
import 'phone_server_card.dart' show formatPhoneStorage;

/// The `runningPhoneName` kind of [p], or null when its program has no
/// plain name of its own and the kernel's short name is shown as it is.
@visibleForTesting
String? runningNameKind(PhoneProcess p) {
  if (p.owner == PhoneProcessOwner.app) return 'app';
  final name = p.name.toLowerCase();
  final command = p.command.toLowerCase();
  switch (name) {
    case 'flutter_tester':
      return 'flutterTest';
    case 'bash' || 'sh' || 'dash' || 'zsh' || 'ash' || 'fish' || 'busybox':
      return 'command';
    case 'proot' || 'proot-rs' || 'libproot.so' || 'loader':
      return 'linux';
    case 'claude':
      return 'claudeCode';
    case 'opencode' || '.opencode':
      return 'openCode';
    case 'rg':
      return 'search';
    case 'git' || 'git-remote-http' || 'git-remote-https':
      return 'git';
    case 'npm' || 'npx' || 'npm exec':
      return 'npm';
    case 'dart' || 'dartvm' || 'dartaotruntime':
      return 'dart';
    case 'flutter':
      return 'flutter';
    case 'java':
      return command.contains('gradle') ? 'gradle' : 'java';
    // Node names its main thread "MainThread" in the kernel.
    case 'node' || 'node-mainthread' || 'mainthread':
      if (command.contains('opencode')) return 'openCode';
      if (command.contains('claude')) return 'claudeCode';
      if (command.contains('paseo')) return 'agentHelper';
      if (command.contains('npm-cli') || command.contains('/npm ')) {
        return 'npm';
      }
      return 'node';
  }
  if (name.startsWith('python')) return 'python';
  // The in-app server's binary ("opencode2") and the helper's renamed
  // processes ("Paseo Supervisor", "Paseo Daemon").
  if (name.startsWith('opencode')) return 'openCode';
  if (name.startsWith('paseo')) return 'agentHelper';
  if (p.owner == PhoneProcessOwner.engine) return 'team';
  if (p.owner == PhoneProcessOwner.agentSignIn) return 'signIn';
  if (name.isEmpty) return 'other';
  return null;
}

/// "Flutter test", "Command", "Claude Code"; the kernel's own short name
/// ("make") when the program has no plain name here.
String runningProcessName(AppLocalizations l10n, PhoneProcess p) {
  final kind = runningNameKind(p);
  return kind == null ? p.name : l10n.runningPhoneName(kind);
}

/// "Idle", "Light", "Busy" or "Very busy" by share of one core.
@visibleForTesting
String runningCpuLevel(double percent) => percent < 5
    ? 'idle'
    : percent < 30
    ? 'light'
    : percent <= PhoneProcessWatch.heavyCpu
    ? 'busy'
    : 'veryBusy';

/// "Busy · 45%"; nothing measured is "0%", never "0.0%".
String runningCpuText(AppLocalizations l10n, double percent) =>
    l10n.runningPhoneCpu(
      runningCpuLevel(percent),
      percent < 0.05 ? '0' : formatTermuxCpuPct(percent),
    );

/// "43 MB"; from a gigabyte, "1.5 GB".
String runningMemoryText(AppLocalizations l10n, int bytes) {
  final mb = (bytes / (1024 * 1024)).round();
  return mb >= 1024 ? formatPhoneStorage(bytes) : l10n.termuxProcsMemoryMb(mb);
}

/// "Busy · 45% · 43 MB · running 12 min". A row has one load word, at most
/// "Busy"; "Very busy" is a group heading's word.
String runningFacts(AppLocalizations l10n, PhoneProcess p) => [
  l10n.runningPhoneCpu(
    runningCpuLevel(p.cpuPercent) == 'veryBusy'
        ? 'busy'
        : runningCpuLevel(p.cpuPercent),
    p.cpuPercent < 0.05 ? '0' : formatTermuxCpuPct(p.cpuPercent),
  ),
  if (p.rssBytes > 0) runningMemoryText(l10n, p.rssBytes),
  if (p.elapsed.inSeconds > 0)
    l10n.termuxProcsRunningFor(formatTermuxDuration(l10n, p.elapsed.inSeconds)),
].join(' · ');

/// A group's label: "OpenCode", "Left behind", "Claude Code · Fix login".
/// [title] is the conversation's title, when it is known.
String runningGroupLabel(
  AppLocalizations l10n,
  PhoneProcessGroupKind kind, {
  String? title,
}) => kind == PhoneProcessGroupKind.claudeCode && title != null
    ? l10n.runningPhoneGroupChat(title)
    : l10n.runningPhoneGroup(kind.name);

/// A group's mark, always beside its name in words.
IconData runningGroupIcon(PhoneProcessGroupKind kind) => switch (kind) {
  PhoneProcessGroupKind.leftBehind => AppIconography.warning,
  PhoneProcessGroupKind.openCode => AppIconography.server,
  PhoneProcessGroupKind.claudeCode => AppIconography.code,
  PhoneProcessGroupKind.agentHelper => AppIconography.agent,
  PhoneProcessGroupKind.aiTeam => AppIconography.kanban,
  PhoneProcessGroupKind.terminal => AppIconography.terminal,
  PhoneProcessGroupKind.setup => AppIconography.package,
  PhoneProcessGroupKind.helpers => AppIconography.tools,
  PhoneProcessGroupKind.app => AppIconography.phone,
  PhoneProcessGroupKind.other => AppIconography.processor,
};

/// The group a reading puts [node] in.
PhoneProcessGroupKind runningGroupOf(
  PhoneProcessReading reading,
  PhoneProcessNode node,
) => reading.groups
    .firstWhere(
      (g) => g.nodes.any((n) => n.pid == node.pid),
      orElse: () =>
          const PhoneProcessGroup(kind: PhoneProcessGroupKind.other, nodes: []),
    )
    .kind;
