/// Backend-only Ubuntu host adoption. UI uses the controller, never shell text.
library;

import 'dart:convert';

import 'package:crypto/crypto.dart';

const byoHostBuildEnabled = bool.fromEnvironment(
  'OC_BYO_HOST',
  defaultValue: false,
);

bool byoHostSafeId(String value) =>
    RegExp(r'^[a-zA-Z0-9_-]{1,64}$').hasMatch(value);

enum ByoHostPhase {
  idle,
  checking,
  needsTrust,
  installing,
  connecting,
  ready,
  disconnected,
  removing,
  failed,
  removed,
}

enum ByoHostFailureCode {
  disabled,
  invalidTarget,
  unavailable,
  needsTrust,
  hostKeyChanged,
  authentication,
  bundleUnavailable,
  checksum,
  lingerRequired,
  sshPolicyRequired,
  unsupportedHost,
  transport,
  protocol,
  identityChanged,
  storage,
  busy,
  revocationFailed,
  uncertain,
}

class ByoHostFailure implements Exception {
  const ByoHostFailure(this.code);
  final ByoHostFailureCode code;
  String get message => switch (code) {
    ByoHostFailureCode.disabled => 'Adding a machine is not available yet.',
    ByoHostFailureCode.invalidTarget => 'Enter a user and machine address.',
    ByoHostFailureCode.needsTrust => 'Verify this machine before connecting.',
    ByoHostFailureCode.hostKeyChanged =>
      'This machine’s identity changed. Verify it before continuing.',
    ByoHostFailureCode.authentication => 'Sign in to this machine again.',
    ByoHostFailureCode.bundleUnavailable =>
      'The setup package is not available for this machine.',
    ByoHostFailureCode.checksum => 'The setup package could not be verified.',
    ByoHostFailureCode.unsupportedHost =>
      'Use an Ubuntu 24.04 machine with a regular user account.',
    ByoHostFailureCode.sshPolicyRequired =>
      'This machine needs permission for private connections only.',
    ByoHostFailureCode.lingerRequired =>
      'This machine needs permission to stay running after sign-out.',
    ByoHostFailureCode.identityChanged =>
      'The installed host has changed. Check this machine.',
    ByoHostFailureCode.storage =>
      'The connection could not be saved or removed securely.',
    ByoHostFailureCode.busy => 'Another connection action is running.',
    ByoHostFailureCode.revocationFailed =>
      'Access could not be revoked. Your connection is kept.',
    ByoHostFailureCode.uncertain =>
      'Setup may have finished. Reconnect to check it.',
    _ => 'The machine could not be connected. Try again.',
  };
  @override
  String toString() => 'ByoHostFailure(${code.name})';
}

class ByoHostTarget {
  ByoHostTarget({required this.user, required this.host, this.port = 22}) {
    if (!RegExp(r'^[a-zA-Z_][a-zA-Z0-9_.-]{0,63}$').hasMatch(user) ||
        !RegExp(r'^[a-zA-Z0-9][a-zA-Z0-9.:-]{0,252}$').hasMatch(host) ||
        host.contains('..') ||
        port < 1 ||
        port > 65535) {
      throw const ByoHostFailure(ByoHostFailureCode.invalidTarget);
    }
  }
  factory ByoHostTarget.parse(String input, {int port = 22}) {
    final parts = input.trim().split('@');
    if (parts.length != 2) {
      throw const ByoHostFailure(ByoHostFailureCode.invalidTarget);
    }
    return ByoHostTarget(user: parts[0], host: parts[1], port: port);
  }
  final String user;
  final String host;
  final int port;
  Map<String, Object?> toJson() => {'user': user, 'host': host, 'port': port};
  factory ByoHostTarget.fromJson(Map<String, dynamic> json) => ByoHostTarget(
    user: json['user'] as String,
    host: json['host'] as String,
    port: json['port'] as int,
  );
}

