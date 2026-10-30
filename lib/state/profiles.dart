import 'dart:convert';

import 'package:flutter/foundation.dart'
    show ChangeNotifier, Listenable, TargetPlatform;
import 'package:flutter/services.dart'
    show MissingPluginException, PlatformException;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../ui/kit/kit_redact.dart';
import 'factsbook/factsbook_store.dart' show FactsBookStore;
import '../domain/phone_agent_host.dart';
import '../builtin/agents/agent_host_cleanup.dart';
import '../builtin/builtin_linux.dart';

import '../domain/models.dart' show ModelRef;
import '../domain/server_probe.dart' show ServerFlavor;
import '../domain/loopback_host.dart';
import '../domain/byo_host.dart';
import '../host/byo_host_signer.dart';
import '../domain/orchestration_gateway.dart' show OrchestrationHostMode;
import '../orchestration/adapters/gascity/gascity_probe.dart'
    show isTailnetHost;
import '../platform/platform_capabilities.dart';
// A plain value type (no widgets): the person's effect choices.
import 'effects.dart' show KitEffects, KitTextSize;
import 'app_preferences.dart';
import '../domain/settings_scope.dart';
import 'model_library.dart';
import 'interaction_defaults.dart';
import 'context_usage_memory.dart';
import 'phrase_chips.dart';
import 'setup_audit_store.dart';
import 'session_link_bindings.dart';
import 'agent_handoff_links.dart';
import 'pending_command_journal.dart';

export '../domain/server_probe.dart' show ServerFlavor;
export '../domain/loopback_host.dart' show isLoopbackHost, isPrivateNetworkHost;
export '../domain/orchestration_gateway.dart' show OrchestrationHostMode;

part 'profiles/models.dart';
part 'profiles/validation.dart';
part 'profiles/preferences_types.dart';
part 'profiles/phone_agent_owner.dart';

class _ProfileStoreChanges extends ChangeNotifier {
  void changed() => notifyListeners();
}

/// Persists server profiles. Metadata in SharedPreferences, secrets in the
/// Android Keystore via flutter_secure_storage.
class ProfileStore {
  static const _profilesKey = 'oc.profiles';
  static const _activeKey = 'oc.activeProfile';

  /// The origin the person confirmed for non-loopback plain HTTP,
  /// per profile: `oc.cleartextOk.<profileId>`. Named by the
  /// `oc.<what>.<profileId>` rule so the deletion sweep removes it.
  static const cleartextConfirmedKeyPrefix = 'oc.cleartextOk.';

  /// JSON containing the exact HTTPS origin and SHA-256 certificate pin.
  /// The profile deletion sweep owns this key through its normal namespace.
  static const certificatePinKeyPrefix = 'oc.certificatePin.';
  static const _passwordKey = 'pw.';
  static const byoHostSecretsKeyPrefix = 'oc.byoHostSecrets.';
  static const _codexTokenKey = 'oc.codexToken.';
  static const teamEngineAuthKey = 'oc.teamEngineAuth.';

  /// Secure-storage slot (`oc.quotaToken.<profileId>`) for the read token of
  /// the usage collector the app runs in In-app Ubuntu. Deleted with the
  /// profile.
  static const quotaTokenKeyPrefix = 'oc.quotaToken.';

  /// Secure-storage slot (`oc.githubToken.<profileId>`) for the GitHub
  /// sign-in the Git page hands to `git push`/`git pull` in that profile's
  /// environment (lib/state/github_account.dart). Deleted with the profile
  /// and by the saved sign-ins reset.
  static const githubTokenKeyPrefix = 'oc.githubToken.';
  static const _modelKey = 'oc.model.'; // + profileId -> "providerID|modelID"
  static const _modelExplicitKey = 'oc.modelExplicit.'; // + profileId
  static const _agentKey = 'oc.agent.'; // + profileId
  static const _variantKey = 'oc.variant.'; // + profileId
  // + profileId -> JSON {sessionID: "providerID|modelID|variant"}
  static const _sessionModelsKey = 'oc.sessionModels.';
  static const _sessionModelsCap = 200;
  static const _modelLibraryKey = 'oc.modelLibrary.';
  static const _locationKey = 'oc.location.'; // + profileId -> JSON
  // + profileId -> JSON list, most recent first. Ends in the profile id, so
  // the profile deletion sweep removes it.
  static const _recentLocationsKey = 'oc.recentLocations.';

  /// How many projects a server remembers. Enough to move between the ones
  /// in play this week; not a history.
  static const maxRecentLocations = 8;
  static const _providerRuntimeRefreshVersion = 'v1';

  final SharedPreferences prefs;

  AppPreferencesStore get appPreferences =>
      AppPreferencesStore.forPreferences(prefs);
  final FlutterSecureStorage secure;
  final ByoHostSigner? byoHostSigner;
  final Future<void> Function(String)? agentHostCleanup;

  ProfileStore({
    required this.prefs,
    FlutterSecureStorage? secure,
    ByoHostSigner? byoHostSigner,
    Future<void> Function(String)? agentHostCleanup,
  }) : secure = secure ?? const FlutterSecureStorage(),
       agentHostCleanup =
           agentHostCleanup ??
           (ChannelAgentHostCleanup.supported
               ? ChannelAgentHostCleanup().call
               : null),
       byoHostSigner =
           byoHostSigner ??
           (AndroidByoHostSigner.supported ? AndroidByoHostSigner() : null);

  static final _profileCaches = Expando<List<ServerProfile>>();
  List<ServerProfile> get _cache => _profileCaches[prefs] ??= [];
  set _cache(List<ServerProfile> value) => _profileCaches[prefs] = value;
  List<ServerProfile> get profiles => List.unmodifiable(_cache);

  static final _changeOwners = Expando<_ProfileStoreChanges>();
  _ProfileStoreChanges get _changes =>
      _changeOwners[prefs] ??= _ProfileStoreChanges();

  /// Fires after a profile is saved. Profiles are also changed in place
  /// (phone setup renames the in-app one to "This phone" while the app is
  /// connected to it), and nothing else tells the screens that show a name:
  /// without this the app bar kept the old name until a restart.
  Listenable get changes => _changes;

