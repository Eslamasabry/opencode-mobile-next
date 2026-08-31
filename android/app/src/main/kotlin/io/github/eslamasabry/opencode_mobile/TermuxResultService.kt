package io.github.eslamasabry.opencode_mobile

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import java.util.concurrent.ConcurrentHashMap

object TermuxCommandRegistry {
    private val callbacks = ConcurrentHashMap<Int, (Bundle?) -> Unit>()

    fun register(executionId: Int, callback: (Bundle?) -> Unit) {
        callbacks[executionId] = callback
    }

    fun complete(executionId: Int, result: Bundle?) {
        callbacks.remove(executionId)?.invoke(result)
    }

    fun remove(executionId: Int): Boolean = callbacks.remove(executionId) != null
}

class TermuxResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val executionId = intent?.getIntExtra(EXTRA_EXECUTION_ID, -1) ?: -1
        val result = intent?.getBundleExtra(EXTRA_RESULT_BUNDLE)
        if (executionId >= 0) TermuxCommandRegistry.complete(executionId, result)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_EXECUTION_ID = "oc.executionId"
        private const val EXTRA_RESULT_BUNDLE = "result"
    }
}
