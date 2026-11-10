/// The one definition of which Paseo daemon and Claude Code versions this app
/// has been qualified against. Behavior that was once keyed to a single exact
/// version string (bounded history frames, browser launch, permission
/// handling, GenUI, certification) asks here instead.
///
/// Add a version only with runtime evidence for it. The previous daemon stays
/// listed so a phone that has not updated yet keeps working.
const Set<String> paseoTestedVersions = {'0.9.2', '0.11.2'};

/// Claude Code builds the app has been qualified with. 2.1.319 is the pin for
/// Paseo 0.11.2 (Sonnet 5.5 needs 2.1.284+, Haiku 5.5 needs 2.1.293+);
/// 2.1.283 and 2.1.294 stay accepted for phones that still run an earlier pin.
const Set<String> claudeCodeTestedVersions = {'2.1.283', '2.1.294', '2.1.319'};

/// Whether [version] (the number the daemon reported) is a tested release.
bool paseoSupports(String? version) =>
    version != null && paseoTestedVersions.contains(version);

/// Whether [version] is a tested Claude Code release.
bool claudeCodeSupports(String? version) =>
    version != null && claudeCodeTestedVersions.contains(version);
