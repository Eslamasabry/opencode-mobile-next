import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'platform_capabilities.dart';

enum NetworkStatus { unknown, offline, online }

enum NetworkTransport { unknown, wifi, mobile, other }

/// Android's app-default network, not reachability of a particular server.
@immutable
class NetworkReading {
  const NetworkReading({
    this.status = NetworkStatus.unknown,
    this.transport = NetworkTransport.unknown,
    this.metered,
    this.connected,
  });

  static const unknown = NetworkReading();

  factory NetworkReading.fromMap(Object? raw) {
    if (raw is! Map) return unknown;
    return NetworkReading(
      status: switch (raw['status']) {
        'online' => NetworkStatus.online,
        'offline' => NetworkStatus.offline,
        _ => NetworkStatus.unknown,
      },
      transport: switch (raw['transport']) {
        'wifi' => NetworkTransport.wifi,
        'mobile' => NetworkTransport.mobile,
        'other' => NetworkTransport.other,
        _ => NetworkTransport.unknown,
      },
      metered: raw['metered'] is bool ? raw['metered'] as bool : null,
      connected: raw['connected'] is bool ? raw['connected'] as bool : null,
    );
  }

  /// Online means Android validated Internet access. An offline network can
  /// still reach a LAN server (including on Wi-Fi without Internet).
  final NetworkStatus status;

  /// VPN networks report unknown rather than guessing their underlying link.
  final NetworkTransport transport;
  final bool? metered;

  /// Whether an app-default network exists; null means unsupported/unavailable.
  final bool? connected;

  /// Appropriate for connection-screen advice, never for blocking a connection:
  /// even with no default network, a built-in/loopback server remains reachable.
  bool get hasNoNetwork => connected == false;

  @override
  bool operator ==(Object other) =>
      other is NetworkReading &&
      other.status == status &&
      other.transport == transport &&
      other.metered == metered &&
      other.connected == connected;

  @override
  int get hashCode => Object.hash(status, transport, metered, connected);
}

/// Both channel halves: this file and Android's NetworkMonitor.kt.
class NetworkBridge {
  const NetworkBridge({
    MethodChannel channel = const MethodChannel('oc/network'),
  }) : _channel = channel;

  final MethodChannel _channel;

  // One shared broadcast subscription prevents independent consumers from
  // replacing each other's native EventChannel listener.
  static final Stream<NetworkReading> _readings =
      const EventChannel(
        'oc/network/events',
      ).receiveBroadcastStream().transform(
        StreamTransformer<dynamic, NetworkReading>.fromHandlers(
          handleData: (raw, sink) => sink.add(NetworkReading.fromMap(raw)),
          handleError: (_, _, sink) => sink.add(NetworkReading.unknown),
        ),
      );

  Future<NetworkReading> current() async {
    if (!platformCapabilities.isAndroid) return NetworkReading.unknown;
    try {
      return NetworkReading.fromMap(
        await _channel
            .invokeMethod<Object?>('current')
            .timeout(const Duration(seconds: 5)),
      );
    } catch (_) {
      return NetworkReading.unknown;
    }
  }

  /// [current] with no deadline and so no timer: for a reader that only
  /// shows what it hears (the setup cost line) and must leave nothing
  /// running behind a closed screen.
  Future<NetworkReading> currentUntimed() async {
    if (!platformCapabilities.isAndroid) return NetworkReading.unknown;
    try {
      return NetworkReading.fromMap(
        await _channel.invokeMethod<Object?>('current'),
      );
    } catch (_) {
      return NetworkReading.unknown;
    }
  }

  /// Starts with a native snapshot, then reports changes. Desktop/web explicitly
  /// report unknown; this API makes no HTTP probe and records no network names.
  Stream<NetworkReading> readings() => platformCapabilities.isAndroid
      ? _readings
      : Stream.value(NetworkReading.unknown);
}

/// UI-owned observable: call start once, refresh on resume, and dispose with
/// its owner. No profile data is stored. start is safe to call more than once.
class NetworkState extends ChangeNotifier {
  NetworkState({NetworkBridge bridge = const NetworkBridge()})
    : _bridge = bridge;

  final NetworkBridge _bridge;
  StreamSubscription<NetworkReading>? _subscription;
  NetworkReading _value = NetworkReading.unknown;
  bool _started = false;
  bool _disposed = false;
  int _revision = 0;

  NetworkReading get value => _value;

  Future<void> start() async {
    if (_disposed || _started) return;
    _started = true;
    _subscription = _bridge.readings().listen(
      (reading) {
        _revision++;
        _setValue(reading);
      },
      onError: (Object _) {
        _revision++;
        _setValue(NetworkReading.unknown);
      },
      onDone: () {
        _revision++;
        _setValue(NetworkReading.unknown);
      },
    );
    await refresh();
  }

  Future<void> refresh() async {
    if (_disposed) return;
    final revision = ++_revision;
    NetworkReading reading;
    try {
      reading = await _bridge.current();
    } catch (_) {
      reading = NetworkReading.unknown;
    }
    // A newer event/request wins over an older asynchronous snapshot.
    if (!_disposed && revision == _revision) _setValue(reading);
  }

  void _setValue(NetworkReading reading) {
    if (_disposed || _value == reading) return;
    _value = reading;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _revision++;
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
