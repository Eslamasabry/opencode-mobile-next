package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

/**
 * The kit's one answer to "is a modal part open anywhere?": every kit part that dims or covers the page (sheet, dialog,
 * side sheet, menu, floating panel, page overlay) reports here through [KitReportsModal], so a floating control that lives
 * above the page (the edge light) can stand down without any screen passing a flag. A new modal part reports the same way;
 * `KitModalReportGuardTest` fails a modal file that does not.
 */
public object KitModalOpen {
    private val holders = mutableStateListOf<Any>()

    /** True while at least one kit modal part is open, opening or fading out. Read in composition. */
    public val isOpen: Boolean
        get() = holders.isNotEmpty()

    internal fun acquire(holder: Any) {
        holders.add(holder)
    }

    internal fun release(holder: Any) {
        holders.remove(holder)
    }
}

private val LocalCountsAsModal = compositionLocalOf { true }

/**
 * Modal parts drawn inside [content] do not count in [KitModalOpen]: for the floating control that owns them (the edge
 * light's own panel and spot picker), which must not hide itself behind its own layers.
 */
@Composable
public fun KitOwnModalLayer(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCountsAsModal provides false, content = content)
}

/** Reports a modal part as open for as long as [open] is true and this call stays composed. */
@Composable
public fun KitReportsModal(open: Boolean = true) {
    val holder = remember { Any() }
    val counts = LocalCountsAsModal.current
    DisposableEffect(open, counts) {
        if (open && counts) KitModalOpen.acquire(holder)
        onDispose { KitModalOpen.release(holder) }
    }
}
