package io.github.eslamasabry.opencode_mobile

import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicBoolean

/** One terminal reply attempt, including queued replies across engine detach.
 * A disconnected messenger cannot receive a reply; its exception must never
 * crash Android's main looper or cause a second reply attempt.
 */
class NativeChannelReplies(private val dispatch: (Runnable) -> Boolean) {
    private val pending = mutableSetOf<Reply>()
    private var attached = true

    @Synchronized
    fun wrap(delegate: MethodChannel.Result): Reply = Reply(delegate).also { pending.add(it) }

    fun detach() {
        val selected = synchronized(this) {
            attached = false
            pending.toList()
        }
        selected.forEach { it.error("engine_detached", "The connection stopped. Try again.", null) }
    }

    inner class Reply(private val delegate: MethodChannel.Result) : MethodChannel.Result {
        private val answered = AtomicBoolean(false)

        fun guarded(code: String, work: () -> Unit) {
            try { work() } catch (_: Throwable) {
                error(code, "The phone operation could not finish. Try again.", null)
            }
        }

        private fun reply(send: () -> Unit) {
            if (answered.get()) return
            var dispatchFailed = false
            val deliver = Runnable {
                if (!answered.compareAndSet(false, true)) return@Runnable
                val live = synchronized(this@NativeChannelReplies) {
                    pending.remove(this)
                    attached
                }
                try {
                    if (live && !dispatchFailed) send()
                    else delegate.error("engine_detached", "The connection stopped. Try again.", null)
                } catch (_: Throwable) {
                    // A reply that threw may already have been sent: never retry.
                }
            }
            try {
                if (!dispatch(deliver)) { dispatchFailed = true; deliver.run() }
            } catch (_: Throwable) {
                dispatchFailed = true
                deliver.run()
            }
        }

        override fun success(result: Any?) = reply { delegate.success(result) }
        override fun error(code: String, message: String?, details: Any?) =
            reply { delegate.error(code, message, details) }
        override fun notImplemented() = reply { delegate.notImplemented() }
    }
}
