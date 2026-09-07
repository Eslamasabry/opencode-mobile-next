import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/development_service.dart';

/// Configurations and ownership receipts only. Log output and credentials are
/// never persisted. ProfileStore's standard `oc.*.<profileId>` sweep deletes it.
class DevelopmentServiceStore {
  DevelopmentServiceStore({
    required this.preferences,
    required this.profileID,
    required this.canWrite,
  });
  final SharedPreferences preferences;
  final String profileID;
  final bool Function() canWrite;
  static const maxServices = 20;
  static final Map<String, Future<void>> _writes = {};
  String get key => 'oc.developmentServices.$profileID';

  List<DevelopmentService> load() {
    final raw = preferences.getString(key);
    if (raw == null) return const [];
    if (raw.length > 256 * 1024) {
      throw const FormatException('Saved service data is too large');
    }
    final json = jsonDecode(raw);
    if (json is! Map || json['version'] != 1 || json['services'] is! List) {
      throw const FormatException('Saved services could not be read');
    }
    final items = json['services'] as List;
    if (items.length > maxServices) {
      throw const FormatException('Too many saved services');
    }
    final services = items
        .map(
          (item) => DevelopmentService.fromJson(
            Map<String, dynamic>.from(item as Map),
          ),
        )
        .toList();
    if (services.map((s) => s.id).toSet().length != services.length) {
      throw const FormatException('Duplicate saved service');
    }
    return services;
  }

  Future<void> save(DevelopmentService service) => _change((items) {
    final index = items.indexWhere((item) => item.id == service.id);
    if (index < 0) {
      if (items.length >= maxServices) {
        throw StateError('Service limit reached');
      }
      items.add(service);
    } else {
      items[index] = service;
    }
  });

  Future<void> remove(String id) => _change((items) {
    items.removeWhere((item) => item.id == id);
  });

  Future<void> updateRun(
    String id,
    DevelopmentServiceRun? run, {
    String? expectedOwner,
  }) => _change((items) {
    final index = items.indexWhere((item) => item.id == id);
    if (index < 0 ||
        (expectedOwner != null &&
            items[index].run?.ownerToken != expectedOwner)) {
      throw StateError('The saved service changed');
    }
    items[index] = items[index].withRun(run);
  });

  Future<void> _change(void Function(List<DevelopmentService>) change) {
    final previous = _writes[key] ?? Future<void>.value();
    final next = previous.catchError((Object _) {}).then((_) async {
      if (!canWrite()) throw StateError('The server profile is unavailable');
      final items = load().toList();
      change(items);
      final saved = await preferences.setString(
        key,
        jsonEncode({
          'version': 1,
          'services': items.map((item) => item.toJson()).toList(),
        }),
      );
      // A pending write must not recreate profile data after the deletion sweep.
      if (!canWrite()) {
        await preferences.remove(key);
        throw StateError('The server profile was removed');
      }
      if (!saved) throw StateError('Services could not be saved');
    });
    _writes[key] = next;
    return next.whenComplete(() {
      if (identical(_writes[key], next)) _writes.remove(key);
    });
  }
}