  /// Bootstrap recovery only: removes saved profile sign-ins and the active
  /// selection without loading or rewriting profiles, drafts, queues or settings.
  /// The caller must exclude concurrent bootstrap loads and live connections.
  /// Enumerating secure keys includes orphaned sign-ins, while unrelated secure
  /// entries remain untouched. A partial failure is retryable, never success.
  Future<void> resetSavedSignIns() async {
    // Invalidate retained references even if storage fails part-way through.
    // Keep redaction registrations: late diagnostics can still contain a secret.
    for (final profile in _cache) {
      profile.requiresPasswordReentry =
          !profile.usesAgentSocket &&
          (profile.password.isNotEmpty || profile.requiresPasswordReentry);
      profile.requiresCodexTokenReentry =
          profile.usesAgentSocket &&
          (profile.agentSocketSecretRequired ||
              profile.codexToken.isNotEmpty ||
              profile.requiresCodexTokenReentry);
      profile.password = '';
      profile.codexToken = '';
      profile.teamEngineAuth = '';
    }
    bool ownsKey(String key) =>
        key.startsWith(_passwordKey) ||
        key.startsWith(_codexTokenKey) ||
        key.startsWith(teamEngineAuthKey) ||
        key.startsWith(quotaTokenKeyPrefix) ||
        key.startsWith(githubTokenKeyPrefix) ||
        key.startsWith(phoneAgentHostSecretPrefix) ||
        key.startsWith(byoHostSecretsKeyPrefix);
    try {
      final secrets = await secure.readAll();
      final owned = <String>[
        for (final entry in secrets.entries)
          if (ownsKey(entry.key)) entry.key,
      ];
      for (final key in owned) {
        KitRedact.registerKnownSecret(secrets[key]!);
      }
      // Confirm selection removal before deleting secrets. A preferences
      // refusal leaves all durable sign-ins available for another attempt.
      if (!await prefs.remove(_activeKey)) {
        throw const SavedSignInResetException();
      }
      await byoHostSigner?.deleteAllIdentities();
      for (final key in owned.where(
        (key) => key.startsWith(phoneAgentHostSecretPrefix),
      )) {
        await agentHostCleanup?.call(
          key.substring(phoneAgentHostSecretPrefix.length),
        );
      }
      for (final key in owned) {
        await secure.delete(key: key);
      }
      final remaining = await secure.readAll();
      for (final entry in remaining.entries) {
        if (ownsKey(entry.key)) {
          KitRedact.registerKnownSecret(entry.value);
        }
      }
      if (remaining.keys.any(ownsKey)) {
        throw const SavedSignInResetException();
      }
      await prefs.reload();
      if (prefs.containsKey(_activeKey)) {
        throw const SavedSignInResetException();
      }
    } catch (_) {
      // SharedPreferences changes its cache before platform confirmation.
      try {
        await prefs.reload();
      } catch (_) {}
      // Never retain a keyring exception: it may echo any stored credential.
      throw const SavedSignInResetException();
    } finally {
      _changes.changed();
    }
  }

  /// Where the retired personal quota budgets were kept, per profile
  /// (`oc.budgets.<profileId>`). Quota monitoring's own threshold replaced
  /// them and nothing reads or writes them any more.
  static const retiredQuotaBudgetsPrefix = 'oc.budgets.';

  /// Drops what the retired quota budgets left on the device. It runs on
  /// every load, but only the first finds anything. A key the store refuses
  /// to drop stays for the next load, and profile deletion's sweep still
  /// matches it as an `oc.<what>.<profileId>` key.
  Future<void> _retireQuotaBudgets() async {
    final keys = [
      for (final key in prefs.getKeys())
        if (key.startsWith(retiredQuotaBudgetsPrefix)) key,
    ];
    for (final key in keys) {
      try {
        await prefs.remove(key);
      } catch (_) {}
    }
  }

  /// Keys nothing reads any more: the retired liquid glass crash guard's
  /// strike counters, and the live background's old on/off (it went off by
  /// default on 2026-10-26 and moved to a new key).
  static const retiredPreferenceKeys = [
    'oc.glassLiquidActive',
    'oc.glassLiquidStrikes',
    'oc.glassLiquidOffUntil',
    'oc.effectsLiveBackground',
  ];

  /// Drops [retiredPreferenceKeys]. Runs on every load; a key the store
  /// refuses to drop stays for the next load.
  Future<void> _retirePreferences() async {
    for (final key in retiredPreferenceKeys) {
      if (!prefs.containsKey(key)) continue;
      try {
        await prefs.remove(key);
      } catch (_) {}
    }
  }

  /// Explicit one-shot launcher action: restore only its saved profile. Unlike
  /// app bootstrap this performs no migrations, preference writes or hosting.
  Future<ServerProfile?> loadForWidgetAction(String profileId) async {
    await prefs.reload();
    final raw = prefs.getString(_profilesKey);
    if (raw == null) return null;
    final values = jsonDecode(raw);
    if (values is! List) return null;
    final metadata = values
        .whereType<Map>()
        .where((p) => p['id'] == profileId)
        .firstOrNull;
    if (metadata == null) return null;
    final profile = ServerProfile.fromJson(Map<String, dynamic>.from(metadata));
    profile.cleartextConfirmedOrigin = prefs.getString(
      '$cleartextConfirmedKeyPrefix$profileId',
    );
    _restoreCertificatePin(profile);
    await _restoreSecret(profile);
    return profile;
  }

