package io.github.eslamasabry.oc.core.domain.capabilities

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The structural half of test/desktop_platform_gating_test.dart: the Dart app reached the platform channel through a
 * runtime gate; the native desktop app cannot reach it at all, because it depends on no `:android:*` module (the
 * service, widget, shortcut and tile code lives only there) and binds desktop capabilities.
 */
class DesktopPlatformReachTest {
    private fun nativeRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir)
    }

    private val desktopBuild get() = File(nativeRoot(), "desktopApp/build.gradle.kts").readText()
    private val desktopGraph get() =
        File(nativeRoot(), "desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/DesktopAppGraph.kt").readText()

    @Test
    fun `B-ANDR-021 the desktop app never touches the background service channel`() {
        assertEquals(emptyList(), Regex("\":android:[a-z-]+\"").findAll(desktopBuild).map { it.value }.toList())
        assertTrue("PlatformCapabilities.desktop()" in desktopGraph)
        assertTrue(File(nativeRoot(), "android/notifications").isDirectory, "the Android service side exists")
    }

    @Test
    fun `B-ANDR-022 the desktop app has no widget, shortcut or tile surface to write`() {
        for (module in listOf(":android:widgets", ":android:tiles", ":android:notifications")) {
            assertTrue(module !in desktopBuild, module)
        }
        assertTrue(File(nativeRoot(), "android/widgets").isDirectory)
        assertTrue(File(nativeRoot(), "android/tiles").isDirectory)
    }
}
