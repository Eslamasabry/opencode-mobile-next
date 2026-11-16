package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.ui.kit.foundation.KitAndroidClipboard
import io.github.eslamasabry.oc.ui.kit.foundation.KitClipboardMode
import android.app.Application
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
            KitAndroidClipboard.write(app, address, KitClipboardMode.Public, app.applicationInfo.loadLabel(app.packageManager))
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
