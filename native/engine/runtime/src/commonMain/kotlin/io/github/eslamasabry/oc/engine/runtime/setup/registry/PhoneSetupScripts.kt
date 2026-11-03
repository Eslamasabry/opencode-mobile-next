package io.github.eslamasabry.oc.engine.runtime.setup.registry

import io.github.eslamasabry.oc.engine.runtime.install.AgentNode

/**
 * Port of the first-setup scripts of `SetupScripts` (lib/builtin/setup/components.dart), verbatim: the Linux base
 * check, the essentials (curl, Git, SSH), Python and the pinned Node. Checks exit 0 only when the piece is there and
 * works and print its version on their last line; installs are idempotent and report with the setup prelude's helpers
 * (`oc_stage`, `oc_download`, `oc_apt_install`, `oc_version`; components/SetupPrelude.kt). OpenCode's own scripts are
 * install/OpenCodeNativeInstall.kt; the removal-only ones are components/RemovableComponents.kt.
 */
public object PhoneSetupScripts {
    /** Flutter `termuxLocalAgentsPins` (lib/termux/scripts/local_agents_script.dart): the same pins as [AgentNode]. */
    public const val NODE_VERSION: String = AgentNode.VERSION
    public const val NODE_BASE_URL: String = "https://nodejs.org/dist"
    public const val NODE_SHA256_ARM64: String = AgentNode.ARM64_SHA256
    public const val NODE_SHA256_X64: String = AgentNode.X64_SHA256

    /** Run through proot only once Android says Ubuntu is installed; before that there is nothing to run it in. */
    public const val LINUX_CHECK: String = "[ -s /etc/os-release ] && . /etc/os-release && echo \"${'$'}{VERSION%% *}\""

    public const val ESSENTIALS_CHECK: String = """set -e
command -v curl >/dev/null
command -v git >/dev/null
command -v ssh >/dev/null
[ -s /etc/ssl/certs/ca-certificates.crt ]
git --version | cut -d ' ' -f 3
"""

    public const val ESSENTIALS_INSTALL: String = """set -eu
oc_apt_install curl ca-certificates git openssh-client
oc_version "${'$'}(git --version | cut -d ' ' -f 3)"
"""

    public const val PYTHON_CHECK: String = """set -e
python3 -c 'import ensurepip, venv' >/dev/null
python3 -m pip --version >/dev/null
python3 --version | cut -d ' ' -f 2
"""

    public const val PYTHON_INSTALL: String = """set -eu
oc_apt_install python3 python3-venv python3-pip
oc_version "${'$'}(python3 --version | cut -d ' ' -f 2)"
"""

    /**
     * Only the pinned Node counts: an older one from Ubuntu's own packages or an earlier app would leave OpenCode on a
     * Node it was not tested with.
     */
    public const val NODE_CHECK: String = """set -e
[ "${'$'}(node --version)" = '$NODE_VERSION' ]
command -v npm >/dev/null
[ "${'$'}(npm config get prefix)" = /usr/local ]
node --version | sed 's/^v//'
"""

    private val NODE_PLAIN: String = NODE_VERSION.removePrefix("v")

    /**
     * Node from its official pinned download instead of Ubuntu's `npm` package. The download sits outside /tmp so a
     * force-stopped run resumes it, and is deleted once unpacked. npm's global folder is /usr/local.
     */
    public val NODE_INSTALL: String = """set -eu
case "${'$'}(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=$NODE_SHA256_ARM64 ;;
  x86_64|amd64) node_arch=x64; node_sha=$NODE_SHA256_X64 ;;
  *) echo "[oc] Unsupported CPU: ${'$'}(uname -m)" >&2; exit 64 ;;
esac
node_version=$NODE_VERSION
node_file=/var/cache/oc-setup/node-${'$'}node_version-linux-${'$'}node_arch.tar.gz
oc_stage 'Downloading Node.js $NODE_PLAIN'
oc_download "$NODE_BASE_URL/${'$'}node_version/node-${'$'}node_version-linux-${'$'}node_arch.tar.gz" \
  "${'$'}node_file" "${'$'}node_sha"
oc_stage 'Unpacking Node.js'
rm -rf /opt/node.new
mkdir -p /opt/node.new
tar -xzf "${'$'}node_file" -C /opt/node.new --strip-components=1
rm -rf /opt/node
mv /opt/node.new /opt/node
for tool in node npm npx; do ln -sf /opt/node/bin/${'$'}tool /usr/local/bin/${'$'}tool; done
npm config set prefix /usr/local
rm -f "${'$'}node_file"
oc_version "${'$'}(node --version | sed 's/^v//')"
"""
}
