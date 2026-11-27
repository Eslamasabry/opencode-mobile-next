package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.engine.delivery.InteractiveSendCheck
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.net.URI
import kotlinx.coroutines.withContext

/** B-ANDR-070: a scheduled occurrence's bounded phone runtime never accepts interactive work. */
@ContributesIntoSet(AppScope::class)
@Inject
class InteractiveScheduleAdmission(
    private val app: Application,
    private val profiles: ProfileStore,
    private val dispatchers: AppDispatchers,
) : InteractiveSendCheck {
    override suspend fun check(profile: ServerProfile): Outcome<Unit> {
        val saved = profiles.profile(profile.id) ?: return stale("server removed")
        val current = saved.toServerProfile(profile.managedOnPhone) ?: return stale("server unreadable")
        // B-399: the name is a label, never the connection (the phone's helper is saved under its phone's name at each
        // start). A rename alone refused every send through a connection opened before it as a changed connection.
        if (current.copy(name = profile.name) != profile) return stale("server edited since it connected")
        // Agent sockets and remote servers do not use the scheduled OpenCode phone runtime.
        val address = URI(profile.origin.toString())
        if (
            saved.profile.usesAgentSocket ||
                address.host !in LOOPBACK ||
                address.port != PhoneServerScripts.PORT
        )
            return Outcome.Ok(Unit)
        val reading =
            withContext(dispatchers.io) {
                attempt({ Failure.Unavailable(FeatureId("schedulePhoneCheckFailed")) }) {
                    BuiltinLinux.get(app).hasScheduledPhoneLease()
                }
            }
        if (profiles.profile(profile.id) !== saved) return stale()
        return when (reading) {
            is Outcome.Failed -> reading
            is Outcome.Ok ->
                if (reading.value)
                    Outcome.Failed(Failure.Unavailable(FeatureId("schedulePhoneBusy")))
                else Outcome.Ok(Unit)
        }
    }

    private fun stale(): Outcome.Failed = Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    /** SEND-STALE: the refusal, with the one clue a report needs (fixed words). */
    private fun stale(why: String): Outcome.Failed {
        log.w { "send refused as stale: $why" }
        return stale()
    }

    private val log = io.github.eslamasabry.oc.core.common.AppLog.tagged("send-admission")

    private companion object {
        val LOOPBACK = setOf("127.0.0.1", "localhost", "::1", "[::1]")
    }
}
