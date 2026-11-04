package io.github.eslamasabry.oc.engine.runtime.install

/**
 * Port of scripts/host/ubuntu-opencode.sh (the Ubuntu/Linux host install script; Flutter's
 * host-setup docs and lib/termux/bridge.dart's pinned version point at it). [TEXT] is the file byte
 * for byte, so the native app can hand out or run the very same script. It installs one pinned
 * OpenCode release, checks its SHA-256 before anything is unpacked, and runs it as a per-user
 * systemd service bound to loopback.
 *
 * [installDecision] and [updateDecision] are the same rules as data, for callers that explain a
 * run's result.
 */
public object UbuntuHostScript {
    public const val PINNED_VERSION: String = "2.2.5"
    public const val RELEASES: String = "https://registry.npmjs.org/@opencode"
    public const val X64_ASSET: String = "cli-linux-x64-baseline-2.2.5.tgz"
    public const val X64_SHA256: String =
        "873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08"
    public const val ARM64_ASSET: String = "cli-linux-arm64-2.2.5.tgz"
    public const val ARM64_SHA256: String =
        "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053"
    public const val SERVICE_NAME: String = "opencode-serve"

    /** Where the script installs the program, relative to `$HOME`. */
    public const val BINARY_IN_HOME: String = ".opencode/bin/opencode"

    /** The script's subcommands (`bash ubuntu-opencode.sh <command>`). */
    public enum class Command(public val wireName: String) {
        Install("install"),
        Start("start"),
        Stop("stop"),
        Restart("restart"),
        Status("status"),
        Logs("logs"),
        Update("update"),
        Password("password"),
    }

    /**
     * A complete copyable command: writes this build's bundled installer, then runs only the chosen
     * verb.
     */
    public fun copyCommand(command: Command, port: Int = 4096): String {
        require(port in 1..65535) { "Invalid port." }
        return "cat > ubuntu-opencode.sh <<'OC_NATIVE_UBUNTU_SCRIPT'\n" +
            TEXT +
            "\nOC_NATIVE_UBUNTU_SCRIPT\nOPENCODE_PORT=$port bash ubuntu-opencode.sh ${command.wireName}"
    }

    /** `uname -m` → the pinned archive, or null when OpenCode has no Linux build for that CPU. */
    public fun assetFor(machine: String): String? =
        when (machine.trim()) {
            "x86_64",
            "amd64" -> X64_ASSET
            "aarch64",
            "arm64" -> ARM64_ASSET
            else -> null
        }

    /** The pinned SHA-256 of [asset]. */
    public fun sha256For(asset: String): String? =
        when (asset) {
            X64_ASSET -> X64_SHA256
            ARM64_ASSET -> ARM64_SHA256
            else -> null
        }

    /** The script's `$OPENCODE_RELEASES/${asset%-$OPENCODE_VERSION.tgz}/-/$asset`. */
    public fun downloadUrl(asset: String): String =
        "$RELEASES/${asset.removeSuffix("-$PINNED_VERSION.tgz")}/-/$asset"

    /** What `install` does with an `opencode` that is (or is not) already on the machine. */
    public enum class InstallDecision {
        /** None found: download, check, install the pinned release. */
        InstallPinned,

        /** An OpenCode 2 is already there: left alone, the service runs it. */
        KeepExisting,

        /**
         * An unsupported (OpenCode 1) server is there: refused, nothing changed, nothing
         * downloaded.
         */
        RefuseUnsupported,
    }

    /**
     * `install_opencode_if_missing`: [existingVersion] is what the found program reports (null when
     * none is found).
     */
    public fun installDecision(existingVersion: String?): InstallDecision =
        when {
            existingVersion == null -> InstallDecision.InstallPinned
            existingVersion.startsWith("2.") -> InstallDecision.KeepExisting
            else -> InstallDecision.RefuseUnsupported
        }

    /** What `update` does with the installed program. */
    public enum class UpdateDecision {
        /** Not installed: refused (run install first). */
        NotInstalled,

        /** An old OpenCode 1 is never migrated by a service update; it stays unchanged. */
        RefuseLegacy,

        /** The installed program reports no version: refused, nothing changed. */
        Unidentified,

        /**
         * Already at or past the pin: never downgraded (a newer OpenCode may have moved its data
         * forward).
         */
        AlreadyCurrent,

        /** An older OpenCode 2: moved to the pin (and a checksum mismatch keeps the old one). */
        MoveToPinned,
    }

