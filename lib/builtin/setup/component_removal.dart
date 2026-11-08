import 'dart:convert';

import 'package:flutter/foundation.dart';

import '../builtin_linux.dart';
import '../team/builtin_team.dart';
import 'aiteam_scripts.dart';
import 'components.dart';
import 'setup_contract.dart';

enum ComponentPresence { installed, absent, unknown }

/// Stable reasons for localized UI copy. No shell output crosses this API.
enum ComponentRemovalBlock {
  dependentInstalled,
  dependencyUnknown,
  requiredComponent,
  unsupported,
  notInstalled,
  inventoryUnavailable,
  setupRunning,
  busy,
  failed,
}

@immutable
class ComponentRemovalEntry {
  ComponentRemovalEntry({
    required this.component,
    required this.presence,
    required this.block,
    List<String> blockingDependents = const [],
  }) : blockingDependents = List.unmodifiable(blockingDependents);

  final SetupComponent component;
  final ComponentPresence presence;
  final ComponentRemovalBlock? block;

  /// Installed or unknown dependents, including indirect ones, by registry id.
  final List<String> blockingDependents;
  bool get canRemove => block == null;
}

class ComponentRemovalException implements Exception {
  const ComponentRemovalException(this.code);
  final ComponentRemovalBlock code;

  @override
  String toString() => 'ComponentRemovalException(${code.name})';
}

/// Share one instance for a This phone removal session. Inventory uses presence
/// probes, never version checks: an outdated dependent still blocks removal.
/// Every remove refreshes inventory and native setup status before mutating.
///
/// Only registry-authored scripts run; ids never become shell input. Nothing is
/// persisted or logged. UI owns localized confirmations and profile updates.
/// The native runner does not expose a cross-operation lock: callers must also
/// disable setup/start actions while [busy] is true (see the QA UI hook-up).
class ComponentRemovalService {
  ComponentRemovalService({
    required BuiltinLinux linux,
    required List<SetupComponent> registry,
    BuiltinTeam? team,
    Future<void> Function()? removeTeam,
  }) : _linux = linux,
       _registry = List.unmodifiable(registry),
       _team = team,
       _removeTeam = removeTeam {
    if (_registry.map((c) => c.id).toSet().length != _registry.length) {
      throw ArgumentError('Component ids must be unique.');
    }
  }

  final BuiltinLinux _linux;
  final List<SetupComponent> _registry;
  final BuiltinTeam? _team;

  /// Removes AI Team where the team is not the in-app [BuiltinTeam] (tests
  /// pass their own). Without it or [_team], AI Team is
  /// [ComponentRemovalBlock.unsupported].
  final Future<void> Function()? _removeTeam;
  final _busy = ValueNotifier(false);
  ValueListenable<bool> get busy => _busy;

  Future<List<ComponentRemovalEntry>> inventory() async {
    BuiltinLinuxStatus? status;
    try {
      status = await _linux.status();
    } catch (_) {
      // Unknown is not absence and cannot authorize removal.
    }
    final presence = <String, ComponentPresence>{};
    for (final component in _registry.where((c) => !c.jobStep)) {
      presence[component.id] = component.app != null
          // In the app's own storage: Linux's state says nothing about it.
          ? await _presence(component)
          : status == null || status.phase == BuiltinLinuxPhase.installing
          ? ComponentPresence.unknown
          : !status.installed
          ? ComponentPresence.absent
          : component.native
          ? ComponentPresence.installed
          : await _presence(component);
    }
    return List.unmodifiable([
      for (final component in _registry.where((c) => !c.jobStep))
        _entry(component, presence),
    ]);
  }

  ComponentRemovalEntry _entry(
    SetupComponent component,
    Map<String, ComponentPresence> presence,
  ) {
    final dependents = [
      for (final candidate in _registry)
        if (!candidate.jobStep &&
            candidate.id != component.id &&
            _dependsOn(candidate, component.id, {}) &&
            presence[candidate.id] != ComponentPresence.absent)
          candidate.id,
    ];
    final own = presence[component.id]!;
    final ComponentRemovalBlock? block;
    if (own == ComponentPresence.unknown) {
      block = ComponentRemovalBlock.inventoryUnavailable;
    } else if (own == ComponentPresence.absent) {
      block = ComponentRemovalBlock.notInstalled;
    } else if (dependents.any(
      (id) => presence[id] == ComponentPresence.installed,
    )) {
      block = ComponentRemovalBlock.dependentInstalled;
    } else if (dependents.isNotEmpty) {
      block = ComponentRemovalBlock.dependencyUnknown;
    } else if (component.required) {
      block = ComponentRemovalBlock.requiredComponent;
    } else if (component.app != null) {
      block = null;
    } else if (component.native ||
        component.removeScript == null ||
        component.removeScript!.trim().isEmpty ||
        (component.id == SetupComponentIds.aiTeam &&
            (component.removeScript != AiTeamScripts.removeScript ||
                (_team == null && _removeTeam == null)))) {
      block = ComponentRemovalBlock.unsupported;
    } else {
      block = null;
    }
    return ComponentRemovalEntry(
      component: component,
      presence: own,
      block: block,
      blockingDependents: dependents,
    );
  }

