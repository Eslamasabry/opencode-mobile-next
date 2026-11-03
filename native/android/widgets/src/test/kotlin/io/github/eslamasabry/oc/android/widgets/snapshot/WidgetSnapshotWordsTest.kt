package io.github.eslamasabry.oc.android.widgets.snapshot

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetSnapshotWordsTest {
    @Test
    fun `B-ANDR-116 the widget snapshot speaks the app's language, English and Arabic`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(WidgetSnapshotWords(), WidgetSnapshotWords.load(context, "en"))
        val arabic = WidgetSnapshotWords.load(context, "ar")
        assertEquals("محادثة جديدة", arabic.newConversation)
        assertEquals("جارٍ تشغيل أمر…", arabic.toolCommand)
        assertEquals("استخدام webfetch", arabic.usePermission("webfetch"))
        val snapshot =
            WidgetSnapshotBuilder(Redactor(), arabic)
                .build(WidgetSnapshotInput(sessions = listOf(WidgetSession("s1", "New session - 2026-10-15T04:39:03.218Z", 1))))
        val title = snapshot.getValue("sessions").jsonArray.single().jsonObject["title"]?.jsonPrimitive?.contentOrNull
        assertEquals("محادثة جديدة", title)
    }

    @Test
    fun `B-ANDR-112 an outside-the-project ask is asked in Arabic when the app speaks Arabic`() {
        val arabic = WidgetSnapshotWords.load(RuntimeEnvironment.getApplication(), "ar")
        val ask = WidgetText(Redactor()).askFor("external_directory", listOf("/root/*"), arabic, tool = "write", target = "/root/a.txt")
        assertEquals("الكتابة خارج هذا المشروع؟", ask["q"])
    }
}
