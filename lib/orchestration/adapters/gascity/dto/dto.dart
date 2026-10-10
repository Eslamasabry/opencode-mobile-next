/// Hand-written Gas City DTOs: the subset of the pinned supervisor OpenAPI
/// (`contracts/gascity-supervisor-openapi-v0-*.json`) the plugin reads.
///
/// Every `fromJson` ignores unknown fields, tolerates missing, null and
/// mistyped values, never throws on a shape the fixture recordings contain,
/// and keeps the untouched payload in `raw`.
library;

export 'agent.dart';
export 'bead.dart';
export 'event.dart';
export 'health.dart';
export 'json_read.dart';
export 'list.dart';
export 'order.dart';
export 'pending.dart';
export 'problem.dart';
export 'run.dart';
export 'session.dart';
export 'sling.dart';
export 'usage.dart';