    /** `cmd_update`'s order: legacy, then unknown, then `version_at_least` (sort -V), else move. */
    public fun updateDecision(installedVersion: String?, found: Boolean = true): UpdateDecision =
        when {
            !found -> UpdateDecision.NotInstalled
            installedVersion != null &&
                installedVersion.startsWith("1.") &&
                PINNED_VERSION.startsWith("2.") -> UpdateDecision.RefuseLegacy
            installedVersion.isNullOrEmpty() -> UpdateDecision.Unidentified
            versionAtLeast(installedVersion, PINNED_VERSION) -> UpdateDecision.AlreadyCurrent
            else -> UpdateDecision.MoveToPinned
        }

    /**
     * `version_at_least`: true when [version] is [minimum] or newer, compared like GNU `sort -V` on
     * dotted numbers.
     */
    public fun versionAtLeast(version: String, minimum: String): Boolean =
        compareVersions(version, minimum) >= 0

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.', '-')
        val right = b.split('.', '-')
        for (i in 0 until maxOf(left.size, right.size)) {
            val x = left.getOrNull(i) ?: return -1
            val y = right.getOrNull(i) ?: return 1
            val xn = x.toLongOrNull()
            val yn = y.toLongOrNull()
            val c = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    /** scripts/host/ubuntu-opencode.sh, verbatim. */
    public const val TEXT: String =
        """#!/usr/bin/env bash
# ubuntu-opencode.sh — install and manage an OpenCode server on Ubuntu/Linux.
#
# Runs OpenCode as a per-user systemd service so it survives terminal exits
# and reboots without root. Safe to re-run: every subcommand is idempotent.
#
#   bash ubuntu-opencode.sh install    # install/refresh OpenCode + the service
#   bash ubuntu-opencode.sh start      # start the service
#   bash ubuntu-opencode.sh stop       # stop the service
#   bash ubuntu-opencode.sh restart    # restart the service
#   bash ubuntu-opencode.sh status     # service state and listening check
#   bash ubuntu-opencode.sh logs       # follow the server log
#   bash ubuntu-opencode.sh update     # move to the pinned OpenCode, restart
#   bash ubuntu-opencode.sh password   # print the server password for the app
#
# Configuration (environment variables, all optional):
#   OPENCODE_PORT      port to listen on            (default 4096)
#   OPENCODE_HOSTNAME  address to bind              (default 127.0.0.1)
#
# The server binds loopback only. An OpenCode server runs shell commands with
# your account's permissions, so exposing it to a network — even a home or
# office LAN — hands anyone on that network your shell. To reach it from a
# phone, forward the port over something authenticated and encrypted:
# 'adb reverse tcp:4096 tcp:4096' over USB, an SSH tunnel, or a Tailscale or
# WireGuard address. A password is generated on install and required on every
# request; it lives in a 0600 environment file, never on the command line.
#
# OpenCode itself comes from a pinned npm archive, never from a
# script piped into a shell: the archive for this CPU is downloaded, checked
# against the SHA-256 recorded below, and only then unpacked and installed
# to ~/.opencode/bin. A download that does not match stops before anything
# runs. Moving to a newer OpenCode means a new version of this script with a
# new version and new checksums.
#
# Full walkthrough: docs/ubuntu-host.md in https://github.com/Eslamasabry/opencode-mobile-next
set -euo pipefail

readonly SERVICE_NAME="opencode-serve"
readonly PORT="${'$'}{OPENCODE_PORT:-4096}"
readonly BIND_HOST="${'$'}{OPENCODE_HOSTNAME:-127.0.0.1}"
readonly ENV_FILE="${'$'}{XDG_CONFIG_HOME:-${'$'}HOME/.config}/${'$'}SERVICE_NAME.env"
readonly UNIT_DIR="${'$'}{XDG_CONFIG_HOME:-${'$'}HOME/.config}/systemd/user"
readonly UNIT_FILE="${'$'}UNIT_DIR/${'$'}SERVICE_NAME.service"

# OpenCode 2 is the only supported server. Reviewed npm tarball hashes.
readonly OPENCODE_VERSION="2.2.5"
readonly OPENCODE_RELEASES="https://registry.npmjs.org/@opencode"
readonly OPENCODE_X64_ASSET="cli-linux-x64-baseline-2.2.5.tgz"
readonly OPENCODE_X64_SHA256="873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08"
readonly OPENCODE_ARM64_ASSET="cli-linux-arm64-2.2.5.tgz"
readonly OPENCODE_ARM64_SHA256="f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053"
readonly OPENCODE_MEMBER="package/bin/opencode"
readonly OPENCODE_BIN_DIR="${'$'}HOME/.opencode/bin"

WORK_DIR=""
cleanup_work_dir() {
  if [[ -n "${'$'}WORK_DIR" ]]; then
    rm -rf -- "${'$'}WORK_DIR"
  fi
}
trap cleanup_work_dir EXIT

fail() {
  echo "ERROR: ${'$'}*" >&2
  exit 1
}

usage() {
  sed -n '2,16p' "${'$'}0" | sed 's/^# \{0,1\}//'
}

require_systemd_user() {
  command -v systemctl >/dev/null 2>&1 ||
    fail "systemd is required. On WSL or containers, run 'opencode serve' directly instead."
  systemctl --user show-environment >/dev/null 2>&1 ||
    fail "The systemd user manager is not reachable. Log in as this user (not via 'su') and retry."
}

find_opencode() {
  local candidate
  for candidate in \
    "${'$'}(command -v opencode 2>/dev/null || true)" \
    "${'$'}HOME/.opencode/bin/opencode" \
    "${'$'}HOME/.local/bin/opencode" \
    "${'$'}HOME/.bun/bin/opencode"; do
    if [[ -n "${'$'}candidate" && -x "${'$'}candidate" ]]; then
      echo "${'$'}candidate"
      return 0
    fi
  done
  return 1
}

# The version an opencode binary reports, without a leading 'v'.
opencode_version() {
  "${'$'}1" --version 2>/dev/null | head -n 1 | tr -d '[:space:]' | sed 's/^v//'
}

# True when version ${'$'}1 is ${'$'}2 or newer.
version_at_least() {
  [[ "${'$'}(printf '%s\n%s\n' "${'$'}2" "${'$'}1" | sort -V | head -n 1)" == "${'$'}2" ]]
}

# Download the pinned release for this CPU, check its SHA-256, and only
# then unpack it into ${'$'}OPENCODE_BIN_DIR. Prints nothing on stdout.
install_pinned_opencode() {
  local asset expected actual tool
  case "${'$'}(uname -m)" in
    x86_64 | amd64)
      asset="${'$'}OPENCODE_X64_ASSET"
      expected="${'$'}OPENCODE_X64_SHA256"
      ;;
    aarch64 | arm64)
      asset="${'$'}OPENCODE_ARM64_ASSET"
      expected="${'$'}OPENCODE_ARM64_SHA256"
      ;;
    *)
      fail "OpenCode ${'$'}OPENCODE_VERSION has no Linux build for this CPU (${'$'}(uname -m)). Use a 64-bit x86 or ARM machine."
      ;;
  esac
  for tool in curl tar sha256sum; do
    command -v "${'$'}tool" >/dev/null 2>&1 ||
      fail "'${'$'}tool' is needed to install OpenCode. Install it (sudo apt install curl tar coreutils) and re-run this script."
  done

  local url="${'$'}OPENCODE_RELEASES/${'$'}{asset%-${'$'}OPENCODE_VERSION.tgz}/-/${'$'}asset"
  WORK_DIR="${'$'}(mktemp -d)"
  echo "==> Downloading OpenCode ${'$'}OPENCODE_VERSION (${'$'}asset)" >&2
  curl -fsSL --retry 3 --connect-timeout 20 -o "${'$'}WORK_DIR/${'$'}asset" "${'$'}url" ||
    fail "Could not download OpenCode ${'$'}OPENCODE_VERSION from ${'$'}url. Check the network and re-run this script."

  actual="${'$'}(sha256sum "${'$'}WORK_DIR/${'$'}asset" | cut -d ' ' -f 1)"
  if [[ "${'$'}actual" != "${'$'}expected" ]]; then
    fail "The OpenCode download does not match its published checksum, so nothing was installed.
  file:     ${'$'}asset
  expected: ${'$'}expected
  got:      ${'$'}actual
Re-run this script; if it fails again, do not install this file."
  fi
  echo "==> Checksum matches (${'$'}expected)" >&2

  tar -xzf "${'$'}WORK_DIR/${'$'}asset" -C "${'$'}WORK_DIR" "${'$'}OPENCODE_MEMBER" ||
    fail "Could not unpack the OpenCode archive. Re-run this script."
  [[ -f "${'$'}WORK_DIR/${'$'}OPENCODE_MEMBER" && ! -L "${'$'}WORK_DIR/${'$'}OPENCODE_MEMBER" ]] ||
    fail "The OpenCode archive did not contain the opencode program. Nothing was installed."
  mkdir -p "${'$'}OPENCODE_BIN_DIR"
  # Rename over the old copy so a running server keeps its file until restart.
  install -m 0755 "${'$'}WORK_DIR/${'$'}OPENCODE_MEMBER" "${'$'}OPENCODE_BIN_DIR/opencode.new"
  mv -f "${'$'}OPENCODE_BIN_DIR/opencode.new" "${'$'}OPENCODE_BIN_DIR/opencode"
  cleanup_work_dir
  WORK_DIR=""

  local installed
  installed="${'$'}(opencode_version "${'$'}OPENCODE_BIN_DIR/opencode")"
  [[ "${'$'}installed" == "${'$'}OPENCODE_VERSION" ]] ||
    fail "OpenCode was installed but reports version '${'$'}{installed:-unknown}' instead of ${'$'}OPENCODE_VERSION."
  echo "==> Installed OpenCode ${'$'}OPENCODE_VERSION to ${'$'}OPENCODE_BIN_DIR/opencode" >&2
}

install_opencode_if_missing() {
  if find_opencode >/dev/null; then
    [[ "${'$'}(opencode_version "${'$'}(find_opencode)")" == 2.* ]] ||
      fail "This server runs OpenCode 1, which this app no longer supports. Update it to OpenCode 2 before using this setup script. Your existing server data is unchanged."
    echo "==> OpenCode 2 is already installed: ${'$'}(find_opencode)"
    return
  fi
  install_pinned_opencode
  find_opencode >/dev/null ||
    fail "OpenCode was installed but 'opencode' was not found. Open a new shell and re-run this script."
}

validate_bind() {
  [[ "${'$'}PORT" =~ ^[0-9]+${'$'} ]] && ((PORT >= 1 && PORT <= 65535)) ||
    fail "OPENCODE_PORT must be a number between 1 and 65535 (got '${'$'}PORT')."
  [[ "${'$'}BIND_HOST" =~ ^[A-Za-z0-9.:_-]+${'$'} ]] ||
    fail "OPENCODE_HOSTNAME contains characters that are not a valid address."
  case "${'$'}BIND_HOST" in
    127.0.0.1|localhost|::1) ;;
    *)
      cat >&2 <<WARN
WARNING: binding ${'$'}BIND_HOST exposes this server beyond loopback.
An OpenCode server runs shell commands as ${'$'}USER, so anyone who can reach
this address and learns the password can run code on this machine. Plain
HTTP also sends that password in clear text. Prefer loopback plus an SSH
tunnel, adb reverse, or Tailscale/WireGuard.
WARN
      [[ "${'$'}{OPENCODE_ALLOW_REMOTE_BIND:-}" == "1" ]] ||
        fail "Refusing to bind ${'$'}BIND_HOST. Re-run with OPENCODE_ALLOW_REMOTE_BIND=1 if you accept the risk."
      ;;
  esac
}

ensure_password() {
  if [[ -s "${'$'}ENV_FILE" ]] && grep -q '^OPENCODE_SERVER_PASSWORD=.\+${'$'}' "${'$'}ENV_FILE"; then
    return
  fi
  local generated
  generated="${'$'}(head -c 32 /dev/urandom | base64 | tr -d '=+/[:space:]' | cut -c1-32)"
  [[ -n "${'$'}generated" ]] || fail "Could not generate a server password."
  mkdir -p "${'$'}(dirname "${'$'}ENV_FILE")"
  local previous
  previous="${'$'}(umask)"
  umask 077
  printf 'OPENCODE_SERVER_PASSWORD=%s\n' "${'$'}generated" >"${'$'}ENV_FILE"
  umask "${'$'}previous"
  chmod 600 "${'$'}ENV_FILE"
  echo "==> Generated a server password in ${'$'}ENV_FILE (0600)"
}

# write_unit [binary]: the service runs [binary], or the first opencode found.
write_unit() {
  local opencode_bin="${'$'}{1:-}"
  if [[ -z "${'$'}opencode_bin" ]]; then
    opencode_bin="${'$'}(find_opencode)" || fail "OpenCode is not installed. Run: bash ${'$'}0 install"
  fi
  validate_bind
  ensure_password
  mkdir -p "${'$'}UNIT_DIR"
  cat >"${'$'}UNIT_FILE" <<UNIT
# Generated by ubuntu-opencode.sh — safe to re-run 'install' to refresh.
[Unit]
Description=OpenCode server for phone and remote clients
After=network-online.target

[Service]
# The password is read from a 0600 file so it never appears in the unit
# text, in 'ps' output, or in the journal.
EnvironmentFile=${'$'}ENV_FILE
# OC2 prefers this alias; do not inherit a different manager-level secret.
UnsetEnvironment=OPENCODE_PASSWORD
ExecStart=${'$'}opencode_bin serve --hostname ${'$'}BIND_HOST --port ${'$'}PORT
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
UNIT
  systemctl --user daemon-reload
  echo "==> Wrote ${'$'}UNIT_FILE (bind ${'$'}BIND_HOST, port ${'$'}PORT)"
}

post_install_hints() {
  cat <<HINTS

Next steps:
  - Keep the server running after you log out:
      loginctl enable-linger "${'$'}USER"
  - The server listens on ${'$'}BIND_HOST:${'$'}PORT only. Reach it from a phone over
    an authenticated, encrypted path rather than opening a firewall port:
      USB:       adb reverse tcp:${'$'}PORT tcp:${'$'}PORT   then connect to http://127.0.0.1:${'$'}PORT
      SSH:       ssh -N -L ${'$'}PORT:127.0.0.1:${'$'}PORT ${'$'}USER@<this-machine>
      Tailscale: connect to http://<tailscale-name>:${'$'}PORT (its network is authenticated)
  - Read the server password when the app asks for it:
      bash ${'$'}0 password
HINTS
}

listening_check() {
  if command -v ss >/dev/null 2>&1 && ss -ltn 2>/dev/null | grep -q ":${'$'}PORT "; then
    echo "==> Listening on port ${'$'}PORT"
  else
    echo "==> Nothing is listening on port ${'$'}PORT yet"
  fi
}

cmd_install() {
  require_systemd_user
  install_opencode_if_missing
  write_unit
  systemctl --user enable --now "${'$'}SERVICE_NAME"
  systemctl --user --no-pager --full status "${'$'}SERVICE_NAME" || true
  listening_check
  post_install_hints
}

cmd_update() {
  require_systemd_user
  local opencode_bin
  opencode_bin="${'$'}(find_opencode)" || fail "OpenCode is not installed. Run: bash ${'$'}0 install"
  local installed
  installed="${'$'}(opencode_version "${'$'}opencode_bin")"
  # A remote legacy installation needs its own data-preserving upgrade.
  # Refuse to replace it with a different runtime through a service update.
  if [[ "${'$'}installed" == 1.* && "${'$'}OPENCODE_VERSION" == 2.* ]]; then
    fail "Older OpenCode is unchanged. Update it to OpenCode 2 before using this setup script. Your existing server data is unchanged."
  fi
  if [[ -z "${'$'}installed" ]]; then
    fail "Could not identify the installed OpenCode; nothing was changed."
  fi
  if [[ -n "${'$'}installed" ]] && version_at_least "${'$'}installed" "${'$'}OPENCODE_VERSION"; then
    # Never downgrade: a newer OpenCode may have moved its data forward.
    echo "==> OpenCode ${'$'}installed is already at or past the pinned ${'$'}OPENCODE_VERSION: ${'$'}opencode_bin"
  else
    echo "==> Moving OpenCode ${'$'}{installed:-(unknown version)} to the pinned ${'$'}OPENCODE_VERSION"
    install_pinned_opencode
    opencode_bin="${'$'}OPENCODE_BIN_DIR/opencode"
  fi
  # The binary can move to ${'$'}OPENCODE_BIN_DIR; refresh the unit before restart.
  write_unit "${'$'}opencode_bin"
  systemctl --user restart "${'$'}SERVICE_NAME"
  echo "==> Restarted ${'$'}SERVICE_NAME"
  listening_check
}

main() {
  local command="${'$'}{1:-}"
  case "${'$'}command" in
    install) cmd_install ;;
    start)
      require_systemd_user
      systemctl --user start "${'$'}SERVICE_NAME"
      listening_check
      ;;
    stop)
      require_systemd_user
      systemctl --user stop "${'$'}SERVICE_NAME"
      ;;
    restart)
      require_systemd_user
      systemctl --user restart "${'$'}SERVICE_NAME"
      listening_check
      ;;
    status)
      require_systemd_user
      systemctl --user --no-pager --full status "${'$'}SERVICE_NAME" || true
      listening_check
      ;;
    logs)
      require_systemd_user
      journalctl --user -u "${'$'}SERVICE_NAME" -f
      ;;
    update) cmd_update ;;
    password)
      [[ -s "${'$'}ENV_FILE" ]] || fail "No password yet. Run: bash ${'$'}0 install"
      sed -n 's/^OPENCODE_SERVER_PASSWORD=//p' "${'$'}ENV_FILE"
      ;;
    *)
      usage
      exit 64
      ;;
  esac
}

main "${'$'}@"
"""
}
