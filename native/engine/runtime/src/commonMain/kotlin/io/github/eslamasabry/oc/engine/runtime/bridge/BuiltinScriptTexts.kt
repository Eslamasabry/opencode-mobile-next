package io.github.eslamasabry.oc.engine.runtime.bridge

/**
 * The shared shell texts the built-in Linux scripts are made of, byte for byte the Flutter constants they port
 * (generated from lib/termux/opencode_ubuntu_setup.dart, lib/domain/opencode_history_copy.dart,
 * lib/domain/phone_agent_context.dart and `BuiltinLinux._fastPrerequisites` with the local-agents pins).
 * A desktop test pins each by SHA-256, so they only ever change on purpose.
 */
public object BuiltinScriptTexts {
    /** The in-Ubuntu half of installing OpenCode (`openCodeUbuntuSetupScript`): bash, fed on stdin. */
    public const val UBUNTU_SETUP: String =
        "set -Eeuo pipefail\n" +
        "export DEBIAN_FRONTEND=noninteractive\n" +
        "# Keep Node filesystem calls visible to PRoot's path translation.\n" +
        "export UV_USE_IO_URING=0\n" +
        "if ! command -v node >/dev/null 2>&1 ||\n" +
        "   ! command -v npm >/dev/null 2>&1 ||\n" +
        "   ! command -v curl >/dev/null 2>&1 ||\n" +
        "   ! command -v git >/dev/null 2>&1 ||\n" +
        "   ! command -v ssh >/dev/null 2>&1 ||\n" +
        "   [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then\n" +
        "  apt-get update -y -o Acquire::Retries=5\n" +
        "  # Skip optional distro tooling, but retain Git and SSH explicitly for coding\n" +
        "  # projects: these must not depend on npm/git's recommended-package defaults.\n" +
        "  apt-get install -y --no-install-recommends -o Acquire::Retries=5 \\\n" +
        "    nodejs npm curl ca-certificates git openssh-client\n" +
        "fi\n" +
        "export NODE_OPTIONS=\"\${NODE_OPTIONS:+\$NODE_OPTIONS }--dns-result-order=ipv4first\"\n" +
        "# Project folders live here; the server starts in it instead of /root.\n" +
        "mkdir -p /root/projects\n" +
        "# Only OpenCode 2 is installable. Legacy binaries/data are left untouched.\n" +
        "OC_RUNTIME=\${OC_RUNTIME:-opencode2}\n" +
        "[ \"\$OC_RUNTIME\" = opencode2 ] || { echo '[oc] OpenCode 1 is no longer supported' >&2; exit 64; }\n" +
        "command=opencode2\n" +
        "install_opencode() {\n" +
        "  local npm_cache\n" +
        "  local install_code\n" +
        "  local binary_package\n" +
        "  local binary_suffix\n" +
        "  local main_package=@opencode/cli\n" +
        "  case \"\$(node -p 'process.arch')\" in\n" +
        "    arm64) binary_suffix=linux-arm64 ;;\n" +
        "    x64) binary_suffix=linux-x64-baseline ;;\n" +
        "    *) printf '[oc] ERROR: OpenCode requires a 64-bit ARM or x64 Ubuntu environment\\n' >&2; return 64 ;;\n" +
        "  esac\n" +
        "  # Keep the new runtime separate from the original database and binary.\n" +
        "  local prefix_args=(--prefix \"\${OC2_PREFIX:-/opt/oc2}\")\n" +
        "  binary_package=\"@opencode/cli-\$binary_suffix\"\n" +
        "  npm_cache=\$(mktemp -d /tmp/opencode-mobile-npm.XXXXXX)\n" +
        "  # Make the compatible Ubuntu binary a required package. Optional dependency\n" +
        "  # failures must not silently leave postinstall trying a musl-only fallback.\n" +
        "  # Keep upstream postinstall intact and visible so runtime errors are actionable.\n" +
        "  # npm 11 only runs install scripts it was told to allow: exactly the main\n" +
        "  # package's postinstall, nothing else (older npm ignores the flag).\n" +
        "  if npm install -g \\\n" +
        "    \${prefix_args[@]+\"\${prefix_args[@]}\"} \\\n" +
        "    --include=optional \\\n" +
        "    --foreground-scripts \\\n" +
        "    --allow-scripts=\"\$main_package\" \\\n" +
        "    --cache \"\$npm_cache\" \\\n" +
        "    --fetch-retries=5 \\\n" +
        "    --fetch-retry-mintimeout=10000 \\\n" +
        "    --fetch-retry-maxtimeout=60000 \\\n" +
        "    --fetch-timeout=300000 \\\n" +
        "    \"\$binary_package@\$OC_REQUESTED_VERSION\" \\\n" +
        "    \"\$main_package@\$OC_REQUESTED_VERSION\"; then\n" +
        "    install_code=0\n" +
        "    if [ \"\$OC_RUNTIME\" = opencode2 ]; then\n" +
        "      # The overrides exist for the script tests only.\n" +
        "      ln -sfn \"\${OC2_PREFIX:-/opt/oc2}/bin/opencode2\" \\\n" +
        "        \"\${OC2_LINK_DIR:-\$(npm prefix -g)/bin}/opencode2\" || install_code=\$?\n" +
        "    fi\n" +
        "  else\n" +
        "    install_code=\$?\n" +
        "  fi\n" +
        "  rm -rf -- \"\$npm_cache\"\n" +
        "  return \"\$install_code\"\n" +
        "}\n" +
        "install_opencode || {\n" +
        "  printf '[oc] OpenCode installation failed; retrying in 10 seconds\\n'\n" +
        "  sleep 10\n" +
        "  install_opencode\n" +
        "}\n" +
        "\"\$command\" --version\n"

