import 'json_read.dart';

/// One entry of `GET /orders` (`OrderResponse`): a scheduled job the host
/// starts on its own. Only the fields the product uses are decoded.
class GcOrder {
  const GcOrder({
    required this.name,
    this.scopedName,
    this.enabled = true,
    this.trigger,
    this.schedule,
    this.interval,
    this.on,
    this.rig,
    this.description,
    this.raw = const {},
  });

  factory GcOrder.fromJson(Map<String, Object?> json) => GcOrder(
    name: readText(json, 'name') ?? '',
    scopedName: readText(json, 'scoped_name'),
    enabled: readBool(json, 'enabled') ?? true,
    trigger: readText(json, 'trigger') ?? readText(json, 'gate'),
    schedule: readText(json, 'schedule'),
    interval: readText(json, 'interval'),
    on: readText(json, 'on'),
    rig: readText(json, 'rig'),
    description: readText(json, 'description'),
    raw: json,
  );

  final String name;

  /// `rig/name` for a project's order; what the enable and disable routes
  /// take.
  final String? scopedName;
  final bool enabled;
  final String? trigger;
  final String? schedule;
  final String? interval;
  final String? on;
  final String? rig;
  final String? description;
  final Map<String, Object?> raw;
}
