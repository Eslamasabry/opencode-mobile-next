import 'package:flutter/material.dart';

/// Owns only the routes opened to answer one pending request. Invalidation is
/// synchronous; navigation waits until after build/notification callbacks.
/// Removing exact routes also works when an unrelated screen is on top.
class RequestRoutes {
  RequestRoutes({Listenable? changes, bool Function()? isPending})
    : _changes = changes,
      _isPending = isPending {
    _changes?.addListener(_check);
    _check();
  }

  final Listenable? _changes;
  final bool Function()? _isPending;
  final List<Route<dynamic>> _routes = [];
  bool _closed = false;
  bool _scheduled = false;

  bool get isPending {
    _check();
    return !_closed;
  }

  void own(Route<dynamic>? route) {
    if (route == null || _routes.contains(route)) return;
    _routes.add(route);
    if (_closed) _scheduleRemoval();
  }

  void _check() {
    if (!_closed && _isPending?.call() == false) close();
  }

  void close() {
    if (_closed) return;
    _closed = true;
    _changes?.removeListener(_check);
    _scheduleRemoval();
  }

  void _scheduleRemoval() {
    if (_scheduled) return;
    _scheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scheduled = false;
      for (final route in _routes.reversed.toList()) {
        if (route.isActive && !route.isFirst) {
          route.navigator?.removeRoute(route);
        }
      }
      _routes.removeWhere((route) => !route.isActive);
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }
}
