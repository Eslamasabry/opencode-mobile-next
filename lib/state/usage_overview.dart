import 'package:flutter/foundation.dart';
import 'package:flutter_timezone/flutter_timezone.dart';

import '../domain/server_gateway.dart';
import 'connection.dart';

enum UsageScope { allProjects, currentProject }

class UsageProjectUnavailable implements Exception {
  const UsageProjectUnavailable();
}

class UsageTimezoneUnavailable implements Exception {
  const UsageTimezoneUnavailable();
}

class UsageRefreshInterrupted implements Exception {
  const UsageRefreshInterrupted();
}

class UsageSnapshot {
  final UsageStatistics statistics;
  final UsageQuery query;
  final UsageRange range;
  final UsageScope scope;
  final String? projectName;
  final DateTime fetchedAt;
  const UsageSnapshot({
    required this.statistics,
    required this.query,
    required this.range,
    required this.scope,
    this.projectName,
    required this.fetchedAt,
  });
}

/// A retained usage page belongs to one connection location. Filter changes
/// supersede old reads; refreshing the same filters can retain visible totals.
class UsageOverview extends ChangeNotifier {
  final ConnectionController connection;
  final DateTime Function() clock;
  final Future<String> Function() timezoneLoader;
  final int _location;
  int _request = 0;
  bool _disposed = false;
  bool detached = false;
  bool loading = false;
  Object? error;
  UsageSnapshot? snapshot;
  UsageRange range = UsageRange.thirtyDays;
  UsageScope scope = UsageScope.allProjects;

  UsageOverview(
    this.connection, {
    DateTime Function()? clock,
    Future<String> Function()? timezoneLoader,
  }) : _location = connection.locationRevision,
       clock = clock ?? DateTime.now,
       timezoneLoader = timezoneLoader ?? _deviceTimezone {
    connection.addListener(_connectionChanged);
  }

  static Future<String> _deviceTimezone() async {
    try {
      final identifier = (await FlutterTimezone.getLocalTimezone()).identifier
          .trim();
      if (identifier.isNotEmpty && identifier != 'Etc/Unknown') {
        return identifier;
      }
    } catch (_) {}
    throw const UsageTimezoneUnavailable();
  }

  void _connectionChanged() {
    if (_disposed || connection.locationRevision == _location) return;
    detached = true;
    _request++;
    snapshot = null;
    error = null;
    loading = false;
    notifyListeners();
  }

  Future<void> setRange(UsageRange value) async {
    if (range == value) return;
    range = value;
    snapshot = null;
    await refresh();
  }

  Future<void> setScope(UsageScope value) async {
    if (scope == value) return;
    scope = value;
    snapshot = null;
    await refresh();
  }

  bool _current(int request) =>
      !_disposed &&
      !detached &&
      request == _request &&
      connection.locationRevision == _location;

  Future<void> refresh() async {
    if (_disposed || detached) return;
    final request = ++_request;
    final selectedRange = range;
    final selectedScope = scope;
    loading = true;
    error = null;
    notifyListeners();
    try {
      final repository = await connection.prepareActionRepository();
      if (!_current(request)) return;
      if (repository == null) throw const UsageRefreshInterrupted();
      if (repository is! UsageStatisticsGateway ||
          !(repository as UsageStatisticsGateway).usageStatisticsSupported) {
        throw const UsageUnsupported();
      }
      final timezone = await timezoneLoader();
      if (!_current(request)) return;
      final project = selectedScope == UsageScope.currentProject
          ? await repository.loadCurrentProject()
          : null;
      if (!_current(request)) return;
      if (selectedScope == UsageScope.currentProject &&
          (project == null || project.id.isEmpty)) {
        throw const UsageProjectUnavailable();
      }
      // A wake or reload may replace the transport without changing location.
      // Do not dispatch this request through an object retired during setup.
      if (!identical(repository, connection.repository)) {
        throw const UsageRefreshInterrupted();
      }
      final now = clock().toLocal();
      final query = UsageQuery.forRange(
        selectedRange,
        now: now,
        timezone: timezone,
        projectID: project?.id,
      );
      final statistics = await (repository as UsageStatisticsGateway)
          .loadUsageStatistics(query);
      if (!_current(request)) return;
      if (!identical(repository, connection.repository)) {
        throw const UsageRefreshInterrupted();
      }
      snapshot = UsageSnapshot(
        statistics: statistics,
        query: query,
        range: selectedRange,
        scope: selectedScope,
        projectName: project?.name,
        fetchedAt: clock(),
      );
    } catch (failure) {
      if (_current(request)) error = failure;
    } finally {
      if (_current(request)) {
        loading = false;
        notifyListeners();
      }
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _request++;
    connection.removeListener(_connectionChanged);
    super.dispose();
  }
}
