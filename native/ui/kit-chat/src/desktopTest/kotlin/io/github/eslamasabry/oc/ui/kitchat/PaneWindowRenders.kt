package io.github.eslamasabry.oc.ui.kitchat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

private const val LONG =
    "val resolvedProfileKeyForTheCurrentServerConnection = profileScopeRegistry.keysFor(profileId).filter { i" +
        "t.startsWith(prefix) }"

private val PATCH =
    buildString {
        append("--- a/src/Profiles.kt\n+++ b/src/Profiles.kt\n")
        append(
            "@@ -10,5 +10,5 @@\n context line one\n context line two\n-    $LONG.first()\n+    " +
                "$LONG.firstOrNull()\n context line three\n",
        )
        append(
            "@@ -80,5 +80,6 @@\n context line four\n-    $LONG.size\n+    $LONG.count()\n+    $LONG.toList()" +
                "\n context line five\n",
        )
    }

@Composable
private fun Parts() {
    Column {
        KitTopBar(
            title = "Profiles.kt",
            subtitle = "Review the change",
            actions = listOf("Copy", "Share", "Comment", "Open all").map { KitLineAction(it, {}) },
            more = { hidden -> KitText("+${hidden.size}") },
        )
        KitDiffView(
            file = DiffModel.parsePatch(PATCH, "src/Profiles.kt").first(),
            hunkLabel = { a, b, c, d -> "Lines $a-$b to $c-$d" },
            gapLabel = { "$it unchanged lines" },
        )
        KitPermissionCard(
            title = "Edit src/Profiles.kt?", summary = null, subject = "src/Profiles.kt",
            allowOnceLabel = "Allow once", alwaysLabel = "Always allow", denyLabel = "Deny",
            onAllowOnce = {}, onAlways = {}, onDeny = {}, waitingAge = "2 min", needsYou = true,
        )
    }
}

/** Picture-making only: asserts nothing. */
@OptIn(ExperimentalTestApi::class)
class PaneWindowRenders {
    private val out = File("build/panewindow").also { it.mkdirs() }

    private fun shoot(name: String, wDp: Int, hDp: Int, content: @Composable () -> Unit) {
        runDesktopComposeUiTest(wDp * 2, hDp * 2) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    ChatTestTheme(dark = true) { Box(Modifier.fillMaxSize()) { content() } }
                }
            }
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    @Test
    fun render() {
        shoot("a-window-1280x800", 1280, 800) { Parts() }
        shoot("b-pane-420-in-1280x800", 1280, 800) {
            Row(Modifier.fillMaxSize()) {
                KitPaneWindow(Modifier.width(420.dp).fillMaxHeight()) { Parts() }
            }
        }
        shoot("c-window-420x800", 420, 800) { Parts() }
    }
}