class ByoHostKey {
  ByoHostKey({required this.publicKey, required this.fingerprint}) {
    if (!RegExp(r'^ssh-ed25519 [A-Za-z0-9+/]+={0,2}$').hasMatch(publicKey) ||
        !RegExp(r'^SHA256:[A-Za-z0-9+/]{43}$').hasMatch(fingerprint)) {
      throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
    }
    try {
      final bytes = base64Decode(publicKey.split(' ')[1]);
      final prefix = <int>[
        0,
        0,
        0,
        11,
        ...ascii.encode('ssh-ed25519'),
        0,
        0,
        0,
        32,
      ];
      if (bytes.length != 51 ||
          List.generate(
            prefix.length,
            (i) => bytes[i] == prefix[i],
          ).contains(false) ||
          'SHA256:${base64Encode(sha256.convert(bytes).bytes).replaceAll('=', '')}' !=
              fingerprint) {
        throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
      }
    } catch (_) {
      throw const ByoHostFailure(ByoHostFailureCode.needsTrust);
    }
  }
  final String publicKey;
  final String fingerprint;
  Map<String, Object?> toJson() => {
    'publicKey': publicKey,
    'fingerprint': fingerprint,
  };
  factory ByoHostKey.fromJson(Map<String, dynamic> json) => ByoHostKey(
    publicKey: json['publicKey'] as String,
    fingerprint: json['fingerprint'] as String,
  );
}

/// Initial login only. Password/passphrase is never serialized or stored.
class ByoHostLogin {
  ByoHostLogin({this.privateKey, this.password, this.passphrase});
  String? privateKey;
  String? password;
  String? passphrase;
  void consume() {
    privateKey = null;
    password = null;
    passphrase = null;
  }

  @override
  String toString() => 'ByoHostLogin(<redacted>)';
}

class ByoHostIdentity {
  const ByoHostIdentity({required this.privateKey, required this.publicKey});
  final String privateKey;
  final String publicKey;
  @override
  String toString() => 'ByoHostIdentity(<redacted>)';
}

/// Every architecture has a reviewed full-bundle digest; no mutable sidecar trust.
class ByoHostBundle {
  ByoHostBundle({
    required this.version,
    required this.openCodeVersion,
    required Map<String, ByoHostArtifact> artifacts,
  }) : artifacts = Map.unmodifiable(artifacts) {
    if (!RegExp(r'^[0-9]+\.[0-9]+\.[0-9]+$').hasMatch(version) ||
        !RegExp(r'^[0-9]+\.[0-9]+\.[0-9]+$').hasMatch(openCodeVersion) ||
        artifacts.isEmpty) {
      throw const ByoHostFailure(ByoHostFailureCode.bundleUnavailable);
    }
    if (artifacts.keys.any((a) => !['x64', 'arm64'].contains(a))) {
      throw const ByoHostFailure(ByoHostFailureCode.bundleUnavailable);
    }
  }
  final String version;
  final String openCodeVersion;
  final Map<String, ByoHostArtifact> artifacts;
}

class ByoHostArtifact {
  ByoHostArtifact({required this.url, required this.sha256}) {
    final uri = Uri.tryParse(url);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        !RegExp(r'^[a-f0-9]{64}$').hasMatch(sha256)) {
      throw const ByoHostFailure(ByoHostFailureCode.bundleUnavailable);
    }
  }
  final String url;
  final String sha256;
}

class ByoHostDescriptor {
  const ByoHostDescriptor({
    required this.hostId,
    required this.bundleVersion,
    required this.openCodeVersion,
    required this.port,
  });
  final String hostId;
  final String bundleVersion;
  final String openCodeVersion;
  final int port;
}

abstract interface class ByoHostTunnel {
  int get localPort;
  Future<void> close();
}

