/// The one definition of which Paseo daemon and Claude Code versions this app
/// has been qualified against. Behavior that was once keyed to a single exact
/// version string (bounded history frames, browser launch, permission
/// handling, GenUI, certification) asks here instead.
///
/// Add a version only with runtime evidence for it. The previous daemon stays
/// listed so a phone that has not updated yet keeps working.
///
/// 0.12.0, 0.13.2 and 0.14.0 were added 2026-10-31 for computers whose daemon
/// updated itself (`daemon.update.request`) past 0.11.2: protocol schema diff
/// 0.11.2 to 0.14.0 is additive, and each ran live against the app's gateway
/// (handshake, wrong-password refusal, provider list with Claude Code 2.1.316,
/// draft session, signed-out prompt, history, delete, reconnect). Not proven:
/// a signed-in Claude turn, and 0.13.0/0.13.1. The app still installs 0.11.2;
/// docs/qa/pins-2026-10-31/README.md has the evidence and the launch-flag note.
const Set<String> paseoTestedVersions = {
  '0.9.2',
  '0.11.2',
  '0.12.0',
  '0.13.2',
  '0.14.0',
};

/// Claude Code builds the app has been qualified with. 2.1.316 is the current
/// pin (checked 2026-10-31: sha256 from the official manifest, `--version`,
/// signed-out `auth status`, and a signed-out turn through Paseo and the app's
/// gateway; see docs/qa/pins-2026-10-31/README.md). 2.1.294 (the pin for
/// Paseo 0.11.2: Sonnet 5.5 needs 2.1.284+, Haiku 5.5 needs 2.1.293+) and
/// 2.1.283 stay accepted for phones that still run an earlier pin.
const Set<String> claudeCodeTestedVersions = {'2.1.283', '2.1.294', '2.1.316'};

/// Whether [version] (the number the daemon reported) is a tested release.
bool paseoSupports(String? version) =>
    version != null && paseoTestedVersions.contains(version);

/// Whether [version] is a tested Claude Code release.
bool claudeCodeSupports(String? version) =>
    version != null && claudeCodeTestedVersions.contains(version);
