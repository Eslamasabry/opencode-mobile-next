# Changelog

This project is in public alpha. Only the newest preview is supported.

## 1.0.34+35 - Unreleased

- Save model favorites and eight recent models per server profile. Cycle
  models from the chat's switch menu or with F2 / Shift+F2 on a keyboard.
- Simplify model selection into one searchable list with All, Favorites, and
  Recent tabs. Move detailed options out of the list and keep Apply reachable
  with the keyboard open or large accessibility text.
- Show the active chat's model and reasoning mode consistently in the picker,
  preserve model choices across workspace changes, and offer a clear-filters
  action when model searches have no results.
- Put downloads, setup requirements, and connection troubleshooting at the
  front of the README.
- Keep server symbol paths readable on Windows clients and make the
  localization check recognize Windows paths.
- Keep model and reasoning-mode choices scoped to the conversation where they
  were selected; other active and new sessions retain their own/default model.
- Update the generated OpenCode SDK to upstream `f12e14cf`, including support
  for `ProviderConfig.options.chunkTimeout: false`.
- Give CI branch APKs one stable non-production signer so they update one
  another; public-release signing remains separate.
- Establish a recoverable permanent public signer. Moving from `1.0.33+34`
  requires one final uninstall because that release's private key was lost.
- Show the installed version, package ID, and Android signing certificate in
  About.
- Require device authentication for notification-based tool approval on
  Android 12 and newer.
- Rename Linux package/runtime/launcher paths to `opencode-mobile` and refuse
  to replace or remove paths the installer cannot prove it owns.
- Enable private vulnerability reporting, Discussions, dependency alerts, and
  public repository metadata.
- Correct privacy, compatibility, release-signing, desktop, and support docs.

## 1.0.33+34 - 2026-09-02

- Added provider presentation and live Android background status.
- Added OpenCode 1 and OpenCode 2 beta support, QR pairing, activity inbox,
  phone-sized diff review, local voice transcription, and Termux hosting.
- Published the first public-alpha Android APK.

The original Linux assets for this version were withdrawn because their
installer could collide with the OpenCode server command. The Android APK is
still available from the release page.
