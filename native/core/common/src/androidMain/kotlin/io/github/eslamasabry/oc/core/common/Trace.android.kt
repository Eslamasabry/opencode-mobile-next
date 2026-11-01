package io.github.eslamasabry.oc.core.common

/** android.os.Trace (API 18+) until androidx.tracing 2.x is wired with the PerfTrace port. */
public actual fun platformTrace(): Trace = AndroidTrace

private object AndroidTrace : Trace {
    override fun <T> section(name: String, block: () -> T): T {
        android.os.Trace.beginSection(name.take(127))
        try {
            return block()
        } finally {
            android.os.Trace.endSection()
        }
    }
}
