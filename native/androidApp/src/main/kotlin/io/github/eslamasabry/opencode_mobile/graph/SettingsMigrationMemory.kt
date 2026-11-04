package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideMemory
import io.github.eslamasabry.oc.engine.settings.walkthrough.SettingsMemory
import io.github.eslamasabry.oc.engine.settings.walkthrough.WhatsNewMemory
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.OnboardingRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Both walkthroughs use migrated, device-wide keys: forgetting a server must leave them intact. */
@SingleIn(AppScope::class)
@Inject
class NativeSettingsMemory(private val stores: PreviewStores) {
    private val storage =
        object : SettingsMemory {
            override suspend fun read(key: String): Outcome<String?> {
                when (val warmed = stores.warm()) {
                    is Outcome.Failed -> return warmed
                    is Outcome.Ok -> Unit
                }
                return (stores.prefs.get(key) as? PrefValue.Str)?.value.ok()
            }

            override suspend fun write(key: String, value: String): Outcome<Unit> {
                when (val warmed = stores.warm()) {
                    is Outcome.Failed -> return warmed
                    is Outcome.Ok -> Unit
                }
                return stores.prefs.commit(stores.lanes.app) { putString(key, value) }
            }
        }
    val appearanceGuide: AppearanceGuideMemory = AppearanceGuideMemory(storage)
    val whatsNew: WhatsNewMemory = WhatsNewMemory(storage)
}

/** Called only after the migration gate and first shell frame, for an ordinary launcher start. */
@SingleIn(AppScope::class)
@Inject
class NativeWhatsNewStarter(
    private val memory: NativeSettingsMemory,
    private val stores: PreviewStores,
    private val nav: AppNavigator,
    private val build: AppBuildInfo,
    private val firstRun: FirstRunStarter,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            firstRun.awaitReady()
            if (stores.warm() is Outcome.Failed) return@launch
            val launchTop = nav.top.value
            if (
                launchTop != ChatsRoute.Home &&
                    launchTop != ServersRoute.Home &&
                    launchTop != OnboardingRoute.Welcome
            )
                return@launch
            val due =
                memory.whatsNew.takeDue(
                    build.versionName,
                    build.versionCode.toString(),
                    stores.profiles.profiles.value.isNotEmpty(),
                )
            if (due is Outcome.Ok && due.value && nav.top.value == launchTop)
                nav.open(SettingsRoute.WhatsNew)
        }
    }
}