    /** POSIX sh copying OpenCode 1 chats into OpenCode 2's data folder (`openCode1HistoryCopyScript`). */
    public const val HISTORY_COPY: String =
        "\n" +
        "history_source_signature() (\n" +
        "  cd \"\$1\" || exit 1\n" +
        "  for history_file in opencode.db opencode.db-wal opencode.db-shm; do\n" +
        "    [ ! -L \"\$history_file\" ] || exit 1\n" +
        "    if [ -e \"\$history_file\" ]; then\n" +
        "      [ -f \"\$history_file\" ] || exit 1\n" +
        "      sha256sum \"\$history_file\" || exit 1\n" +
        "    else\n" +
        "      printf 'absent %s\\n' \"\$history_file\"\n" +
        "    fi\n" +
        "  done\n" +
        ")\n" +
        "history_copy_receipt_matches() {\n" +
        "  [ -f \"\$oc2_dir/.oc1-import-source.sha256\" ] &&\n" +
        "  [ ! -L \"\$oc2_dir/.oc1-import-source.sha256\" ] || return 1\n" +
        "  history_signature=\$(history_source_signature \"\$oc1_dir\") || return 1\n" +
        "  [ \"\$(cat \"\$oc2_dir/.oc1-import-source.sha256\")\" = \"\$history_signature\" ]\n" +
        "}\n" +
        "\n" +
        "oc1_dir=\"\${HOME:-/root}/.local/share/opencode\"\n" +
        "oc2_dir=/root/.oc-opencode2/data/opencode\n" +
        "if [ ! -e \"\$oc2_dir/opencode.db\" ] && [ -f \"\$oc1_dir/opencode.db\" ]; then\n" +
        "  history_copy_failed() {\n" +
        "    echo '[oc-history-copy-failed] Your earlier conversations could not be copied. The original data is still safe on this phone. Free some space and try again.' >&2\n" +
        "    exit 74\n" +
        "  }\n" +
        "  [ ! -L \"\$oc1_dir\" ] && [ ! -L \"\$oc2_dir\" ] || history_copy_failed\n" +
        "  original_signature=\$(history_source_signature \"\$oc1_dir\") || history_copy_failed\n" +
        "  # Only our own verified interrupted copy may leave target sidecars.\n" +
        "  if [ -e \"\$oc2_dir/opencode.db-wal\" ] || [ -e \"\$oc2_dir/opencode.db-shm\" ]; then\n" +
        "    history_copy_receipt_matches || history_copy_failed\n" +
        "    for history_file in opencode.db-wal opencode.db-shm; do\n" +
        "      if [ -e \"\$oc2_dir/\$history_file\" ]; then\n" +
        "        [ ! -L \"\$oc2_dir/\$history_file\" ] &&\n" +
        "        cmp -s \"\$oc1_dir/\$history_file\" \"\$oc2_dir/\$history_file\" || history_copy_failed\n" +
        "      fi\n" +
        "    done\n" +
        "    rm -f \"\$oc2_dir/opencode.db-wal\" \"\$oc2_dir/opencode.db-shm\" || history_copy_failed\n" +
        "  fi\n" +
        "  mkdir -p \"\$oc2_dir\" || history_copy_failed\n" +
        "  stage=\"\$oc2_dir/.oc1-copy.\$\$\"\n" +
        "  # Do not reuse an interrupted staging folder or follow a stale symlink.\n" +
        "  mkdir \"\$stage\" || history_copy_failed\n" +
        "  if ! { cp \"\$oc1_dir/opencode.db\" \"\$stage/opencode.db\" \\\n" +
        "    && { [ ! -f \"\$oc1_dir/opencode.db-wal\" ] || cp \"\$oc1_dir/opencode.db-wal\" \"\$stage/opencode.db-wal\"; } \\\n" +
        "    && { [ ! -f \"\$oc1_dir/opencode.db-shm\" ] || cp \"\$oc1_dir/opencode.db-shm\" \"\$stage/opencode.db-shm\"; }; }; then\n" +
        "    rm -rf \"\$stage\"\n" +
        "    history_copy_failed\n" +
        "  fi\n" +
        "  # Commit provenance before the database. A crash after the database rename\n" +
        "  # can then safely retry admission, including after OC2 has converted it.\n" +
        "  copied_signature=\$(history_source_signature \"\$stage\") || history_copy_failed\n" +
        "  [ \"\$copied_signature\" = \"\$original_signature\" ] || history_copy_failed\n" +
        "  [ \"\$(history_source_signature \"\$oc1_dir\")\" = \"\$original_signature\" ] || history_copy_failed\n" +
        "  printf '%s\\n' \"\$copied_signature\" > \"\$stage/.oc1-import-source.sha256\" || history_copy_failed\n" +
        "  mv \"\$stage/.oc1-import-source.sha256\" \"\$oc2_dir/.oc1-import-source.sha256\" || history_copy_failed\n" +
        "  if ! { { [ ! -f \"\$stage/opencode.db-wal\" ] || mv \"\$stage/opencode.db-wal\" \"\$oc2_dir/opencode.db-wal\"; } \\\n" +
        "    && { [ ! -f \"\$stage/opencode.db-shm\" ] || mv \"\$stage/opencode.db-shm\" \"\$oc2_dir/opencode.db-shm\"; } \\\n" +
        "    && mv \"\$stage/opencode.db\" \"\$oc2_dir/opencode.db\"; }; then\n" +
        "    rm -rf \"\$stage\"\n" +
        "    history_copy_failed\n" +
        "  fi\n" +
        "  rm -rf \"\$stage\"\n" +
        "fi\n"

