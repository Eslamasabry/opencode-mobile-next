enum UsageRange { today, thirtyDays, year, allTime }

class UsageQuery {
  final int? from;
  final int to;
  final String timezone;
  final String? projectID;
  const UsageQuery({
    this.from,
    required this.to,
    required this.timezone,
    this.projectID,
  });

  factory UsageQuery.forRange(
    UsageRange range, {
    required DateTime now,
    required String timezone,
    String? projectID,
  }) {
    // Calendar arithmetic, not 24-hour subtraction: local days can have 23
    // or 25 hours. Include the current millisecond in the half-open interval.
    final start = switch (range) {
      UsageRange.today => DateTime(now.year, now.month, now.day),
      UsageRange.thirtyDays => DateTime(now.year, now.month, now.day - 29),
      UsageRange.year => DateTime(now.year, 1, 1),
      UsageRange.allTime => null,
    };
    return UsageQuery(
      from: start?.millisecondsSinceEpoch,
      to: now.millisecondsSinceEpoch + 1,
      timezone: timezone,
      projectID: projectID,
    );
  }

  Map<String, dynamic> toQuery() {
    if (timezone.trim().isEmpty ||
        (from != null && from! >= to) ||
        (projectID != null && projectID!.trim().isEmpty)) {
      throw ArgumentError('Invalid usage query');
    }
    return {
      if (from != null) 'from': from,
      'to': to,
      'timezone': timezone,
      if (projectID != null) 'project': projectID,
      'tools': 'summary',
    };
  }
}

abstract interface class UsageStatisticsGateway {
  bool get usageStatisticsSupported;
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query);
}

class UsageUnsupported implements Exception {
  const UsageUnsupported();
}

Map<String, dynamic> _map(dynamic value) {
  if (value is! Map<String, dynamic>) {
    throw const FormatException('Invalid usage object');
  }
  return value;
}

int _count(dynamic value) {
  if (value is! num ||
      !value.isFinite ||
      value < 0 ||
      value != value.truncate()) {
    throw const FormatException('Invalid usage count');
  }
  return value.toInt();
}

double _cost(dynamic value) {
  if (value is! num || !value.isFinite || value < 0) {
    throw const FormatException('Invalid usage cost');
  }
  return value.toDouble();
}

int _timestamp(dynamic value) {
  if (value is! num ||
      !value.isFinite ||
      value != value.truncate() ||
      value.abs() > 8640000000000000) {
    throw const FormatException('Invalid usage timestamp');
  }
  return value.toInt();
}

String _text(dynamic value) {
  if (value is! String || value.isEmpty) {
    throw const FormatException('Invalid usage label');
  }
  return value;
}

List<T> _list<T>(dynamic value, T Function(Map<String, dynamic>) parse) {
  if (value is! List) throw const FormatException('Invalid usage list');
  return List.unmodifiable(value.map((entry) => parse(_map(entry))));
}

class UsageTokens {
  final int input, output, reasoning, cacheRead, cacheWrite;
  const UsageTokens({
    required this.input,
    required this.output,
    required this.reasoning,
    required this.cacheRead,
    required this.cacheWrite,
  });
  int get total => input + output + reasoning + cacheRead + cacheWrite;
  factory UsageTokens.fromJson(Map<String, dynamic> json) {
    final cache = _map(json['cache']);
    return UsageTokens(
      input: _count(json['input']),
      output: _count(json['output']),
      reasoning: _count(json['reasoning']),
      cacheRead: _count(cache['read']),
      cacheWrite: _count(cache['write']),
    );
  }
}

class UsageToolTotals {
  final int calls, succeeded, failed, unfinished;
  const UsageToolTotals({
    required this.calls,
    required this.succeeded,
    required this.failed,
    required this.unfinished,
  });
  double? get successRate =>
      succeeded + failed == 0 ? null : succeeded / (succeeded + failed);
  factory UsageToolTotals.fromJson(Map<String, dynamic> json) =>
      UsageToolTotals(
        calls: _count(json['calls']),
        succeeded: _count(json['succeeded']),
        failed: _count(json['failed']),
        unfinished: _count(json['unfinished']),
      );
}

class UsageModel {
  final String providerID, modelID;
  final String? variant;
  final int steps;
  final UsageTokens tokens;
  final double cost;
  const UsageModel({
    required this.providerID,
    required this.modelID,
    this.variant,
    required this.steps,
    required this.tokens,
    required this.cost,
  });
  factory UsageModel.fromJson(Map<String, dynamic> json) {
    final model = _map(json['model']);
    if (model['variant'] != null && model['variant'] is! String) {
      throw const FormatException('Invalid model variant');
    }
    return UsageModel(
      providerID: _text(model['providerID']),
      modelID: _text(model['id']),
      variant: model['variant'] as String?,
      steps: _count(json['steps']),
      tokens: UsageTokens.fromJson(_map(json['tokens'])),
      cost: _cost(json['cost']),
    );
  }
}

class UsageActivity {
  final String date;
  final int steps;
  const UsageActivity({required this.date, required this.steps});
  factory UsageActivity.fromJson(Map<String, dynamic> json) =>
      UsageActivity(date: _text(json['date']), steps: _count(json['steps']));
}

class UsageStatistics {
  final int from, to, sessions, subagents, prompts, steps, activeDays, streak;
  final double cost;
  final UsageTokens tokens;
  final UsageToolTotals? tools;
  final List<UsageModel> models;
  final List<UsageActivity> activity;
  const UsageStatistics({
    required this.from,
    required this.to,
    required this.sessions,
    required this.subagents,
    required this.prompts,
    required this.steps,
    required this.activeDays,
    required this.streak,
    required this.cost,
    required this.tokens,
    this.tools,
    required this.models,
    required this.activity,
  });
  bool get isEmpty =>
      sessions == 0 &&
      subagents == 0 &&
      prompts == 0 &&
      steps == 0 &&
      cost == 0 &&
      tokens.total == 0 &&
      (tools?.calls ?? 0) == 0;

  factory UsageStatistics.fromJson(Map<String, dynamic> json) {
    final range = _map(json['range']);
    final from = _timestamp(range['from']);
    final to = _timestamp(range['to']);
    if (from > to) throw const FormatException('Invalid usage range');
    final tools = _map(json['tools']);
    if (!['none', 'summary', 'detail'].contains(tools['mode'])) {
      throw const FormatException('Unknown tool statistics');
    }
    return UsageStatistics(
      from: from,
      to: to,
      sessions: _count(json['sessions']),
      subagents: _count(json['subagents']),
      prompts: _count(json['prompts']),
      steps: _count(json['steps']),
      activeDays: _count(json['activeDays']),
      streak: _count(json['streak']),
      cost: _cost(json['cost']),
      tokens: UsageTokens.fromJson(_map(json['tokens'])),
      tools: tools['mode'] == 'none'
          ? null
          : UsageToolTotals.fromJson(_map(tools['totals'])),
      models: _list(json['models'], UsageModel.fromJson),
      activity: _list(json['activity'], UsageActivity.fromJson),
    );
  }
}
