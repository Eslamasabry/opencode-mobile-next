package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.eslamasabry.oc.engine.factsbook.FactsBookDeviceCheck
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.fbAuthReason
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getString

/**
 * The person's screen lock, asked right before FactsBook signs an owner decision (F: device_authenticator.dart, which
 * used `local_auth`). Native asks Android's own confirm-credential screen (PIN, pattern, password, or the fingerprint
 * the lock screen offers), so the app holds no biometric permission (B-ANDR-068). One question at a time.
 */
internal class AndroidFactsBookDeviceCheck(private val app: Application, private val clock: Clock) : FactsBookDeviceCheck {
    private val one = Mutex()

    override suspend fun confirm(): FbResult<Instant> =
        one.withLock {
            val keyguard = app.getSystemService(KeyguardManager::class.java)
            if (keyguard == null || !keyguard.isDeviceSecure) return@withLock FbResult.Err(FactsBookError("device_auth_unavailable"))
            val answer = CompletableDeferred<Boolean>()
            FactsBookConfirmActivity.pending = answer
            FactsBookConfirmActivity.reason = getString(Res.string.fbAuthReason)
            val intent = Intent(app, FactsBookConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(app.packageManager) == null) return@withLock FbResult.Err(FactsBookError("device_auth_failed"))
            app.startActivity(intent)
            try {
                if (answer.await()) FbResult.Ok(clock.now()) else FbResult.Err(FactsBookError("device_auth_cancelled"))
            } finally {
                FactsBookConfirmActivity.pending = null
            }
        }
}

/** Shows nothing itself: it opens the confirm-credential screen and reports whether the person passed it. */
class FactsBookConfirmActivity : ComponentActivity() {
    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            answer(result.resultCode == Activity.RESULT_OK)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // the confirm screen is already up; its result comes to the launcher
        val keyguard = getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION") // the one confirm-credential path without a biometric permission, API 26+
        val ask = keyguard?.createConfirmDeviceCredentialIntent(reason, null)
        if (ask == null) {
            answer(false)
            return
        }
        // No resolveActivity here: the system confirm screen is not visible to it from API 30 without <queries>.
        runCatching { launcher.launch(ask) }.onFailure { answer(false) }
    }

    override fun onDestroy() {
        // Leaving without an answer (the task was removed) is a no, never a hang.
        if (isFinishing) pending?.complete(false)
        super.onDestroy()
    }

    private fun answer(ok: Boolean) {
        pending?.complete(ok)
        finish()
    }

    internal companion object {
        @Volatile var pending: CompletableDeferred<Boolean>? = null

        @Volatile var reason: String = ""
    }
}
