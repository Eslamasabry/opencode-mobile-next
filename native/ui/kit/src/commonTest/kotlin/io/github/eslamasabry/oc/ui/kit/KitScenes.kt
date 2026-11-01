package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The P7 scenes, shared by the desktop test and the Robolectric goldens so "one kit part tested three ways" tests the
 * same composition. Copy is sample copy (tests only); screens take theirs from `:ui:words`.
 */
class SceneWords(
    val send: String, val disabled: String, val reason: String, val row: String,
    val sheetTitle: String, val sheetBody: String, val close: String, val screenTitle: String,
    val frameLine: String, val readingLine: String,
) {
    companion object {
        val En = SceneWords(
            send = "Send", disabled = "Run again", reason = "Connect a server first", row = "Open settings",
            sheetTitle = "Rename chat", sheetBody = "The new name shows in the list and on other devices.",
            close = "Close", screenTitle = "Conversations",
            frameLine = "Stop and send", readingLine = "A long reply keeps the reading size the person chose.",
        )
        val Ar = SceneWords(
            send = "إرسال", disabled = "شغّل مرة أخرى", reason = "صِل بخادم أولًا", row = "افتح الإعدادات",
            sheetTitle = "أعد تسمية المحادثة", sheetBody = "يظهر الاسم الجديد في القائمة وعلى أجهزتك الأخرى.",
            close = "إغلاق", screenTitle = "المحادثات",
            frameLine = "أوقِف وأرسِل", readingLine = "مُحَمَّدٌ — إِنْ شَاءَ ٱللَّٰهُ: الردّ الطويل يبقى بحجم القراءة.",
        )

        fun of(localeTag: String): SceneWords = if (localeTag.startsWith("ar")) Ar else En
    }
}

@Composable
fun ButtonsScene(w: SceneWords, onSend: () -> Unit = {}, onLong: () -> Unit = {}) {
    Column(
        Modifier.background(LocalKitColors.current.ground).padding(16.dp).testTag("buttons"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KitButton(w.send, onClick = onSend, haptic = KitHaptic.Send, modifier = Modifier.fillMaxWidth())
        KitButton(w.disabled, onClick = {}, enabled = false, disabledReason = w.reason, modifier = Modifier.fillMaxWidth())
        KitTappable(onClick = {}, onLongClick = onLong, modifier = Modifier.fillMaxWidth()) {
            KitText(w.row, Modifier.fillMaxWidth().padding(horizontal = 16.dp), role = TypeRole.RowTitle)
        }
    }
}

@Composable
fun TextScaleScene(w: SceneWords) {
    Column(
        Modifier.background(LocalKitColors.current.ground).padding(16.dp).testTag("text"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KitText(w.frameLine, Modifier.testTag("frame"), role = TypeRole.Button, kind = KitTextKind.Frame)
        KitText(w.readingLine, Modifier.testTag("reading"), role = TypeRole.Body)
        KitText(w.readingLine, role = TypeRole.RowTitle, tone = TextTone.Secondary)
    }
}

@Composable
fun SheetScene(w: SceneWords, visible: Boolean, onDismiss: () -> Unit) {
    KitScreen(title = w.screenTitle) {
        KitText(w.readingLine, role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitSheet(visible = visible, onDismissRequest = onDismiss, title = w.sheetTitle, closeLabel = w.close) {
            KitText(w.sheetBody, role = TypeRole.Body)
            KitButton(w.send, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Stripes scroll "behind" a chat header; the header wears frost. */
@Composable
fun FrostScene(w: SceneWords) {
    val frost = rememberKitFrostState()
    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
        Column(Modifier.fillMaxSize().kitFrostSource(frost)) {
            val stripes = listOf(Color(0xFF3DDC8A), Color(0xFFC7A6FF), Color(0xFFFFB547), Color(0xFF7FD1FF))
            repeat(12) { i -> Box(Modifier.fillMaxWidth().height(24.dp).background(stripes[i % stripes.size])) }
        }
        Box(
            Modifier.fillMaxWidth().height(72.dp).kitFrost(FrostRole.ChatHeader, frost).testTag("header"),
            contentAlignment = Alignment.CenterStart,
        ) {
            KitText(w.screenTitle, Modifier.padding(horizontal = 16.dp), role = TypeRole.Title, kind = KitTextKind.Frame)
        }
    }
}