  Future<List<ServerProfile>> load() async {
    await _retireQuotaBudgets();
    await _retirePreferences();
    final raw = prefs.getString(_profilesKey);
    if (raw == null) {
      _cache = [];
      await appPreferences.readStateConsent.migrateExistingProfiles(const []);
      return _cache;
    }
    try {
      final list = jsonDecode(raw) as List;
      _cache = list
          .whereType<Map<String, dynamic>>()
          .map(ServerProfile.fromJson)
          .toList();
    } catch (_) {
      _cache = [];
    }
    await appPreferences.readStateConsent.migrateExistingProfiles(
      _cache.map((profile) => profile.id),
    );
    for (final p in _cache) {
      p.cleartextConfirmedOrigin = prefs.getString(
        '$cleartextConfirmedKeyPrefix${p.id}',
      );
      _restoreCertificatePin(p);
    }
    // Independent Keystore reads can overlap. Bound the fan-out so many
    // saved servers do not flood the platform channel. Await every secret
    // (and its redaction registration) before bootstrap may expose the shell.
    const batchSize = 4;
    for (var start = 0; start < _cache.length; start += batchSize) {
      await Future.wait(_cache.skip(start).take(batchSize).map(_restoreSecret));
    }
    await _migratePhoneAgentOwners();
    return _cache;
  }

  Future<void> _restoreSecret(ServerProfile p) async {
    try {
      p.teamEngineAuth =
          await secure.read(key: '$teamEngineAuthKey${p.id}') ?? '';
      KitRedact.registerKnownSecret(p.teamEngineAuth);
    } catch (_) {
      p.teamEngineAuth = '';
    }
    try {
      if (p.usesAgentSocket) {
        p.codexToken = await secure.read(key: '$_codexTokenKey${p.id}') ?? '';
        KitRedact.registerKnownSecret(p.codexToken);
        p.requiresCodexTokenReentry =
            p.agentSocketSecretRequired && p.codexToken.isEmpty;
        p.password = '';
        p.requiresPasswordReentry = false;
      } else {
        p.password = await secure.read(key: '$_passwordKey${p.id}') ?? '';
        KitRedact.registerKnownSecret(p.password);
        p.requiresPasswordReentry = false;
        p.codexToken = '';
        p.requiresCodexTokenReentry = false;
      }
    } catch (_) {
      // Keystore entries can become unreadable after a device restore or a
      // lock-screen security change. Keep the non-secret profile usable so
      // the user can re-enter its password instead of failing app startup.
      if (p.usesAgentSocket) {
        p.codexToken = '';
        p.requiresCodexTokenReentry = true;
        p.password = '';
        p.requiresPasswordReentry = false;
      } else {
        p.password = '';
        p.requiresPasswordReentry = true;
        p.codexToken = '';
        p.requiresCodexTokenReentry = false;
      }
    }
  }

  String _encode(List<ServerProfile> profiles) =>
      jsonEncode(profiles.map((p) => p.toJson()).toList());

  Future<void> _restoreProfiles(String? raw) async {
    final restored = raw == null
        ? await prefs.remove(_profilesKey)
        : await prefs.setString(_profilesKey, raw);
    if (!restored) {
      throw StateError('Could not restore the saved server profiles');
    }
  }

  Future<void> upsert(ServerProfile profile) async {
    // Reject malformed explicit trust before writing metadata or credentials.
    final suppliedPin = profile.certificatePinSha256;
    if ((suppliedPin != null &&
            !RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(suppliedPin)) ||
        ((suppliedPin == null) != (profile.certificatePinOrigin == null))) {
      throw ArgumentError('Invalid server certificate pin');
    }
    final oldIds = <String>[];
    try {
      final raw = prefs.getString(_profilesKey);
      final saved = raw == null ? null : jsonDecode(raw);
      if (saved is List) {
        for (final value in saved) {
          if (value is Map && value['id'] is String) {
            oldIds.add(value['id'] as String);
          }
        }
      }
    } catch (_) {}
    await appPreferences.readStateConsent.migrateExistingProfiles(oldIds);
    if (profile.transientTransport) {
      throw const ByoHostFailure(ByoHostFailureCode.storage);
    }
    // Register before persistence: a failing keyring may echo its input.
    KitRedact.registerKnownSecret(profile.password);
    KitRedact.registerKnownSecret(profile.codexToken);
    KitRedact.registerKnownSecret(profile.teamEngineAuth);
    final previousRaw = prefs.getString(_profilesKey);
    final next = List<ServerProfile>.of(_cache);
    final i = _cache.indexWhere((p) => p.id == profile.id);
    if (i >= 0) {
      next[i] = profile;
    } else {
      next.add(profile);
    }
    if (!await prefs.setString(_profilesKey, _encode(next))) {
      throw StateError('Could not save the server profile');
    }
    // The same id added again after a finished removal (the built-in
    // server's fixed id): a new server, readable again. The removed visit
    // stays retired; only this new one may read.
    if (i < 0 && appPreferences.readStateConsent.isClosed(profile.id)) {
      appPreferences.readStateConsent.reopenProfile(profile.id);
    }
    var teamEngineAuth = profile.teamEngineAuth;
    try {
      if (teamEngineAuth.isEmpty) {
        // Editors reconstruct profiles without the engine's private token.
        // An ordinary metadata save must preserve the separate Keystore key.
        teamEngineAuth =
            await secure.read(key: '$teamEngineAuthKey${profile.id}') ?? '';
        KitRedact.registerKnownSecret(teamEngineAuth);
      } else {
        await secure.write(
          key: '$teamEngineAuthKey${profile.id}',
          value: teamEngineAuth,
        );
      }
      if (profile.usesAgentSocket) {
        if (profile.codexToken.isEmpty) {
          await secure.delete(key: '$_codexTokenKey${profile.id}');
        } else {
          await secure.write(
            key: '$_codexTokenKey${profile.id}',
            value: profile.codexToken,
          );
        }
      } else if (profile.password.isEmpty) {
        await secure.delete(key: '$_passwordKey${profile.id}');
      } else {
        await secure.write(
          key: '$_passwordKey${profile.id}',
          value: profile.password,
        );
      }
    } catch (error) {
      await _restoreProfiles(previousRaw);
      if (_isKeyringFailure(error)) {
        throw SecureStorageUnavailable.forPlatform(
          platformCapabilities.platform,
          cause: error,
        );
      }
      rethrow;
    }
    await _syncCleartextConfirmation(profile);
    await _syncCertificatePin(profile);
    profile.requiresPasswordReentry = false;
    profile.requiresCodexTokenReentry = false;
    profile.teamEngineAuth = teamEngineAuth;
    _cache = next;
    await _migratePhoneAgentOwners();
    _changes.changed();
  }

