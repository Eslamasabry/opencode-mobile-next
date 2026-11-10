/// Target and provenance contracts for Settings. No platform or storage types.
enum SettingOwnerKind {
  device,
  connection,
  host,
  providerAccount,
  project,
  chat,
}

enum SettingAuthority {
  localPreference,
  serverConfiguration,
  hostRuntime,
  providerAccount,
}

enum SettingValueState { known, unknown, unsupported, stale }

enum SettingOriginKind { defaultValue, explicitValue, migrated, reported }

enum SettingsFailureReason {
  unsupported,
  invalidValue,
  staleTarget,
  conflict,
  storageFailed,
  disposed,
  offline,
  needsSignIn,
  managed,
  outcomeUnknown,
}

/// IDs are non-secret identities, never presentation labels or credentials.
class SettingsTarget {
  const SettingsTarget.device()
    : kind = SettingOwnerKind.device,
      ownerId = 'device',
      profileId = null,
      projectId = null,
      sessionId = null;

  const SettingsTarget.connection(String profileId)
    : assert(profileId != ''),
      kind = SettingOwnerKind.connection,
      ownerId = profileId,
      profileId = profileId,
      projectId = null,
      sessionId = null;

  const SettingsTarget.host(String hostId)
    : assert(hostId != ''),
      kind = SettingOwnerKind.host,
      ownerId = hostId,
      profileId = null,
      projectId = null,
      sessionId = null;

  const SettingsTarget.providerAccount(String accountId)
    : assert(accountId != ''),
      kind = SettingOwnerKind.providerAccount,
      ownerId = accountId,
      profileId = null,
      projectId = null,
      sessionId = null;

  const SettingsTarget.project(String profileId, String projectId)
    : assert(profileId != ''),
      assert(projectId != ''),
      kind = SettingOwnerKind.project,
      ownerId = projectId,
      profileId = profileId,
      projectId = projectId,
      sessionId = null;

  const SettingsTarget.chat(
    String profileId,
    String sessionId, {
    this.projectId,
  }) : assert(profileId != ''),
       assert(sessionId != ''),
       assert(projectId == null || projectId != ''),
       kind = SettingOwnerKind.chat,
       ownerId = sessionId,
       profileId = profileId,
       sessionId = sessionId;

  final SettingOwnerKind kind;
  final String ownerId;
  final String? profileId;
  final String? projectId;
  final String? sessionId;

  @override
  bool operator ==(Object other) =>
      other is SettingsTarget &&
      kind == other.kind &&
      ownerId == other.ownerId &&
      profileId == other.profileId &&
      projectId == other.projectId &&
      sessionId == other.sessionId;

  @override
  int get hashCode =>
      Object.hash(kind, ownerId, profileId, projectId, sessionId);
}

class SettingsRowScope {
  const SettingsRowScope({
    required this.rowId,
    required Set<SettingOwnerKind> owners,
    required this.labelCode,
  }) : _owners = owners;

  final String rowId;
  final Set<SettingOwnerKind> _owners;
  final String labelCode;

  /// Catalog sets are const; callers cannot mutate the exposed collection.
  Set<SettingOwnerKind> get owners => Set.unmodifiable(_owners);
}

class EffectiveSetting {
  const EffectiveSetting({
    required this.id,
    required this.target,
    this.value,
    this.state = SettingValueState.known,
    this.authority = SettingAuthority.localPreference,
    this.origin = SettingOriginKind.defaultValue,
    this.revision = 0,
    this.writable = false,
    this.reason,
  });

  final String id;
  final SettingsTarget target;
  final Object? value;
  final SettingValueState state;
  final SettingAuthority authority;
  final SettingOriginKind origin;
  final int revision;
  final bool writable;
  final SettingsFailureReason? reason;
}

class SettingsEditToken {
  const SettingsEditToken({
    required this.before,
    required this.ownerFingerprint,
  });

  final EffectiveSetting before;
  final String ownerFingerprint;
}

class SettingsReceipt {
  const SettingsReceipt({required this.confirmed});
  final EffectiveSetting confirmed;
}

class SettingsFailure implements Exception {
  const SettingsFailure(this.reason);
  final SettingsFailureReason reason;

  @override
  String toString() => 'SettingsFailure(${reason.name})';
}

abstract interface class SettingsAccess {
  Future<EffectiveSetting> readSetting(String id, SettingsTarget target);
  Stream<EffectiveSetting> watchSetting(String id, SettingsTarget target);
  Future<SettingsEditToken> beginEdit(String id, SettingsTarget target);
  Future<SettingsReceipt> writeSetting(SettingsEditToken edit, Object? value);
}

/// Ownership describes a destination, not permission to write every owner.
/// Aliases deliberately share metadata, never create duplicate preferences.
abstract final class SettingsScopeCatalog {
  static const _device = {SettingOwnerKind.device};
  static const _connection = {SettingOwnerKind.connection};
  static const _host = {SettingOwnerKind.host};
  static const _registry = {
    SettingOwnerKind.device,
    SettingOwnerKind.connection,
  };
  static const _account = {
    SettingOwnerKind.connection,
    SettingOwnerKind.providerAccount,
  };
  static const _project = {
    SettingOwnerKind.connection,
    SettingOwnerKind.project,
  };
  static const _rules = {
    SettingOwnerKind.connection,
    SettingOwnerKind.project,
    SettingOwnerKind.chat,
  };
  static const _team = {
    SettingOwnerKind.connection,
    SettingOwnerKind.host,
    SettingOwnerKind.project,
  };
  static const _deviceHost = {SettingOwnerKind.device, SettingOwnerKind.host};

