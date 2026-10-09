import '../domain/app_exit_history.dart';
import 'builtin_linux.dart';

/// Attach the latest observed exit per helper beside Android app exits.
/// Never expose the service key: its suffix identifies a private profile.
AppExitHistory withAgentHelperExits(
  AppExitHistory history,
  BuiltinPerformance performance, {
  required int limit,
}) {
  final helpers = <AgentHelperExit>[];
  for (final entry in performance.services.entries) {
    final record = entry.value;
    final at = record.lastExitAtMs;
    if (!entry.key.startsWith('agent-host.') ||
        at == null ||
        record.lastStopRequested != false) {
      continue;
    }
    helpers.add(
      AgentHelperExit(
        at: DateTime.fromMillisecondsSinceEpoch(at, isUtc: true),
        exitCode: record.lastExitCode,
        possibleResourceKill:
            record.exitReason == BuiltinServiceExitReason.memoryOrPhantomKill,
      ),
    );
  }
  helpers.sort((a, b) => b.at.compareTo(a.at));
  return AppExitHistory(
    supported: history.supported,
    entries: history.entries,
    helperExits: helpers.take(limit.clamp(0, 100)),
    error: history.error,
  );
}