  /// Explicit credential removal; a normal profile edit leaves it untouched.
  Future<void> clearTeamEngineAuth(String profileId) async {
    await secure.delete(key: '$teamEngineAuthKey$profileId');
    for (final profile in _cache) {
      if (profile.id == profileId) profile.teamEngineAuth = '';
    }
    _changes.changed();
  }

  /// Records (or clears) the cleartext confirmation that goes with a saved
  /// profile. A confirmation only stands for the exact origin it was given
  /// for; a profile moved to another address, or to HTTPS, loses it. A
  /// profile that carries none keeps what is stored, so an editor that
  /// rebuilds the profile cannot erase a confirmation by accident.
  Future<void> _syncCleartextConfirmation(ServerProfile profile) async {
    final key = '$cleartextConfirmedKeyPrefix${profile.id}';
    final origin = cleartextOriginOf(profile.baseUrl);
    final needs =
        !profile.usesAgentSocket &&
        serverUrlNeedsCleartextConfirmation(profile.baseUrl);
    final wanted = profile.cleartextConfirmedOrigin ?? prefs.getString(key);
    if (!needs || wanted != origin) {
      if (prefs.containsKey(key) && !await prefs.remove(key)) {
        throw StateError('Could not save the server profile');
      }
      profile.cleartextConfirmedOrigin = null;
      return;
    }
    if (prefs.getString(key) != wanted &&
        !await prefs.setString(key, wanted!)) {
      throw StateError('Could not save the server profile');
    }
    profile.cleartextConfirmedOrigin = wanted;
  }

  ({String origin, String sha256})? _storedCertificatePin(String profileId) {
    final raw = prefs.getString('$certificatePinKeyPrefix$profileId');
    if (raw == null) return null;
    try {
      final value = jsonDecode(raw);
      if (value is! Map ||
          value['origin'] is! String ||
          value['sha256'] is! String) {
        return null;
      }
      final origin = value['origin'] as String;
      final sha256 = value['sha256'] as String;
      if (!origin.startsWith('https://') ||
          cleartextOriginOf(origin) != origin ||
          !RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(sha256)) {
        return null;
      }
      return (origin: origin, sha256: sha256.toLowerCase());
    } on FormatException {
      return null;
    }
  }

  void _restoreCertificatePin(ServerProfile profile) {
    final stored = _storedCertificatePin(profile.id);
    if (!profile.usesAgentSocket &&
        stored != null &&
        stored.origin == cleartextOriginOf(profile.baseUrl)) {
      profile.certificatePinOrigin = stored.origin;
      profile.certificatePinSha256 = stored.sha256;
    } else {
      profile.certificatePinOrigin = null;
      profile.certificatePinSha256 = null;
    }
  }

  /// Rebuilt profiles retain their pin only at the exact approved HTTPS
  /// origin. Moving a profile never grants the new address the old trust.
  Future<void> _syncCertificatePin(ServerProfile profile) async {
    final key = '$certificatePinKeyPrefix${profile.id}';
    final stored = _storedCertificatePin(profile.id);
    final origin = cleartextOriginOf(profile.baseUrl);
    final wantedOrigin = profile.certificatePinOrigin ?? stored?.origin;
    final wantedPin = profile.certificatePinSha256 ?? stored?.sha256;
    if (profile.usesAgentSocket ||
        origin == null ||
        !origin.startsWith('https://') ||
        wantedOrigin != origin ||
        wantedPin == null) {
      if (prefs.containsKey(key) && !await prefs.remove(key)) {
        throw StateError('Could not save the server profile');
      }
      profile.certificatePinOrigin = null;
      profile.certificatePinSha256 = null;
      return;
    }
    final pin = wantedPin.toLowerCase();
    final encoded = jsonEncode({'origin': origin, 'sha256': pin});
    if (prefs.getString(key) != encoded &&
        !await prefs.setString(key, encoded)) {
      throw StateError('Could not save the server profile');
    }
    profile.certificatePinOrigin = origin;
    profile.certificatePinSha256 = pin;
  }

  /// flutter_secure_storage reports a missing or locked keyring as a
  /// [PlatformException]; on a platform without the plugin at all the call
  /// surfaces as [MissingPluginException].
  static bool _isKeyringFailure(Object error) =>
      error is PlatformException || error is MissingPluginException;

  static const _probeKey = 'oc.secure.probe';

  /// Null when the keyring answers, otherwise the sentence to show before the
  /// user types a password that could not be kept. Only Linux desktops lack
  /// a keyring in practice; elsewhere the probe is skipped.
  Future<String?> secureStorageProblem() async {
    final platform = platformCapabilities.platform;
    if (platform != TargetPlatform.linux) return null;
    try {
      await secure.read(key: _probeKey);
      return null;
    } catch (error) {
      if (_isKeyringFailure(error)) {
        return SecureStorageUnavailable.messageFor(platform);
      }
      return null;
    }
  }

  /// Every preference key this app scopes to [profileId].
  ///
  /// Profile-scoped keys are namespaced as `oc.<what>.<profileId>` — model,
  /// explicit-model flag, agent, variant, and location — or carry the id as an
  /// interior segment, as the provider-runtime migration flag does
  /// (`oc.providerRuntimeRefresh.v1.<profileId>.<location>`). Matching the
  /// shape rather than a fixed list means a key added later is deleted with
  /// the profile even if nobody remembers to update this method; the app-wide
  /// keys (`oc.profiles`, `oc.activeProfile`, `oc.offlineQueue`,
  /// `oc.sessionDrafts`, `oc.widgetSessions`, appearance, theme) carry no id
  /// segment and are never matched.
  Set<String> profileScopedPreferenceKeys(String profileId) {
    if (profileId.isEmpty) return const {};
    final suffix = '.$profileId';
    final infix = '.$profileId.';
    return {
      for (final key in prefs.getKeys())
        if (key.startsWith('oc.') &&
            (key.endsWith(suffix) || key.contains(infix)))
          key,
    };
  }

