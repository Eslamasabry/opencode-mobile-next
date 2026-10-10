/// What starts a scheduled job (Gas City order `trigger`).
enum ScheduledJobTrigger {
  /// At fixed times, a cron expression in [ScheduledJob.schedule].
  cron,

  /// Every so often, after the last run: [ScheduledJob.interval].
  interval,

  /// When something happens on the host: [ScheduledJob.onEvent].
  event,

  /// Only when a person starts it.
  manual,

  /// When a web request arrives.
  webhook,

  /// When a check the host runs passes.
  condition,

  /// A trigger this app does not know.
  other,
}

/// One scheduled job of the host (Gas City "order"): something it starts on
/// its own, on a schedule or when something happens. Plain value; [raw]
/// keeps the provider payload for Technical details.
class ScheduledJob {
  const ScheduledJob({
    required this.id,
    required this.name,
    required this.enabled,
    required this.trigger,
    this.projectId,
    this.schedule,
    this.interval,
    this.onEvent,
    this.description,
    this.rawTrigger,
    this.raw = const {},
  });

  /// The host's scoped name (`rig/name` for a project's job); what a
  /// control addresses.
  final String id;

  /// The job's own name.
  final String name;

  /// Switched on: the host starts it by itself.
  final bool enabled;
  final ScheduledJobTrigger trigger;

  /// The project (rig) the job belongs to; null for the whole team's.
  final String? projectId;

  /// The cron expression, when [trigger] is [ScheduledJobTrigger.cron].
  final String? schedule;

  /// The wait between runs (`5m`, `1h`), when [trigger] is
  /// [ScheduledJobTrigger.interval].
  final String? interval;

  /// The event name, when [trigger] is [ScheduledJobTrigger.event].
  final String? onEvent;
  final String? description;

  /// The trigger word as the host sent it.
  final String? rawTrigger;
  final Map<String, Object?> raw;

  @override
  String toString() => 'ScheduledJob($id, ${enabled ? 'on' : 'off'})';
}