    /** POSIX sh writing the phone context block into both AGENTS.md files (`PhoneAgentContext.builtinScript`). */
    public const val PHONE_CONTEXT: String =
        "oc_ctx_body() {\n" +
        "cat <<'OC_CTX_BODY'\n" +
        "<!-- opencode-mobile:phone-context:begin -->\n" +
        "## Where you are running\n" +
        "\n" +
        "Written by OpenCode Mobile; the text between these markers is replaced on each server start.\n" +
        "\n" +
        "- You run on the person's Android phone, inside the OpenCode Mobile app's built-in Ubuntu. The person talks to you through the OpenCode Mobile app on this same phone. There is no desktop app, no display, no window you can open, and no adb connection to this phone from here.\n" +
        "- Projects live in /root/projects. The person sees your replies, changes and files in the app.\n" +
        "- A server you start on 127.0.0.1:PORT opens in the phone's own browser at http://127.0.0.1:PORT; give the person that link to try a web app. Bind servers to 127.0.0.1 only, never to every network interface, unless the person asks for more.\n" +
        "- There is no visible browser here. Headless browsers (Playwright and similar) work but are slow and use a lot of memory: prefer tests, curl and logs, and close any browser you start as soon as you are done.\n" +
        "- The phone's memory and CPU are limited and shared with the person's apps. Android stops background processes when an app runs more than about 32 of them, and can close this whole environment when memory runs low. Avoid many parallel processes, file watchers and leftover servers; stop what you start once it is not needed.\n" +
        "- Long builds (Gradle, the Android SDK, large installs) are slow here and may be stopped by Android: say so before starting one.\n" +
        "<!-- opencode-mobile:phone-context:end -->\n" +
        "OC_CTX_BODY\n" +
        "}\n" +
        "oc_ctx() {\n" +
        "  f=\"\${OC_CTX_ROOT:-}\$1\"\n" +
        "  mkdir -p \"\$(dirname \"\$f\")\"\n" +
        "  tmp=\"\$f.oc-tmp.\$\$\"\n" +
        "  if [ -f \"\$f\" ]; then\n" +
        "    awk -v b='<!-- opencode-mobile:phone-context:begin -->' -v e='<!-- opencode-mobile:phone-context:end -->' '\n" +
        "      \$0 == b { skip = 1; next }\n" +
        "      \$0 == e { skip = 0; next }\n" +
        "      skip { next }\n" +
        "      \$0 == \"\" { if (seen) blank++; next }\n" +
        "      { while (blank > 0) { print \"\"; blank-- } print; seen = 1 }\n" +
        "    ' \"\$f\" > \"\$tmp\"\n" +
        "  else\n" +
        "    : > \"\$tmp\"\n" +
        "  fi\n" +
        "  if [ -s \"\$tmp\" ]; then printf '\\n' >> \"\$tmp\"; fi\n" +
        "  oc_ctx_body >> \"\$tmp\"\n" +
        "  mv \"\$tmp\" \"\$f\"\n" +
        "}\n" +
        "oc_ctx /root/.config/opencode/AGENTS.md\n" +
        "oc_ctx /root/.oc-opencode2/config/opencode/AGENTS.md\n"

