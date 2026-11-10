import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../diagnostics/app_diagnostics.dart';
import '../diagnostics/report_problem_startup.dart';
import '../platform/platform_capabilities.dart';
import '../platform/thermal.dart';
import '../state/automation_policy.dart';
import '../state/profiles.dart';
import 'builtin_linux.dart';
import 'setup/setup_engine.dart' show ChannelSetupEngine;
import 'team/builtin_team.dart';
import 'thermal_guard.dart';

/// One answer from a team's supervisor; null status on no answer.
@immutable
class ThermalHttpAnswer {
  const ThermalHttpAnswer(this.status, [this.body]);

  final int status;
  final Object? body;

  bool get ok => status >= 200 && status < 300;
}

typedef ThermalHttp =
    Future<ThermalHttpAnswer?> Function(
      String method,
      Uri uri, [
      Map<String, Object?>? body,
    ]);

/// The Gas City teams on this phone, over the supervisor's own API on
/// loopback (the built-in team on 8472, a Termux team on 8372).
///
/// Pausing keeps everything: the city is suspended (`PATCH /v0/city/{c}
/// {suspended: true}`, which Gas City keeps across restarts, so nothing
/// new is started), then every running session is suspended (`POST
/// session/{id}/suspend`: "save state, free resources"; its bead and
/// conversation stay). Resuming undoes exactly that: the sessions the
/// guard suspended are woken and the city is resumed. A city or a session
/// that was already suspended is the person's, and is never touched.
///
/// Stopping, at CRITICAL and above, pauses first and then stops the
/// built-in team's supervisor service; the app cannot stop a Termux team,
/// which stays paused. The OpenCode server itself is left alone: the
/// person's conversation may be running there.
class GasCityThermalTeams implements ThermalTeamPort {
  GasCityThermalTeams({
    required this.store,
    BuiltinTeam? builtinTeam,
    ThermalHttp? http,
    this.restartTimeout = const Duration(minutes: 3),
    this.pollInterval = const Duration(seconds: 3),
  }) : _builtin = builtinTeam ?? BuiltinTeam(),
       _http = http ?? _loopback;

  final ProfileStore store;
  final BuiltinTeam _builtin;
  final ThermalHttp _http;
  final Duration restartTimeout;
  final Duration pollInterval;

  /// A team on this phone: a Gas City plugin reached on loopback without a
  /// front. One per address.
  List<ThermalTeam> teamsHere() {
    final seen = <String>{};
    return [
      for (final profile in store.profiles)
        if (profile.orchestration case final config?)
          if (config.provider == OrchestrationProvider.gascity &&
              !config.front &&
              isLoopback(config.url) &&
              seen.add('${_base(config.url)}|${config.city}'))
            ThermalTeam(
              id: profile.id,
              url: _base(config.url),
              city: config.city,
              builtin: BuiltinTeam.isBuiltinConfig(config),
            ),
    ];
  }

  static bool isLoopback(String url) {
    final uri = Uri.tryParse(url);
    return uri != null &&
        uri.scheme == 'http' &&
        (uri.host == '127.0.0.1' || uri.host == 'localhost');
  }

  static String _base(String url) =>
      url.endsWith('/') ? url.substring(0, url.length - 1) : url;

  @override
  Future<List<ThermalTeam>> runningHere() async {
    final running = <ThermalTeam>[];
    for (final team in teamsHere()) {
      final city = await _cityOf(team);
      if (city == null) continue;
      final state = await _http('GET', _uri(team, city));
      if (state == null || !state.ok) continue;
      if (_suspended(state.body)) continue; // the person's pause
      running.add(team);
    }
    return running;
  }

  @override
  Future<ThermalTeamHold?> pause(
    ThermalTeam team, {
    required DateTime now,
  }) async {
    final city = await _cityOf(team);
    if (city == null) return null;
    final state = await _http('GET', _uri(team, city));
    if (state == null || !state.ok || _suspended(state.body)) return null;
    final suspended = await _http('PATCH', _uri(team, city), {
      'suspended': true,
    });
    if (suspended == null || !suspended.ok) return null;
    final sessions = <String>[];
    for (final id in await _runningSessions(team, city)) {
      final answer = await _http(
        'POST',
        _uri(team, city, '/session/${Uri.encodeComponent(id)}/suspend'),
      );
      if (answer != null && answer.ok) sessions.add(id);
    }
    return ThermalTeamHold(
      team: team.city.isEmpty
          ? ThermalTeam(
              id: team.id,
              url: team.url,
              city: city,
              builtin: team.builtin,
            )
          : team,
      since: now,
      sessions: sessions,
    );
  }

  @override
  Future<ThermalTeamHold> stop(ThermalTeamHold hold) async {
    if (!hold.team.builtin || hold.serviceStopped) return hold;
    try {
      await _builtin.stop();
      return hold.copyWith(serviceStopped: true);
    } on Object {
      return hold;
    }
  }

