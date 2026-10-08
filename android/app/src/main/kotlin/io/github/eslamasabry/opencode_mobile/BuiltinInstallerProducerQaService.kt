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
    private val expiry = Runnable { finish("stopped", "producer_lifetime_expired") }
    private val metadata get() = File(filesDir, "bb9-producer-qa.json")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BuildConfig.BUILTIN_RUNTIME_QA) { stopSelf(); return START_NOT_STICKY }
        try {
            checkSafe(intent != null && intent.action in setOf(ACTION_START, ACTION_PERMIT, ACTION_STOP),
                "producer_action_invalid")
            checkSafe(intent!!.component?.className == javaClass.name && intent.data == null &&
                intent.clipData == null && intent.selector == null, "producer_action_invalid")
            val extras = intent.extras
            when (intent.action) {
                ACTION_START -> {
                    checkSafe(extras == null || extras.isEmpty, "producer_action_invalid")
                    foreground()
                    synchronized(guard) {
                        checkSafe(!started && !stopped, "producer_already_started")
                        started = true
                        lifetimeDeadline = SystemClock.elapsedRealtime() + LIFETIME_MS
                    }
                    handler.postDelayed(expiry, LIFETIME_MS)
                    worker = Thread({ launch() }, "bb9-qa-producer").apply { isDaemon = true; start() }
                }
                ACTION_PERMIT, ACTION_STOP -> {
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
                            permitWorker = Thread({ release() }, "bb9-qa-permit").apply { isDaemon = true; start() }
                        }
                    }
                    if (intent.action == ACTION_STOP) finish("stopped", null)
                }
            }
        } catch (error: Refused) { finish("failed", error.code) }
        catch (_: Throwable) { finish("failed", "producer_unavailable") }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { finish("stopped", "producer_system_timeout") }

    override fun onDestroy() {
        finish("stopped", null)
        worker?.interrupt(); permitWorker?.interrupt()
        super.onDestroy()
        // This private QA process must not remain a cached metadata writer.
        // Only terminate this component's own OS-confirmed Android process.
        if (BuildConfig.BUILTIN_RUNTIME_QA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            android.app.Application.getProcessName() == packageName + ":bb9producer") {
            Process.killProcess(Process.myPid())
        }
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
            ?: throw Refused("producer_unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Update recovery check", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) })
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
            else { @Suppress("DEPRECATION") Notification.Builder(this) }
        val notification = builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Checking update recovery").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION, notification)
    }

    private fun launch() {
        try {
            producer = identity(Process.myPid())
            launchStage = "export"
            val value = loadExport()
            synchronized(guard) {
                checkSafe(!stopped, "producer_stopped")
                checkSafe(stat(metadata) == null, "producer_metadata_exists")
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
                root = identity(pid)
                checkSafe(root!!.parent == Process.myPid() && uid(pid) == Process.myUid(), "producer_root_invalid")
            }
            val child = process ?: throw Refused("producer_root_invalid")
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
                leader = identity(pid!!.toInt())
                prove(value)
                publish("waiting", null)
            }
            var discarded = 0
            while (!stopped && !Thread.currentThread().isInterrupted) {
                checkSafe(SystemClock.elapsedRealtime() < lifetimeDeadline, "producer_lifetime_expired")
                if (!child.isAlive) throw Refused("producer_child_exited")
                val available = child.inputStream.available()
                if (available > 0) {
                    val buffer = ByteArray(minOf(available, 1024))
                    val count = child.inputStream.read(buffer)
                    if (count > 0) discarded += count
                    checkSafe(discarded <= 4096, "producer_output_overflow")
                }
                Thread.sleep(25)
            }
        } catch (error: Refused) { finish("failed", error.code) }
        catch (error: Throwable) {
            val category = when (error) {
                is ErrnoException -> when (error.errno) {
                    OsConstants.EACCES, OsConstants.EPERM -> "denied"
                    OsConstants.ENOENT -> "missing"
                    else -> "system"
                }
                is NoSuchFieldException -> "field"
                is NoSuchMethodError -> "method"
                is SecurityException -> "denied"
                is java.io.IOException -> "io"
                else -> "unavailable"
            }
            finish("failed", "producer_" + launchStage + "_" + category)
        }
    }

    private fun release() {
        try {
            synchronized(guard) {
                checkSafe(!stopped && !permitted, "producer_permit_invalid")
                checkSafe(SystemClock.elapsedRealtime() < lifetimeDeadline, "producer_lifetime_expired")
                val value = exported ?: throw Refused("producer_permit_invalid")
                prove(value)
                val durable = readWriter()
                val fixture = JSONObject(readPrivate(File(filesDir, "bb9-runtime-qa.json"), PRIVATE_LIMIT))
                checkSafe(fixture.getInt("version") == 1 && fixture.getString("stage") == "committed",
                    "producer_fixture_invalid")
                checkSafe(readTicket(fixture.getJSONObject("ticket")) == durable &&
                    readIdentity(fixture.getJSONObject("app")) == value.app, "producer_ticket_changed")
                checkSafe(!durable.ownership.prepared && durable.id == value.ticketId &&
                    durable.operation == InstallerOperation.INSTALL && durable.ownership.nonce == value.ticketId &&
                    durable.ownership.root == root && durable.ownership.leader == leader &&
                    durable.ownership.boot == boot(), "producer_ticket_changed")
                checkSafe(durable.observed.any { it.sameProcess(root) } &&
                    durable.observed.any { it.sameProcess(leader) }, "producer_ticket_changed")
                // Fresh disk reads avoid cross-process SharedPreferences caches.
                checkSafe(readWriter() == durable, "producer_ticket_changed")
                val again = JSONObject(readPrivate(File(filesDir, "bb9-runtime-qa.json"), PRIVATE_LIMIT))
                checkSafe(again.getString("stage") == "committed" &&
                    readTicket(again.getJSONObject("ticket")) == durable &&
                    readIdentity(again.getJSONObject("app")) == value.app, "producer_ticket_changed")
                prove(value)
                checkSafe(!stopped && process?.isAlive == true, "producer_stopped")
                // Mark before write: partial permit delivery is never treated as an untouched gate.
                permitted = true
                process!!.outputStream.write((value.ticketId + "\n").toByteArray(Charsets.US_ASCII))
                process!!.outputStream.flush()
                publish("running", null)
            }
        } catch (error: Refused) { finish("failed", error.code) }
        catch (_: Throwable) { finish("failed", "producer_unavailable") }
    }

    private fun loadExport(): Export {
        val value = JSONObject(readPrivate(File(filesDir, "bb9-external-qa.json"), PRIVATE_LIMIT))
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
        val fixture = JSONObject(readPrivate(File(filesDir, "bb9-runtime-qa.json"), PRIVATE_LIMIT))
        val durable = readWriter()
        checkSafe(fixture.getInt("version") == 1 && fixture.getString("stage") == "preparing" &&
            readIdentity(fixture.getJSONObject("app")) == app &&
            readTicket(fixture.getJSONObject("ticket")) == durable && durable.ownership.prepared &&
            durable.id == id && durable.ownership.boot == boot(), "producer_ticket_changed")
        launchStage = "command"
        val array = value.getJSONArray("command")
        checkSafe(array.length() in 3..128, "producer_command_invalid")
        val command = (0 until array.length()).map {
            val item = array.get(it)
            checkSafe(item is String && item.toByteArray().size in 1..32768 &&
                item.all { c -> c >= ' ' && c != '\u007f' || c == '\n' || c == '\t' }, "producer_command_invalid")
            item as String
        }
        checkSafe(command.sumOf { it.toByteArray().size } <= 65536, "producer_command_invalid")
        val native = applicationInfo.nativeLibraryDir
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
        launchStage = "environment"
        val environment = value.getJSONObject("environment")
        val expected = mapOf("PROOT_LOADER" to "$native/libproot-loader.so",
            "PROOT_TMP_DIR" to "${cacheDir.absolutePath}/proot-tmp", "LD_LIBRARY_PATH" to native, "OC_RUNTIME_OWNER" to id)
        checkSafe(keys(environment) == expected.keys && expected.all { (key, item) -> environment.opt(key) == item },
            "producer_environment_invalid")
        return Export(app, id, command, expected)
    }

    private fun prove(value: Export) {
        val own = producer ?: throw Refused("producer_identity_invalid")
        val fixedRoot = root ?: throw Refused("producer_root_invalid")
        val fixedLeader = leader ?: throw Refused("producer_leader_invalid")
        checkSafe(own == identity(Process.myPid()) && fixedRoot == identity(fixedRoot.pid) &&
            fixedRoot.parent == own.pid && uid(fixedRoot.pid) == Process.myUid() && nonce(fixedRoot.pid, value.ticketId),
            "producer_root_changed")
        checkSafe(fixedLeader == identity(fixedLeader.pid) && fixedLeader.pid != fixedRoot.pid &&
            fixedLeader.pid == fixedLeader.session && fixedLeader.pid == fixedLeader.group &&
            uid(fixedLeader.pid) == Process.myUid() && nonce(fixedLeader.pid, value.ticketId), "producer_leader_changed")
        val seen = mutableSetOf<Int>()
        var current = fixedLeader
        repeat(128) {
            checkSafe(seen.add(current.pid), "producer_ancestry_invalid")
            if (current.parent == fixedRoot.pid) return
            checkSafe(current.parent > 1 && uid(current.parent) == Process.myUid(), "producer_ancestry_invalid")
            current = identity(current.parent)
            checkSafe(nonce(current.pid, value.ticketId), "producer_ancestry_invalid")
        }
        throw Refused("producer_ancestry_invalid")
    }

    private fun header(input: InputStream): String {
        val bytes = ArrayList<Byte>()
        val until = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < until && !stopped && !Thread.currentThread().isInterrupted) {
            if (input.available() == 0) { checkSafe(process?.isAlive == true, "producer_child_exited"); Thread.sleep(10); continue }
            val byte = input.read()
            checkSafe(byte >= 0, "producer_header_invalid")
            if (byte == 10) return bytes.toByteArray().toString(Charsets.US_ASCII)
            checkSafe(byte in 32..126 && bytes.size < 128, "producer_header_invalid")
            bytes.add(byte.toByte())
        }
        throw Refused("producer_header_timeout")
    }

    private fun readWriter(): InstallerTicket {
        val raw = readPrivate(File(applicationInfo.dataDir, "shared_prefs/builtin_component_writer.xml"), 524288)
        checkSafe(!raw.contains("<!DOCTYPE", true) && !raw.contains("<!ENTITY", true), "producer_writer_invalid")
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(StringReader(raw))
        var selected: String? = null
        var events = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            checkSafe(++events <= 16384 && parser.depth <= 4 && parser.eventType != XmlPullParser.DOCDECL,
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

    private fun readTicket(value: JSONObject) = InstallerTicket.read(decode(value) as Map<*, *>)
    private fun readIdentity(value: JSONObject) = RuntimeProcessIdentity.read(decode(value) as Map<*, *>)
    private fun decode(value: Any?, depth: Int = 0): Any? {
        checkSafe(depth <= 16, "producer_metadata_invalid")
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> { checkSafe(value.length() <= 32, "producer_metadata_invalid"); keys(value).associateWith { decode(value.get(it), depth + 1) } }
            is JSONArray -> { checkSafe(value.length() <= 128, "producer_metadata_invalid"); (0 until value.length()).map { decode(value.get(it), depth + 1) } }
            is String -> { checkSafe(value.toByteArray().size <= PRIVATE_LIMIT, "producer_metadata_invalid"); value }
            is Int, is Long, is Boolean -> value
            else -> throw Refused("producer_metadata_invalid")
        }
    }
    private fun keys(value: JSONObject) = value.keys().asSequence().toSet()

    private fun readPrivate(file: File, maximum: Int): String {
        val before = stat(file) ?: throw Refused("producer_file_unavailable")
        checkSafe(OsConstants.S_ISREG(before.st_mode) && before.st_uid == Process.myUid() &&
            before.st_size in 1..maximum.toLong(), "producer_file_invalid")
        val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
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
    private fun sameFile(first: StructStat, second: StructStat?) = second != null &&
        first.st_dev == second.st_dev && first.st_ino == second.st_ino && first.st_size == second.st_size &&
        first.st_mode == second.st_mode && first.st_uid == second.st_uid &&
        first.st_mtime == second.st_mtime && first.st_ctime == second.st_ctime
    private fun stat(file: File): StructStat? = try { Os.lstat(file.absolutePath) }
        catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) null else throw error }
    private fun kernel(pid: Int, name: String, maximum: Int): String {
        checkSafe(pid > 1 && name in setOf("stat", "status", "environ"), "producer_kernel_invalid")
        val descriptor = Os.open("/proc/$pid/$name", OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        return FileInputStream(descriptor).use { stream ->
            val bytes = ByteArray(maximum + 1)
            var used = 0
            while (used < bytes.size) { val count = stream.read(bytes, used, bytes.size - used); if (count < 0) break; checkSafe(count > 0, "producer_kernel_invalid"); used += count }
            checkSafe(used in 1..maximum, "producer_kernel_invalid")
            String(bytes, 0, used, Charsets.UTF_8)
        }
    }
    private fun identity(pid: Int) = RuntimeProcessIdentity.stat(kernel(pid, "stat", 4096))
    private fun uid(pid: Int): Int {
        val lines = kernel(pid, "status", 16384).lineSequence().filter { it.startsWith("Uid:") }.toList()
        checkSafe(lines.size == 1, "producer_uid_invalid")
        val values = lines.single().substringAfter(':').trim().split(Regex("\\s+"))
        checkSafe(values.size == 4 && values.all { it.toIntOrNull() == Process.myUid() }, "producer_uid_invalid")
        return Process.myUid()
    }
    private fun nonce(pid: Int, id: String) = kernel(pid, "environ", PRIVATE_LIMIT).split('\u0000').any { it == "OC_RUNTIME_OWNER=$id" }
    private fun boot(): String {
        val bytes = ByteArray(65)
        val size = FileInputStream("/proc/sys/kernel/random/boot_id").use { it.read(bytes) }
        checkSafe(size in 1..64, "producer_boot_invalid")
        return String(bytes, 0, size, Charsets.US_ASCII).trim().also {
            checkSafe(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(it), "producer_boot_invalid") }
    }

    private fun publish(state: String, error: String?) {
        val id = ticketId ?: return
        val own = producer ?: return
        checkSafe(state in setOf("waiting", "running", "failed", "stopped") &&
            (error == null || error in ERRORS), "producer_metadata_invalid")
        val existing = stat(metadata)
        checkSafe(if (outputIdentity == null) existing == null else sameFile(outputIdentity!!, existing), "producer_metadata_changed")
        val temporary = File(filesDir, "bb9-producer-qa.json.tmp")
        checkSafe(stat(temporary) == null, "producer_metadata_exists")
        val value = JSONObject().put("version", 1).put("ticketId", id).put("uid", Process.myUid())
            .put("producer", JSONObject(own.map())).put("root", root?.let { JSONObject(it.map()) } ?: JSONObject.NULL)
            .put("leader", leader?.let { JSONObject(it.map()) } ?: JSONObject.NULL).put("state", state)
        if (error != null) value.put("error", error)
        val descriptor = Os.open(temporary.absolutePath, OsConstants.O_WRONLY or OsConstants.O_CREAT or
            OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
        val temporaryIdentity = FileOutputStream(descriptor).use {
            checkSafe(OsConstants.S_ISREG(Os.fstat(descriptor).st_mode), "producer_metadata_invalid")
            it.write(value.toString().toByteArray(Charsets.UTF_8)); it.fd.sync()
            Os.fstat(descriptor)
        }
        checkSafe(sameFile(temporaryIdentity, stat(temporary)), "producer_metadata_changed")
        checkSafe(if (outputIdentity == null) stat(metadata) == null else sameFile(outputIdentity!!, stat(metadata)),
            "producer_metadata_changed")
        Os.rename(temporary.absolutePath, metadata.absolutePath)
        val directory = Os.open(filesDir.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try { Os.fsync(directory) } finally { Os.close(directory) }
        outputIdentity = stat(metadata) ?: throw Refused("producer_metadata_invalid")
    }

    private fun finish(state: String, error: String?) {
        // Honour foreground timeout before waiting for the bounded gate/file proof.
        stopped = true
        handler.removeCallbacks(expiry)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
        try { stopSelf() } catch (_: Throwable) { }
        val cleanupHere = synchronized(guard) {
            if (!finalized) {
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
        if (!cleanupHere) return
        val current = Thread.currentThread()
        val interrupted = Thread.interrupted()
        val others = listOfNotNull(worker, permitWorker).filter { it !== current }
        for (thread in others) try { thread.join(500) } catch (_: InterruptedException) { }
        synchronized(guard) {
            try {
                if (others.any { it.isAlive }) publish("failed", "producer_worker_unavailable")
                else publish(state, error)
            } catch (_: Throwable) { }
        }
        if (interrupted) current.interrupt()
    }
    private fun checkSafe(value: Boolean, code: String) { if (!value) throw Refused(code) }

    companion object {
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
            "producer_command_invalid", "producer_environment_invalid", "producer_fixture_invalid", "producer_writer_invalid",
            "producer_metadata_invalid", "producer_metadata_exists", "producer_metadata_changed", "producer_file_unavailable",
            "producer_file_invalid", "producer_file_changed", "producer_kernel_invalid", "producer_uid_invalid",
            "producer_boot_invalid", "producer_lifetime_expired", "producer_system_timeout", "producer_worker_unavailable") +
            setOf("identity", "export", "app_identity", "app_uid", "fixture", "command", "environment", "metadata", "process", "pid", "kernel", "header", "proof").flatMap { stage ->
                setOf("denied", "missing", "system", "field", "method", "io", "unavailable").map { category ->
                    "producer_" + stage + "_" + category
                }
            }
    }
}
