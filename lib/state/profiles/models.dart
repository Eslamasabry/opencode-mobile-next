part of '../profiles.dart';

/// A model (and variant) chosen for one session from inside its chat.
class SessionModelChoice {
  final ModelRef model;
  final String variant;
  const SessionModelChoice({required this.model, this.variant = ''});
}

/// One opencode server the user can connect to.
/// Which server a profile talks to. `codex` and `paseo` are agent sockets: a
/// WebSocket endpoint, one secret, and one project folder, stored in the
/// shared `codexToken` / `codexDirectory` fields.
enum ServerBackend { openCode, codex, paseo }

/// Which orchestration host the AI Team plugin talks to for a profile.
enum OrchestrationProvider {
  /// A Gas City supervisor reached over HTTP(S).
  gascity,

  /// The recorded fixture in `tool/qa/gascity_fixture` (tests and demos).
  fixture,

  /// Native phone-resident durable project engine.
  phoneEngine;

  /// Maps a stored name; unknown names fall back to [gascity] so an old
  /// profile never loses its host.
  static OrchestrationProvider fromName(Object? name) =>
      name == phoneEngine.name
      ? phoneEngine
      : name == fixture.name
      ? fixture
      : gascity;
}

/// The kind of machine an AI Team host runs on, chosen by the person when
/// they add the host and used only for the performance disclaimer
/// (03-onboarding §4, TEAM-206). Finer than [OrchestrationHostMode]: every
/// kind maps to one mode ([mode]), and a mode without a chosen kind takes
/// [forMode]'s default so a config stored before this field existed still
/// shows a disclaimer.
enum OrchestrationHostKind {
  /// A desktop computer that stays awake.
  pc,

  /// A laptop: sleep and lid-close pause the team.
  laptop,

  /// Windows via WSL2: sleeps like a laptop and stops with its last terminal.
  wsl,

  /// This phone (Termux); set from the host mode, never chosen in the form.
  phone;

  /// The host mode this kind belongs to.
  OrchestrationHostMode get mode => switch (this) {
    pc || laptop || wsl => OrchestrationHostMode.computer,
    phone => OrchestrationHostMode.phone,
  };

  /// The default kind for a mode: a computer is a [pc] until told otherwise.
  static OrchestrationHostKind forMode(OrchestrationHostMode mode) =>
      switch (mode) {
        OrchestrationHostMode.computer => pc,
        OrchestrationHostMode.phone => phone,
      };

  /// Maps a stored name; null for an unknown or missing one so the caller
  /// can fall back to [forMode].
  static OrchestrationHostKind? fromName(Object? name) {
    for (final kind in values) {
      if (kind.name == name) return kind;
    }
    return null;
  }
}

/// The AI Team plugin's per-profile settings (04-plugin-architecture §1).
/// A profile whose [ServerProfile.orchestration] is null has the plugin
/// off: no controller, no store keys, no widgets in the tree.
///
/// Immutable; [toJson] / [fromJson] round-trip every field and tolerate a
/// stored shape from an older build (missing fields take their defaults).
class OrchestrationConfig {
  const OrchestrationConfig({
    required this.provider,
    required this.url,
    this.city = '',
    this.hostMode = OrchestrationHostMode.computer,
    OrchestrationHostKind? hostKind,
    this.front = false,
    this.enabledAt,
  }) : hostKind =
           hostKind ??
           (hostMode == OrchestrationHostMode.phone
               ? OrchestrationHostKind.phone
               : OrchestrationHostKind.pc);

  final OrchestrationProvider provider;

  /// Base URL of the host (`https://…`, `http://` to loopback or tailnet
  /// only); for [OrchestrationProvider.fixture], the fixture directory.
  final String url;

  /// Gas City city name; empty means whatever the host reports.
  final String city;

  /// Where the host runs relative to the OpenCode server.
  final OrchestrationHostMode hostMode;

  /// The kind of machine behind [hostMode], for the disclaimer line only.
  /// Always agrees with [hostMode]: absent or contradicting input takes
  /// [OrchestrationHostKind.forMode].
  final OrchestrationHostKind hostKind;

  /// True once the host front is in use (Sprint B); the read adapter alone
  /// never writes.
  final bool front;

  /// When the person turned the plugin on for this profile.
  final DateTime? enabledAt;

  OrchestrationConfig copyWith({
    OrchestrationProvider? provider,
    String? url,
    String? city,
    OrchestrationHostMode? hostMode,
    OrchestrationHostKind? hostKind,
    bool? front,
    DateTime? enabledAt,
  }) {
    final mode = hostMode ?? this.hostMode;
    return OrchestrationConfig(
      provider: provider ?? this.provider,
      url: url ?? this.url,
      city: city ?? this.city,
      hostMode: mode,
      hostKind: _kindFor(mode, hostKind ?? this.hostKind),
      front: front ?? this.front,
      enabledAt: enabledAt ?? this.enabledAt,
    );
  }

  /// [kind] when it belongs to [mode], else the mode's default; a kind
  /// never contradicts the mode it is stored with.
  static OrchestrationHostKind _kindFor(
    OrchestrationHostMode mode,
    OrchestrationHostKind? kind,
  ) => kind != null && kind.mode == mode
      ? kind
      : OrchestrationHostKind.forMode(mode);

  Map<String, dynamic> toJson() => {
    'provider': provider.name,
    'url': url,
    'city': city,
    'hostMode': hostMode.name,
    'hostKind': hostKind.name,
    'front': front,
    if (enabledAt != null) 'enabledAt': enabledAt!.toUtc().toIso8601String(),
  };

