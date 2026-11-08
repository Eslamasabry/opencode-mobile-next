package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.engine.factsbook.FactsBookNodeHost
import io.github.eslamasabry.oc.engine.factsbook.NodeHostFacts
import io.github.eslamasabry.oc.engine.factsbook.NodeHostFailure
import io.github.eslamasabry.oc.engine.factsbook.NodePairing
import io.github.eslamasabry.oc.engine.factsbook.NodeStep
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.host.ShellRun
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.fbNodeRunningTitle
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * The FactsBook node inside the app's built-in Linux (F: lib/builtin/factsbook_node_host.dart, ported step for step so
 * a node the Flutter app set up keeps working): the same rootfs, binary path, data folder, port, service name and
 * passphrase key (`oc.factsbook-node.passphrase`, copied by the day-1 migration). The node build is found at
 * `<external files>/factsbook/factsbook-node-<abi>` with an optional `.sha256` beside it; there is no published
 * download yet, so none is made up. The passphrase is never logged, never in a command line, and on disk only while a
 * node command reads it.
 */
internal class AndroidFactsBookNodeHost(
    private val app: Application,
    private val linux: LinuxHost,
    private val secrets: SecretStore,
    private val dispatchers: AppDispatchers,
) : FactsBookNodeHost {
    private val random = SecureRandom()

    override val supported: Boolean = true

    override val address: String = "127.0.0.1:$PORT"

    private val rootfs: File get() = File(app.filesDir, "linux/ubuntu")

    private val passphraseFile: File get() = File(rootfs, PASSPHRASE_PATH.removePrefix("/"))

    private val arch: String get() = if (Build.SUPPORTED_ABIS.firstOrNull() == "x86_64") "x86_64" else "aarch64"

    private fun source(): File? = app.getExternalFilesDir(null)?.let { File(it, "factsbook/factsbook-node-$arch") }?.takeIf { it.isFile }

    private suspend fun run(script: String, seconds: Long): ShellRun? = (linux.run(script, seconds) as? Outcome.Ok)?.value

    private suspend fun passphrase(create: Boolean): String? {
        migratePassphrase()
        (secrets.read(SECRET_KEY) as? Outcome.Ok)?.value?.takeIf { it.isNotEmpty() }?.let { return it }
        if (!create) return null
        val fresh = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        return if (secrets.write(SECRET_KEY, fresh) is Outcome.Ok) fresh else null
    }

    /** Moves a passphrase file an early Flutter version left inside the Linux into the secret store, then deletes it. */
    private suspend fun migratePassphrase() {
        val file = passphraseFile
        val legacy = withContext(dispatchers.io) { if (file.isFile) file.readText().trim() else null } ?: return
        val stored = (secrets.read(SECRET_KEY) as? Outcome.Ok)?.value
        if (stored.isNullOrEmpty() && legacy.isNotEmpty()) secrets.write(SECRET_KEY, legacy)
        withContext(dispatchers.io) { file.delete() }
    }

    private suspend fun stage(pass: String): Boolean =
        withContext(dispatchers.io) {
            // Plain file work, no suspension inside: a write that fails leaves no half file behind.
            runCatching {
                passphraseFile.parentFile?.mkdirs()
                passphraseFile.writeText("$pass\n")
            }.onFailure { passphraseFile.delete() }.isSuccess
        }

    private suspend fun unstage() {
        withContext(dispatchers.io) { passphraseFile.delete() }
    }

    private suspend fun <T> withPassphrase(create: Boolean, body: suspend () -> T): T? {
        val pass = passphrase(create) ?: return null
        if (!stage(pass)) return null
        return try {
            body()
        } finally {
            unstage()
        }
    }

    override suspend fun inspect(): NodeStep<NodeHostFacts> {
        migratePassphrase()
        if (linux.presence() != LinuxPresence.Ready) return NodeStep.Done(NodeHostFacts())
        val result =
            run(
                "b=0; [ -x $BINARY ] && b=1; d=0; [ -f $HOME/state/enrollment.enc ] && d=1; echo \"fb b=\$b d=\$d\"",
                30,
            ) ?: return NodeStep.Failed(NodeHostFailure.Start, "The built-in Linux did not answer.")
        val match = Regex("""fb b=(\d) d=(\d)""").find(result.output)
        val installed = match?.groupValues?.get(1) == "1"
        return NodeStep.Done(
            NodeHostFacts(
                linuxReady = true,
                binaryInstalled = installed,
                dataExists = match?.groupValues?.get(2) == "1",
                serviceRunning = linux.serviceRunning(SERVICE),
                binaryAvailable = installed || withContext(dispatchers.io) { source() } != null,
            )
        )
    }

    override suspend fun installBinary(): NodeStep<Unit> {
        val bytes =
            withContext(dispatchers.io) {
                val file = source() ?: return@withContext null
                val data = file.readBytes()
                val digest = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
                val sidecar = File(file.path + ".sha256")
                val expected = if (sidecar.isFile) sidecar.readText().trim().split(Regex("\\s+")).first().lowercase() else null
                if (expected != null && expected != digest) return@withContext ByteArray(0)
                File(rootfs, BINARY.removePrefix("/")).apply {
                    parentFile?.mkdirs()
                    writeBytes(data)
                }
                data
            } ?: return NodeStep.Failed(NodeHostFailure.NoBinary)
        if (bytes.isEmpty()) return NodeStep.Failed(NodeHostFailure.BadBinary, "The node build does not match its checksum.")
        val check = run("chmod 755 $BINARY && $BINARY --help >/dev/null 2>&1; echo \"fb exit=\$?\"", 30)
        // `--help` prints usage and exits normally.
        return if (check?.output?.contains("fb exit=0") == true) NodeStep.Done(Unit)
        else NodeStep.Failed(NodeHostFailure.BadBinary, "The node build did not run.")
    }

    override suspend fun createVault(): NodeStep<Unit> {
        // Kept: a vault made with an earlier passphrase needs that one. The folders and the config are made inside the
        // Linux: the node refuses a data folder another user owns.
        val result =
            withPassphrase(create = true) {
                run(
                    "chmod 600 $PASSPHRASE_PATH && umask 077 && " +
                        "[ -f $HOME/state/enrollment.enc ] || rm -rf $HOME; mkdir -p $HOME/config && " +
                        "chmod 700 $HOME $HOME/config && " +
                        "printf '%s\\n' 'node = { listen = \"127.0.0.1:$PORT\" }' 'vault = { path = \"$HOME/vault\" }' > $HOME/config/config.toml && " +
                        "export FACTSBOOK_HOME=$HOME && if [ -f $HOME/state/enrollment.enc ]; then echo \"fb init=exists\"; " +
                        "else out=\$($BINARY init < $PASSPHRASE_PATH 2>&1); echo \"fb init=\$?\"; echo \"\$out\"; fi",
                    300,
                )
            } ?: return NodeStep.Failed(NodeHostFailure.Prepare, "The vault passphrase could not be kept.")
        val out = result.output
        // The node's own error is a code, never a secret.
        return if (out.contains("fb init=0") || out.contains("fb init=exists")) NodeStep.Done(Unit)
        else NodeStep.Failed(NodeHostFailure.Prepare, "The vault could not be created: ${out.trim().take(2000)}")
    }

    override suspend fun startNode(): NodeStep<Unit> {
        val pass = passphrase(create = false) ?: return NodeStep.Failed(NodeHostFailure.Prepare, "The vault passphrase is not in the secret store.")
        if (!stage(pass)) return NodeStep.Failed(NodeHostFailure.Start, "The passphrase could not be handed to the node.")
        // The service reads the passphrase once from stdin and removes the staged file itself.
        val started =
            linux.startService(
                SERVICE,
                "export FACTSBOOK_HOME=$HOME\nexec 3<$PASSPHRASE_PATH\nrm -f $PASSPHRASE_PATH\nexec $BINARY serve <&3\n",
                getString(Res.string.fbNodeRunningTitle),
            )
        return if (started is Outcome.Ok) NodeStep.Done(Unit)
        else {
            unstage()
            NodeStep.Failed(NodeHostFailure.Start, "The node service did not start.")
        }
    }

    override suspend fun stopNode(): NodeStep<Unit> =
        if (linux.stopService(SERVICE) is Outcome.Ok) NodeStep.Done(Unit) else NodeStep.Failed(NodeHostFailure.Start, "The node did not stop.")

    override suspend fun pairingCode(ownerKeyHex: String): NodeStep<NodePairing> {
        if (!Regex("^[0-9a-f]{64}$").matches(ownerKeyHex)) return NodeStep.Failed(NodeHostFailure.Pair, "The approval key is not hex.")
        val result =
            withPassphrase(create = false) {
                run(
                    "export FACTSBOOK_HOME=$HOME && $BINARY pair --scopes read,write,admin,owner --owner-key $ownerKeyHex < $PASSPHRASE_PATH 2>&1",
                    120,
                )
            }
        val pairing = result?.takeIf { it.exitCode == 0 }?.let { NodePairing.parse(it.output) }
        // Never put the output in the failure: it holds the code.
        return pairing?.let { NodeStep.Done(it) } ?: NodeStep.Failed(NodeHostFailure.Pair, "The node gave no pairing code (exit ${result?.exitCode}).")
    }

    override suspend fun log(): String = linux.serviceLog(SERVICE, 16384)

    override suspend fun removeData(): NodeStep<Unit> {
        linux.stopService(SERVICE)
        val result = run("rm -rf $HOME $PASSPHRASE_PATH && echo \"fb removed\"", 120)
        if (result?.output?.contains("fb removed") != true) return NodeStep.Failed(NodeHostFailure.Remove)
        secrets.delete(SECRET_KEY)
        unstage()
        return NodeStep.Done(Unit)
    }

    companion object {
        const val SECRET_KEY = "oc.factsbook-node.passphrase"
        const val PORT = 8787
        const val SERVICE = "factsbook"
        const val HOME = "/root/.factsbook"
        const val BINARY = "/usr/local/bin/factsbook-node"
        const val PASSPHRASE_PATH = "/root/.oc-builtin/factsbook.passphrase"
    }
}