  static const List<SettingsRowScope> _rows = [
    SettingsRowScope(
      rowId: 'settings-saved-servers',
      owners: _registry,
      labelCode: 'connection',
    ),
    SettingsRowScope(
      rowId: 'settings-tailscale',
      owners: _registry,
      labelCode: 'connection',
    ),
    SettingsRowScope(
      rowId: 'settings-add-server',
      owners: _registry,
      labelCode: 'connection',
    ),
    SettingsRowScope(
      rowId: 'inside-server-disconnect',
      owners: _registry,
      labelCode: 'connection',
    ),
    SettingsRowScope(
      rowId: 'settings-category-server',
      owners: _connection,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'settings-server-capabilities',
      owners: _connection,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'settings-server-plugins',
      owners: _connection,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'inside-server-ai-setup',
      owners: _connection,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'settings-this-phone',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'settings-on-this-phone',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'inside-phone-running-now',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'inside-phone-storage',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'inside-phone-crash-recovery',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'inside-phone-claude-code',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'inside-phone-claude-code-unavailable',
      owners: _host,
      labelCode: 'host',
    ),
    SettingsRowScope(
      rowId: 'settings-agents',
      owners: _team,
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'settings-ai-team',
      owners: _team,
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'settings-model-and-mode',
      owners: _connection,
      labelCode: 'newChats',
    ),
    SettingsRowScope(
      rowId: 'settings-models',
      owners: _connection,
      labelCode: 'newChats',
    ),
    SettingsRowScope(
      rowId: 'settings-providers',
      owners: _account,
      labelCode: 'serverAccount',
    ),
    SettingsRowScope(
      rowId: 'settings-accounts',
      owners: _account,
      labelCode: 'serverAccount',
    ),
    SettingsRowScope(
      rowId: 'settings-mcp',
      owners: _rules,
      labelCode: 'nativeConfiguration',
    ),
    SettingsRowScope(
      rowId: 'settings-show-reasoning',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-show-timestamps',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-voice',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-category-appearance',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-setup-guide',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-tips',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'library-report-bug',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-about-notices',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-external-agents',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-privacy-data-use',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-try-demo',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'library-keyboard-shortcuts',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-show-tips-again',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-voice-notices',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-notifications-what',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-notifications-quiet',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-notifications-background',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-mode',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-language',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-theme',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-text',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-live-background',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-motion',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'inside-appearance-glow',
      owners: _device,
      labelCode: 'device',
    ),
    SettingsRowScope(
      rowId: 'settings-keep-running',
      owners: _deviceHost,
      labelCode: 'deviceAndHost',
    ),
    SettingsRowScope(
      rowId: 'inside-keep-running-battery',
      owners: _deviceHost,
      labelCode: 'deviceAndHost',
    ),
    SettingsRowScope(
      rowId: 'inside-keep-running-thermal',
      owners: _deviceHost,
      labelCode: 'deviceAndHost',
    ),
    SettingsRowScope(
      rowId: 'inside-notifications-servers',
      owners: _connection,
      labelCode: 'connectionLocal',
    ),
    SettingsRowScope(
      rowId: 'inside-usage-spent',
      owners: _project,
      labelCode: 'measurement',
    ),
    SettingsRowScope(
      rowId: 'inside-usage-budgets',
      owners: _project,
      labelCode: 'measurement',
    ),
    SettingsRowScope(
      rowId: 'inside-usage-remaining',
      owners: _account,
      labelCode: 'accountSource',
    ),
    SettingsRowScope(
      rowId: 'inside-usage-alerts',
      owners: _account,
      labelCode: 'accountSource',
    ),
    SettingsRowScope(
      rowId: 'settings-commands-tools',
      owners: _project,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'inside-capabilities-commands',
      owners: _project,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'inside-capabilities-tools',
      owners: _project,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'inside-capabilities-skills',
      owners: _project,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'inside-capabilities-references',
      owners: _project,
      labelCode: 'serverReported',
    ),
    SettingsRowScope(
      rowId: 'settings-automation',
      owners: _rules,
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'saved-permissions-entry',
      owners: _rules,
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'default-shell-settings-entry',
      owners: _connection,
      labelCode: 'nativeConfiguration',
    ),
    SettingsRowScope(
      rowId: 'settings-tools',
      owners: {
        SettingOwnerKind.device,
        SettingOwnerKind.connection,
        SettingOwnerKind.project,
        SettingOwnerKind.chat,
      },
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'settings-phrase-chips',
      owners: _registry,
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'settings-category-background',
      owners: {
        SettingOwnerKind.device,
        SettingOwnerKind.connection,
        SettingOwnerKind.host,
      },
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'settings-category-privacy',
      owners: _registry,
      labelCode: 'byOwner',
    ),
    SettingsRowScope(
      rowId: 'settings-category-usage',
      owners: {
        SettingOwnerKind.connection,
        SettingOwnerKind.project,
        SettingOwnerKind.providerAccount,
      },
      labelCode: 'byOwner',
    ),
  ];

  static List<SettingsRowScope> get rows => _rows;

  static final Map<String, SettingsRowScope> _byId = Map.unmodifiable({
    for (final row in _rows) row.rowId: row,
  });

  static SettingsRowScope? forRow(String rowId) => _byId[rowId];
}
