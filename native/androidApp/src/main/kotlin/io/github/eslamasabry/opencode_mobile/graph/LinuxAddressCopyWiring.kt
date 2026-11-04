package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxAddressCopy

@Inject
@ContributesBinding(AppScope::class)
class AndroidLinuxAddressCopy(private val app: Application) : LinuxAddressCopy {
    override suspend fun copy(address: String): Outcome<Unit> {
        if (!Regex("^http://127\\.0\\.0\\.1:[0-9]{2,5}/$").matches(address))
            return Outcome.Failed(Failure.Device())
        return try {
            val clipboard =
                app.getSystemService(ClipboardManager::class.java)
                    ?: return Outcome.Failed(Failure.Device())
            clipboard.setPrimaryClip(
                ClipData.newPlainText(app.applicationInfo.loadLabel(app.packageManager), address)
            )
            Outcome.Ok(Unit)
        } catch (_: SecurityException) {
            Outcome.Failed(Failure.Device())
        }
    }
}

@Inject
@ContributesBinding(AppScope::class)
class AndroidLinuxLogDetails(
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor
) : io.github.eslamasabry.oc.feature.linux.host.LinuxLogDetails {
    override fun redact(text: String): io.github.eslamasabry.oc.core.common.Redacted =
        redactor.details(text)
}
