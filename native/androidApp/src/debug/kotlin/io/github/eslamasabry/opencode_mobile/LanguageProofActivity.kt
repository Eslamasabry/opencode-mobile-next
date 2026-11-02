package io.github.eslamasabry.opencode_mobile

import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chats_title
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceFileCount
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Slice-0 P6 proof (debug only). Shows CMP strings from two `strings_<feature>.xml` files of `:ui:words` and an Arabic
 * plural for 0, 1, 2, 3, 11 and 100, and posts one notification whose words come from Android resources (the
 * `strings_os.xml` path). The language switches at runtime through the per-app language API (Android 13+): the
 * buttons call [LocaleManager]; `adb shell cmd locale set-app-locales` and system Settings work the same way. The
 * activity is recreated on the switch, so both the screen and the re-posted notification change.
 */
class LanguageProofActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        postNotification()
        val platform = AndroidKitPlatform(window.decorView)
        setContent {
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme {
                    KitScreen(title = stringResource(Res.string.chats_title)) {
                        KitText("locale: ${resources.configuration.locales.toLanguageTags()}", KitTextRole.Supporting)
                        COUNTS.forEach { n ->
                            KitText("$n → ${pluralStringResource(Res.plurals.e7WorkspaceFileCount, n, n)}", KitTextRole.Body)
                        }
                        KitButton(label = "العربية", onClick = { switchTo("ar") })
                        KitButton(label = "English", onClick = { switchTo("en") })
                    }
                }
            }
        }
    }

    private fun switchTo(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        }
    }

    private fun postNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "P6 proof", NotificationManager.IMPORTANCE_DEFAULT))
        val r = resources // follows the per-app language: this activity was created with it
        val body = COUNTS.joinToString(" · ") { r.getQuantityString(R.plurals.notifStatusNeedsYou, it, it) }
        val n =
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(r.getString(R.string.notifStatusReconnectingGeneric))
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setOnlyAlertOnce(true)
                .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    private companion object {
        const val CHANNEL = "p6.proof"
        const val NOTIFICATION_ID = 6
        val COUNTS = listOf(0, 1, 2, 3, 11, 100)
    }
}