    /** Node from its pinned official download and the few packages OpenCode needs (`_fastPrerequisites`). */
    public const val FAST_PREREQUISITES: String =
        "export DEBIAN_FRONTEND=noninteractive\n" +
        "if ! command -v curl >/dev/null 2>&1 || ! command -v git >/dev/null 2>&1 ||\n" +
        "   ! command -v ssh >/dev/null 2>&1 || [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then\n" +
        "  echo '[oc] Installing Git, SSH and certificates'\n" +
        "  apt-get update -y -o Acquire::Retries=5 >/dev/null\n" +
        "  apt-get install -y --no-install-recommends -o Acquire::Retries=5 \\\n" +
        "    curl ca-certificates git openssh-client >/dev/null\n" +
        "fi\n" +
        "if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then\n" +
        "  case \"\$(uname -m)\" in\n" +
        "    aarch64|arm64) node_arch=arm64; node_sha=724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5 ;;\n" +
        "    x86_64|amd64) node_arch=x64; node_sha=6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff ;;\n" +
        "    *) echo \"[oc] Unsupported CPU: \$(uname -m)\" >&2; exit 64 ;;\n" +
        "  esac\n" +
        "  node_version=v24.21.0\n" +
        "  echo \"[oc] Installing Node.js \$node_version\"\n" +
        "  curl -fsSL --retry 5 -o /tmp/node.tar.gz \\\n" +
        "    \"https://nodejs.org/dist/\$node_version/node-\$node_version-linux-\$node_arch.tar.gz\"\n" +
        "  echo \"\$node_sha  /tmp/node.tar.gz\" | sha256sum -c --quiet -\n" +
        "  rm -rf /opt/node && mkdir -p /opt/node\n" +
        "  tar -xzf /tmp/node.tar.gz -C /opt/node --strip-components=1\n" +
        "  rm -f /tmp/node.tar.gz\n" +
        "  for tool in node npm npx; do ln -sf /opt/node/bin/\$tool /usr/local/bin/\$tool; done\n" +
        "  npm config set prefix /usr/local\n" +
        "fi\n"
}
