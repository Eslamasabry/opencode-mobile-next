import 'dart:async';
import 'dart:io' show HandshakeException, SocketException, TlsException;

import '../builtin/builtin_linux.dart' show BuiltinLinux;
import '../domain/models.dart' show ApiException;
import '../domain/phone_agents.dart' show PhoneAgentStatus;
import 'analytics_events.dart';
import 'health_schema.dart';
import 'perf_traces.dart';
import 'telemetry_provider.dart';

/// What kind of failure [error] is, in the contract's fixed words
/// (`conn_attempt.fail_kind`, `send_result.fail_kind`). Only the word leaves
/// the call site: the error and its message are read here and dropped.
String failKindOf(Object? error, {bool forSend = false}) {
  if (error == null) return 'other';
  if (error is TimeoutException) return 'timeout';
  if (error is HandshakeException || error is TlsException) return 'tls';
  if (error is ApiException) {
    final code = error.statusCode;
    if (code == 401 || code == 403) return 'auth';
    if (code == 413) return forSend ? 'too_large' : 'protocol';
    if (code != null && code >= 500) return 'server_5xx';
    if (code != null && code >= 400) return forSend ? 'rejected' : 'protocol';
  }
  if (error is FormatException) return 'protocol';
  final text = error.toString().toLowerCase();
  if (error is SocketException || text.contains('socketexception')) {
    if (text.contains('host lookup') ||
        text.contains('nodename') ||
        text.contains('no address associated')) {
      return 'dns';
    }
    if (text.contains('refused')) return 'refused';
    if (text.contains('timed out') || text.contains('timeout')) {
      return 'timeout';
    }
    if (text.contains('unreachable') || text.contains('no route')) {
      return 'offline';
    }
    return 'other';
  }
  if (text.contains('handshake') ||
      text.contains('certificate') ||
      text.contains('tls') ||
      text.contains('ssl')) {
    return 'tls';
  }
  if (text.contains('unauthor') ||
      text.contains('authenticat') ||
      text.contains('password') ||
      text.contains('forbidden')) {
    return 'auth';
  }
  if (text.contains('timed out') || text.contains('timeout')) return 'timeout';
  if (text.contains('refused')) return 'refused';
  if (text.contains('host lookup')) return 'dns';
  if (text.contains('offline') || text.contains('network is unreachable')) {
    return 'offline';
  }
  return 'other';
}

/// The health flow events (section B of the contract), one function each, so
/// a call site names a result and a duration and the words come from here.
/// Each event is dropped unless the person agreed to `usage`; each also
/// leaves a breadcrumb where the contract lists one.
abstract final class HealthFlow {
  static void connAttempt({
    required String serverKind,
    required bool ok,
    required Duration elapsed,
    Object? failure,
  }) {
    AppTelemetry.crumb('conn.connect');
    AppTelemetry.track(AnalyticsEvent.connAttempt, {
      'server_kind': serverKind,
      'result': ok ? 'ok' : 'fail',
      if (!ok) 'fail_kind': failKindOf(failure),
      'ms_range': HealthRanges.connMsOf(elapsed),
    });
    AppTelemetry.current.performanceTrace(PerfSchema.connConnect, elapsed, {
      'server_kind': serverKind,
      'result': ok ? 'ok' : 'fail',
    });
  }

  static void connDrop({
    required String serverKind,
    required String reason,
    required bool recovered,
  }) {
    AppTelemetry.crumb(recovered ? 'conn.reconnect' : 'conn.drop');
    AppTelemetry.track(AnalyticsEvent.connDrop, {
      'server_kind': serverKind,
      'reason': reason,
      'recovered': recovered ? 'yes' : 'no',
    });
  }

  /// [firstReply] is how long the first reply took, or null for none.
  static void sendResult({
    required String result,
    Object? failure,
    Duration? firstReply,
    String? serverUrl,
  }) {
    AppTelemetry.crumb('chat.send');
    AppTelemetry.track(AnalyticsEvent.sendResult, {
      'result': result,
      if (result == 'fail') 'fail_kind': failKindOf(failure, forSend: true),
      'first_reply_range': HealthRanges.firstReplyOf(firstReply),
    });
    if (firstReply != null) {
      AppTelemetry.current
          .performanceTrace(PerfSchema.sendFirstReply, firstReply, {
            'server_kind': BuiltinLinux.managesServerUrl(serverUrl)
                ? 'phone'
                : 'computer',
          });
    }
  }

  /// An accepted send whose first reply took [firstReply] (null for none);
  /// [serverUrl] only tells phone from computer and is never sent.
  static void sendOk(Duration? firstReply, String? serverUrl) =>
      sendResult(result: 'ok', firstReply: firstReply, serverUrl: serverUrl);

