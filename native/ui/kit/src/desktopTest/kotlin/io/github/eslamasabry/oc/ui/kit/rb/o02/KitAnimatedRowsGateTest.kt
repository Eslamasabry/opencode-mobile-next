package io.github.eslamasabry.oc.ui.kit.rb.o02

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.motion.*
import kotlin.test.*

class KitAnimatedRowsGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_animated_rows", caps = mapOf("KitAnimatedRows" to "wrapper")) {
            KitAnimatedRows(listOf("First row", "Second row"), key = { it }) { KitText(it) }
        }
    }
    @Test fun firstBuildAndOffChangesAreImmediateAndKeepIdentity() {
        val holder = RowsHolder<String>()
        val first = holder.merge(listOf("a", "b"), {it}, false)
        assertTrue(first.all { it.state.currentState && it.state.targetState })
        val changed = holder.merge(listOf("b", "c"), {it}, true)
        assertEquals(listOf("b", "c"),changed.map {it.item})
        assertSame(first[1],changed[0])
        assertTrue(changed.all {it.state.currentState && it.state.targetState})
    }
    @Test fun leavingRowStaysUntilFoldFinishesAndCanReturn() {
        val holder = RowsHolder<String>()
        val first = holder.merge(listOf("a","b"),{it},false)
        val leaving = holder.merge(listOf("b"),{it},false)
        assertEquals(listOf("a","b"),leaving.map {it.item})
        assertFalse(leaving[0].state.targetState)
        val returned=holder.merge(listOf("a","b"),{it},false)
        assertSame(first[0],returned[0]); assertTrue(returned[0].state.targetState)
    }
}
