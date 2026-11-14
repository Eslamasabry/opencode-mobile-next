package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.ui.graphics.layer.GraphicsLayer
import java.util.WeakHashMap

private val thread: ThreadLocal<KitLayerThread> = ThreadLocal.withInitial { KitLayerThread() }

internal actual fun kitLayerThread(): KitLayerThread = thread.get()

internal actual fun kitWeakLayerMap(): MutableMap<GraphicsLayer, List<GraphicsLayer>> = WeakHashMap()
