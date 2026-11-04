package io.github.eslamasabry.opencode_mobile.graph

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Xml
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import java.io.File
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser

/**
 * A bounded read-only view of plain display preferences and the saved-server count. Opening Android
 * SharedPreferences before the startup backup can rewrite its XML/backup pair; this window must not
 * do so, and must not construct secure stores. Call on the injected IO dispatcher. The parser
 * ignores every other value and never returns profile records.
 */
internal object ManageSpacePreferences {
    data class Display(
        val appearance: KitAppearance = KitAppearance.Default,
        val savedServers: Int = 0,
        val localeTag: String? = null,
    )

    fun read(context: Context): Display =
        try {
            val file =
                File(context.applicationInfo.dataDir, "shared_prefs/FlutterSharedPreferences.xml")
            val fd =
                Os.open(
                    file.path,
                    OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                    0,
                )
            val stream =
                try {
                    val stat = Os.fstat(fd)
                    check(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 0..MAX_XML_BYTES)
                    ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd))
                } finally {
                    Os.close(fd)
                }
            var mode: String? = null
            var pack: String? = null
            var count = 0
            var locale: String? = null
            stream.use {
                val parser = Xml.newPullParser()
                parser.setInput(it, null)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "string") {
                        when (parser.getAttributeValue(null, "name")) {
                            "flutter.oc.appearance" -> mode = parser.nextText()
                            "flutter.oc.themePack" -> pack = parser.nextText()
                            "flutter.oc.appLocale" ->
                                locale =
                                    parser.nextText().takeIf { tag ->
                                        tag in setOf("en", "ar", "zh", "es", "pt", "ja", "ru")
                                    }
                            "flutter.oc.profiles" ->
                                count =
                                    try {
                                        JSONArray(parser.nextText()).length()
                                    } catch (_: Exception) {
                                        0
                                    }
                        }
                    }
                    event = parser.next()
                }
            }
            Display(
                KitAppearance(
                    mode =
                        AppearanceMode.entries.firstOrNull { it.name.lowercase() == mode }
                            ?: AppearanceMode.Dark,
                    pack = PackId.entries.firstOrNull { it.storageName == pack } ?: PackId.Opencode,
                ),
                count,
                locale,
            )
        } catch (_: Exception) {
            Display()
        }

    private const val MAX_XML_BYTES = 32L * 1024 * 1024
}
