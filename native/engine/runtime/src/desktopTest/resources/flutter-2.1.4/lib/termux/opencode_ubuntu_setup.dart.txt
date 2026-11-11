/// The in-Ubuntu half of installing OpenCode: apt installs Node, npm, Git,
/// curl and SSH when any is missing, then `npm install -g` pins the requested
/// OpenCode release.
///
/// Two callers feed it the same text: the Termux manager runs it through
/// `proot-distro login ... bash -s`, and the built-in runtime (Ubuntu shipped
/// inside the app, no Termux) runs it through the app's own proot. One copy
/// means a fix to the npm flags or the OpenCode 2 prefix reaches both at once.
///
/// It reads two variables from its environment:
/// - `OC_REQUESTED_VERSION`: the npm version to install, never empty.
/// - `OC_RUNTIME`: must be `opencode2`; defaults to OpenCode 2.
///
/// It is fed on stdin to `bash` (it uses arrays), ends with a newline, and
/// holds no heredoc terminator of its own, so callers wrap it in
/// `<<'OC_PROOT_SETUP'`. The Termux manager's text is pinned byte for byte by
/// a test; editing this changes that script too.
const openCodeUbuntuSetupScript = r'''set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
# Keep Node filesystem calls visible to PRoot's path translation.
export UV_USE_IO_URING=0
if ! command -v node >/dev/null 2>&1 ||
   ! command -v npm >/dev/null 2>&1 ||
   ! command -v curl >/dev/null 2>&1 ||
   ! command -v git >/dev/null 2>&1 ||
   ! command -v ssh >/dev/null 2>&1 ||
   [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then
  apt-get update -y -o Acquire::Retries=5
  # Skip optional distro tooling, but retain Git and SSH explicitly for coding
  # projects: these must not depend on npm/git's recommended-package defaults.
  apt-get install -y --no-install-recommends -o Acquire::Retries=5 \
    nodejs npm curl ca-certificates git openssh-client
fi
export NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--dns-result-order=ipv4first"
# Project folders live here; the server starts in it instead of /root.
mkdir -p /root/projects
# Only OpenCode 2 is installable. Legacy binaries/data are left untouched.
OC_RUNTIME=${OC_RUNTIME:-opencode2}
[ "$OC_RUNTIME" = opencode2 ] || { echo '[oc] OpenCode 1 is no longer supported' >&2; exit 64; }
command=opencode2
install_opencode() {
  local npm_cache
  local install_code
  local binary_package
  local binary_suffix
  local main_package=@opencode/cli
  case "$(node -p 'process.arch')" in
    arm64) binary_suffix=linux-arm64 ;;
    x64) binary_suffix=linux-x64-baseline ;;
    *) printf '[oc] ERROR: OpenCode requires a 64-bit ARM or x64 Ubuntu environment\n' >&2; return 64 ;;
  esac
  # Keep the new runtime separate from the original database and binary.
  local prefix_args=(--prefix "${OC2_PREFIX:-/opt/oc2}")
  binary_package="@opencode/cli-$binary_suffix"
  npm_cache=$(mktemp -d /tmp/opencode-mobile-npm.XXXXXX)
  # Make the compatible Ubuntu binary a required package. Optional dependency
  # failures must not silently leave postinstall trying a musl-only fallback.
  # Keep upstream postinstall intact and visible so runtime errors are actionable.
  # npm 11 only runs install scripts it was told to allow: exactly the main
  # package's postinstall, nothing else (older npm ignores the flag).
  if npm install -g \
    ${prefix_args[@]+"${prefix_args[@]}"} \
    --include=optional \
    --foreground-scripts \
    --allow-scripts="$main_package" \
    --cache "$npm_cache" \
    --fetch-retries=5 \
    --fetch-retry-mintimeout=10000 \
    --fetch-retry-maxtimeout=60000 \
    --fetch-timeout=300000 \
    "$binary_package@$OC_REQUESTED_VERSION" \
    "$main_package@$OC_REQUESTED_VERSION"; then
    install_code=0
    if [ "$OC_RUNTIME" = opencode2 ]; then
      # The overrides exist for the script tests only.
      ln -sfn "${OC2_PREFIX:-/opt/oc2}/bin/opencode2" \
        "${OC2_LINK_DIR:-$(npm prefix -g)/bin}/opencode2" || install_code=$?
    fi
  else
    install_code=$?
  fi
  rm -rf -- "$npm_cache"
  return "$install_code"
}
install_opencode || {
  printf '[oc] OpenCode installation failed; retrying in 10 seconds\n'
  sleep 10
  install_opencode
}
"$command" --version
''';
