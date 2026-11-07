/// Termux is no longer a way to run OpenCode on this phone (owner decision,
/// 2026-11-07): built-in Linux is the only on-phone engine. A saved
/// connection an older build made for Termux's server still loads as an
/// ordinary saved server, and the app tells its owner once, in plain words,
/// what to do instead.
library;

import '../platform/platform_capabilities.dart';
import 'profiles.dart';

/// The fixed address older builds gave the OpenCode server they ran in
/// Termux (built-in Linux uses 4097).
const termuxRetiredServerPort = 4096;

/// The fixed address older builds gave the Claude Code daemon (Paseo) they
/// ran in Termux (the built-in agents use 4099).
const termuxRetiredAgentPort = 6767;

/// Whether [profile] is a saved connection an older build made for Termux:
/// OpenCode or the Claude Code daemon at the loopback address that build
/// always used, on Android.
bool isTermuxRetiredProfile(ServerProfile? profile, {bool? android}) {
  if (profile == null) return false;
  if (!(android ?? platformCapabilities.isAndroid)) return false;
  final uri = Uri.tryParse(profile.baseUrl.trim());
  if (uri == null || !isLoopbackHost(uri.host)) return false;
  final port = uri.hasPort ? uri.port : 80;
  return switch (profile.backend) {
    ServerBackend.openCode =>
      uri.scheme == 'http' && port == termuxRetiredServerPort,
    ServerBackend.paseo => uri.scheme == 'ws' && port == termuxRetiredAgentPort,
    ServerBackend.codex => false,
  };
}

/// The first saved Termux connection, preferring the active one, or null.
ServerProfile? termuxRetiredProfile(ProfileStore store, {bool? android}) {
  ServerProfile? first;
  for (final profile in store.profiles) {
    if (!isTermuxRetiredProfile(profile, android: android)) continue;
    if (profile.id == store.activeId) return profile;
    first ??= profile;
  }
  return first;
}
