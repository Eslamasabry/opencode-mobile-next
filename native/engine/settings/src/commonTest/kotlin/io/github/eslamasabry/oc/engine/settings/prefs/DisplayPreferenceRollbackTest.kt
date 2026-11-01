package io.github.eslamasabry.oc.engine.settings.prefs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DisplayPreferenceRollbackTest {
    @Test
    fun `B-STATE-005 a refused display preference restores the cached value whether the write returns false or throws`() =
        runTest {
            for (throws in listOf(false, true)) {
                val disk = TestDisk()
                val store = storeOver(disk, StandardTestDispatcher(testScheduler))
                store.seed("oc.appearance" to "system")
                val owner = Preferences(store, WriterLane("app"), backgroundScope)
                if (throws) disk.throwing = true else disk.refuse = { _, _ -> true }

                for (change in listOf(PreferenceChange.Appearance(AppearanceChoice.Dark), PreferenceChange.Theme(ThemePack.Solarized))) {
                    val failed = assertIs<Outcome.Failed>(owner.set(change), "throws=$throws")
                    assertEquals(StorageProblem.WriteRefused, assertIs<Failure.Storage>(failed.failure).problem)
                }
                assertEquals(AppearanceChoice.System, owner.value.value.appearance, "throws=$throws")
                assertEquals(AppearanceChoice.System, owner.appearance.value, "throws=$throws")
                assertEquals(ThemePack.OpenCode, owner.value.value.themePack, "throws=$throws")
                assertEquals(0L, owner.revision.value)

                val restarted = Preferences(storeOver(TestDisk(disk.persisted()), StandardTestDispatcher(testScheduler)), WriterLane("app"), backgroundScope)
                assertEquals(AppearanceChoice.System, restarted.value.value.appearance, "throws=$throws")
                assertEquals(ThemePack.OpenCode, restarted.value.value.themePack, "throws=$throws")
            }
        }
}
