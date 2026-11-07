package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesTo(AppScope::class)
interface TermuxRetiredGraph {
    val termuxRetired: AndroidTermuxRetired
}

val AndroidAppGraph.termuxRetired: AndroidTermuxRetired
    get() = (this as TermuxRetiredGraph).termuxRetired

/**
 * Owner decision 2026-11-07: binds [TermuxRetiredNotice] to the app's files folder, the saved profiles and the
 * app-scoped "closed" flag. It reads again whenever the saved profiles change, so profiles that arrive with the
 * Flutter migration still lead to the notice.
 */
@Inject
@SingleIn(AppScope::class)
class AndroidTermuxRetired(
    application: Application,
    private val stores: PreviewStores,
    profileScope: ProfileScope,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)

    val notice =
        TermuxRetiredNotice(
            legacySetupFile = {
                withContext(dispatchers.io) {
                    File(application.filesDir, TermuxRetiredNotice.LEGACY_SETUP_FILE).isFile
                }
            },
            savedAddresses = { stores.profiles.profiles.value.map { it.profile.baseUrl } },
            memory =
                object : TermuxRetiredNotice.Memory {
                    override suspend fun seen(): Boolean =
                        stores.warm() !is Outcome.Ok ||
                            stores.prefs.bool(TermuxRetiredNotice.SEEN_KEY) == true

                    override suspend fun markSeen() {
                        if (stores.warm() is Outcome.Ok)
                            stores.prefs.commit(stores.lanes.app) {
                                putBool(TermuxRetiredNotice.SEEN_KEY, true)
                            }
                    }
                },
        )

    init {
        profileScope.register(
            KeyShape(
                TermuxRetiredNotice.SEEN_KEY,
                KeyScope.App,
                PrefType.Bool,
                ":androidApp",
                "Termux notice closed",
            )
        )
        scope.launch {
            if (stores.warm() is Outcome.Ok) stores.profiles.profiles.collect { notice.check() }
        }
    }
}
