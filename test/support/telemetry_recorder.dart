import 'package:opencode_mobile/telemetry/telemetry.dart';

/// A vendor that keeps everything it is given, for the health telemetry
/// tests. [events] are (name, params); the rest are plain lines.
class RecordingAdapter implements TelemetryAdapter {
  RecordingAdapter({this.present = true, this.instanceId = 'abcdef0123456789'});

  final bool present;
  final String? instanceId;
  final events = <(String, Map<String, String>)>[];
  final crashes = <String>[];
  final failures = <(String, Map<String, String>)>[];
  final crumbs = <String>[];
  final keys = <String, String>{};
  final properties = <String, String>{};
  final reports = <({String text, bool collecting})>[];
  bool? usageOn;
  bool? crashesOn;

  /// Every call that reached the vendor, in order, as a short label.
  final calls = <String>[];

  @override
  Future<bool> probe() async => present;
  @override
  Future<void> setCollection({
    required bool usage,
    required bool crashes,
  }) async {
    usageOn = usage;
    crashesOn = crashes;
  }

  @override
  Future<void> crash(
    String category,
    String stack, {
    required bool fatal,
  }) async {
    crashes.add('$category|$fatal|$stack');
    calls.add('crash');
  }

  @override
  Future<void> failure(String name, Map<String, String> keys) async {
    failures.add((name, keys));
    calls.add('failure');
  }

  @override
  Future<void> breadcrumb(String name) async {
    crumbs.add(name);
    calls.add('crumb');
  }

  @override
  Future<void> event(String name, Map<String, String> props) async {
    events.add((name, props));
    calls.add('event');
  }

  @override
  Future<void> setKeys(Map<String, String> values) async {
    keys.addAll(values);
    calls.add('keys');
  }

  @override
  Future<void> setUserProperties(Map<String, String> values) async {
    properties.addAll(values);
    calls.add('properties');
  }

  @override
  Future<void> problemReport(String text, {required bool collecting}) async {
    reports.add((text: text, collecting: collecting));
    calls.add('report');
  }

  @override
  Future<String?> appInstanceId() async => instanceId;
}

/// A started service over [adapter] with the given answers.
Future<TelemetryService> startedService(
  RecordingAdapter adapter, {
  bool usage = false,
  bool crashes = false,
  TelemetryReportStore? reports,
}) async {
  final service = TelemetryService(
    adapter: adapter,
    store: MemoryTelemetryConsentStore.split(usage: usage, crashes: crashes),
    reports: reports,
  );
  await service.start();
  return service;
}

Future<void> settleTelemetry() => Future<void>.delayed(Duration.zero);
