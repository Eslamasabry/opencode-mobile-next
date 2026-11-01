/// Browser side of the profile-secret probe; see `browser_secret_storage.dart`.
/// Selected by the conditional import in `profiles.dart` whenever
/// `dart:js_interop` exists. Nothing here touches `dart:io`.
library;

import 'package:web/web.dart' as web;

import 'browser_secret_storage.dart';

/// The key the write probe touches; removed again immediately.
const browserSecretStorageProbeKey = 'oc.secure.probe';

/// Mirrors the checks flutter_secure_storage_web makes lazily, without
/// storing anything: WebCrypto needs a secure context, and the ciphertext
/// needs a localStorage that accepts writes. Reading a key is not enough
/// for the second (some private modes read fine and refuse writes), so the
/// probe writes and removes one throwaway entry.
BrowserSecretStorageState browserSecretStorageState() {
  if (!web.window.isSecureContext) {
    return BrowserSecretStorageState.insecureContext;
  }
  try {
    final storage = web.window.localStorage;
    storage.setItem(browserSecretStorageProbeKey, '1');
    storage.removeItem(browserSecretStorageProbeKey);
  } catch (_) {
    return BrowserSecretStorageState.storageBlocked;
  }
  return BrowserSecretStorageState.available;
}