  /// Removes every preference scoped to [profileId], reporting the keys the
  /// store refused to drop.
  ///
  /// `SharedPreferences.remove` answers with a bool that the old deletion
  /// path threw away, so a full disk or a broken store left a profile's
  /// model, agent, and location on the device while the user was told the
  /// server had been removed. The caller decides what to do about a
  /// non-empty result; this method only refuses to lie about it.
  Future<Set<String>> removeScopedPreferences(
    String profileId, {
    Set<String> excluding = const {},
  }) async {
    if (profileId.isEmpty) return const {};
    await appPreferences.readStateConsent.closeProfile(profileId);
    await _sessionAgentWrites[prefs]?[profileId];
    _sessionAgentValues[prefs]?.remove(profileId);
    await _selectionWrites[prefs];
    _selectionValues[prefs]?.removeWhere(
      (key, _) => key.endsWith('.$profileId'),
    );
    // This method already runs inside the controller's deletion transaction.
    // Drain before key discovery so a late platform write cannot resurrect data.
    final defaultsDrain = InteractionDefaultsStore.closeProfile(
      prefs,
      profileId,
    );
    final auditDrain = SetupAuditStore.closeProfile(prefs, profileId);
    final commandsDrain = PendingCommandJournal.closeProfile(prefs, profileId);
    await Future.wait([
      commandsDrain,
      defaultsDrain,
      auditDrain,
      SessionLinkBindings.closeProfile(prefs, profileId),
      ContextUsageMemory.closeProfile(prefs, profileId),
      PhraseChipStore.closeProfile(prefs, profileId),
    ]);
    final failed = <String>{};
    try {
      await AgentHandoffLinks.removeProfile(prefs, profileId);
    } catch (_) {
      failed.add(AgentHandoffLinks.keyFor(profileId));
    }
    for (final key in profileScopedPreferenceKeys(profileId)) {
      if (excluding.contains(key)) continue;
      try {
        if (!await prefs.remove(key)) failed.add(key);
      } catch (_) {
        failed.add(key);
      }
    }
    return failed;
  }

  /// Removes the profile, its active-profile pointer, its Keystore secret,
  /// and every preference key scoped to it.
  ///
  /// Profile metadata and the password stay transactional: if the Keystore
  /// delete fails, the saved profiles come back exactly as they were. The
  /// scoped preference sweep runs only once that succeeded, at which point
  /// the keys are orphaned regardless, so a failure there cannot resurrect a
  /// deleted server. [ConnectionController.deleteProfileAndLocalData] sweeps
  /// them *before* calling this and verifies the result, so on that path the
  /// sweep below finds only owners intentionally retained until this commit.
  ///
  /// This clears only what [ProfileStore] owns. Queued prompts, drafts, and
  /// the home-screen widget snapshot live in shared blobs; the full cascade
  /// is [ConnectionController.deleteProfileAndLocalData].
  Future<void> remove(String id) async {
    await appPreferences.readStateConsent.closeProfile(id);
    // Cross-profile navigation references must be gone while profile removal
    // can still be aborted. The controller normally did this in its sweep.
    try {
      await AgentHandoffLinks.removeProfile(prefs, id);
    } catch (_) {
      await AgentHandoffLinks.cancelDeletion(prefs, id);
      rethrow;
    }
    final agentOwner = phoneAgentOwnerId(id);
    final retainAgents = phoneAgentOwnerRetainedAfterRemoving(id);
    final retainedAgentKeys = retainedPhoneAgentPreferenceKeys(id);
    final previousRaw = prefs.getString(_profilesKey);
    final previousActive = prefs.getString(_activeKey);
    final next = _cache.where((profile) => profile.id != id).toList();
    if (!await prefs.setString(_profilesKey, _encode(next))) {
      await AgentHandoffLinks.cancelDeletion(prefs, id);
      throw StateError('Could not remove the server profile');
    }
    ServerProfile? removedProfile;
    for (final profile in _cache) {
      if (profile.id == id) {
        removedProfile = profile;
        break;
      }
    }
    final secretKey = (removedProfile?.usesAgentSocket ?? false)
        ? '$_codexTokenKey$id'
        : '$_passwordKey$id';
    try {
      if (previousActive == id) await setActiveId(null);
      // Secure slots are not covered by the scoped preference sweep. Delete
      // BYO identity first; refusal keeps its metadata available for retry.
      // Only a server adopted over SSH has that slot; other servers keep the
      // deletion order they always had. A storage failure keeps its own type
      // (the outer catch maps keyring failures) instead of being renamed.
      final byoKey = '$byoHostSecretsKeyPrefix$id';
      // The phone's SSH identity lives in Android's keystore; deleting it is
      // safe when there is none, and also clears orphan aliases. Its own
      // failure is a BYO host failure.
      try {
        if (byoHostSafeId(id)) await byoHostSigner?.deleteIdentity(id);
      } catch (_) {
        throw const ByoHostFailure(ByoHostFailureCode.storage);
      }
      if (await secure.read(key: byoKey) != null) {
        await secure.delete(key: byoKey);
        if (await secure.read(key: byoKey) != null) {
          throw const ByoHostFailure(ByoHostFailureCode.storage);
        }
      }
      final agentKey = '$phoneAgentHostSecretPrefix$agentOwner';
      final agentSecret = await secure.read(key: agentKey);
      if (!retainAgents &&
          (agentSecret != null ||
              prefs.containsKey('$phoneAgentInstallPrefix$agentOwner') ||
              prefs.containsKey('$phoneAgentGatePrefix$agentOwner'))) {
        await agentHostCleanup?.call(agentOwner);
      }
      if (!retainAgents && agentSecret != null) {
        await secure.delete(key: agentKey);
        if (await secure.read(key: agentKey) != null) {
          throw const AgentHostException(AgentHostFailure.storage);
        }
      }
      await secure.delete(key: secretKey);
      await secure.delete(key: '$teamEngineAuthKey$id');
      // FactsBook's token, owner key and unsent notes are secure slots too;
      // its pairing record is a scoped preference swept below.
      for (final key in FactsBookStore.secureKeys(id)) {
        await secure.delete(key: key);
      }
      await secure.delete(key: '$quotaTokenKeyPrefix$id');
      await secure.delete(key: '$githubTokenKeyPrefix$id');
    } catch (error) {
      await _restoreProfiles(previousRaw);
      await AgentHandoffLinks.cancelDeletion(prefs, id);
      if (previousActive == id &&
          !await prefs.setString(_activeKey, previousActive!)) {
        throw StateError('Could not restore the active server profile');
      }
      if (_isKeyringFailure(error)) {
        throw SecureStorageUnavailable.forPlatform(
          platformCapabilities.platform,
          cause: error,
        );
      }
      rethrow;
    }
    _cache = next;
    _changes.changed();
    await removeScopedPreferences(id, excluding: retainedAgentKeys);
    if (!retainAgents && agentOwner != id) {
      for (final key in _phoneAgentPreferenceKeys(agentOwner)) {
        if (!await prefs.remove(key)) {
          throw const AgentHostException(AgentHostFailure.storage);
        }
      }
    }
  }

