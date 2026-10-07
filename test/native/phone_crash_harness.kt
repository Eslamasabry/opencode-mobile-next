package io.github.eslamasabry.opencode_mobile

import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.nio.file.Files

private class Replies : MethodChannel.Result {
    val calls = mutableListOf<String>()
    var throws = false
    override fun success(result: Any?) { calls.add("success"); if (throws) error("reply failed") }
    override fun error(code: String, message: String?, details: Any?) { calls.add(code); if (throws) error("reply failed") }
    override fun notImplemented() { calls.add("unimplemented") }
}

fun main(args: Array<String>) {
    when (args.single()) {
        "reply-once" -> {
            val queue = mutableListOf<Runnable>()
            val replies = NativeChannelReplies { queue.add(it); true }
            val sink = Replies()
            val reply = replies.wrap(sink)
            reply.guarded("channel_error") { reply.success(null); throw java.io.IOException("private payload") }
            reply.error("late_error", null, null)
            queue.forEach { it.run() }
            check(sink.calls == listOf("success"))
            val throwing = Replies().also { it.throws = true }
            val second = replies.wrap(throwing)
            second.success(null); second.error("late_error", null, null)
            queue.forEach { it.run() }
            check(throwing.calls == listOf("success"))
        }
        "reply-detached" -> {
            val queue = mutableListOf<Runnable>()
            val replies = NativeChannelReplies { queue.add(it); true }
            val sink = Replies()
            replies.wrap(sink).success(null)
            replies.detach()
            queue.toList().forEach { it.run() }
            check(sink.calls == listOf("engine_detached"))
            val rejected = Replies()
            NativeChannelReplies { false }.wrap(rejected).guarded("denied") { throw SecurityException("secret") }
            check(rejected.calls == listOf("engine_detached"))
            val dispatchThrows = Replies()
            NativeChannelReplies { throw IllegalStateException("gone") }.wrap(dispatchThrows).success(null)
            check(dispatchThrows.calls == listOf("engine_detached"))
        }
        "crash-redaction" -> {
            val root = Files.createTempDirectory("crash-native-").toFile()
            try {
                val store = NativeCrashStore(root)
                val error = java.io.IOException("https://claude.com/login?code=SHORTCODE /home/oc/.claude/projects private prompt")
                error.stackTrace = arrayOf(
                    StackTraceElement("io.github.eslamasabry.opencode_mobile.PhoneAgentHost", "start", "PhoneAgentHost.kt", 42),
                    StackTraceElement("private prompt", "https://evil?token=CODE", "/home/oc/private.txt", 3),
                    StackTraceElement("java.io.File", "read", "/home/oc/.claude/secret.java", 2),
                )
                // Crash capture is default OFF. The category-only snapshot
                // below is meaningful only after explicit consent.
                store.write(error, 1000L)
                check(store.read() == null)
                check(!File(root, "native-last-crash.properties").exists())
                File(root, "crash-diagnostics-consent").writeText("1000")
                store.write(error, 1000L)
                val record = store.read()!!
                check(record["message"] == "Input/output failure")
                // Even apparently valid symbols are throwable-controlled.
                check(record["frames"] == emptyList<String>())
                val bytes = File(root, "native-last-crash.properties").readText()
                for (secret in listOf("SHORTCODE", "https", "/home/oc", "private prompt", "PhoneAgentHost")) check(secret !in bytes)
                check(record["exceptionClass"] == "java.io.IOException")
                val denied = SecurityException("https://host/login?code=SHORTCODE /home/oc/private")
                denied.stackTrace = arrayOf(StackTraceElement("android.app.Service", "startForeground", "Service.java", 10))
                store.write(RuntimeException("private service intent", denied), 1500L)
                val causes = store.read()!!["causes"] as List<*>
                check((causes.single() as Map<*, *>)["exceptionClass"] == "java.lang.SecurityException")
                check((causes.single() as Map<*, *>)["message"] == "Permission denied")
                check("SHORTCODE" !in File(root, "native-last-crash.properties").readText())
                store.write(IllegalStateException("other user content"), 2000L)
                check(store.read()!!["timestamp"] == 2000L)
                check("other user content" !in File(root, "native-last-crash.properties").readText())
                File(root, "native-last-crash.properties").writeText("bad")
                check(store.read() == null)
            } finally { root.deleteRecursively() }
        }
        "crash-chain-and-storage-failure" -> {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val root = Files.createTempDirectory("crash-native-").toFile()
            try {
                var chained = 0
                val error = IllegalStateException("private")
                Thread.setDefaultUncaughtExceptionHandler { receivedThread, received ->
                    check(receivedThread === Thread.currentThread())
                    check(received !== error)
                    check(received.message == "Native application error")
                    check(received.stackTrace.isEmpty())
                    check(received.cause == null)
                    check(received.suppressed.isEmpty())
                    chained++
                }
                val handler = NativeCrashStore(File(root, "missing")).install()
                handler.uncaughtException(Thread.currentThread(), error)
                check(chained == 1)
                Thread.setDefaultUncaughtExceptionHandler(null)
                File(root, "crash-diagnostics-consent").writeText("1000")
                NativeCrashStore(root).install().uncaughtException(Thread.currentThread(), error)
                check(NativeCrashStore(root).read() != null)
            } finally { Thread.setDefaultUncaughtExceptionHandler(previous); root.deleteRecursively() }
        }
        "crash-symlinks" -> {
            val root = Files.createTempDirectory("crash-native-").toFile()
            try {
                val outside = File(root, "outside").also { it.writeText("unchanged") }
                val private = File(root, "private").also { it.mkdir() }
                Files.createSymbolicLink(File(private, "native-last-crash.properties").toPath(), outside.toPath())
                val store = NativeCrashStore(private)
                check(store.read() == null)
                check(runCatching { store.write(IllegalStateException("private")) }.isFailure)
                check(outside.readText() == "unchanged")
            } finally { root.deleteRecursively() }
        }
        else -> error("Unknown scenario")
    }
    println("PASS ${args.single()}")
}
