package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader

/** Private generated-manifest QA component. It never initializes BuiltinLinux. */
class BuiltinInstallerProducerQaService : Service() {
    private class Refused(val code: String) : Exception()
    private data class Export(val app: RuntimeProcessIdentity, val ticketId: String,
        val command: List<String>, val environment: Map<String, String>)
    private val files = ProducerFiles()
    private val records = ProducerRecords()
    private val launcher = ProducerLaunch()
    private val lifecycle = ProducerLifecycle()
    private val proof = ProducerProof()
    private val guard = Any()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var started = false
    @Volatile private var launchStage = "identity"
    private var permitted = false
    private var permitClaimed = false
    private var finalized = false
    private var lifetimeDeadline = 0L
    private var ticketId: String? = null
    private var exported: Export? = null
    private var producer: RuntimeProcessIdentity? = null
    private var root: RuntimeProcessIdentity? = null
    private var leader: RuntimeProcessIdentity? = null
    private var process: java.lang.Process? = null
    private var outputIdentity: StructStat? = null
    private var worker: Thread? = null
    private var permitWorker: Thread? = null
    private val expiry = Runnable { lifecycle.finish("stopped", "producer_lifetime_expired") }
    private val metadata get() = File(filesDir, "bb9-producer-qa.json")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.BUILTIN_RUNTIME_QA) { stopSelf(); return START_NOT_STICKY }
        try {
            checkSafe(intent != null && intent.action in setOf(ACTION_START, ACTION_PERMIT, ACTION_STOP),
                "producer_action_invalid")
            checkSafe(intent!!.component?.className == javaClass.name && intent.data == null &&
                intent.clipData == null && intent.selector == null, "producer_action_invalid")
            when (intent.action) {
                ACTION_START -> lifecycle.startAction(intent)
                ACTION_PERMIT, ACTION_STOP -> lifecycle.controlAction(intent)
            }
        } catch (error: Refused) { lifecycle.finish("failed", error.code) }
        catch (_: Throwable) { lifecycle.finish("failed", "producer_unavailable") }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { lifecycle.finish("stopped", "producer_system_timeout") }

    override fun onDestroy() {
        lifecycle.finish("stopped", null)
        worker?.interrupt(); permitWorker?.interrupt()
        super.onDestroy()
        // This private QA process must not remain a cached metadata writer.
        // Only terminate this component's own OS-confirmed Android process.
        if (BuildConfig.BUILTIN_RUNTIME_QA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            android.app.Application.getProcessName() == packageName + ":bb9producer") {
            Process.killProcess(Process.myPid())
        }
    }

    private fun publish(state: String, error: String?) {
        val id = ticketId ?: return
        val own = producer ?: return
        checkSafe(state in setOf("waiting", "running", "failed", "stopped") &&
            (error == null || error in ERRORS), "producer_metadata_invalid")
        val existing = files.stat(metadata)
        checkSafe(if (outputIdentity == null) existing == null else files.sameFile(outputIdentity!!, existing),
            "producer_metadata_changed")
        val temporary = File(filesDir, "bb9-producer-qa.json.tmp")
        checkSafe(files.stat(temporary) == null, "producer_metadata_exists")
        val value = JSONObject().put("version", 1).put("ticketId", id).put("uid", Process.myUid())
            .put("producer", JSONObject(own.map())).put("root", root?.let { JSONObject(it.map()) } ?: JSONObject.NULL)
            .put("leader", leader?.let { JSONObject(it.map()) } ?: JSONObject.NULL).put("state", state)
        if (error != null) value.put("error", error)
        val descriptor = Os.open(temporary.absolutePath, OsConstants.O_WRONLY or OsConstants.O_CREAT or
            OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, PRIVATE_MODE)
        val temporaryIdentity = FileOutputStream(descriptor).use {
            checkSafe(OsConstants.S_ISREG(Os.fstat(descriptor).st_mode), "producer_metadata_invalid")
            it.write(value.toString().toByteArray(Charsets.UTF_8)); it.fd.sync()
            Os.fstat(descriptor)
        }
        checkSafe(files.sameFile(temporaryIdentity, files.stat(temporary)), "producer_metadata_changed")
        checkSafe(if (outputIdentity == null) files.stat(metadata) == null else files.sameFile(outputIdentity!!,
            files.stat(metadata)),
            "producer_metadata_changed")
        Os.rename(temporary.absolutePath, metadata.absolutePath)
        val directory = Os.open(filesDir.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        outputIdentity = files.stat(metadata) ?: throw Refused("producer_metadata_invalid")
    }

    private fun checkSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }

    companion object {
        private const val OUTPUT_BUFFER = 1024
        private const val OUTPUT_LIMIT = 4096
        private const val OUTPUT_POLL_MS = 25L
        private const val COMMAND_MIN = 3
        private const val COMMAND_MAX = 128
        private const val ARGUMENT_LIMIT = 32768
        private const val COMMAND_LIMIT = 65536
        private const val ANCESTRY_LIMIT = 128
        private const val HEADER_TIMEOUT_MS = 5000L
        private const val HEADER_POLL_MS = 10L
        private const val NEWLINE_BYTE = 10
        private const val ASCII_FIRST = 32
        private const val ASCII_LAST = 126
        private const val HEADER_LIMIT = 128
        private const val WRITER_LIMIT = 524288
        private const val XML_EVENT_LIMIT = 16384
        private const val XML_DEPTH_LIMIT = 4
        private const val JSON_DEPTH_LIMIT = 16
        private const val JSON_FIELD_LIMIT = 32
        private const val JSON_ARRAY_LIMIT = 128
        private const val STAT_LIMIT = 4096
        private const val STATUS_LIMIT = 16384
        private const val UID_FIELDS = 4
        private const val BOOT_LIMIT = 64
        private const val PRIVATE_MODE = 384
        private const val WORKER_JOIN_MS = 500L

        private const val ACTION_START = "oc.bb9.producer.START"
        private const val ACTION_PERMIT = "oc.bb9.producer.PERMIT"
        private const val ACTION_STOP = "oc.bb9.producer.STOP"
        private const val CHANNEL = "bb9_qa_producer"
        private const val NOTIFICATION = 9099
        private const val LIFETIME_MS = 180000L
        private const val PRIVATE_LIMIT = 131072
        private val HEX = Regex("[0-9a-f]{64}")
        private val ERRORS = setOf("producer_action_invalid", "producer_ticket_invalid", "producer_ticket_changed",
            "producer_already_started", "producer_permit_invalid", "producer_stopped", "producer_unavailable",
            "producer_identity_invalid", "producer_root_invalid", "producer_root_changed", "producer_leader_invalid",
            "producer_leader_changed", "producer_ancestry_invalid", "producer_app_changed", "producer_child_exited",
            "producer_header_invalid", "producer_header_timeout", "producer_output_overflow", "producer_export_invalid",
            "producer_command_invalid", "producer_environment_invalid", "producer_fixture_invalid",
            "producer_writer_invalid",
            "producer_metadata_invalid", "producer_metadata_exists", "producer_metadata_changed",
            "producer_file_unavailable",
            "producer_file_invalid", "producer_file_changed", "producer_kernel_invalid", "producer_uid_invalid",
            "producer_boot_invalid", "producer_lifetime_expired", "producer_system_timeout",
            "producer_worker_unavailable") +
        setOf("identity", "export", "app_identity", "app_uid", "fixture", "command", "environment", "metadata",
            "process", "pid", "kernel", "header", "proof").flatMap { stage ->
            setOf("denied", "missing", "system", "field", "method", "io", "unavailable").map { category ->
                "producer_" + stage + "_" + category
            }
        }
    }
    private inner class ProducerFiles {
        fun readPrivate(file: File, maximum: Int): String {
            val before = stat(file) ?: throw Refused("producer_file_unavailable")
            checkSafe(OsConstants.S_ISREG(before.st_mode) && before.st_uid == Process.myUid() &&
                before.st_size in 1..maximum.toLong(), "producer_file_invalid")
            val descriptor = Os.open(file.absolutePath,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
            return FileInputStream(descriptor).use { stream ->
                val opened = Os.fstat(descriptor)
                checkSafe(sameFile(before, opened), "producer_file_changed")
                val bytes = ByteArray(maximum + 1)
                var used = 0
                while (used < bytes.size) {
                    val count = stream.read(bytes, used, bytes.size - used)
                    if (count < 0) break
                    checkSafe(count > 0, "producer_file_invalid"); used += count
                }
                checkSafe(used.toLong() == before.st_size && used in 1..maximum &&
                    sameFile(opened, Os.fstat(descriptor)) && sameFile(opened, stat(file)), "producer_file_changed")
                String(bytes, 0, used, Charsets.UTF_8)
            }
        }

        fun sameFile(first: StructStat, second: StructStat?) = second != null &&
            first.st_dev == second.st_dev && first.st_ino == second.st_ino && first.st_size == second.st_size &&
            first.st_mode == second.st_mode && first.st_uid == second.st_uid &&
            first.st_mtime == second.st_mtime && first.st_ctime == second.st_ctime

        fun stat(file: File): StructStat? = try { Os.lstat(file.absolutePath) }
        catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) null else throw error }

        fun kernel(pid: Int, name: String, maximum: Int): String {
            checkSafe(pid > 1 && name in setOf("stat", "status", "environ"), "producer_kernel_invalid")
            val descriptor = Os.open("/proc/$pid/$name", OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            return FileInputStream(descriptor).use { stream ->
                val bytes = ByteArray(maximum + 1)
                var used = 0
                while (used < bytes.size) {
                    val count = stream.read(bytes, used, bytes.size - used)
                    if (count < 0) break
                    checkSafe(count > 0, "producer_kernel_invalid")
                    used += count
                }
                checkSafe(used in 1..maximum, "producer_kernel_invalid")
                String(bytes, 0, used, Charsets.UTF_8)
            }
        }

        fun identity(pid: Int) = RuntimeProcessIdentity.stat(kernel(pid, "stat", STAT_LIMIT))

        fun uid(pid: Int): Int {
            val lines = kernel(pid, "status", STATUS_LIMIT).lineSequence().filter { it.startsWith("Uid:") }.toList()
            checkSafe(lines.size == 1, "producer_uid_invalid")
            val values = lines.single().substringAfter(':').trim().split(Regex("\\s+"))
            checkSafe(values.size == UID_FIELDS && values.all { it.toIntOrNull() == Process.myUid() },
                "producer_uid_invalid")
            return Process.myUid()
        }

        fun nonce(pid: Int, id: String) = kernel(pid, "environ",
            PRIVATE_LIMIT).split('\u0000').any { it == "OC_RUNTIME_OWNER=$id" }

        fun boot(): String {
            val bytes = ByteArray(BOOT_LIMIT + 1)
            val size = FileInputStream("/proc/sys/kernel/random/boot_id").use { it.read(bytes) }
            checkSafe(size in 1..BOOT_LIMIT, "producer_boot_invalid")
            return String(bytes, 0, size, Charsets.US_ASCII).trim().also {
                checkSafe(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(it),
                    "producer_boot_invalid") }
        }
    }

    private inner class ProducerRecords {
        fun readWriter(): InstallerTicket {
            val raw = files.readPrivate(File(applicationInfo.dataDir, "shared_prefs/builtin_component_writer.xml"),
                WRITER_LIMIT)
            checkSafe(!raw.contains("<!DOCTYPE", true) && !raw.contains("<!ENTITY", true), "producer_writer_invalid")
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            parser.setInput(StringReader(raw))
            var selected: String? = null
            var events = 0
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                checkSafe(++events <= XML_EVENT_LIMIT && parser.depth <= XML_DEPTH_LIMIT &&
                    parser.eventType != XmlPullParser.DOCDECL,
                    "producer_writer_invalid")
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (parser.depth == 1) checkSafe(parser.name == "map", "producer_writer_invalid")
                    if (parser.depth == 2 && parser.getAttributeValue(null, "name") == "ticket") {
                        checkSafe(parser.name == "string" && selected == null, "producer_writer_invalid")
                        selected = parser.nextText()
                    }
                }
                parser.next()
            }
            checkSafe(selected != null && selected!!.toByteArray().size <= PRIVATE_LIMIT, "producer_writer_invalid")
            return readTicket(JSONObject(selected!!))
        }

        fun readTicket(value: JSONObject) = InstallerTicket.read(decode(value) as Map<*, *>)

        fun readIdentity(value: JSONObject) = RuntimeProcessIdentity.read(decode(value) as Map<*, *>)

        fun decode(value: Any?, depth: Int = 0): Any? {
            checkSafe(depth <= JSON_DEPTH_LIMIT, "producer_metadata_invalid")
            return when (value) {
                null, JSONObject.NULL -> null
                is JSONObject -> { checkSafe(value.length() <= JSON_FIELD_LIMIT, "producer_metadata_invalid");
                    keys(value).associateWith { decode(value.get(it), depth + 1) } }
                is JSONArray -> { checkSafe(value.length() <= JSON_ARRAY_LIMIT, "producer_metadata_invalid");
                    (0 until value.length()).map { decode(value.get(it), depth + 1) } }
                is String -> { checkSafe(value.toByteArray().size <= PRIVATE_LIMIT, "producer_metadata_invalid");
                    value }
                is Int, is Long, is Boolean -> value
                else -> throw Refused("producer_metadata_invalid")
            }
        }

        fun keys(value: JSONObject) = value.keys().asSequence().toSet()

        fun loadExport(): Export {
            val value = JSONObject(files.readPrivate(File(filesDir, "bb9-external-qa.json"), PRIVATE_LIMIT))
            val id = value.opt("ticketId") as? String ?: throw Refused("producer_export_invalid")
            checkSafe(HEX.matches(id), "producer_export_invalid")
            ticketId = id
            checkSafe(keys(value) == setOf("version", "uid", "app", "ticketId", "command", "environment") &&
                value.get("version") == 1 && value.get("uid") == Process.myUid(), "producer_export_invalid")
            val app = readIdentity(value.getJSONObject("app"))
            checkSafe(app.pid != Process.myPid(), "producer_app_changed")
            // The private native-authored export is tied to a fresh preparing fixture.
            // A sibling Android process may hide Main's /proc entry while Main is alive.
            launchStage = "fixture"
            val fixture = JSONObject(files.readPrivate(File(filesDir, "bb9-runtime-qa.json"), PRIVATE_LIMIT))
            val durable = readWriter()
            checkSafe(fixture.getInt("version") == 1 && fixture.getString("stage") == "preparing" &&
                readIdentity(fixture.getJSONObject("app")) == app &&
                readTicket(fixture.getJSONObject("ticket")) == durable && durable.ownership.prepared &&
                durable.id == id && durable.ownership.boot == files.boot(), "producer_ticket_changed")
            launchStage = "command"
            val (command, native) = readCommand(value, id)
            launchStage = "environment"
            val environment = value.getJSONObject("environment")
            val expected = mapOf("PROOT_LOADER" to "$native/libproot-loader.so",
                "PROOT_TMP_DIR" to "${cacheDir.absolutePath}/proot-tmp", "LD_LIBRARY_PATH" to native,
                "OC_RUNTIME_OWNER" to id)
            checkSafe(keys(environment) == expected.keys && expected.all { (key,
                    item) -> environment.opt(key) == item },
                "producer_environment_invalid")
            return Export(app, id, command, expected)
        }

        fun readCommand(value: JSONObject, id: String): Pair<List<String>, String> {
            val array = value.getJSONArray("command")
            checkSafe(array.length() in COMMAND_MIN..COMMAND_MAX, "producer_command_invalid")
            val command = (0 until array.length()).map {
                val item = array.get(it)
                checkSafe(item is String && item.toByteArray().size in 1..ARGUMENT_LIMIT &&
                    item.all { c -> c >= ' ' && c != '\u007f' || c == '\n' || c == '\t' }, "producer_command_invalid")
                item as String
            }
            checkSafe(command.sumOf { it.toByteArray().size } <= COMMAND_LIMIT, "producer_command_invalid")
            val native = applicationInfo.nativeLibraryDir
            verifyCommand(command, id, native)
            return command to native
        }
        private fun verifyCommand(command: List<String>, id: String, native: String) {
            val proot = "$native/libproot.so"
            val index = command.indexOf(proot)
            checkSafe(index >= 0 && command.count { it.endsWith("/libproot.so") } == 1 &&
                (command.first() == proot || command.first() == "$native/libaiteam_sandbox.so" &&
                index > 0 && "--" in command.take(index)), "producer_command_invalid")
            val roots = command.filter { it.startsWith("--rootfs=") }
            val allowed = setOf("--rootfs=${filesDir.absolutePath}/linux/ubuntu",
                "--rootfs=/data/data/$packageName/files/linux/ubuntu",
                "--rootfs=/data/user/0/$packageName/files/linux/ubuntu")
            checkSafe(roots.size == 1 && roots.single() in allowed && "--kill-on-exit" in command &&
                command.any { "OC-INSTALL-1" in it && id in it && "read -r permit" in it }, "producer_command_invalid")
        }

    }

    private inner class ProducerLaunch {
        fun launch() {
            try {
                producer = files.identity(Process.myPid())
                launchStage = "export"
                val value = records.loadExport()
                startChild(value)
                val child = requireChild()
                acceptHeader(child, value)
                drainOutput(child)
            } catch (error: Refused) { lifecycle.finish("failed", error.code) }
            catch (error: ErrnoException) {
                val category = when (error.errno) {
                    OsConstants.EACCES, OsConstants.EPERM -> "denied"
                    OsConstants.ENOENT -> "missing"
                    else -> "system"
                }
                launchFailed(category)
            }
            catch (_: NoSuchFieldException) { launchFailed("field") }
            catch (_: NoSuchMethodError) { launchFailed("method") }
            catch (_: SecurityException) { launchFailed("denied") }
            catch (_: java.io.IOException) { launchFailed("io") }
            catch (_: Throwable) { launchFailed("unavailable") }
        }

        fun startChild(value: Export) {
            synchronized(guard) {
                checkSafe(!stopped, "producer_stopped")
                checkSafe(files.stat(metadata) == null, "producer_metadata_exists")
                exported = value
                ticketId = value.ticketId
                launchStage = "metadata"
                publish("waiting", null)
                // A separate ordinary Android process supplies its own app context.
                // The native-authored protected argv is never unwrapped or reparsed.
                launchStage = "process"
                process = ProcessBuilder(value.command).apply {
                    environment().clear(); environment().putAll(value.environment)
                    redirectError(File("/dev/null"))
                }.start()
                val child = process!!
                launchStage = "pid"
                val pid = child.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(child)
                launchStage = "kernel"
                root = files.identity(pid)
                checkSafe(root!!.parent == Process.myPid() && files.uid(pid) == Process.myUid(),
                    "producer_root_invalid")
            }
        }

        fun requireChild(): java.lang.Process = process ?: throw Refused("producer_root_invalid")

        fun acceptHeader(child: java.lang.Process, value: Export) {
            launchStage = "header"
            val header = header(child.inputStream)
            val match = Regex("OC-INSTALL-1 ([0-9a-f]{64}) ([1-9][0-9]{0,9})").matchEntire(header)
            ?: throw Refused("producer_header_invalid")
            checkSafe(match.groupValues[1] == value.ticketId, "producer_ticket_changed")
            val pid = match.groupValues[2].toLongOrNull()
            checkSafe(pid != null && pid in 2..Int.MAX_VALUE.toLong(), "producer_leader_invalid")
            synchronized(guard) {
                checkSafe(!stopped, "producer_stopped")
                launchStage = "proof"
                leader = files.identity(pid!!.toInt())
                proof.prove(value)
                publish("waiting", null)
            }
        }

        fun drainOutput(child: java.lang.Process) {
            var discarded = 0
            while (!stopped && !Thread.currentThread().isInterrupted) {
                checkSafe(SystemClock.elapsedRealtime() < lifetimeDeadline, "producer_lifetime_expired")
                checkSafe(child.isAlive, "producer_child_exited")
                val available = child.inputStream.available()
                if (available > 0) {
                    val buffer = ByteArray(minOf(available, OUTPUT_BUFFER))
                    val count = child.inputStream.read(buffer)
                    if (count > 0) discarded += count
                    checkSafe(discarded <= OUTPUT_LIMIT, "producer_output_overflow")
                }
                Thread.sleep(OUTPUT_POLL_MS)
            }
        }

        fun launchFailed(category: String) {
            lifecycle.finish("failed", "producer_" + launchStage + "_" + category)
        }

        fun header(input: InputStream): String {
            val bytes = ArrayList<Byte>()
            val until = SystemClock.elapsedRealtime() + HEADER_TIMEOUT_MS
            while (SystemClock.elapsedRealtime() < until && !stopped && !Thread.currentThread().isInterrupted) {
                if (input.available() == 0) { checkSafe(process?.isAlive == true, "producer_child_exited");
                    Thread.sleep(HEADER_POLL_MS); continue }
                val byte = input.read()
                checkSafe(byte >= 0, "producer_header_invalid")
                if (byte == NEWLINE_BYTE) return bytes.toByteArray().toString(Charsets.US_ASCII)
                checkSafe(byte in ASCII_FIRST..ASCII_LAST && bytes.size < HEADER_LIMIT, "producer_header_invalid")
                bytes.add(byte.toByte())
            }
            throw Refused("producer_header_timeout")
        }
    }

    private inner class ProducerLifecycle {
        fun startAction(intent: Intent) {
            val extras = intent.extras

            checkSafe(extras == null || extras.isEmpty, "producer_action_invalid")
            foreground()
            synchronized(guard) {
                checkSafe(!started && !stopped, "producer_already_started")
                started = true
                lifetimeDeadline = SystemClock.elapsedRealtime() + LIFETIME_MS
            }
            handler.postDelayed(expiry, LIFETIME_MS)
            worker = Thread({ launcher.launch() }, "bb9-qa-producer").apply { isDaemon = true; start() }
        }

        fun controlAction(intent: Intent) {
            val extras = intent.extras

            checkSafe(extras != null && extras.keySet() == setOf("ticketId") &&
                extras.get("ticketId") is String && HEX.matches(extras.getString("ticketId") ?: ""),
                "producer_ticket_invalid")
            synchronized(guard) {
                checkSafe(started && extras!!.getString("ticketId") == ticketId,
                    "producer_ticket_changed")
                if (intent.action == ACTION_PERMIT) {
                    checkSafe(!stopped && !permitted && !permitClaimed && root != null && leader != null,
                        "producer_permit_invalid")
                    permitClaimed = true
                    permitWorker = Thread({ proof.release() }, "bb9-qa-permit").apply { isDaemon = true;
                        start() }
                }
            }
            if (intent.action == ACTION_STOP) finish("stopped", null)
        }

        fun foreground() {
            val manager = getSystemService(NotificationManager::class.java)
            ?: throw Refused("producer_unavailable")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Update recovery check", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) })
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this@BuiltinInstallerProducerQaService, CHANNEL)
            else { @Suppress("DEPRECATION") Notification.Builder(this@BuiltinInstallerProducerQaService) }
            val notification = builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Checking update recovery").setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION, notification)
        }

        fun finish(state: String, error: String?) {
            // Honour foreground timeout before waiting for the bounded gate/file proof.
            stopped = true
            handler.removeCallbacks(expiry)
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
            try { stopSelf() } catch (_: Throwable) { }
            val cleanupHere = synchronized(guard) {
                finalizeChild()
            }
            if (!cleanupHere) return
            val current = Thread.currentThread()
            val interrupted = Thread.interrupted()
            val others = listOfNotNull(worker, permitWorker).filter { it !== current }
            for (thread in others) try { thread.join(WORKER_JOIN_MS) } catch (_: InterruptedException) { }
            synchronized(guard) {
                try {
                    if (others.any { it.isAlive }) publish("failed", "producer_worker_unavailable")
                    else publish(state, error)
                } catch (_: Throwable) { }
            }
            if (interrupted) current.interrupt()
        }

        fun finalizeChild(): Boolean {
            return if (!finalized) {
                finalized = true
                val child = process
                // EOF prevents an unreleased gate from executing. Only this
                // service's captured Process is destroyed; host owns exact tree draining.
                try { child?.outputStream?.close() } catch (_: Throwable) { }
                try { if (child?.isAlive == true) child.destroy() } catch (_: Throwable) { }
                try { child?.inputStream?.close() } catch (_: Throwable) { }
                try { child?.errorStream?.close() } catch (_: Throwable) { }
                worker?.interrupt(); permitWorker?.interrupt()
                true
            } else false

        }
    }

    private inner class ProducerProof {
        fun release() {
            try {
                synchronized(guard) {
                    checkSafe(!stopped && !permitted, "producer_permit_invalid")
                    checkSafe(SystemClock.elapsedRealtime() < lifetimeDeadline, "producer_lifetime_expired")
                    val value = exported ?: throw Refused("producer_permit_invalid")
                    prove(value)
                    verifyPermitRecords(value)
                    prove(value)
                    checkSafe(!stopped && process?.isAlive == true, "producer_stopped")
                    // Mark before write: partial permit delivery is never treated as an untouched gate.
                    permitted = true
                    process!!.outputStream.write((value.ticketId + "\n").toByteArray(Charsets.US_ASCII))
                    process!!.outputStream.flush()
                    publish("running", null)
                }
            } catch (error: Refused) { lifecycle.finish("failed", error.code) }
            catch (_: Throwable) { lifecycle.finish("failed", "producer_unavailable") }
        }

        fun verifyPermitRecords(value: Export) {
            val durable = records.readWriter()
            val fixture = JSONObject(files.readPrivate(File(filesDir, "bb9-runtime-qa.json"), PRIVATE_LIMIT))
            checkSafe(fixture.getInt("version") == 1 && fixture.getString("stage") == "committed",
                "producer_fixture_invalid")
            checkSafe(records.readTicket(fixture.getJSONObject("ticket")) == durable &&
                records.readIdentity(fixture.getJSONObject("app")) == value.app, "producer_ticket_changed")
            checkSafe(!durable.ownership.prepared && durable.id == value.ticketId &&
                durable.operation == InstallerOperation.INSTALL && durable.ownership.nonce == value.ticketId &&
                durable.ownership.root == root && durable.ownership.leader == leader &&
                durable.ownership.boot == files.boot(), "producer_ticket_changed")
            checkSafe(durable.observed.any { it.sameProcess(root) } &&
                durable.observed.any { it.sameProcess(leader) }, "producer_ticket_changed")
            // Fresh disk reads avoid cross-process SharedPreferences caches.
            checkSafe(records.readWriter() == durable, "producer_ticket_changed")
            val again = JSONObject(files.readPrivate(File(filesDir, "bb9-runtime-qa.json"), PRIVATE_LIMIT))
            checkSafe(again.getString("stage") == "committed" &&
                records.readTicket(again.getJSONObject("ticket")) == durable &&
                records.readIdentity(again.getJSONObject("app")) == value.app, "producer_ticket_changed")
        }

        fun prove(value: Export) {
            val own = requiredIdentity(producer, "producer_identity_invalid")
            val fixedRoot = requiredIdentity(root, "producer_root_invalid")
            val fixedLeader = requiredIdentity(leader, "producer_leader_invalid")
            checkSafe(own == files.identity(Process.myPid()) && fixedRoot == files.identity(fixedRoot.pid) &&
                fixedRoot.parent == own.pid && files.uid(fixedRoot.pid) == Process.myUid() && files.nonce(fixedRoot.pid,
                value.ticketId),
                "producer_root_changed")
            checkSafe(fixedLeader == files.identity(fixedLeader.pid) && fixedLeader.pid != fixedRoot.pid &&
                fixedLeader.pid == fixedLeader.session && fixedLeader.pid == fixedLeader.group &&
                files.uid(fixedLeader.pid) == Process.myUid() && files.nonce(fixedLeader.pid, value.ticketId),
                "producer_leader_changed")
            proveAncestry(fixedRoot, fixedLeader, value.ticketId)
        }

        fun requiredIdentity(value: RuntimeProcessIdentity?, code: String): RuntimeProcessIdentity =
            value ?: throw Refused(code)

        fun proveAncestry(fixedRoot: RuntimeProcessIdentity, fixedLeader: RuntimeProcessIdentity, id: String) {
            val seen = mutableSetOf<Int>()
            var current = fixedLeader
            repeat(ANCESTRY_LIMIT) {
                checkSafe(seen.add(current.pid), "producer_ancestry_invalid")
                if (current.parent == fixedRoot.pid) return
                checkSafe(current.parent > 1 && files.uid(current.parent) == Process.myUid(),
                    "producer_ancestry_invalid")
                current = files.identity(current.parent)
                checkSafe(files.nonce(current.pid, id), "producer_ancestry_invalid")
            }
            throw Refused("producer_ancestry_invalid")
        }
    }

}