  String? get activeId => prefs.getString(_activeKey);

  Future<void> setActiveId(String? id) async {
    if (id == null) {
      if (!await prefs.remove(_activeKey)) {
        throw StateError('Could not clear the active server profile');
      }
    } else {
      if (!await prefs.setString(_activeKey, id)) {
        throw StateError('Could not save the active server profile');
      }
    }
  }

  ServerProfile? get active {
    final id = activeId;
    if (id == null) return null;
    for (final p in _cache) {
      if (p.id == id) return p;
    }
    return null;
  }

  ProfileLocation? locationFor(String profileId) {
    final raw = prefs.getString('$_locationKey$profileId');
    if (raw == null) return null;
    try {
      final value = jsonDecode(raw);
      if (value is! Map) return null;
      final directoryValue = value['directory'];
      final workspaceValue = value['workspace'];
      final directory = directoryValue is String && directoryValue.isNotEmpty
          ? directoryValue
          : null;
      final workspace = workspaceValue is String && workspaceValue.isNotEmpty
          ? workspaceValue
          : null;
      if (directory == null && workspace == null) return null;
      return ProfileLocation(directory: directory, workspace: workspace);
    } catch (_) {
      return null;
    }
  }

  Future<void> setLocation(
    String profileId, {
    String? directory,
    String? workspace,
  }) async {
    final normalizedDirectory = directory?.isNotEmpty == true
        ? directory
        : null;
    final normalizedWorkspace = workspace?.isNotEmpty == true
        ? workspace
        : null;
    if (normalizedDirectory == null && normalizedWorkspace == null) {
      await clearLocation(profileId);
      return;
    }
    if (!await prefs.setString(
      '$_locationKey$profileId',
      jsonEncode({
        'directory': normalizedDirectory,
        'workspace': normalizedWorkspace,
      }),
    )) {
      throw StateError('Could not save the selected server location');
    }
    await _rememberLocation(
      profileId,
      ProfileLocation(
        directory: normalizedDirectory,
        workspace: normalizedWorkspace,
      ),
    );
  }

  /// The projects used on this server, most recent first. The selected one
  /// is among them. The app had no memory of projects before this: going
  /// back to yesterday's meant finding it in the server's list again.
  List<ProfileLocation> recentLocations(String profileId) {
    final raw = prefs.getString('$_recentLocationsKey$profileId');
    if (raw == null) return const [];
    try {
      final value = jsonDecode(raw);
      if (value is! List) return const [];
      return [
        for (final item in value)
          if (item is Map && item['directory'] is String)
            ProfileLocation(
              directory: item['directory'] as String,
              workspace: item['workspace'] is String
                  ? item['workspace'] as String
                  : null,
            ),
      ];
    } catch (_) {
      return const [];
    }
  }

  Future<void> _writeRecentLocations(
    String profileId,
    List<ProfileLocation> locations,
  ) async {
    // Convenience data: a refused write must not fail the project switch.
    try {
      await prefs.setString(
        '$_recentLocationsKey$profileId',
        jsonEncode([
          for (final location in locations.take(maxRecentLocations))
            {'directory': location.directory, 'workspace': location.workspace},
        ]),
      );
    } catch (_) {}
  }

  Future<void> _rememberLocation(
    String profileId,
    ProfileLocation location,
  ) async {
    if (location.directory == null) return;
    await _writeRecentLocations(profileId, [
      location,
      for (final other in recentLocations(profileId))
        if (other.directory != location.directory) other,
    ]);
  }

  /// Drops a project from the recent list (it is not deleted anywhere).
  Future<void> forgetRecentLocation(String profileId, String directory) =>
      _writeRecentLocations(profileId, [
        for (final other in recentLocations(profileId))
          if (other.directory != directory) other,
      ]);

  Future<void> clearLocation(String profileId) async {
    if (!await prefs.remove('$_locationKey$profileId')) {
      throw StateError('Could not clear the selected server location');
    }
  }

  String _providerRuntimeRefreshKey(
    String profileId, {
    String? directory,
    String? workspace,
  }) {
    final location = Uri.encodeComponent(
      '${directory ?? '<default>'}\n${workspace ?? '<default>'}',
    );
    return 'oc.providerRuntimeRefresh.$_providerRuntimeRefreshVersion.$profileId.$location';
  }

  bool providerRuntimeWasRefreshed(
    String profileId, {
    String? directory,
    String? workspace,
  }) =>
      prefs.getBool(
        _providerRuntimeRefreshKey(
          profileId,
          directory: directory,
          workspace: workspace,
        ),
      ) ??
      false;

  Future<void> markProviderRuntimeRefreshed(
    String profileId, {
    String? directory,
    String? workspace,
  }) async {
    if (!await prefs.setBool(
      _providerRuntimeRefreshKey(
        profileId,
        directory: directory,
        workspace: workspace,
      ),
      true,
    )) {
      throw StateError('Could not save the provider runtime migration');
    }
  }

