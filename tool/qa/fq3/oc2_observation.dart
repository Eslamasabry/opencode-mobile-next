import 'common.dart';
import 'evidence.dart';

/// Diagnostic observations are separate from capability qualification facts.
/// No event bodies, error text, IDs or directory paths leave this helper.
class Oc2ProbeObservation {
  Oc2ProbeObservation({required String capability, required String directory})
    : _directory = directory {
    if (!capabilityKeys.contains(capability)) {
      throw const ProbeFailure('invalid_observation_capability');
    }
    if (!directory.startsWith('/') ||
        directory.length > 4096 ||
        directory.contains(RegExp(r'[\x00\r\n]'))) {
      throw const ProbeFailure('invalid_observation_directory');
    }
  }

  final String _directory;
  String _stage = 'create';
  String? _model;

  static const _stages = {
    'create',
    'select_model',
    'await_selection',
    'verify_selection',
    'prompt',
    'await_permission',
    'verify_permission',
    'reply_permission',
    'await_reply',
    'await_terminal',
    'verify_outcome',
    'verify_idle',
    'inventory',
    'await_tool',
    'send_receipt',
    'complete',
  };

  static const _countLimit = 10000;
  static const _eventCounters = {
    'session.retry.scheduled': 'retryCount',
    'session.model.selected': 'modelSelectedCount',
    'session.execution.started': 'executionStartedCount',
    'session.execution.succeeded': 'executionSucceededCount',
    'session.execution.failed': 'executionFailedCount',
    'session.execution.interrupted': 'executionInterruptedCount',
    'session.tool.called': 'toolCalledCount',
    'session.tool.progress': 'toolProgressCount',
    'session.tool.success': 'toolSuccessCount',
    'session.tool.failed': 'toolFailedCount',
    'permission.asked': 'permissionAskedCount',
    'permission.replied': 'permissionRepliedCount',
    'session.idle': 'idleCount',
    'session.text.delta': 'textDeltaCount',
  };

  void checkpoint(String fixedStage) {
    if (!_stages.contains(fixedStage)) {
      throw const ProbeFailure('invalid_observation_stage');
    }
    _stage = fixedStage;
  }

  void selectModel(String validatedPublicReference) {
    if (!isPublicModelReference(validatedPublicReference)) {
      throw const ProbeFailure('invalid_observation_model');
    }
    _model = validatedPublicReference;
  }

  Map<String, Object> snapshot(
    Iterable<Map<String, dynamic>> events, {
    required String sessionID,
    required int eventStart,
  }) {
    if (eventStart < 0) {
      throw const ProbeFailure('invalid_observation_start');
    }
    if (!RegExp(r'^[A-Za-z0-9][A-Za-z0-9_-]{0,159}$').hasMatch(sessionID) ||
        sessionID.contains(RegExp(r'[\r\n]'))) {
      throw const ProbeFailure('invalid_observation_session');
    }
    final counts = <String, int>{
      for (final counter in _eventCounters.values) counter: 0,
      'http503Retries': 0,
      'terminalCount': 0,
      'providerNoRouteCount': 0,
      'modelUnavailableCount': 0,
      'providerAuthCount': 0,
    };
    void increment(String counter) {
      final current = counts[counter]!;
      if (current < _countLimit) {
        counts[counter] = current + 1;
      }
    }

    for (final event in events.skip(eventStart)) {
      final data = event['data'];
      final location = event['location'];
      if (data is! Map ||
          data['sessionID'] != sessionID ||
          (event.containsKey('location') &&
              (location is! Map || location['directory'] != _directory))) {
        continue;
      }
      final type = event['type'];
      if (type is! String) {
        continue;
      }
      final counter = _eventCounters[type];
      if (counter == null) {
        continue;
      }
      increment(counter);
      if (type == 'session.execution.succeeded' ||
          type == 'session.execution.failed' ||
          type == 'session.execution.interrupted') {
        increment('terminalCount');
      }
      if (type == 'session.execution.failed' ||
          type == 'session.retry.scheduled') {
        final error = data['error'];
        if (error is Map && error['type'] == 'provider.no-route') {
          increment('providerNoRouteCount');
          final message = error['message'];
          if (message is String && message.startsWith('Model unavailable: ')) {
            increment('modelUnavailableCount');
          }
        }
        if (error is Map && error['type'] == 'provider.auth') {
          increment('providerAuthCount');
        }
      }
      if (type == 'session.retry.scheduled' && _isHttp503(data)) {
        increment('http503Retries');
      }
    }
    return {'stage': _stage, 'model': ?_model, ...counts};
  }

  static bool _isHttp503(Map<dynamic, dynamic> data) {
    final error = data['error'];
    final details = error is Map ? error['data'] : null;
    for (final fields in [
      data,
      if (error is Map) error,
      if (details is Map) details,
    ]) {
      for (final key in const ['status', 'statusCode']) {
        final status = fields[key];
        if (status is num && status.isFinite && status == 503) {
          return true;
        }
      }
    }
    // A small exact whitelist handles status-only internal messages. Neither
    // arbitrary prose nor a numeric substring is treated as an HTTP status.
    final message = error is Map ? error['message'] : null;
    return message is String &&
        message.length <= 64 &&
        const {
          'HTTP 503',
          'HTTP 503 Service Unavailable',
          'HTTP/1.1 503 Service Unavailable',
          'HTTP/2 503 Service Unavailable',
        }.contains(message);
  }
}
