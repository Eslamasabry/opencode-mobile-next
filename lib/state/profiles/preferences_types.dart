part of '../profiles.dart';

enum AppAppearance { system, light, dark }

/// Selectable color identity; palettes live in lib/ui/theme_packs.dart.
/// Stored by name, so new packs can be added anywhere; `dynamic` stays last
/// because the theme list shows them in this order.
enum ThemePackId {
  opencode,
  catppuccin,
  gruvbox,
  solarized,
  dracula,
  nord,
  tokyoNight,
  oneDark,
  monokai,
  rosePine,
  everforest,
  kanagawa,
  ayu,
  nightOwl,
  github,
  palenight,
  synthwave,
  cobalt,
  midnight,
  paper,
  forest,
  ocean,
  sunset,
  sakura,
  lavender,
  mint,
  coffee,
  slate,
  amber,
  highContrast,
  dynamic,
}

/// What a profile deletion actually erased, so the UI can say so and tests
/// can assert it rather than inferring from side effects.
class DeleteProfileResult {
  /// Preference keys scoped to the profile (model, agent, variant, location,
  /// provider-runtime migration flags) that were removed.
  final Set<String> removedPreferenceKeys;

  /// Offline-queue entries — prompts and their embedded attachments — dropped.
  final int removedQueuedPrompts;

  /// Unsent composer drafts dropped.
  final int removedDrafts;

  /// Whether the home-screen widget's session snapshot was cleared.
  final bool clearedWidgetSnapshot;

  /// Whether the launcher's pinned-session shortcuts were withdrawn.
  final bool clearedPinnedShortcuts;

  /// Whether the Quick Settings tile's cached count was dropped.
  final bool clearedAttentionTile;

  /// Whether the server profile itself and its Keystore password are gone.
  ///
  /// False means the deletion stopped before touching them: the local data
  /// this profile owns could not be erased, so the server stays saved rather
  /// than leaving orphaned prompts and drafts behind a removed row.
  final bool removedProfile;

  /// Plain-language descriptions of what could not be deleted, in the order
  /// the deletion tried. Empty means the promise the UI makes — "this
  /// server's data leaves the device" — was actually kept.
  final List<String> failures;

  const DeleteProfileResult({
    this.removedPreferenceKeys = const {},
    this.removedQueuedPrompts = 0,
    this.removedDrafts = 0,
    this.clearedWidgetSnapshot = false,
    this.clearedPinnedShortcuts = false,
    this.clearedAttentionTile = false,
    this.removedProfile = true,
    this.failures = const [],
  });

  /// Whether every piece of local data this app knows about is gone.
  bool get complete => failures.isEmpty && removedProfile;

  /// One sentence naming what survived, for a message the user can act on.
  /// Null when the deletion was complete.
  String? get partialDeletionMessage {
    if (complete) return null;
    final kept = failures.isEmpty
        ? 'some of its local data'
        : failures.join(', ');
    return removedProfile
        ? 'The server was removed, but $kept could not be deleted from this '
              'device. Free up storage and remove it again.'
        : 'The server was kept: $kept could not be deleted from this device, '
              'and removing the server would have left that data behind. '
              'Free up storage and try again.';
  }
}

class ProfileLocation {
  final String? directory;
  final String? workspace;

  const ProfileLocation({this.directory, this.workspace});
}

/// The platform keyring refused to hold a secret.
///
/// On Linux flutter_secure_storage needs a Secret Service (GNOME Keyring,
/// KWallet) inside a desktop session; without one every write throws a
/// [PlatformException]. That is a local storage problem, not a server one,
/// so it carries its own product sentence instead of collapsing into
/// "OpenCode is unreachable".
class SecureStorageUnavailable implements Exception {
  final String message;
  final Object? cause;

  const SecureStorageUnavailable(this.message, {this.cause});

  factory SecureStorageUnavailable.forPlatform(
    TargetPlatform platform, {
    Object? cause,
  }) => SecureStorageUnavailable(messageFor(platform), cause: cause);

  static const linuxMessage =
      'Could not store the password: no keyring is available. Install GNOME '
      'Keyring or KWallet, or run the app inside a desktop session, then try '
      'again.';
  static const genericMessage =
      'Could not store the password securely on this device.';

  static String messageFor(TargetPlatform platform) =>
      platform == TargetPlatform.linux ? linuxMessage : genericMessage;

  @override
  String toString() => message;
}

/// A scoped sign-in reset was not fully confirmed. Some sign-ins may already
/// have been removed; retrying completes the same operation safely.
class SavedSignInResetException implements Exception {
  const SavedSignInResetException();

  @override
  String toString() => 'Could not reset saved sign-ins. Try again.';
}