  /// Decodes a stored config; null for null, non-map or url-less input so
  /// a corrupt entry reads as "plugin off" rather than crashing the load.
  static OrchestrationConfig? fromJson(Object? json) {
    if (json is! Map) return null;
    final url = json['url'];
    if (url is! String || url.isEmpty) return null;
    final enabledAt = json['enabledAt'];
    final hostMode = json['hostMode'] == OrchestrationHostMode.phone.name
        ? OrchestrationHostMode.phone
        : OrchestrationHostMode.computer;
    return OrchestrationConfig(
      provider: OrchestrationProvider.fromName(json['provider']),
      url: url,
      city: (json['city'] ?? '').toString(),
      hostMode: hostMode,
      // Stored before TEAM-206, or an unknown name: the mode's default.
      hostKind: _kindFor(
        hostMode,
        OrchestrationHostKind.fromName(json['hostKind']),
      ),
      front: json['front'] == true,
      enabledAt: enabledAt is String ? DateTime.tryParse(enabledAt) : null,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is OrchestrationConfig &&
      other.provider == provider &&
      other.url == url &&
      other.city == city &&
      other.hostMode == hostMode &&
      other.hostKind == hostKind &&
      other.front == front &&
      other.enabledAt == enabledAt;

  @override
  int get hashCode =>
      Object.hash(provider, url, city, hostMode, hostKind, front, enabledAt);

  @override
  String toString() =>
      'OrchestrationConfig(${provider.name} $url ${city.isEmpty ? '' : city}'
      ' ${hostMode.name}/${hostKind.name}${front ? ' front' : ''})';
}

class ServerProfile {
  final String id;
  String name;
  String baseUrl;
  ServerBackend backend;
  String username;
  String password; // kept in secure storage, mirrored here at runtime
  /// Runtime-only signal that the saved password could not be decrypted.
  bool requiresPasswordReentry;

  /// Runtime-only Codex connection secret. It is kept in secure storage under
  /// its own key and is intentionally excluded from profile JSON.
  String codexToken;

  /// Phone engine bearer auth, runtime-only and never included in JSON.
  String teamEngineAuth;

  /// Codex project directory stored as profile metadata, never as a secret.
  String codexDirectory;

  /// Runtime-only signal that the saved Codex token could not be restored.
  bool requiresCodexTokenReentry;

  /// Protocol generation detected at Test/connect time. Additive: profiles
  /// saved before flavor detection default to [ServerFlavor.v1]. Cached so a
  /// reconnect skips re-detection; a failed connect re-probes and corrects it.
  ServerFlavor flavor;

  /// Server version reported by the last successful probe/connect, cached
  /// alongside [flavor] for the servers list.
  String? serverVersion;

  /// AI Team plugin settings; null means the plugin is off for this server.
  OrchestrationConfig? orchestration;

  /// The cleartext origin the person confirmed for this profile
  /// (`oc.cleartextOk.<profileId>`), mirrored here at runtime. Never part of
  /// the profile JSON.
  String? cleartextConfirmedOrigin;

  /// True when this profile speaks plain HTTP to a private network address
  /// and the person has not confirmed that for this exact address.
  bool get cleartextUnconfirmed =>
      !usesAgentSocket &&
      serverUrlNeedsCleartextConfirmation(baseUrl) &&
      cleartextConfirmedOrigin != cleartextOriginOf(baseUrl);

  ServerProfile({
    required this.id,
    required this.name,
    required this.baseUrl,
    this.backend = ServerBackend.openCode,
    this.username = '',
    this.password = '',
    this.requiresPasswordReentry = false,
    this.codexToken = '',
    this.teamEngineAuth = '',
    this.codexDirectory = '',
    this.requiresCodexTokenReentry = false,
    this.flavor = ServerFlavor.v1,
    this.serverVersion,
    this.orchestration,
  });

  /// Codex app-server and the Paseo daemon share the socket-style profile:
  /// endpoint, secret ([codexToken]) and project folder ([codexDirectory]).
  bool get usesAgentSocket => backend != ServerBackend.openCode;

  /// A Codex token is mandatory; a Paseo daemon on a private network may run
  /// without a password.
  bool get agentSocketSecretRequired => backend == ServerBackend.codex;

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'baseUrl': baseUrl,
    'backend': backend.name,
    'username': username,
    if (usesAgentSocket) 'codexDirectory': codexDirectory,
    'flavor': flavor.name,
    if (serverVersion != null) 'serverVersion': serverVersion,
    if (orchestration != null) 'orchestration': orchestration!.toJson(),
  };

  static ServerProfile fromJson(Map<String, dynamic> j) => ServerProfile(
    id: j['id'] as String,
    name: plainServerName((j['name'] ?? '').toString()),
    baseUrl: (j['baseUrl'] ?? '').toString(),
    backend: switch (j['backend']) {
      'codex' => ServerBackend.codex,
      'paseo' => ServerBackend.paseo,
      _ => ServerBackend.openCode,
    },
    username: (j['username'] ?? '').toString(),
    codexDirectory: (j['codexDirectory'] ?? '').toString(),
    flavor: j['flavor'] == ServerFlavor.v2.name
        ? ServerFlavor.v2
        : ServerFlavor.v1,
    serverVersion: j['serverVersion']?.toString(),
    orchestration: OrchestrationConfig.fromJson(j['orchestration']),
  );
}