  String _providerRuntimeUnloadableKey(
    String profileId, {
    String? directory,
    String? workspace,
  }) {
    final location = Uri.encodeComponent(
      '${directory ?? '<default>'}\n${workspace ?? '<default>'}',
    );
    return 'oc.providerRuntimeUnloadable.$profileId.$location';
  }

  /// Providers that stayed unloaded after a provider runtime refresh at this
  /// location, as a sorted comma-joined id list. A later start that finds the
  /// same set unloaded knows another refresh cannot load them.
  String? providerRuntimeUnloadable(
    String profileId, {
    String? directory,
    String? workspace,
  }) => prefs.getString(
    _providerRuntimeUnloadableKey(
      profileId,
      directory: directory,
      workspace: workspace,
    ),
  );

  Future<void> setProviderRuntimeUnloadable(
    String profileId,
    String? providers, {
    String? directory,
    String? workspace,
  }) async {
    final key = _providerRuntimeUnloadableKey(
      profileId,
      directory: directory,
      workspace: workspace,
    );
    if (providers == null || providers.isEmpty) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, providers);
    }
  }

  // ----- per-profile model/agent selection -----

  ModelLibrary modelLibraryFor(String profileId) {
    final raw = prefs.getString('$_modelLibraryKey$profileId');
    if (raw == null) return const ModelLibrary();
    try {
      return ModelLibrary.fromJson(jsonDecode(raw));
    } on FormatException {
      return const ModelLibrary();
    }
  }

  Future<void> setModelLibrary(String profileId, ModelLibrary library) async {
    final key = '$_modelLibraryKey$profileId';
    final saved = library.favorites.isEmpty && library.recent.isEmpty
        ? await prefs.remove(key)
        : await prefs.setString(key, jsonEncode(library.toJson()));
    if (!saved) throw StateError('Could not save model shortcuts');
  }

  static final _selectionValues = Expando<Map<String, Object?>>();
  static final _selectionWrites = Expando<Future<void>>();

  /// Acknowledged local selection metadata for the read-only Settings facade.
  Object? confirmedSelectionPreference(String key) {
    if (![
      _modelKey,
      _agentKey,
      _variantKey,
      _modelExplicitKey,
    ].any(key.startsWith)) {
      throw const SettingsFailure(SettingsFailureReason.invalidValue);
    }
    return _selectionValue(key);
  }

  Object? _selectionValue(String key) =>
      (_selectionValues[prefs] ??= {}).putIfAbsent(key, () => prefs.get(key));

  Future<void> _writeSelection(Map<String, Object?> values) {
    // Snapshot before the plugin can mutate its optimistic cache.
    for (final key in values.keys) {
      _selectionValue(key);
    }
    final ids = values.keys
        .map((key) => key.substring(key.indexOf('.', 3) + 1))
        .toSet();
    bool closed() => ids.any(appPreferences.readStateConsent.isClosed);
    if (closed()) {
      return Future.error(
        const SettingsFailure(SettingsFailureReason.staleTarget),
      );
    }
    final result = (_selectionWrites[prefs] ?? Future<void>.value()).then((
      _,
    ) async {
      if (closed()) {
        throw const SettingsFailure(SettingsFailureReason.staleTarget);
      }
      final before = {for (final key in values.keys) key: _selectionValue(key)};
      final attempted = <String>[];
      Future<void> put(String key, Object? value) async {
        final accepted = value == null
            ? await prefs.remove(key)
            : value is bool
            ? await prefs.setBool(key, value)
            : await prefs.setString(key, value as String);
        if (!accepted) {
          throw const SettingsFailure(SettingsFailureReason.storageFailed);
        }
      }

      try {
        for (final entry in values.entries) {
          attempted.add(entry.key);
          await put(entry.key, entry.value);
        }
        if (closed()) {
          throw const SettingsFailure(SettingsFailureReason.staleTarget);
        }
        (_selectionValues[prefs] ??= {}).addAll(values);
        _changes.changed();
      } catch (error) {
        for (final key in attempted.reversed) {
          try {
            await put(key, before[key]);
          } catch (_) {}
        }
        try {
          await prefs.reload();
          for (final key in values.keys) {
            (_selectionValues[prefs] ??= {})[key] = prefs.get(key);
          }
          _changes.changed();
        } catch (_) {}
        if (error is SettingsFailure &&
            error.reason == SettingsFailureReason.staleTarget) {
          rethrow;
        }
        throw const SettingsFailure(SettingsFailureReason.storageFailed);
      }
    });
    _selectionWrites[prefs] = result.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return result;
  }

  (String?, String?) modelFor(String profileId) {
    final v = _selectionValue('$_modelKey$profileId') as String?;
    if (v == null || !v.contains('|')) return (null, null);
    final parts = v.split('|');
    return (parts[0], parts[1]);
  }

  bool modelWasExplicitlySelected(String profileId) =>
      _selectionValue('$_modelExplicitKey$profileId') as bool? ?? false;

  Future<void> setModel(
    String profileId,
    String providerID,
    String modelID, {
    bool explicit = false,
  }) async {
    await _writeSelection({
      '$_modelKey$profileId': '$providerID|$modelID',
      '$_modelExplicitKey$profileId': explicit,
    });
  }

  Future<void> clearModel(String profileId) async {
    await _writeSelection({
      '$_modelKey$profileId': null,
      '$_modelExplicitKey$profileId': null,
      '$_variantKey$profileId': null,
    });
  }

  String variantFor(String profileId) =>
      _selectionValue('$_variantKey$profileId') as String? ?? '';

  Future<void> setVariant(String profileId, String variant) async {
    await _writeSelection({
      '$_variantKey$profileId': variant.isEmpty ? null : variant,
    });
  }

  /// Per-session model choices for [profileId]; malformed entries are
  /// dropped rather than surfaced.
  Map<String, SessionModelChoice> sessionModelsFor(String profileId) {
    final raw = prefs.getString('$_sessionModelsKey$profileId');
    if (raw == null || raw.isEmpty) return {};
    final Object? decoded;
    try {
      decoded = jsonDecode(raw);
    } on FormatException {
      return {};
    }
    if (decoded is! Map) return {};
    final result = <String, SessionModelChoice>{};
    for (final entry in decoded.entries) {
      final id = entry.key.toString();
      final value = entry.value;
      if (id.isEmpty || value is! String) continue;
      final parts = value.split('|');
      if (parts.length < 2 || parts[0].isEmpty || parts[1].isEmpty) continue;
      result[id] = SessionModelChoice(
        model: ModelRef(providerID: parts[0], modelID: parts[1]).normalized,
        variant: parts.length > 2 ? parts[2] : '',
      );
    }
    return result;
  }

  /// Replaces the per-session model choices for [profileId]. Keeps the most
  /// recently written [_sessionModelsCap] entries so a long-lived profile
  /// cannot grow the preference without bound.
  Future<void> setSessionModels(
    String profileId,
    Map<String, SessionModelChoice> choices,
  ) async {
    if (choices.isEmpty) {
      await prefs.remove('$_sessionModelsKey$profileId');
      return;
    }
    final entries = choices.entries.toList();
    final kept = entries.length > _sessionModelsCap
        ? entries.sublist(entries.length - _sessionModelsCap)
        : entries;
    final encoded = <String, String>{
      for (final e in kept)
        e.key:
            '${e.value.model.providerID}|${e.value.model.modelID}|${e.value.variant}',
    };
    await prefs.setString('$_sessionModelsKey$profileId', jsonEncode(encoded));
  }

  static final _sessionAgentWrites = Expando<Map<String, Future<void>>>();
  static final _sessionAgentValues =
      Expando<Map<String, Map<String, String>>>();

  Map<String, String> sessionAgentsFor(String profileId) {
    final values = _sessionAgentValues[prefs] ??= {};
    return Map.of(
      values.putIfAbsent(profileId, () => _readSessionAgents(profileId)),
    );
  }

  Map<String, String> _readSessionAgents(String profileId) {
    try {
      final raw = prefs.getString('oc.sessionAgents.$profileId');
      final decoded = raw == null ? null : jsonDecode(raw);
      if (decoded is! Map) return {};
      return {
        for (final entry
            in decoded.entries.toList().reversed.take(200).toList().reversed)
          if (entry.key is String && entry.value is String)
            entry.key as String: entry.value as String,
      };
    } catch (_) {
      return {};
    }
  }

  Future<void> _changeSessionAgents(
    String profileId,
    Map<String, String> Function(Map<String, String>) update,
  ) {
    final queue = _sessionAgentWrites[prefs] ??= {};
    final result = (queue[profileId] ?? Future<void>.value()).then((_) async {
      if (appPreferences.readStateConsent.isClosed(profileId)) {
        throw const SettingsFailure(SettingsFailureReason.staleTarget);
      }
      final choices = update(sessionAgentsFor(profileId));
      final entries = choices.entries.toList();
      final kept = entries.skip(
        entries.length > 200 ? entries.length - 200 : 0,
      );
      final encoded = {for (final e in kept) e.key: e.value};
      try {
        final key = 'oc.sessionAgents.$profileId';
        final saved = encoded.isEmpty
            ? await prefs.remove(key)
            : await prefs.setString(key, jsonEncode(encoded));
        if (!saved) {
          throw const SettingsFailure(SettingsFailureReason.storageFailed);
        }
        if (appPreferences.readStateConsent.isClosed(profileId)) {
          throw const SettingsFailure(SettingsFailureReason.staleTarget);
        }
        (_sessionAgentValues[prefs] ??= {})[profileId] = encoded;
      } catch (error) {
        try {
          await prefs.reload();
        } catch (_) {}
        if (error is SettingsFailure &&
            error.reason == SettingsFailureReason.staleTarget) {
          rethrow;
        }
        throw const SettingsFailure(SettingsFailureReason.storageFailed);
      }
    });
    queue[profileId] = result.then<void>(
      (_) {},
      onError: (Object _, StackTrace _) {},
    );
    return result;
  }

  Future<void> setSessionAgents(String profileId, Map<String, String> choices) {
    final snapshot = Map<String, String>.of(choices);
    return _changeSessionAgents(profileId, (_) => snapshot);
  }

  Future<void> setSessionAgent(
    String profileId,
    String sessionId,
    String? agent,
  ) => _changeSessionAgents(profileId, (choices) {
    choices.remove(sessionId);
    if (agent != null) choices[sessionId] = agent;
    return choices;
  });

  String agentFor(String profileId) =>
      _selectionValue('$_agentKey$profileId') as String? ?? '';

  Future<void> setAgent(String profileId, String agent) =>
      _writeSelection({'$_agentKey$profileId': agent});

  // One shared owner publishes acknowledged device preferences to every controller.
  bool get transcriptReasoningExpanded =>
      appPreferences.value.reasoningExpanded;
  bool get transcriptTimestampsVisible =>
      appPreferences.value.timestampsVisible;
  Future<void> setTranscriptReasoningExpanded(bool value) =>
      appPreferences.setReasoningExpanded(value);
  Future<void> setTranscriptTimestampsVisible(bool value) =>
      appPreferences.setTimestampsVisible(value);
  AppAppearance get appearance => appPreferences.value.appearance;
  ThemePackId get themePack => appPreferences.value.themePack;
  KitEffects get effects => appPreferences.value.effects;
  KitTextSize get textSize => appPreferences.value.textSize;
  String? get appearanceAccent => appPreferences.value.accent;
  Future<void> setAppearanceAccent(String? value) =>
      appPreferences.setAccent(value);
  Future<void> setTextSize(KitTextSize value) =>
      appPreferences.setTextSize(value);
  Future<void> setAppearance(AppAppearance value) =>
      appPreferences.setAppearance(value);
  Future<void> setThemePack(ThemePackId value) =>
      appPreferences.setThemePack(value);
  Future<void> setEffects(KitEffects value) => appPreferences.setEffects(value);
}