/// All methods return safe failures. No remote stderr/body enters exceptions.
abstract interface class ByoHostSshRunner {
  Future<bool> available();
  Future<ByoHostKey> inspectKey(ByoHostTarget target);
  Future<ByoHostIdentity> generateIdentity(String profileId);
  Future<ByoHostDescriptor> install({
    required String profileId,
    required ByoHostTarget target,
    required ByoHostKey hostKey,
    required ByoHostLogin login,
    required ByoHostIdentity identity,
    required String deviceToken,
    required ByoHostBundle bundle,
  });
  Future<ByoHostTunnel> forward({
    required String profileId,
    required ByoHostTarget target,
    required ByoHostKey hostKey,
    required ByoHostIdentity identity,
    required int remotePort,
  });
  Future<ByoHostDescriptor> describe({
    required ByoHostTunnel tunnel,
    required String deviceId,
    required String deviceToken,
  });
  Future<bool> revoke({
    required ByoHostTunnel tunnel,
    required String deviceId,
    required String deviceToken,
  });
  Future<void> cleanup(String profileId);
  Future<void> dispose();
}

/// Single secure envelope; metadata never contains login or device secrets.
class ByoHostSecrets {
  const ByoHostSecrets({required this.identity, required this.deviceToken});
  final ByoHostIdentity identity;
  final String deviceToken;
  String encode() => jsonEncode({
    'privateKey': identity.privateKey,
    'publicKey': identity.publicKey,
    'deviceToken': deviceToken,
  });
  factory ByoHostSecrets.decode(String value) {
    final json = jsonDecode(value) as Map<String, dynamic>;
    return ByoHostSecrets(
      identity: ByoHostIdentity(
        privateKey: json['privateKey'] as String,
        publicKey: json['publicKey'] as String,
      ),
      deviceToken: json['deviceToken'] as String,
    );
  }
  @override
  String toString() => 'ByoHostSecrets(<redacted>)';
}

class ByoHostRecord {
  const ByoHostRecord({
    required this.profileId,
    required this.target,
    required this.hostKey,
    required this.phase,
    this.hostId,
    this.bundleVersion,
    this.openCodeVersion,
    this.remotePort,
    this.revoked = false,
  });
  final String profileId;
  final ByoHostTarget target;
  final ByoHostKey hostKey;
  final ByoHostPhase phase;
  final String? hostId;
  final String? bundleVersion;
  final String? openCodeVersion;
  final int? remotePort;
  final bool revoked;
  Map<String, Object?> toJson() => {
    'schema': 1,
    'profileId': profileId,
    'target': target.toJson(),
    'hostKey': hostKey.toJson(),
    'phase': phase.name,
    'hostId': hostId,
    'bundleVersion': bundleVersion,
    'openCodeVersion': openCodeVersion,
    'remotePort': remotePort,
    'revoked': revoked,
  };
  factory ByoHostRecord.fromJson(Map<String, dynamic> json) {
    if (json['schema'] != 1 || !byoHostSafeId(json['profileId'] as String)) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
    return ByoHostRecord(
      profileId: json['profileId'] as String,
      target: ByoHostTarget.fromJson(json['target'] as Map<String, dynamic>),
      hostKey: ByoHostKey.fromJson(json['hostKey'] as Map<String, dynamic>),
      phase: ByoHostPhase.values.byName(json['phase'] as String),
      hostId: json['hostId'] as String?,
      bundleVersion: json['bundleVersion'] as String?,
      openCodeVersion: json['openCodeVersion'] as String?,
      remotePort: json['remotePort'] as int?,
      revoked: json['revoked'] == true,
    );
  }
}

abstract interface class ByoHostStore {
  Future<List<ByoHostRecord>> listRecords();
  Future<ByoHostRecord?> readRecord(String profileId);
  Future<ByoHostSecrets?> readSecrets(String profileId);
  Future<void> saveRecord(ByoHostRecord record);
  Future<void> saveSecrets(String profileId, ByoHostSecrets secrets);
  Future<void> remove(String profileId);
}

class ByoHostSnapshot {
  const ByoHostSnapshot({
    required this.phase,
    this.record,
    this.hostKey,
    this.failure,
  });
  final ByoHostPhase phase;
  final ByoHostRecord? record;
  final ByoHostKey? hostKey;
  final ByoHostFailure? failure;
}
