import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/agent_helper_diagnostics.dart';
import 'package:opencode_mobile/domain/agent_helper_status.dart';
import 'package:opencode_mobile/domain/app_exit_history.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';

void main() {
  final at = DateTime.utc(2026, 10, 9, 12);
  Map<String, Object?> record({
    bool running = false,
    bool? requested = false,
  }) => {
    'running': running,
    'lastExitCode': 137,
    'lastExitAtMs': at.millisecondsSinceEpoch,
    'lastStopRequested': requested,
    'exitReason': 'memory_or_phantom_kill',
  };

  test('helper metadata survives restart and has cautious plain notice', () {
    final stopped = AgentHelperStatus.fromMap(record());
    expect(stopped.lastExitAt, at);
    expect(stopped.lastExitCode, 137);
    expect(stopped.unexpectedStop, isTrue);
    expect(stopped.notice, contains('Android may have'));
    expect(stopped.notice, contains('Restart'));
    expect(AgentHelperStatus.fromMap(record(running: true)).notice, isNull);
    for (final requested in [true, null]) {
      expect(
        AgentHelperStatus.fromMap(record(requested: requested)).notice,
        isNull,
      );
    }
    expect(AgentHelperStatus.fromMap({}).notice, isNull);
    final diagnosis = TurnStallDiagnosis(
      kind: TurnStallKind.helperDown,
      evidence: TurnStallEvidence(
        transportConnected: false,
        helperRunning: false,
        helperStatus: stopped,
      ),
      silentFor: const Duration(seconds: 45),
    );
    expect(diagnosis.message, stopped.notice);
  });

  test('malformed and old metadata cannot invent a dated kill', () {
    for (final timestamp in [
      null,
      -1,
      0,
      1.5,
      double.infinity,
      8640000000000001,
      'secret',
    ]) {
      expect(
        AgentHelperStatus.fromMap({
          ...record(),
          'lastExitAtMs': timestamp,
        }).lastExitAt,
        isNull,
      );
      expect(
        BuiltinServiceDiagnostics.fromMap({
          ...record(),
          'lastExitAtMs': timestamp,
        }).lastExitAtMs,
        isNull,
      );
    }
    expect(AgentHelperStatus.fromMap({'running': 'false'}).running, isNull);
    expect(
      AgentHelperStatus.fromMap({...record(), 'exitReason': 'secret'}).notice,
      isNot(contains('Android')),
    );
  });

  test(
    'recent exits names only observed unexpected helper exits without profile data',
    () {
      final android = AppExitEntry(
        reason: 3,
        importance: 100,
        at: at.subtract(const Duration(seconds: 1)),
        category: AppExitCategory.lowMemory,
      );
      final performance = BuiltinPerformance.fromMap({
        'services': {
          'agent-host.private-profile': record(running: true),
          'agent-auth.other': record(),
          'server': record(),
          'agent-host.stopped': record(requested: true),
          'agent-host.old-apk': {'lastExitCode': 137},
        },
      });
      final result = withAgentHelperExits(
        AppExitHistory(supported: true, entries: [android]),
        performance,
        limit: 10,
      );
      expect(result.entries, [android]);
      expect(result.helperExits, hasLength(1));
      final helper = result.helperExits.first;
      expect(helper.at, at);
      expect(helper.exitCode, 137);
      expect(helper.description, contains('Agent helper'));
      expect(helper.description, isNot(contains('private-profile')));
      expect(helper.description, isNot(contains('App ')));
      expect(result.entries.last, same(android));
      expect(
        withAgentHelperExits(
          result,
          const BuiltinPerformance(),
          limit: 0,
        ).helperExits,
        isEmpty,
      );
      expect(
        withAgentHelperExits(
          AppExitHistory(supported: false),
          performance,
          limit: 1,
        ).helperExits,
        hasLength(1),
      );
      expect(
        withAgentHelperExits(
          AppExitHistory(supported: true, entries: [android]),
          performance,
          limit: 1,
        ).helperExits,
        hasLength(1),
      );
    },
  );
}
