package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.app.ActivityManager
import android.system.Os
import java.io.File
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real packaged daemon, real occupied TCP port, and forged child-pipe control. */
internal object PhoneEngineNativeRegressions {
    /** Stable-target subset: no daemon, service, Ubuntu or provider mutations. */
    fun runOfflineSmoke(context: Context, passed: (String) -> Unit = {}): List<String> {
        check(context.packageName == "io.github.eslamasabry.opencode_mobile")
        refusesUnauthenticatedChildBeforeHttp(context)
        passed("auth_pipe_before_http")
        propagatesOnlyStaticPrivateFailureFrames(context)
        passed("private_failure_frames")
        requiresGenuineServerPassword(context)
        passed("qa_password_file")
        acceptsOnlyAuthenticatedHealthTiers()
        passed("authenticated_health_tiers")
        trustsOnlyRegisteredPdfProcess(context)
        passed("registered_pdf_identity")
        signsBeforeRequiringProtection()
        passed("receipt_before_protection")
        stopsRemainingServicesAfterFailure()
        passed("stop_callback_order")
        return listOf("auth_pipe_before_http", "private_failure_frames", "qa_password_file",
            "authenticated_health_tiers", "registered_pdf_identity", "receipt_before_protection",
            "stop_callback_order")
    }

    fun run(context: Context) {
        check(context.packageName == "io.github.eslamasabry.opencode_mobile.preview")
        refusesUnauthenticatedChildBeforeHttp(context)
        propagatesOnlyStaticPrivateFailureFrames(context)
        requiresGenuineServerPassword(context)
        acceptsOnlyAuthenticatedHealthTiers()
        ignoresSquatterAndSurvivesLauncher(context)
        repeatsNativeProofFactoryWithoutReusingDaemon(context)
        trustsOnlyRegisteredPdfProcess(context)
        signsBeforeRequiringProtection()
        stopsRemainingServicesAfterFailure()
        erasesModeZeroWithoutFollowingLinks(context)
        erasesInactiveCredentialCopies(context)
    }

    private fun repeatsNativeProofFactoryWithoutReusingDaemon(context: Context) {
        val profile = "qa_fresh_${UUID.randomUUID().toString().replace("-", "")}"
        val native = PhoneEngineNative(context)
        var attempts = 0
        try {
            val first = native.start(profile, 4098, false, "fixture_unverified") { attempts++; null }
            val oldAuth = native.credentials(profile)["bearerToken"]
            val second = native.start(profile, 4098, false, "fixture_unverified") { attempts++; null }
            check(attempts == 2) { "The running daemon skipped the new proof factory" }
            check(first !== second && !first.isAlive && second.isAlive)
            check(native.credentials(profile)["bearerToken"] != oldAuth)
            check(native.status(profile)["boundary"] == false) // No fake proof grants authority.
            var refused = false
            try { native.start("qa_other", 4098, false, "fixture_unverified") { error("Wrong owner reached proof") } }
            catch (failure: PhoneEngineNative.Failure) { check(failure.code == "engine_in_use"); refused = true }
            check(refused && second.isAlive) { "Start displaced another profile's daemon" }
        } finally { native.stop(profile); native.delete(profile) }
    }

    /** Full proof mode is separate: requires compatible kernel and idle runtime. */
    fun reproofRunningGeneration(context: Context) {
        check(context.packageName == "io.github.eslamasabry.opencode_mobile.preview")
        val linux = BuiltinLinux.get(context)
        val profile = "qa_reproof_${UUID.randomUUID().toString().replace("-", "")}"
        check(linux.phoneEngineStatus(profile)["unconfinedChildren"] == false) {
            "Stop old preview services and terminals before the proof regression"
        }
        try {
            val first = linux.startPhoneEngine(profile, 4098, null)
            check(first["boundary"] == true) { "This device did not pass the actual boundary proof" }
            val generation = first["boundaryGeneration"] as? String
            check(!generation.isNullOrBlank())
            val second = linux.startPhoneEngine(profile, 4098, null)
            check(second["boundary"] == true && second["running"] == true)
            check(second["boundaryGeneration"] != generation) { "Running start reused a stale signed generation" }
            check(second["unconfinedChildren"] == false)
        } finally { linux.stopPhoneEngine(profile); linux.deletePhoneEngine(profile) }
    }

