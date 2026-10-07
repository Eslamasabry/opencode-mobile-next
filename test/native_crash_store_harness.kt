package io.github.eslamasabry.opencode_mobile

import java.io.File

/** Pure JVM regression; uses only synthetic credential values. */
fun main(args: Array<String>) {
    val directory = File(args.single()).apply { mkdirs() }
    val store = NativeCrashStore(directory)
    val privateValue = "oak-cloud"
    val error = IllegalStateException(privateValue).apply {
        stackTrace = arrayOf(StackTraceElement("io.github.eslamasabry.opencode_mobile.$privateValue", privateValue, "$privateValue.kt", 7))
    }
    store.write(error)
    check(store.read() == null) { "Default-off store captured an error" }
    check(directory.listFiles().orEmpty().isEmpty())
    File(directory, "crash-diagnostics-consent").writeText("${System.currentTimeMillis() - 1000}")
    store.write(error)
    for (file in directory.listFiles().orEmpty()) {
        check(!file.readText().contains(privateValue)) { "Private exception value reached disk" }
    }
    check(store.read()?.get("message") == "Invalid state")
    check(File(directory, "native-last-crash.properties").length() <= 24576)
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    var delegated = false
    Thread.setDefaultUncaughtExceptionHandler { _, safe ->
        delegated = true
        check(!safe.toString().contains(privateValue))
        check(safe.stackTrace.isEmpty())
        check(safe.cause == null)
    }
    try {
        val handler = store.install()
        handler.uncaughtException(Thread.currentThread(), error)
        check(delegated) { "Previous fatal handler was not called" }
    } finally {
        Thread.setDefaultUncaughtExceptionHandler(previous)
    }
    store.clear()
    check(store.read() == null)
    File(directory, "crash-diagnostics-consent").delete()
    store.write(error)
    check(store.read() == null)
    println("Native crash store: default OFF, private category capture, deletion and fatal delegation PASS")
}
