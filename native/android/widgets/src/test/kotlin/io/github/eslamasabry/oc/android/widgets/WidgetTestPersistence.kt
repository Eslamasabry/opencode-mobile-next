package io.github.eslamasabry.oc.android.widgets

import android.content.Context
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** A graph-owned real acknowledged store over each Robolectric sandbox's adopted preferences. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun bindWidgetTestPersistence(context: Context): WidgetPersistence {
    val dispatcher = UnconfinedTestDispatcher()
    val store = AndroidDataStores.flutterPrefs(context, StateMigrationGate.alwaysOpen(), dispatcher)
    val lanes = DefaultWriterLanes()
    return WidgetPersistence(
            store,
            lanes,
            DefaultProfileScope(store, NoSecrets, lanes.app),
            CoroutineScope(SupervisorJob() + dispatcher),
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(1000)
            },
        )
        .also { WidgetFeed.persistence = it }
}
