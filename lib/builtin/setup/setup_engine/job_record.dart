part of '../setup_engine.dart';

/// Native failures whose category is safe to use independently of log text.
enum SetupFailureKind { persistence }

/// setup.json, read.
@immutable
class SetupJobRecord {
  const SetupJobRecord({
    required this.jobId,
    required this.state,
    required this.components,
    this.current,
    this.startedAt = 0,
    this.updatedAt = 0,
    this.error,
    this.logTail = '',
    this.params = const {},
    this.host,
    this.failureKind,
  });

  final SetupFailureKind? failureKind;

  /// Missing only for legacy built-in jobs.
  final String? host;
  final String jobId;

  /// running | done | failed | cancelled | interrupted
  final String state;
  final String? current;
  final List<SetupJobComponent> components;
  final int startedAt;
  final int updatedAt;
  final String? error;
  final String logTail;
  final Map<String, Map<String, String>> params;

  bool get canContinue =>
      state == 'failed' || state == 'interrupted' || state == 'cancelled';

  SetupJobComponent? component(String id) {
    for (final component in components) {
      if (component.id == id) return component;
    }
    return null;
  }

  /// Null for no job or text that is not a job; unknown fields are ignored
  /// and missing ones take their empty value, so an older or newer runner
  /// never breaks the screen.
  static SetupJobRecord? parse(String? text) {
    if (text == null || text.trim().isEmpty) return null;
    final Object? decoded;
    try {
      decoded = jsonDecode(text);
    } on FormatException {
      return null;
    }
    if (decoded is! Map) return null;
    final jobId = decoded['jobId'];
    if (jobId is! String || jobId.isEmpty) return null;
    final map = decoded['components'];
    final componentMap = map is Map ? map : const {};
    final order = decoded['order'];
    final ids = order is List
        ? [
            for (final id in order)
              if (id is String) id,
          ]
        : [
            for (final id in componentMap.keys)
              if (id is String) id,
          ];
    final params = <String, Map<String, String>>{};
    final rawParams = decoded['params'];
    if (rawParams is Map) {
      for (final entry in rawParams.entries) {
        if (entry.key is String && entry.value is Map) {
          params[entry.key as String] = _strings(entry.value);
        }
      }
    }
    return SetupJobRecord(
      jobId: jobId,
      host: _string(decoded['host']),
      failureKind: decoded['errorCode'] == 'setup_persistence'
          ? SetupFailureKind.persistence
          : null,
      state: _string(decoded['state']) ?? 'interrupted',
      current: _string(decoded['current']),
      components: [
        for (final id in ids)
          if (componentMap[id] is Map)
            SetupJobComponent.fromMap(id, componentMap[id] as Map),
      ],
      startedAt: _int(decoded['startedAt']) ?? 0,
      updatedAt: _int(decoded['updatedAt']) ?? 0,
      error: _string(decoded['error']),
      logTail: _string(decoded['logTail']) ?? '',
      params: params,
    );
  }
}

@immutable
class SetupJobComponent {
  const SetupJobComponent({
    required this.id,
    required this.state,
    this.weight = 1,
    this.stage,
    this.done,
    this.total,
    this.percent,
    this.version,
    this.error,
    this.startedAt,
    this.endedAt,
    this.data = const {},
  });

  factory SetupJobComponent.fromMap(String id, Map<Object?, Object?> map) =>
      SetupJobComponent(
        id: id,
        state: _string(map['state']) ?? 'pending',
        weight: (map['weight'] is num) ? (map['weight'] as num).toDouble() : 1,
        stage: _string(map['stage']),
        done: _int(map['done']),
        total: _int(map['total']),
        percent: map['percent'] is num
            ? (map['percent'] as num).toDouble()
            : null,
        version: _string(map['version']),
        error: _string(map['error']),
        startedAt: _int(map['startedAt']),
        endedAt: _int(map['endedAt']),
        data: map['data'] is Map ? _strings(map['data']) : const {},
      );

  final String id;

  /// pending | running | done | failed | skipped
  final String state;
  final double weight;
  final String? stage;
  final int? done;
  final int? total;
  final double? percent;
  final String? version;
  final String? error;
  final int? startedAt;
  final int? endedAt;
  final Map<String, String> data;

  ComponentState get componentState => switch (state) {
    'running' => ComponentState.running,
    'done' => ComponentState.done,
    'failed' => ComponentState.failed,
    'skipped' => ComponentState.skipped,
    _ => ComponentState.pending,
  };
}
