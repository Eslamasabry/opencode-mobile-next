import 'dart:io';

import 'package:flutter/services.dart';

/// Public metadata only. The corresponding private key remains in Android's
/// Keystore; there is intentionally no export or raw-sign method.
class ByoHostSignerIdentity {
  const ByoHostSignerIdentity({
    required this.keyAlias,
    required this.publicKey,
  });
  final String keyAlias;
  final String publicKey;
}

abstract interface class ByoHostSigner {
  Future<ByoHostSignerIdentity> ensureIdentity(String profileId);
  Future<void> openAgent({
    required String profileId,
    required String socketPath,
    required String user,
  });
  Future<void> closeAgent(String profileId);
  Future<void> deleteIdentity(String profileId);
  Future<void> deleteAllIdentities();
}

/// Owns both sides of oc/byo_host_signer with ByoHostSigner.kt. Agent clients
/// receive signatures, never private key bytes. Native failures are deliberately
/// replaced by fixed error codes so platform details cannot reach diagnostics.
class AndroidByoHostSigner implements ByoHostSigner {
  static bool get supported => Platform.isAndroid;
  AndroidByoHostSigner({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('oc/byo_host_signer');
  final MethodChannel _channel;

  void _validateId(String id) {
    if (!RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(id)) {
      throw const ByoHostSignerException('invalid');
    }
  }

  Future<T?> _invoke<T>(String method, Map<String, String> arguments) async {
    try {
      return await _channel.invokeMethod<T>(method, arguments);
    } catch (_) {
      throw const ByoHostSignerException('unavailable');
    }
  }

  @override
  Future<ByoHostSignerIdentity> ensureIdentity(String profileId) async {
    _validateId(profileId);
    final result = await _invoke<Map<Object?, Object?>>('ensureIdentity', {
      'profileId': profileId,
    });
    final alias = result?['keyAlias'];
    final publicKey = result?['publicKey'];
    if (alias != 'oc.byoHostSsh.$profileId' ||
        publicKey is! String ||
        !RegExp(
          r'^ecdsa-sha2-nistp256 [A-Za-z0-9+/]+={0,2}$',
        ).hasMatch(publicKey)) {
      throw const ByoHostSignerException('protocol');
    }
    return ByoHostSignerIdentity(
      keyAlias: alias as String,
      publicKey: publicKey,
    );
  }

  @override
  Future<void> openAgent({
    required String profileId,
    required String socketPath,
    required String user,
  }) async {
    _validateId(profileId);
    if (!RegExp(r'^[A-Za-z_][A-Za-z0-9_.-]{0,63}$').hasMatch(user) ||
        !RegExp(
          r'^/.*/linux/ubuntu/tmp/\.oa-[a-f0-9]{16}/s$',
        ).hasMatch(socketPath) ||
        socketPath.contains('/../') ||
        socketPath.contains('/./')) {
      throw const ByoHostSignerException('invalid');
    }
    await _invoke<void>('openAgent', {
      'profileId': profileId,
      'socketPath': socketPath,
      'user': user,
    });
  }

  @override
  Future<void> closeAgent(String profileId) async {
    _validateId(profileId);
    await _invoke<void>('closeAgent', {'profileId': profileId});
  }

  @override
  Future<void> deleteAllIdentities() async {
    await _invoke<void>('deleteAllIdentities', const {});
  }

  @override
  Future<void> deleteIdentity(String profileId) async {
    _validateId(profileId);
    await _invoke<void>('deleteIdentity', {'profileId': profileId});
  }
}

class ByoHostSignerException implements Exception {
  const ByoHostSignerException(this.code);
  final String code;
  @override
  String toString() => 'ByoHostSignerException($code)';
}
