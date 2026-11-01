package io.github.eslamasabry.oc.ui.shell.nav

import android.app.Application
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `Settings.Global.ANIMATOR_DURATION_SCALE`, observed for the app's lifetime (one observer, app-scoped). Accessibility
 * "Remove animations" sets it to 0, so that path arrives here too.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class AndroidSystemMotion(application: Application) : SystemMotion {
    private val resolver: ContentResolver = application.contentResolver
    private val scale = MutableStateFlow(read(resolver))
    override val animatorScale: StateFlow<Float> = scale.asStateFlow()

    init {
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scale.value = read(resolver)
                }
            },
        )
    }

    internal companion object {
        fun read(resolver: ContentResolver): Float =
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}
