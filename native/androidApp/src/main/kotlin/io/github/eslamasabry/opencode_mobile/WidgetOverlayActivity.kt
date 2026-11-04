package io.github.eslamasabry.opencode_mobile

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.eslamasabry.oc.android.widgets.WidgetPanels
import io.github.eslamasabry.oc.android.widgets.WidgetTextActions
import io.github.eslamasabry.oc.feature.widgetactions.presentation.*
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetActionEntries
import io.github.eslamasabry.oc.feature.widgetactions.ui.WidgetPanelEntries
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.opencode_mobile.graph.*

/** One private, kit-only window. Presentation panels never inspect message routing extras. */
open class WidgetOverlayActivity : ComponentActivity() {
    private var voice: WidgetOverlayVoice? = null
    private var messages: WidgetActionViewModel? = null

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        // Typed and dictated widget text never appears in screenshots or the recents preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val app = applicationContext
        val widgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        // The exported launcher entry accepts only a widget id (Flutter WidgetConfigureActivity):
        // the window is chosen locally, and the Conversations widget's Edit picks its project.
        val mode =
            if (this is WidgetConfigureActivity) {
                val provider =
                    AppWidgetManager.getInstance(app).getAppWidgetInfo(widgetId)?.provider
                if (provider?.className == SessionsWideWidgetProvider::class.java.name) "project"
                else "configure"
            } else
                intent.getStringExtra(WidgetTextActions.EXTRA_MODE).takeIf {
                    it in setOf("configure", "project", "steer", "voice")
                } ?: "configure"
        val panel = mode in setOf("configure", "project")
        val target = if (panel) null else WidgetTextActions.target(intent)
        if ((panel && !WidgetPanels.owns(app, widgetId)) || (!panel && target == null)) {
            finish()
            return
        }
        val panelModel =
            if (panel)
                model(WidgetPanelViewModel::class.java) {
                    val saved = WidgetPanels.read(app, widgetId)
                    WidgetPanelViewModel(
                        WidgetPanelUi(
                            mode =
                                if (mode == "project") WidgetPanelMode.Project
                                else WidgetPanelMode.Style,
                            background = saved.background,
                            accent = saved.accent,
                            projects = saved.projects,
                            project = saved.project,
                        ),
                        WidgetPanelPort { state ->
                            WidgetPanels.save(
                                app,
                                widgetId,
                                saved.copy(
                                    background = state.background,
                                    accent = state.accent,
                                    projects = state.projects,
                                    project = state.project,
                                ),
                            )
                        },
                    )
                }
            else null
        val messageModel =
            if (target != null) {
                voice = WidgetOverlayVoice((appGraph as WidgetOverlayAccessors).widgetOverlayVoice)
                model(WidgetActionViewModel::class.java) {
                    WidgetActionViewModel(
                        WidgetActionPort { text ->
                            when (WidgetTextActions.send(app, target, text)) {
                                "accepted" -> WidgetActionPhase.Accepted
                                "rejected" -> WidgetActionPhase.Rejected
                                else -> WidgetActionPhase.Uncertain
                            }
                        },
                        voice,
                        newConversation = mode == "voice",
                    )
                }
            } else null
        messages = messageModel
        val settings = appGraph as SettingsAppGraph
        val platform = AndroidKitPlatform(window.decorView, window)
        setContent {
            val appearance by settings.appPreferences.appearance.collectAsState()
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme(
                    appearance = appearance,
                    platform = platform,
                    localeTag =
                        AppLocale.applied ?: resources.configuration.locales[0].toLanguageTag(),
                ) {
                    if (panelModel != null) {
                        val state by panelModel.state.collectAsState()
                        LaunchedEffect(state.closed) {
                            if (state.closed) {
                                if (state.saved)
                                    setResult(
                                        RESULT_OK,
                                        Intent()
                                            .putExtra(
                                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                widgetId,
                                            ),
                                    )
                                finish()
                            }
                        }
                        WidgetPanelEntries.Content(panelModel)
                    } else if (messageModel != null && target != null) {
                        val state by messageModel.state.collectAsState()
                        LaunchedEffect(state.destination) {
                            val destination = state.destination ?: return@LaunchedEffect
                            messageModel.acknowledgeDestination()
                            if (destination == WidgetActionDestination.Conversation) {
                                startActivity(
                                    Intent(this@WidgetOverlayActivity, MainActivity::class.java)
                                        .apply {
                                            addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                            )
                                            if (target.sessionId.isNotEmpty()) {
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
                                        }
                                )
                            }
                            finish()
                        }
                        WidgetActionEntries.Content(messageModel)
                    }
                }
            }
        }
    }

    private fun <T : ViewModel> model(type: Class<T>, create: () -> T): T =
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
            },
        )[type]

    override fun onResume() {
        super.onResume()
        messages?.foreground(true)
    }

    override fun onPause() {
        messages?.foreground(false)
        super.onPause()
    }
}

/** Frozen configure target used by the launcher's Edit and initial placement. */
class WidgetConfigureActivity : WidgetOverlayActivity()
