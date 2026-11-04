package io.github.eslamasabry.oc.engine.runtime.projects

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The repository checkout this test runs in (Gradle runs tests in the module folder), or null outside one. */
internal fun repoRoot(): File? =
    generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "native/android/linux").isDirectory }

/**
 * Source guards over the built-in Linux Kotlin (port of test/builtin_project_lifecycle_guard_test.dart; B-LINUX-028),
 * run against the native copies in :android:linux. Native behaviour is proven by the storage scenarios
 * (BuiltinProjectStorageTest); these guards keep a later merge from disconnecting the safe storage helper from real
 * launches, installation, removal and the runtime lock. The activity's channel dispatch (`"status" -> inBackground`)
 * is not in the native app: the binding is S3-RTWIRE's and gets its own guard there.
 */
class ProjectLifecycleGuardTest {
    private val native = repoRoot()?.let { File(it, "native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile") }

    private fun source(name: String): String? = native?.let { File(it, name) }?.takeIf { it.isFile }?.readText()

    private fun method(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue(start >= 0, "$signature must remain integrated")
        val end = source.indexOf("\n    }", start)
        assertTrue(end > start, signature)
        return source.substring(start, end)
    }

    private fun assertBefore(text: String, first: String, second: String) {
        val a = text.indexOf(first)
        val b = text.indexOf(second)
        assertTrue(a >= 0 && b >= 0 && a < b, "$first must come before $second")
    }

    @Test
    fun `B-LINUX-028 every proot command prepares and binds the persistent projects`() {
        val linux = source("BuiltinLinux.kt") ?: return
        val body = method(linux, "fun prootCommand(")
        val prepare = method(linux, "private fun prepareGuestForProot(")
        assertTrue("{\n        prepareGuestForProot()" in body.trimStart())
        assertTrue("check(!installingRuntime)" in prepare)
        assertTrue("projectStorage.prepare()" in prepare)
        assertTrue("--bind=\${projectStorage.projects.absolutePath}:/root/projects" in body)
        assertBefore(body, "prepareGuestForProot()", "return")
    }

    @Test
    fun `B-LINUX-028 installation preserves projects before replacing the system`() {
        val linux = source("BuiltinLinux.kt") ?: return
        val body = method(linux, "fun install(image: Image = imageForDevice(), progress: InstallProgress)")
        assertTrue("projectStorage.resetRootfs()" in body)
        assertTrue("rootfs.deleteRecursively()" !in body)
        assertBefore(body, "projectStorage.prepare()", "projectStorage.resetRootfs()")
        assertTrue("installingRuntime = true" in body)
        assertTrue("finally" in body)
        assertTrue("synchronized(this) { installingRuntime = false }" in body)
        assertTrue("@Synchronized\n    fun install(image:" !in linux, "long downloads must not hold the runtime monitor")
    }

    @Test
    fun `B-LINUX-028 removal independently enforces preservation and confirmation`() {
        val linux = source("BuiltinLinux.kt") ?: return
        val body = method(linux, "fun uninstall(")
        assertTrue("alsoDeleteProjects: Boolean = false" in body)
        assertTrue("require(!alsoDeleteProjects || confirmationName == \"${ProjectRemoval.DELETION_CONFIRMATION_NAME}\")" in body)
        assertTrue("!installingRuntime" in body)
        assertTrue("!SetupRunner.get(context).running" in body)
        assertTrue("projectStorage.removeRuntime(alsoDeleteProjects)" in body)
        assertTrue("terminals.remove(it.id)" in body)
        assertTrue("processes.filter { it.isAlive }" in body)
        assertBefore(body, "stopAllServices()", "projectStorage.removeRuntime")
    }

    @Test
    fun `B-LINUX-028 setup admission takes the runtime lock first`() {
        val setup = source("SetupRunner.kt") ?: return
        val body = method(setup, "fun start(jobId:")
        val runtimeLock = body.indexOf("synchronized(linux)")
        assertTrue(runtimeLock >= 0)
        assertTrue(body.indexOf("synchronized(", runtimeLock + 1) > runtimeLock)
        // The local terminal is still a shim in :android:linux; it must not lock itself first either.
        val terminal = repoRoot()
            ?.let { File(it, "native/android/linux/src/shims/kotlin/io/github/eslamasabry/opencode_mobile/LocalTerminal.kt") }
            ?.takeIf { it.isFile }?.readText()
        if (terminal != null) assertTrue("@Synchronized\n    fun start(rows:" !in terminal)
    }

    @Test
    fun `B-LINUX-028 a reinstall recreates the proc bindings instead of reusing deleted files`() {
        val linux = source("BuiltinLinux.kt") ?: return
        assertTrue("private val fakeProcBinds: List<String> get()" in linux)
        assertTrue("private val fakeProcBinds: List<String> by lazy" !in linux)
    }

    @Test
    fun `B-LINUX-028 the notification stop waits for runtime storage off the UI thread`() {
        val service = source("BuiltinServerService.kt") ?: return
        val start = service.indexOf("if (intent?.action == ACTION_STOP)")
        val end = service.indexOf("return START_NOT_STICKY", start)
        assertTrue(start >= 0 && end > start)
        assertTrue("stopRuntime(startId)" in service.substring(start, end))
        val stop = method(service, "private fun stopRuntime(")
        val workerStart = stop.indexOf("Thread({")
        val workerEnd = stop.indexOf("}, \"phone-service-policy-stop\").start()")
        assertTrue(workerStart >= 0 && workerEnd > workerStart)
        val revocation = stop.substring(0, workerStart)
        val worker = stop.substring(workerStart, workerEnd)
        assertTrue("linux.requestServerStop(reason, includeOther = true, onRevoked = { revision = it })" in revocation)
        assertBefore(revocation, "linux.requestServerStop(", "val capturedRevision = revision")
        assertTrue("linux.stopAllServices(" !in revocation)
        assertTrue("linux.drainRevokedRuntimeChildren()" !in revocation)
        assertTrue("if (capturedRevision != null) linux.stopAllServices(capturedRevision)" in worker)
        assertTrue("else linux.drainRevokedRuntimeChildren()" in worker)
        assertTrue("linux.stopAllServices()" !in worker)
        assertTrue("finally { try { stopSelf(startId) } catch (_: Throwable) { } }" in worker)
    }

    @Test
    fun `B-LINUX-017 Ubuntu's place on the phone matches the Android side`() {
        val linux = source("BuiltinLinux.kt") ?: return
        // PhoneFolderLocations mirrors these three lines.
        assertTrue("val home = File(context.filesDir, \"${PhoneFolderLocations.LINUX_HOME}\")" in linux)
        assertTrue("val rootfs = File(home, \"${PhoneFolderLocations.ROOTFS}\")" in linux)
        assertTrue("File(home, \"${PhoneFolderLocations.READY_MARK}\")" in linux)
    }
}