  @override
  Future<bool> resume(ThermalTeamHold hold) async {
    final team = hold.team;
    // The person turned the team off meanwhile: nothing of ours to give
    // back, and it must not start again.
    if (!teamsHere().any((t) => t.url == team.url)) return true;
    if (!_allowsRecovery(team.id)) return false;
    if (hold.serviceStopped) {
      await _builtin.ensureRunning(
        notice: ChannelSetupEngine.deviceStrings().aiteamComponentNotice,
      );
      if (!await _answers(team)) return false;
    }
    final city = await _cityOf(team);
    if (city == null) return false;
    final state = await _http('GET', _uri(team, city));
    if (state == null || !state.ok) return false;
    if (!_allowsRecovery(team.id)) return false;
    if (_suspended(state.body)) {
      final resumed = await _http('PATCH', _uri(team, city), {
        'suspended': false,
      });
      if (resumed == null || !resumed.ok) return false;
    }
    var allConfirmed = true;
    for (final id in hold.sessions) {
      if (!_allowsRecovery(team.id)) return false;
      final sessionUri = _uri(
        team,
        city,
        '/session/${Uri.encodeComponent(id)}',
      );
      final session = await _http('GET', sessionUri);
      // Keep the full durable hold until all work is accounted for. On a
      // retry (including after app restart), do not wake sessions that have
      // already resumed. The session resource, not a failed wake endpoint,
      // establishes that a session has been closed meanwhile.
      if (session?.status == 404) continue;
      if (session == null || !session.ok) {
        allConfirmed = false;
        continue;
      }
      final body = session.body;
      if (body is Map && body['id'] == id && body['running'] == true) {
        continue;
      }
      final answer = await _http(
        'POST',
        _uri(team, city, '/session/${Uri.encodeComponent(id)}/wake'),
      );
      if (answer != null && answer.ok) continue;
      if (answer?.status == 404) {
        // The session can disappear between the read and the wake. A 404
        // from /wake alone can also mean that mutation is unavailable.
        final after = await _http('GET', sessionUri);
        if (after?.status == 404) continue;
      }
      allConfirmed = false;
    }
    return allConfirmed;
  }

  bool _allowsRecovery(String profileId) =>
      store.profiles.any((profile) => profile.id == profileId) &&
      AutomationPolicyController.forProfile(
        store.prefs,
        profileId,
      ).value.allows(AutomationBehavior.thermalRecovery);

  Future<bool> _answers(ThermalTeam team) async {
    final deadline = DateTime.now().add(restartTimeout);
    while (true) {
      final city = await _cityOf(team);
      if (city != null) {
        final state = await _http('GET', _uri(team, city));
        if (state != null && state.ok) return true;
      }
      if (DateTime.now().isAfter(deadline)) return false;
      await Future<void>.delayed(pollInterval);
    }
  }

  /// The city's name: the profile's, else the first the supervisor lists.
  Future<String?> _cityOf(ThermalTeam team) async {
    if (team.city.isNotEmpty) return team.city;
    final cities = await _http('GET', Uri.parse('${team.url}/v0/cities'));
    if (cities == null || !cities.ok) return null;
    final body = cities.body;
    final items = body is Map ? body['items'] : null;
    if (items is List) {
      for (final item in items) {
        if (item is Map && item['name'] is String) {
          return item['name'] as String;
        }
      }
    }
    return null;
  }

  Future<List<String>> _runningSessions(ThermalTeam team, String city) async {
    final answer = await _http('GET', _uri(team, city, '/sessions'));
    if (answer == null || !answer.ok) return const [];
    final body = answer.body;
    final items = body is Map ? body['items'] : null;
    return [
      if (items is List)
        for (final item in items)
          if (item is Map &&
              item['id'] is String &&
              (item['running'] == true || item['state'] == 'active'))
            item['id'] as String,
    ];
  }

  static bool _suspended(Object? body) =>
      body is Map && body['suspended'] == true;

  static Uri _uri(ThermalTeam team, String city, [String tail = '']) =>
      Uri.parse('${team.url}/v0/city/${Uri.encodeComponent(city)}$tail');

  /// Loopback only; the supervisor takes writes with `X-GC-Request` there.
  static Future<ThermalHttpAnswer?> _loopback(
    String method,
    Uri uri, [
    Map<String, Object?>? body,
  ]) async {
    if (!isLoopback(uri.toString())) return null;
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 3);
    try {
      final request = await client.openUrl(method, uri);
      request.headers.set('Accept', 'application/json');
      if (method != 'GET') request.headers.set('X-GC-Request', 'thermal');
      if (body != null) {
        request.headers.contentType = ContentType.json;
        request.write(jsonEncode(body));
      }
      final response = await request.close().timeout(
        const Duration(seconds: 10),
      );
      final text = await response
          .transform(utf8.decoder)
          .join()
          .timeout(const Duration(seconds: 10));
      Object? json;
      try {
        json = text.isEmpty ? null : jsonDecode(text);
      } on FormatException {
        json = null;
      }
      return ThermalHttpAnswer(response.statusCode, json);
    } catch (_) {
      return null;
    } finally {
      client.close(force: true);
    }
  }
}

/// The app's guard once the shell started it (Android only); null before,
/// elsewhere, and in tests that do not set one.
final thermalGuardSlotProvider = Provider<ValueNotifier<ThermalGuard?>>((ref) {
  final slot = ValueNotifier<ThermalGuard?>(null);
  ref.onDispose(() {
    slot.value?.dispose();
    slot.dispose();
  });
  return slot;
});

/// Starts the guard once per process from the app shell.
void startThermalGuard(
  ValueNotifier<ThermalGuard?> slot, {
  required ProfileStore store,
  required BuiltinLinux linux,
  AppDiagnosticsController? diagnostics,
  void Function(
    ThermalNoticeKind kind,
    ThermalTeam team,
    DateTime since,
    DateTime at,
  )?
  onAct,
}) {
  if (slot.value != null || !platformCapabilities.isAndroid) return;
  final guard = ThermalGuard(
    bridge: ThermalBridge(),
    port: GasCityThermalTeams(
      store: store,
      builtinTeam: BuiltinTeam(linux: linux),
    ),
    prefs: store.prefs,
    diagnostics: diagnostics,
    // Thermal changes go to the persisted problem report once it is open.
    onReading: (reading) =>
        ReportProblemStartup.current?.recordThermal(reading),
    onAct: onAct,
  );
  slot.value = guard;
  unawaited(guard.start());
}