  static void turnStall({required String stage, required Duration waited}) {
    AppTelemetry.crumb('chat.tool_wait');
    AppTelemetry.track(AnalyticsEvent.turnStall, {
      'stage': stage,
      'minutes_range': HealthRanges.stallMinutesOf(waited),
    });
  }

  static void queueFlush({required int sent, required int failed}) {
    AppTelemetry.track(AnalyticsEvent.queueFlush, {
      'sent': HealthRanges.countOf(sent),
      'failed': HealthRanges.countOf(failed),
    });
  }

  /// One built-in Linux setup step. [exit] is the exit code if it gave one.
  static void linuxSetupStep({
    required String step,
    required bool ok,
    required Duration elapsed,
    int? exit,
    String compatMode = 'none',
    double? freeGigabytes,
  }) {
    AppTelemetry.crumb('linux.step:$step');
    if (!ok) AppTelemetry.crumb('linux.fail');
    final signal = ok ? 'none' : signalOfExit(exit);
    AppTelemetry.track(AnalyticsEvent.linuxSetupStep, {
      'step': step,
      'result': ok ? 'ok' : 'fail',
      'ms_range': HealthRanges.setupMsOf(elapsed),
      'exit_kind': exitKindOf(exit, ok: ok),
      'signal': signal,
      'compat_mode': compatMode,
      if (freeGigabytes != null)
        'free_space_range': HealthRanges.storageOf(freeGigabytes),
    });
    AppTelemetry.current.performanceTrace(
      PerfSchema.linuxSetup(step),
      elapsed,
      {'step': step, 'result': ok ? 'ok' : 'fail'},
    );
  }

  static String exitKindOf(int? exit, {required bool ok}) {
    if (ok) return '0';
    if (exit == null) return 'other';
    if (exit > 128) return 'signal';
    return switch (exit) {
      0 => '0',
      1 => '1',
      126 => '126',
      127 => '127',
      _ => 'other',
    };
  }

  /// `SIGILL` ... for an exit code of 128 plus a signal, `other` for any
  /// other signal, `none` for a code that is not a signal.
  static String signalOfExit(int? exit) {
    if (exit == null || exit <= 128) return 'none';
    return switch (exit - 128) {
      4 => 'SIGILL',
      11 => 'SIGSEGV',
      7 => 'SIGBUS',
      9 => 'SIGKILL',
      _ => 'other',
    };
  }

  static void agentStart({
    required String agent,
    required bool ok,
    String failKind = 'other',
    Duration? elapsed,
  }) {
    AppTelemetry.crumb('agent.start:${_agentWord(agent)}');
    if (!ok) AppTelemetry.crumb('agent.fail');
    AppTelemetry.track(AnalyticsEvent.agentStart, {
      'agent': _agentWord(agent),
      'result': ok ? 'ok' : 'fail',
      if (!ok) 'fail_kind': failKind,
    });
    if (elapsed != null) {
      AppTelemetry.current.performanceTrace(PerfSchema.agentStart, elapsed, {
        'agent': _agentWord(agent),
        'result': ok ? 'ok' : 'fail',
      });
    }
  }

  /// An agent that could not start before reaching its host: [status] is its
  /// row's state, [error] only tells a timeout.
  static void agentNotStarted(
    String agentId,
    PhoneAgentStatus? status,
    Object error,
  ) => agentStart(
    agent: agentId == 'claude' ? 'claude_code' : agentId,
    ok: false,
    failKind: switch (status) {
      PhoneAgentStatus.needsInstall => 'not_installed',
      PhoneAgentStatus.signedOut => 'needs_sign_in',
      _ => error is TimeoutException ? 'timeout' : 'other',
    },
  );

  static String _agentWord(String agent) =>
      HealthWords.agents.contains(agent) ? agent : 'other';

  static void bgServiceEnd({
    required String reason,
    required bool batteryExempt,
  }) {
    AppTelemetry.track(AnalyticsEvent.bgServiceEnd, {
      'reason': reason,
      'battery_exempt': batteryExempt ? 'yes' : 'no',
    });
  }

  static void updateResult({
    required String kind,
    required String result,
    String? failKind,
  }) {
    AppTelemetry.crumb(switch (result) {
      'installed' => 'update.install',
      'failed' => 'update.fail',
      _ => 'update.offer',
    });
    AppTelemetry.track(AnalyticsEvent.updateResult, {
      'kind': kind,
      'result': result,
      'fail_kind': ?failKind,
    });
  }
}
