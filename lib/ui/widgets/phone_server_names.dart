import '../../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../../l10n/app_localizations.dart';
import '../../state/profiles.dart';
import 'termux_running_server_entry.dart' show isManagedPhoneProfile;

/// How a saved server is named wherever the app shows it: the server card,
/// the switcher, Settings and the model picker.

/// The name to show for [profile]: "This phone" for the in-app server,
/// otherwise the name it was saved under.
///
/// Setup saves one in-app profile per OpenCode generation, and both are
/// "This phone". When [among] (the saved profiles) holds the other
/// generation too, the name says which one this is ("This phone ·
/// OpenCode 2"), so two of them never sit side by side looking the same.
/// Only the shown name changes; the stored names stay as they were saved.
String serverDisplayName(
  ServerProfile? profile,
  AppLocalizations l10n, {
  Iterable<ServerProfile> among = const [],
}) {
  if (profile == null) return 'OpenCode';
  // OpenCode in Termux is named as its row on Servers and in the switcher
  // ("This phone · Termux"), not by the name setup saved it under; the
  // app's own server is plain "This phone".
  if (isManagedPhoneProfile(profile)) return l10n.phoneServerTermuxTitle;
  if (!looksLikeInAppServer(profile)) {
    return _distinctServerName(profile, among);
  }
  return phoneServerDisplayName(profile, l10n, among: among);
}

/// The plain name, made distinct when another saved server would read the
/// same ("Computer at 10.0.2.2" three times): the port is added, and when
/// the address is the same too, the server's number among them.
String _distinctServerName(
  ServerProfile profile,
  Iterable<ServerProfile> among,
) {
  final name = plainServerName(profile.name);
  final same = [
    for (final other in among)
      if (other.id == profile.id ||
          (!looksLikeInAppServer(other) &&
              !isManagedPhoneProfile(other) &&
              plainServerName(other.name) == name))
        other,
  ];
  if (same.length < 2 || !same.any((o) => o.id == profile.id)) return name;
  String portOf(ServerProfile p) {
    final uri = Uri.tryParse(p.baseUrl);
    return uri != null && uri.hasPort ? ':${uri.port}' : '';
  }

  final port = portOf(profile);
  // "Computer at 10.0.2.2:4443 (secure)": a secure address that shares its
  // host with a plain one says so, since a port alone can look arbitrary.
  final secure =
      Uri.tryParse(profile.baseUrl)?.scheme == 'https' &&
      same.any((o) => Uri.tryParse(o.baseUrl)?.scheme != 'https');
  final tag = secure ? ' (secure)' : '';
  final withPort = '${port.isEmpty ? name : '$name$port'}$tag';
  final clashes = same
      .where((o) => o.id != profile.id && portOf(o) == port)
      .toList();
  if (clashes.isEmpty) return withPort;
  final index = same
      .where((o) => portOf(o) == port)
      .toList()
      .indexWhere((o) => o.id == profile.id);
  return '$withPort (${index + 1})';
}

/// The model picker's title for the server called [name] (a
/// [serverDisplayName]). The app's own server is named "On this phone", which
/// would read "on On this phone" after "New conversations on".
String newConversationsTitle(AppLocalizations l10n, String name) =>
    name == l10n.phoneServerCardTitle
    ? l10n.settingsV3ModelNewOnPhone
    : l10n.settingsV3ModelNewOn(name);

/// The "On (server)" heading for a server page section; the phone's own name
/// already starts with "On".
String sectionOnTitle(AppLocalizations l10n, String name) =>
    name == l10n.phoneServerCardTitle
    ? l10n.phoneServerCardTitle
    : l10n.settingsV3SectionOn(name);

/// The "Saved on (server)…" note, worded for the phone's own server.
String savedOnNote(AppLocalizations l10n, String name) =>
    name == l10n.phoneServerCardTitle
    ? l10n.settingsV3SavedOnPhone
    : l10n.settingsV3SavedOn(name);

/// Confirmation after "Use my default" on the server called [name].
String useMyDefaultDoneNote(AppLocalizations l10n, String name, String what) =>
    name == l10n.phoneServerCardTitle
    ? l10n.settingsV3UseMyDefaultDonePhone(what)
    : l10n.settingsV3UseMyDefaultDone(name, what);

/// [serverDisplayName] for a profile known to be the in-app server (the
/// "This phone" card's own title).
String phoneServerDisplayName(
  ServerProfile profile,
  AppLocalizations l10n, {
  Iterable<ServerProfile> among = const [],
}) {
  return l10n.phoneServerCardTitle;
}