  bool _dependsOn(SetupComponent component, String id, Set<String> seen) {
    if (!seen.add(component.id)) return false;
    for (final dependency in component.dependsOn) {
      if (dependency == id) return true;
      for (final next in _registry.where((c) => c.id == dependency)) {
        if (_dependsOn(next, id, seen)) return true;
      }
    }
    return false;
  }

  Future<ComponentPresence> _presence(SetupComponent component) async {
    if (component.app case final app?) {
      try {
        return (await app.check()).ok
            ? ComponentPresence.installed
            : ComponentPresence.absent;
      } catch (_) {
        return ComponentPresence.unknown;
      }
    }
    final script = component.presenceScript;
    if (script == null || script.trim().isEmpty) {
      return ComponentPresence.unknown;
    }
    try {
      final result = await _linux.run(
        script,
        timeout: const Duration(seconds: 30),
      );
      return switch (result.exitCode) {
        0 => ComponentPresence.installed,
        1 => ComponentPresence.absent,
        _ => ComponentPresence.unknown,
      };
    } catch (_) {
      return ComponentPresence.unknown;
    }
  }

  Future<void> _checkSetup() async {
    try {
      final raw = await _linux.setupStatus();
      if (raw == null) return;
      final record = jsonDecode(raw);
      if (record is! Map || record['state'] is! String) {
        throw const ComponentRemovalException(
          ComponentRemovalBlock.inventoryUnavailable,
        );
      }
      if (record['state'] == 'running') {
        throw const ComponentRemovalException(
          ComponentRemovalBlock.setupRunning,
        );
      }
      if (!const {
        'idle',
        'done',
        'failed',
        'interrupted',
        'cancelled',
      }.contains(record['state'])) {
        throw const ComponentRemovalException(
          ComponentRemovalBlock.inventoryUnavailable,
        );
      }
    } on ComponentRemovalException {
      rethrow;
    } catch (_) {
      throw const ComponentRemovalException(
        ComponentRemovalBlock.inventoryUnavailable,
      );
    }
  }

  /// Removes only [id], never its dependents. A completed future means the
  /// script succeeded and a fresh presence probe confirmed absence. Failures
  /// can leave partially removed tools; refresh inventory before retrying.
  Future<void> remove(String id) async {
    if (_busy.value) {
      throw const ComponentRemovalException(ComponentRemovalBlock.busy);
    }
    _busy.value = true;
    var mutationAttempted = false;
    try {
      await _checkSetup();
      final entries = await inventory();
      final matches = entries.where((entry) => entry.component.id == id);
      if (matches.isEmpty) {
        throw const ComponentRemovalException(
          ComponentRemovalBlock.unsupported,
        );
      }
      final entry = matches.single;
      if (entry.block != null) throw ComponentRemovalException(entry.block!);
      await _checkSetup();
      mutationAttempted = true;
      if (entry.component.app case final app?) {
        await app.remove();
      } else if (id == SetupComponentIds.aiTeam) {
        final removeTeam = _removeTeam;
        if (removeTeam != null) {
          await removeTeam();
        } else {
          await _team!.remove();
        }
      } else {
        final result = await _linux.run(
          entry.component.removeScript!,
          timeout: const Duration(minutes: 5),
        );
        if (!result.ok) {
          throw const ComponentRemovalException(ComponentRemovalBlock.failed);
        }
      }
      if (await _presence(entry.component) != ComponentPresence.absent) {
        throw const ComponentRemovalException(ComponentRemovalBlock.failed);
      }
    } on ComponentRemovalException {
      rethrow;
    } catch (_) {
      // Native/script errors may contain credentials. Return only a reason.
      throw const ComponentRemovalException(ComponentRemovalBlock.failed);
    } finally {
      _busy.value = false;
      // A failed removal can still change what is installed. Admission-only
      // failures leave the tools untouched and do not invalidate inventory.
      if (mutationAttempted) setupToolsChanged.value++;
    }
  }

  /// What removing [id] gives back, in bytes, measured now: the app-side
  /// component's own size, or the component's [SetupComponent.sizeScript]
  /// (kilobytes on its last line). Null when it cannot be measured or
  /// measures nothing: callers then leave the figure out, never "0 B".
  Future<int?> freedBytes(String id, {Duration timeout = _sizeTimeout}) async {
    final matches = _registry.where((c) => c.id == id);
    if (matches.isEmpty) return null;
    final component = matches.first;
    try {
      if (component.app case final app?) {
        final offer = await app.offer().timeout(timeout);
        if (offer == null || !offer.installed) return null;
        return offer.downloadBytes > 0 ? offer.downloadBytes : null;
      }
      final script = component.sizeScript;
      if (script == null || script.trim().isEmpty) return null;
      final result = await _linux.run(script, timeout: timeout);
      if (!result.ok) return null;
      final lines = result.output.trim().split('\n');
      final kilobytes = int.tryParse(lines.last.trim());
      if (kilobytes == null || kilobytes <= 0) return null;
      return kilobytes * 1024;
    } catch (_) {
      return null;
    }
  }

  static const _sizeTimeout = Duration(seconds: 20);
}
