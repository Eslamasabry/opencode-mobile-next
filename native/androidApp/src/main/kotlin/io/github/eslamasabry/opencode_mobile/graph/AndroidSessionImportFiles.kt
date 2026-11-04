package io.github.eslamasabry.opencode_mobile.graph

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportDocument
import io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportFiles
import io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportProblem
import io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportResult
import java.io.FileNotFoundException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

/**
 * Local SAF access only after Choose JSON file; no URI, file name or document is persisted or
 * logged.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<SessionImportFiles>())
@Inject
class AndroidSessionImportFiles(private val dispatchers: AppDispatchers) : SessionImportFiles {
    private var activity: ComponentActivity? = null
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var pending: CompletableDeferred<Uri?>? = null

    fun attach(host: ComponentActivity) {
        if (activity === host) return
        activity?.let(::detach)
        activity = host
        launcher =
            host.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                pending?.complete(uri)
                pending = null
            }
    }

    fun detach(host: ComponentActivity) {
        if (activity !== host) return
        pending?.complete(null)
        pending = null
        launcher?.unregister()
        launcher = null
        activity = null
    }

    override suspend fun choose(): Outcome<SessionImportDocument?> =
        withContext(dispatchers.main) {
            val host = activity ?: return@withContext Outcome.Failed(Failure.Device())
            val picker = launcher ?: return@withContext Outcome.Failed(Failure.Device())
            if (pending != null)
                return@withContext Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            val answer = CompletableDeferred<Uri?>()
            pending = answer
            try {
                when (
                    val launched =
                        attempt({ Failure.Device() }) {
                            picker.launch(arrayOf("application/json", "text/json", "text/plain"))
                        }
                ) {
                    is Outcome.Failed -> {
                        answer.complete(null)
                        return@withContext launched
                    }
                    is Outcome.Ok -> Unit
                }
                val uri = answer.await() ?: return@withContext Outcome.Ok(null)
                if (activity !== host) return@withContext Outcome.Ok(null)
                withContext(dispatchers.io) {
                    when (
                        val read =
                            attempt({ Failure.Device() }) {
                                host.contentResolver.openInputStream(uri)?.use { input ->
                                    SessionImportDocument.read(
                                        sequence {
                                            val buffer = ByteArray(16 * 1024)
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                if (count > 0) yield(buffer.copyOf(count))
                                            }
                                        }
                                    )
                                } ?: throw FileNotFoundException()
                            }
                    ) {
                        is Outcome.Failed -> read
                        is Outcome.Ok ->
                            when (val document = read.value) {
                                is SessionImportResult.Ok -> Outcome.Ok(document.value)
                                is SessionImportResult.Refused ->
                                    Outcome.Failed(
                                        if (document.problem == SessionImportProblem.TooLarge)
                                            Failure.StorageRefused()
                                        else Failure.Refused(RefusalReason.InvalidAddress)
                                    )
                            }
                    }
                }
            } finally {
                if (pending === answer && answer.isCompleted) pending = null
            }
        }
}
