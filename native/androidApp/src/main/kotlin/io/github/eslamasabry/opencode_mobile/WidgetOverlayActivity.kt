package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.eslamasabry.oc.android.widgets.WidgetTextActions
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionDestination
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionPhase
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionPort
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetActionViewModel
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetActionEntries
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.opencode_mobile.graph.AppLocale
import io.github.eslamasabry.opencode_mobile.graph.SettingsAppGraph
import io.github.eslamasabry.opencode_mobile.graph.appGraph

/**
 * A real kit screen for one widget message. Unknown delivery stays visible and never offers resend.
 */
class WidgetOverlayActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val target = WidgetTextActions.target(intent)
        if (target == null) {
            finish()
            return
        }
        val app = applicationContext
        val model =
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val port = WidgetActionPort { text ->
                            when (WidgetTextActions.send(app, target, text)) {
                                "accepted" -> WidgetActionPhase.Accepted
                                "rejected" -> WidgetActionPhase.Rejected
                                else -> WidgetActionPhase.Uncertain
                            }
                        }
                        @Suppress("UNCHECKED_CAST")
                        return WidgetActionViewModel(port) as T
                    }
                },
            )[WidgetActionViewModel::class.java]
        val settings = appGraph as SettingsAppGraph
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            val state by model.state.collectAsState()
            LaunchedEffect(state.destination) {
                val destination = state.destination ?: return@LaunchedEffect
                model.acknowledgeDestination()
                if (destination == WidgetActionDestination.Conversation) {
                    startActivity(
                        Intent(this@WidgetOverlayActivity, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                            putExtra(
                                "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND",
                                "complete",
                            )
                            putExtra(
                                "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_SESSION_ID",
                                target.sessionId,
                            )
                            putExtra(
                                "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_PROFILE_ID",
                                target.profileId,
                            )
                        }
                    )
                }
                finish()
            }
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme(
                    appearance = appearance,
                    platform = platform,
                    localeTag =
                        AppLocale.applied ?: resources.configuration.locales[0].toLanguageTag(),
                ) {
                    WidgetActionEntries.Content(model)
                }
            }
        }
    }
}
