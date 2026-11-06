package io.github.eslamasabry.oc.android.termux

import android.content.Intent
import android.os.Bundle
import org.robolectric.RuntimeEnvironment
import io.github.eslamasabry.opencode_mobile.TermuxResultService
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// E09-S04: Termux answers the class the Flutter app named in its PendingIntent; that class exists after the update
// and keeps the answer (without output) instead of dropping it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TermuxResultServiceTest {
    @Test
    fun `E09-S04 a Termux setup answer sent to the Flutter class name is kept`() {
        val context = RuntimeEnvironment.getApplication()
        TermuxLateResults.inApp(context.filesDir).clear()
        val intent =
            Intent(context, TermuxResultService::class.java)
                .putExtra("oc.setupToken", "3b0e2a4c")
                .putExtra(
                    "result",
                    Bundle().apply {
                        putString("stdout", "secret-output")
                        putInt("exitCode", 0)
                        putInt("err", -1)
                    },
                )
        Robolectric.buildService(TermuxResultService::class.java, intent).create().startCommand(0, 1)

        val kept = TermuxLateResults.inApp(context.filesDir).read()
        assertEquals(listOf(TermuxResultKind.Setup to true), kept.map { it.kind to it.ok })
        val stored = File(context.filesDir, TermuxLateResults.FILE_NAME).readText()
        assertTrue("secret-output" !in stored && "3b0e2a4c" !in stored, stored)
    }

    @Test
    fun `E09-S04 an answer with no extras does not crash and keeps nothing`() {
        val context = RuntimeEnvironment.getApplication()
        TermuxLateResults.inApp(context.filesDir).clear()
        Robolectric.buildService(TermuxResultService::class.java, null).create().startCommand(0, 1)
        assertEquals(emptyList(), TermuxLateResults.inApp(context.filesDir).read())
    }

    @Test
    fun `E09-S04 the module manifest declares the frozen Flutter class name, not exported`() {
        val manifest = File("src/main/AndroidManifest.xml")
        val doc =
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val services = doc.getElementsByTagName("service")
        val android = "http://schemas.android.com/apk/res/android"
        val found =
            (0 until services.length).map { services.item(it) as org.w3c.dom.Element }.firstOrNull {
                it.getAttributeNS(android, "name") == "io.github.eslamasabry.opencode_mobile.TermuxResultService"
            }
        assertTrue(found != null, "TermuxResultService is not declared under its Flutter name")
        assertEquals("false", found.getAttributeNS(android, "exported"))
        assertEquals("io.github.eslamasabry.opencode_mobile.TermuxResultService", TermuxResultService::class.java.name)
    }
}