    private fun refusesUnauthenticatedChildBeforeHttp(context: Context) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { squatter ->
            squatter.soTimeout = 300
            val profile = "qa_pipe"
            val token = "a".repeat(64)
            val line = JSONObject().put("schemaVersion", 1).put("profileId", profile)
                .put("port", squatter.localPort).put("nonce", UUID.randomUUID().toString())
                .put("mac", "0".repeat(64)).toString() + "\n"
            var refused = false
            try {
                PhoneEngineNative(context).authenticatedStartup(PipeChild(line), token, profile)
            } catch (failure: PhoneEngineNative.Failure) {
                check(failure.code == "engine_ready_invalid")
                refused = true
            }
            check(refused) { "An unauthenticated pipe was accepted" }
            try {
                squatter.accept().use { throw IllegalStateException("HTTP opened before pipe authentication") }
            } catch (_: SocketTimeoutException) { }
        }
    }

    private fun propagatesOnlyStaticPrivateFailureFrames(context: Context) {
        val native = PhoneEngineNative(context)
        val token = "a".repeat(64)
        val profile = "qa_frame"
        for (code in listOf("server_auth_unavailable", "repositoryUnavailable", "symlinkRefused")) {
            val frame = JSONObject().put("schemaVersion", 1).put("startupError", code).toString() + "\n"
            var reported: String? = null
            try { native.authenticatedStartup(PipeChild(frame), token, profile) }
            catch (failure: PhoneEngineNative.Failure) { reported = failure.code }
            check(reported == code)
        }
        for (frame in listOf(
            "{\"schemaVersion\":1,\"startupError\":\"raw-provider-secret\"}\n",
            "{\"schemaVersion\":1,\"startupError\":\"configInvalid\",\"secret\":\"raw\"}\n",
        )) {
            var refused = false
            try { native.authenticatedStartup(PipeChild(frame), token, profile) }
            catch (failure: PhoneEngineNative.Failure) { refused = failure.code == "engine_ready_invalid" }
            check(refused)
        }
    }

    private fun requiresGenuineServerPassword(context: Context) {
        val fixture = File(context.filesDir.canonicalFile, ".qa-password-${UUID.randomUUID()}")
        val native = PhoneEngineNative(context)
        fun refused() {
            var code: String? = null
            try { native.requiredServerPassword(fixture) }
            catch (failure: PhoneEngineNative.Failure) { code = failure.code }
            check(code == "server_auth_unavailable")
        }
        try {
            refused() // A first start must not invent credentials.
            fixture.writeText("")
            refused()
            fixture.writeText("test-only-existing-server-password")
            check(native.requiredServerPassword(fixture) == "test-only-existing-server-password")
            fixture.writeText("a".repeat(4097))
            refused()
        } finally { fixture.delete() }
    }

    private fun acceptsOnlyAuthenticatedHealthTiers() {
        fun health(tier: String, capabilityTier: String = tier, boundary: Boolean = true) =
            JSONObject().put("boundaryTier", tier).put("capabilities", JSONObject()
                .put("boundary", boundary).put("boundaryTier", capabilityTier))
        check(PhoneEngineNative.healthBoundaryTier(health("landlock"), true) == "landlock")
        check(PhoneEngineNative.healthBoundaryTier(health("proot"), true) == "proot")
        check(PhoneEngineNative.healthBoundaryTier(health("proot"), false) == "none")
        check(PhoneEngineNative.healthBoundaryTier(health("proot", "landlock"), true) == "none")
        check(PhoneEngineNative.healthBoundaryTier(health("proot", boundary = false), true) == "none")
        check(PhoneEngineNative.healthBoundaryTier(health("unknown"), true) == "none")
        check(PhoneEngineNative.healthBoundaryTier(JSONObject(), true) == "none")
    }

    private fun ignoresSquatterAndSurvivesLauncher(context: Context) {
        val profile = "qa_native_${UUID.randomUUID().toString().replace("-", "")}"
        val native = PhoneEngineNative(context)
        val failure = AtomicReference<Throwable?>(null)
        val child = AtomicReference<Process?>(null)
        val connections = AtomicInteger(0)
        // Refuse if 4098 is already occupied; never displace a live listener.
        ServerSocket(4098, 4, InetAddress.getByName("127.0.0.1")).use { squatter ->
            squatter.soTimeout = 100
            val listener = Thread {
                while (!squatter.isClosed) {
                    try {
                        squatter.accept().use { socket ->
                            connections.incrementAndGet()
                            socket.soTimeout = 200
                            // The response deliberately impersonates health, but
                            // no received bytes or credentials are persisted.
                            val body = JSONObject().put("schemaVersion", 1).put("profileId", profile)
                                .put("capabilities", JSONObject().put("boundary", true).put("execution", true)).toString()
                            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body").toByteArray())
                        }
                    } catch (_: SocketTimeoutException) { }
                    catch (_: Exception) { if (!squatter.isClosed) failure.compareAndSet(null, IllegalStateException("Squatter control failed")) }
                }
            }.apply { isDaemon = true; start() }
            try {
                // Matches the MethodChannel's short-lived launcher thread.
                val launcher = Thread {
                    try { child.set(native.start(profile, 4098, false, "boundary_unverified") { null }) }
                    catch (error: Throwable) { failure.set(error) }
                }.apply { start() }
                launcher.join(25_000)
                check(!launcher.isAlive) { "Launcher did not finish" }
                failure.get()?.let { throw IllegalStateException("Native startup regression failed") }
                Thread.sleep(300)
                check(child.get()?.isAlive == true) { "Daemon died with the launcher thread" }
                val status = native.status(profile)
                check(status["running"] == true)
                check(status["port"] != 4098)
                check(status["boundary"] == false && status["execution"] == false)
                check(connections.get() == 0) { "The squatter received a connection" }
                // Pipe EOF must stop this actual daemon generation.
                child.get()!!.outputStream.close()
                check(child.get()!!.waitFor(5, TimeUnit.SECONDS)) { "Daemon survived app pipe EOF" }
            } finally {
                try { native.stop(profile) } finally {
                    native.delete(profile)
                    squatter.close()
                    listener.join(1000)
                }
            }
        }
    }

    private fun trustsOnlyRegisteredPdfProcess(context: Context) {
        val pkg = context.packageName
        val uid = android.os.Process.myUid()
        fun entry(pid: Int, name: String, owner: Int, packages: Array<String>) =
            ActivityManager.RunningAppProcessInfo(name, pid, packages).apply { this.uid = owner }
        val records = listOf(entry(101, "$pkg:local_pdf", uid, arrayOf(pkg)),
            entry(102, "$pkg:forged_worker", uid, arrayOf(pkg)),
            entry(103, "$pkg:local_pdf", uid + 1, arrayOf(pkg)),
            entry(104, "$pkg:local_pdf", uid, arrayOf("other.package")))
        check(BuiltinLinux.registeredAppProcessIds(records, pkg, uid) == setOf(101))
        check(BuiltinLinux.registeredAppProcessIds(null, pkg, uid).isEmpty())
    }

    private fun signsBeforeRequiringProtection() {
        val order = mutableListOf<String>()
        check(BuiltinLinux.commitProtectionAfterReceipt({ order.add("sign"); "receipt" }) {
            order.add("protect")
        } == "receipt")
        check(order == listOf("sign", "protect"))
        var protected = false
        try {
            BuiltinLinux.commitProtectionAfterReceipt<String>({ throw IllegalStateException("Signer refused") }) {
                protected = true
            }
            error("A failed signer returned a receipt")
        } catch (_: IllegalStateException) { }
        check(!protected)
    }

    private fun stopsRemainingServicesAfterFailure() {
        val stopped = mutableListOf<String>()
        val original = IllegalStateException("Engine stop failed")
        var reported: Exception? = null
        try {
            BuiltinLinux.stopEveryService(listOf("engine", "server", "terminal_service")) {
                stopped.add(it)
                if (it == "engine") throw original
            }
        } catch (error: Exception) { reported = error }
        check(stopped == listOf("engine", "server", "terminal_service"))
        check(reported === original)
    }

    private fun erasesModeZeroWithoutFollowingLinks(context: Context) {
        val profile = "qa_erase_${UUID.randomUUID().toString().replace("-", "")}"
        val files = context.filesDir.canonicalFile
        val worker = File(files, "linux/ubuntu/root/aiteam/work/$profile")
        val sentinel = File(files, ".qa-erase-sentinel-$profile")
        check(worker.mkdirs())
        check(sentinel.mkdir())
        val control = File(sentinel, "control").apply { writeText("private-positive-control") }
        try {
            Os.symlink(sentinel.absolutePath, File(worker, "outside-link").absolutePath)
            val modeZero = File(worker, "mode-zero").apply { mkdir() }
            File(modeZero, "data").writeText("worker-only")
            Os.chmod(modeZero.absolutePath, 0)
            val readOnly = File(worker, "read-only").apply { mkdir() }
            File(readOnly, "data").writeText("worker-only")
            Os.chmod(readOnly.absolutePath, 365) // 0555
            PhoneEngineNative(context).delete(profile)
            check(!worker.exists())
            check(control.readText() == "private-positive-control")
            check(!File(files, "oc.teamEngineDeletion.$profile").exists())
        } finally {
            // Exact fixtures only. Production helper owns any hostile worker cleanup.
            if (worker.exists()) PhoneEngineNative(context).delete(profile)
            control.delete()
            sentinel.delete()
        }
    }

    private fun erasesInactiveCredentialCopies(context: Context) {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val profiles = listOf("qa_credential_a_$suffix", "qa_credential_b_$suffix")
        val roots = profiles.map { File(context.filesDir.canonicalFile, "oc.teamEngine.$it") }
        val sentinel = File(context.filesDir.canonicalFile, ".qa-credential-control-$suffix")
        sentinel.writeText("outside-control")
        try {
            roots.forEach { check(it.mkdir()); Os.chmod(it.absolutePath, 448) }
            File(roots[0], "oc1-credentials.json").writeText("test-only-copy")
            Os.symlink(sentinel.absolutePath, File(roots[1], "oc1-credentials.json").absolutePath)
            PhoneEngineNative(context).eraseCredentialCopies(profiles)
            check(roots.none { File(it, "oc1-credentials.json").exists() })
            check(sentinel.readText() == "outside-control")
        } finally {
            profiles.forEach { PhoneEngineNative(context).delete(it) }
            sentinel.delete()
        }
    }

    private class PipeChild(line: String) : Process() {
        private val pipe = ByteArrayInputStream(line.toByteArray(Charsets.UTF_8))
        override fun getInputStream(): InputStream = pipe
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = throw IllegalThreadStateException()
        override fun destroy() { }
        override fun isAlive(): Boolean = true
    }
}
